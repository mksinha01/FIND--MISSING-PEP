"""Settings configuration dialog for Edge Agent parameters, thresholds, and localization."""
import logging
from typing import Any, Dict, Optional

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QComboBox,
    QDialog,
    QDoubleSpinBox,
    QFormLayout,
    QGroupBox,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QPushButton,
    QSpinBox,
    QVBoxLayout,
    QWidget,
)

from edge_agent.config import EdgeSettings

logger = logging.getLogger(__name__)


class SettingsDialog(QDialog):
    """
    Dialog for viewing and editing Edge Agent settings:
    - Localization (English / Hindi)
    - Biometric Thresholds (SCRFD detection, FAISS candidate cutoff, Temporal threshold)
    - Backend Connection URLs & Keys
    """

    settings_saved = Signal(object)  # Emits updated EdgeSettings

    def __init__(
        self,
        settings: Optional[EdgeSettings] = None,
        parent: Optional[QWidget] = None,
    ):
        super().__init__(parent)
        self.settings = settings or EdgeSettings()
        self.setWindowTitle("Settings — Edge Agent")
        self.setMinimumSize(480, 420)
        self._init_ui()

    def _init_ui(self) -> None:
        main_layout = QVBoxLayout(self)
        main_layout.setContentsMargins(16, 16, 16, 16)
        main_layout.setSpacing(14)

        # 1. General & Localization
        gen_group = QGroupBox("General & Localization")
        gen_layout = QFormLayout(gen_group)
        gen_layout.setContentsMargins(12, 12, 12, 12)
        gen_layout.setSpacing(10)

        self.lang_combo = QComboBox()
        self.lang_combo.addItem("English (en)", "en")
        self.lang_combo.addItem("Hindi / हिन्दी (hi)", "hi")
        if self.settings.ui.language == "hi":
            self.lang_combo.setCurrentIndex(1)
        else:
            self.lang_combo.setCurrentIndex(0)
        gen_layout.addRow("Language / भाषा:", self.lang_combo)

        self.theme_combo = QComboBox()
        self.theme_combo.addItem("Dark Theme (Default)", "dark")
        self.theme_combo.addItem("Light Theme", "light")
        gen_layout.addRow("Interface Theme:", self.theme_combo)

        main_layout.addWidget(gen_group)

        # 2. AI & Biometric Thresholds
        ai_group = QGroupBox("AI Recognition & Verification Thresholds")
        ai_layout = QFormLayout(ai_group)
        ai_layout.setContentsMargins(12, 12, 12, 12)
        ai_layout.setSpacing(10)

        self.detect_thresh_spin = QDoubleSpinBox()
        self.detect_thresh_spin.setRange(0.1, 1.0)
        self.detect_thresh_spin.setSingleStep(0.05)
        self.detect_thresh_spin.setValue(self.settings.ai.face_detect_threshold)
        ai_layout.addRow("Face Detect Cutoff (SCRFD):", self.detect_thresh_spin)

        self.faiss_cutoff_spin = QDoubleSpinBox()
        self.faiss_cutoff_spin.setRange(0.2, 0.9)
        self.faiss_cutoff_spin.setSingleStep(0.05)
        self.faiss_cutoff_spin.setValue(self.settings.ai.face_similarity_threshold)
        ai_layout.addRow("FAISS Candidate Cutoff:", self.faiss_cutoff_spin)

        self.temporal_thresh_spin = QDoubleSpinBox()
        self.temporal_thresh_spin.setRange(0.4, 0.95)
        self.temporal_thresh_spin.setSingleStep(0.02)
        self.temporal_thresh_spin.setValue(self.settings.tracking.temporal_threshold)
        ai_layout.addRow("Temporal Match Threshold (Mean):", self.temporal_thresh_spin)

        self.window_size_spin = QSpinBox()
        self.window_size_spin.setRange(2, 10)
        self.window_size_spin.setValue(self.settings.tracking.temporal_window_size)
        ai_layout.addRow("Consecutive Match Window (N):", self.window_size_spin)

        main_layout.addWidget(ai_group)

        # 3. Backend REST Connection
        backend_group = QGroupBox("Backend Server")
        backend_layout = QFormLayout(backend_group)
        backend_layout.setContentsMargins(12, 12, 12, 12)
        backend_layout.setSpacing(10)

        self.backend_url_input = QLineEdit(self.settings.backend.url)
        backend_layout.addRow("Backend URL:", self.backend_url_input)

        self.enrollment_key_input = QLineEdit(self.settings.backend.enrollment_key)
        self.enrollment_key_input.setEchoMode(QLineEdit.Password)
        backend_layout.addRow("Enrollment Key:", self.enrollment_key_input)

        main_layout.addWidget(backend_group)

        main_layout.addStretch()

        # Action Buttons
        btn_layout = QHBoxLayout()
        btn_layout.addStretch()

        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        btn_layout.addWidget(cancel_btn)

        save_btn = QPushButton("Save Settings")
        save_btn.setObjectName("primaryButton")
        save_btn.clicked.connect(self._save_settings)
        btn_layout.addWidget(save_btn)

        main_layout.addLayout(btn_layout)

    def _save_settings(self) -> None:
        """Apply form values to settings object."""
        self.settings.ui.language = self.lang_combo.currentData()
        self.settings.ui.theme = self.theme_combo.currentData()
        self.settings.ai.face_detect_threshold = self.detect_thresh_spin.value()
        self.settings.ai.face_similarity_threshold = self.faiss_cutoff_spin.value()
        self.settings.tracking.temporal_threshold = self.temporal_thresh_spin.value()
        self.settings.tracking.temporal_window_size = self.window_size_spin.value()
        self.settings.backend.url = self.backend_url_input.text().strip()
        self.settings.backend.enrollment_key = self.enrollment_key_input.text().strip()

        try:
            self.settings.save_to_ini()
        except Exception as e:
            logger.warning(f"Could not persist settings to INI: {e}")

        self.settings_saved.emit(self.settings)
        self.accept()
