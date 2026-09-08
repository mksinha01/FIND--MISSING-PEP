"""Camera feed preview widget with real-time bounding box overlays and HUD status badges."""
import logging
from typing import Any, List, Optional, Tuple

import cv2
import numpy as np
from PySide6.QtCore import QPoint, QRect, QRectF, QSize, Qt, Signal
from PySide6.QtGui import (
    QBrush,
    QColor,
    QFont,
    QImage,
    QPainter,
    QPen,
    QPixmap,
)
from PySide6.QtWidgets import QFrame, QLabel, QSizePolicy, QVBoxLayout, QWidget

logger = logging.getLogger(__name__)


class CameraFeedWidget(QWidget):
    """
    Renders live CCTV camera stream with real-time biometric and tracking overlays.

    Key Capabilities:
    1. Aspect-ratio-preserving hardware-accelerated QPainter frame rendering.
    2. Overlays active multi-object tracking bounding boxes (green) and match alerts (amber/red).
    3. Live telemetry HUD: camera channel tag, stream status dot, and real-time FPS badge.
    4. Sleek dark graphic fallback when camera stream is disconnected or buffering.
    """

    double_clicked = Signal(int)  # Emits camera_index for fullscreen toggle

    def __init__(
        self,
        camera_index: int = 0,
        camera_id: str = "CAM-01",
        camera_name: str = "Camera 1",
        parent: Optional[QWidget] = None,
    ):
        super().__init__(parent)
        self.camera_index = camera_index
        self.camera_id = camera_id
        self.camera_name = camera_name

        self.current_frame: Optional[np.ndarray] = None
        self.current_tracks: List[Any] = []
        self.fps: float = 0.0
        self.status_text: str = "Connecting..."
        self.is_connected: bool = False

        self.setMinimumSize(320, 200)
        self.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        self.setObjectName("cameraFeedContainer")
        self.setAttribute(Qt.WA_OpaquePaintEvent, False)

    def update_frame(
        self,
        camera_index: int,
        frame: np.ndarray,
        tracks: Optional[List[Any]] = None,
    ) -> None:
        """Slot receiving processed frame and tracks from StreamWorker (Signal crosses thread safely)."""
        if camera_index != self.camera_index:
            return

        if frame is not None and frame.size > 0:
            self.current_frame = frame
            self.current_tracks = tracks or []
            self.is_connected = True
            self.update()  # Request Qt repaint

    def update_fps(self, camera_index: int, fps: float) -> None:
        """Slot receiving FPS updates."""
        if camera_index == self.camera_index:
            self.fps = fps
            self.update()

    def clear_frame(self) -> None:
        """Reset current frame and tracks to empty state."""
        self.current_frame = None
        self.current_tracks = []
        self.fps = 0.0
        self.is_connected = False
        self.status_text = "Disconnected"
        self.update()

    def update_status(self, camera_index: int, status: str) -> None:
        """Slot receiving stream status change."""
        if camera_index == self.camera_index:
            self.status_text = status
            self.is_connected = status.lower() in ("connected", "streaming")
            self.update()

    def mouseDoubleClickEvent(self, event) -> None:
        """Emit double click signal to toggle focus/fullscreen in main window."""
        self.double_clicked.emit(self.camera_index)
        super().mouseDoubleClickEvent(event)

    def paintEvent(self, event) -> None:
        """Custom QPainter rendering video frame and HUD overlays."""
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        painter.setRenderHint(QPainter.SmoothPixmapTransform)

        w = self.width()
        h = self.height()

        if self.current_frame is None:
            self._draw_no_signal(painter, w, h)
            return

        # 1. Convert BGR numpy frame to QImage
        frame_rgb = cv2.cvtColor(self.current_frame, cv2.COLOR_BGR2RGB)
        fh, fw, ch = frame_rgb.shape
        bytes_per_line = ch * fw
        qimg = QImage(frame_rgb.data, fw, fh, bytes_per_line, QImage.Format_RGB888)

        # 2. Calculate aspect-ratio-preserving target rect
        scale_x = w / fw
        scale_y = h / fh
        scale = min(scale_x, scale_y)

        target_w = int(fw * scale)
        target_h = int(fh * scale)
        offset_x = (w - target_w) // 2
        offset_y = (h - target_h) // 2

        # Draw letterboxed background
        painter.fillRect(0, 0, w, h, QColor("#09090b"))

        # Draw scaled video frame
        target_rect = QRect(offset_x, offset_y, target_w, target_h)
        painter.drawImage(target_rect, qimg)

        # 3. Draw bounding boxes & track overlays
        self._draw_tracks(painter, scale, offset_x, offset_y)

        # 4. Draw HUD Overlays (Channel Badge, Status Dot, FPS)
        self._draw_hud(painter, w, h)

    def _draw_tracks(
        self,
        painter: QPainter,
        scale: float,
        offset_x: int,
        offset_y: int,
    ) -> None:
        """Render facial bounding boxes and ID tags over active tracks."""
        for track in self.current_tracks:
            bbox = getattr(track, "bbox", None)
            if bbox is None:
                # Handle dictionary or raw list format
                if isinstance(track, dict):
                    bbox = track.get("bbox")
                elif isinstance(track, (list, tuple)) and len(track) >= 4:
                    bbox = track[:4]

            if bbox is None or len(bbox) < 4:
                continue

            x1, y1, x2, y2 = [float(v) for v in bbox[:4]]
            rx = int(x1 * scale + offset_x)
            ry = int(y1 * scale + offset_y)
            rw = int((x2 - x1) * scale)
            rh = int((y2 - y1) * scale)

            track_id = getattr(track, "track_id", getattr(track, "id", None))
            person_id = getattr(track, "person_id", None)
            similarity = getattr(track, "similarity", None)

            # Determine box styling (Green for tracked face, Amber/Red for possible match)
            is_match = person_id is not None or (similarity is not None and similarity >= 0.50)
            box_color = QColor("#f59e0b") if is_match else QColor("#10b981")
            bg_color = QColor(245, 158, 11, 200) if is_match else QColor(16, 185, 129, 200)

            # Draw rectangle
            pen = QPen(box_color, 2)
            painter.setPen(pen)
            painter.setBrush(Qt.NoBrush)
            painter.drawRoundedRect(rx, ry, rw, rh, 4, 4)

            # Draw label pill
            label_text = f"ID #{track_id}" if track_id is not None else "Face"
            if is_match and similarity is not None:
                label_text = f"{person_id or 'MATCH'} ({similarity*100:.0f}%)"

            font = QFont("Segoe UI", 9, QFont.Bold)
            painter.setFont(font)
            fm = painter.fontMetrics()
            text_w = fm.horizontalAdvance(label_text) + 8
            text_h = fm.height() + 4

            label_rect = QRect(rx, max(0, ry - text_h - 2), text_w, text_h)
            painter.fillRect(label_rect, bg_color)
            painter.setPen(QColor("#ffffff"))
            painter.drawText(label_rect, Qt.AlignCenter, label_text)

    def _draw_hud(self, painter: QPainter, w: int, h: int) -> None:
        """Render top-left channel tag pill and top-right FPS badge."""
        # Top-left Channel Badge Pill
        pill_text = f"{self.camera_id} — {self.camera_name}"
        font = QFont("Segoe UI", 10, QFont.Bold)
        painter.setFont(font)
        fm = painter.fontMetrics()
        pill_w = fm.horizontalAdvance(pill_text) + 30
        pill_h = 26

        pill_rect = QRect(10, 10, pill_w, pill_h)
        painter.setPen(QPen(QColor(63, 63, 70, 180), 1))
        painter.setBrush(QColor(24, 24, 27, 210))
        painter.drawRoundedRect(pill_rect, 13, 13)

        # Status indicator dot
        dot_color = QColor("#10b981") if self.is_connected else QColor("#ef4444")
        painter.setPen(Qt.NoPen)
        painter.setBrush(dot_color)
        painter.drawEllipse(20, 18, 10, 10)

        # Channel text
        painter.setPen(QColor("#f4f4f5"))
        painter.drawText(36, 27, pill_text)

        # Top-right FPS Badge
        if self.is_connected:
            fps_text = f"{self.fps:.1f} FPS"
            fps_font = QFont("Segoe UI", 9, QFont.Bold)
            painter.setFont(fps_font)
            fps_fm = painter.fontMetrics()
            fps_w = fps_fm.horizontalAdvance(fps_text) + 16
            fps_h = 24

            fps_rect = QRect(w - fps_w - 10, 10, fps_w, fps_h)
            painter.setPen(QPen(QColor(63, 63, 70, 180), 1))
            painter.setBrush(QColor(24, 24, 27, 210))
            painter.drawRoundedRect(fps_rect, 6, 6)

            painter.setPen(QColor("#38bdf8"))  # Light cyan/blue
            painter.drawText(fps_rect, Qt.AlignCenter, fps_text)

    def _draw_no_signal(self, painter: QPainter, w: int, h: int) -> None:
        """Render aesthetic placeholder when stream is inactive or connecting."""
        painter.fillRect(0, 0, w, h, QColor("#09090b"))

        # Outer dashed frame
        painter.setPen(QPen(QColor("#27272a"), 2, Qt.DashLine))
        painter.setBrush(Qt.NoBrush)
        painter.drawRoundedRect(8, 8, w - 16, h - 16, 8, 8)

        # Camera tag
        font = QFont("Segoe UI", 12, QFont.Bold)
        painter.setFont(font)
        painter.setPen(QColor("#71717a"))
        painter.drawText(QRect(0, h // 2 - 24, w, 24), Qt.AlignCenter, f"{self.camera_id} — {self.camera_name}")

        # Status subtitle
        sub_font = QFont("Segoe UI", 10)
        painter.setFont(sub_font)
        painter.setPen(QColor("#a1a1aa"))
        status_msg = self.status_text if self.status_text else "NO SIGNAL"
        painter.drawText(QRect(0, h // 2 + 4, w, 20), Qt.AlignCenter, status_msg)
