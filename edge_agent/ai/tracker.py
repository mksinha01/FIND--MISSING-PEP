"""ByteTrack multi-object tracker modernized for landmark preservation and zero deprecated np.float."""
import copy
from typing import Dict, List, Optional, Tuple, Union

import numpy as np
from scipy.linalg import cho_factor, cho_solve

from edge_agent.ai.face_detector import Detection
from edge_agent.ai.track_state import TrackState, TrackStatus


# ═════════════════════════════════════════════════════════════════════════════
# Modernized 8D Kalman Filter for Bounding Box Motion
# ═════════════════════════════════════════════════════════════════════════════


class KalmanFilter:
    """
    8-dimensional Kalman filter for tracking bounding boxes in image space.
    State vector: [x, y, a, h, vx, vy, va, vh]^T
    where:
        (x, y): center bounding box coordinates
        a: aspect ratio (width / height)
        h: height of bounding box
        (vx, vy, va, vh): velocities
    
    CRITICAL: Avoids deprecated np.float from filterpy; uses np.float32 and np.float64.
    """

    def __init__(self):
        ndim = 4
        dt = 1.0

        # State transition matrix F (8x8)
        self._motion_mat = np.eye(2 * ndim, 2 * ndim, dtype=np.float32)
        for i in range(ndim):
            self._motion_mat[i, ndim + i] = dt

        # Measurement projection matrix H (4x8)
        self._update_mat = np.eye(ndim, 2 * ndim, dtype=np.float32)

        # Motion and measurement noise weights
        self._std_weight_position = 1.0 / 20.0
        self._std_weight_velocity = 1.0 / 160.0

    def initiate(self, measurement: np.ndarray) -> Tuple[np.ndarray, np.ndarray]:
        """
        Creates track state from unassociated measurement [x, y, a, h].

        Returns:
            mean: (8,) float32 state vector
            covariance: (8, 8) float32 covariance matrix
        """
        mean_pos = np.asarray(measurement, dtype=np.float32)
        mean_vel = np.zeros_like(mean_pos, dtype=np.float32)
        mean = np.r_[mean_pos, mean_vel]

        std = [
            2.0 * self._std_weight_position * measurement[3],
            2.0 * self._std_weight_position * measurement[3],
            1e-2,
            2.0 * self._std_weight_position * measurement[3],
            10.0 * self._std_weight_velocity * measurement[3],
            10.0 * self._std_weight_velocity * measurement[3],
            1e-5,
            10.0 * self._std_weight_velocity * measurement[3],
        ]
        covariance = np.diag(np.square(std)).astype(np.float32)
        return mean, covariance

    def predict(self, mean: np.ndarray, covariance: np.ndarray) -> Tuple[np.ndarray, np.ndarray]:
        """
        Runs state prediction step: x_{k|k-1} = F * x_{k-1}, P_{k|k-1} = F * P * F^T + Q
        """
        std_pos = [
            self._std_weight_position * mean[3],
            self._std_weight_position * mean[3],
            1e-2,
            self._std_weight_position * mean[3],
        ]
        std_vel = [
            self._std_weight_velocity * mean[3],
            self._std_weight_velocity * mean[3],
            1e-5,
            self._std_weight_velocity * mean[3],
        ]
        sqr = np.square(np.r_[std_pos, std_vel])
        motion_cov = np.diag(sqr).astype(np.float32)

        mean = np.dot(self._motion_mat, mean).astype(np.float32)
        covariance = np.linalg.multi_dot(
            (self._motion_mat, covariance, self._motion_mat.T)
        ).astype(np.float32) + motion_cov

        return mean, covariance

    def project(self, mean: np.ndarray, covariance: np.ndarray) -> Tuple[np.ndarray, np.ndarray]:
        """
        Projects state distribution to measurement space: z = H * x, S = H * P * H^T + R
        """
        std = [
            self._std_weight_position * mean[3],
            self._std_weight_position * mean[3],
            1e-1,
            self._std_weight_position * mean[3],
        ]
        innovation_cov = np.diag(np.square(std)).astype(np.float32)

        projected_mean = np.dot(self._update_mat, mean).astype(np.float32)
        projected_cov = np.linalg.multi_dot(
            (self._update_mat, covariance, self._update_mat.T)
        ).astype(np.float32) + innovation_cov

        return projected_mean, projected_cov

    def update(
        self,
        mean: np.ndarray,
        covariance: np.ndarray,
        measurement: np.ndarray,
    ) -> Tuple[np.ndarray, np.ndarray]:
        """
        Updates state vector and covariance with new measurement [x, y, a, h].
        Uses Cholesky factorization for numerical stability.
        """
        projected_mean, projected_cov = self.project(mean, covariance)

        # Cholesky factorization: S = L * L^T
        chol_factor, lower = cho_factor(projected_cov.astype(np.float64), lower=True, check_finite=False)
        kalman_gain = cho_solve(
            (chol_factor, lower),
            np.dot(covariance, self._update_mat.T).T.astype(np.float64),
            check_finite=False,
        ).T.astype(np.float32)

        innovation = (measurement - projected_mean).astype(np.float32)
        new_mean = mean + np.dot(kalman_gain, innovation)
        new_covariance = covariance - np.linalg.multi_dot(
            (kalman_gain, projected_cov, kalman_gain.T)
        )

        return new_mean.astype(np.float32), new_covariance.astype(np.float32)


