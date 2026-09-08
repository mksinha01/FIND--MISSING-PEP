"""Edge Agent schemas."""
from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import BaseModel


class AgentRegister(BaseModel):
    """Register a new Edge Agent device."""
    device_id: str
    name: Optional[str] = None
    location: Optional[str] = None
    os_info: Optional[str] = None
    version: Optional[str] = None


class AgentRegisterResponse(BaseModel):
    """Response after registration — includes the API key (shown only once)."""
    agent_id: UUID
    api_key: str  # Plain-text API key, only returned on registration
    message: str = "Agent registered successfully. Save the API key — it cannot be retrieved again."


class AgentHeartbeat(BaseModel):
    """Heartbeat payload sent periodically by the Edge Agent."""
    status: str = "ONLINE"
    camera_count: int = 0
    version: Optional[str] = None
    stats: Optional[dict] = None  # CPU usage, faces detected, etc.


class AgentResponse(BaseModel):
    """Edge Agent data in responses."""
    id: UUID
    device_id: str
    name: Optional[str] = None
    location: Optional[str] = None
    status: str
    last_heartbeat: Optional[datetime] = None
    last_sync_at: Optional[datetime] = None
    camera_count: int
    version: Optional[str] = None
    created_at: datetime

    model_config = {"from_attributes": True}
