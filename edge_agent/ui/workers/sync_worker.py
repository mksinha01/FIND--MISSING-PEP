"""Background synchronization worker executing embedding sync and sighting uploads in a QThread."""
import logging
import threading
import time
from typing import Any, Dict, Optional

from PySide6.QtCore import QThread, Signal

from edge_agent.network.embedding_syncer import EmbeddingSyncer
from edge_agent.network.sighting_uploader import SightingUploader
from edge_agent.storage.local_db import SQLiteStore

logger = logging.getLogger(__name__)


class SyncWorker(QThread):
    """
    QThread background worker orchestrating periodic and on-demand synchronization:
    1. Downloads new/modified face embeddings from backend via EmbeddingSyncer.
    2. Flushes offline queued sightings to backend via SightingUploader.
    3. Queries local SQLite metrics and emits stats for the GUI status bar.
    """

    sync_started = Signal()
    sync_finished = Signal(dict)
    sync_failed = Signal(str)
    stats_updated = Signal(dict)  # cases_count, embeddings_count, pending_sightings, last_sync

    def __init__(
        self,
        syncer: Optional[EmbeddingSyncer] = None,
        uploader: Optional[SightingUploader] = None,
        local_db: Optional[SQLiteStore] = None,
        sync_interval_seconds: int = 60,
        parent: Optional[Any] = None,
    ):
        super().__init__(parent)
        self.syncer = syncer
        self.uploader = uploader
        self.local_db = local_db
        self.sync_interval_seconds = sync_interval_seconds

        self._stop_event = threading.Event()
        self._sync_trigger = threading.Event()

    @property
    def is_running(self) -> bool:
        return not self._stop_event.is_set()

    def stop(self) -> None:
        """Cooperative thread shutdown request (Fix #18 / Fix #26)."""
        logger.info("[SyncWorker] Stop signal received")
        self._stop_event.set()
        self._sync_trigger.set()  # Wake up sleeping loop immediately

    def trigger_sync(self) -> None:
        """Triggers an immediate manual sync cycle from GUI."""
        logger.info("[SyncWorker] Immediate sync triggered")
        self._sync_trigger.set()

    def _collect_stats(self) -> Dict[str, Any]:
        """Collect current SQLite storage metrics."""
        stats = {
            "cases_count": 0,
            "embeddings_count": 0,
            "pending_sightings": 0,
            "last_sync": "Never",
        }
        if self.local_db is not None:
            try:
                stats["cases_count"] = self.local_db.count_persons()
                stats["embeddings_count"] = self.local_db.count_embeddings()
                pending = self.local_db.get_pending_sightings(limit=100)
                stats["pending_sightings"] = len(pending)
                last_sync = self.local_db.get_sync_state("last_synced_at")
                if last_sync:
                    stats["last_sync"] = last_sync.split("T")[-1][:8]
            except Exception as e:
                logger.warning(f"[SyncWorker] Failed to collect SQLite stats: {e}")
        return stats

    def _run_sync_cycle(self) -> None:
        """Execute one complete synchronization and upload cycle."""
        self.sync_started.emit()
        summary = {"added": 0, "pruned": 0, "uploaded": 0}
        error_msg = None

        # 1. Sync embeddings
        if self.syncer is not None:
            try:
                sync_res = self.syncer.sync_now()
                summary.update(sync_res)
            except Exception as se:
                logger.warning(f"[SyncWorker] Embedding sync warning: {se}")
                error_msg = str(se)

        # 2. Upload pending sightings
        if self.uploader is not None:
            try:
                uploaded_count = self.uploader.process_queue_once()
                summary["uploaded"] = uploaded_count
            except Exception as ue:
                logger.warning(f"[SyncWorker] Sighting upload warning: {ue}")
                if not error_msg:
                    error_msg = str(ue)

        # 3. Update stats
        stats = self._collect_stats()
        self.stats_updated.emit(stats)

        if error_msg:
            self.sync_failed.emit(error_msg)
        else:
            self.sync_finished.emit(summary)

    def run(self) -> None:
        """Periodic background sync loop."""
        logger.info("[SyncWorker] Background sync thread started")

        # Initial collection and startup sync
        self.stats_updated.emit(self._collect_stats())
        time.sleep(1.0)  # Brief initial grace period
        if not self._stop_event.is_set():
            self._run_sync_cycle()

        while not self._stop_event.is_set():
            # Wait for sync interval or until explicitly triggered
            triggered = self._sync_trigger.wait(timeout=self.sync_interval_seconds)
            self._sync_trigger.clear()

            if self._stop_event.is_set():
                break

            self._run_sync_cycle()

        logger.info("[SyncWorker] Thread exiting cleanly")
