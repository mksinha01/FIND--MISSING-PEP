"""User notifications API endpoints."""
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user, get_db
from app.crud.notification import (
    get_unread_count,
    list_notifications_for_user,
    mark_all_read,
    mark_notification_read,
)
from app.models.user import User
from app.schemas.notification import NotificationListResponse, NotificationResponse

router = APIRouter(prefix="/notifications", tags=["notifications"])


@router.get("/", response_model=NotificationListResponse)
async def list_user_notifications(
    unread_only: bool = Query(False, description="Filter only unread notifications"),
    limit: int = Query(50, ge=1, le=100),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """List recent notifications for the authenticated user."""
    items = await list_notifications_for_user(db=db, user_id=current_user.id, limit=limit)
    unread = await get_unread_count(db=db, user_id=current_user.id)

    if unread_only:
        items = [n for n in items if not n.is_read]

    return NotificationListResponse(
        items=items,
        total=len(items),
        unread_count=unread,
    )


@router.get("/unread-count")
async def get_user_unread_count(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Get the badge count of unread notifications."""
    count = await get_unread_count(db=db, user_id=current_user.id)
    return {"unread_count": count}


@router.put("/{notification_id}/read", response_model=NotificationResponse)
async def mark_single_notification_read(
    notification_id: UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Mark a notification as read."""
    notif = await mark_notification_read(
        db=db,
        notification_id=notification_id,
        user_id=current_user.id,
    )
    if not notif:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Notification not found",
        )
    return notif


@router.put("/read-all")
async def mark_all_notifications_read(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Mark all unread notifications as read for current user."""
    count = await mark_all_read(db=db, user_id=current_user.id)
    return {"status": "success", "updated_count": count}
