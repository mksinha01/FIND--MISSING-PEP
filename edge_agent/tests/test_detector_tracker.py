"""Unit and integration test suite for SCRFD face detector, quality gate, and landmark ByteTrack."""
from typing import List, Tuple
from unittest.mock import MagicMock

import cv2
import numpy as np
import pytest

from edge_agent.ai.face_aligner import (
    ARCFACE_REFERENCE_POINTS_112x112,
    FaceAligner,
    umeyama_similarity_transform,
)
from edge_agent.ai.face_detector import Detection, SCRFDFaceDetector, nms
from edge_agent.ai.face_quality import (
    FaceQualityChecker,
    FaceQualityGate,
    QualityResult,
)
from edge_agent.ai.track_state import TrackState, TrackStatus
from edge_agent.ai.tracker import (
    ByteTracker,
    ExtendedTrack,
    KalmanFilter,
    STrack,
)


# ═════════════════════════════════════════════════════════════════════════════
# Helper Fixtures & Synthetic Generators
# ═════════════════════════════════════════════════════════════════════════════


def create_synthetic_frame(
    width: int = 640,
    height: int = 480,
    with_texture: bool = True,
) -> np.ndarray:
    """Creates synthetic frame with high-frequency texture for blur testing."""
    if not with_texture:
        return np.full((height, width, 3), fill_value=128, dtype=np.uint8)

    # Checkerboard pattern for high Laplacian variance
    x = np.arange(width)
    y = np.arange(height)
    xx, yy = np.meshgrid(x, y)
    pattern = (((xx // 8) + (yy // 8)) % 2) * 255
    bgr = np.stack([pattern, pattern, pattern], axis=-1).astype(np.uint8)
    return bgr


def create_standard_landmarks(bbox: np.ndarray) -> np.ndarray:
    """Generates canonical 5 facial landmarks for a given bbox [x1, y1, x2, y2]."""
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
# 1. Face Quality Gate Tests
# ═════════════════════════════════════════════════════════════════════════════


def test_face_quality_minimum_size():
    """Verify face quality gate rejects boxes under 40px and accepts >= 40px."""
    gate = FaceQualityGate(min_face_size=40, min_blur_var=50.0)
    frame = create_synthetic_frame()

    # Face too small (35x35 px)
    small_box = np.array([50, 50, 85, 85], dtype=np.float32)
    small_kps = create_standard_landmarks(small_box)
    res_small = gate.check_quality(frame, small_box, small_kps)
    assert not res_small.passed
    assert "face_too_small" in (res_small.reason or "")

    # Face valid size (60x60 px)
    valid_box = np.array([50, 50, 110, 110], dtype=np.float32)
    valid_kps = create_standard_landmarks(valid_box)
    res_valid = gate.check_quality(frame, valid_box, valid_kps)
    assert res_valid.face_width == 60
    assert res_valid.face_height == 60
    assert res_valid.passed


def test_face_quality_aspect_ratio():
    """Verify face quality gate rejects aspect ratio outside [0.6, 1.2]."""
    gate = FaceQualityGate(min_face_size=40, min_aspect=0.6, max_aspect=1.2)
    frame = create_synthetic_frame()

    # Narrow box: w=40, h=100 -> ratio = 0.4 (< 0.6)
    narrow_box = np.array([50, 50, 90, 150], dtype=np.float32)
    narrow_kps = create_standard_landmarks(narrow_box)
    res_narrow = gate.check_quality(frame, narrow_box, narrow_kps)
    assert not res_narrow.passed
    assert "invalid_aspect_ratio" in (res_narrow.reason or "")

    # Wide box: w=120, h=60 -> ratio = 2.0 (> 1.2)
    wide_box = np.array([50, 50, 170, 110], dtype=np.float32)
    wide_kps = create_standard_landmarks(wide_box)
    res_wide = gate.check_quality(frame, wide_box, wide_kps)
    assert not res_wide.passed
    assert "invalid_aspect_ratio" in (res_wide.reason or "")

    # Canonical aspect ratio: w=70, h=80 -> ratio = 0.875
    good_box = np.array([50, 50, 120, 130], dtype=np.float32)
    good_kps = create_standard_landmarks(good_box)
    res_good = gate.check_quality(frame, good_box, good_kps)
    assert res_good.passed


def test_face_quality_blur_check():
    """Verify Laplacian blur variance threshold (>= 100.0)."""
    gate = FaceQualityGate(min_face_size=40, min_blur_var=100.0)
    box = np.array([50, 50, 150, 150], dtype=np.float32)
    kps = create_standard_landmarks(box)

    # Blurry uniform frame (Laplacian variance == 0.0 < 100.0)
    blurry_frame = create_synthetic_frame(with_texture=False)
    res_blur = gate.check_quality(blurry_frame, box, kps)
    assert not res_blur.passed
    assert "face_blurred" in (res_blur.reason or "")
    assert res_blur.blur_score < 100.0

    # Sharp textured frame (Laplacian variance > 100.0)
    sharp_frame = create_synthetic_frame(with_texture=True)
    res_sharp = gate.check_quality(sharp_frame, box, kps)
    assert res_sharp.passed
    assert res_sharp.blur_score >= 100.0


def test_face_quality_landmarks_and_pose_filter():
    """Verify landmark presence check and extreme pose rejection (yaw > 35 deg)."""
    gate = FaceQualityGate(min_face_size=40, min_blur_var=10.0, max_yaw_deg=35.0)
    frame = create_synthetic_frame()
    box = np.array([100, 100, 200, 200], dtype=np.float32)

    # Missing landmarks
    res_missing = gate.check_quality(frame, box, None)
    assert not res_missing.passed
    assert res_missing.reason == "missing_landmarks"

    # Extreme profile yaw (nose shifted right next to right eye)
    profile_kps = np.array(
        [
            [120.0, 130.0],  # left eye
            [180.0, 130.0],  # right eye
            [178.0, 150.0],  # nose extreme right
            [125.0, 175.0],  # left mouth
            [175.0, 175.0],  # right mouth
        ],
        dtype=np.float32,
    )
    res_profile = gate.check_quality(frame, box, profile_kps)
    assert not res_profile.passed
    assert "extreme_yaw" in (res_profile.reason or "")
    assert res_profile.yaw > 35.0

    # Frontal face landmarks
    frontal_kps = create_standard_landmarks(box)
    res_frontal = gate.check_quality(frame, box, frontal_kps)
    assert res_frontal.passed
    assert res_frontal.yaw <= 35.0


# ═════════════════════════════════════════════════════════════════════════════
# 2. Face Aligner Tests
# ═════════════════════════════════════════════════════════════════════════════


def test_face_aligner_output_geometry():
    """Verify affine alignment produces exact 112x112 output with leveled eyes."""
    aligner = FaceAligner(output_size=(112, 112))
    frame = create_synthetic_frame(640, 480)

    # Slightly tilted facial landmarks
    landmarks = np.array(
        [
            [180.0, 190.0],  # left eye (lower)
            [260.0, 210.0],  # right eye (higher/tilted)
            [220.0, 240.0],  # nose
            [190.0, 280.0],  # left mouth
            [250.0, 290.0],  # right mouth
        ],
        dtype=np.float32,
    )

    aligned = aligner.align(frame, landmarks)
    assert aligned is not None
    assert aligned.shape == (112, 112, 3)

    # Edge cases
    assert aligner.align(None, landmarks) is None
    assert aligner.align(frame, None) is None
    assert aligner.align(frame, np.empty((0, 2), dtype=np.float32)) is None


def test_umeyama_similarity_transform():
    """Verify Umeyama similarity transform mathematically computes correct scale and translation."""
    src = np.array([[0, 0], [10, 0], [5, 10]], dtype=np.float32)
    # Translate by (+50, +50) and scale by 2.0
    dst = (src * 2.0) + np.array([50.0, 50.0], dtype=np.float32)

    matrix = umeyama_similarity_transform(src, dst)
    assert matrix.shape == (2, 3)

    # Apply transform to src: [x', y'] = M * [x, y, 1]^T
    ones = np.ones((src.shape[0], 1), dtype=np.float32)
    src_homo = np.hstack([src, ones])
    transformed = (matrix @ src_homo.T).T

    np.testing.assert_allclose(transformed, dst, atol=1e-4)


# ═════════════════════════════════════════════════════════════════════════════
# 3. SCRFD Face Detector & NMS Tests
# ═════════════════════════════════════════════════════════════════════════════


def test_nms_suppression():
    """Verify Non-Maximum Suppression eliminates overlapping boxes."""
    boxes = np.array(
        [
            [10.0, 10.0, 50.0, 50.0],   # Box 0 (high conf)
            [12.0, 12.0, 52.0, 52.0],   # Box 1 (overlapping with Box 0, lower conf)
            [100.0, 100.0, 150.0, 150.0], # Box 2 (disjoint)
        ],
        dtype=np.float32,
    )
    scores = np.array([0.95, 0.80, 0.70], dtype=np.float32)

    keep = nms(boxes, scores, iou_threshold=0.4)
    assert keep == [0, 2], "Box 1 should be suppressed by Box 0!"


def test_scrfd_preprocessing():
    """Verify SCRFD frame resizing maintains canvas geometry and normalizes to [-1, 1]."""
    detector = SCRFDFaceDetector(input_size=(640, 640))
    frame = np.full((720, 1280, 3), fill_value=128, dtype=np.uint8)

    blob, det_scale, pad = detector.preprocess(frame)
    assert blob.shape == (1, 3, 640, 640)
    assert blob.dtype == np.float32
    assert -1.1 <= float(blob.min()) <= 1.1
    assert -1.1 <= float(blob.max()) <= 1.1
    assert det_scale == pytest.approx(640.0 / 1280.0, abs=1e-4)


def test_scrfd_single_stride_decoding():
    """Verify mathematical decoding of stride anchors, bboxes, and 5 facial landmarks."""
    h, w, stride = 10, 10, 8
    num_anchors = 1
    det_scale = 0.5

    # Mock score map with single detection at (grid_y=5, grid_x=5)
    score_map = np.zeros((h, w, num_anchors), dtype=np.float32)
    score_map[5, 5, 0] = 0.95

    # Bbox distances: [dl=2, dt=2, dr=2, db=2]
    bbox_map = np.zeros((h, w, 4 * num_anchors), dtype=np.float32)
    bbox_map[5, 5, :] = [2.0, 2.0, 2.0, 2.0]

    # 5 landmarks distances: [dx_i=1.0, dy_i=1.0]
    kps_map = np.ones((h, w, 10 * num_anchors), dtype=np.float32)

    boxes, scores, landmarks = SCRFDFaceDetector.decode_single_stride(
        score_map, bbox_map, kps_map, stride=stride, conf_threshold=0.5, det_scale=det_scale
    )

    assert len(boxes) == 1
    assert float(scores[0]) == pytest.approx(0.95, abs=1e-3)
    assert landmarks.shape == (1, 5, 2)

    # Anchor center for (grid_x=5, grid_y=5) with stride 8 is (40, 40)
    # Box: [cx - dl*stride, cy - dt*stride, cx + dr*stride, cy + db*stride] / det_scale
    # dl * stride = 2 * 8 = 16 -> (40 - 16) / 0.5 = 48
    # (40 + 16) / 0.5 = 112
    expected_box = np.array([48.0, 48.0, 112.0, 112.0], dtype=np.float32)
    np.testing.assert_allclose(boxes[0], expected_box, atol=1e-3)


# ═════════════════════════════════════════════════════════════════════════════
# 4. Kalman Filter & Numerical Stability Tests
# ═════════════════════════════════════════════════════════════════════════════


def test_kalman_filter_numeric_types():
    """Verify Kalman filter operations execute strictly on np.float32 without deprecated np.float."""
    kf = KalmanFilter()
    measurement = np.array([100.0, 100.0, 1.0, 50.0], dtype=np.float32)

    mean, cov = kf.initiate(measurement)
    assert mean.dtype == np.float32
    assert cov.dtype == np.float32
    assert mean.shape == (8,)
    assert cov.shape == (8, 8)

    # Predict
    pred_mean, pred_cov = kf.predict(mean, cov)
    assert pred_mean.dtype == np.float32
    assert pred_cov.dtype == np.float32

    # Project and Update
    new_measurement = np.array([105.0, 102.0, 1.0, 50.0], dtype=np.float32)
    upd_mean, upd_cov = kf.update(pred_mean, pred_cov, new_measurement)
    assert upd_mean.dtype == np.float32
    assert upd_cov.dtype == np.float32
    assert not np.isnan(upd_mean).any()
    assert not np.isnan(upd_cov).any()


# ═════════════════════════════════════════════════════════════════════════════
# 5. ExtendedTrack Landmark Lifecycle Tests (CRITICAL ARCHITECTURE)
# ═════════════════════════════════════════════════════════════════════════════


def test_extended_track_landmarks_lifecycle():
    """
    CRITICAL REQUIREMENT:
    - ExtendedTrack retains landmarks when updated with detection.
    - ExtendedTrack landmarks MUST be None when predicted via Kalman filter!
    """
    kps = np.array(
        [[10, 15], [30, 15], [20, 25], [12, 35], [28, 35]],
        dtype=np.float32,
    )
    tlwh = np.array([10.0, 10.0, 40.0, 50.0], dtype=np.float32)
    track = ExtendedTrack(tlwh, score=0.9, landmarks=kps)
    kf = KalmanFilter()
    track.activate(kf, frame_id=1)

    # 1. Initially has landmarks
    assert track.landmarks is not None
    np.testing.assert_allclose(track.landmarks, kps)

    # 2. Predict step without detection in current frame -> landmarks MUST BE NONE!
    track.predict()
    assert track.landmarks is None, (
        "CRITICAL BUG: Track predicted via Kalman filter MUST have landmarks=None!"
    )
    assert track.state_data.landmarks is None

    # 3. Fresh detection matched -> landmarks restored!
    new_kps = kps + 5.0
    new_det = ExtendedTrack(tlwh + 5.0, score=0.88, landmarks=new_kps)
    track.update(new_det, frame_id=2)
    assert track.landmarks is not None
    np.testing.assert_allclose(track.landmarks, new_kps)
    assert track.state_data.landmarks is not None


# ═════════════════════════════════════════════════════════════════════════════
# 6. ByteTracker Multi-Object Tracking Tests (Synthetic 30-Frame Sequence)
# ═════════════════════════════════════════════════════════════════════════════


def test_bytetracker_synthetic_30_frames():
    """
    Verifies 10-15 FPS ByteTrack continuity across a 30-frame linear trajectory:
    - Verifies constant Track ID across all frames.
    - Verifies landmarks presence on detection matches.
    - Verifies smooth trajectory estimation.
    """
    tracker = ByteTracker(track_thresh=0.5, high_thresh=0.5)
    tracker.reset()

    track_id = None
    positions: List[Tuple[float, float]] = []

    # Simulate object moving from x=50 to x=340 (dx=10 px per frame)
    for frame_idx in range(1, 31):
        x = 50.0 + (frame_idx - 1) * 10.0
        y = 100.0
        w, h = 60.0, 60.0

        bbox = np.array([x, y, x + w, y + h], dtype=np.float32)
        landmarks = create_standard_landmarks(bbox)
        detection = Detection(bbox=bbox, score=0.92, landmarks=landmarks)

        active_tracks = tracker.update([detection])
        assert len(active_tracks) == 1, f"Expected 1 active track at frame {frame_idx}"
        current_track = active_tracks[0]

        if track_id is None:
            track_id = current_track.track_id
        else:
            assert current_track.track_id == track_id, (
                f"Track ID churned from {track_id} to {current_track.track_id} at frame {frame_idx}!"
            )

        # Matched detection must retain landmarks
        assert current_track.landmarks is not None
        positions.append((float(current_track.tlbr[0]), float(current_track.tlbr[1])))

    # Verify smooth motion
    assert len(positions) == 30
    assert positions[-1][0] > positions[0][0] + 250.0


def test_bytetracker_two_stage_association_and_lost_handling():
    """
    Verifies ByteTrack two-stage matching:
    1. Low-confidence detection (0.35) matches existing track.
    2. Occluded track (missing detections) transitions to LOST and has landmarks=None.
    3. Reappearing track recovers with SAME track_id.
    """
    tracker = ByteTracker(track_thresh=0.5, high_thresh=0.5)
    tracker.reset()

    # Frame 1: High confidence detection
    box1 = np.array([100.0, 100.0, 160.0, 160.0], dtype=np.float32)
    kps1 = create_standard_landmarks(box1)
    tracks_f1 = tracker.update([Detection(bbox=box1, score=0.95, landmarks=kps1)])
    assert len(tracks_f1) == 1
    orig_id = tracks_f1[0].track_id

    # Frame 2: Low confidence detection (0.35) due to shadow/motion
    box2 = np.array([105.0, 102.0, 165.0, 162.0], dtype=np.float32)
    kps2 = create_standard_landmarks(box2)
    tracks_f2 = tracker.update([Detection(bbox=box2, score=0.35, landmarks=kps2)])
    assert len(tracks_f2) == 1
    assert tracks_f2[0].track_id == orig_id
    assert tracks_f2[0].landmarks is not None

    # Frame 3: Occlusion (0 detections) -> track is lost and predicted
    tracks_f3 = tracker.update([])
    # Not in active returned tracks when lost
    assert len(tracks_f3) == 0
    assert len(tracker.lost_stracks) == 1
    lost_track = tracker.lost_stracks[0]
    assert lost_track.track_id == orig_id
    # Crucial: landmarks must be None when predicted during occlusion
    assert lost_track.landmarks is None

    # Frame 4: Reappears -> recovered with same Track ID
    box4 = np.array([115.0, 105.0, 175.0, 165.0], dtype=np.float32)
    kps4 = create_standard_landmarks(box4)
    tracks_f4 = tracker.update([Detection(bbox=box4, score=0.90, landmarks=kps4)])
    assert len(tracks_f4) == 1
    assert tracks_f4[0].track_id == orig_id
    assert tracks_f4[0].landmarks is not None


# ═════════════════════════════════════════════════════════════════════════════
# 7. TrackState Telemetry Tests
# ═════════════════════════════════════════════════════════════════════════════


def test_track_state_throttling_and_scoring():
    """Verify TrackState throttles recognition to 1.0s and computes temporal mean."""
    state = TrackState(track_id=42)

    # Initial state: not throttled
    assert not state.is_throttled(now=100.0, interval_seconds=1.0)

    # Recognition attempted at t=100.0
    state.record_recognition(now=100.0)

    # Throttled at t=100.5 (< 1.0s)
    assert state.is_throttled(now=100.5, interval_seconds=1.0)

    # Not throttled at t=101.1 (> 1.0s)
    assert not state.is_throttled(now=101.1, interval_seconds=1.0)

    # Add temporal similarity scores for candidate
    state.add_score("person_abc", 0.62)
    state.add_score("person_abc", 0.64)
    state.add_score("person_abc", 0.66)

    assert state.get_recent_scores("person_abc", window_size=3) == [0.62, 0.64, 0.66]
    assert state.get_mean_score("person_abc", window_size=3) == pytest.approx(0.64, abs=1e-4)
    assert state.best_match_id == "person_abc"
    assert state.best_match_score == pytest.approx(0.66, abs=1e-4)
