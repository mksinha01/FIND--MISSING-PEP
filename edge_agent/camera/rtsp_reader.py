"""High-performance RTSP camera reader with hardware decode, TCP transport & auto-reconnect."""
import enum
import logging
import os
import threading
import time
from typing import Any, Callable, Dict, List, Optional, Tuple, Union

import cv2
import numpy as np

from edge_agent.camera.circular_buffer import CircularFrameBuffer

logger = logging.getLogger(__name__)


class StreamStatus(str, enum.Enum):
    """Lifecycle statuses for camera ingestion stream."""
    STOPPED = "STOPPED"
    CONNECTING = "CONNECTING"
    STREAMING = "STREAMING"
    RECONNECTING = "RECONNECTING"
    ERROR = "ERROR"


class RTSPReader:
    """
    OpenCV-based RTSP video capture thread.
    
    Features:
    - Enforces TCP transport (?rtsp_transport=tcp) to prevent packet loss.
    - Sets CAP_PROP_BUFFERSIZE=1 to eliminate network latency & green frames.
    - Enables hardware decode acceleration flags (DXVA2/D3D11 on Windows).
    - Maintains CircularFrameBuffer(maxlen=150) for 10-second evidentiary clips.
    - Exponential backoff reconnection loop ([1, 2, 5, 10, 30] seconds).
    - Cooperative thread termination using threading.Event().
    """

    RECONNECT_BACKOFF: List[int] = [1, 2, 5, 10, 30]

    def __init__(
        self,
        camera_id: str,
        rtsp_url: str,
        buffer_size: int = 150,
        hw_accel: bool = True,
        on_frame_callback: Optional[Callable[[np.ndarray, float], None]] = None,
    ):
        self.camera_id = str(camera_id)
        self.raw_rtsp_url = str(rtsp_url)
        self.rtsp_url = self._format_rtsp_url(self.raw_rtsp_url)
        self.buffer_size = buffer_size
        self.hw_accel = hw_accel
        self.on_frame_callback = on_frame_callback

        # Evidentiary rolling circular frame buffer
        self.circular_buffer = CircularFrameBuffer(maxlen=buffer_size)

        # Threading & lifecycle
        self._stop_event = threading.Event()
        self._thread: Optional[threading.Thread] = None
        self._lock = threading.RLock()
        self._status = StreamStatus.STOPPED

        # Latest frame cache
        self._latest_frame: Optional[np.ndarray] = None
        self._latest_timestamp: float = 0.0

        # Diagnostics & metrics
        self._frames_received: int = 0
        self._reconnect_count: int = 0
        self._current_backoff_idx: int = 0
        self._last_error: Optional[str] = None
        self._fps: float = 0.0
        self._fps_window_start: float = time.time()
        self._fps_window_count: int = 0

    @staticmethod
    def _format_rtsp_url(url: str) -> str:
        """
        Enforce TCP transport in RTSP URL (?rtsp_transport=tcp).
        Also configures OpenCV FFMPEG environment options.
        """
        # Set environment variable for OpenCV's FFmpeg backend
        os.environ["OPENCV_FFMPEG_CAPTURE_OPTIONS"] = "rtsp_transport;tcp"

        if url.startswith("rtsp://") and "rtsp_transport=" not in url:
            delimiter = "&" if "?" in url else "?"
            return f"{url}{delimiter}rtsp_transport=tcp"
        return url

    @property
    def status(self) -> StreamStatus:
        with self._lock:
            return self._status

    @property
    def is_connected(self) -> bool:
        with self._lock:
            return self._status == StreamStatus.STREAMING

    def start(self) -> None:
        """Start the background ingestion thread cooperatively."""
        with self._lock:
            if self._thread is not None and self._thread.is_alive():
                logger.warning(f"RTSPReader[{self.camera_id}] is already running.")
                return

            self._stop_event.clear()
            self._status = StreamStatus.CONNECTING
            self._thread = threading.Thread(
                target=self._capture_loop,
                name=f"RTSPReader-{self.camera_id}",
                daemon=True,
            )
            self._thread.start()
            logger.info(f"Started RTSPReader thread for camera '{self.camera_id}'")

    def stop(self, timeout: float = 3.0) -> None:
        """
        Signal cooperative thread shutdown and wait for termination.
        Does NOT abruptly kill the thread (Fix #26).
        """
        with self._lock:
            if self._thread is None or not self._thread.is_alive():
                self._status = StreamStatus.STOPPED
                return

            logger.info(f"Signaling RTSPReader[{self.camera_id}] to stop cooperatively...")
            self._stop_event.set()

        # Join outside lock to prevent deadlocks
        thread = self._thread
        if thread and thread.is_alive():
            thread.join(timeout=timeout)
            if thread and thread.is_alive():
                logger.warning(
                    f"RTSPReader[{self.camera_id}] thread did not terminate within {timeout}s timeout."
                )

        with self._lock:
            self._status = StreamStatus.STOPPED
            self._thread = None
            logger.info(f"RTSPReader[{self.camera_id}] stopped cleanly.")

    def _resolve_source_path(self) -> Tuple[bool, str]:
        """Check if source is a local video file and resolve full path."""
        raw = self.raw_rtsp_url.strip()
        if raw.isdigit() or raw.startswith("rtsp://") or raw.startswith("http://") or raw.startswith("https://"):
            return False, raw

        candidates = [
            raw,
            os.path.abspath(raw),
            os.path.join(os.getcwd(), raw),
        ]
        for cp in candidates:
            if cp and os.path.isfile(cp):
                return True, cp
        return False, raw

    def _create_capture(self) -> cv2.VideoCapture:
        """Initialize OpenCV VideoCapture supporting webcam index, video file, or RTSP stream."""
        raw = self.raw_rtsp_url.strip()
        if raw.isdigit():
            # Webcam index (e.g. 0, 1) - use DirectShow on Windows for fast init
            idx = int(raw)
            cap = None
            if hasattr(cv2, "CAP_DSHOW"):
                cap = cv2.VideoCapture(idx, cv2.CAP_DSHOW)
            if cap is None or not cap.isOpened():
                cap = cv2.VideoCapture(idx)
            if cap and cap.isOpened():
                cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
            return cap

        is_file, resolved_path = self._resolve_source_path()
        if is_file:
            cap = cv2.VideoCapture(resolved_path)
            return cap

        cap = None

        # Attempt hardware accelerated decode if requested and available for RTSP
        if self.hw_accel and hasattr(cv2, "CAP_FFMPEG") and raw.startswith("rtsp://"):
            try:
                params = []
                if hasattr(cv2, "CAP_PROP_HW_ACCELERATION") and hasattr(cv2, "VIDEO_ACCELERATION_ANY"):
                    params = [cv2.CAP_PROP_HW_ACCELERATION, cv2.VIDEO_ACCELERATION_ANY]

                if params:
                    cap = cv2.VideoCapture(self.rtsp_url, cv2.CAP_FFMPEG, params)
                else:
                    cap = cv2.VideoCapture(self.rtsp_url, cv2.CAP_FFMPEG)
            except Exception as e:
                logger.debug(f"Hardware-accelerated capture init failed: {e}; falling back.")
                cap = None

        if cap is None or not cap.isOpened():
            cap = cv2.VideoCapture(self.rtsp_url)

        # Fix #17: Minimal internal buffer size to eliminate packet lag and green frames
        if cap and cap.isOpened():
            cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)

        return cap

    def _capture_loop(self) -> None:
        """Main capture loop executing in dedicated worker thread."""
        logger.info(f"Capture loop started for camera '{self.camera_id}' -> {self.raw_rtsp_url}")
        self._current_backoff_idx = 0
        is_video_file, _ = self._resolve_source_path()

        while not self._stop_event.is_set():
            with self._lock:
                self._status = (
                    StreamStatus.RECONNECTING
                    if self._reconnect_count > 0
                    else StreamStatus.CONNECTING
                )

            cap = None
            try:
                cap = self._create_capture()

                if not cap or not cap.isOpened():
                    raise ConnectionError(f"Failed to open video source: {self.raw_rtsp_url}")

                with self._lock:
                    self._status = StreamStatus.STREAMING
                    self._current_backoff_idx = 0
                    self._last_error = None
                logger.info(f"Connected to stream '{self.camera_id}'. Ingesting frames...")

                # Frame reading loop
                consecutive_failures = 0
                while not self._stop_event.is_set():
                    t_start = time.time()
                    ret, frame = cap.read()

                    if not ret or frame is None:
                        if is_video_file:
                            # Loop video file continuously for test feeds
                            cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
                            ret, frame = cap.read()
                            if ret and frame is not None:
                                consecutive_failures = 0
                            else:
                                consecutive_failures += 1
                        else:
                            consecutive_failures += 1

                        if consecutive_failures >= 5:
                            logger.warning(
                                f"Failed to read consecutive frames from '{self.camera_id}'"
                            )
                            break
                        time.sleep(0.01)
                        continue

                    consecutive_failures = 0
                    timestamp = time.time()

                    # Update circular buffer and cached frame
                    self.circular_buffer.append(frame, timestamp)

                    with self._lock:
                        self._latest_frame = frame
                        self._latest_timestamp = timestamp
                        self._frames_received += 1
                        self._fps_window_count += 1

                        # Update FPS telemetry
                        elapsed = timestamp - self._fps_window_start
                        if elapsed >= 1.0:
                            self._fps = round(self._fps_window_count / elapsed, 1)
                            self._fps_window_count = 0
                            self._fps_window_start = timestamp

                    # Invoke optional frame callback
                    if self.on_frame_callback is not None:
                        try:
                            self.on_frame_callback(frame, timestamp)
                        except Exception as cb_err:
                            logger.error(f"Error in on_frame_callback: {cb_err}", exc_info=True)

                    # Frame pacing: if reading from a local video file, pace at ~15 FPS
                    if is_video_file:
                        elapsed_read = time.time() - t_start
                        sleep_needed = (1.0 / 15.0) - elapsed_read
                        if sleep_needed > 0:
                            time.sleep(sleep_needed)

            except Exception as e:
                with self._lock:
                    self._last_error = str(e)
                    self._status = StreamStatus.ERROR
                logger.warning(f"RTSP stream error on camera '{self.camera_id}': {e}")
            finally:
                if cap is not None:
                    try:
                        cap.release()
                    except Exception:
                        pass

            if self._stop_event.is_set():
                break

            # Exponential backoff reconnection state machine
            backoff_delay = self.RECONNECT_BACKOFF[self._current_backoff_idx]
            self._reconnect_count += 1
            logger.info(
                f"Reconnecting camera '{self.camera_id}' in {backoff_delay}s "
                f"(attempt {self._reconnect_count}, backoff index {self._current_backoff_idx})..."
            )

            # Advance backoff index capped at maximum delay
            self._current_backoff_idx = min(
                self._current_backoff_idx + 1, len(self.RECONNECT_BACKOFF) - 1
            )

            # Wait cooperatively: early-wakes if self._stop_event is set
            self._stop_event.wait(timeout=float(backoff_delay))

        with self._lock:
            self._status = StreamStatus.STOPPED

    def get_latest_frame(self) -> Optional[Tuple[float, np.ndarray]]:
        """Return the most recently captured (timestamp, frame), or None if unavailable."""
        with self._lock:
            if self._latest_frame is None:
                return None
            return self._latest_timestamp, self._latest_frame.copy()

    def get_stats(self) -> Dict[str, Any]:
        """Return telemetry statistics for monitoring dashboard."""
        with self._lock:
            return {
                "camera_id": self.camera_id,
                "status": self._status.value,
                "is_connected": self._status == StreamStatus.STREAMING,
                "fps": self._fps,
                "frames_received": self._frames_received,
                "buffer_len": len(self.circular_buffer),
                "reconnect_count": self._reconnect_count,
                "last_error": self._last_error,
            }
