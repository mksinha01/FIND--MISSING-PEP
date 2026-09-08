"""Seed test users, missing person reports with photos & embeddings, and edge agent devices."""
import argparse
import asyncio
import hashlib
import io
import logging
import os
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import cv2
import httpx
import numpy as np
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

# Ensure backend package is in python path
backend_dir = Path(__file__).resolve().parent.parent / "backend"
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))
parent_dir = Path(__file__).resolve().parent.parent
if str(parent_dir) not in sys.path:
    sys.path.insert(0, str(parent_dir))

from app.config import settings
from app.database import Base
from app.models.camera import Camera
from app.models.edge_agent import EdgeAgent
from app.models.face_embedding import FaceEmbedding
from app.models.missing_person import MissingPerson
from app.models.photo import Photo
from app.models.user import User
from app.utils.crypto import AESGCMCrypto

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)


def generate_synthetic_portrait(name: str = "Subject", width: int = 300, height: int = 300) -> bytes:
    """Generates synthetic JPEG portrait image with realistic features."""
    canvas = np.full((height, width, 3), fill_value=(235, 235, 235), dtype=np.uint8)
    cx, cy = width // 2, int(height * 0.45)
    r = int(min(width, height) * 0.28)

    # Torso / clothes
    cv2.ellipse(canvas, (cx, height + 30), (int(r * 2.2), int(r * 2.0)), 0, 0, 360, (140, 90, 60), -1)

    # Face contour
    skin_tone = (190, 210, 230)
    cv2.ellipse(canvas, (cx, cy), (r, int(r * 1.25)), 0, 0, 360, skin_tone, -1)

    # Hair
    hair_color = (35, 30, 25)
    cv2.ellipse(canvas, (cx, cy - int(r * 0.45)), (int(r * 1.05), int(r * 0.7)), 0, 180, 360, hair_color, -1)

    # Eyes
    eye_x = int(r * 0.38)
    eye_y = int(r * 0.18)
    cv2.circle(canvas, (cx - eye_x, cy - eye_y), int(r * 0.12), (255, 255, 255), -1)
    cv2.circle(canvas, (cx + eye_x, cy - eye_y), int(r * 0.12), (255, 255, 255), -1)
    cv2.circle(canvas, (cx - eye_x, cy - eye_y), int(r * 0.06), (40, 25, 20), -1)
    cv2.circle(canvas, (cx + eye_x, cy - eye_y), int(r * 0.06), (40, 25, 20), -1)

    # Nose
    cv2.circle(canvas, (cx, cy + int(r * 0.15)), int(r * 0.08), (160, 180, 200), -1)

    # Mouth
    cv2.line(canvas, (cx - int(r * 0.3), cy + int(r * 0.55)), (cx + int(r * 0.3), cy + int(r * 0.55)), (80, 80, 160), 2)

    # Subtitle
    cv2.putText(canvas, name, (15, height - 15), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (100, 100, 100), 1)

    success, buf = cv2.imencode(".jpg", canvas, [int(cv2.IMWRITE_JPEG_QUALITY), 95])
    if not success:
        raise RuntimeError("Failed to encode synthetic portrait to JPEG")
    return buf.tobytes()


def generate_deterministic_embedding(seed_str: str) -> np.ndarray:
    """Generates a reproducible, L2-normalized 512-D float32 ArcFace embedding vector."""
    seed = int(hashlib.sha256(seed_str.encode("utf-8")).hexdigest()[:8], 16)
    rng = np.random.RandomState(seed)
    vec = rng.randn(512).astype(np.float32)
    return vec / np.linalg.norm(vec)