# ═════════════════════════════════════════════════════════════════════════════
# Base STrack Implementation
# ═════════════════════════════════════════════════════════════════════════════


class STrack:
    """
    Single object track representation managing Kalman filter state and ID.
    """

    shared_kalman = KalmanFilter()
    _count = 0

    def __init__(self, tlwh: np.ndarray, score: float):
        # tlwh: [top-left x, top-left y, width, height]
        self._tlwh = np.asarray(tlwh, dtype=np.float32)
        self.kalman_filter: Optional[KalmanFilter] = None
        self.mean: Optional[np.ndarray] = None
        self.covariance: Optional[np.ndarray] = None
        self.is_activated = False

        self.score = float(score)
        self.tracklet_len = 0

        self.track_id = 0
        self.state = TrackStatus.NEW
        self.frame_id = 0
        self.start_frame = 0

    @classmethod
    def next_id(cls) -> int:
        cls._count += 1
        return cls._count

    @classmethod
    def reset_id_counter(cls) -> None:
        cls._count = 0

    @property
    def tlwh(self) -> np.ndarray:
        """Top-left x, top-left y, width, height."""
        if self.mean is None:
            return self._tlwh.copy()
        ret = self.mean[:4].copy()
        ret[2] *= ret[3]
        ret[:2] -= ret[2:] / 2.0
        return ret.astype(np.float32)

    @property
    def tlbr(self) -> np.ndarray:
        """Top-left x, top-left y, bottom-right x, bottom-right y."""
        ret = self.tlwh
        ret[2:] += ret[:2]
        return ret.astype(np.float32)

    @staticmethod
    def tlwh_to_xyah(tlwh: np.ndarray) -> np.ndarray:
        """Converts [x, y, w, h] to [center_x, center_y, aspect_ratio, h]."""
        ret = np.asarray(tlwh, dtype=np.float32).copy()
        ret[:2] += ret[2:] / 2.0
        ret[2] /= max(1e-6, ret[3])
        return ret

    def to_xyah(self) -> np.ndarray:
        return self.tlwh_to_xyah(self.tlwh)

    def activate(self, kalman_filter: KalmanFilter, frame_id: int) -> None:
        """Initializes a new track with Kalman filter."""
        self.kalman_filter = kalman_filter
        self.track_id = self.next_id()
        self.mean, self.covariance = self.kalman_filter.initiate(self.to_xyah())

        self.tracklet_len = 0
        self.state = TrackStatus.TRACKED
        if frame_id == 1:
            self.is_activated = True
        self.frame_id = frame_id
        self.start_frame = frame_id

    def re_activate(self, new_track: "STrack", frame_id: int, new_id: bool = False) -> None:
        """Re-activates a lost or unconfirmed track."""
        if self.kalman_filter is not None and self.mean is not None and self.covariance is not None:
            self.mean, self.covariance = self.kalman_filter.update(
                self.mean, self.covariance, self.tlwh_to_xyah(new_track.tlwh)
            )
        self.tracklet_len = 0
        self.state = TrackStatus.TRACKED
        self.is_activated = True
        self.frame_id = frame_id
        if new_id:
            self.track_id = self.next_id()
        self.score = new_track.score

    def update(self, new_track: "STrack", frame_id: int) -> None:
        """Updates track with a matched detection in the current frame."""
        self.frame_id = frame_id
        self.tracklet_len += 1

        new_tlwh = new_track.tlwh
        if self.kalman_filter is not None and self.mean is not None and self.covariance is not None:
            self.mean, self.covariance = self.kalman_filter.update(
                self.mean, self.covariance, self.tlwh_to_xyah(new_tlwh)
            )
        else:
            self._tlwh = new_tlwh

        self.state = TrackStatus.TRACKED
        self.is_activated = True
        self.score = new_track.score

    def predict(self) -> None:
        """Predicts track state into the current frame via Kalman filter."""
        mean_state = self.mean.copy() if self.mean is not None else None
        if mean_state is not None and self.state != TrackStatus.TRACKED:
            mean_state[7] = 0.0  # Zero velocity on height if lost

        if self.kalman_filter is not None and mean_state is not None and self.covariance is not None:
            self.mean, self.covariance = self.kalman_filter.predict(mean_state, self.covariance)

    def mark_lost(self) -> None:
        self.state = TrackStatus.LOST

    def mark_removed(self) -> None:
        self.state = TrackStatus.REMOVED


