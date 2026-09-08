"""Comprehensive automated test suite for Edge Agent local storage, FAISS vector cache, and network components."""
import base64
import os
import shutil
import tempfile
import time
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch
import uuid

import numpy as np
import pytest

from edge_agent.config import EdgeSettings
from edge_agent.network.api_client import ApiClient, ApiClientError, AuthenticationError
from edge_agent.network.embedding_syncer import EmbeddingSyncer
from edge_agent.network.sighting_uploader import SightingUploader
from edge_agent.network.sse_listener import SSEListener
from edge_agent.storage.evidence_store import EvidenceStore
from edge_agent.storage.faiss_store import FAISSStore
from edge_agent.storage.local_db import SQLiteStore


@pytest.fixture
def temp_dir():
    """Create a clean temporary directory for tests."""
    td = tempfile.mkdtemp(prefix="edge_test_")
    yield td
    shutil.rmtree(td, ignore_errors=True)


@pytest.fixture
def local_db(temp_dir):
    """Initialize an isolated SQLiteStore."""
    db_file = os.path.join(temp_dir, "test_local.db")
    return SQLiteStore(db_path=db_file)


@pytest.fixture
def evidence_store(temp_dir):
    """Initialize an isolated EvidenceStore."""
    ev_dir = os.path.join(temp_dir, "evidence")
    return EvidenceStore(base_dir=ev_dir, max_storage_mb=50.0, retention_days=7)


@pytest.fixture
def faiss_store(temp_dir):
    """Initialize an isolated FAISSStore."""
    idx_path = os.path.join(temp_dir, "test_faiss.bin")
    return FAISSStore(index_path=idx_path, dim=512)


# ═════════════════════════════════════════════════════════════════════════
# 1. SQLite Storage Tests (Fix #13, Fix #15, Fix #17, Fix #18)
# ═════════════════════════════════════════════════════════════════════════

