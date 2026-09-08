"""Camera channel configuration and ONVIF discovery dialog."""
import logging
from typing import Any, Dict, List, Optional

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QDialog,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QLineEdit,
    QMessageBox,
    QPushButton,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)

from edge_agent.camera.onvif_discovery import ONVIFDiscovery

logger = logging.getLogger(__name__)


class CameraConfigDialog(QDialog):
    """
    Dialog for configuring camera channels (1–4 cameras):
    - Add, edit, or remove RTSP streams.
    - Automatic ONVIF WS-Discovery to locate network cameras.
    """

    cameras_updated = Signal(list)  # Emits list of camera dictionaries

    def __init__(
        self,
        existing_cameras: Optional[List[Dict[str, Any]]] = None,
        max_cameras: int = 4,
        parent: Optional[QWidget] = None,
    ):
        super().__init__(parent)
        self.max_cameras = max_cameras
        self.cameras: List[Dict[str, Any]] = list(existing_cameras or [])
        self.setWindowTitle("Camera Configuration — 1 to 4 Feeds")
        self.setMinimumSize(600, 380)
        self._init_ui()

    def _init_ui(self) -> None:
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(12)

        header = QLabel("Configure Local CCTV Ingestion Channels (Max 4 concurrent)")
        header.setStyleSheet("color: #f4f4f5; font-size: 13px; font-weight: 600;")
        layout.addWidget(header)

        # Table showing cameras
        self.table = QTableWidget()
        self.table.setColumnCount(3)
        self.table.setHorizontalHeaderLabels(["Channel ID", "Display Name", "RTSP Stream URL"])
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(2, QHeaderView.Stretch)
        self.table.setSelectionBehavior(QTableWidget.SelectRows)
        self.table.setSelectionMode(QTableWidget.SingleSelection)
        layout.addWidget(self.table)

        self._populate_table()

        # Helpful format hint
        hint_label = QLabel(
            "💡 Sources: 0 for Laptop Webcam | test_cctv_feed.mp4 for video file | rtsp://user:pass@ip:554/live for real CCTV"
        )
        hint_label.setStyleSheet("color: #a1a1aa; font-size: 11px; font-style: italic;")
        layout.addWidget(hint_label)

        # Toolbar buttons
        tool_layout = QHBoxLayout()
        self.add_btn = QPushButton("+ Add Camera")
        self.add_btn.clicked.connect(self._add_camera_row)
        tool_layout.addWidget(self.add_btn)

        self.webcam_btn = QPushButton("📹 Use Laptop Webcam (0)")
        self.webcam_btn.clicked.connect(self._use_webcam)
        tool_layout.addWidget(self.webcam_btn)

        self.browse_btn = QPushButton("📁 Browse Video File...")
        self.browse_btn.clicked.connect(self._browse_video_file)
        tool_layout.addWidget(self.browse_btn)

        self.remove_btn = QPushButton("- Remove Selected")
        self.remove_btn.clicked.connect(self._remove_camera_row)
        tool_layout.addWidget(self.remove_btn)

        self.discover_btn = QPushButton("🔍 Discover ONVIF")
        self.discover_btn.clicked.connect(self._discover_onvif)
        tool_layout.addWidget(self.discover_btn)

        tool_layout.addStretch()
        layout.addLayout(tool_layout)

        # Dialog buttons
        btn_layout = QHBoxLayout()
        btn_layout.addStretch()

        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        btn_layout.addWidget(cancel_btn)

        save_btn = QPushButton("Save Cameras")
        save_btn.setObjectName("primaryButton")
        save_btn.clicked.connect(self._save_cameras)
        btn_layout.addWidget(save_btn)

        layout.addLayout(btn_layout)

    def _populate_table(self) -> None:
        """Populate table with current camera list."""
        self.table.setRowCount(0)
        for i, cam in enumerate(self.cameras):
            self.table.insertRow(i)
            self.table.setItem(i, 0, QTableWidgetItem(cam.get("local_camera_id", f"CAM-{i+1:02d}")))
            self.table.setItem(i, 1, QTableWidgetItem(cam.get("name", f"Camera {i+1}")))
            self.table.setItem(i, 2, QTableWidgetItem(cam.get("rtsp_url", "")))

    def _use_webcam(self) -> None:
        """Configure selected row or first row to use Laptop/USB Webcam (device 0)."""
        selected = self.table.currentRow()
        if selected < 0:
            if self.table.rowCount() == 0:
                self._add_camera_row()
                selected = 0
            else:
                selected = 0
        self.table.setItem(selected, 1, QTableWidgetItem("Laptop Webcam"))
        self.table.setItem(selected, 2, QTableWidgetItem("0"))

    def _browse_video_file(self) -> None:
        """Browse for local MP4/AVI/MKV video files for testing."""
        from PySide6.QtWidgets import QFileDialog
        file_path, _ = QFileDialog.getOpenFileName(
            self,
            "Select CCTV Video File",
            "",
            "Video Files (*.mp4 *.avi *.mkv *.mov);;All Files (*.*)",
        )
        if file_path:
            selected = self.table.currentRow()
            if selected < 0:
                if self.table.rowCount() < self.max_cameras:
                    self._add_camera_row()
                    selected = self.table.rowCount() - 1
                else:
                    selected = 0

            self.table.setItem(selected, 2, QTableWidgetItem(file_path))

    def _add_camera_row(self) -> None:
        """Add new row if under max_cameras limit."""
        if self.table.rowCount() >= self.max_cameras:
            QMessageBox.warning(self, "Limit Reached", f"Maximum {self.max_cameras} cameras supported for MVP edge hardware.")
            return

        idx = self.table.rowCount() + 1
        self.table.insertRow(self.table.rowCount())
        row = self.table.rowCount() - 1
        self.table.setItem(row, 0, QTableWidgetItem(f"CAM-{idx:02d}"))
        self.table.setItem(row, 1, QTableWidgetItem(f"Camera {idx}"))
        self.table.setItem(row, 2, QTableWidgetItem("0"))

    def _remove_camera_row(self) -> None:
        """Remove selected camera row."""
        selected = self.table.currentRow()
        if selected >= 0:
            self.table.removeRow(selected)

    def _discover_onvif(self) -> None:
        """Execute ONVIF discovery and add discovered streams."""
        try:
            discovery = ONVIFDiscovery(timeout=2.0)
            results = discovery.discover()
            if not results:
                QMessageBox.information(self, "ONVIF Discovery", "No ONVIF cameras discovered on the local network subnet.")
                return

            added = 0
            for cam in results:
                if self.table.rowCount() >= self.max_cameras:
                    break
                idx = self.table.rowCount() + 1
                row = self.table.rowCount()
                self.table.insertRow(row)
                self.table.setItem(row, 0, QTableWidgetItem(f"CAM-{idx:02d}"))
                self.table.setItem(row, 1, QTableWidgetItem(cam.name or f"ONVIF Cam {idx}"))
                self.table.setItem(row, 2, QTableWidgetItem(cam.rtsp_url or ""))
                added += 1

            QMessageBox.information(self, "ONVIF Discovery", f"Discovered and added {added} camera(s).")
        except Exception as e:
            logger.error(f"ONVIF discovery error: {e}")
            QMessageBox.warning(self, "Discovery Error", f"Discovery failed: {e}")

    def _save_cameras(self) -> None:
        """Extract camera definitions and emit updated signal."""
        updated: List[Dict[str, Any]] = []
        for row in range(self.table.rowCount()):
            cid_item = self.table.item(row, 0)
            name_item = self.table.item(row, 1)
            url_item = self.table.item(row, 2)

            cid = cid_item.text().strip() if cid_item else f"CAM-{row+1:02d}"
            name = name_item.text().strip() if name_item else f"Camera {row+1}"
            url = url_item.text().strip() if url_item else ""

            if url:
                updated.append({
                    "local_camera_id": cid,
                    "name": name,
                    "rtsp_url": url,
                })

        self.cameras = updated
        self.cameras_updated.emit(updated)
        self.accept()
