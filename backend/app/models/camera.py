"""Camera model — CCTV cameras registered by Edge Agents."""
import uuid
from datetime import datetime, timezone

from sqlalchemy import String, Float, DateTime, ForeignKey, Text, UniqueConstraint
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Camera(Base):
    __tablename__ = "cameras"
    __table_args__ = (
        UniqueConstraint('agent_id', 'local_camera_id', name='uq_agent_camera'),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    agent_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("edge_agents.id", ondelete="CASCADE"),
        nullable=False, index=True,
    )
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    local_camera_id: Mapped[str] = mapped_column(String(50), nullable=False)
    encrypted_rtsp_url: Mapped[str] = mapped_column(Text, nullable=False)
    location: Mapped[str | None] = mapped_column(String(255), nullable=True)
    latitude: Mapped[float | None] = mapped_column(Float, nullable=True)
    longitude: Mapped[float | None] = mapped_column(Float, nullable=True)
    # ACTIVE | INACTIVE | ERROR
    status: Mapped[str] = mapped_column(String(20), default="INACTIVE")
    resolution: Mapped[str | None] = mapped_column(String(20), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )

    # Relationships
    agent = relationship("EdgeAgent", back_populates="cameras")
    sightings = relationship("Sighting", back_populates="camera")

    def __repr__(self) -> str:
        return f"<Camera {self.name} ({self.status})>"
