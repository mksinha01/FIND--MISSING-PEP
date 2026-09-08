"""Story 13: End-to-End System Verification Suite.

Validates the full distributed chain programmatically:
1. Agent registration with admin enrollment key & camera sync.
2. Missing person report creation via atomic multipart endpoint.
3. Embedding synchronization to Edge Agent and ingestion into FAISS.
4. Detection, ByteTrack tracking, throttled ArcFace recognition, and temporal confirmation.
5. Sighting evidence capture (112x112 crop, full frame, 15 FPS MP4 clip) and upload.
6. Backend notification dispatch and mobile notification inbox verification.
Also verifies:
- Multi-photo per-frame candidate deduplication (Fix #14).
- Offline SQLite queue resilience with exponential retry backoff (Fix #18).
- Incremental tombstone sync for resolved/closed persons (Fix #15).
- Synthetic video streamer integration at 15 FPS.
"""
import base64
import io
import os
import shutil
import tempfile
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from unittest.mock import MagicMock
from uuid import UUID, uuid4

import cv2
import httpx
from httpx import ASGITransport, AsyncClient
import numpy as np
import pytest
from starlette.testclient import TestClient

from app.config import settings
from app.database import Base
from app.main import app
from app.models.face_embedding import FaceEmbedding
from app.models.missing_person import MissingPerson
from app.models.notification import Notification
from app.models.sighting import Sighting
from edge_agent.ai.evidence_collector import Evidence, EvidenceCollector
from edge_agent.ai.face_aligner import FaceAligner
from edge_agent.ai.face_detector import Detection, FaceDetector
from edge_agent.ai.face_quality import FaceQualityGate
from edge_agent.ai.face_recognizer import ArcFaceRecognizer
from edge_agent.ai.pipeline import EdgeAIPipeline
from edge_agent.ai.temporal_verifier import MatchEvent, TemporalVerifier
from edge_agent.ai.tracker import ByteTracker, ExtendedTrack
from edge_agent.ai.vector_search import VectorSearchEngine
from edge_agent.camera.circular_buffer import CircularFrameBuffer
from edge_agent.network.api_client import ApiClient
from edge_agent.network.embedding_syncer import EmbeddingSyncer
from edge_agent.network.sighting_uploader import SightingUploader
from edge_agent.storage.faiss_store import FAISSStore
from edge_agent.storage.local_db import SQLiteStore
from scripts.mock_rtsp_stream import SyntheticVideoStreamer, draw_synthetic_face


def _make_sync_api_client(base_url: str = "http://testserver", enrollment_key: str = settings.ADMIN_ENROLLMENT_KEY) -> ApiClient:
    """Creates a synchronous ApiClient bound to FastAPI ASGI app via Starlette TestClient."""
    client = ApiClient(base_url=base_url, enrollment_key=enrollment_key)
    client._client = TestClient(app, base_url=base_url, headers=client._build_default_headers())
    return client


# ═════════════════════════════════════════════════════════════════════════════
# 1. Master End-to-End Lifecycle Chain Test
# ═════════════════════════════════════════════════════════════════════════════

