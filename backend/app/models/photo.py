"""Photo model — uploaded photos of missing persons."""
import uuid
from datetime import datetime, timezone

from sqlalchemy import String, Boolean, DateTime, ForeignKey
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Photo(Base):
    __tablename__ = "photos"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    person_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("missing_persons.id", ondelete="CASCADE"),
        nullable=False, index=True,
    )
    original_path: Mapped[str] = mapped_column(String(512), nullable=False)
    face_crop_path: Mapped[str | None] = mapped_column(String(512), nullable=True)
    is_primary: Mapped[bool] = mapped_column(Boolean, default=False)
    # PENDING | SUCCESS | FAILED | NO_FACE
    processing_status: Mapped[str] = mapped_column(String(20), default="PENDING")
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )

    # Relationships
    person = relationship("MissingPerson", back_populates="photos")

    def __repr__(self) -> str:
        return f"<Photo {self.id} ({self.processing_status})>"
