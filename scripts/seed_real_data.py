"""Seed real missing person reports, actual photos, and genuine ArcFace 512-D embeddings."""
import asyncio
import glob
import hashlib
import json
import logging
import os
import shutil
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List

import cv2
import numpy as np
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

# Ensure paths
repo_root = Path(__file__).resolve().parent.parent
backend_dir = repo_root / "backend"
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))
if str(repo_root) not in sys.path:
    sys.path.insert(0, str(repo_root))

from app.config import settings
from app.database import Base
from app.models.camera import Camera
from app.models.edge_agent import EdgeAgent
from app.models.face_embedding import FaceEmbedding
from app.models.missing_person import MissingPerson
from app.models.photo import Photo
from app.models.user import User
from app.services.face_processing import process_person_photo
from app.utils.crypto import AESGCMCrypto
from edge_agent.storage.local_db import SQLiteStore

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("seed_real_data")


def find_portrait_files() -> Dict[str, str]:
    """Finds the generated high-quality portrait files for each subject."""
    artifact_dir = r"C:\Users\mksin\.gemini\antigravity-ide\brain\c4598e5a-62cd-4fea-964a-5c319a94991a"
    results = {}
    
    aarav_matches = glob.glob(os.path.join(artifact_dir, "*aarav_sharma*.jpg"))
    if aarav_matches:
        results["Aarav Sharma"] = aarav_matches[0]
        
    priya_matches = glob.glob(os.path.join(artifact_dir, "*priya_patel*.jpg"))
    if priya_matches:
        results["Priya Patel"] = priya_matches[0]
        
    rohan_matches = glob.glob(os.path.join(artifact_dir, "*rohan_gupta*.jpg"))
    if rohan_matches:
        results["Rohan Gupta"] = rohan_matches[0]
        
    return results