async def seed_database(
    db_url: Optional[str] = None,
    session: Optional[AsyncSession] = None,
    upload_base_dir: Optional[str] = None,
    num_reports: int = 3,
) -> Dict[str, Any]:
    """
    Directly seeds PostgreSQL / SQLite database with test users, missing person reports,
    embeddings, edge agents, and cameras.
    """
    upload_dir = Path(upload_base_dir or settings.UPLOAD_DIR)
    photos_dir = upload_dir / "photos"
    faces_dir = upload_dir / "faces"
    photos_dir.mkdir(parents=True, exist_ok=True)
    faces_dir.mkdir(parents=True, exist_ok=True)

    owns_session = False
    if session is None:
        target_url = db_url or settings.DATABASE_URL_ASYNC
        try:
            engine = create_async_engine(target_url, echo=False)
            async with engine.begin() as conn:
                await conn.run_sync(Base.metadata.create_all)
        except Exception as conn_err:
            if not db_url and ("refused" in str(conn_err).lower() or "connect" in str(conn_err).lower() or "10061" in str(conn_err)):
                fallback_url = "sqlite+aiosqlite:///backend_local.db"
                logger.warning(f"Could not connect to {target_url}; falling back to local SQLite: {fallback_url}")
                engine = create_async_engine(fallback_url, echo=False)
                async with engine.begin() as conn:
                    await conn.run_sync(Base.metadata.create_all)
            else:
                raise

        session_factory = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)
        session = session_factory()
        owns_session = True

    summary: Dict[str, Any] = {
        "users": [],
        "reports": [],
        "agent": None,
        "cameras": [],
    }

    try:
        # 1. Seed Test Users
        test_users_data = [
            ("uid_admin_001", "System Admin", "admin@fmp.local", "ADMIN"),
            ("uid_operator_001", "CCTV Operator", "operator@fmp.local", "OPERATOR"),
            ("uid_citizen_raj", "Rajesh Sharma", "rajesh@fmp.local", "CITIZEN"),
        ]

        user_records: List[User] = []
        for uid, name, email, role in test_users_data:
            res = await session.execute(select(User).where(User.firebase_uid == uid))
            user = res.scalar_one_or_none()
            if not user:
                user = User(
                    id=uuid.uuid4(),
                    firebase_uid=uid,
                    name=name,
                    email=email,
                    language="en",
                )
                session.add(user)
                await session.flush()
                await session.refresh(user)
            user_records.append(user)
            summary["users"].append({"id": str(user.id), "firebase_uid": uid, "name": name})

        citizen_user = user_records[-1]

        # 2. Seed Missing Person Reports with Photos & Embeddings
        sample_profiles = [
            {
                "name": "Aarav Sharma",
                "age": 14,
                "gender": "male",
                "height": 155,
                "description": "Wearing blue school uniform and black shoes",
                "last_seen": "Metro Station Gate 2",
                "contact": "+91-9876543210",
            },
            {
                "name": "Priya Patel",
                "age": 22,
                "gender": "female",
                "height": 162,
                "description": "Wearing red kurti and carrying beige shoulder bag",
                "last_seen": "Central Bus Terminus Platform 4",
                "contact": "+91-9876543211",
            },
            {
                "name": "Rohan Gupta",
                "age": 8,
                "gender": "male",
                "height": 120,
                "description": "Wearing yellow striped t-shirt and blue shorts",
                "last_seen": "City Park Playground",
                "contact": "+91-9876543212",
            },
        ]

        for i in range(min(num_reports, len(sample_profiles))):
            prof = sample_profiles[i]
            # Check if report already exists
            res = await session.execute(
                select(MissingPerson).where(
                    MissingPerson.user_id == citizen_user.id,
                    MissingPerson.full_name == prof["name"],
                )
            )
            person = res.scalar_one_or_none()

            if not person:
                person = MissingPerson(
                    id=uuid.uuid4(),
                    user_id=citizen_user.id,
                    full_name=prof["name"],
                    age=prof["age"],
                    gender=prof["gender"],
                    height_cm=prof["height"],
                    description=prof["description"],
                    last_seen_location=prof["last_seen"],
                    last_seen_time=datetime.now(timezone.utc),
                    status="ACTIVE",
                    contact_info=prof["contact"],
                )
                session.add(person)
                await session.flush()
                await session.refresh(person)

                # Generate photo files
                photo_bytes = generate_synthetic_portrait(name=prof["name"])
                photo_filename = f"{uuid.uuid4().hex}.jpg"
                crop_filename = f"crop_{uuid.uuid4().hex}.jpg"

                orig_path = photos_dir / photo_filename
                crop_path = faces_dir / crop_filename
                orig_path.write_bytes(photo_bytes)
                crop_path.write_bytes(photo_bytes)

                photo = Photo(
                    id=uuid.uuid4(),
                    person_id=person.id,
                    original_path=f"/uploads/photos/{photo_filename}",
                    face_crop_path=f"/uploads/faces/{crop_filename}",
                    is_primary=True,
                    processing_status="SUCCESS",
                )
                session.add(photo)
                await session.flush()

                # Generate and save ArcFace 512-D embedding
                emb_vec = generate_deterministic_embedding(prof["name"])
                emb_bytes = emb_vec.tobytes()

                embedding = FaceEmbedding(
                    id=uuid.uuid4(),
                    person_id=person.id,
                    photo_id=photo.id,
                    embedding=emb_bytes,
                    model_version="arcface_r50",
                    quality_score=0.96,
                    is_active=True,
                )
                session.add(embedding)
                await session.flush()

            summary["reports"].append({"id": str(person.id), "full_name": person.full_name, "status": person.status})

        # 3. Seed Registered Edge Agent Device & Cameras
        dev_id = "EDGE-CAMPUS-01"
        res = await session.execute(select(EdgeAgent).where(EdgeAgent.device_id == dev_id))
        agent = res.scalar_one_or_none()

        plain_api_key = "fmp_agent_key_dev_seed_998877665544332211"
        api_key_hash = hashlib.sha256(plain_api_key.encode("utf-8")).hexdigest()

        if not agent:
            agent = EdgeAgent(
                id=uuid.uuid4(),
                device_id=dev_id,
                name="Main Campus Hub",
                location="Administration Building",
                api_key_hash=api_key_hash,
                status="ONLINE",
                camera_count=2,
                version="1.0.0",
                last_heartbeat=datetime.now(timezone.utc),
            )
            session.add(agent)
            await session.flush()
            await session.refresh(agent)

            crypto = AESGCMCrypto(settings.RTSP_SECRET_KEY)
            cam_data = [
                ("CAM-01", "Main Entrance Gate", "rtsp://admin:pass@192.168.1.10:554/ch0", "Gate 1", 28.6139, 77.2090),
                ("CAM-02", "South Parking Lot", "rtsp://admin:pass@192.168.1.11:554/ch0", "Lot B", 28.6145, 77.2095),
            ]

            for local_id, name, rtsp, loc, lat, lon in cam_data:
                camera = Camera(
                    id=uuid.uuid4(),
                    agent_id=agent.id,
                    local_camera_id=local_id,
                    name=name,
                    encrypted_rtsp_url=crypto.encrypt(rtsp),
                    location=loc,
                    latitude=lat,
                    longitude=lon,
                    resolution="1080p",
                    status="ACTIVE",
                )
                session.add(camera)
                summary["cameras"].append({"id": str(camera.id), "local_id": local_id, "name": name})

            await session.flush()

        summary["agent"] = {
            "id": str(agent.id),
            "device_id": agent.device_id,
            "name": agent.name,
            "api_key": plain_api_key,
        }

        await session.commit()
        logger.info(f"Database seeded successfully: {len(summary['users'])} users, {len(summary['reports'])} reports, 1 agent.")
        return summary

    finally:
        if owns_session:
            await session.close()


