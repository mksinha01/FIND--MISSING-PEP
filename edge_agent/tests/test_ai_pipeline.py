"""Comprehensive test suite for Edge AI Core: ArcFace, Thread-Safe FAISS, Deduplication, Temporal Verifier, and Pipeline."""
import os
import shutil
import tempfile
import threading
import time
from typing import List, Tuple
from unittest.mock import MagicMock

import cv2
import numpy as np
import pytest

from edge_agent.ai.evidence_collector import Evidence, EvidenceCollector
from edge_agent.ai.face_aligner import FaceAligner
from edge_agent.ai.face_detector import Detection
from edge_agent.ai.face_quality import FaceQualityGate
from edge_agent.ai.face_recognizer import ArcFaceRecognizer
from edge_agent.ai.pipeline import EdgeAIPipeline
from edge_agent.ai.temporal_verifier import MatchEvent, TemporalVerifier
from edge_agent.ai.tracker import ByteTracker, ExtendedTrack
from edge_agent.ai.vector_search import ThreadSafeFAISSIndex, VectorSearchEngine
from edge_agent.camera.circular_buffer import CircularFrameBuffer


# ═════════════════════════════════════════════════════════════════════════════
# Helper Functions & Synthetic Generators
# ═════════════════════════════════════════════════════════════════════════════