class TestSQLiteStore:
    def test_sync_state_crud(self, local_db):
        """Test key-value sync_state operations."""
        assert local_db.get_sync_state("device_id") is None
        assert local_db.get_sync_state("device_id", default="default_id") == "default_id"

        local_db.set_sync_state("device_id", "dev-12345")
        assert local_db.get_sync_state("device_id") == "dev-12345"

        local_db.set_sync_state("device_id", "dev-67890")
        assert local_db.get_sync_state("device_id") == "dev-67890"

        local_db.set_sync_state("api_key", "sec-key-abc")
        all_states = local_db.get_all_sync_state()
        assert all_states["device_id"] == "dev-67890"
        assert all_states["api_key"] == "sec-key-abc"

    def test_camera_mappings(self, local_db):
        """Test Fix #17: Local camera name to backend UUID mapping."""
        cam1_uuid = str(uuid.uuid4())
        cam2_uuid = str(uuid.uuid4())

        local_db.save_camera_mapping("CAM-01", cam1_uuid)
        assert local_db.get_camera_uuid("CAM-01") == cam1_uuid
        assert local_db.get_local_camera_id(cam1_uuid) == "CAM-01"

        mappings = {"CAM-02": cam2_uuid, "CAM-03": str(uuid.uuid4())}
        local_db.save_camera_mappings(mappings)

        all_maps = local_db.get_all_camera_mappings()
        assert len(all_maps) == 3
        assert all_maps["CAM-02"] == cam2_uuid

        assert local_db.delete_camera_mapping("CAM-01") is True
        assert local_db.get_camera_uuid("CAM-01") is None

    def test_multi_embedding_per_person_no_collision(self, local_db):
        """
        Test Fix #13: Multi-photo per person support.
        Storing multiple embeddings for the SAME person_id MUST NOT cause a UNIQUE constraint failure.
        """
        person_id = str(uuid.uuid4())
        emb1_id = str(uuid.uuid4())
        emb2_id = str(uuid.uuid4())
        emb3_id = str(uuid.uuid4())

        vec1 = np.random.randn(512).astype(np.float32).tobytes()
        vec2 = np.random.randn(512).astype(np.float32).tobytes()
        vec3 = np.random.randn(512).astype(np.float32).tobytes()

        # Save 3 different photos for the same person
        local_db.save_embedding(emb1_id, person_id, "Rahul Sharma", vec1, "http://photos/1.jpg")
        local_db.save_embedding(emb2_id, person_id, "Rahul Sharma", vec2, "http://photos/2.jpg")
        local_db.save_embedding(emb3_id, person_id, "Rahul Sharma", vec3, "http://photos/3.jpg")

        assert local_db.count_embeddings() == 3
        assert local_db.count_persons() == 1

        person_embeddings = local_db.get_embeddings_for_person(person_id)
        assert len(person_embeddings) == 3
        ids = {e["id"] for e in person_embeddings}
        assert ids == {emb1_id, emb2_id, emb3_id}

    def test_tombstone_pruning(self, local_db):
        """
        Test Fix #15: Removing persons by ID prunes all their embeddings.
        """
        p1 = str(uuid.uuid4())
        p2 = str(uuid.uuid4())

        vec = np.random.randn(512).astype(np.float32).tobytes()

        local_db.save_embedding(str(uuid.uuid4()), p1, "Person A", vec)
        local_db.save_embedding(str(uuid.uuid4()), p1, "Person A", vec)
        local_db.save_embedding(str(uuid.uuid4()), p2, "Person B", vec)

        assert local_db.count_embeddings() == 3
        assert local_db.count_persons() == 2

        # Prune person 1 (e.g. found/resolved)
        deleted = local_db.remove_embeddings_for_persons([p1])
        assert deleted == 2
        assert local_db.count_embeddings() == 1
        assert local_db.count_persons() == 1
        assert len(local_db.get_embeddings_for_person(p1)) == 0
        assert len(local_db.get_embeddings_for_person(p2)) == 1

    def test_pending_sightings_offline_queue(self, local_db):
        """
        Test Fix #18: Durable offline queue operations.
        """
        person_id = str(uuid.uuid4())
        camera_id = str(uuid.uuid4())

        s_id = local_db.enqueue_sighting(
            person_id=person_id,
            camera_id=camera_id,
            similarity_score=0.88,
            detected_at=datetime.now(timezone.utc).isoformat(),
            face_crop_path="/tmp/crop.jpg",
            full_frame_path="/tmp/frame.jpg",
            confidence_level="HIGH",
        )

        assert s_id > 0
        assert local_db.count_pending_sightings() == 1

        pending = local_db.get_pending_sightings()
        assert len(pending) == 1
        assert pending[0]["person_id"] == person_id
        assert pending[0]["similarity_score"] == 0.88
        assert pending[0]["retry_count"] == 0

        # Increment retry count
        new_count = local_db.increment_retry_count(s_id)
        assert new_count == 1
        pending = local_db.get_pending_sightings()
        assert pending[0]["retry_count"] == 1

        # Delete sighting after successful upload
        deleted = local_db.delete_pending_sighting(s_id)
        assert deleted is True
        assert local_db.count_pending_sightings() == 0


# ═════════════════════════════════════════════════════════════════════════
# 2. Evidence Store Tests
# ═════════════════════════════════════════════════════════════════════════

class TestEvidenceStore:
    def test_save_and_retrieve_media(self, evidence_store):
        """Test saving crops, frames, and video clips."""
        # 1. Face crop (numpy array)
        crop_img = np.zeros((112, 112, 3), dtype=np.uint8)
        crop_path = evidence_store.save_face_crop(crop_img)
        assert os.path.isfile(crop_path)
        assert crop_path.endswith(".jpg")

        # 2. Full frame (bytes)
        frame_bytes = b"fake_jpeg_image_content"
        frame_path = evidence_store.save_full_frame(frame_bytes)
        assert os.path.isfile(frame_path)

        # 3. Video clip (bytes)
        clip_bytes = b"fake_mp4_video_content"
        clip_path = evidence_store.save_video_clip(clip_bytes)
        assert os.path.isfile(clip_path)
        assert clip_path.endswith(".mp4")

        # Usage check
        usage_mb = evidence_store.get_storage_usage_mb()
        assert usage_mb > 0

        # Delete specific file
        assert evidence_store.delete_file(crop_path) is True
        assert not os.path.exists(crop_path)

    def test_cleanup_quota_and_retention(self, evidence_store):
        """Test evidence pruning on storage quota limits."""
        # Create multiple dummy files
        dummy_data = b"x" * 1024 * 1024  # 1 MB each
        paths = [evidence_store.save_full_frame(dummy_data) for _ in range(5)]

        assert evidence_store.get_storage_usage_mb() >= 4.5

        # Prune with 2 MB quota limit
        deleted = evidence_store.cleanup_old_evidence(max_storage_mb=2.0)
        assert deleted >= 3
        assert evidence_store.get_storage_usage_mb() <= 2.5