async def seed_via_api(
    base_url: str = "http://localhost:8000",
    enrollment_key: str = "dev-enroll-secret-change-me",
) -> Dict[str, Any]:
    """Seeds test data through running FastAPI HTTP server."""
    logger.info(f"Connecting to API at {base_url} to seed test data...")
    client = httpx.AsyncClient(base_url=base_url, timeout=30.0)
    try:
        # 1. Register Edge Agent
        agent_payload = {
            "device_id": f"EDGE-API-{uuid.uuid4().hex[:6]}",
            "name": "Synthetic Edge Agent",
            "location": "North Entrance",
            "version": "1.0.0",
        }
        reg_resp = await client.post(
            "/api/agents/register",
            json=agent_payload,
            headers={"X-Enrollment-Key": enrollment_key},
        )
        reg_resp.raise_for_status()
        agent_info = reg_resp.json()
        agent_id = agent_info["agent_id"]
        api_key = agent_info["api_key"]
        logger.info(f"Registered Edge Agent via API: {agent_id}")

        # 2. Sync Cameras
        cam_sync_resp = await client.post(
            f"/api/agents/{agent_id}/cameras/sync",
            json={
                "cameras": [
                    {
                        "local_camera_id": "CAM-01",
                        "name": "Entrance Cam 1",
                        "rtsp_url": "rtsp://admin:pass@192.168.1.50:554/live",
                        "location": "Lobby",
                        "latitude": 28.6139,
                        "longitude": 77.2090,
                    }
                ]
            },
            headers={"X-API-Key": api_key},
        )
        cam_sync_resp.raise_for_status()
        cam_mappings = cam_sync_resp.json()["mappings"]

        # 3. Create Missing Person Report
        portrait_bytes = generate_synthetic_portrait(name="Aarav Sharma")
        report_files = {
            "photos": ("aarav.jpg", io.BytesIO(portrait_bytes), "image/jpeg"),
        }
        report_data = {
            "full_name": "Aarav Sharma",
            "age": "14",
            "gender": "male",
            "description": "Test subject for automated integration tests",
            "last_seen_location": "Gate 2",
            "contact_info": "+91-9876543210",
        }
        report_resp = await client.post(
            "/api/reports/",
            data=report_data,
            files=report_files,
            headers={"Authorization": "Bearer test-user-api-seed"},
        )
        report_resp.raise_for_status()
        report_info = report_resp.json()
        logger.info(f"Created Missing Person Report via API: {report_info['id']} ({report_info['full_name']})")

        return {
            "agent_id": agent_id,
            "api_key": api_key,
            "camera_mappings": cam_mappings,
            "report_id": report_info["id"],
            "report_name": report_info["full_name"],
        }
    finally:
        await client.aclose()


def main() -> None:
    parser = argparse.ArgumentParser(description="FIND-MISSING-PEP Test Data Seeder")
    parser.add_argument("--db-url", type=str, default=None, help="Database URL (default: settings.DATABASE_URL with fallback)")
    parser.add_argument("--api-url", type=str, default=None, help="Backend API base URL (e.g. http://localhost:8000)")
    parser.add_argument("--enrollment-key", type=str, default="dev-enroll-secret-change-me")
    parser.add_argument("--count", type=int, default=3, help="Number of missing persons to seed")
    args = parser.parse_args()

    if args.api_url:
        result = asyncio.run(seed_via_api(base_url=args.api_url, enrollment_key=args.enrollment_key))
    else:
        result = asyncio.run(seed_database(db_url=args.db_url, num_reports=args.count))

    logger.info(f"Seeding completed successfully: {result}")


if __name__ == "__main__":
    main()
