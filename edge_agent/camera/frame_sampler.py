"""Frame rate regulation and temporal sampling for Edge Agent AI pipeline."""
import logging
import threading
import time
from typing import Any, Dict, Optional, Tuple

import numpy as np

logger = logging.getLogger(__name__)


class FrameSampler:
    """
    Regulates high-frequency incoming CCTV video streams (e.g., 25–30 FPS)
    down to the desired 10–15 FPS required by SCRFD face detection and ByteTrack.
    
    Prevents CPU saturation while maintaining high frame-to-frame tracking IoU.
    """

    def __init__(self, target_fps: float = 15.0):
        if target_fps <= 0:
            raise ValueError("target_fps must be strictly positive.")
        self._target_fps = float(target_fps)
        self._interval = 1.0 / self._target_fps
        self._last_sample_time: float = 0.0
        self._lock = threading.Lock()

        # Telemetry metrics
        self._total_frames: int = 0
        self._sampled_frames: int = 0
        self._dropped_frames: int = 0
        self._fps_window_start: float = time.time()
        self._fps_window_count: int = 0
        self._measured_fps: float = 0.0

    @property
    def target_fps(self) -> float:
        with self._lock:
            return self._target_fps

    @target_fps.setter
    def target_fps(self, value: float) -> None:
        if value <= 0:
            raise ValueError("target_fps must be strictly positive.")
        with self._lock:
            self._target_fps = float(value)
            self._interval = 1.0 / self._target_fps

    def should_sample(self, timestamp: Optional[float] = None) -> bool:
        """
        Evaluate whether the current frame should be sampled based on elapsed time.
        
        Args:
            timestamp: Optional caller-provided timestamp (seconds).
                       Defaults to time.time().
                       
        Returns:
            True if elapsed time since last sample >= 1.0 / target_fps.
        """
        now = float(timestamp) if timestamp is not None else time.time()
        with self._lock:
            self._total_frames += 1
            elapsed = now - self._last_sample_time

            # Check if interval threshold has passed (with 1ms / 5% jitter tolerance for discrete frames)
            tolerance = min(0.002, self._interval * 0.05)
            if elapsed >= (self._interval - tolerance):
                self._last_sample_time = now
                self._sampled_frames += 1
                self._fps_window_count += 1

                # Update rolling measured FPS every 1 second
                window_elapsed = now - self._fps_window_start
                if window_elapsed >= 1.0:
                    self._measured_fps = self._fps_window_count / window_elapsed
                    self._fps_window_count = 0
                    self._fps_window_start = now

                return True

            self._dropped_frames += 1
            return False

    def sample(
        self, frame: np.ndarray, timestamp: Optional[float] = None
    ) -> Optional[Tuple[float, np.ndarray]]:
        """
        Sample the given frame if the interval threshold is met.
        
        Returns:
            Tuple of (timestamp, frame) if sampled, or None if skipped.
        """
        ts = float(timestamp) if timestamp is not None else time.time()
        if self.should_sample(timestamp=ts):
            return ts, frame
        return None

    def reset(self) -> None:
        """Reset sampler state and metrics."""
        with self._lock:
            self._last_sample_time = 0.0
            self._total_frames = 0
            self._sampled_frames = 0
            self._dropped_frames = 0
            self._fps_window_start = time.time()
            self._fps_window_count = 0
            self._measured_fps = 0.0

    def get_stats(self) -> Dict[str, Any]:
        """Return diagnostic metrics for telemetry reporting."""
        with self._lock:
            return {
                "target_fps": self._target_fps,
                "measured_fps": round(self._measured_fps, 2),
                "total_frames": self._total_frames,
                "sampled_frames": self._sampled_frames,
                "dropped_frames": self._dropped_frames,
                "sample_rate_pct": (
                    round((self._sampled_frames / self._total_frames) * 100, 1)
                    if self._total_frames > 0
                    else 0.0
                ),
            }