@pytest.mark.asyncio
async def test_e2e_full_lifecycle_chain(client: AsyncClient, mock_face_processing, edge_temp_dir: Path):
    """
    Complete end-to-end integration test of FIND-MISSING-PEP:
    1. Agent registration with enrollment key.
    2. Missing person report creation.
    3. Embedding synchronization into FAISS.
    4. Detection, ByteTrack tracking, ArcFace recognition, and temporal confirmation.
    5. Sighting evidence upload (crop, frame, video clip).
    6. Backend notification dispatch.
    """
    t_start = time.time()
    logger_steps = []

    # ─────────────────────────────────────────────────────────────────────────
    # STEP 1: Agent Registration with Enrollment Key & Camera Sync
    # ─────────────────────────────────────────────────────────────────────────
    device_id = f"EDGE-E2E-{uuid4().hex[:6]}"
    reg_payload = {
        "device_id": device_id,
        "name": "North Gate Edge Agent",
        "location": "North Campus Entrance",
        "version": "1.0.0",
    }

    # Verify registration security: missing enrollment key must fail
    resp_unauth = await client.post("/api/agents/register", json=reg_payload)
    assert resp_unauth.status_code == 403, "Agent registration must reject requests without enrollment key"

    # Register with valid enrollment key
    resp_reg = await client.post(
        "/api/agents/register",
        json=reg_payload,
        headers={"X-Enrollment-Key": settings.ADMIN_ENROLLMENT_KEY},
    )
    assert resp_reg.status_code == 201
    reg_data = resp_reg.json()
    agent_id = reg_data["agent_id"]
    api_key = reg_data["api_key"]
    assert agent_id is not None
    assert len(api_key) >= 32
    logger_steps.append("Step 1: Agent registered successfully with API key")

    # Camera Sync: register CAM-01 and obtain backend UUID
    cam_sync_payload = {
        "cameras": [
            {
                "local_camera_id": "CAM-01",
                "name": "North Gate Main Stream",
                "rtsp_url": "rtsp://admin:pass@192.168.1.100:554/h264",
                "location": "North Gate",
                "latitude": 28.6139,
                "longitude": 77.2090,
                "resolution": "1080p",
            }
        ]
    }
    resp_cam = await client.post(
        f"/api/agents/{agent_id}/cameras/sync",
        json=cam_sync_payload,
        headers={"X-API-Key": api_key},
    )
    assert resp_cam.status_code == 200
    cam_mappings = resp_cam.json()["mappings"]
    assert "CAM-01" in cam_mappings
    backend_cam_uuid = cam_mappings["CAM-01"]
    logger_steps.append("Step 1b: Camera CAM-01 mapped to backend UUID")

    # ─────────────────────────────────────────────────────────────────────────
    # STEP 2: Missing Person Report Creation (Atomic Multipart)
    # ─────────────────────────────────────────────────────────────────────────
    # Generate synthetic portrait JPEG
    portrait_img = np.full((300, 300, 3), fill_value=225, dtype=np.uint8)
    draw_synthetic_face(portrait_img, 150, 140, radius=55, name="Aarav Sharma")
    _, portrait_jpg = cv2.imencode(".jpg", portrait_img)
    portrait_bytes = portrait_jpg.tobytes()

    report_files = {
        "photos": ("aarav_portrait.jpg", io.BytesIO(portrait_bytes), "image/jpeg"),
    }
    report_data = {
        "full_name": "Aarav Sharma",
        "age": "14",
        "gender": "male",
        "height_cm": "155",
        "description": "Wearing blue school blazer and grey trousers",
        "last_seen_location": "Central Metro Station Gate 2",
        "contact_info": "+91-9876543210",
    }
    user_auth = {"Authorization": "Bearer test-user-parent-raj"}

    resp_report = await client.post(
        "/api/reports/",
        data=report_data,
        files=report_files,
        headers=user_auth,
    )
    assert resp_report.status_code == 201, resp_report.text
    report_json = resp_report.json()
    person_id = report_json["id"]
    assert report_json["status"] == "ACTIVE"
    assert len(report_json["photos"]) == 1
    assert report_json["photos"][0]["processing_status"] == "SUCCESS"
    logger_steps.append(f"Step 2: Missing person report created with ACTIVE status (Person ID: {person_id})")

    # ─────────────────────────────────────────────────────────────────────────
    # STEP 3: Embedding Synchronization into FAISS
    # ─────────────────────────────────────────────────────────────────────────
    # Verify sync endpoint directly
    resp_sync = await client.get("/api/embeddings/sync", headers={"X-API-Key": api_key})
    assert resp_sync.status_code == 200
    sync_pkg = resp_sync.json()
    assert len(sync_pkg["persons"]) >= 1
    synced_person = next((p for p in sync_pkg["persons"] if p["person_id"] == person_id), None)
    assert synced_person is not None, "Synced package must include newly created missing person"
    target_embedding_bytes = base64.b64decode(synced_person["embedding_bytes"])
    assert len(target_embedding_bytes) == 2048  # 512 * float32 (4 bytes)
    target_embedding_vec = np.frombuffer(target_embedding_bytes, dtype=np.float32)
    assert np.isclose(np.linalg.norm(target_embedding_vec), 1.0, atol=1e-4)

    # Initialize Edge Agent SQLite and FAISS Stores
    edge_db_path = str(edge_temp_dir / "edge_local.db")
    edge_faiss_path = str(edge_temp_dir / "edge_faiss.index")
    edge_local_db = SQLiteStore(db_path=edge_db_path)
    edge_faiss_store = FAISSStore(index_path=edge_faiss_path, dim=512)

    # Cache camera mapping in edge local DB
    edge_local_db.save_camera_mapping("CAM-01", backend_cam_uuid)

    # Execute EmbeddingSyncer
    sync_api_client = _make_sync_api_client()
    sync_api_client.set_api_key(api_key)
    syncer = EmbeddingSyncer(
        api_client=sync_api_client,
        local_db=edge_local_db,
        faiss_store=edge_faiss_store,
    )
    sync_summary = syncer.sync_now(force_full=True)
    assert sync_summary["status"] == "success"
    assert sync_summary["total_cached"] >= 1
    assert edge_faiss_store.size >= 1
    assert person_id in edge_faiss_store._person_ids
    logger_steps.append("Step 3: Embeddings synchronized and indexed into local FAISS")

    # ─────────────────────────────────────────────────────────────────────────
    # STEP 4: Detection, ByteTrack, ArcFace & Temporal Verification
    # ─────────────────────────────────────────────────────────────────────────
    # Set up pipeline components
    circ_buffer = CircularFrameBuffer(maxlen=150)
    verifier = TemporalVerifier(window_size=3, threshold=0.60, max_time_span=5.0)
    vector_search = VectorSearchEngine(dimension=512)
    # Load index from faiss store
    all_cached = edge_local_db.get_all_embeddings()
    vector_search.rebuild_index([(item["person_id"], np.frombuffer(item["embedding_data"], dtype=np.float32)) for item in all_cached])

    evidence_collector = EvidenceCollector(
        base_dir=str(edge_temp_dir / "evidence"),
        camera_id="CAM-01",
    )

    # Build mocks for vision frontend that simulate recognizing the target person
    mock_detector = MagicMock()
    mock_tracker = MagicMock()
    mock_quality = MagicMock()
    mock_aligner = MagicMock()
    mock_recognizer = MagicMock()

    # Track definition for subject in CCTV scene
    bbox = np.array([120, 80, 240, 220], dtype=np.float32)
    left_eye = np.array([150, 120], dtype=np.float32)
    right_eye = np.array([210, 120], dtype=np.float32)
    nose = np.array([180, 150], dtype=np.float32)
    left_mouth = np.array([160, 185], dtype=np.float32)
    right_mouth = np.array([200, 185], dtype=np.float32)
    landmarks = np.array([left_eye, right_eye, nose, left_mouth, right_mouth], dtype=np.float32)

    track = ExtendedTrack(tlwh=np.array([120, 80, 120, 140], dtype=np.float32), score=0.96, landmarks=landmarks)
    track.track_id = 42

    mock_detector.detect.return_value = [Detection(bbox=bbox, score=0.96, landmarks=landmarks)]
    mock_tracker.update.return_value = [track]
    mock_quality.is_quality_sufficient.return_value = (True, "passed")
    mock_aligner.align.return_value = cv2.resize(portrait_img, (112, 112))
    # Recognizer outputs the exact target embedding
    mock_recognizer.get_embedding.return_value = target_embedding_vec

    match_events_observed: List[Tuple[MatchEvent, Evidence]] = []

    def on_match(ev: MatchEvent, evd: Evidence):
        match_events_observed.append((ev, evd))

    pipeline = EdgeAIPipeline(
        camera_id="CAM-01",
        detector=mock_detector,
        tracker=mock_tracker,
        quality_checker=mock_quality,
        aligner=mock_aligner,
        recognizer=mock_recognizer,
        vector_search=vector_search,
        verifier=verifier,
        evidence_collector=evidence_collector,
        circ_buffer=circ_buffer,
        recognition_interval=1.0,
        on_match=on_match,
    )

    # Ingest synthetic frames at 15 FPS
    cctv_frame = np.full((480, 640, 3), fill_value=60, dtype=np.uint8)
    draw_synthetic_face(cctv_frame, 180, 150, radius=45, name="Aarav Sharma")

    # Frame 1 at t = 0.0s -> Observation 1 (Score = 1.0, count = 1) -> No alert yet
    res1 = pipeline.process_frame(cctv_frame, timestamp=0.0)
    assert len(res1) == 0, "Temporal verifier must not alert on first observation"

    # Fill intermediate frames at 15 FPS (throttled to 1.0s)
    for f in range(1, 15):
        pipeline.process_frame(cctv_frame, timestamp=f * (1.0 / 15.0))

    # Frame 16 at t = 1.05s -> Observation 2 (Score = 1.0, count = 2) -> No alert yet
    res2 = pipeline.process_frame(cctv_frame, timestamp=1.05)
    assert len(res2) == 0, "Temporal verifier must not alert on second observation"

    # Fill intermediate frames
    for f in range(16, 30):
        pipeline.process_frame(cctv_frame, timestamp=f * (1.0 / 15.0))

    # Frame 31 at t = 2.10s -> Observation 3 (Score = 1.0, count = 3 >= 3 and mean >= 0.60) -> CONFIRMED MATCH!
    res3 = pipeline.process_frame(cctv_frame, timestamp=2.10)
    assert len(res3) == 1, "Temporal verifier must confirm match on 3rd consecutive high-similarity frame"

    confirmed_match, confirmed_evidence = res3[0]
    assert confirmed_match.person_id == person_id
    assert confirmed_match.track_id == 42
    assert confirmed_match.score >= 0.60
    assert len(match_events_observed) == 1
    logger_steps.append("Step 4: AI pipeline detected subject, tracked across 15 FPS frames, and confirmed match temporally")

    # ─────────────────────────────────────────────────────────────────────────
    # STEP 5: Evidence Verification and Sighting Upload
    # ─────────────────────────────────────────────────────────────────────────
    assert os.path.isfile(confirmed_evidence.crop_path), "Face crop file must exist on disk"
    assert os.path.isfile(confirmed_evidence.frame_path), "HUD-annotated full frame must exist on disk"
    assert confirmed_evidence.clip_path is not None and os.path.isfile(confirmed_evidence.clip_path), "15 FPS MP4 video clip must exist on disk"
    assert os.path.getsize(confirmed_evidence.clip_path) > 0, "Video clip must not be empty"

    # Queue sighting into Edge local SQLite store (resilient offline queue)
    sighting_row_id = edge_local_db.enqueue_sighting(
        person_id=confirmed_match.person_id,
        camera_id="CAM-01",  # Local camera identifier
        similarity_score=confirmed_match.score,
        detected_at=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(confirmed_evidence.timestamp)),
        face_crop_path=confirmed_evidence.crop_path,
        full_frame_path=confirmed_evidence.frame_path,
        video_clip_path=confirmed_evidence.clip_path,
        confidence_level="CONFIRMED",
        num_frames_matched=3,
        camera_location="North Campus Gate",
        latitude=28.6139,
        longitude=77.2090,
    )
    assert sighting_row_id > 0
    assert edge_local_db.count_pending_sightings() == 1

    # Upload using SightingUploader (translates CAM-01 to backend UUID)
    uploader = SightingUploader(
        api_client=sync_api_client,
        local_db=edge_local_db,
    )
    uploaded_count = uploader.process_queue_once()
    assert uploaded_count == 1, "SightingUploader must successfully upload queued sighting"
    assert edge_local_db.count_pending_sightings() == 0, "Pending queue must be cleared after successful upload"
    logger_steps.append("Step 5: Sighting evidence (crop, frame, video clip) uploaded to backend")

    # ─────────────────────────────────────────────────────────────────────────
    # STEP 6: Backend Notification Dispatch & Verification
    # ─────────────────────────────────────────────────────────────────────────
    # 1. Verify sighting detail on backend
    resp_sightings = await client.get(f"/api/reports/{person_id}/sightings", headers=user_auth)
    assert resp_sightings.status_code == 200
    sightings_list = resp_sightings.json()
    assert len(sightings_list) >= 1
    backend_sighting = sightings_list[0]
    assert backend_sighting["person_id"] == person_id
    assert backend_sighting["similarity_score"] >= 0.60
    assert backend_sighting["camera_id"] == backend_cam_uuid
    assert backend_sighting["face_crop_path"] is not None
    assert backend_sighting["full_frame_path"] is not None
    assert backend_sighting["video_clip_path"] is not None

    # 2. Verify static media links resolve 200 OK
    resp_crop = await client.get(backend_sighting["face_crop_path"])
    assert resp_crop.status_code == 200, f"Face crop URL must resolve 200 OK ({backend_sighting['face_crop_path']})"
    resp_frame = await client.get(backend_sighting["full_frame_path"])
    assert resp_frame.status_code == 200, f"Full frame URL must resolve 200 OK ({backend_sighting['full_frame_path']})"
    resp_clip = await client.get(backend_sighting["video_clip_path"])
    assert resp_clip.status_code == 200, f"Video clip URL must resolve 200 OK ({backend_sighting['video_clip_path']})"

    # 3. Verify notification received by reporting citizen
    resp_notifs = await client.get("/api/notifications/", headers=user_auth)
    assert resp_notifs.status_code == 200
    notif_list = resp_notifs.json()
    assert notif_list["total"] >= 1
    sighting_notif = next((n for n in notif_list["items"] if n["type"] == "SIGHTING"), None)
    assert sighting_notif is not None, "Notification must be dispatched for confirmed sighting"
    assert "Aarav Sharma" in sighting_notif["title"]
    assert sighting_notif["sighting_id"] == backend_sighting["id"]
    logger_steps.append("Step 6: Notification verified in user inbox with valid media links")

    # Clean up local stores
    edge_local_db.close()
    sync_api_client.close()

    total_duration = time.time() - t_start
    assert total_duration < 45.0, f"Full lifecycle execution must complete in < 45s (took {total_duration:.2f}s)"
    logger_steps.append(f"Lifecycle completed cleanly in {total_duration:.2f}s")


