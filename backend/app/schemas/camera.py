"""Camera schemas."""
from datetime import datetime
from typing import Optional, List, Dict
from uuid import UUID

from pydantic import BaseModel, Field


class CameraBase(BaseModel):
    name: str
    local_camera_id: str = "CAM-01"
    location: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    resolution: Optional[str] = None


class CameraCreate(CameraBase):
    """Register a camera for an Edge Agent."""
    rtsp_url: str


class CameraSyncItem(BaseModel):
    """Single camera channel reported by Edge Agent."""
    local_camera_id: str
    name: str
    rtsp_url: str
    location: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    resolution: Optional[str] = None


class CameraSyncRequest(BaseModel):
    """Payload for syncing multiple cameras from Edge Agent."""
    cameras: List[CameraSyncItem]


class CameraSyncResponse(BaseModel):
    """Response mapping local camera IDs (e.g. CAM-01) to backend UUIDs."""
    mappings: Dict[str, str] = Field(
        default_factory=dict,
        description="Map from local_camera_id to backend camera UUID string"
    )


class CameraResponse(BaseModel):
    """Camera data in responses."""
    id: UUID
    agent_id: UUID
    name: str
    local_camera_id: str
    location: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    status: str
    resolution: Optional[str] = None
    created_at: datetime

    model_config = {"from_attributes": True}
