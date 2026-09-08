"""Application entrypoint for the FIND-MISSING-PEP Edge Agent desktop GUI."""
import logging
import os
import signal
import sys

from PySide6.QtCore import QCoreApplication, Qt
from PySide6.QtGui import QIcon
from PySide6.QtWidgets import QApplication

from edge_agent.config import EdgeSettings
from edge_agent.ui.main_window import MainWindow
from edge_agent.utils.path_resolver import get_resource_path

# Configure structured logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(name)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("edge_agent.main")


def load_stylesheet(app: QApplication) -> None:
    """Load and apply the dark theme QSS stylesheet using get_resource_path."""
    candidate_paths = [
        get_resource_path(os.path.join("ui", "resources", "style.qss")),
        get_resource_path(os.path.join("edge_agent", "ui", "resources", "style.qss")),
        os.path.join(os.path.dirname(__file__), "ui", "resources", "style.qss"),
    ]

    for qss_path in candidate_paths:
        if qss_path and os.path.isfile(qss_path):
            try:
                with open(qss_path, "r", encoding="utf-8") as f:
                    app.setStyleSheet(f.read())
                logger.info(f"Loaded stylesheet from {qss_path}")
                return
            except Exception as e:
                logger.warning(f"Failed to load stylesheet from {qss_path}: {e}")

    logger.warning("No valid style.qss found; running with default Qt styling")


def main() -> int:
    """Initialize QApplication, configure resources, and launch MainWindow."""
    logger.info("Starting FIND-MISSING-PEP Edge Agent Desktop GUI...")

    # High-DPI scaling is enabled by default in Qt 6

    app = QApplication(sys.argv)
    app.setApplicationName("FIND-MISSING-PEP Edge Agent")
    app.setOrganizationName("FIND-MISSING-PEP")

    # Load dark stylesheet via resource path resolver (Fix #23)
    load_stylesheet(app)

    # Allow graceful termination on Ctrl+C in terminal
    signal.signal(signal.SIGINT, signal.SIG_DFL)

    # Load settings and instantiate main window
    settings = EdgeSettings.load_from_ini()
    window = MainWindow(settings=settings)
    window.show()

    logger.info("Edge Agent GUI event loop running")
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
