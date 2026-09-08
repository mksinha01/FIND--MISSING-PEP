"""CLI tool to enroll a real missing person with photo from file or webcam."""
import argparse
import asyncio
import os
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path

import cv2
import numpy as np
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

# Ensure utf-8 output on Windows
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# Ensure paths
repo_root = Path(__file__).resolve().parent.parent
backend_dir = repo_root / "backend"
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))
if str(repo_root) not in sys.path:
    sys.path.insert(0, str(repo_root))

from app.database import Base
from app.models.face_embedding import FaceEmbedding
from app.models.missing_person import MissingPerson
from app.models.photo import Photo
from app.models.user import User
from app.services.face_processing import process_person_photo
from edge_agent.storage.local_db import SQLiteStore


def capture_from_webcam(device_idx: int = 0) -> np.ndarray:
    """Captures a single frame from the local webcam with live preview window."""
    cap = None
    if hasattr(cv2, "CAP_DSHOW"):
        cap = cv2.VideoCapture(device_idx, cv2.CAP_DSHOW)
    if cap is None or not cap.isOpened():
        cap = cv2.VideoCapture(device_idx)

    if not cap or not cap.isOpened():
        raise RuntimeError(f"Could not open webcam device {device_idx}")

    print("══════════════════════════════════════════════════════════════════")
    print("LIVE WEBCAM ENROLLMENT")
    print("Position face clearly in front of camera and press SPACEBAR to capture, or ESC to cancel.")
    print("══════════════════════════════════════════════════════════════════")

    captured_frame = None
    while True:
        ret, frame = cap.read()
        if not ret or frame is None:
            continue

        preview = frame.copy()
        cv2.putText(
            preview,
            "Press [SPACE] to capture photo | [ESC] to exit",
            (20, 40),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.7,
            (0, 255, 0),
            2,
        )
        cv2.imshow("FIND-MISSING-PEP — Webcam Enrollment", preview)
        key = cv2.waitKey(1) & 0xFF
        if key == 32:  # Spacebar
            captured_frame = frame
            break
        elif key == 27:  # ESC
            break

    cap.release()
    cv2.destroyAllWindows()

    if captured_frame is None:
        raise RuntimeError("Webcam capture was cancelled by user.")
    return captured_frame


