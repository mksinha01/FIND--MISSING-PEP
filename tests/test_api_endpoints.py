"""Comprehensive integration tests for Story 4 API endpoints, auth dependencies, and static mounts."""
import base64
import io
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import MagicMock
from uuid import UUID, uuid4

import numpy as np
import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.api.deps import get_db
from app.config import settings
from app.database import Base
from app.main import app
from app.models.camera import Camera
from app.models.face_embedding import FaceEmbedding
from app.models.user import User

# Central test database engine and session factory from conftest
try:
    from tests.conftest import test_engine as _engine, test_session_factory as _session_factory
except ImportError:
    from conftest import test_engine as _engine, test_session_factory as _session_factory


@pytest_asyncio.fixture
async def client():
    """Async HTTP test client bound to the FastAPI application."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://testserver") as ac:
        yield ac


@pytest.fixture
def mock_face_processing(monkeypatch):
    """Mocks face processing to return a deterministic 512-D ArcFace embedding."""
    import app.api.reports as reports_module

    def fake_process_person_photo(image_bytes: bytes):
        crop_bytes = b"fake-aligned-face-jpeg-bytes"
        # Generate normalized 512-D float32 vector
        vec = np.ones(512, dtype=np.float32)
        vec = vec / np.linalg.norm(vec)
        det_score = 0.95
        return crop_bytes, vec, det_score

    monkeypatch.setattr(reports_module, "process_person_photo", fake_process_person_photo)


# ============================================================================
# 1. Health & Static File Mounts
# ============================================================================

@pytest.mark.asyncio
async def test_health_check(client: AsyncClient):
    """Verify /health returns 200 OK with version info."""
    response = await client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["version"] == "1.0.0"


@pytest.mark.asyncio
async def test_static_file_mount(client: AsyncClient):
    """Verify StaticFiles mounted at /uploads serves files from settings.UPLOAD_DIR."""
    upload_dir = Path(settings.UPLOAD_DIR)
    upload_dir.mkdir(parents=True, exist_ok=True)
    test_file = upload_dir / "static_test.txt"
    test_file.write_bytes(b"hello-static-cctv-evidence")

    response = await client.get("/uploads/static_test.txt")
    assert response.status_code == 200
    assert response.content == b"hello-static-cctv-evidence"

    # Cleanup
    if test_file.exists():
        test_file.unlink()


# ============================================================================
# 2. Edge Agent Registration & Security Dependencies
# ============================================================================

@pytest.mark.asyncio
async def test_agent_registration_security(client: AsyncClient):
    """Verify POST /api/agents/register requires valid X-Enrollment-Key."""
    payload = {
        "device_id": "EDGE-TEST-001",
        "name": "Gate-01-Agent",
        "location": "North Campus Gate",
        "os_info": "Ubuntu 22.04 LTS",
        "version": "1.0.0",
    }

    # 1. Missing header -> 403 Forbidden
    resp_missing = await client.post("/api/agents/register", json=payload)
    assert resp_missing.status_code == 403

    # 2. Invalid header -> 403 Forbidden
    resp_wrong = await client.post(
        "/api/agents/register",
        json=payload,
        headers={"X-Enrollment-Key": "wrong-secret-key"},
    )
    assert resp_wrong.status_code == 403
    assert "Invalid enrollment key" in resp_wrong.json()["detail"]

    # 3. Valid header -> 201 Created with plain-text API key
    resp_ok = await client.post(
        "/api/agents/register",
        json=payload,
        headers={"X-Enrollment-Key": settings.ADMIN_ENROLLMENT_KEY},
    )
    assert resp_ok.status_code == 201
    reg_data = resp_ok.json()
    assert "agent_id" in reg_data
    assert "api_key" in reg_data
    assert len(reg_data["api_key"]) > 30


@pytest.mark.asyncio
async def test_agent_heartbeat(client: AsyncClient):
    """Verify heartbeat updates agent status and requires X-API-Key."""
    # Register agent first
    reg_resp = await client.post(
        "/api/agents/register",
        json={"device_id": "EDGE-HB-01", "name": "HB Agent"},
        headers={"X-Enrollment-Key": settings.ADMIN_ENROLLMENT_KEY},
    )
    agent_info = reg_resp.json()
    agent_id = agent_info["agent_id"]
    api_key = agent_info["api_key"]

    # Invalid API key -> 401
    hb_bad = await client.post(
        f"/api/agents/{agent_id}/heartbeat",
        json={"status": "ONLINE", "camera_count": 4},
        headers={"X-API-Key": "invalid-key"},
    )
    assert hb_bad.status_code == 401

    # Valid API key -> 200 OK
    hb_good = await client.post(
        f"/api/agents/{agent_id}/heartbeat",
        json={"status": "ONLINE", "camera_count": 3, "version": "1.1.0"},
        headers={"X-API-Key": api_key},
    )
    assert hb_good.status_code == 200
    assert hb_good.json()["status"] == "ONLINE"
    assert hb_good.json()["camera_count"] == 3


# ============================================================================
# 3. Camera Synchronization & RTSP Encryption
# ============================================================================

@pytest.mark.asyncio
async def test_camera_sync_and_encryption(client: AsyncClient):
    """
    Verify POST /api/agents/{id}/cameras/sync returns local ID -> backend UUID mappings
    and stores encrypted RTSP URLs at rest.
    """
    # 1. Register agent
    reg = await client.post(
        "/api/agents/register",
        json={"device_id": "EDGE-CAM-01", "name": "CCTV Hub"},
        headers={"X-Enrollment-Key": settings.ADMIN_ENROLLMENT_KEY},
    )
    agent_id = reg.json()["agent_id"]
    api_key = reg.json()["api_key"]

    # 2. Sync cameras
    sync_payload = {
        "cameras": [
            {
                "local_camera_id": "CAM-01",
                "name": "Main Entrance",
                "rtsp_url": "rtsp://admin:supersecret@192.168.1.100:554/ch0",
                "location": "Lobby",
                "latitude": 28.6139,
                "longitude": 77.2090,
                "resolution": "1080p",
            },
            {
                "local_camera_id": "CAM-02",
                "name": "Parking Gate",
                "rtsp_url": "rtsp://admin:supersecret@192.168.1.101:554/ch0",
                "location": "Basement",
                "latitude": 28.6140,
                "longitude": 77.2092,
                "resolution": "720p",
            },
        ]
    }

    resp = await client.post(
        f"/api/agents/{agent_id}/cameras/sync",
        json=sync_payload,
        headers={"X-API-Key": api_key},
    )
    assert resp.status_code == 200
    mappings = resp.json()["mappings"]
    assert "CAM-01" in mappings
    assert "CAM-02" in mappings

    cam1_uuid = mappings["CAM-01"]
    cam2_uuid = mappings["CAM-02"]
    assert cam1_uuid != cam2_uuid

    # 3. Verify in DB that RTSP URL is encrypted at rest
    async with _session_factory() as session:
        cam_record = await session.get(Camera, UUID(cam1_uuid))
        assert cam_record is not None
        assert cam_record.local_camera_id == "CAM-01"
        assert "supersecret" not in cam_record.encrypted_rtsp_url
        assert cam_record.encrypted_rtsp_url != sync_payload["cameras"][0]["rtsp_url"]

    # 4. Re-syncing same local cameras updates records and returns same UUIDs (idempotent)
    resp_repeat = await client.post(
        f"/api/agents/{agent_id}/cameras/sync",
        json=sync_payload,
        headers={"X-API-Key": api_key},
    )
    assert resp_repeat.status_code == 200
    repeat_mappings = resp_repeat.json()["mappings"]
    assert repeat_mappings["CAM-01"] == cam1_uuid


# ============================================================================
# 4. Atomic Multipart Report Creation & Face Processing
# ============================================================================

@pytest.mark.asyncio
async def test_atomic_multipart_report_creation(client: AsyncClient, mock_face_processing):
    """
    Verify POST /api/reports/ creates report atomically with metadata and photo,
    triggers face processing, creates FaceEmbedding, and sets status=ACTIVE.
    """
    headers = {"Authorization": "Bearer test-user-raj"}

    dummy_image = io.BytesIO(b"fake-image-bytes-jpeg")
    files = {
        "photos": ("person_photo.jpg", dummy_image, "image/jpeg"),
    }
    data = {
        "full_name": "Aarav Sharma",
        "age": "14",
        "gender": "male",
        "height_cm": "155",
        "description": "Wearing blue school uniform",
        "last_seen_location": "Metro Station Gate 2",
        "contact_info": "+91-9876543210",
    }

    response = await client.post(
        "/api/reports/",
        data=data,
        files=files,
        headers=headers,
    )
    assert response.status_code == 201, response.text
    report_json = response.json()

    assert report_json["full_name"] == "Aarav Sharma"
    assert report_json["status"] == "ACTIVE"
    assert len(report_json["photos"]) == 1
    photo = report_json["photos"][0]
    assert photo["processing_status"] == "SUCCESS"
    assert photo["face_crop_path"] is not None

    # Verify FaceEmbedding record in DB
    report_id = UUID(report_json["id"])
    async with _session_factory() as session:
        from sqlalchemy import select
        res = await session.execute(
            select(FaceEmbedding).where(FaceEmbedding.person_id == report_id)
        )
        embedding_record = res.scalar_one_or_none()
        assert embedding_record is not None
        assert len(embedding_record.embedding) == 2048  # 512 float32 * 4 bytes
        assert embedding_record.is_active is True


@pytest.mark.asyncio
async def test_report_listing_and_detail(client: AsyncClient, mock_face_processing):
    """Verify GET /api/reports/ and GET /api/reports/{id}."""
    headers = {"Authorization": "Bearer test-user-reporter"}

    # Create report
    create_resp = await client.post(
        "/api/reports/",
        data={"full_name": "Priya Verma", "age": "22"},
        files={"photos": ("priya.jpg", io.BytesIO(b"image-data"), "image/jpeg")},
        headers=headers,
    )
    report_id = create_resp.json()["id"]

    # List reports
    list_resp = await client.get("/api/reports/", headers=headers)
    assert list_resp.status_code == 200
    items = list_resp.json()["items"]
    assert len(items) >= 1
    assert any(item["id"] == report_id for item in items)

    # Get report detail
    detail_resp = await client.get(f"/api/reports/{report_id}")
    assert detail_resp.status_code == 200
    assert detail_resp.json()["full_name"] == "Priya Verma"


# ============================================================================
# 5. Embeddings Sync Endpoint (Full & Incremental Tombstone)
# ============================================================================

@pytest.mark.asyncio
async def test_embeddings_sync(client: AsyncClient, mock_face_processing):
    """Verify GET /api/embeddings/sync returns base64 embeddings for Edge Agents."""
    # 1. Register agent
    reg = await client.post(
        "/api/agents/register",
        json={"device_id": "EDGE-SYNC-01", "name": "Sync Agent"},
        headers={"X-Enrollment-Key": settings.ADMIN_ENROLLMENT_KEY},
    )
    api_key = reg.json()["api_key"]

    # 2. Create an active report with face embedding
    user_headers = {"Authorization": "Bearer test-reporter-1"}
    await client.post(
        "/api/reports/",
        data={"full_name": "Deepak Patel"},
        files={"photos": ("deepak.jpg", io.BytesIO(b"img"), "image/jpeg")},
        headers=user_headers,
    )

    # 3. Call sync endpoint
    sync_resp = await client.get(
        "/api/embeddings/sync",
        headers={"X-API-Key": api_key},
    )
    assert sync_resp.status_code == 200
    sync_data = sync_resp.json()
    assert sync_data["full_sync"] is True
    assert len(sync_data["persons"]) >= 1

    person_item = sync_data["persons"][0]
    assert person_item["person_name"] == "Deepak Patel"

    # Decode base64 vector -> check length 2048 bytes
    raw_vec = base64.b64decode(person_item["embedding_bytes"])
    assert len(raw_vec) == 2048


# ============================================================================
# 6. Sighting Ingestion & Review Workflow
# ============================================================================

@pytest.mark.asyncio
async def test_sighting_workflow(client: AsyncClient, mock_face_processing):
    """
    Verify POST /api/sightings/ ingests multipart evidence files,
    creates sighting, and allows operator confirm/reject.
    """
    # 1. Setup agent & camera
    reg = await client.post(
        "/api/agents/register",
        json={"device_id": "EDGE-SIGHT-01", "name": "Sighting Agent"},
        headers={"X-Enrollment-Key": settings.ADMIN_ENROLLMENT_KEY},
    )
    agent_id = reg.json()["agent_id"]
    api_key = reg.json()["api_key"]

    sync_cam = await client.post(
        f"/api/agents/{agent_id}/cameras/sync",
        json={"cameras": [{"local_camera_id": "CAM-09", "name": "Gate 9", "rtsp_url": "rtsp://cam/0"}]},
        headers={"X-API-Key": api_key},
    )
    camera_id = sync_cam.json()["mappings"]["CAM-09"]

    # 2. Create missing person
    user_headers = {"Authorization": "Bearer test-sighting-user"}
    rep_resp = await client.post(
        "/api/reports/",
        data={"full_name": "Sunita Devi"},
        files={"photos": ("sunita.jpg", io.BytesIO(b"sunita-face"), "image/jpeg")},
        headers=user_headers,
    )
    person_id = rep_resp.json()["id"]

    # 3. Report sighting with multipart evidence
    sight_data = {
        "person_id": person_id,
        "camera_id": camera_id,
        "similarity_score": "0.92",
        "detected_at": datetime.now(timezone.utc).isoformat(),
        "camera_location": "Gate 9 Exit",
        "latitude": "28.5355",
        "longitude": "77.3910",
    }
    files = {
        "face_crop": ("crop.jpg", io.BytesIO(b"crop-bytes"), "image/jpeg"),
        "full_frame": ("frame.jpg", io.BytesIO(b"full-frame-bytes"), "image/jpeg"),
    }

    sight_resp = await client.post(
        "/api/sightings/",
        data=sight_data,
        files=files,
        headers={"X-API-Key": api_key},
    )
    assert sight_resp.status_code == 201, sight_resp.text
    sighting = sight_resp.json()
    sighting_id = sighting["id"]
    assert sighting["status"] == "PENDING"
    assert sighting["similarity_score"] == 0.92
    assert sighting["face_crop_path"] is not None

    # 4. Check person timeline
    timeline_resp = await client.get(f"/api/reports/{person_id}/timeline")
    assert timeline_resp.status_code == 200
    timeline = timeline_resp.json()
    assert len(timeline["entries"]) == 1
    assert timeline["entries"][0]["camera_name"] == "Gate 9"

    # 5. Operator confirms sighting
    confirm_resp = await client.put(
        f"/api/sightings/{sighting_id}/confirm",
        json={"status": "CONFIRMED", "review_notes": "Facial match verified on CCTV"},
        headers=user_headers,
    )
    assert confirm_resp.status_code == 200
    assert confirm_resp.json()["status"] == "CONFIRMED"


# ============================================================================
# 7. Notifications & User Endpoints
# ============================================================================

@pytest.mark.asyncio
async def test_notifications_and_user_profile(client: AsyncClient):
    """Verify user profile management and notification listing/read endpoints."""
    headers = {"Authorization": "Bearer test-user-notifications"}

    # 1. Get initial profile
    me_resp = await client.get("/api/users/me", headers=headers)
    assert me_resp.status_code == 200
    assert me_resp.json()["language"] == "en"

    # 2. Update profile
    update_resp = await client.put(
        "/api/users/me",
        json={"name": "Karan Malhotra", "language": "hi"},
        headers=headers,
    )
    assert update_resp.status_code == 200
    assert update_resp.json()["name"] == "Karan Malhotra"
    assert update_resp.json()["language"] == "hi"

    # 3. Check notifications
    notif_resp = await client.get("/api/notifications/", headers=headers)
    assert notif_resp.status_code == 200
    notif_data = notif_resp.json()
    assert "items" in notif_data
    assert "unread_count" in notif_data

    # 4. Mark all read
    read_all = await client.put("/api/notifications/read-all", headers=headers)
    assert read_all.status_code == 200


# ============================================================================
# 8. Auth, Unauthenticated & Error Handling Tests
# ============================================================================

@pytest.mark.asyncio
async def test_auth_verify_and_me(client: AsyncClient):
    """Verify POST /api/auth/verify and GET /api/auth/me."""
    headers = {"Authorization": "Bearer test-auth-user"}

    # Verify token
    resp_verify = await client.post("/api/auth/verify", headers=headers)
    assert resp_verify.status_code == 200
    user_data = resp_verify.json()
    assert user_data["firebase_uid"] == "uid_test-auth-user"

    # Get authenticated user profile
    resp_me = await client.get("/api/auth/me", headers=headers)
    assert resp_me.status_code == 200
    assert resp_me.json()["id"] == user_data["id"]

    # Unauthenticated request -> 401
    resp_unauth = await client.get("/api/auth/me")
    assert resp_unauth.status_code == 401


@pytest.mark.asyncio
async def test_report_validation_and_photo_addition(client: AsyncClient, mock_face_processing):
    """Verify report input validation errors and photo upload to existing report."""
    headers = {"Authorization": "Bearer test-photo-user"}

    # 1. Invalid JSON in report_data -> 422
    resp_bad_json = await client.post(
        "/api/reports/",
        data={"report_data": "invalid-json-string"},
        headers=headers,
    )
    assert resp_bad_json.status_code == 422

    # 2. Missing metadata -> 422
    resp_empty = await client.post(
        "/api/reports/",
        data={},
        headers=headers,
    )
    assert resp_empty.status_code == 422

    # 3. Create initial report
    resp_create = await client.post(
        "/api/reports/",
        data={"full_name": "Rohan Gupta", "age": "16"},
        headers=headers,
    )
    assert resp_create.status_code == 201
    report_id = resp_create.json()["id"]

    # 4. Upload photo to existing report
    photo_file = ("rohan.jpg", io.BytesIO(b"rohan-photo-bytes"), "image/jpeg")
    resp_photo = await client.post(
        f"/api/reports/{report_id}/photos",
        files={"file": photo_file},
        data={"is_primary": "true"},
        headers=headers,
    )
    assert resp_photo.status_code == 200
    photo_data = resp_photo.json()
    assert photo_data["processing_status"] == "SUCCESS"
    assert photo_data["is_primary"] is True


@pytest.mark.asyncio
async def test_close_report_and_incremental_tombstones(client: AsyncClient, mock_face_processing):
    """Verify DELETE /api/reports/{id} closes report and emits tombstone in GET /api/embeddings/sync."""
    # 1. Register agent
    reg = await client.post(
        "/api/agents/register",
        json={"device_id": "EDGE-TOMB-01", "name": "Tombstone Agent"},
        headers={"X-Enrollment-Key": settings.ADMIN_ENROLLMENT_KEY},
    )
    api_key = reg.json()["api_key"]

    # 2. Create report with photo
    user_headers = {"Authorization": "Bearer test-tombstone-user"}
    create_resp = await client.post(
        "/api/reports/",
        data={"full_name": "Meera Kapoor"},
        files={"photos": ("meera.jpg", io.BytesIO(b"meera-bytes"), "image/jpeg")},
        headers=user_headers,
    )
    report_id = create_resp.json()["id"]

    # Full sync contains Meera
    sync1 = await client.get("/api/embeddings/sync", headers={"X-API-Key": api_key})
    assert sync1.status_code == 200
    assert any(p["person_id"] == report_id for p in sync1.json()["persons"])

    since_time = datetime.now(timezone.utc).isoformat()

    # 3. Close report (DELETE)
    del_resp = await client.delete(f"/api/reports/{report_id}", headers=user_headers)
    assert del_resp.status_code == 200

    # 4. Incremental sync should now return Meera's ID in removed_ids
    sync2 = await client.get(
        f"/api/embeddings/sync?since={since_time}",
        headers={"X-API-Key": api_key},
    )
    assert sync2.status_code == 200
    sync2_data = sync2.json()
    assert sync2_data["full_sync"] is False
    assert report_id in sync2_data["removed_ids"]


@pytest.mark.asyncio
async def test_sighting_reject_and_camera_management(client: AsyncClient, mock_face_processing):
    """Verify operator sighting rejection and camera CRUD endpoints."""
    # 1. Register agent
    reg = await client.post(
        "/api/agents/register",
        json={"device_id": "EDGE-REJ-01", "name": "Rejection Agent"},
        headers={"X-Enrollment-Key": settings.ADMIN_ENROLLMENT_KEY},
    )
    agent_id = reg.json()["agent_id"]
    api_key = reg.json()["api_key"]

    # 2. Register single camera via POST /api/cameras/
    cam_resp = await client.post(
        "/api/cameras/",
        json={
            "name": "North Gate Bullet Cam",
            "local_camera_id": "CAM-NORTH",
            "rtsp_url": "rtsp://admin:pass@192.168.1.50:554/live",
            "location": "North Gate",
        },
        headers={"X-API-Key": api_key},
    )
    assert cam_resp.status_code == 201
    camera_id = cam_resp.json()["id"]

    # List cameras
    cams_list = await client.get(f"/api/cameras/?agent_id={agent_id}")
    assert cams_list.status_code == 200
    assert len(cams_list.json()) >= 1

    # Get single camera
    cam_detail = await client.get(f"/api/cameras/{camera_id}")
    assert cam_detail.status_code == 200
    assert cam_detail.json()["name"] == "North Gate Bullet Cam"

    # 3. Create missing person & sighting
    user_headers = {"Authorization": "Bearer test-reject-user"}
    rep = await client.post(
        "/api/reports/",
        data={"full_name": "Anil Saxena"},
        files={"photos": ("anil.jpg", io.BytesIO(b"anil"), "image/jpeg")},
        headers=user_headers,
    )
    person_id = rep.json()["id"]

    sight = await client.post(
        "/api/sightings/",
        data={
            "person_id": person_id,
            "camera_id": camera_id,
            "similarity_score": "0.72",
            "detected_at": datetime.now(timezone.utc).isoformat(),
        },
        files={
            "face_crop": ("crop.jpg", io.BytesIO(b"crop"), "image/jpeg"),
            "full_frame": ("frame.jpg", io.BytesIO(b"frame"), "image/jpeg"),
        },
        headers={"X-API-Key": api_key},
    )
    sighting_id = sight.json()["id"]

    # 4. Reject sighting
    rej_resp = await client.put(
        f"/api/sightings/{sighting_id}/reject",
        json={"status": "REJECTED", "review_notes": "False match on CCTV"},
        headers=user_headers,
    )
    assert rej_resp.status_code == 200
    assert rej_resp.json()["status"] == "REJECTED"

    # 5. List and get edge agent detail
    agents_list = await client.get("/api/agents/")
    assert agents_list.status_code == 200
    assert len(agents_list.json()) >= 1

    agent_detail = await client.get(f"/api/agents/{agent_id}")
    assert agent_detail.status_code == 200
    assert agent_detail.json()["device_id"] == "EDGE-REJ-01"

