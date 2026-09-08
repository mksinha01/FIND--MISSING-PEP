"""Notification schemas."""
from datetime import datetime
from typing import Optional, List
from uuid import UUID

from pydantic import BaseModel


class NotificationResponse(BaseModel):
    """Notification data in responses."""
    id: UUID
    type: str
    title: str
    body: Optional[str] = None
    data: Optional[dict] = None
    sighting_id: Optional[UUID] = None
    is_read: bool
    created_at: datetime

    model_config = {"from_attributes": True}


class NotificationListResponse(BaseModel):
    """Paginated notification list."""
    items: List[NotificationResponse]
    total: int
    unread_count: int
