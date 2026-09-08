"""Photo schemas for request/response validation."""
from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import BaseModel


class PhotoBase(BaseModel):
    is_primary: bool = False


class PhotoCreate(PhotoBase):
    person_id: UUID
    original_path: str
    face_crop_path: Optional[str] = None
    processing_status: str = "PENDING"


class PhotoResponse(BaseModel):
    id: UUID
    person_id: UUID
    original_path: str
    face_crop_path: Optional[str] = None
    is_primary: bool
    processing_status: str  # PENDING | SUCCESS | FAILED | NO_FACE
    created_at: datetime

    model_config = {"from_attributes": True}


class PhotoUploadResponse(BaseModel):
    photo: PhotoResponse
    message: str = "Photo uploaded and processed successfully."
