"""Embedding synchronization service for edge agents."""
from datetime import datetime, timezone
from typing import Dict, Any, Optional
from sqlalchemy import select, or_
from sqlalchemy.ext.asyncio import AsyncSession

from app.crud.face_embedding import (
    get_active_embeddings,
    get_embeddings_since,
    get_deactivated_person_ids_since,
)
from app.models.face_embedding import FaceEmbedding
from app.models.missing_person import MissingPerson


async def get_sync_package(
    db: AsyncSession, since: Optional[datetime] = None
) -> Dict[str, Any]:
    """
    Computes the sync payload for Edge Agents.
    If 'since' is None, returns a full sync package containing all active embeddings.
    If 'since' is provided, returns an incremental package containing updated embeddings
    and 'removed_ids' (tombstones) for reports whose status transitioned to FOUND/CLOSED
    or whose embeddings were deactivated.
    """
    now_ts = datetime.now(timezone.utc).isoformat()

    if since is None:
        # Full synchronization
        active_embeddings = await get_active_embeddings(db)
        return {
            "full_sync": True,
            "persons": active_embeddings,
            "removed_ids": [],
            "sync_timestamp": now_ts,
        }

    # Incremental synchronization
    active_since = await get_embeddings_since(db, since)
    active_person_ids = {item["person_id"] for item in active_since}

    # Query tombstones:
    # 1. MissingPerson records updated since timestamp with status in ('FOUND', 'CLOSED')
    # 2. FaceEmbedding records updated since timestamp with is_active=False
    # 3. Direct joined records with FaceEmbedding.updated_at >= since and (is_active=False OR status != 'ACTIVE')
    tombstone_ids = set(await get_deactivated_person_ids_since(db, since))

    # Also check joined table in case embedding was updated after person status changed
    joined_result = await db.execute(
        select(FaceEmbedding.person_id)
        .join(MissingPerson, FaceEmbedding.person_id == MissingPerson.id)
        .where(
            or_(
                FaceEmbedding.updated_at >= since,
                MissingPerson.updated_at >= since,
            )
        )
        .where(
            or_(
                FaceEmbedding.is_active == False,  # noqa: E712
                MissingPerson.status != "ACTIVE",
            )
        )
    )
    for pid in joined_result.scalars().all():
        tombstone_ids.add(str(pid))

    # Filter out persons who currently have active embeddings being transmitted in this sync
    final_removed_ids = [pid for pid in tombstone_ids if pid not in active_person_ids]

    return {
        "full_sync": False,
        "persons": active_since,
        "removed_ids": sorted(final_removed_ids),
        "sync_timestamp": now_ts,
    }
