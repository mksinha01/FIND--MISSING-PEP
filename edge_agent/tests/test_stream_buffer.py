"""Unit and integration tests for Edge Agent camera stream ingestion and circular buffer."""
import os
import tempfile
import threading
import time
from typing import Any, Dict, List
from unittest.mock import MagicMock, patch

import cv2
import numpy as np
import pytest

from edge_agent.camera.circular_buffer import CircularFrameBuffer
from edge_agent.camera.frame_sampler import FrameSampler
from edge_agent.camera.onvif_discovery import DiscoveredCamera, ONVIFDiscovery
from edge_agent.camera.rtsp_reader import RTSPReader, StreamStatus
from edge_agent.camera.stream_manager import StreamManager
from edge_agent.storage.local_db import SQLiteStore


# ═════════════════════════════════════════════════════════════════════════════
# CircularFrameBuffer Tests
# ═════════════════════════════════════════════════════════════════════════════


def test_circular_buffer_capacity_eviction():
    """Verify FIFO eviction maintains maximum buffer capacity of 150 frames."""
    buffer = CircularFrameBuffer(maxlen=150)
    assert len(buffer) == 0
    assert not buffer

    # Append 200 synthetic frames
    for i in range(200):
        dummy_frame = np.full((100, 100, 3), fill_value=i % 256, dtype=np.uint8)
        buffer.append(dummy_frame, timestamp=float(i))

    assert len(buffer) == 150
    assert bool(buffer) is True

    # Oldest 50 frames (0..49) should be evicted; first frame should be index 50
    snapshots = buffer.get_snapshots()
    assert len(snapshots) == 150
    assert snapshots[0][0] == 50.0
    assert snapshots[-1][0] == 199.0


def test_circular_buffer_frame_isolation():
    """Verify frames are copied on append, preventing external mutation bugs."""
    buffer = CircularFrameBuffer(maxlen=10)
    original = np.zeros((50, 50, 3), dtype=np.uint8)
    buffer.append(original, timestamp=1.0)

    # Mutate original in-place
    original[:] = 255

    latest_frame = buffer.get_latest_frame()
    assert latest_frame is not None
    assert np.all(latest_frame == 0), "Buffered frame was mutated by external array modify!"


def test_circular_buffer_dump_video_and_clip():
    """Verify 150-frame buffer dumps a valid, playable MP4 video file at 15 FPS."""
    buffer = CircularFrameBuffer(maxlen=150)
    w, h = 320, 240
    start_ts = 1000.0

    # Populate exact 150 frames (10 seconds at 15 FPS)
    for i in range(150):
        frame = np.zeros((h, w, 3), dtype=np.uint8)
        # Add varying color pattern to verify frame integrity
        frame[:, :, 0] = (i * 2) % 256
        frame[:, :, 1] = (i * 3) % 256
        buffer.append(frame, timestamp=start_ts + (i / 15.0))

    assert len(buffer) == 150
    assert abs(buffer.duration_seconds - (149.0 / 15.0)) < 0.1

    with tempfile.TemporaryDirectory() as tmpdir:
        output_path = os.path.join(tmpdir, "sighting_clip.mp4")

        success = buffer.dump_video(output_path=output_path, fps=15.0)
        assert success is True
        assert os.path.isfile(output_path)
        assert os.path.getsize(output_path) > 0

        # Verify with OpenCV VideoCapture
        cap = cv2.VideoCapture(output_path)
        assert cap.isOpened(), "Generated MP4 could not be opened by cv2.VideoCapture"

        read_count = 0
        while True:
            ret, frame = cap.read()
            if not ret or frame is None:
                break
            assert frame.shape == (h, w, 3)
            read_count += 1
        cap.release()

        assert read_count == 150, f"Expected 150 frames, read {read_count}"

        # Test dump_clip alias
        clip_path = os.path.join(tmpdir, "alias_clip.mp4")
        result_path = buffer.dump_clip(clip_path, fps=15.0)
        assert result_path == clip_path
        assert os.path.isfile(clip_path)


def test_circular_buffer_empty_dump():
    """Verify dump_video gracefully returns False on empty buffer without raising."""
    buffer = CircularFrameBuffer(maxlen=150)
    with tempfile.TemporaryDirectory() as tmpdir:
        output_path = os.path.join(tmpdir, "empty.mp4")
        assert buffer.dump_video(output_path) is False
        assert buffer.dump_clip(output_path) is None
        assert not os.path.exists(output_path)


