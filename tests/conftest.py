"""Central pytest fixtures for FIND-MISSING-PEP end-to-end and integration tests."""
import asyncio
import base64
import hashlib
import io
import os
import shutil
import sys
import tempfile
from pathlib import Path
from typing import AsyncGenerator, Dict, Generator, Tuple
from unittest.mock import MagicMock
from uuid import UUID, uuid4

import cv2
import httpx
from httpx import ASGITransport, AsyncClient
import numpy as np
import pytest
import pytest_asyncio
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

# Ensure workspace root, backend, and edge_agent are dynamically in python path
ROOT_DIR = Path(__file__).resolve().parent.parent
for p in [str(ROOT_DIR), str(ROOT_DIR / "backend"), str(ROOT_DIR / "edge_agent")]:
    if p not in sys.path:
        sys.path.insert(0, p)

from app.api.deps import get_db
from app.config import settings
from app.database import Base
from app.main import app
from edge_agent.storage.faiss_store import FAISSStore
from edge_agent.storage.local_db import SQLiteStore

# ═════════════════════════════════════════════════════════════════════════════
# 1. Database & Isolation Fixtures
# ═════════════════════════════════════════════════════════════════════════════

from sqlalchemy.pool import StaticPool

TEST_DATABASE_URL = "sqlite+aiosqlite:///:memory:"

test_engine = create_async_engine(
    TEST_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
    echo=False,
)
test_session_factory = async_sessionmaker(
    test_engine, class_=AsyncSession, expire_on_commit=False
)


async def override_get_db() -> AsyncGenerator[AsyncSession, None]:
    """Dependency override providing isolated in-memory async session."""
    async with test_session_factory() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()


app.dependency_overrides[get_db] = override_get_db


@pytest_asyncio.fixture(autouse=True)
async def setup_database() -> AsyncGenerator[None, None]:
    """Recreate clean database tables before each test execution."""
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
        await conn.run_sync(Base.metadata.create_all)
    yield
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


@pytest.fixture(autouse=True)
def isolated_upload_dir(monkeypatch) -> Generator[Path, None, None]:
    """Isolate file uploads and evidence to a transient directory."""
    temp_dir = tempfile.mkdtemp(prefix="fmp_uploads_")
    monkeypatch.setattr(settings, "UPLOAD_DIR", temp_dir)

    # Ensure required subfolders exist
    for sub in ["photos", "faces", "evidence"]:
        os.makedirs(os.path.join(temp_dir, sub), exist_ok=True)

    # Re-point mounted StaticFiles app to temp_dir
    orig_states = []
    for route in app.routes:
        if getattr(route, "name", None) == "uploads" and hasattr(route.app, "all_directories"):
            orig_states.append((route.app, route.app.directory, list(route.app.all_directories)))
            route.app.directory = temp_dir
            route.app.all_directories = [temp_dir]

    yield Path(temp_dir)

    for sf_app, orig_dir, orig_all in orig_states:
        sf_app.directory = orig_dir
        sf_app.all_directories = orig_all

    shutil.rmtree(temp_dir, ignore_errors=True)


# ═════════════════════════════════════════════════════════════════════════════
# 2. HTTP Clients & Mock Face Processing
# ═════════════════════════════════════════════════════════════════════════════

@pytest_asyncio.fixture
async def client() -> AsyncGenerator[AsyncClient, None]:
    """Async HTTP test client directly bound to the FastAPI application."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://testserver") as ac:
        yield ac


@pytest.fixture
def mock_face_processing(monkeypatch) -> None:
    """
    Mocks face processing to deterministically extract ArcFace embeddings
    without requiring ONNX model weights to be downloaded.
    """
    import app.api.reports as reports_api
    import app.services.face_processing as face_service

    def fake_process_person_photo(image_bytes: bytes) -> Tuple[bytes, np.ndarray, float]:
        # Return synthetic aligned 112x112 JPEG face crop
        crop_img = np.full((112, 112, 3), fill_value=200, dtype=np.uint8)
        cv2.circle(crop_img, (56, 56), 35, (180, 200, 220), -1)
        _, crop_bytes = cv2.imencode(".jpg", crop_img)

        # Seed embedding deterministically from image contents
        seed = int(hashlib.md5(image_bytes).hexdigest()[:8], 16)
        rng = np.random.RandomState(seed)
        vec = rng.randn(512).astype(np.float32)
        vec = vec / np.linalg.norm(vec)

        return crop_bytes.tobytes(), vec, 0.98

    monkeypatch.setattr(reports_api, "process_person_photo", fake_process_person_photo)
    monkeypatch.setattr(face_service, "process_person_photo", fake_process_person_photo)


# ═════════════════════════════════════════════════════════════════════════════
# 3. Edge Agent Fixtures
# ═════════════════════════════════════════════════════════════════════════════

@pytest.fixture
def edge_temp_dir() -> Generator[Path, None, None]:
    """Transient working directory for Edge Agent SQLite database and FAISS index."""
    temp_dir = tempfile.mkdtemp(prefix="fmp_edge_test_")
    yield Path(temp_dir)
    shutil.rmtree(temp_dir, ignore_errors=True)


@pytest.fixture
def edge_sqlite_store(edge_temp_dir: Path) -> Generator[SQLiteStore, None, None]:
    """Isolated Edge Agent SQLite store."""
    db_file = str(edge_temp_dir / "edge_agent.db")
    store = SQLiteStore(db_path=db_file)
    yield store
    store.close()


@pytest.fixture
def edge_faiss_store(edge_temp_dir: Path) -> Generator[FAISSStore, None, None]:
    """Isolated Edge Agent FAISS vector search store."""
    index_file = str(edge_temp_dir / "embeddings.index")
    store = FAISSStore(index_path=index_file, dim=512)
    yield store


# ═════════════════════════════════════════════════════════════════════════════
# 4. Synthetic Generators
# ═════════════════════════════════════════════════════════════════════════════

def create_synthetic_portrait_bytes(name: str = "Test Person") -> bytes:
    """Generates synthetic JPEG portrait bytes for HTTP upload."""
    img = np.full((300, 300, 3), fill_value=220, dtype=np.uint8)
    cv2.circle(img, (150, 140), 65, (190, 210, 230), -1)
    cv2.circle(img, (130, 125), 8, (40, 30, 20), -1)
    cv2.circle(img, (170, 125), 8, (40, 30, 20), -1)
    cv2.line(img, (135, 170), (165, 170), (70, 70, 150), 2)
    cv2.putText(img, name, (10, 290), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (50, 50, 50), 1)
    _, buf = cv2.imencode(".jpg", img)
    return buf.tobytes()


def make_unit_embedding(seed: int = 42) -> np.ndarray:
    """Generates a unit L2-normalized 512-D float32 vector."""
    rng = np.random.RandomState(seed)
    vec = rng.randn(512).astype(np.float32)
    return vec / np.linalg.norm(vec)
