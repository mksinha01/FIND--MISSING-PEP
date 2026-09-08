"""CRUD operations for cameras table."""
from typing import List, Optional
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.camera import Camera
from app.schemas.camera import CameraCreate


# ── Cameras ──

async def register_camera(
    db: AsyncSession, agent_id: UUID, data: CameraCreate
) -> Camera:
    """Register a camera for an Edge Agent."""
    camera = Camera(agent_id=agent_id, **data.model_dump())
    db.add(camera)
    await db.flush()
    await db.refresh(camera)
    return camera


async def get_camera_by_id(db: AsyncSession, camera_id: UUID) -> Optional[Camera]:
    """Get camera by UUID."""
    result = await db.execute(select(Camera).where(Camera.id == camera_id))
    return result.scalar_one_or_none()


async def list_cameras_for_agent(db: AsyncSession, agent_id: UUID) -> List[Camera]:
    """List all cameras for an Edge Agent."""
    result = await db.execute(
        select(Camera)
        .where(Camera.agent_id == agent_id)
        .order_by(Camera.name)
    )
    return list(result.scalars().all())


async def update_camera_status(
    db: AsyncSession, camera_id: UUID, status: str
) -> Optional[Camera]:
    """Update camera status (ACTIVE/INACTIVE/ERROR)."""
    camera = await get_camera_by_id(db, camera_id)
    if not camera:
        return None
    camera.status = status
    await db.flush()
    return camera
