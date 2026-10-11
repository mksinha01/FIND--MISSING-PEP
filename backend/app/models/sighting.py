"""Sighting model — a possible match detected by an Edge Agent."""
import uuid
from datetime import datetime, timezone

from sqlalchemy import String, Float, Integer, Text, DateTime, ForeignKey
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Sighting(Base):
    __tablename__ = "sightings"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    person_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("missing_persons.id", ondelete="CASCADE"), nullable=False, index=True
    )
    agent_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("edge_agents.id"), nullable=False
    )
    camera_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("cameras.id"), nullable=False
    )

    # Match data
    similarity_score: Mapped[float] = mapped_column(Float, nullable=False)
    # POSSIBLE | PROBABLE | CONFIRMED | REJECTED
    confidence_level: Mapped[str] = mapped_column(String(20), default="POSSIBLE")
    num_frames_matched: Mapped[int | None] = mapped_column(Integer, nullable=True)

    # Evidence file paths
    face_crop_path: Mapped[str | None] = mapped_column(String(512), nullable=True)
    full_frame_path: Mapped[str | None] = mapped_column(String(512), nullable=True)
    video_clip_path: Mapped[str | None] = mapped_column(String(512), nullable=True)

    # Location & time
    camera_location: Mapped[str | None] = mapped_column(String(255), nullable=True)
    latitude: Mapped[float | None] = mapped_column(Float, nullable=True)
    longitude: Mapped[float | None] = mapped_column(Float, nullable=True)
    detected_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, index=True
    )

    # Operator review
    reviewed_by: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id"), nullable=True
    )
    reviewed_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    review_notes: Mapped[str | None] = mapped_column(Text, nullable=True)

    # PENDING | CONFIRMED | REJECTED
    status: Mapped[str] = mapped_column(String(20), default="PENDING", index=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )

    # Relationships
    person = relationship("MissingPerson", back_populates="sightings")
    agent = relationship("EdgeAgent", back_populates="sightings")
    camera = relationship("Camera", back_populates="sightings")

    def __repr__(self) -> str:
        return f"<Sighting person={self.person_id} sim={self.similarity_score:.2f} ({self.status})>"
