"""Edge Agent configuration loader and settings management."""
import configparser
import logging
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

from edge_agent.utils.path_resolver import get_resource_path

logger = logging.getLogger(__name__)


@dataclass
class BackendConfig:
    url: str = "http://localhost:8000"
    enrollment_key: str = "change-me-to-enrollment-key"
    api_key: str = ""


@dataclass
class AgentConfig:
    device_id: str = ""
    location_name: str = "Main Building"
    sync_interval_seconds: int = 60
    heartbeat_interval_seconds: int = 30
    db_path: str = "local_data.db"
    evidence_dir: str = "evidence"


@dataclass
class AIConfig:
    onnx_model_dir: str = "./models"
    face_detect_threshold: float = 0.5
    face_similarity_threshold: float = 0.50
    tracking_fps: int = 15
    quality_min_face_size: int = 40
    quality_max_blur: float = 100.0
    quality_min_aspect: float = 0.6
    quality_max_aspect: float = 1.2


@dataclass
class TrackingConfig:
    max_track_age: int = 30
    min_recognition_interval: float = 1.0
    temporal_window_size: int = 3
    temporal_threshold: float = 0.60
    match_cooldown_seconds: int = 300


@dataclass
class UIConfig:
    language: str = "en"
    theme: str = "dark"
    show_face_boxes: bool = True
    show_track_ids: bool = True