# ═════════════════════════════════════════════════════════════════════════════
# ExtendedTrack with 5 Facial Landmarks Preservation
# ═════════════════════════════════════════════════════════════════════════════


class ExtendedTrack(STrack):
    """
    ByteTrack track extended with 5 facial landmarks preservation.
    
    CRITICAL ARCHITECTURAL REQUIREMENT:
    - If updated with a detection, preserves detection.landmarks.
    - If predicted via Kalman filter WITHOUT detection in the current frame,
      self.landmarks MUST be None so downstream recognition is cleanly skipped!
    """

    def __init__(
        self,
        tlwh: np.ndarray,
        score: float,
        landmarks: Optional[np.ndarray] = None,
    ):
        super().__init__(tlwh, score)
        if landmarks is not None:
            self.landmarks: Optional[np.ndarray] = np.asarray(landmarks, dtype=np.float32)
        else:
            self.landmarks = None

        self.last_recognition_time: float = 0.0
        self.score_history: Dict[str, List[float]] = {}
        self.state_data = TrackState(track_id=self.track_id)

    @property
    def bbox(self) -> np.ndarray:
        """Returns [x1, y1, x2, y2] bounding box."""
        return self.tlbr

    def predict(self) -> None:
        """
        Kalman filter prediction step.
        
        CRITICAL REQUIREMENT:
        When a track is predicted without a matched detection in the current frame,
        self.landmarks MUST be set to None.
        """
        super().predict()
        self.landmarks = None
        if self.state_data:
            self.state_data.landmarks = None
            self.state_data.bbox = self.tlbr

    def activate(self, kalman_filter: KalmanFilter, frame_id: int) -> None:
        """Initializes a new track with Kalman filter."""
        super().activate(kalman_filter, frame_id)
        if self.state_data:
            self.state_data.track_id = self.track_id
            self.state_data.update_geometry(
                bbox=self.tlbr,
                landmarks=self.landmarks,
                timestamp=float(frame_id),
            )

    def update(self, new_track: "ExtendedTrack", frame_id: int) -> None:
        """
        Updates track with matched detection and retains detection's 5 landmarks.
        """
        super().update(new_track, frame_id)
        if new_track.landmarks is not None:
            self.landmarks = np.asarray(new_track.landmarks, dtype=np.float32)
        else:
            self.landmarks = None

        if self.state_data:
            self.state_data.update_geometry(
                bbox=self.tlbr,
                landmarks=self.landmarks,
                timestamp=float(frame_id),
            )

    def re_activate(
        self,
        new_track: "ExtendedTrack",
        frame_id: int,
        new_id: bool = False,
    ) -> None:
        """
        Re-activates a lost track and restores fresh detection landmarks.
        """
        super().re_activate(new_track, frame_id, new_id=new_id)
        if new_track.landmarks is not None:
            self.landmarks = np.asarray(new_track.landmarks, dtype=np.float32)
        else:
            self.landmarks = None

        if self.state_data:
            if new_id:
                self.state_data.track_id = self.track_id
            self.state_data.update_geometry(
                bbox=self.tlbr,
                landmarks=self.landmarks,
                timestamp=float(frame_id),
            )


