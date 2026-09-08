"""Durable offline sighting queue processor with exponential retry backoff."""
import logging
import os
import random
import threading
import time
from datetime import datetime, timezone
from typing import Any, Callable, Dict, List, Optional

from edge_agent.network.api_client import ApiClient, ApiClientError
from edge_agent.storage.evidence_store import EvidenceStore
from edge_agent.storage.local_db import SQLiteStore

logger = logging.getLogger(__name__)


class SightingUploader:
    """
    Processes local pending sightings queued in SQLite.
    Guarantees reliable upload with exponential backoff retry during network interruptions (Fix #18).
    Translates local camera names to remote backend UUIDs (Fix #17).
    """

    def __init__(
        self,
        api_client: ApiClient,
        local_db: SQLiteStore,
        evidence_store: Optional[EvidenceStore] = None,
        poll_interval_seconds: int = 5,
        max_retries: int = 15,
        initial_backoff_seconds: float = 2.0,
        max_backoff_seconds: float = 300.0,
        backoff_multiplier: float = 2.0,
        cleanup_evidence_on_upload: bool = False,
        on_upload_success: Optional[Callable[[int, Dict[str, Any]], None]] = None,
    ):
        self.api_client = api_client
        self.local_db = local_db
        self.evidence_store = evidence_store
        self.poll_interval_seconds = poll_interval_seconds
        self.max_retries = max_retries
        self.initial_backoff_seconds = initial_backoff_seconds
        self.max_backoff_seconds = max_backoff_seconds
        self.backoff_multiplier = backoff_multiplier
        self.cleanup_evidence_on_upload = cleanup_evidence_on_upload
        self.on_upload_success = on_upload_success

        self._stop_event = threading.Event()
        self._thread: Optional[threading.Thread] = None

    def calculate_backoff(self, retry_count: int) -> float:
        """Calculate exponential backoff with jitter."""
        if retry_count <= 0:
            return 0.0
        backoff = self.initial_backoff_seconds * (self.backoff_multiplier ** (retry_count - 1))
        backoff = min(backoff, self.max_backoff_seconds)
        jitter = random.uniform(0, 0.5 * backoff)
        return backoff + jitter

    def _should_attempt_upload(self, sighting: Dict[str, Any], now_epoch: float) -> bool:
        """Determine if sufficient backoff time has elapsed since last attempt."""
        retry_count = sighting.get("retry_count", 0)
        if retry_count == 0:
            return True

        if retry_count >= self.max_retries:
            logger.warning(
                f"Sighting {sighting.get('id')} reached max retries ({self.max_retries}). Still queued for recovery."
            )

        last_attempt = sighting.get("last_attempt_at")
        if not last_attempt:
            return True

        try:
            last_epoch = datetime.fromisoformat(last_attempt).timestamp()
        except Exception:
            return True

        required_backoff = self.calculate_backoff(retry_count)
        return (now_epoch - last_epoch) >= required_backoff

    def process_queue_once(self, batch_size: int = 10) -> int:
        """
        Process a single batch of pending sightings from SQLite.
        Returns the number of successfully uploaded sightings.
        """
        pending = self.local_db.get_pending_sightings(limit=batch_size)
        if not pending:
            return 0

        now_epoch = time.time()
        success_count = 0

        for item in pending:
            sighting_id = item["id"]

            if not self._should_attempt_upload(item, now_epoch):
                continue

            # 1. Resolve camera ID (Local name e.g. "CAM-01" -> Backend UUID)
            raw_camera_id = item["camera_id"]
            backend_camera_uuid = self.local_db.get_camera_uuid(raw_camera_id) or raw_camera_id

            # 2. Read evidence files
            face_crop_path = item.get("face_crop_path")
            full_frame_path = item.get("full_frame_path")
            video_clip_path = item.get("video_clip_path")

            if not face_crop_path or not os.path.isfile(face_crop_path):
                logger.error(f"Sighting {sighting_id} missing face crop file: {face_crop_path}")
                self.local_db.increment_retry_count(sighting_id)
                continue

            if not full_frame_path or not os.path.isfile(full_frame_path):
                logger.error(f"Sighting {sighting_id} missing full frame file: {full_frame_path}")
                self.local_db.increment_retry_count(sighting_id)
                continue

            try:
                with open(face_crop_path, "rb") as f:
                    face_crop_bytes = f.read()
                with open(full_frame_path, "rb") as f:
                    full_frame_bytes = f.read()

                video_clip_bytes = None
                if video_clip_path and os.path.isfile(video_clip_path):
                    with open(video_clip_path, "rb") as f:
                        video_clip_bytes = f.read()
            except OSError as e:
                logger.error(f"Failed to read evidence files for sighting {sighting_id}: {e}")
                self.local_db.increment_retry_count(sighting_id)
                continue

            # 3. Upload to backend
            try:
                resp = self.api_client.upload_sighting(
                    person_id=item["person_id"],
                    camera_id=backend_camera_uuid,
                    similarity_score=item["similarity_score"],
                    detected_at=item["detected_at"],
                    face_crop_bytes=face_crop_bytes,
                    full_frame_bytes=full_frame_bytes,
                    video_clip_bytes=video_clip_bytes,
                    confidence_level=item.get("confidence_level", "POSSIBLE"),
                    num_frames_matched=item.get("num_frames_matched"),
                    camera_location=item.get("camera_location"),
                    latitude=item.get("latitude"),
                    longitude=item.get("longitude"),
                )

                # 4. Successful upload -> remove from SQLite queue
                self.local_db.delete_pending_sighting(sighting_id)
                success_count += 1
                logger.info(f"Successfully uploaded sighting {sighting_id} (person: {item['person_id']})")

                if self.cleanup_evidence_on_upload and self.evidence_store:
                    self.evidence_store.delete_file(face_crop_path)
                    self.evidence_store.delete_file(full_frame_path)
                    if video_clip_path:
                        self.evidence_store.delete_file(video_clip_path)

                if self.on_upload_success:
                    try:
                        self.on_upload_success(sighting_id, resp)
                    except Exception as cb_err:
                        logger.warning(f"Error in on_upload_success callback: {cb_err}")

            except ApiClientError as api_err:
                logger.warning(
                    f"Failed to upload sighting {sighting_id} (attempt {item.get('retry_count', 0) + 1}): {api_err}"
                )
                self.local_db.increment_retry_count(sighting_id)
            except Exception as unk_err:
                logger.error(f"Unexpected error uploading sighting {sighting_id}: {unk_err}")
                self.local_db.increment_retry_count(sighting_id)

        return success_count

    def _run_worker(self) -> None:
        """Background loop constantly draining offline queue."""
        logger.info("SightingUploader worker thread started")
        while not self._stop_event.is_set():
            try:
                self.process_queue_once()
            except Exception as e:
                logger.warning(f"SightingUploader loop encountered error: {e}")

            # Sleep in short intervals
            for _ in range(self.poll_interval_seconds):
                if self._stop_event.is_set():
                    break
                time.sleep(1)

        logger.info("SightingUploader worker thread stopped")

    def start(self) -> None:
        """Start background queue processor thread."""
        if self._thread is not None and self._thread.is_alive():
            logger.warning("SightingUploader worker is already running")
            return
        self._stop_event.clear()
        self._thread = threading.Thread(target=self._run_worker, daemon=True, name="SightingUploaderThread")
        self._thread.start()

    def stop(self, timeout: float = 3.0) -> None:
        """Cooperative thread shutdown signal."""
        self._stop_event.set()
        if self._thread is not None and self._thread.is_alive():
            self._thread.join(timeout=timeout)
            self._thread = None
