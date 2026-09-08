"""Track state representation and telemetry for multi-object face tracking."""
from dataclasses import dataclass, field
from enum import IntEnum
import time
from typing import Dict, List, Optional, Tuple

import numpy as np


class TrackStatus(IntEnum):
    """Lifecycle states of an object track."""
    NEW = 1
    TRACKED = 2
    LOST = 3
    REMOVED = 4


@dataclass
class TrackState:
    """
    Maintains per-track telemetry, recognition history, and temporal scores.
    
    Used by the Edge AI pipeline to decouple fast 10-15 FPS tracking from throttled
    1.0s ArcFace recognition and multi-observation verification.
    """
    track_id: int
    bbox: Optional[np.ndarray] = None  # tlbr [x1, y1, x2, y2] float32
    landmarks: Optional[np.ndarray] = None  # shape (5, 2) float32
    first_seen: float = field(default_factory=time.time)
    last_seen: float = field(default_factory=time.time)
    last_recognition_time: float = 0.0
    frame_count: int = 0
    score_history: Dict[str, List[float]] = field(default_factory=dict)
    best_match_id: Optional[str] = None
    best_match_score: float = 0.0

    def is_throttled(self, now: float, interval_seconds: float = 1.0) -> bool:
        """
        Determines whether recognition should be skipped for this track.
        Enforces a minimum interval (default 1.0s) between ArcFace evaluations.
        """
        if self.last_recognition_time <= 0.0:
            return False
        return (now - self.last_recognition_time) < interval_seconds

    def record_recognition(self, now: float) -> None:
        """Updates the timestamp of the latest recognition attempt."""
        self.last_recognition_time = now

    def update_geometry(
        self,
        bbox: np.ndarray,
        landmarks: Optional[np.ndarray],
        timestamp: float,
    ) -> None:
        """Updates track bounding box, facial landmarks, and activity timestamp."""
        self.bbox = np.asarray(bbox, dtype=np.float32)
        if landmarks is not None:
            self.landmarks = np.asarray(landmarks, dtype=np.float32)
        else:
            self.landmarks = None
        self.last_seen = timestamp
        self.frame_count += 1

    def add_score(self, person_id: str, score: float) -> None:
        """Records a similarity score observation for candidate person_id."""
        if person_id not in self.score_history:
            self.score_history[person_id] = []
        self.score_history[person_id].append(float(score))

        if score > self.best_match_score:
            self.best_match_score = float(score)
            self.best_match_id = person_id

    def get_recent_scores(self, person_id: str, window_size: int = 3) -> List[float]:
        """Returns the most recent N similarity scores for candidate person_id."""
        scores = self.score_history.get(person_id, [])
        return scores[-window_size:] if scores else []

    def get_mean_score(self, person_id: str, window_size: int = 3) -> float:
        """Returns the arithmetic mean of the last N scores for candidate person_id."""
        recent = self.get_recent_scores(person_id, window_size=window_size)
        if not recent:
            return 0.0
        return float(np.mean(recent, dtype=np.float64))

    def clear_scores(self, person_id: Optional[str] = None) -> None:
        """Clears score history for a specific person or all persons."""
        if person_id is not None:
            self.score_history.pop(person_id, None)
        else:
            self.score_history.clear()
            self.best_match_id = None
            self.best_match_score = 0.0