# ═════════════════════════════════════════════════════════════════════════════
# Matching and Association Utilities
# ═════════════════════════════════════════════════════════════════════════════


def bbox_ious(atlbrs: np.ndarray, btlbrs: np.ndarray) -> np.ndarray:
    """
    Vectorized Intersection-over-Union (IoU) calculation.
    atlbrs: (N, 4), btlbrs: (M, 4) -> returns (N, M) IoU matrix.
    """
    ious = np.zeros((len(atlbrs), len(btlbrs)), dtype=np.float32)
    if atlbrs.size == 0 or btlbrs.size == 0:
        return ious

    a_x1, a_y1, a_x2, a_y2 = atlbrs[:, 0], atlbrs[:, 1], atlbrs[:, 2], atlbrs[:, 3]
    b_x1, b_y1, b_x2, b_y2 = btlbrs[:, 0], btlbrs[:, 1], btlbrs[:, 2], btlbrs[:, 3]

    a_area = np.maximum(0.0, a_x2 - a_x1) * np.maximum(0.0, a_y2 - a_y1)
    b_area = np.maximum(0.0, b_x2 - b_x1) * np.maximum(0.0, b_y2 - b_y1)

    for i in range(len(atlbrs)):
        xx1 = np.maximum(a_x1[i], b_x1)
        yy1 = np.maximum(a_y1[i], b_y1)
        xx2 = np.minimum(a_x2[i], b_x2)
        yy2 = np.minimum(a_y2[i], b_y2)

        w = np.maximum(0.0, xx2 - xx1)
        h = np.maximum(0.0, yy2 - yy1)
        inter = w * h

        union = a_area[i] + b_area - inter
        ious[i] = inter / np.maximum(union, 1e-10)

    return ious


def iou_distance(atracks: List[STrack], btracks: List[STrack]) -> np.ndarray:
    """
    Computes cost matrix based on IoU distance: 1.0 - IoU.
    """
    if len(atracks) == 0 or len(btracks) == 0:
        return np.zeros((len(atracks), len(btracks)), dtype=np.float32)

    atlbrs = np.array([t.tlbr for t in atracks], dtype=np.float32)
    btlbrs = np.array([t.tlbr for t in btracks], dtype=np.float32)
    ious = bbox_ious(atlbrs, btlbrs)
    return (1.0 - ious).astype(np.float32)


def linear_assignment(
    cost_matrix: np.ndarray,
    thresh: float,
) -> Tuple[np.ndarray, np.ndarray, np.ndarray]:
    """
    Solves linear sum assignment using lapx (with defensive scipy fallback).

    Returns:
        matches: (K, 2) matched index pairs [row_idx, col_idx]
        unmatched_a: row indices with no match
        unmatched_b: col indices with no match
    """
    if cost_matrix.size == 0:
        return (
            np.empty((0, 2), dtype=int),
            np.arange(cost_matrix.shape[0], dtype=int),
            np.arange(cost_matrix.shape[1], dtype=int),
        )

    # 1. Attempt lapx.lapjv (Windows binary wheel optimized)
    try:
        import lapx

        cost, x, y = lapx.lapjv(cost_matrix, extend_cost=True, cost_limit=thresh)
        matches = [[ix, mx] for ix, mx in enumerate(x) if mx >= 0]
        unmatched_a = np.where(x < 0)[0]
        unmatched_b = np.where(y < 0)[0]
        return np.asarray(matches, dtype=int), unmatched_a, unmatched_b
    except Exception:
        pass

    # 2. Scipy linear_sum_assignment fallback
    from scipy.optimize import linear_sum_assignment

    row_ind, col_ind = linear_sum_assignment(cost_matrix)
    matches_list = []
    for r, c in zip(row_ind, col_ind):
        if cost_matrix[r, c] <= thresh:
            matches_list.append([r, c])

    matches = np.asarray(matches_list, dtype=int) if matches_list else np.empty((0, 2), dtype=int)
    matched_rows = set(matches[:, 0]) if len(matches) > 0 else set()
    matched_cols = set(matches[:, 1]) if len(matches) > 0 else set()

    unmatched_a = np.array([r for r in range(cost_matrix.shape[0]) if r not in matched_rows], dtype=int)
    unmatched_b = np.array([c for c in range(cost_matrix.shape[1]) if c not in matched_cols], dtype=int)
    return matches, unmatched_a, unmatched_b


