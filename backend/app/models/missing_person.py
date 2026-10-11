"""Missing Person report model with FIR verification support."""
import uuid
from datetime import datetime, timezone

from sqlalchemy import String, Integer, Text, DateTime, ForeignKey, Float
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class MissingPerson(Base):
    __tablename__ = "missing_persons"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )

    # Person details
    full_name: Mapped[str] = mapped_column(String(255), nullable=False)
    age: Mapped[int | None] = mapped_column(Integer, nullable=True)
    gender: Mapped[str | None] = mapped_column(String(20), nullable=True)
    height_cm: Mapped[int | None] = mapped_column(Integer, nullable=True)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    last_seen_location: Mapped[str | None] = mapped_column(Text, nullable=True)
    last_seen_time: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )

    # Report status: PROCESSING | PENDING_FIR_REVIEW | ACTIVE | FOUND | CLOSED | REJECTED_NO_FACE | FIR_REJECTED
    status: Mapped[str] = mapped_column(String(30), default="PROCESSING", index=True)
    contact_info: Mapped[str | None] = mapped_column(Text, nullable=True)

    # ── FIR (First Information Report) Verification ──
    fir_number: Mapped[str | None] = mapped_column(
        String(100), nullable=True, index=True,
        comment="FIR number e.g. FIR/2026/1234 or 1234/2026"
    )
    fir_police_station: Mapped[str | None] = mapped_column(
        String(255), nullable=True,
        comment="Police station name where FIR was filed"
    )
    fir_date: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True,
        comment="Date when FIR was filed at the police station"
    )
    fir_document_path: Mapped[str | None] = mapped_column(
        String(512), nullable=True,
        comment="Upload path for scanned FIR document (PDF/image)"
    )
    fir_status: Mapped[str] = mapped_column(
        String(20), default="PENDING", nullable=False,
        comment="FIR verification status: PENDING | VERIFIED | REJECTED | WAIVED"
    )
    fir_verified_by: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="SET NULL"), nullable=True,
        comment="Admin user who verified/rejected the FIR"
    )
    fir_verified_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True,
        comment="Timestamp when FIR was verified or rejected"
    )
    fir_rejection_reason: Mapped[str | None] = mapped_column(
        Text, nullable=True,
        comment="Reason for FIR rejection, shown to user for resubmission"
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
    )

    # Relationships
    user = relationship("User", back_populates="reports", foreign_keys=[user_id])
    photos = relationship("Photo", back_populates="person", cascade="all, delete-orphan")
    embeddings = relationship("FaceEmbedding", back_populates="person", cascade="all, delete-orphan")
    sightings = relationship("Sighting", back_populates="person")

    def __repr__(self) -> str:
        return f"<MissingPerson {self.full_name} ({self.status}) FIR:{self.fir_status}>"
