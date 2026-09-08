"""User schemas for request/response validation."""
from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, EmailStr


class UserCreate(BaseModel):
    """Fields needed when creating a new user from Firebase Auth."""
    firebase_uid: str
    name: str
    email: Optional[str] = None
    phone: Optional[str] = None
    avatar_url: Optional[str] = None
    language: str = "en"


class UserUpdate(BaseModel):
    """Fields that can be updated."""
    name: Optional[str] = None
    phone: Optional[str] = None
    avatar_url: Optional[str] = None
    language: Optional[str] = None
    fcm_token: Optional[str] = None


class UserResponse(BaseModel):
    """User data returned in API responses."""
    id: UUID
    firebase_uid: str
    name: str
    email: Optional[str] = None
    phone: Optional[str] = None
    avatar_url: Optional[str] = None
    language: str
    created_at: datetime

    model_config = {"from_attributes": True}