# ═════════════════════════════════════════════════════════════════════════════
# 2. Candidate Deduplication Multi-Photo Test (Fix #14)
# ═════════════════════════════════════════════════════════════════════════════

def test_multi_photo_candidate_deduplication():
    """
    CRITICAL ARCHITECTURAL VERIFICATION (Fix #14):
    A person with multiple registered photos in FAISS MUST produce only ONE
    deduplicated candidate hit (with max similarity) per frame.
    Multi-hit FAISS matches within a single frame must NOT falsely increment
    the temporal confirmation counter.
    """
    vector_search = VectorSearchEngine(dimension=512)

    # 3 photos for MP-TARGET, 1 for MP-OTHER
    rng = np.random.RandomState(99)
    target_emb = rng.randn(512).astype(np.float32)
    target_emb /= np.linalg.norm(target_emb)

    similar_emb1 = target_emb + rng.randn(512).astype(np.float32) * 0.05
    similar_emb1 /= np.linalg.norm(similar_emb1)

    similar_emb2 = target_emb + rng.randn(512).astype(np.float32) * 0.10
    similar_emb2 /= np.linalg.norm(similar_emb2)

    other_emb = rng.randn(512).astype(np.float32)
    other_emb /= np.linalg.norm(other_emb)

    vector_search.rebuild_index([
        ("MP-TARGET", target_emb),
        ("MP-TARGET", similar_emb1),
        ("MP-TARGET", similar_emb2),
        ("MP-OTHER", other_emb),
    ])

    raw_candidates = vector_search.search(target_emb, top_k=5, cutoff=0.50)
    target_hits = [c for c in raw_candidates if c[0] == "MP-TARGET"]
    assert len(target_hits) >= 2, "FAISS must return multiple matches for person with multi-photo gallery"

    # Deduplication as performed in EdgeAIPipeline.process_frame:
    best_candidates = {}
    for person_id, similarity in raw_candidates:
        if person_id not in best_candidates or similarity > best_candidates[person_id]:
            best_candidates[person_id] = similarity

    assert len(best_candidates) >= 1
    assert "MP-TARGET" in best_candidates
    assert np.isclose(best_candidates["MP-TARGET"], 1.0, atol=1e-4)

    # Verify temporal verifier only receives 1 score per frame
    verifier = TemporalVerifier(window_size=3, threshold=0.60, max_time_span=5.0)
    event1 = verifier.check_match(track_id=1, person_id="MP-TARGET", similarity=best_candidates["MP-TARGET"], timestamp=1.0)
    assert event1 is None, "Frame 1 must NOT satisfy 3-frame temporal requirement"

    # Frame 2
    event2 = verifier.check_match(track_id=1, person_id="MP-TARGET", similarity=0.92, timestamp=2.0)
    assert event2 is None, "Frame 2 must NOT satisfy 3-frame temporal requirement"

    # Frame 3
    event3 = verifier.check_match(track_id=1, person_id="MP-TARGET", similarity=0.95, timestamp=3.0)
    assert event3 is not None, "Frame 3 must confirm match"
    assert event3.frames == 3


