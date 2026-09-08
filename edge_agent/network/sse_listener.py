"""Server-Sent Events (SSE) listener for receiving live real-time backend notifications."""
import json
import logging
import threading
import time
from typing import Any, Callable, Dict, List, Optional
import httpx

logger = logging.getLogger(__name__)

EventHandler = Callable[[str, Dict[str, Any]], None]


class SSEListener:
    """
    Subscribes to the backend SSE event stream (/api/events/stream) to receive
    real-time notifications (e.g., embedding update triggers, sighting alerts).
    Features auto-reconnection and cooperative background thread execution.
    """

    def __init__(
        self,
        base_url: str = "http://localhost:8000",
        endpoint: str = "/api/events/stream",
        auth_token: Optional[str] = None,
        api_key: Optional[str] = None,
        reconnect_delay_seconds: float = 3.0,
        max_reconnect_delay_seconds: float = 60.0,
        on_event: Optional[EventHandler] = None,
    ):
        self.base_url = base_url.rstrip("/")
        self.endpoint = endpoint
        self.auth_token = auth_token
        self.api_key = api_key
        self.reconnect_delay_seconds = reconnect_delay_seconds
        self.max_reconnect_delay_seconds = max_reconnect_delay_seconds
        self.on_event = on_event

        self._handlers: Dict[str, List[Callable[[Dict[str, Any]], None]]] = {}
        self._stop_event = threading.Event()
        self._thread: Optional[threading.Thread] = None

    def register_handler(self, event_name: str, handler: Callable[[Dict[str, Any]], None]) -> None:
        """Register a callback for a specific SSE event name."""
        if event_name not in self._handlers:
            self._handlers[event_name] = []
        self._handlers[event_name].append(handler)

    def _dispatch_event(self, event_name: str, data: Dict[str, Any]) -> None:
        """Route received event to general and specific handlers."""
        if self.on_event:
            try:
                self.on_event(event_name, data)
            except Exception as e:
                logger.warning(f"Error in general SSE event handler: {e}")

        for handler in self._handlers.get(event_name, []):
            try:
                handler(data)
            except Exception as e:
                logger.warning(f"Error in handler for '{event_name}': {e}")

    def _listen_loop(self) -> None:
        """Main listening loop with auto-reconnection."""
        url = f"{self.base_url}/{self.endpoint.lstrip('/')}"
        delay = self.reconnect_delay_seconds

        logger.info(f"Starting SSE listener on {url}")

        while not self._stop_event.is_set():
            headers = {"Accept": "text/event-stream"}
            if self.auth_token:
                headers["Authorization"] = f"Bearer {self.auth_token}"
            if self.api_key:
                headers["X-API-Key"] = self.api_key

            params = {}
            if self.auth_token:
                params["token"] = self.auth_token

            try:
                with httpx.Client(timeout=None) as client:
                    with client.stream("GET", url, headers=headers, params=params) as response:
                        if response.status_code != 200:
                            logger.warning(f"SSE connection returned HTTP {response.status_code}")
                            raise httpx.HTTPError(f"HTTP {response.status_code}")

                        logger.info("Connected to SSE stream")
                        delay = self.reconnect_delay_seconds  # Reset backoff on successful connect

                        current_event = "message"
                        current_data_lines = []

                        for line in response.iter_lines():
                            if self._stop_event.is_set():
                                break

                            line = line.strip()
                            if not line:
                                # Empty line signifies end of SSE message block
                                if current_data_lines:
                                    raw_data = "\n".join(current_data_lines)
                                    parsed_data = {}
                                    try:
                                        parsed_data = json.loads(raw_data)
                                    except Exception:
                                        parsed_data = {"raw": raw_data}

                                    self._dispatch_event(current_event, parsed_data)
                                    current_event = "message"
                                    current_data_lines = []
                                continue

                            if line.startswith(":"):
                                # SSE comment / heartbeat ping (:ping)
                                continue
                            elif line.startswith("event:"):
                                current_event = line[len("event:") :].strip()
                            elif line.startswith("data:"):
                                current_data_lines.append(line[len("data:") :].strip())

            except Exception as e:
                if not self._stop_event.is_set():
                    logger.warning(f"SSE stream disconnected: {e}. Reconnecting in {delay:.1f}s...")
                    time.sleep(delay)
                    delay = min(delay * 1.5, self.max_reconnect_delay_seconds)

        logger.info("SSE listener terminated")

    def start(self) -> None:
        """Start SSE listener in a background thread."""
        if self._thread is not None and self._thread.is_alive():
            logger.warning("SSE listener is already running")
            return
        self._stop_event.clear()
        self._thread = threading.Thread(target=self._listen_loop, daemon=True, name="SSEListenerThread")
        self._thread.start()

    def stop(self, timeout: float = 3.0) -> None:
        """Signal listener to stop and wait for thread termination."""
        self._stop_event.set()
        if self._thread is not None and self._thread.is_alive():
            self._thread.join(timeout=timeout)
            self._thread = None