# ═════════════════════════════════════════════════════════════════════════
# 3. FAISS Vector Store Tests (Rule 7)
# ═════════════════════════════════════════════════════════════════════════

class TestFAISSStore:
    def test_rebuild_and_search(self, faiss_store):
        """Test thread-safe vector addition and cosine search."""
        target_vec = np.random.randn(512).astype(np.float32)
        target_vec = target_vec / np.linalg.norm(target_vec)

        other_vec = np.random.randn(512).astype(np.float32)
        other_vec = other_vec / np.linalg.norm(other_vec)

        items = [
            {
                "id": "emb-1",
                "person_id": "person-target",
                "embedding_data": target_vec.tobytes(),
            },
            {
                "id": "emb-2",
                "person_id": "person-other",
                "embedding_data": other_vec.tobytes(),
            },
        ]

        count = faiss_store.rebuild(items)
        assert count == 2
        assert faiss_store.size == 2

        # Search with exact target vector
        results = faiss_store.search(target_vec, top_k=2, threshold=0.90)
        assert len(results) == 1
        assert results[0]["person_id"] == "person-target"
        assert results[0]["embedding_id"] == "emb-1"
        assert results[0]["similarity"] >= 0.99

    def test_persistence_save_load(self, temp_dir):
        """Test FAISS index serialization and deserialization."""
        idx_path = os.path.join(temp_dir, "persisted_faiss.bin")
        store = FAISSStore(index_path=idx_path, dim=512)

        vec = np.random.randn(512).astype(np.float32)
        vec = vec / np.linalg.norm(vec)

        store.rebuild([{"id": "emb-persist", "person_id": "person-p", "embedding_data": vec}])
        store.save()

        # Load in a fresh store instance
        store2 = FAISSStore(index_path=idx_path, dim=512)
        assert store2.size == 1
        results = store2.search(vec, top_k=1, threshold=0.95)
        assert len(results) == 1
        assert results[0]["person_id"] == "person-p"


# ═════════════════════════════════════════════════════════════════════════
# 4. Network & Sync Tests
# ═════════════════════════════════════════════════════════════════════════

