"""CRUD operations for users table."""
from typing import Optional
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.user import User
from app.schemas.user import UserCreate, UserUpdate


# ── Users ──

async def create_user(db: AsyncSession, data: UserCreate) -> User:
    """Create a new user. Called after Firebase Auth registration."""
    user = User(**data.model_dump())
    db.add(user)
    await db.flush()
    await db.refresh(user)
    return user


async def get_user_by_firebase_uid(db: AsyncSession, firebase_uid: str) -> Optional[User]:
    """Look up user by Firebase UID. Used by auth middleware."""
    result = await db.execute(
        select(User).where(User.firebase_uid == firebase_uid)
    )
    return result.scalar_one_or_none()


async def get_user_by_id(db: AsyncSession, user_id: UUID) -> Optional[User]:
    """Get user by internal UUID."""
    result = await db.execute(
        select(User).where(User.id == user_id)
    )
    return result.scalar_one_or_none()


async def update_user(db: AsyncSession, user_id: UUID, data: UserUpdate) -> Optional[User]:
    """Update user fields. Only updates non-None fields."""
    user = await get_user_by_id(db, user_id)
    if not user:
        return None
    update_data = data.model_dump(exclude_unset=True)
    for field, value in update_data.items():
        setattr(user, field, value)
    await db.flush()
    await db.refresh(user)
    return user


async def update_fcm_token(db: AsyncSession, user_id: UUID, fcm_token: str) -> None:
    """Update user's FCM token for push notifications."""
    user = await get_user_by_id(db, user_id)
    if user:
        user.fcm_token = fcm_token
        await db.flush()