@dataclass
class EdgeSettings:
    backend: BackendConfig = field(default_factory=BackendConfig)
    agent: AgentConfig = field(default_factory=AgentConfig)
    ai: AIConfig = field(default_factory=AIConfig)
    tracking: TrackingConfig = field(default_factory=TrackingConfig)
    ui: UIConfig = field(default_factory=UIConfig)
    config_file_path: Optional[str] = None

    @classmethod
    def load_from_ini(cls, ini_path: Optional[str] = None) -> "EdgeSettings":
        """Load settings from an INI file with fallbacks."""
        parser = configparser.ConfigParser()
        resolved_path = None

        candidate_paths = [
            ini_path,
            os.environ.get("EDGE_CONFIG_PATH"),
            os.path.join(os.getcwd(), "config.ini"),
            os.path.join(os.getcwd(), "edge_agent", "config.ini"),
            get_resource_path("config.ini"),
            get_resource_path(os.path.join("edge_agent", "config.ini")),
            os.path.join(os.path.dirname(__file__), "config.ini"),
        ]

        for path in candidate_paths:
            if path and os.path.isfile(path):
                resolved_path = os.path.abspath(path)
                break

        settings = cls(config_file_path=resolved_path)

        if resolved_path and os.path.isfile(resolved_path):
            try:
                parser.read(resolved_path, encoding="utf-8")
                logger.info(f"Loaded edge configuration from {resolved_path}")
            except Exception as e:
                logger.warning(f"Failed to read config file {resolved_path}: {e}")

        # Section: [backend]
        if parser.has_section("backend"):
            sec = parser["backend"]
            settings.backend.url = sec.get("url", settings.backend.url).rstrip("/")
            settings.backend.enrollment_key = sec.get("enrollment_key", settings.backend.enrollment_key)
            settings.backend.api_key = sec.get("api_key", settings.backend.api_key)

        # Section: [agent]
        if parser.has_section("agent"):
            sec = parser["agent"]
            settings.agent.device_id = sec.get("device_id", settings.agent.device_id)
            settings.agent.location_name = sec.get("location_name", settings.agent.location_name)
            settings.agent.sync_interval_seconds = sec.getint("sync_interval_seconds", settings.agent.sync_interval_seconds)
            settings.agent.heartbeat_interval_seconds = sec.getint("heartbeat_interval_seconds", settings.agent.heartbeat_interval_seconds)
            settings.agent.db_path = sec.get("db_path", settings.agent.db_path)
            settings.agent.evidence_dir = sec.get("evidence_dir", settings.agent.evidence_dir)

        # Section: [ai]
        if parser.has_section("ai"):
            sec = parser["ai"]
            settings.ai.onnx_model_dir = sec.get("onnx_model_dir", settings.ai.onnx_model_dir)
            settings.ai.face_detect_threshold = sec.getfloat("face_detect_threshold", settings.ai.face_detect_threshold)
            settings.ai.face_similarity_threshold = sec.getfloat("face_similarity_threshold", settings.ai.face_similarity_threshold)
            settings.ai.tracking_fps = sec.getint("tracking_fps", settings.ai.tracking_fps)
            settings.ai.quality_min_face_size = sec.getint("quality_min_face_size", settings.ai.quality_min_face_size)
            settings.ai.quality_max_blur = sec.getfloat("quality_max_blur", settings.ai.quality_max_blur)
            settings.ai.quality_min_aspect = sec.getfloat("quality_min_aspect", settings.ai.quality_min_aspect)
            settings.ai.quality_max_aspect = sec.getfloat("quality_max_aspect", settings.ai.quality_max_aspect)

        # Section: [tracking]
        if parser.has_section("tracking"):
            sec = parser["tracking"]
            settings.tracking.max_track_age = sec.getint("max_track_age", settings.tracking.max_track_age)
            settings.tracking.min_recognition_interval = sec.getfloat("min_recognition_interval", settings.tracking.min_recognition_interval)
            settings.tracking.temporal_window_size = sec.getint("temporal_window_size", settings.tracking.temporal_window_size)
            settings.tracking.temporal_threshold = sec.getfloat("temporal_threshold", settings.tracking.temporal_threshold)
            settings.tracking.match_cooldown_seconds = sec.getint("match_cooldown_seconds", settings.tracking.match_cooldown_seconds)

        # Section: [ui]
        if parser.has_section("ui"):
            sec = parser["ui"]
            settings.ui.language = sec.get("language", settings.ui.language)
            settings.ui.theme = sec.get("theme", settings.ui.theme)
            settings.ui.show_face_boxes = sec.getboolean("show_face_boxes", settings.ui.show_face_boxes)
            settings.ui.show_track_ids = sec.getboolean("show_track_ids", settings.ui.show_track_ids)

        return settings

    def save_to_ini(self, target_path: Optional[str] = None) -> str:
        """Persist current settings to INI file."""
        path = target_path or self.config_file_path or os.path.join(os.path.dirname(__file__), "config.ini")
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)

        parser = configparser.ConfigParser()
        parser["backend"] = {
            "url": self.backend.url,
            "enrollment_key": self.backend.enrollment_key,
            "api_key": self.backend.api_key,
        }
        parser["agent"] = {
            "device_id": self.agent.device_id,
            "location_name": self.agent.location_name,
            "sync_interval_seconds": str(self.agent.sync_interval_seconds),
            "heartbeat_interval_seconds": str(self.agent.heartbeat_interval_seconds),
            "db_path": self.agent.db_path,
            "evidence_dir": self.agent.evidence_dir,
        }
        parser["ai"] = {
            "onnx_model_dir": self.ai.onnx_model_dir,
            "face_detect_threshold": str(self.ai.face_detect_threshold),
            "face_similarity_threshold": str(self.ai.face_similarity_threshold),
            "tracking_fps": str(self.ai.tracking_fps),
            "quality_min_face_size": str(self.ai.quality_min_face_size),
            "quality_max_blur": str(self.ai.quality_max_blur),
            "quality_min_aspect": str(self.ai.quality_min_aspect),
            "quality_max_aspect": str(self.ai.quality_max_aspect),
        }
        parser["tracking"] = {
            "max_track_age": str(self.tracking.max_track_age),
            "min_recognition_interval": str(self.tracking.min_recognition_interval),
            "temporal_window_size": str(self.tracking.temporal_window_size),
            "temporal_threshold": str(self.tracking.temporal_threshold),
            "match_cooldown_seconds": str(self.tracking.match_cooldown_seconds),
        }
        parser["ui"] = {
            "language": self.ui.language,
            "theme": self.ui.theme,
            "show_face_boxes": str(self.ui.show_face_boxes).lower(),
            "show_track_ids": str(self.ui.show_track_ids).lower(),
        }

        with open(path, "w", encoding="utf-8") as f:
            parser.write(f)

        self.config_file_path = os.path.abspath(path)
        return self.config_file_path

    def get_api_url(self, endpoint: str) -> str:
        """Helper to construct full backend API URL."""
        base = self.backend.url.rstrip("/")
        ep = endpoint.lstrip("/")
        return f"{base}/{ep}"


# Singleton instance for global convenience
settings = EdgeSettings.load_from_ini()