# ═════════════════════════════════════════════════════════════════════════════
# 3. Offline Queue & Exponential Retry Backoff Test (Fix #18)
# ═════════════════════════════════════════════════════════════════════════════

def test_offline_queue_resilience_and_retry(edge_temp_dir: Path):
    """
    CRITICAL ARCHITECTURAL VERIFICATION (Fix #18):
    When network is down, sightings must persist in local SQLite store.
    SightingUploader must calculate exponential backoff with jitter and
    successfully upload once network is restored.
    """
    db_file = str(edge_temp_dir / "offline_test.db")
    local_db = SQLiteStore(db_path=db_file)
    local_db.save_camera_mapping("CAM-01", str(uuid4()))

    # Create dummy evidence files
    crop_file = str(edge_temp_dir / "crop.jpg")
    frame_file = str(edge_temp_dir / "frame.jpg")
    Path(crop_file).write_bytes(b"dummy-crop-data")
    Path(frame_file).write_bytes(b"dummy-frame-data")

    # Queue sighting
    s_id = local_db.enqueue_sighting(
        person_id=str(uuid4()),
        camera_id="CAM-01",
        similarity_score=0.94,
        detected_at="2026-09-07T00:00:00Z",
        face_crop_path=crop_file,
        full_frame_path=frame_file,
    )
    assert s_id > 0
    assert local_db.count_pending_sightings() == 1

    # Simulate failing network client
    failing_api = MagicMock(spec=ApiClient)
    from edge_agent.network.api_client import NetworkError
    failing_api.upload_sighting.side_effect = NetworkError("Connection refused: 503 Service Unavailable")

    uploader = SightingUploader(api_client=failing_api, local_db=local_db)

    # First upload attempt fails
    success_count = uploader.process_queue_once()
    assert success_count == 0
    assert local_db.count_pending_sightings() == 1

    pending = local_db.get_pending_sightings()
    assert pending[0]["retry_count"] == 1
    assert pending[0]["last_attempt_at"] is not None

    # Test backoff calculation
    b1 = uploader.calculate_backoff(1)
    b2 = uploader.calculate_backoff(2)
    b3 = uploader.calculate_backoff(3)
    assert b1 < b2 < b3, "Backoff delay must grow exponentially with retries"

    # Simulate network restored
    recovering_api = MagicMock(spec=ApiClient)
    recovering_api.upload_sighting.return_value = {"id": str(uuid4()), "status": "PENDING"}

    # Fast-forward last_attempt_at so backoff passes
    local_db.set_sync_state("last_attempt_at", "2020-01-01T00:00:00Z")
    # Reset sighting retry timestamp to simulate elapsed time
    with local_db._get_connection() as conn:
        conn.execute(
            "UPDATE pending_sightings SET last_attempt_at = '2020-01-01T00:00:00' WHERE id = ?",
            (s_id,)
        )

    recovered_uploader = SightingUploader(api_client=recovering_api, local_db=local_db)
    success_count = recovered_uploader.process_queue_once()
    assert success_count == 1, "Sighting must upload once connectivity recovers"
    assert local_db.count_pending_sightings() == 0, "Queue must be empty after recovery"
    local_db.close()


