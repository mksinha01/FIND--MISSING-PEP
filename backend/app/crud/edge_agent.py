"""CRUD operations for edge_agents table."""
import hashlib
import secrets
from typing import List, Optional
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.edge_agent import EdgeAgent
from app.schemas.edge_agent import AgentRegister


# ── Edge Agents ──

def _hash_api_key(api_key: str) -> str:
    """SHA-256 hash of the API key for storage."""
    return hashlib.sha256(api_key.encode()).hexdigest()


async def register_agent(
    db: AsyncSession, data: AgentRegister
) -> tuple[EdgeAgent, str]:
    """
    Register a new Edge Agent. Generates and returns a plain-text API key.
    The key is hashed before storage — it cannot be retrieved again.
    Returns: (agent, plain_text_api_key)
    """
    api_key = secrets.token_urlsafe(48)
    agent = EdgeAgent(
        device_id=data.device_id,
        name=data.name,
        location=data.location,
        api_key_hash=_hash_api_key(api_key),
        os_info=data.os_info,
        version=data.version,
    )
    db.add(agent)
    await db.flush()
    await db.refresh(agent)
    return agent, api_key


async def verify_api_key(db: AsyncSession, api_key: str) -> Optional[EdgeAgent]:
    """Verify an API key and return the associated agent."""
    key_hash = _hash_api_key(api_key)
    result = await db.execute(
        select(EdgeAgent).where(EdgeAgent.api_key_hash == key_hash)
    )
    return result.scalar_one_or_none()


async def get_agent_by_id(db: AsyncSession, agent_id: UUID) -> Optional[EdgeAgent]:
    """Get agent by UUID."""
    result = await db.execute(
        select(EdgeAgent).where(EdgeAgent.id == agent_id)
    )
    return result.scalar_one_or_none()


async def update_heartbeat(
    db: AsyncSession, agent_id: UUID, status: str = "ONLINE",
    camera_count: int = 0, version: Optional[str] = None
) -> Optional[EdgeAgent]:
    """Update agent heartbeat timestamp and status."""
    from datetime import datetime, timezone
    agent = await get_agent_by_id(db, agent_id)
    if not agent:
        return None
    agent.status = status
    agent.last_heartbeat = datetime.now(timezone.utc)
    agent.camera_count = camera_count
    if version:
        agent.version = version
    await db.flush()
    return agent


async def list_agents(db: AsyncSession) -> List[EdgeAgent]:
    """List all registered agents."""
    result = await db.execute(
        select(EdgeAgent).order_by(EdgeAgent.created_at.desc())
    )
    return list(result.scalars().all())