def test_circular_buffer_thread_safety():
    """Verify concurrent appends and dumps under multithreaded contention."""
    buffer = CircularFrameBuffer(maxlen=150)
    stop_workers = threading.Event()
    errors = []

    def writer_worker():
        idx = 0
        while not stop_workers.is_set():
            try:
                frame = np.full((100, 100, 3), fill_value=idx % 256, dtype=np.uint8)
                buffer.append(frame, timestamp=time.time())
                idx += 1
                time.sleep(0.001)
            except Exception as e:
                errors.append(e)

    def reader_worker():
        while not stop_workers.is_set():
            try:
                _ = buffer.get_frames()
                _ = buffer.get_latest()
                _ = len(buffer)
                time.sleep(0.002)
            except Exception as e:
                errors.append(e)

    threads = [
        threading.Thread(target=writer_worker),
        threading.Thread(target=writer_worker),
        threading.Thread(target=reader_worker),
        threading.Thread(target=reader_worker),
    ]

    for t in threads:
        t.start()

    time.sleep(0.3)
    stop_workers.set()

    for t in threads:
        t.join()

    assert not errors, f"Thread contention errors occurred: {errors}"
    assert len(buffer) > 0


# ═════════════════════════════════════════════════════════════════════════════
# FrameSampler Tests
# ═════════════════════════════════════════════════════════════════════════════


def test_frame_sampler_regulation():
    """Verify FrameSampler regulates a simulated 30 FPS stream down to 15 FPS."""
    sampler = FrameSampler(target_fps=15.0)
    assert sampler.target_fps == 15.0

    frame = np.zeros((100, 100, 3), dtype=np.uint8)

    # Simulate 60 frames arriving at 30 FPS (33.3ms per frame = 2.0s duration)
    dt = 1.0 / 30.0
    sampled_count = 0
    current_time = 100.0

    for _ in range(60):
        res = sampler.sample(frame, timestamp=current_time)
        if res is not None:
            sampled_count += 1
        current_time += dt

    # At 15 FPS across 2.0s, we expect approximately 30-31 sampled frames
    assert 28 <= sampled_count <= 32, f"Expected ~30 frames sampled, got {sampled_count}"

    stats = sampler.get_stats()
    assert stats["total_frames"] == 60
    assert stats["sampled_frames"] == sampled_count
    assert stats["dropped_frames"] == 60 - sampled_count
    assert 45.0 <= stats["sample_rate_pct"] <= 55.0


def test_frame_sampler_target_fps_update_and_reset():
    """Verify updating target_fps changes sampling interval and reset clears counts."""
    sampler = FrameSampler(target_fps=10.0)
    assert sampler._interval == 0.1

    sampler.target_fps = 20.0
    assert sampler._interval == 0.05

    frame = np.zeros((10, 10, 3), dtype=np.uint8)
    sampler.sample(frame, timestamp=1.0)
    sampler.sample(frame, timestamp=1.01)

    assert sampler._total_frames == 2
    sampler.reset()
    assert sampler._total_frames == 0
    assert sampler._sampled_frames == 0


# ═════════════════════════════════════════════════════════════════════════════
# RTSPReader Tests
# ═════════════════════════════════════════════════════════════════════════════


def test_rtsp_reader_url_formatting():
    """Verify RTSPReader enforces ?rtsp_transport=tcp on RTSP URLs."""
    reader1 = RTSPReader(camera_id="CAM-01", rtsp_url="rtsp://192.168.1.50:554/live")
    assert "rtsp_transport=tcp" in reader1.rtsp_url

    # If query param already exists
    reader2 = RTSPReader(camera_id="CAM-02", rtsp_url="rtsp://192.168.1.50:554/live?channel=1")
    assert "channel=1&rtsp_transport=tcp" in reader2.rtsp_url

    # Non-rtsp (e.g. test video file) is not modified
    reader3 = RTSPReader(camera_id="CAM-03", rtsp_url="test.mp4")
    assert reader3.rtsp_url == "test.mp4"