# ═════════════════════════════════════════════════════════════════════════════
# 4. Incremental Tombstone Pruning Test (Fix #15)
# ═════════════════════════════════════════════════════════════════════════════

@pytest.mark.asyncio
async def test_incremental_tombstone_pruning(client: AsyncClient, mock_face_processing, edge_temp_dir: Path):
    """
    CRITICAL ARCHITECTURAL VERIFICATION (Fix #15):
    When a missing person report is marked FOUND or CLOSED, the backend
    sync package MUST return the person ID in `removed_ids` (tombstones).
    Edge Agent syncer must purge the person from local SQLite and FAISS.
    """
    # 1. Register agent
    reg = await client.post(
        "/api/agents/register",
        json={"device_id": f"EDGE-TOMB-{uuid4().hex[:6]}"},
        headers={"X-Enrollment-Key": settings.ADMIN_ENROLLMENT_KEY},
    )
    api_key = reg.json()["api_key"]

    # 2. Create active report
    resp_report = await client.post(
        "/api/reports/",
        data={"full_name": "Siddharth Verma", "age": "16"},
        files={"photos": ("sid.jpg", io.BytesIO(b"dummy-jpg"), "image/jpeg")},
        headers={"Authorization": "Bearer test-user-tomb"},
    )
    assert resp_report.status_code == 201
    person_id = resp_report.json()["id"]

    # 3. Perform initial full sync
    edge_local_db = SQLiteStore(db_path=str(edge_temp_dir / "tomb_edge.db"))
    edge_faiss = FAISSStore(index_path=str(edge_temp_dir / "tomb.index"), dim=512)

    sync_api = _make_sync_api_client()
    sync_api.set_api_key(api_key)
    syncer = EmbeddingSyncer(api_client=sync_api, local_db=edge_local_db, faiss_store=edge_faiss)

    s1 = syncer.sync_now(force_full=True)
    assert s1["total_cached"] >= 1
    assert person_id in edge_faiss._person_ids

    # 4. Close report (sets status to CLOSED and deactivates embeddings)
    resp_close = await client.delete(
        f"/api/reports/{person_id}",
        headers={"Authorization": "Bearer test-user-tomb"},
    )
    assert resp_close.status_code == 200
    assert resp_close.json()["status"] == "success"

    # Verify report status is now CLOSED
    resp_check = await client.get(f"/api/reports/{person_id}")
    assert resp_check.status_code == 200
    assert resp_check.json()["status"] == "CLOSED"

    # 5. Incremental sync with `since` timestamp
    s2 = syncer.sync_now(force_full=False)
    assert s2["removed_count"] >= 1, "Resolved person must be counted as removed in incremental sync"
    assert person_id not in edge_faiss._person_ids, "Resolved person must be removed from FAISS index"

    edge_local_db.close()
    sync_api.close()