async def register_missing_person(
    name: str,
    image_bytes: bytes,
    age: int = 25,
    gender: str = "male",
    height_cm: int = 170,
    description: str = "",
    last_seen_location: str = "",
    contact_info: str = "",
) -> None:
    # 1. Run AI face processing
    crop_bytes, emb_vec, det_score = process_person_photo(image_bytes)
    print(f"✓ Face detected with confidence: {det_score:.2f} (Quality: PASSED)")
    print(f"✓ 512-D ArcFace embedding extracted: L2 Norm = {np.linalg.norm(emb_vec):.4f}")

    # 2. Setup upload directories
    uploads_dir = repo_root / "uploads"
    photos_dir = uploads_dir / "photos"
    faces_dir = uploads_dir / "faces"
    photos_dir.mkdir(parents=True, exist_ok=True)
    faces_dir.mkdir(parents=True, exist_ok=True)

    photo_id = uuid.uuid4().hex
    orig_path = photos_dir / f"{photo_id}.jpg"
    crop_path = faces_dir / f"crop_{photo_id}.jpg"
    orig_path.write_bytes(image_bytes)
    crop_path.write_bytes(crop_bytes)

    # 3. Save to Backend DB
    backend_db_url = "sqlite+aiosqlite:///backend_local.db"
    engine = create_async_engine(backend_db_url, echo=False)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    session_factory = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)
    async with session_factory() as session:
        # Get or create admin user
        res = await session.execute(select(User).where(User.firebase_uid == "uid_admin_001"))
        user = res.scalar_one_or_none()
        if not user:
            user = User(
                id=uuid.uuid4(),
                firebase_uid="uid_admin_001",
                name="System Admin",
                email="admin@fmp.local",
                role="ADMIN",
            )
            session.add(user)
            await session.flush()
            await session.refresh(user)

        person = MissingPerson(
            id=uuid.uuid4(),
            user_id=user.id,
            full_name=name,
            age=age,
            gender=gender,
            height_cm=height_cm,
            description=description or "Enrolled via register_person CLI",
            last_seen_location=last_seen_location or "Campus / City Area",
            last_seen_time=datetime.now(timezone.utc),
            status="ACTIVE",
            contact_info=contact_info or "+91-9876543210",
        )
        session.add(person)
        await session.flush()
        await session.refresh(person)

        photo = Photo(
            id=uuid.uuid4(),
            person_id=person.id,
            original_path=f"/uploads/photos/{photo_id}.jpg",
            face_crop_path=f"/uploads/faces/crop_{photo_id}.jpg",
            is_primary=True,
            processing_status="SUCCESS",
        )
        session.add(photo)
        await session.flush()

        emb_bytes = emb_vec.astype(np.float32).tobytes()
        embedding = FaceEmbedding(
            id=uuid.uuid4(),
            person_id=person.id,
            photo_id=photo.id,
            embedding=emb_bytes,
            model_version="arcface_r50",
            quality_score=float(det_score),
            is_active=True,
        )
        session.add(embedding)
        await session.commit()

    # 4. Save directly into Edge Agent SQLite Store
    edge_store = SQLiteStore(db_path=os.path.join(repo_root, "local_data.db"))
    edge_store.save_embedding(
        id=str(embedding.id),
        person_id=str(person.id),
        person_name=person.full_name,
        embedding_data=emb_bytes,
        photo_url=f"/uploads/photos/{photo_id}.jpg",
    )

    print("══════════════════════════════════════════════════════════════════")
    print(f"SUCCESS: Enrolled '{name}' into active missing person database!")
    print(f"  • Person ID: {person.id}")
    print(f"  • Photo Saved: {orig_path}")
    print(f"  • Aligned 112x112 Crop: {crop_path}")
    print(f"  • Edge FAISS Index: Synced & Ready for Real-Time CCTV Alerting")
    print("══════════════════════════════════════════════════════════════════")


def main():
    parser = argparse.ArgumentParser(description="Register a real missing person with photo or webcam")
    parser.add_argument("--name", type=str, required=True, help="Full name of missing person")
    parser.add_argument("--photo", type=str, help="Path to portrait image file (.jpg, .png)")
    parser.add_argument("--webcam", action="store_true", help="Capture photo from local webcam")
    parser.add_argument("--device", type=int, default=0, help="Webcam device index (default: 0)")
    parser.add_argument("--age", type=int, default=25, help="Age in years")
    parser.add_argument("--gender", type=str, default="male", choices=["male", "female", "other"])
    parser.add_argument("--description", type=str, default="Enrolled subject")
    parser.add_argument("--last-seen", type=str, default="Main Street")
    parser.add_argument("--contact", type=str, default="+91-9876543210")

    args = parser.parse_args()

    if args.webcam:
        frame = capture_from_webcam(args.device)
        success, buf = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), 95])
        if not success:
            raise RuntimeError("Failed to encode webcam frame to JPEG")
        image_bytes = buf.tobytes()
    elif args.photo:
        if not os.path.isfile(args.photo):
            raise FileNotFoundError(f"Photo file not found: {args.photo}")
        with open(args.photo, "rb") as f:
            image_bytes = f.read()
    else:
        print("Error: Must provide either --photo <path> or --webcam")
        sys.exit(1)

    asyncio.run(
        register_missing_person(
            name=args.name,
            image_bytes=image_bytes,
            age=args.age,
            gender=args.gender,
            description=args.description,
            last_seen_location=args.last_seen,
            contact_info=args.contact,
        )
    )


if __name__ == "__main__":
    main()
