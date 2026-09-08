"""15 FPS Circular Frame Buffer for pre- and post-event video clip evidence capture."""
import logging
import os
import threading
import time
from collections import deque
from typing import List, Optional, Tuple, Union

import cv2
import numpy as np

logger = logging.getLogger(__name__)


class CircularFrameBuffer:
    """
    Thread-safe circular ring buffer storing up to `maxlen` decoded video frames.
    
    Default maxlen=150 maintains a 10-second rolling window at 15 FPS.
    Upon positive biometric verification, `dump_video()` writes the buffered
    frames to an MP4 video clip for evidentiary verification.
    """

    def __init__(self, maxlen: int = 150):
        self.maxlen = maxlen
        self._buffer: deque = deque(maxlen=maxlen)
        self._lock = threading.RLock()

    def append(
        self,
        frame: Union[np.ndarray, float],
        timestamp: Optional[Union[float, np.ndarray]] = None,
    ) -> None:
        """
        Thread-safely append a decoded frame and its timestamp to the ring buffer.
        
        Supports both append(frame, timestamp) and append(timestamp, frame)
        to accommodate diverse caller conventions seamlessly.
        """
        # Auto-detect argument order if timestamp was passed as first parameter
        if isinstance(frame, (int, float)) and isinstance(timestamp, np.ndarray):
            actual_timestamp = float(frame)
            actual_frame = timestamp
        elif isinstance(frame, np.ndarray):
            actual_frame = frame
            actual_timestamp = float(timestamp) if timestamp is not None else time.time()
        else:
            raise TypeError(
                f"Expected frame to be a numpy.ndarray, got {type(frame).__name__}"
            )

        with self._lock:
            # Always store a detached copy to guard against in-place mutations
            self._buffer.append((actual_timestamp, actual_frame.copy()))

    def dump_video(self, output_path: str, fps: float = 15.0) -> bool:
        """
        Dump all buffered frames to an MP4 video file using OpenCV VideoWriter.
        
        Args:
            output_path: Target filesystem path for the .mp4 file.
            fps: Frame rate for video encoding (default 15.0 FPS).
            
        Returns:
            True if the video clip was successfully written, False otherwise.
        """
        with self._lock:
            if not self._buffer:
                logger.warning("Cannot dump video clip: circular buffer is empty.")
                return False

            # Take an immutable snapshot of buffered frames under lock
            snapshot = list(self._buffer)

        output_dir = os.path.dirname(os.path.abspath(output_path))
        if output_dir:
            os.makedirs(output_dir, exist_ok=True)

        first_frame = snapshot[0][1]
        h, w = first_frame.shape[:2]

        # Primary codec is mp4v for broad cross-platform support without external codecs
        codecs_to_try = [
            cv2.VideoWriter_fourcc(*"mp4v"),
            cv2.VideoWriter_fourcc(*"avc1"),
            cv2.VideoWriter_fourcc(*"XVID"),
        ]

        writer = None
        for fourcc in codecs_to_try:
            writer = cv2.VideoWriter(output_path, fourcc, float(fps), (w, h))
            if writer.isOpened():
                break
            writer.release()
            writer = None

        if writer is None or not writer.isOpened():
            logger.error(f"Failed to initialize VideoWriter for '{output_path}'")
            return False

        try:
            for _, frame in snapshot:
                # Ensure frame dimensions match writer initialization
                if frame.shape[:2] != (h, w):
                    frame_resized = cv2.resize(frame, (w, h))
                    writer.write(frame_resized)
                else:
                    writer.write(frame)
            return True
        except Exception as e:
            logger.error(f"Error writing frames to '{output_path}': {e}", exc_info=True)
            return False
        finally:
            writer.release()
            # Confirm file was actually created and is non-empty
            if not (os.path.isfile(output_path) and os.path.getsize(output_path) > 0):
                logger.error(f"Video file '{output_path}' was not created or is empty.")
                return False

    def dump_clip(self, output_path: str, fps: float = 15.0) -> Optional[str]:
        """
        Convenience alias returning the output filepath on success, or None on failure.
        """
        success = self.dump_video(output_path=output_path, fps=fps)
        return output_path if success else None

    def get_frames(self) -> List[np.ndarray]:
        """Return a list of all buffered frame arrays in chronological order."""
        with self._lock:
            return [frame.copy() for _, frame in self._buffer]

    def get_snapshots(self) -> List[Tuple[float, np.ndarray]]:
        """Return a list of (timestamp, frame) tuples in chronological order."""
        with self._lock:
            return [(ts, frame.copy()) for ts, frame in self._buffer]

    def get_latest(self) -> Optional[Tuple[float, np.ndarray]]:
        """Return the most recent (timestamp, frame) tuple, or None if empty."""
        with self._lock:
            if not self._buffer:
                return None
            ts, frame = self._buffer[-1]
            return ts, frame.copy()

    def get_latest_frame(self) -> Optional[np.ndarray]:
        """Return the most recent frame array, or None if empty."""
        with self._lock:
            if not self._buffer:
                return None
            return self._buffer[-1][1].copy()

    def clear(self) -> None:
        """Clear all buffered frames."""
        with self._lock:
            self._buffer.clear()

    @property
    def duration_seconds(self) -> float:
        """Calculate the time span covered by frames currently in the buffer."""
        with self._lock:
            if len(self._buffer) < 2:
                return 0.0
            return float(self._buffer[-1][0] - self._buffer[0][0])

    def __len__(self) -> int:
        with self._lock:
            return len(self._buffer)

    def __bool__(self) -> bool:
        with self._lock:
            return bool(self._buffer)