class TestNetworkAndSync:
    @patch("httpx.Client.post")
    def test_api_client_device_register(self, mock_post):
        """Test ApiClient device registration call."""
        mock_post.return_value = MagicMock(
            status_code=201,
            is_error=False,
            json=lambda: {
                "agent_id": "agent-uuid-123",
                "api_key": "generated-api-key-xyz",
                "message": "Agent registered successfully.",
            },
        )

        client = ApiClient(base_url="http://test-server:8000", enrollment_key="admin-key")
        res = client.register_device(device_id="device-01", name="Entrance Agent")

        assert res["agent_id"] == "agent-uuid-123"
        assert client.api_key == "generated-api-key-xyz"

    @patch("httpx.Client.get")
    def test_embedding_syncer_full_and_incremental(self, mock_get, local_db, faiss_store):
        """Test EmbeddingSyncer pulling embeddings and updating SQLite & FAISS."""
        vec1 = np.random.randn(512).astype(np.float32)
        vec1 = vec1 / np.linalg.norm(vec1)
        b64_vec1 = base64.b64encode(vec1.tobytes()).decode("ascii")

        mock_get.return_value = MagicMock(
            status_code=200,
            is_error=False,
            json=lambda: {
                "full_sync": True,
                "persons": [
                    {
                        "embedding_id": "emb-sync-1",
                        "person_id": "person-sync-1",
                        "person_name": "Sita Devi",
                        "embedding_bytes": b64_vec1,
                        "quality_score": 0.94,
                    }
                ],
                "removed_ids": [],
                "sync_timestamp": "2026-09-06T12:00:00Z",
            },
        )

        api_client = ApiClient(base_url="http://test-server:8000", api_key="test-key")
        syncer = EmbeddingSyncer(api_client=api_client, local_db=local_db, faiss_store=faiss_store)

        summary = syncer.sync_now()
        assert summary["status"] == "success"
        assert summary["added_count"] == 1
        assert local_db.count_embeddings() == 1
        assert faiss_store.size == 1
        assert local_db.get_sync_state("last_synced_at") == "2026-09-06T12:00:00Z"

        # Now test incremental sync with tombstone removal
        mock_get.return_value = MagicMock(
            status_code=200,
            is_error=False,
            json=lambda: {
                "full_sync": False,
                "persons": [],
                "removed_ids": ["person-sync-1"],
                "sync_timestamp": "2026-09-06T13:00:00Z",
            },
        )

        summary2 = syncer.sync_now()
        assert summary2["removed_count"] == 1
        assert local_db.count_embeddings() == 0
        assert faiss_store.size == 0
        assert local_db.get_sync_state("last_synced_at") == "2026-09-06T13:00:00Z"

    @patch("httpx.Client.post")
    def test_sighting_uploader_flow(self, mock_post, local_db, evidence_store):
        """Test SightingUploader offline queue processing, mapping resolution, and backoff."""
        crop_path = evidence_store.save_face_crop(np.zeros((112, 112, 3), dtype=np.uint8))
        frame_path = evidence_store.save_full_frame(np.zeros((480, 640, 3), dtype=np.uint8))

        backend_cam_uuid = str(uuid.uuid4())
        local_db.save_camera_mapping("CAM-FRONT", backend_cam_uuid)

        person_uuid = str(uuid.uuid4())
        sighting_id = local_db.enqueue_sighting(
            person_id=person_uuid,
            camera_id="CAM-FRONT",  # Local name that must be translated
            similarity_score=0.85,
            detected_at=datetime.now(timezone.utc).isoformat(),
            face_crop_path=crop_path,
            full_frame_path=frame_path,
        )

        # 1. Simulate failure -> retry count increments
        mock_post.side_effect = ApiClientError("Network timeout")
        api_client = ApiClient(base_url="http://test-server:8000", api_key="key")
        uploader = SightingUploader(
            api_client=api_client,
            local_db=local_db,
            evidence_store=evidence_store,
            initial_backoff_seconds=0.01,
        )

        uploaded = uploader.process_queue_once()
        assert uploaded == 0
        assert local_db.count_pending_sightings() == 1
        pending = local_db.get_pending_sightings()
        assert pending[0]["retry_count"] == 1

        # 2. Simulate success -> item deleted from SQLite queue
        mock_post.side_effect = None
        mock_post.return_value = MagicMock(
            status_code=201,
            is_error=False,
            json=lambda: {"id": str(uuid.uuid4()), "status": "PENDING"},
        )

        time.sleep(0.03)
        uploaded = uploader.process_queue_once()
        assert uploaded == 1
        assert local_db.count_pending_sightings() == 0

    def test_sse_listener_event_dispatch(self):
        """Test SSE listener parsing stream lines and routing to handlers."""
        listener = SSEListener(base_url="http://test-server:8000")

        received = []
        listener.register_handler("sighting_alert", lambda d: received.append(d))

        # Test internal dispatch directly
        listener._dispatch_event("sighting_alert", {"person_name": "Test Person", "score": 0.92})
        assert len(received) == 1
        assert received[0]["person_name"] == "Test Person"


# ═════════════════════════════════════════════════════════════════════════
# 5. Configuration Tests
# ═════════════════════════════════════════════════════════════════════════

class TestConfig:
    def test_config_save_and_load(self, temp_dir):
        """Test INI configuration serialization and round-trip parsing."""
        ini_file = os.path.join(temp_dir, "test_config.ini")
        cfg = EdgeSettings.load_from_ini(ini_file)

        cfg.backend.url = "http://192.168.1.100:8000"
        cfg.backend.api_key = "saved-agent-key"
        cfg.agent.device_id = "agent-unit-07"
        cfg.ai.face_similarity_threshold = 0.55

        saved_path = cfg.save_to_ini(ini_file)
        assert os.path.isfile(saved_path)

        cfg2 = EdgeSettings.load_from_ini(saved_path)
        assert cfg2.backend.url == "http://192.168.1.100:8000"
        assert cfg2.backend.api_key == "saved-agent-key"
        assert cfg2.agent.device_id == "agent-unit-07"
        assert cfg2.ai.face_similarity_threshold == 0.55
