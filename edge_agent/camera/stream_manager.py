"""Multi-camera stream orchestrator and backend UUID synchronizer."""
import logging
import threading
from typing import Any, Dict, List, Optional

from edge_agent.camera.rtsp_reader import RTSPReader, StreamStatus
from edge_agent.network.api_client import ApiClient
from edge_agent.storage.local_db import SQLiteStore

logger = logging.getLogger(__name__)


class StreamManager:
    """
    Orchestrates the lifecycle of 1 to 4 concurrent camera streams.
    
    Responsibilities:
    - Maintains active RTSPReader instances for local camera channels (CAM-01 to CAM-04).
    - Enforces maximum concurrency limit (1–4 cameras for MVP edge hardware).
    - Synchronizes local camera IDs with remote backend UUIDs via ApiClient.
    - Persists camera mappings in SQLite to resolve local IDs to backend UUIDs.
    - Provides cooperative shutdown across all running streams.
    """

    MAX_CAMERAS: int = 4

    def __init__(
        self,
        agent_id: Optional[str] = None,
        api_client: Optional[ApiClient] = None,
        local_db: Optional[SQLiteStore] = None,
        max_cameras: int = MAX_CAMERAS,
    ):
        self.agent_id = str(agent_id or "")
        self.api_client = api_client
        self.local_db = local_db
        self.max_cameras = max_cameras

        self._readers: Dict[str, RTSPReader] = {}
        self._metadata: Dict[str, Dict[str, Any]] = {}
        self._lock = threading.RLock()

    @property
    def camera_count(self) -> int:
        with self._lock:
            return len(self._readers)

    def add_camera(
        self,
        local_camera_id: str,
        rtsp_url: str,
        name: Optional[str] = None,
        location: Optional[str] = None,
        latitude: Optional[float] = None,
        longitude: Optional[float] = None,
        resolution: Optional[str] = None,
        autostart: bool = True,
        hw_accel: bool = True,
    ) -> RTSPReader:
        """
        Register a new camera channel and optionally start ingestion.
        
        Args:
            local_camera_id: Human-readable identifier (e.g., 'CAM-01').
            rtsp_url: Target RTSP stream URL.
            name: Display name.
            location: Physical placement description.
            latitude: GPS latitude.
            longitude: GPS longitude.
            resolution: Stream resolution string (e.g. '1920x1080').
            autostart: Whether to start the reader thread immediately.
            hw_accel: Enable hardware decoding acceleration.
            
        Returns:
            The configured RTSPReader instance.
            
        Raises:
            ValueError: If max_cameras limit is exceeded or invalid arguments.
        """
        with self._lock:
            if local_camera_id in self._readers:
                logger.warning(
                    f"Camera '{local_camera_id}' already registered. Replacing existing stream..."
                )
                self.remove_camera(local_camera_id)

            if len(self._readers) >= self.max_cameras:
                raise ValueError(
                    f"Maximum concurrent camera limit ({self.max_cameras}) reached. "
                    f"Cannot register '{local_camera_id}'."
                )

            reader = RTSPReader(
                camera_id=local_camera_id,
                rtsp_url=rtsp_url,
                hw_accel=hw_accel,
            )

            self._readers[local_camera_id] = reader
            self._metadata[local_camera_id] = {
                "local_camera_id": local_camera_id,
                "name": name or local_camera_id,
                "rtsp_url": rtsp_url,
                "location": location,
                "latitude": latitude,
                "longitude": longitude,
                "resolution": resolution,
            }

            if autostart:
                reader.start()

            logger.info(
                f"Registered camera '{local_camera_id}' (Total active: {len(self._readers)}/{self.max_cameras})"
            )
            return reader

    def remove_camera(self, local_camera_id: str) -> bool:
        """
        Cooperatively stop and remove a camera channel.
        
        Returns:
            True if camera existed and was removed, False otherwise.
        """
        with self._lock:
            reader = self._readers.pop(local_camera_id, None)
            self._metadata.pop(local_camera_id, None)

        if reader is not None:
            reader.stop()
            logger.info(f"Removed and stopped camera '{local_camera_id}'")
            return True
        return False

    def get_camera(self, local_camera_id: str) -> Optional[RTSPReader]:
        """Retrieve an RTSPReader instance by local camera ID."""
        with self._lock:
            return self._readers.get(local_camera_id)

    def get_all_cameras(self) -> Dict[str, RTSPReader]:
        """Return a copy of all active local camera readers."""
        with self._lock:
            return dict(self._readers)

    def get_active_camera_ids(self) -> List[str]:
        """Return list of all registered local camera IDs."""
        with self._lock:
            return list(self._readers.keys())

    def get_camera_uuid(self, local_camera_id: str) -> Optional[str]:
        """
        Query local SQLite store to resolve local camera ID (e.g. 'CAM-01')
        to its corresponding backend UUID.
        """
        if self.local_db:
            return self.local_db.get_camera_uuid(local_camera_id)
        return None

    def get_local_camera_id(self, backend_uuid: str) -> Optional[str]:
        """Query local SQLite store to resolve backend UUID back to local camera ID."""
        if self.local_db:
            return self.local_db.get_local_camera_id(backend_uuid)
        return None

    def sync_with_backend(
        self,
        api_client: Optional[ApiClient] = None,
        agent_id: Optional[str] = None,
    ) -> Dict[str, str]:
        """
        Synchronize registered camera metadata with the backend API.
        POST /api/agents/{agent_id}/cameras/sync
        
        Persists returned local_camera_id -> backend_uuid mappings into SQLite.
        
        Returns:
            Dict mapping local_camera_id -> backend_uuid.
        """
        client = api_client or self.api_client
        target_agent_id = agent_id or self.agent_id

        if not client:
            raise ValueError("Cannot sync cameras: no ApiClient provided.")
        if not target_agent_id:
            raise ValueError("Cannot sync cameras: no agent_id configured.")

        with self._lock:
            cameras_payload = list(self._metadata.values())

        logger.info(
            f"Synchronizing {len(cameras_payload)} cameras with backend for agent '{target_agent_id}'..."
        )

        mappings = client.sync_cameras(
            agent_id=target_agent_id,
            cameras=cameras_payload,
        )

        logger.info(f"Received {len(mappings)} camera mapping(s) from backend.")

        # Persist mappings into local SQLite store
        if self.local_db and mappings:
            self.local_db.save_camera_mappings(mappings)
            logger.info("Persisted camera mappings to local database.")

        return mappings

    def start_all(self) -> None:
        """Start all registered camera streams."""
        with self._lock:
            readers = list(self._readers.values())

        for reader in readers:
            reader.start()

    def stop_all(self, timeout: float = 3.0) -> None:
        """
        Cooperatively stop all registered camera streams and join threads.
        """
        with self._lock:
            readers = list(self._readers.values())

        logger.info(f"Stopping all {len(readers)} camera streams cooperatively...")
        for reader in readers:
            reader.stop(timeout=timeout)
        logger.info("All camera streams stopped cleanly.")

    def get_stats(self) -> Dict[str, Any]:
        """Return aggregate telemetry statistics across all camera channels."""
        with self._lock:
            readers = dict(self._readers)

        stats: Dict[str, Any] = {
            "total_cameras": len(readers),
            "max_cameras": self.max_cameras,
            "cameras": {},
        }

        for cam_id, reader in readers.items():
            cam_stats = reader.get_stats()
            backend_uuid = self.get_camera_uuid(cam_id)
            cam_stats["backend_uuid"] = backend_uuid
            stats["cameras"][cam_id] = cam_stats

        return stats
