"""Missing Person report schemas."""
from datetime import datetime
from typing import Optional, List
from uuid import UUID

from pydantic import BaseModel


class MissingPersonCreate(BaseModel):
    """Create a new missing person report."""
    full_name: str
    age: Optional[int] = None
    gender: Optional[str] = None
    height_cm: Optional[int] = None
    description: Optional[str] = None
    last_seen_location: Optional[str] = None
    last_seen_time: Optional[datetime] = None
    contact_info: Optional[str] = None


class MissingPersonUpdate(BaseModel):
    """Update a missing person report."""
    full_name: Optional[str] = None
    age: Optional[int] = None
    gender: Optional[str] = None
    height_cm: Optional[int] = None
    description: Optional[str] = None
    last_seen_location: Optional[str] = None
    last_seen_time: Optional[datetime] = None
    contact_info: Optional[str] = None
    status: Optional[str] = None


class PhotoResponse(BaseModel):
    """Photo data in responses."""
    id: UUID
    original_path: str
    face_crop_path: Optional[str] = None
    is_primary: bool
    processing_status: str
    created_at: datetime

    model_config = {"from_attributes": True}


class MissingPersonResponse(BaseModel):
    """Full missing person report response."""
    id: UUID
    user_id: UUID
    full_name: str
    age: Optional[int] = None
    gender: Optional[str] = None
    height_cm: Optional[int] = None
    description: Optional[str] = None
    last_seen_location: Optional[str] = None
    last_seen_time: Optional[datetime] = None
    status: str
    contact_info: Optional[str] = None
    created_at: datetime
    updated_at: datetime
    photos: List[PhotoResponse] = []

    model_config = {"from_attributes": True}


class MissingPersonListResponse(BaseModel):
    """Paginated list of reports."""
    items: List[MissingPersonResponse]
    total: int
    page: int
    per_page: int