async def seed_real_data() -> None:
    logger.info("══════════════════════════════════════════════════════════════════")
    logger.info("Starting Real Data Seeding (Genuine Photos & ArcFace Embeddings)")
    logger.info("══════════════════════════════════════════════════════════════════")

    portraits = find_portrait_files()
    logger.info(f"Located {len(portraits)} realistic portrait image(s): {list(portraits.keys())}")

    # 1. Setup Directories
    uploads_dir = repo_root / "uploads"
    photos_dir = uploads_dir / "photos"
    faces_dir = uploads_dir / "faces"
    evidence_dir = uploads_dir / "evidence"
    photos_dir.mkdir(parents=True, exist_ok=True)
    faces_dir.mkdir(parents=True, exist_ok=True)
    evidence_dir.mkdir(parents=True, exist_ok=True)

    # 2. Connect to Backend DB (SQLite / PostgreSQL)
    backend_db_url = "sqlite+aiosqlite:///backend_local.db"
    engine = create_async_engine(backend_db_url, echo=False)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    session_factory = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)
    
    # 3. Setup Edge Agent SQLite Store
    edge_store = SQLiteStore(db_path=os.path.join(repo_root, "local_data.db"))

    enrolled_embeddings = []

    async with session_factory() as session:
        # A. Seed Users
        users_data = [
            ("uid_admin_001", "System Admin", "admin@fmp.local", "ADMIN"),
            ("uid_operator_001", "CCTV Operator", "operator@fmp.local", "OPERATOR"),
            ("uid_citizen_raj", "Rajesh Sharma", "rajesh@fmp.local", "CITIZEN"),
        ]
        user_records = []
        for uid, name, email, role in users_data:
            res = await session.execute(select(User).where(User.firebase_uid == uid))
            user = res.scalar_one_or_none()
            if not user:
                user = User(
                    id=uuid.uuid4(),
                    firebase_uid=uid,
                    name=name,
                    email=email,
                    role=role,
                    language="en",
                )
                session.add(user)
                await session.flush()
                await session.refresh(user)
            user_records.append(user)

        citizen_user = user_records[-1]

        # B. Seed Missing Persons Profiles with Real Photos & ArcFace Embeddings
        profiles = [
            {
                "name": "Aarav Sharma",
                "age": 20,
                "gender": "male",
                "height": 175,
                "description": "Wearing navy blue t-shirt, short dark hair",
                "last_seen": "Metro Station Gate 2",
                "contact": "+91-9876543210",
                "portrait_file": portraits.get("Aarav Sharma"),
            },
            {
                "name": "Priya Patel",
                "age": 22,
                "gender": "female",
                "height": 162,
                "description": "Wearing red kurti with gold chain, long dark hair",
                "last_seen": "Central Bus Terminus Platform 4",
                "contact": "+91-9876543211",
                "portrait_file": portraits.get("Priya Patel"),
            },
            {
                "name": "Rohan Gupta",
                "age": 10,
                "gender": "male",
                "height": 135,
                "description": "Wearing yellow striped polo shirt, short hair",
                "last_seen": "City Park Playground",
                "contact": "+91-9876543212",
                "portrait_file": portraits.get("Rohan Gupta"),
            },
        ]

        # Clear existing old cached embeddings in Edge DB
        with edge_store._get_connection() as conn:
            conn.execute("DELETE FROM cached_embeddings;")

        for prof in profiles:
            p_file = prof["portrait_file"]
            if not p_file or not os.path.isfile(p_file):
                logger.warning(f"Portrait not found for {prof['name']}, skipping...")
                continue

            with open(p_file, "rb") as pf:
                image_bytes = pf.read()

            # Real AI face detection, affine alignment, and 512-D ArcFace embedding
            try:
                crop_bytes, emb_vec, det_score = process_person_photo(image_bytes)
                logger.info(
                    f"Processed real face photo for {prof['name']}: "
                    f"det_score={det_score:.3f}, emb_dim={emb_vec.shape[0]}, norm={np.linalg.norm(emb_vec):.4f}"
                )
            except Exception as e:
                logger.error(f"Failed to process face photo for {prof['name']}: {e}")
                continue

            # Save files
            photo_uuid = uuid.uuid4().hex
            orig_filename = f"{photo_uuid}.jpg"
            crop_filename = f"crop_{photo_uuid}.jpg"

            (photos_dir / orig_filename).write_bytes(image_bytes)
            (faces_dir / crop_filename).write_bytes(crop_bytes)

            # Check if person exists in DB
            res = await session.execute(
                select(MissingPerson).where(MissingPerson.full_name == prof["name"])
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

            # Add Photo record
            photo = Photo(
                id=uuid.uuid4(),
                person_id=person.id,
                original_path=f"/uploads/photos/{orig_filename}",
                face_crop_path=f"/uploads/faces/{crop_filename}",
                is_primary=True,
                processing_status="SUCCESS",
            )
            session.add(photo)
            await session.flush()

            # Add FaceEmbedding record
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
            await session.flush()

            # Insert directly into Edge Agent SQLite Store
            edge_store.save_embedding(
                id=str(embedding.id),
                person_id=str(person.id),
                person_name=person.full_name,
                embedding_data=emb_bytes,
                photo_url=f"/uploads/photos/{orig_filename}",
            )
            enrolled_embeddings.append((str(person.id), person.full_name, emb_vec))

        # C. Seed Edge Agent Device & Cameras
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
                ("CAM-01", "CCTV Monitor 1", "test_cctv_feed.mp4", "Main Entrance", 28.6139, 77.2090),
                ("CAM-02", "Laptop Webcam", "0", "Security Desk", 28.6145, 77.2095),
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
                    is_active=True,
                )
                session.add(camera)
                await session.flush()

        # Update configured cameras in Edge local store
        edge_cams = [
            {"local_camera_id": "CAM-01", "name": "CCTV Monitor 1", "rtsp_url": "test_cctv_feed.mp4"},
            {"local_camera_id": "CAM-02", "name": "Laptop Webcam", "rtsp_url": "0"},
        ]
        edge_store.set_sync_state("configured_cameras", json.dumps(edge_cams))
        edge_store.set_sync_state("last_sync_timestamp", datetime.now(timezone.utc).isoformat())

        await session.commit()

    logger.info("══════════════════════════════════════════════════════════════════")
    logger.info(f"SUCCESS: Seeded {len(enrolled_embeddings)} real missing persons with ArcFace 512-D embeddings!")
    for pid, pname, _ in enrolled_embeddings:
        logger.info(f"  • {pname} (ID: {pid})")
    logger.info("══════════════════════════════════════════════════════════════════")


if __name__ == "__main__":
    asyncio.run(seed_real_data())
