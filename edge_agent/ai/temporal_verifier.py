"""Temporal verification engine enforcing multi-observation biometric confidence criteria."""
from dataclasses import dataclass, field
import logging
import threading
import time
from typing import Dict, List, Optional, Tuple

import numpy as np

logger = logging.getLogger(__name__)


@dataclass
class MatchEvent:
    """
    Biometric match confirmation event emitted when temporal criteria are met.
    """
    track_id: int
    person_id: str
    score: float
    frames: int
    timestamp: float
    scores: List[float] = field(default_factory=list)


class TemporalVerifier:
    """
    Multi-observation temporal biometric verification engine.

    Solves single-frame false accept anomalies and walking pedestrian transit constraints:
    Requires N=3 observations within a rolling 5.0-second window where the mean
    similarity score is >= 0.60 (standard 1:N FAR <= 10^-4).
    Upon triggering, applies a 300-second cooldown to suppress alert storms.
    """

    def __init__(
        self,
        window_size: int = 3,
        threshold: float = 0.60,
        max_time_span: float = 5.0,
        cooldown: float = 300.0,
    ):
        self.window_size = window_size
        self.threshold = threshold
        self.max_time_span = max_time_span
        self.cooldown = cooldown

        self._lock = threading.Lock()
        # Maps (track_id, person_id) -> List[(observation_time, similarity)]
        self.track_scores: Dict[Tuple[int, str], List[Tuple[float, float]]] = {}
        # Maps (track_id, person_id) -> last_alert_timestamp
        self.last_alert_time: Dict[Tuple[int, str], float] = {}

    @property
    def history(self) -> Dict[Tuple[int, str], List[Tuple[float, float]]]:
        """Convenience alias to internal score history."""
        return self.track_scores

    def check_match(
        self,
        track_id: int,
        person_id: str,
        similarity: float,
        timestamp: Optional[float] = None,
    ) -> Optional[MatchEvent]:
        """
        Records an observation and evaluates whether temporal criteria are fulfilled.

        Args:
            track_id: Persistent tracking ID assigned by ByteTrack.
            person_id: Candidate missing person identifier.
            similarity: Cosine similarity score for this observation.
            timestamp: Observation timestamp (defaults to time.time()).

        Returns:
            MatchEvent if N=3 observations within 5s average >= 0.60, otherwise None.
        """
        now = float(timestamp) if timestamp is not None else time.time()
        key = (int(track_id), str(person_id))
        sim = float(similarity)

        with self._lock:
            # 1. Enforce cooldown suppression
            last_alert = self.last_alert_time.get(key)
            if last_alert is not None and (now - last_alert) < self.cooldown:
                logger.debug(
                    f"Match suppressed by cooldown for {key}: "
                    f"{now - last_alert:.1f}s < {self.cooldown}s"
                )
                return None

            # 2. Retrieve history and discard observations older than max_time_span
            obs_list = self.track_scores.get(key, [])
            valid_obs = [
                (t, s) for t, s in obs_list if (now - t) <= self.max_time_span and t <= now + 0.5
            ]

            # 3. Append current observation
            valid_obs.append((now, sim))
            self.track_scores[key] = valid_obs

            # 4. Evaluate temporal multi-observation criteria
            if len(valid_obs) >= self.window_size:
                recent_window = valid_obs[-self.window_size:]
                scores = [s for _, s in recent_window]
                mean_score = float(np.mean(scores))

                if mean_score >= self.threshold:
                    self.last_alert_time[key] = now
                    # Clear history to prevent duplicate alerts on overlapping window
                    self.track_scores[key] = []

                    logger.info(
                        f"POSSIBLE MATCH CONFIRMED: Track {track_id} -> Person {person_id}, "
                        f"Mean={mean_score:.4f} >= {self.threshold}, "
                        f"Window={len(scores)} scores within {now - recent_window[0][0]:.2f}s"
                    )

                    return MatchEvent(
                        track_id=int(track_id),
                        person_id=str(person_id),
                        score=round(mean_score, 4),
                        frames=len(scores),
                        timestamp=now,
                        scores=[round(s, 4) for s in scores],
                    )

            return None

    def prune_stale(
        self,
        max_idle_seconds: float = 60.0,
        current_time: Optional[float] = None,
    ) -> int:
        """
        Removes observation records that have seen no activity for max_idle_seconds.
        """
        now = float(current_time) if current_time is not None else time.time()
        pruned_count = 0

        with self._lock:
            keys_to_remove = []
            for key, obs in self.track_scores.items():
                if not obs:
                    keys_to_remove.append(key)
                else:
                    latest_t = max(t for t, _ in obs)
                    if (now - latest_t) > max_idle_seconds:
                        keys_to_remove.append(key)

            for key in keys_to_remove:
                self.track_scores.pop(key, None)
                pruned_count += 1

            # Prune ancient cooldown entries (> 2x cooldown duration)
            ancient_alert_keys = [
                key
                for key, alert_t in self.last_alert_time.items()
                if (now - alert_t) > (self.cooldown * 2)
            ]
            for key in ancient_alert_keys:
                self.last_alert_time.pop(key, None)

        return pruned_count

    def clear_track(self, track_id: int) -> None:
        """Clears all observation scores associated with a specific track ID."""
        target_id = int(track_id)
        with self._lock:
            keys_to_delete = [k for k in self.track_scores.keys() if k[0] == target_id]
            for k in keys_to_delete:
                self.track_scores.pop(k, None)

    def reset(self) -> None:
        """Resets all history and cooldown tracking."""
        with self._lock:
            self.track_scores.clear()
            self.last_alert_time.clear()