# ═════════════════════════════════════════════════════════════════════════════
# ByteTracker Implementation
# ═════════════════════════════════════════════════════════════════════════════


class ByteTracker:
    """
    ByteTrack multi-object tracking algorithm running at 10-15 FPS on CPU.
    Performs two-stage association (high-confidence and low-confidence detections).
    Preserves 5 facial landmarks in ExtendedTrack instances.
    """

    def __init__(
        self,
        track_thresh: float = 0.5,
        high_thresh: float = 0.5,
        match_thresh: float = 0.8,
        low_match_thresh: float = 0.5,
        unconfirmed_match_thresh: float = 0.7,
        max_time_lost: int = 30,
        frame_rate: int = 15,
    ):
        self.track_thresh = track_thresh
        self.high_thresh = high_thresh
        self.match_thresh = match_thresh
        self.low_match_thresh = low_match_thresh
        self.unconfirmed_match_thresh = unconfirmed_match_thresh
        self.max_time_lost = max_time_lost
        self.frame_rate = frame_rate

        self.tracked_stracks: List[ExtendedTrack] = []
        self.lost_stracks: List[ExtendedTrack] = []
        self.removed_stracks: List[ExtendedTrack] = []

        self.frame_id = 0
        self.kalman_filter = KalmanFilter()

    def reset(self) -> None:
        """Resets tracker state."""
        self.tracked_stracks.clear()
        self.lost_stracks.clear()
        self.removed_stracks.clear()
        self.frame_id = 0
        STrack.reset_id_counter()

    def update(
        self,
        detections: Union[List[Detection], List[ExtendedTrack], np.ndarray],
    ) -> List[ExtendedTrack]:
        """
        Updates multi-object tracker with detections for the current frame.

        Args:
            detections: List of Detection objects, ExtendedTrack instances,
                        or array [[x1, y1, x2, y2, score], ...].

        Returns:
            List of active ExtendedTrack instances for the current frame.
        """
        self.frame_id += 1
        activated_stracks: List[ExtendedTrack] = []
        refind_stracks: List[ExtendedTrack] = []
        lost_stracks: List[ExtendedTrack] = []
        removed_stracks: List[ExtendedTrack] = []

        # 1. Parse input detections into ExtendedTrack instances
        detections_list: List[ExtendedTrack] = []
        if isinstance(detections, list):
            for det in detections:
                if isinstance(det, ExtendedTrack):
                    detections_list.append(det)
                elif isinstance(det, Detection):
                    x1, y1, x2, y2 = det.bbox
                    tlwh = np.array([x1, y1, x2 - x1, y2 - y1], dtype=np.float32)
                    detections_list.append(ExtendedTrack(tlwh, det.score, det.landmarks))
                elif isinstance(det, dict):
                    bbox = det.get("bbox", [0, 0, 0, 0])
                    score = float(det.get("score", 1.0))
                    landmarks = det.get("landmarks", None)
                    x1, y1, x2, y2 = bbox
                    tlwh = np.array([x1, y1, x2 - x1, y2 - y1], dtype=np.float32)
                    detections_list.append(ExtendedTrack(tlwh, score, landmarks))
        elif isinstance(detections, np.ndarray) and detections.size > 0:
            for row in detections:
                x1, y1, x2, y2 = row[:4]
                score = float(row[4]) if len(row) > 4 else 1.0
                tlwh = np.array([x1, y1, x2 - x1, y2 - y1], dtype=np.float32)
                detections_list.append(ExtendedTrack(tlwh, score, None))

        # 2. Divide detections into high-score and low-score pools
        detections_high: List[ExtendedTrack] = []
        detections_low: List[ExtendedTrack] = []
        for track in detections_list:
            if track.score >= self.track_thresh:
                detections_high.append(track)
            elif track.score >= 0.1:
                detections_low.append(track)

        # 3. Predict new locations of existing tracks via Kalman filter
        # CRITICAL: In ExtendedTrack.predict(), landmarks are set to None!
        unconfirmed: List[ExtendedTrack] = []
        tracked_stracks: List[ExtendedTrack] = []
        for track in self.tracked_stracks:
            if not track.is_activated:
                unconfirmed.append(track)
            else:
                tracked_stracks.append(track)

        track_pool = tracked_stracks + self.lost_stracks
        for track in track_pool:
            track.predict()

        # 4. Stage 1 Association: High confidence detections with tracked pool
        cost_mat = iou_distance(track_pool, detections_high)
        matches, u_track, u_detection = linear_assignment(cost_mat, thresh=self.match_thresh)

        for itracked, idet in matches:
            track = track_pool[itracked]
            det = detections_high[idet]
            if track.state == TrackStatus.TRACKED:
                track.update(det, self.frame_id)
            else:
                track.re_activate(det, self.frame_id, new_id=False)
                refind_stracks.append(track)

        # 5. Stage 2 Association: Low confidence detections with remaining active tracks
        r_tracked_stracks = [
            track_pool[i]
            for i in u_track
            if track_pool[i].state == TrackStatus.TRACKED
        ]
        cost_mat_low = iou_distance(r_tracked_stracks, detections_low)
        matches_low, u_track_low, _ = linear_assignment(cost_mat_low, thresh=self.low_match_thresh)

        for itracked, idet in matches_low:
            track = r_tracked_stracks[itracked]
            det = detections_low[idet]
            if track.state == TrackStatus.TRACKED:
                track.update(det, self.frame_id)
            else:
                track.re_activate(det, self.frame_id, new_id=False)
                refind_stracks.append(track)

        # Tracks that remain unmatched after stage 2 are marked lost
        for it in u_track_low:
            track = r_tracked_stracks[it]
            if track.state != TrackStatus.LOST:
                track.mark_lost()
                lost_stracks.append(track)

        # 6. Deal with unconfirmed tracks: match with remaining high-confidence detections
        detections_remaining = [detections_high[i] for i in u_detection]
        cost_mat_unconf = iou_distance(unconfirmed, detections_remaining)
        matches_unconf, u_unconf, u_detection_final = linear_assignment(
            cost_mat_unconf, thresh=self.unconfirmed_match_thresh
        )

        for itracked, idet in matches_unconf:
            unconf_track = unconfirmed[itracked]
            unconf_track.update(detections_remaining[idet], self.frame_id)
            activated_stracks.append(unconf_track)

        for it in u_unconf:
            track = unconfirmed[it]
            track.mark_removed()
            removed_stracks.append(track)

        # 7. Initialize new tracks from unmatched high-confidence detections
        for it in u_detection_final:
            track = detections_remaining[it]
            if track.score >= self.high_thresh:
                track.activate(self.kalman_filter, self.frame_id)
                activated_stracks.append(track)

        # 8. Manage lost tracks: prune if exceeded max_time_lost
        for track in self.lost_stracks:
            if self.frame_id - track.frame_id > self.max_time_lost:
                track.mark_removed()
                removed_stracks.append(track)

        # 9. Update state lists with strict deduplication
        new_tracked: List[ExtendedTrack] = []
        for t in self.tracked_stracks:
            if t.state == TrackStatus.TRACKED and t not in new_tracked:
                new_tracked.append(t)

        for t in activated_stracks:
            if t.state == TrackStatus.TRACKED and t not in new_tracked:
                new_tracked.append(t)

        for t in refind_stracks:
            if t.state == TrackStatus.TRACKED and t not in new_tracked:
                new_tracked.append(t)

        self.tracked_stracks = new_tracked

        self.lost_stracks = [
            t for t in self.lost_stracks if t.state == TrackStatus.LOST
        ]
        self.lost_stracks.extend(lost_stracks)
        self.lost_stracks = [
            t for t in self.lost_stracks if t not in self.tracked_stracks
        ]

        self.removed_stracks.extend(removed_stracks)

        # Return active tracks
        output_stracks = [track for track in self.tracked_stracks if track.is_activated]
        return output_stracks