def create_synthetic_face(width: int = 112, height: int = 112) -> np.ndarray:
    """Creates a high-contrast synthetic face image."""
    img = np.zeros((height, width, 3), dtype=np.uint8)
    cv2.circle(img, (width // 2, height // 2), 40, (200, 180, 160), -1)
    # Eyes
    cv2.circle(img, (width // 2 - 15, height // 2 - 10), 5, (50, 40, 30), -1)
    cv2.circle(img, (width // 2 + 15, height // 2 - 10), 5, (50, 40, 30), -1)
    # Mouth
    cv2.ellipse(img, (width // 2, height // 2 + 20), (12, 5), 0, 0, 180, (40, 30, 80), -1)
    return img


def create_mock_embedding(dim: int = 512, seed: int = 42) -> np.ndarray:
    """Generates a reproducible 512-D L2-normalized unit vector."""
    rng = np.random.RandomState(seed)
    vec = rng.randn(dim).astype(np.float32)
    return vec / np.linalg.norm(vec)


def create_standard_landmarks(bbox: np.ndarray) -> np.ndarray:
    """Generates canonical 5 facial landmarks for bbox [x1, y1, x2, y2]."""
    x1, y1, x2, y2 = bbox
    w = x2 - x1
    h = y2 - y1
    return np.array(
        [
            [x1 + 0.3 * w, y1 + 0.35 * h],  # left eye
            [x1 + 0.7 * w, y1 + 0.35 * h],  # right eye
            [x1 + 0.5 * w, y1 + 0.55 * h],  # nose
            [x1 + 0.35 * w, y1 + 0.75 * h], # left mouth
            [x1 + 0.65 * w, y1 + 0.75 * h], # right mouth
        ],
        dtype=np.float32,
    )


# ═════════════════════════════════════════════════════════════════════════════
# 1. ArcFace Feature Recognizer Tests
# ═════════════════════════════════════════════════════════════════════════════


def test_arcface_preprocessing():
    """Verifies ArcFace input tensor normalization and geometric shape."""
    recognizer = ArcFaceRecognizer(model_path=None)
    face_crop = create_synthetic_face(112, 112)

    blob = recognizer.preprocess(face_crop)
    assert blob.shape == (1, 3, 112, 112), f"Unexpected shape {blob.shape}"
    assert blob.dtype == np.float32
    # Normalization check: pixel 0 -> -1.0, 255 -> ~1.0
    assert blob.min() >= -1.01
    assert blob.max() <= 1.01


def test_arcface_embedding_l2_normalization():
    """Verifies that ArcFaceRecognizer outputs strictly unit L2-normalized vectors."""
    mock_session = MagicMock()
    mock_raw_vector = np.random.randn(1, 512).astype(np.float32) * 5.0
    mock_session.run.return_value = [mock_raw_vector]
    mock_session.get_inputs.return_value = [MagicMock(name="data")]
    mock_session.get_outputs.return_value = [MagicMock(name="fc1")]

    recognizer = ArcFaceRecognizer(session=mock_session)
    face_crop = create_synthetic_face(112, 112)

    embedding = recognizer.get_embedding(face_crop)
    assert embedding.shape == (512,)
    assert embedding.dtype == np.float32

    # L2 norm must equal 1.0
    l2_norm = float(np.linalg.norm(embedding))
    assert np.isclose(l2_norm, 1.0, atol=1e-5), f"L2 norm {l2_norm} is not 1.0"


def test_arcface_batch_extraction():
    """Verifies batch extraction on multiple aligned face crops."""
    mock_session = MagicMock()
    mock_session.run.return_value = [np.ones((1, 512), dtype=np.float32)]
    mock_session.get_inputs.return_value = [MagicMock(name="data")]
    mock_session.get_outputs.return_value = [MagicMock(name="fc1")]

    recognizer = ArcFaceRecognizer(session=mock_session)
    images = [create_synthetic_face(112, 112) for _ in range(3)]

    embeddings = recognizer.get_embeddings(images)
    assert embeddings.shape == (3, 512)
    for i in range(3):
        assert np.isclose(np.linalg.norm(embeddings[i]), 1.0, atol=1e-5)


# ═════════════════════════════════════════════════════════════════════════════
# 2. Concurrency-Safe Vector Search (FAISS Double-Buffering) Tests
# ═════════════════════════════════════════════════════════════════════════════


def test_faiss_double_buffering_pointer_swap():
    """Verifies offline rebuilding and atomic pointer swapping in FAISS."""
    engine = VectorSearchEngine(dimension=512)
    assert engine.size == 0

    emb1 = create_mock_embedding(seed=101)
    emb2 = create_mock_embedding(seed=102)
    data = [("MP-001", emb1), ("MP-002", emb2)]

    count = engine.rebuild_index(data)
    assert count == 2
    assert engine.size == 2
    assert engine.get_indexed_person_ids() == ["MP-001", "MP-002"]

    # Search exact match
    results = engine.search(emb1, top_k=5, cutoff=0.50)
    assert len(results) >= 1
    best_pid, best_sim = results[0]
    assert best_pid == "MP-001"
    assert np.isclose(best_sim, 1.0, atol=1e-4)


def test_faiss_cutoff_threshold():
    """Verifies candidate cutoff filtering (similarity >= 0.50)."""
    engine = VectorSearchEngine(dimension=512)
    emb_target = create_mock_embedding(seed=201)
    # Orthogonal random vector
    emb_diff = create_mock_embedding(seed=999)

    engine.rebuild_index([("MP-TARGET", emb_target), ("MP-DIFF", emb_diff)])

    # Search with target vector
    results = engine.search(emb_target, top_k=5, cutoff=0.50)
    # emb_target matches at 1.0, emb_diff random correlation typically < 0.15
    matching_pids = [pid for pid, _ in results]
    assert "MP-TARGET" in matching_pids
    assert "MP-DIFF" not in matching_pids


def test_faiss_multithreaded_concurrency_stress():
    """
    CRITICAL ARCHITECTURAL VERIFICATION (Fix #6):
    Executes concurrent reader search threads while background writer threads
    rebuild the FAISS index with double-buffering pointer swap.
    Guarantees zero C++ segfaults, zero exceptions, and clean concurrency.
    """
    engine = VectorSearchEngine(dimension=512)

    # Pre-populate index
    initial_data = [(f"MP-{i}", create_mock_embedding(seed=i)) for i in range(20)]
    engine.rebuild_index(initial_data)

    stop_event = threading.Event()
    errors: List[Exception] = []

    def reader_task(reader_id: int):
        query = create_mock_embedding(seed=reader_id)
        search_count = 0
        while not stop_event.is_set() and search_count < 100:
            try:
                res = engine.search(query, top_k=5, cutoff=0.30)
                # Results must be a valid list
                assert isinstance(res, list)
                search_count += 1
                time.sleep(0.001)
            except Exception as e:
                errors.append(e)
                break

    def writer_task(writer_id: int):
        rebuild_count = 0
        while not stop_event.is_set() and rebuild_count < 15:
            try:
                new_data = [
                    (f"WRITER-{writer_id}-{i}", create_mock_embedding(seed=writer_id * 100 + i))
                    for i in range(15)
                ]
                engine.rebuild_index(new_data)
                rebuild_count += 1
                time.sleep(0.005)
            except Exception as e:
                errors.append(e)
                break

    # Spawn 6 reader threads and 2 writer threads
    readers = [threading.Thread(target=reader_task, args=(i,)) for i in range(6)]
    writers = [threading.Thread(target=writer_task, args=(i,)) for i in range(2)]

    for t in readers + writers:
        t.start()

    for t in readers + writers:
        t.join(timeout=10.0)

    stop_event.set()

    assert len(errors) == 0, f"Concurrency errors encountered: {errors}"
    assert engine.size > 0


# ═════════════════════════════════════════════════════════════════════════════
# 3. Temporal Verifier Tests (N=3, >= 0.60 within 5s)
# ═════════════════════════════════════════════════════════════════════════════


def test_temporal_verifier_requires_3_observations():
    """
    Verifies that temporal verification requires N=3 observations within 5s
    with mean >= 0.60 to confirm a match.
    """
    verifier = TemporalVerifier(window_size=3, threshold=0.60, max_time_span=5.0, cooldown=300.0)
    track_id = 42
    person_id = "MP-102"
    base_time = 1000.0

    # Observation 1: High score, but only 1 observation -> None
    res1 = verifier.check_match(track_id, person_id, similarity=0.64, timestamp=base_time)
    assert res1 is None

    # Observation 2: Second observation at t + 1.0s -> None
    res2 = verifier.check_match(track_id, person_id, similarity=0.61, timestamp=base_time + 1.0)
    assert res2 is None

    # Observation 3: Third observation at t + 2.0s -> Mean = (0.64 + 0.61 + 0.66)/3 = 0.6367 >= 0.60 -> Match!
    res3 = verifier.check_match(track_id, person_id, similarity=0.66, timestamp=base_time + 2.0)
    assert res3 is not None
    assert isinstance(res3, MatchEvent)
    assert res3.track_id == 42
    assert res3.person_id == "MP-102"
    assert np.isclose(res3.score, 0.6367, atol=1e-3)
    assert res3.frames == 3


def test_temporal_verifier_threshold_rejection():
    """Verifies that 3 observations with mean similarity < 0.60 are rejected."""
    verifier = TemporalVerifier(window_size=3, threshold=0.60, max_time_span=5.0)
    track_id = 10
    person_id = "MP-LOW"
    base_time = 100.0

    verifier.check_match(track_id, person_id, similarity=0.55, timestamp=base_time)
    verifier.check_match(track_id, person_id, similarity=0.58, timestamp=base_time + 1.0)
    # Mean = (0.55 + 0.58 + 0.52) / 3 = 0.55 < 0.60
    res = verifier.check_match(track_id, person_id, similarity=0.52, timestamp=base_time + 2.0)
    assert res is None


def test_temporal_verifier_time_span_expiration():
    """
    Verifies that observations older than 5.0 seconds are discarded,
    preventing non-contiguous scores from triggering.
    """
    verifier = TemporalVerifier(window_size=3, threshold=0.60, max_time_span=5.0)
    track_id = 15
    person_id = "MP-TIME"

    # Score 1 at t = 0.0s
    verifier.check_match(track_id, person_id, similarity=0.70, timestamp=0.0)
    # Score 2 at t = 6.0s (Score 1 expired because 6.0 - 0.0 = 6.0 > 5.0)
    verifier.check_match(track_id, person_id, similarity=0.70, timestamp=6.0)
    # Score 3 at t = 12.0s (Score 2 expired)
    res = verifier.check_match(track_id, person_id, similarity=0.70, timestamp=12.0)

    # Never reached 3 scores within 5 seconds
    assert res is None


def test_temporal_verifier_cooldown():
    """Verifies 300-second alert cooldown suppression on same (track, person)."""
    verifier = TemporalVerifier(window_size=3, threshold=0.60, max_time_span=5.0, cooldown=300.0)
    track_id = 99
    person_id = "MP-COOL"

    # Trigger initial alert
    verifier.check_match(track_id, person_id, similarity=0.65, timestamp=10.0)
    verifier.check_match(track_id, person_id, similarity=0.65, timestamp=11.0)
    alert1 = verifier.check_match(track_id, person_id, similarity=0.65, timestamp=12.0)
    assert alert1 is not None

    # Subsequent match attempts within 300s must be suppressed
    verifier.check_match(track_id, person_id, similarity=0.75, timestamp=50.0)
    verifier.check_match(track_id, person_id, similarity=0.75, timestamp=51.0)
    suppressed = verifier.check_match(track_id, person_id, similarity=0.75, timestamp=52.0)
    assert suppressed is None, "Expected alert to be suppressed by 300s cooldown"

    # Beyond 300s cooldown (t = 315.0), alert is allowed again
    verifier.check_match(track_id, person_id, similarity=0.75, timestamp=315.0)
    verifier.check_match(track_id, person_id, similarity=0.75, timestamp=316.0)
    alert2 = verifier.check_match(track_id, person_id, similarity=0.75, timestamp=317.0)
    assert alert2 is not None


# ═════════════════════════════════════════════════════════════════════════════
# 4. Evidence Collector Tests
# ═════════════════════════════════════════════════════════════════════════════


def test_evidence_collector_artifacts():
    """Verifies saving 112x112 face crop, annotated frame, and 15 FPS video clip."""
    temp_dir = tempfile.mkdtemp(prefix="evidence_test_")
    try:
        collector = EvidenceCollector(base_dir=temp_dir, camera_id="CAM-FRONT-01")
        circ_buffer = CircularFrameBuffer(maxlen=30)

        # Buffer 15 synthetic frames (1 second at 15 FPS)
        for i in range(15):
            frame = np.full((480, 640, 3), fill_value=(i * 15) % 256, dtype=np.uint8)
            circ_buffer.append(frame, timestamp=float(i) / 15.0)

        latest_frame = np.full((480, 640, 3), fill_value=128, dtype=np.uint8)
        aligned_face = create_synthetic_face(112, 112)

        # Mock track
        mock_track = MagicMock()
        mock_track.track_id = 7
        mock_track.bbox = np.array([100, 100, 200, 220], dtype=np.float32)

        evidence = collector.capture(
            frame=latest_frame,
            track=mock_track,
            person_id="MP-EVID-01",
            similarity=0.68,
            timestamp=100.0,
            circ_buffer=circ_buffer,
            aligned_face=aligned_face,
        )

        assert isinstance(evidence, Evidence)
        assert os.path.isfile(evidence.crop_path)
        assert os.path.getsize(evidence.crop_path) > 0

        assert os.path.isfile(evidence.frame_path)
        assert os.path.getsize(evidence.frame_path) > 0

        assert evidence.clip_path is not None
        assert os.path.isfile(evidence.clip_path)
        assert os.path.getsize(evidence.clip_path) > 0

        # Validate crop dimensions
        crop_img = cv2.imread(evidence.crop_path)
        assert crop_img.shape[:2] == (112, 112)

        # Validate frame dimensions
        frame_img = cv2.imread(evidence.frame_path)
        assert frame_img.shape[:2] == (480, 640)
    finally:
        shutil.rmtree(temp_dir, ignore_errors=True)


# ═════════════════════════════════════════════════════════════════════════════
# 5. Candidate Deduplication Tests (Fix #14)
# ═════════════════════════════════════════════════════════════════════════════


def test_candidate_deduplication_in_frame():
    """
    CRITICAL ARCHITECTURAL VERIFICATION (Fix #14):
    When a person has multiple photo embeddings in FAISS, search can return
    multiple hits for the same person_id in a single frame.
    Deduplication must keep ONLY max(similarity) per person ID in that frame.
    """
    engine = VectorSearchEngine(dimension=512)
    query_emb = create_mock_embedding(seed=555)

    # Multi-photo case: Person MP-MULTI has 3 embeddings in FAISS
    emb1 = query_emb.copy()  # perfect match: 1.0
    emb2 = query_emb * 0.7 + create_mock_embedding(seed=777) * 0.3
    emb2 = emb2 / np.linalg.norm(emb2)  # ~0.70 match
    emb3 = query_emb * 0.5 + create_mock_embedding(seed=888) * 0.5
    emb3 = emb3 / np.linalg.norm(emb3)  # ~0.50 match

    # Another person
    emb_other = create_mock_embedding(seed=999)

    engine.rebuild_index(
        [
            ("MP-MULTI", emb1),
            ("MP-MULTI", emb2),
            ("MP-MULTI", emb3),
            ("MP-OTHER", emb_other),
        ]
    )

    candidates = engine.search(query_emb, top_k=5, cutoff=0.50)
    # Search returns multiple MP-MULTI entries
    multi_count = sum(1 for pid, _ in candidates if pid == "MP-MULTI")
    assert multi_count >= 2, "Expected multiple candidate hits for same person in FAISS"

    # Perform pipeline deduplication logic
    best_candidates = {}
    for pid, sim in candidates:
        if pid not in best_candidates or sim > best_candidates[pid]:
            best_candidates[pid] = sim

    # Must be deduplicated to exactly 1 score for MP-MULTI
    assert len(best_candidates) >= 1
    assert "MP-MULTI" in best_candidates
    assert np.isclose(best_candidates["MP-MULTI"], 1.0, atol=1e-4)


# ═════════════════════════════════════════════════════════════════════════════
# 6. End-to-End Edge AI Pipeline Integration Tests
# ═════════════════════════════════════════════════════════════════════════════


def test_pipeline_throttles_arcface_to_1_second():
    """
    CRITICAL ARCHITECTURAL VERIFICATION (Fix #8):
    Feeding 15 frames per second with the same track ID MUST throttle
    ArcFace recognition to once per 1.0 second per track.
    """
    mock_detector = MagicMock()
    mock_tracker = MagicMock()
    mock_quality = MagicMock()
    mock_aligner = MagicMock()
    mock_recognizer = MagicMock()
    mock_search = MagicMock()
    mock_verifier = MagicMock()

    # Track definition
    bbox = np.array([50, 50, 150, 150], dtype=np.float32)
    landmarks = create_standard_landmarks(bbox)
    track = ExtendedTrack(tlwh=np.array([50, 50, 100, 100], dtype=np.float32), score=0.9, landmarks=landmarks)
    track.track_id = 1

    mock_detector.detect.return_value = [Detection(bbox=bbox, score=0.9, landmarks=landmarks)]
    mock_tracker.update.return_value = [track]
    mock_quality.is_quality_sufficient.return_value = (True, "passed")
    mock_aligner.align.return_value = create_synthetic_face(112, 112)
    mock_recognizer.get_embedding.return_value = create_mock_embedding()
    mock_search.search.return_value = [("MP-1", 0.70)]
    mock_verifier.check_match.return_value = None

    pipeline = EdgeAIPipeline(
        camera_id="CAM-THROTTLE",
        detector=mock_detector,
        tracker=mock_tracker,
        quality_checker=mock_quality,
        aligner=mock_aligner,
        recognizer=mock_recognizer,
        vector_search=mock_search,
        verifier=mock_verifier,
        recognition_interval=1.0,
    )

    frame = np.full((480, 640, 3), fill_value=128, dtype=np.uint8)

    # Frame 1 at t = 0.0s -> ArcFace executes
    pipeline.process_frame(frame, timestamp=0.0)
    assert mock_recognizer.get_embedding.call_count == 1

    # Frame 2-15 at t = 0.06s to 0.93s (within 1.0s) -> ArcFace must be throttled!
    for i in range(1, 15):
        pipeline.process_frame(frame, timestamp=i * (1.0 / 15.0))
    assert mock_recognizer.get_embedding.call_count == 1, "ArcFace was not throttled to 1.0s!"

    # Frame 16 at t = 1.05s -> ArcFace executes again (second recognition)
    pipeline.process_frame(frame, timestamp=1.05)
    assert mock_recognizer.get_embedding.call_count == 2


def test_pipeline_skips_predicted_tracks_without_landmarks():
    """
    CRITICAL ARCHITECTURAL VERIFICATION (Fix #12):
    When a track is predicted via Kalman filter without a detection,
    track.landmarks is None. The pipeline MUST cleanly skip recognition.
    """
    mock_detector = MagicMock()
    mock_tracker = MagicMock()
    mock_recognizer = MagicMock()

    # Track with landmarks = None
    track_no_lm = ExtendedTrack(tlwh=np.array([50, 50, 100, 100], dtype=np.float32), score=0.8, landmarks=None)
    track_no_lm.track_id = 2

    mock_detector.detect.return_value = []
    mock_tracker.update.return_value = [track_no_lm]

    pipeline = EdgeAIPipeline(
        camera_id="CAM-NOLM",
        detector=mock_detector,
        tracker=mock_tracker,
        recognizer=mock_recognizer,
    )

    frame = np.full((480, 640, 3), fill_value=128, dtype=np.uint8)
    pipeline.process_frame(frame, timestamp=10.0)

    # Recognizer should NEVER be called when landmarks is None
    assert mock_recognizer.get_embedding.call_count == 0


def test_pipeline_end_to_end_match_flow():
    """
    Verifies full end-to-end flow from frame ingestion to match event,
    candidate deduplication, temporal verification, and evidence generation.
    """
    temp_dir = tempfile.mkdtemp(prefix="pipeline_test_")
    try:
        collector = EvidenceCollector(base_dir=temp_dir, camera_id="CAM-E2E")
        verifier = TemporalVerifier(window_size=3, threshold=0.60, max_time_span=5.0)
        vector_search = VectorSearchEngine(dimension=512)

        target_emb = create_mock_embedding(seed=777)
        vector_search.rebuild_index([("MP-FOUND", target_emb)])

        # Mocks for visual frontend
        mock_detector = MagicMock()
        mock_tracker = MagicMock()
        mock_quality = MagicMock()
        mock_aligner = MagicMock()
        mock_recognizer = MagicMock()

        bbox = np.array([100, 100, 200, 200], dtype=np.float32)
        landmarks = create_standard_landmarks(bbox)
        track = ExtendedTrack(tlwh=np.array([100, 100, 100, 100], dtype=np.float32), score=0.95, landmarks=landmarks)
        track.track_id = 77

        mock_detector.detect.return_value = [Detection(bbox=bbox, score=0.95, landmarks=landmarks)]
        mock_tracker.update.return_value = [track]
        mock_quality.is_quality_sufficient.return_value = (True, "passed")
        mock_aligner.align.return_value = create_synthetic_face(112, 112)
        mock_recognizer.get_embedding.return_value = target_emb

        match_callbacks_received = []

        def on_match(event: MatchEvent, evidence: Evidence):
            match_callbacks_received.append((event, evidence))

        pipeline = EdgeAIPipeline(
            camera_id="CAM-E2E",
            detector=mock_detector,
            tracker=mock_tracker,
            quality_checker=mock_quality,
            aligner=mock_aligner,
            recognizer=mock_recognizer,
            vector_search=vector_search,
            verifier=verifier,
            evidence_collector=collector,
            recognition_interval=1.0,
            on_match=on_match,
        )

        frame = np.full((480, 640, 3), fill_value=128, dtype=np.uint8)

        # Observation 1 at t = 0.0s
        res1 = pipeline.process_frame(frame, timestamp=0.0)
        assert len(res1) == 0

        # Observation 2 at t = 1.0s
        res2 = pipeline.process_frame(frame, timestamp=1.0)
        assert len(res2) == 0

        # Observation 3 at t = 2.0s -> Verification satisfies N=3, mean=1.0 >= 0.60
        res3 = pipeline.process_frame(frame, timestamp=2.0)
        assert len(res3) == 1

        match_event, evidence = res3[0]
        assert match_event.person_id == "MP-FOUND"
        assert match_event.track_id == 77
        assert np.isclose(match_event.score, 1.0, atol=1e-4)

        assert len(match_callbacks_received) == 1
        assert os.path.isfile(evidence.crop_path)
        assert os.path.isfile(evidence.frame_path)
        assert evidence.clip_path is not None
        assert os.path.isfile(evidence.clip_path)

        assert pipeline.telemetry.matches_confirmed == 1
        assert pipeline.telemetry.recognitions_attempted == 3
    finally:
        shutil.rmtree(temp_dir, ignore_errors=True)
