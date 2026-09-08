"""Server-Sent Events (SSE) manager using Redis Pub/Sub with 30-second keepalive heartbeats."""
import asyncio
import json
import logging
import time
from typing import AsyncGenerator, Optional
import redis.asyncio as aioredis
from fastapi import Request

from app.config import settings

logger = logging.getLogger(__name__)


class SSEManager:
    """Manages Server-Sent Events broadcasting using Redis asynchronous Pub/Sub."""

    def __init__(self, redis_url: Optional[str] = None):
        self.redis_url = redis_url or settings.REDIS_URL
        self._redis = aioredis.from_url(self.redis_url, decode_responses=True)

    @property
    def redis(self):
        return self._redis

    async def publish(self, channel: str, message: dict) -> None:
        """
        Publish a JSON-serializable message dictionary to a Redis channel.
        """
        try:
            payload = json.dumps(message)
            await self._redis.publish(channel, payload)
        except Exception as e:
            logger.error(f"Failed to publish SSE event to channel {channel}: {e}")

    async def event_generator(
        self, channel: str, request: Request
    ) -> AsyncGenerator[str, None]:
        """
        Subscribes to a Redis channel and yields formatted SSE messages.
        Includes a 30-second ':ping\n\n' keepalive heartbeat to prevent reverse proxies
        and load balancers (e.g. Nginx, Cloudflare) from closing idle connections.
        """
        pubsub = self._redis.pubsub()
        await pubsub.subscribe(channel)
        last_ping_time = time.monotonic()

        try:
            while True:
                # Check for client disconnection
                if await request.is_disconnected():
                    logger.debug(f"SSE client disconnected from channel {channel}")
                    break

                # Poll Redis pubsub with a 1.0 second timeout to permit heartbeat checking
                try:
                    message = await pubsub.get_message(
                        ignore_subscribe_messages=True, timeout=1.0
                    )
                except Exception as e:
                    logger.warning(f"Error reading message from pubsub on {channel}: {e}")
                    message = None

                now = time.monotonic()

                if message is not None:
                    event_data = message.get("data")
                    if isinstance(event_data, str):
                        try:
                            parsed = json.loads(event_data)
                            event_name = parsed.pop("event", "sighting")
                            yield f"event: {event_name}\ndata: {json.dumps(parsed)}\n\n"
                        except json.JSONDecodeError:
                            yield f"data: {event_data}\n\n"
                    last_ping_time = now

                # 30-second keepalive heartbeat
                if now - last_ping_time >= 30.0:
                    yield ":ping\n\n"
                    last_ping_time = now

        except asyncio.CancelledError:
            logger.debug(f"SSE stream cancelled for channel {channel}")
        finally:
            try:
                await pubsub.unsubscribe(channel)
                await pubsub.close()
            except Exception as e:
                logger.debug(f"Error closing pubsub on channel {channel}: {e}")


sse_manager = SSEManager()
