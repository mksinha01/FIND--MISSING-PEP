"""Face alignment using 5 facial landmarks and affine similarity transformation."""
import logging
from typing import Optional, Tuple

import cv2
import numpy as np

logger = logging.getLogger(__name__)

# Standard ArcFace reference points for 112x112 aligned face crops:
# 0: left_eye, 1: right_eye, 2: nose, 3: left_mouth, 4: right_mouth
ARCFACE_REFERENCE_POINTS_112x112 = np.array(
    [
        [38.2946, 51.6963],  # left eye
        [73.5318, 51.5014],  # right eye
        [56.0252, 71.7366],  # nose
        [41.5493, 92.3655],  # left mouth corner
        [70.7299, 92.2041],  # right mouth corner
    ],
    dtype=np.float32,
)


def umeyama_similarity_transform(
    src_points: np.ndarray,
    dst_points: np.ndarray,
) -> np.ndarray:
    """
    Computes 2D similarity transform matrix M [2x3] from src to dst using Umeyama algorithm.
    Guarantees strict similarity transformation (isotropic scale, rotation, translation).
    """
    src = np.asarray(src_points, dtype=np.float64)
    dst = np.asarray(dst_points, dtype=np.float64)

    num_points, dim = src.shape
    if num_points < 2 or dim != 2:
        raise ValueError(f"Expected at least 2 points of dimension 2, got {src.shape}")

    src_mean = np.mean(src, axis=0)
    dst_mean = np.mean(dst, axis=0)

    src_centered = src - src_mean
    dst_centered = dst - dst_mean

    src_var = np.mean(np.sum(src_centered**2, axis=1))
    if src_var < 1e-10:
        src_var = 1e-10

    # Covariance matrix: shape (2, 2)
    cov = (dst_centered.T @ src_centered) / num_points

    # SVD decomposition: cov = U @ np.diag(S) @ Vt
    u, s, vt = np.linalg.svd(cov)
    v = vt.T

    # Reflection correction
    d = np.ones(dim, dtype=np.float64)
    if np.linalg.det(u) * np.linalg.det(v) < 0:
        d[-1] = -1.0

    rot = u @ np.diag(d) @ vt
    scale = (1.0 / src_var) * np.sum(s * d)
    translation = dst_mean - scale * (rot @ src_mean)

    matrix = np.zeros((2, 3), dtype=np.float32)
    matrix[:2, :2] = (scale * rot).astype(np.float32)
    matrix[:2, 2] = translation.astype(np.float32)
    return matrix


class FaceAligner:
    """
    Performs 5-point landmark-based affine alignment to canonical dimensions (112x112).
    Standardizes face pose, scale, and eye-level prior to ArcFace feature extraction.
    """

    def __init__(
        self,
        reference_points: np.ndarray = ARCFACE_REFERENCE_POINTS_112x112,
        output_size: Tuple[int, int] = (112, 112),
    ):
        self.reference_points = np.asarray(reference_points, dtype=np.float32)
        self.output_size = output_size

    def align(
        self,
        frame: np.ndarray,
        landmarks: np.ndarray,
        output_size: Optional[Tuple[int, int]] = None,
    ) -> Optional[np.ndarray]:
        """
        Aligns and crops a face from the source frame using 5 landmarks.

        Args:
            frame: Source image (BGR or RGB), shape (H, W, C).
            landmarks: 5 facial landmarks [[x, y], ...], shape (5, 2).
            output_size: Optional custom (width, height), defaults to self.output_size.

        Returns:
            Aligned face crop of shape (height, width, C) or None if transformation fails.
        """
        if frame is None or frame.size == 0:
            return None

        if landmarks is None:
            return None

        landmarks_arr = np.asarray(landmarks, dtype=np.float32)
        if landmarks_arr.size < 10:
            return None
        if landmarks_arr.shape == (10,):
            landmarks_arr = landmarks_arr.reshape(5, 2)
        if len(landmarks_arr) < 5:
            return None

        target_size = output_size or self.output_size
        src_pts = landmarks_arr[:5]

        # Scale reference points if target size differs from standard (112, 112)
        if target_size == (112, 112):
            dst_pts = self.reference_points
        else:
            scale_x = target_size[0] / 112.0
            scale_y = target_size[1] / 112.0
            dst_pts = self.reference_points * np.array([scale_x, scale_y], dtype=np.float32)

        transform_matrix: Optional[np.ndarray] = None

        # 1. Attempt OpenCV partial affine estimation
        try:
            m, inliers = cv2.estimateAffinePartial2D(
                src_pts,
                dst_pts,
                method=cv2.LMEDS,
            )
            if m is not None and m.shape == (2, 3):
                transform_matrix = m.astype(np.float32)
        except Exception as e:
            logger.debug(f"OpenCV estimateAffinePartial2D failed, falling back to Umeyama: {e}")

        # 2. Analytical Umeyama fallback
        if transform_matrix is None:
            try:
                transform_matrix = umeyama_similarity_transform(src_pts, dst_pts)
            except Exception as e:
                logger.warning(f"Umeyama similarity transform failed: {e}")
                return None

        # 3. Warp source frame to canonical target dimensions
        try:
            aligned_face = cv2.warpAffine(
                frame,
                transform_matrix,
                target_size,
                flags=cv2.INTER_LINEAR,
                borderMode=cv2.BORDER_CONSTANT,
                borderValue=(0, 0, 0),
            )
            return aligned_face
        except Exception as e:
            logger.warning(f"cv2.warpAffine failed: {e}")
            return None
