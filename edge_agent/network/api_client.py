"""HTTP Client communicating with FIND-MISSING-PEP backend REST API."""
import logging
from typing import Any, Dict, List, Optional, Tuple, Union
import httpx

logger = logging.getLogger(__name__)


class ApiClientError(Exception):
    """Base exception for API client errors."""
    pass


class AuthenticationError(ApiClientError):
    """Raised when authentication (API Key or Enrollment Key) fails."""
    pass


class NetworkError(ApiClientError):
    """Raised when connection drops, times out, or fails to reach server."""
    pass


class ServerError(ApiClientError):
    """Raised when server responds with 5xx error."""
    pass


class ApiClient:
    """
    Thread-safe synchronous HTTP client for communicating with backend API.
    Handles device registration, camera sync, heartbeats, embedding downloads,
    and multipart sighting uploads.
    """

    def __init__(
        self,
        base_url: str = "http://localhost:8000",
        api_key: Optional[str] = None,
        enrollment_key: Optional[str] = None,
        timeout: float = 15.0,
    ):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.enrollment_key = enrollment_key
        self.timeout = timeout
        self._client = httpx.Client(
            base_url=self.base_url,
            timeout=self.timeout,
            headers=self._build_default_headers(),
        )

    def _build_default_headers(self) -> Dict[str, str]:
        headers = {
            "Accept": "application/json",
            "User-Agent": "FindMissingPerson-EdgeAgent/1.0",
        }
        if self.api_key:
            headers["X-API-Key"] = self.api_key
        if self.enrollment_key:
            headers["X-Enrollment-Key"] = self.enrollment_key
        return headers

    def set_api_key(self, api_key: str) -> None:
        """Update API key and refresh client headers."""
        self.api_key = api_key
        self._client.headers.update(self._build_default_headers())

    def set_enrollment_key(self, enrollment_key: str) -> None:
        """Update enrollment key and refresh client headers."""
        self.enrollment_key = enrollment_key
        self._client.headers.update(self._build_default_headers())

    def _handle_response_error(self, response: httpx.Response) -> None:
        """Translate HTTP status codes to appropriate exceptions."""
        if response.status_code in (401, 403):
            raise AuthenticationError(
                f"Authentication failed ({response.status_code}): {response.text}"
            )
        elif response.status_code >= 500:
            raise ServerError(
                f"Server error ({response.status_code}): {response.text}"
            )
        elif response.is_error:
            raise ApiClientError(
                f"API request failed ({response.status_code}): {response.text}"
            )

    # ═════════════════════════════════════════════════════════════════════════
    # Endpoints
    # ═════════════════════════════════════════════════════════════════════════

    def register_device(
        self,
        device_id: str,
        name: str = "Edge CCTV Agent",
        location: str = "Main Building",
        version: str = "1.0.0",
    ) -> Dict[str, Any]:
        """
        Register a new edge device using the admin enrollment key.
        POST /api/agents/register
        """
        headers = {}
        if self.enrollment_key:
            headers["X-Enrollment-Key"] = self.enrollment_key

        payload = {
            "device_id": str(device_id),
            "name": name,
            "location": location,
            "version": version,
        }

        try:
            resp = self._client.post(
                "/api/agents/register",
                json=payload,
                headers=headers,
            )
            self._handle_response_error(resp)
            data = resp.json()
            if "api_key" in data:
                self.set_api_key(data["api_key"])
            return data
        except (httpx.ConnectError, httpx.TimeoutException, httpx.NetworkError) as e:
            raise NetworkError(f"Network error during device registration: {e}") from e

    def send_heartbeat(
        self,
        agent_id: str,
        status: str = "ONLINE",
        camera_count: int = 0,
        version: str = "1.0.0",
    ) -> Dict[str, Any]:
        """
        Send periodic heartbeat ping to backend.
        POST /api/agents/{agent_id}/heartbeat
        """
        payload = {
            "status": status,
            "camera_count": camera_count,
            "version": version,
        }
        try:
            resp = self._client.post(
                f"/api/agents/{agent_id}/heartbeat",
                json=payload,
            )
            self._handle_response_error(resp)
            return resp.json()
        except (httpx.ConnectError, httpx.TimeoutException, httpx.NetworkError) as e:
            raise NetworkError(f"Heartbeat failed due to network error: {e}") from e

    def sync_cameras(
        self,
        agent_id: str,
        cameras: List[Dict[str, Any]],
    ) -> Dict[str, str]:
        """
        Synchronize local camera configurations and receive backend UUID mappings.
        POST /api/agents/{agent_id}/cameras/sync
        Returns: {'mappings': {'CAM-01': 'backend-uuid', ...}}
        """
        payload = {
            "cameras": [
                {
                    "local_camera_id": str(cam["local_camera_id"]),
                    "name": str(cam.get("name", cam["local_camera_id"])),
                    "rtsp_url": str(cam["rtsp_url"]),
                    "location": cam.get("location"),
                    "latitude": cam.get("latitude"),
                    "longitude": cam.get("longitude"),
                    "resolution": cam.get("resolution"),
                }
                for cam in cameras
            ]
        }
        try:
            resp = self._client.post(
                f"/api/agents/{agent_id}/cameras/sync",
                json=payload,
            )
            self._handle_response_error(resp)
            data = resp.json()
            return data.get("mappings", {})
        except (httpx.ConnectError, httpx.TimeoutException, httpx.NetworkError) as e:
            raise NetworkError(f"Camera sync failed due to network error: {e}") from e

    def get_embeddings_sync(
        self,
        since: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Fetch incremental or full face embeddings sync package.
        GET /api/embeddings/sync?since={since}
        """
        params = {}
        if since:
            params["since"] = since

        try:
            resp = self._client.get(
                "/api/embeddings/sync",
                params=params,
            )
            self._handle_response_error(resp)
            return resp.json()
        except (httpx.ConnectError, httpx.TimeoutException, httpx.NetworkError) as e:
            raise NetworkError(f"Embedding sync failed due to network error: {e}") from e

    def upload_sighting(
        self,
        person_id: str,
        camera_id: str,
        similarity_score: float,
        detected_at: str,
        face_crop_bytes: bytes,
        full_frame_bytes: bytes,
        video_clip_bytes: Optional[bytes] = None,
        confidence_level: str = "POSSIBLE",
        num_frames_matched: Optional[int] = 1,
        camera_location: Optional[str] = None,
        latitude: Optional[float] = None,
        longitude: Optional[float] = None,
    ) -> Dict[str, Any]:
        """
        Upload a verified sighting match with evidence files.
        POST /api/sightings/ (multipart/form-data)
        """
        data = {
            "person_id": str(person_id),
            "camera_id": str(camera_id),
            "similarity_score": str(similarity_score),
            "detected_at": str(detected_at),
            "confidence_level": confidence_level,
        }
        if num_frames_matched is not None:
            data["num_frames_matched"] = str(num_frames_matched)
        if camera_location is not None:
            data["camera_location"] = camera_location
        if latitude is not None:
            data["latitude"] = str(latitude)
        if longitude is not None:
            data["longitude"] = str(longitude)

        files: Dict[str, Any] = {
            "face_crop": ("face_crop.jpg", face_crop_bytes, "image/jpeg"),
            "full_frame": ("full_frame.jpg", full_frame_bytes, "image/jpeg"),
        }

        if video_clip_bytes:
            files["video_clip"] = ("clip.mp4", video_clip_bytes, "video/mp4")

        try:
            resp = self._client.post(
                "/api/sightings/",
                data=data,
                files=files,
            )
            self._handle_response_error(resp)
            return resp.json()
        except (httpx.ConnectError, httpx.TimeoutException, httpx.NetworkError) as e:
            raise NetworkError(f"Sighting upload failed due to network error: {e}") from e

    def health_check(self) -> bool:
        """Check if the backend server is reachable."""
        try:
            resp = self._client.get("/health")
            return resp.status_code == 200
        except Exception:
            return False

    def close(self) -> None:
        """Close the underlying HTTP client session."""
        self._client.close()

    def __enter__(self) -> "ApiClient":
        return self

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        self.close()
