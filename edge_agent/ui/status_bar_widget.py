"""Real-time system telemetry and AI health status bar widget."""
import logging
import time
from typing import Any, Dict, Optional

try:
    import psutil
except ImportError:
    psutil = None  # type: ignore

from PySide6.QtCore import QTimer, Qt
from PySide6.QtGui import QColor, QFont
from PySide6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QLabel,
    QWidget,
)

logger = logging.getLogger(__name__)


class StatusBarWidget(QWidget):
    """
    Bottom telemetry strip providing live operational awareness:
    - AI Pipeline Execution Status (Running / Paused / Error)
    - Active Missing Person Target Count
    - Aggregate Camera FPS
    - Cumulative Real-Time Faces Detected
    - Hardware CPU Utilization (%)
    - Synchronization Freshness (Last Sync)
    - Central Backend Connectivity Status
    """

    def __init__(self, parent: Optional[QWidget] = None):
        super().__init__(parent)
        self.faces_count: int = 0
        self._init_ui()

        # Hardware metrics polling timer (every 2.0s)
        self.metrics_timer = QTimer(self)
        self.metrics_timer.timeout.connect(self._poll_system_metrics)
        self.metrics_timer.start(2000)

    def _init_ui(self) -> None:
        layout = QHBoxLayout(self)
        layout.setContentsMargins(12, 4, 12, 4)
        layout.setSpacing(16)

        # 1. AI Pipeline Status Indicator
        self.ai_status_label = QLabel("● AI Running")
        self.ai_status_label.setStyleSheet("color: #10b981; font-weight: 700; font-size: 12px;")
        layout.addWidget(self.ai_status_label)
        layout.addWidget(self._create_separator())

        # 2. Active Missing Person Cases
        self.cases_label = QLabel("Cases: 0")
        self.cases_label.setStyleSheet("color: #f4f4f5; font-weight: 500; font-size: 12px;")
        layout.addWidget(self.cases_label)
        layout.addWidget(self._create_separator())

        # 3. Aggregate FPS
        self.fps_label = QLabel("FPS: 0.0")
        self.fps_label.setStyleSheet("color: #38bdf8; font-weight: 600; font-size: 12px;")
        layout.addWidget(self.fps_label)
        layout.addWidget(self._create_separator())

        # 4. Faces Detected Counter
        self.faces_label = QLabel("Faces: 0")
        self.faces_label.setStyleSheet("color: #e4e4e7; font-size: 12px;")
        layout.addWidget(self.faces_label)
        layout.addWidget(self._create_separator())

        # 5. CPU Utilization
        self.cpu_label = QLabel("CPU: 0%")
        self.cpu_label.setStyleSheet("color: #a1a1aa; font-size: 12px;")
        layout.addWidget(self.cpu_label)
        layout.addWidget(self._create_separator())

        # 6. Synchronization Freshness
        self.sync_label = QLabel("Sync: Never")
        self.sync_label.setStyleSheet("color: #a1a1aa; font-size: 12px;")
        layout.addWidget(self.sync_label)

        layout.addStretch()

        # 7. Backend Connection Status Indicator
        self.backend_status_label = QLabel("● Backend: Online")
        self.backend_status_label.setStyleSheet("color: #10b981; font-weight: 600; font-size: 12px;")
        layout.addWidget(self.backend_status_label)

        self.setStyleSheet("background-color: #141417; border-top: 1px solid #27272a;")
        self.setFixedHeight(32)

    def _create_separator(self) -> QFrame:
        sep = QFrame()
        sep.setFrameShape(QFrame.VLine)
        sep.setFrameShadow(QFrame.Sunken)
        sep.setStyleSheet("color: #27272a; margin: 2px 0;")
        return sep

    def set_ai_status(self, text: str, color_hex: str = "#10b981") -> None:
        """Update AI status indicator text and color."""
        self.ai_status_label.setText(text)
        self.ai_status_label.setStyleSheet(f"color: {color_hex}; font-weight: 700; font-size: 12px;")

    def set_cases_count(self, count: int) -> None:
        """Update active cases counter."""
        self.cases_label.setText(f"Cases: {count}")

    def set_fps(self, fps: float) -> None:
        """Update aggregate FPS display."""
        self.fps_label.setText(f"FPS: {fps:.1f}")

    def increment_faces(self, count: int = 1) -> None:
        """Increment cumulative faces detected count."""
        self.faces_count += count
        self.faces_label.setText(f"Faces: {self.faces_count:,}")

    def set_faces_count(self, count: int) -> None:
        """Directly set cumulative faces detected count."""
        self.faces_count = count
        self.faces_label.setText(f"Faces: {self.faces_count:,}")

    def set_last_sync(self, sync_time: str) -> None:
        """Update synchronization timestamp."""
        self.sync_label.setText(f"Sync: {sync_time}")

    def set_backend_online(self, is_online: bool) -> None:
        """Update backend connectivity status."""
        if is_online:
            self.backend_status_label.setText("● Backend: Online")
            self.backend_status_label.setStyleSheet("color: #10b981; font-weight: 600; font-size: 12px;")
        else:
            self.backend_status_label.setText("● Backend: Offline")
            self.backend_status_label.setStyleSheet("color: #ef4444; font-weight: 600; font-size: 12px;")

    def update_stats(self, stats: Dict[str, Any]) -> None:
        """Slot receiving dictionary of metrics from SyncWorker."""
        if "cases_count" in stats:
            self.set_cases_count(stats["cases_count"])
        if "last_sync" in stats:
            self.set_last_sync(stats["last_sync"])

    def _poll_system_metrics(self) -> None:
        """Periodic background poll for CPU utilization."""
        if psutil is not None:
            try:
                cpu = psutil.cpu_percent()
                color = "#ef4444" if cpu > 85 else ("#f59e0b" if cpu > 65 else "#a1a1aa")
                self.cpu_label.setText(f"CPU: {cpu:.0f}%")
                self.cpu_label.setStyleSheet(f"color: {color}; font-size: 12px;")
            except Exception:
                pass
