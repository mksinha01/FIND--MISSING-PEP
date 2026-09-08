"""Face quality assessment: blur variance, scale, aspect ratio, and pose filtering."""
from dataclasses import dataclass
import logging
import math
from typing import Optional, Tuple

import cv2
import numpy as np

logger = logging.getLogger(__name__)


@dataclass
class QualityResult:
    """Detailed quality gate assessment results."""
    passed: bool
    reason: Optional[str]
    blur_score: float
    face_width: int
    face_height: int
    aspect_ratio: float
    yaw: float
    pitch: float


class FaceQualityGate:
    """
    Evaluates face image quality before passing candidate crops to ArcFace recognition.
    Enforces Laplacian blur variance, minimum resolution, aspect ratio, and pose thresholds.
    """

    def __init__(
        self,
        min_face_size: int = 40,
        min_blur_var: float = 100.0,
        min_aspect: float = 0.6,
        max_aspect: float = 1.2,
        max_yaw_deg: float = 35.0,
        max_pitch_deg: float = 35.0,
    ):
        self.min_face_size = min_face_size
        self.min_blur_var = min_blur_var
        self.min_aspect = min_aspect
        self.max_aspect = max_aspect
        self.max_yaw_deg = max_yaw_deg
        self.max_pitch_deg = max_pitch_deg

    def compute_blur_variance(self, face_crop: np.ndarray) -> float:
        """
        Calculates Laplacian variance of the cropped face image.
        Low values indicate motion or defocus blur.
        """
        if face_crop is None or face_crop.size == 0:
            return 0.0

        if len(face_crop.shape) == 3 and face_crop.shape[2] == 3:
            gray = cv2.cvtColor(face_crop, cv2.COLOR_BGR2GRAY)
        elif len(face_crop.shape) == 2:
            gray = face_crop
        else:
            return 0.0

        laplacian = cv2.Laplacian(gray, cv2.CV_64F)
        var = float(laplacian.var())
        return var

    def estimate_pose_angles(self, landmarks: np.ndarray) -> Tuple[float, float]:
        """
        Estimates yaw and pitch angles (in degrees) from 5 facial landmarks:
        0: left_eye, 1: right_eye, 2: nose, 3: left_mouth, 4: right_mouth.
        """
        if landmarks is None:
            return (0.0, 0.0)

        pts = np.asarray(landmarks, dtype=np.float64)
        if pts.size < 10:
            return (0.0, 0.0)
        if pts.shape == (10,):
            pts = pts.reshape(5, 2)
        if len(pts) < 5:
            return (0.0, 0.0)

        left_eye, right_eye, nose, left_mouth, right_mouth = pts[:5]

        # 1. Yaw angle estimation from nose-eye horizontal asymmetry
        dist_left_eye_nose = float(np.linalg.norm(nose - left_eye))
        dist_right_eye_nose = float(np.linalg.norm(nose - right_eye))
        eye_dist = dist_left_eye_nose + dist_right_eye_nose

        if eye_dist > 1e-6:
            asymmetry = abs(dist_left_eye_nose - dist_right_eye_nose) / eye_dist
            # Clamp to [0, 1] for arcsin
            clamped = min(1.0, asymmetry * 1.8)
            yaw_deg = float(math.degrees(math.asin(clamped)))
        else:
            yaw_deg = 0.0

        # 2. Pitch angle estimation from eye-nose vs nose-mouth vertical distances
        eye_mid_y = (left_eye[1] + right_eye[1]) / 2.0
        mouth_mid_y = (left_mouth[1] + right_mouth[1]) / 2.0
        nose_y = nose[1]

        total_vertical = mouth_mid_y - eye_mid_y
        if total_vertical > 1e-6:
            eye_to_nose = nose_y - eye_mid_y
            ratio = eye_to_nose / total_vertical
            # Canonical ratio for frontal face is approx 0.55
            pitch_dev = abs(ratio - 0.55) / 0.55
            clamped_pitch = min(1.0, pitch_dev * 1.5)
            pitch_deg = float(math.degrees(math.asin(clamped_pitch)))
        else:
            pitch_deg = 0.0

        return (yaw_deg, pitch_deg)

    def check_quality(
        self,
        frame: np.ndarray,
        bbox: np.ndarray,
        landmarks: Optional[np.ndarray],
    ) -> QualityResult:
        """
        Executes comprehensive quality evaluation pipeline on candidate face.

        Args:
            frame: Original BGR or RGB video frame.
            bbox: Bounding box [x1, y1, x2, y2] in pixel coordinates.
            landmarks: Optional 5 facial landmarks shape (5, 2).

        Returns:
            QualityResult with pass/fail decision and metric telemetry.
        """
        if frame is None or frame.size == 0 or bbox is None or len(bbox) < 4:
            return QualityResult(
                passed=False,
                reason="invalid_frame_or_bbox",
                blur_score=0.0,
                face_width=0,
                face_height=0,
                aspect_ratio=0.0,
                yaw=0.0,
                pitch=0.0,
            )

        # 1. Bounding box dimension checks
        h_img, w_img = frame.shape[:2]
        x1 = max(0, int(round(float(bbox[0]))))
        y1 = max(0, int(round(float(bbox[1]))))
        x2 = min(w_img, int(round(float(bbox[2]))))
        y2 = min(h_img, int(round(float(bbox[3]))))

        width = max(0, x2 - x1)
        height = max(0, y2 - y1)

        if width < self.min_face_size or height < self.min_face_size:
            return QualityResult(
                passed=False,
                reason=f"face_too_small: {width}x{height}px < {self.min_face_size}px",
                blur_score=0.0,
                face_width=width,
                face_height=height,
                aspect_ratio=float(width) / max(1.0, float(height)),
                yaw=0.0,
                pitch=0.0,
            )

        # 2. Aspect ratio check
        aspect_ratio = float(width) / max(1.0, float(height))
        if aspect_ratio < self.min_aspect or aspect_ratio > self.max_aspect:
            return QualityResult(
                passed=False,
                reason=f"invalid_aspect_ratio: {aspect_ratio:.2f} not in [{self.min_aspect}, {self.max_aspect}]",
                blur_score=0.0,
                face_width=width,
                face_height=height,
                aspect_ratio=aspect_ratio,
                yaw=0.0,
                pitch=0.0,
            )

        # 3. Blur variance check (Laplacian >= 100.0)
        face_crop = frame[y1:y2, x1:x2]
        blur_score = self.compute_blur_variance(face_crop)
        if blur_score < self.min_blur_var:
            return QualityResult(
                passed=False,
                reason=f"face_blurred: laplacian_var {blur_score:.1f} < {self.min_blur_var:.1f}",
                blur_score=blur_score,
                face_width=width,
                face_height=height,
                aspect_ratio=aspect_ratio,
                yaw=0.0,
                pitch=0.0,
            )

        # 4. Landmark check
        if landmarks is None:
            return QualityResult(
                passed=False,
                reason="missing_landmarks",
                blur_score=blur_score,
                face_width=width,
                face_height=height,
                aspect_ratio=aspect_ratio,
                yaw=0.0,
                pitch=0.0,
            )

        landmarks_arr = np.asarray(landmarks, dtype=np.float32)
        if landmarks_arr.size < 10:
            return QualityResult(
                passed=False,
                reason="missing_landmarks",
                blur_score=blur_score,
                face_width=width,
                face_height=height,
                aspect_ratio=aspect_ratio,
                yaw=0.0,
                pitch=0.0,
            )
        if landmarks_arr.shape == (10,):
            landmarks_arr = landmarks_arr.reshape(5, 2)
        if len(landmarks_arr) < 5:
            return QualityResult(
                passed=False,
                reason="missing_landmarks",
                blur_score=blur_score,
                face_width=width,
                face_height=height,
                aspect_ratio=aspect_ratio,
                yaw=0.0,
                pitch=0.0,
            )

        # Check landmarks are reasonably inside or near bounding box
        margin_x = width * 0.2
        margin_y = height * 0.2
        for i, (lx, ly) in enumerate(landmarks_arr[:5]):
            if (
                lx < (x1 - margin_x)
                or lx > (x2 + margin_x)
                or ly < (y1 - margin_y)
                or ly > (y2 + margin_y)
            ):
                return QualityResult(
                    passed=False,
                    reason=f"landmark_{i}_out_of_bounds",
                    blur_score=blur_score,
                    face_width=width,
                    face_height=height,
                    aspect_ratio=aspect_ratio,
                    yaw=0.0,
                    pitch=0.0,
                )

        # 5. Pose angle filter
        yaw, pitch = self.estimate_pose_angles(landmarks_arr)
        if yaw > self.max_yaw_deg:
            return QualityResult(
                passed=False,
                reason=f"extreme_yaw: {yaw:.1f}deg > {self.max_yaw_deg:.1f}deg",
                blur_score=blur_score,
                face_width=width,
                face_height=height,
                aspect_ratio=aspect_ratio,
                yaw=yaw,
                pitch=pitch,
            )

        if pitch > self.max_pitch_deg:
            return QualityResult(
                passed=False,
                reason=f"extreme_pitch: {pitch:.1f}deg > {self.max_pitch_deg:.1f}deg",
                blur_score=blur_score,
                face_width=width,
                face_height=height,
                aspect_ratio=aspect_ratio,
                yaw=yaw,
                pitch=pitch,
            )

        return QualityResult(
            passed=True,
            reason=None,
            blur_score=blur_score,
            face_width=width,
            face_height=height,
            aspect_ratio=aspect_ratio,
            yaw=yaw,
            pitch=pitch,
        )

    def is_quality_sufficient(
        self,
        frame: np.ndarray,
        bbox: np.ndarray,
        landmarks: Optional[np.ndarray],
    ) -> Tuple[bool, str]:
        """
        Convenience wrapper returning (passed, reason_str).
        """
        res = self.check_quality(frame, bbox, landmarks)
        return (res.passed, res.reason or "passed")


# Backward compatibility alias
FaceQualityChecker = FaceQualityGate