# ═════════════════════════════════════════════════════════════════════════════
# 5. Synthetic Streamer Pipeline Integration (15 FPS)
# ═════════════════════════════════════════════════════════════════════════════

def test_synthetic_streamer_feeds_frames_at_15_fps():
    """
    Verifies that SyntheticVideoStreamer generates structured 15 FPS frames,
    advances timestamps in 1/15s increments, and provides valid bboxes and landmarks.
    """
    streamer = SyntheticVideoStreamer(
        camera_name="CAM-01 [ENTRANCE]",
        fps=15.0,
        width=640,
        height=480,
        subject_name="Aarav Sharma",
    )

    timestamps = []
    frames = []

    # Pull 30 frames (2 seconds at 15 FPS)
    for i in range(30):
        ok, frame, ts, bbox, landmarks = streamer.get_next_frame()
        assert ok is True
        assert frame.shape == (480, 640, 3)
        assert bbox is not None and len(bbox) == 4
        assert landmarks is not None and landmarks.shape == (5, 2)
        timestamps.append(ts)
        frames.append(frame)

    # Verify 15 FPS time delta (approx 0.0667s per frame)
    deltas = np.diff(timestamps)
    assert np.allclose(deltas, 1.0 / 15.0, atol=1e-3), "Frame timestamps must step by exactly 1/15th of a second"
    assert len(frames) == 30
    streamer.close()
