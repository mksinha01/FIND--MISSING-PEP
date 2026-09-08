"""CRUD operations for photos table."""
from typing import List, Optional
from uuid import UUID
from sqlalchemy import select, delete
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.photo import Photo


async def save_photo_record(
    db: AsyncSession,
    person_id: UUID,
    original_path: str,
    is_primary: bool = False,
) -> Photo:
    """Save a photo record after file upload."""
    photo = Photo(
        person_id=person_id,
        original_path=original_path,
        is_primary=is_primary,
        processing_status="PENDING",
    )
    db.add(photo)
    await db.flush()
    await db.refresh(photo)
    return photo


async def get_photo_by_id(db: AsyncSession, photo_id: UUID) -> Optional[Photo]:
    """Get a single photo record by ID."""
    result = await db.execute(select(Photo).where(Photo.id == photo_id))
    return result.scalar_one_or_none()


async def get_photos_for_person(db: AsyncSession, person_id: UUID) -> List[Photo]:
    """Get all photos for a missing person, ordered chronologically."""
    result = await db.execute(
        select(Photo)
        .where(Photo.person_id == person_id)
        .order_by(Photo.created_at)
    )
    return list(result.scalars().all())


async def update_photo_status(
    db: AsyncSession,
    photo_id: UUID,
    status: str,
    face_crop_path: Optional[str] = None,
) -> Optional[Photo]:
    """Update photo processing status (e.g. SUCCESS, FAILED, NO_FACE) and face crop path."""
    photo = await get_photo_by_id(db, photo_id)
    if not photo:
        return None
    photo.processing_status = status
    if face_crop_path:
        photo.face_crop_path = face_crop_path
    await db.flush()
    return photo


async def delete_photo(db: AsyncSession, photo_id: UUID) -> bool:
    """Delete a photo record."""
    result = await db.execute(delete(Photo).where(Photo.id == photo_id))
    await db.flush()
    return result.rowcount > 0