def test_rtsp_reader_cooperative_lifecycle_and_mock_ingestion():
    """Verify cooperative thread start, frame ingestion to circular buffer, and stop."""
    frame_w, frame_h = 160, 120
    test_frame = np.full((frame_h, frame_w, 3), fill_value=42, dtype=np.uint8)

    mock_cap = MagicMock()
    mock_cap.isOpened.return_value = True
    # Return valid frame for several reads, then brief sleep
    mock_cap.read.return_value = (True, test_frame)

    received_frames = []

    def on_frame(f, ts):
        received_frames.append((f, ts))

    reader = RTSPReader(
        camera_id="TEST-CAM",
        rtsp_url="rtsp://fake-stream/live",
        buffer_size=50,
        on_frame_callback=on_frame,
    )

    with patch.object(reader, "_create_capture", return_value=mock_cap):
        reader.start()
        assert reader.status in (StreamStatus.CONNECTING, StreamStatus.STREAMING)

        # Allow worker thread to read several frames
        time.sleep(0.15)

        assert reader.is_connected is True
        assert len(reader.circular_buffer) > 0
        assert len(received_frames) > 0

        latest = reader.get_latest_frame()
        assert latest is not None
        assert latest[1].shape == (frame_h, frame_w, 3)

        # Test cooperative shutdown
        reader.stop(timeout=2.0)
        assert reader.status == StreamStatus.STOPPED
        assert reader._thread is None


def test_rtsp_reader_reconnect_backoff():
    """Verify exponential backoff reconnection intervals progression upon disconnection."""
    reader = RTSPReader(camera_id="FAIL-CAM", rtsp_url="rtsp://broken-stream")
    assert reader.RECONNECT_BACKOFF == [1, 2, 5, 10, 30]

    mock_failing_cap = MagicMock()
    mock_failing_cap.isOpened.return_value = False

    with patch.object(reader, "_create_capture", return_value=mock_failing_cap):
        # We start the reader and let it fail to connect
        reader.start()
        time.sleep(0.05)

        # Check that reconnect attempt occurred
        stats = reader.get_stats()
        assert stats["reconnect_count"] >= 1
        assert stats["status"] in (StreamStatus.ERROR, StreamStatus.RECONNECTING, StreamStatus.CONNECTING)

        reader.stop(timeout=2.0)
        assert reader.status == StreamStatus.STOPPED


# ═════════════════════════════════════════════════════════════════════════════
# ONVIFDiscovery Tests
# ═════════════════════════════════════════════════════════════════════════════


def test_discovered_camera_dataclass():
    """Verify DiscoveredCamera fields and dictionary serialization."""
    cam = DiscoveredCamera(
        ip="192.168.1.120",
        port=80,
        name="Lobby Camera",
        xaddrs=["http://192.168.1.120:80/onvif/device_service"],
        rtsp_url="rtsp://192.168.1.120:554/live/ch0?rtsp_transport=tcp",
        manufacturer="Hikvision",
        model="DS-2CD2042WD-I",
        scopes=["onvif://www.onvif.org/name/Lobby%20Camera"],
    )

    data = cam.to_dict()
    assert data["ip"] == "192.168.1.120"
    assert data["name"] == "Lobby Camera"
    assert "rtsp_transport=tcp" in data["rtsp_url"]
    assert data["manufacturer"] == "Hikvision"


def test_onvif_discovery_parsing():
    """Verify parsing of mock WS-Discovery service responses."""
    discovery = ONVIFDiscovery(timeout=1.0)

    mock_service = MagicMock()
    mock_service.getXAddrs.return_value = ["http://192.168.1.88:8080/onvif/device_service"]

    mock_scope1 = MagicMock()
    mock_scope1.getValue.return_value = "onvif://www.onvif.org/name/ParkingLot-Cam"
    mock_scope2 = MagicMock()
    mock_scope2.getValue.return_value = "onvif://www.onvif.org/hardware/Model-X1"
    mock_service.getScopes.return_value = [mock_scope1, mock_scope2]

    cam = discovery._parse_ws_service(mock_service)
    assert cam is not None
    assert cam.ip == "192.168.1.88"
    assert cam.port == 8080
    assert cam.name == "ParkingLot-Cam"
    assert cam.model == "Model-X1"
    assert "rtsp://192.168.1.88:554/live/ch0" in cam.rtsp_url


