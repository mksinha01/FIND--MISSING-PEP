"""Device enrollment and registration dialog communicating with central backend."""
import logging
from typing import Any, Dict, Optional
import uuid

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QDialog,
    QFormLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QMessageBox,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

from edge_agent.config import EdgeSettings
from edge_agent.network.api_client import ApiClient

logger = logging.getLogger(__name__)


class LoginDialog(QDialog):
    """
    Device enrollment dialog:
    Authenticates Edge Agent with central backend using the admin enrollment secret key.
    """

    enrolled = Signal(dict)  # Emits agent registration payload (api_key, agent_id)

    def __init__(
        self,
        settings: Optional[EdgeSettings] = None,
        api_client: Optional[ApiClient] = None,
        parent: Optional[QWidget] = None,
    ):
        super().__init__(parent)
        self.settings = settings or EdgeSettings()
        self.api_client = api_client
        self.setWindowTitle("Device Registration — Find Missing Person")
        self.setMinimumSize(420, 260)
        self._init_ui()

    def _init_ui(self) -> None:
        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 18, 18, 18)
        layout.setSpacing(14)

        title = QLabel("Register Edge Agent Device")
        title.setStyleSheet("color: #f4f4f5; font-size: 14px; font-weight: 700;")
        layout.addWidget(title)

        form = QFormLayout()
        form.setSpacing(10)

        # Device ID
        dev_id = self.settings.agent.device_id or str(uuid.uuid4())
        self.device_id_input = QLineEdit(dev_id)
        form.addRow("Device UUID:", self.device_id_input)

        # Location Name
        self.location_input = QLineEdit(self.settings.agent.location_name or "Main Entrance")
        form.addRow("Location Name:", self.location_input)

        # Enrollment Key
        self.key_input = QLineEdit(self.settings.backend.enrollment_key or "")
        self.key_input.setEchoMode(QLineEdit.Password)
        self.key_input.setPlaceholderText("Enter admin enrollment secret key...")
        form.addRow("Enrollment Key:", self.key_input)

        layout.addLayout(form)

        self.status_label = QLabel("")
        self.status_label.setStyleSheet("color: #a1a1aa; font-size: 12px;")
        layout.addWidget(self.status_label)

        layout.addStretch()

        # Buttons
        btn_layout = QHBoxLayout()
        btn_layout.addStretch()

        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        btn_layout.addWidget(cancel_btn)

        self.enroll_btn = QPushButton("Enroll Device")
        self.enroll_btn.setObjectName("primaryButton")
        self.enroll_btn.clicked.connect(self._do_enrollment)
        btn_layout.addWidget(self.enroll_btn)

        layout.addLayout(btn_layout)

    def _do_enrollment(self) -> None:
        """Call backend to register edge device."""
        device_id = self.device_id_input.text().strip()
        location = self.location_input.text().strip()
        key = self.key_input.text().strip()

        if not key:
            QMessageBox.warning(self, "Input Error", "Please provide the admin enrollment key.")
            return

        self.status_label.setText("Connecting to backend server...")
        self.status_label.setStyleSheet("color: #38bdf8;")

        try:
            client = self.api_client or ApiClient(
                base_url=self.settings.backend.url,
                enrollment_key=key,
            )
            resp = client.register_device(
                device_id=device_id,
                name=f"Edge Agent ({location})",
                location=location,
            )

            api_key = resp.get("api_key", "")
            if api_key:
                self.settings.backend.api_key = api_key
                self.settings.agent.device_id = device_id
                self.settings.agent.location_name = location
                self.settings.save_to_ini()

            self.status_label.setText("Device enrolled successfully!")
            self.status_label.setStyleSheet("color: #10b981;")
            self.enrolled.emit(resp)
            self.accept()
        except Exception as e:
            logger.error(f"Device enrollment failed: {e}")
            self.status_label.setText(f"Enrollment failed: {e}")
            self.status_label.setStyleSheet("color: #ef4444;")
