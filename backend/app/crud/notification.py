"""CRUD operations for notifications table."""
from typing import List, Optional, Dict, Any
from uuid import UUID
from sqlalchemy import select, func, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.notification import Notification


async def create_notification(
    db: AsyncSession,
    user_id: UUID,
    type: str,
    title: str,
    body: Optional[str] = None,
    data: Optional[Dict[str, Any]] = None,
    sighting_id: Optional[UUID] = None,
) -> Notification:
    """Create and persist a notification record."""
    notif = Notification(
        user_id=user_id,
        type=type,
        title=title,
        body=body,
        data=data or {},
        sighting_id=sighting_id,
        is_read=False,
    )
    db.add(notif)
    await db.flush()
    await db.refresh(notif)
    return notif


async def list_notifications_for_user(
    db: AsyncSession,
    user_id: UUID,
    unread_only: bool = False,
    limit: int = 50,
) -> List[Notification]:
    """List notifications for a user, newest first."""
    query = select(Notification).where(Notification.user_id == user_id)
    if unread_only:
        query = query.where(Notification.is_read == False)  # noqa: E712
    query = query.order_by(Notification.created_at.desc()).limit(limit)

    result = await db.execute(query)
    return list(result.scalars().all())


async def get_unread_count(db: AsyncSession, user_id: UUID) -> int:
    """Count unread notifications for a user."""
    result = await db.execute(
        select(func.count(Notification.id))
        .where(Notification.user_id == user_id)
        .where(Notification.is_read == False)  # noqa: E712
    )
    return result.scalar_one() or 0


async def mark_notification_read(
    db: AsyncSession, notification_id: UUID, user_id: UUID
) -> Optional[Notification]:
    """Mark a single notification as read."""
    result = await db.execute(
        select(Notification)
        .where(Notification.id == notification_id)
        .where(Notification.user_id == user_id)
    )
    notif = result.scalar_one_or_none()
    if notif:
        notif.is_read = True
        await db.flush()
    return notif


async def mark_all_read(db: AsyncSession, user_id: UUID) -> int:
    """Mark all unread notifications as read for a user. Returns the count updated."""
    result = await db.execute(
        update(Notification)
        .where(Notification.user_id == user_id)
        .where(Notification.is_read == False)  # noqa: E712
        .values(is_read=True)
    )
    await db.flush()
    return result.rowcount