def test_onvif_discovery_fallback():
    """Verify discovery handles network timeouts gracefully without throwing unhandled exceptions."""
    discovery = ONVIFDiscovery(timeout=0.1)
    with patch.object(discovery, "_raw_udp_probe", return_value=[]):
        cams = discovery.discover(timeout=0.1)
        assert isinstance(cams, list)


# ═════════════════════════════════════════════════════════════════════════════
# StreamManager Tests
# ═════════════════════════════════════════════════════════════════════════════


def test_stream_manager_camera_limits_and_lifecycle():
    """Verify StreamManager enforces 1 to 4 streams and manages addition/removal."""
    manager = StreamManager(max_cameras=4)
    assert manager.camera_count == 0

    # Add 4 cameras without autostarting
    for i in range(1, 5):
        cam_id = f"CAM-0{i}"
        reader = manager.add_camera(
            local_camera_id=cam_id,
            rtsp_url=f"rtsp://192.168.1.{10+i}/stream",
            name=f"Camera {i}",
            autostart=False,
        )
        assert reader.camera_id == cam_id

    assert manager.camera_count == 4
    assert set(manager.get_active_camera_ids()) == {"CAM-01", "CAM-02", "CAM-03", "CAM-04"}

    # Attempting to add a 5th camera must raise ValueError
    with pytest.raises(ValueError, match="Maximum concurrent camera limit"):
        manager.add_camera(
            local_camera_id="CAM-05",
            rtsp_url="rtsp://192.168.1.15/stream",
            autostart=False,
        )

    # Removing a camera frees a slot
    assert manager.remove_camera("CAM-02") is True
    assert manager.camera_count == 3
    assert manager.get_camera("CAM-02") is None

    # Now adding CAM-05 succeeds
    manager.add_camera(
        local_camera_id="CAM-05",
        rtsp_url="rtsp://192.168.1.15/stream",
        autostart=False,
    )
    assert manager.camera_count == 4


def test_stream_manager_backend_sync_and_sqlite_persistence():
    """Verify synchronization with backend API and local SQLite camera_mappings persistence."""
    with tempfile.TemporaryDirectory() as tmpdir:
        db_path = os.path.join(tmpdir, "test_camera.db")
        db = SQLiteStore(db_path=db_path)

        mock_api_client = MagicMock()
        mock_api_client.sync_cameras.return_value = {
            "CAM-01": "00000000-0000-0000-0000-000000000001",
            "CAM-02": "00000000-0000-0000-0000-000000000002",
        }

        manager = StreamManager(
            agent_id="test-agent-123",
            api_client=mock_api_client,
            local_db=db,
        )

        manager.add_camera(
            local_camera_id="CAM-01",
            rtsp_url="rtsp://192.168.1.10/live",
            name="Front Gate",
            autostart=False,
        )
        manager.add_camera(
            local_camera_id="CAM-02",
            rtsp_url="rtsp://192.168.1.11/live",
            name="Back Exit",
            autostart=False,
        )

        mappings = manager.sync_with_backend()
        assert mappings["CAM-01"] == "00000000-0000-0000-0000-000000000001"
        assert mappings["CAM-02"] == "00000000-0000-0000-0000-000000000002"

        # Verify persisted in SQLite
        assert manager.get_camera_uuid("CAM-01") == "00000000-0000-0000-0000-000000000001"
        assert manager.get_local_camera_id("00000000-0000-0000-0000-000000000002") == "CAM-02"

        # Verify stats include backend UUID
        stats = manager.get_stats()
        assert stats["total_cameras"] == 2
        assert stats["cameras"]["CAM-01"]["backend_uuid"] == "00000000-0000-0000-0000-000000000001"


def test_stream_manager_stop_all():
    """Verify stop_all cooperatively stops all camera readers."""
    manager = StreamManager()
    cam1 = manager.add_camera("CAM-01", "test1.mp4", autostart=False)
    cam2 = manager.add_camera("CAM-02", "test2.mp4", autostart=False)

    cam1.stop = MagicMock()
    cam2.stop = MagicMock()

    manager.stop_all(timeout=1.0)
    cam1.stop.assert_called_once()
    cam2.stop.assert_called_once()
