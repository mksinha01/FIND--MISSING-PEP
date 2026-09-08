"""Evidence file storage and local disk retention manager for Edge Agent."""
import logging
import os
import shutil
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, List, Optional, Tuple, Union
import uuid

import cv2
import numpy as np

logger = logging.getLogger(__name__)


class EvidenceStore:
    """
    Manages local filesystem storage for sighting evidence files:
    - 112x112 aligned face crops
    - Full CCTV scene frames
    - Pre/post temporal video clips (mp4)
    Also enforces storage quotas and retention cleanup.
    """

    def __init__(
        self,
        base_dir: str = "evidence",
        max_storage_mb: float = 10240.0,  # 10 GB limit default
        retention_days: int = 30,
    ):
        self.base_dir = os.path.abspath(base_dir)
        self.crops_dir = os.path.join(self.base_dir, "crops")
        self.frames_dir = os.path.join(self.base_dir, "frames")
        self.clips_dir = os.path.join(self.base_dir, "clips")
        self.max_storage_mb = max_storage_mb
        self.retention_days = retention_days

        os.makedirs(self.crops_dir, exist_ok=True)
        os.makedirs(self.frames_dir, exist_ok=True)
        os.makedirs(self.clips_dir, exist_ok=True)

    def _generate_filename(self, prefix: str, ext: str) -> str:
        """Generate a collision-free timestamped filename."""
        ts = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
        uid = uuid.uuid4().hex[:8]
        return f"{prefix}_{ts}_{uid}.{ext.lstrip('.')}"

    def save_face_crop(
        self,
        image_or_bytes: Union[np.ndarray, bytes],
        filename_prefix: str = "crop",
    ) -> str:
        """
        Save a 112x112 face crop to disk as JPEG.
        Returns the absolute path to the saved file.
        """
        filename = self._generate_filename(filename_prefix, "jpg")
        filepath = os.path.join(self.crops_dir, filename)

        if isinstance(image_or_bytes, np.ndarray):
            cv2.imwrite(filepath, image_or_bytes, [int(cv2.IMWRITE_JPEG_QUALITY), 95])
        elif isinstance(image_or_bytes, (bytes, bytearray)):
            with open(filepath, "wb") as f:
                f.write(image_or_bytes)
        else:
            raise ValueError("Unsupported image type for save_face_crop")

        return filepath

    def save_full_frame(
        self,
        image_or_bytes: Union[np.ndarray, bytes],
        filename_prefix: str = "frame",
    ) -> str:
        """
        Save a full scene CCTV frame to disk as JPEG.
        Returns the absolute path to the saved file.
        """
        filename = self._generate_filename(filename_prefix, "jpg")
        filepath = os.path.join(self.frames_dir, filename)

        if isinstance(image_or_bytes, np.ndarray):
            cv2.imwrite(filepath, image_or_bytes, [int(cv2.IMWRITE_JPEG_QUALITY), 90])
        elif isinstance(image_or_bytes, (bytes, bytearray)):
            with open(filepath, "wb") as f:
                f.write(image_or_bytes)
        else:
            raise ValueError("Unsupported image type for save_full_frame")

        return filepath

    def save_video_clip(
        self,
        clip_data: Union[bytes, str],
        filename_prefix: str = "clip",
    ) -> str:
        """
        Save a video clip (mp4) to disk.
        Returns the absolute path to the saved file.
        """
        filename = self._generate_filename(filename_prefix, "mp4")
        filepath = os.path.join(self.clips_dir, filename)

        if isinstance(clip_data, (bytes, bytearray)):
            with open(filepath, "wb") as f:
                f.write(clip_data)
        elif isinstance(clip_data, str) and os.path.isfile(clip_data):
            shutil.copyfile(clip_data, filepath)
        else:
            raise ValueError("Unsupported video clip data")

        return filepath

    def get_storage_usage_mb(self) -> float:
        """Calculate total disk space consumed by the evidence directory in MB."""
        total_bytes = 0
        if not os.path.exists(self.base_dir):
            return 0.0

        for root, _, files in os.walk(self.base_dir):
            for f in files:
                fp = os.path.join(root, f)
                try:
                    total_bytes += os.path.getsize(fp)
                except OSError:
                    pass
        return total_bytes / (1024.0 * 1024.0)

    def delete_file(self, file_path: Optional[str]) -> bool:
        """Safely delete a specific evidence file."""
        if not file_path:
            return False
        try:
            if os.path.isfile(file_path):
                os.remove(file_path)
                return True
        except Exception as e:
            logger.warning(f"Failed to delete evidence file {file_path}: {e}")
        return False

    def cleanup_old_evidence(
        self,
        max_age_days: Optional[int] = None,
        max_storage_mb: Optional[float] = None,
    ) -> int:
        """
        Prune files exceeding retention window or disk quota.
        Oldest files are removed first until quota is satisfied.
        Returns the number of deleted files.
        """
        retention = max_age_days if max_age_days is not None else self.retention_days
        storage_limit = max_storage_mb if max_storage_mb is not None else self.max_storage_mb
        now = time.time()
        deleted_count = 0

        # Collect all files with metadata
        file_entries: List[Tuple[str, float, int]] = []  # (filepath, mtime, size)
        for root, _, files in os.walk(self.base_dir):
            for f in files:
                fp = os.path.join(root, f)
                try:
                    stat = os.stat(fp)
                    file_entries.append((fp, stat.st_mtime, stat.st_size))
                except OSError:
                    pass

        # 1. Retention age cleanup
        retention_seconds = retention * 86400
        remaining_files = []
        for fp, mtime, size in file_entries:
            if now - mtime > retention_seconds:
                try:
                    os.remove(fp)
                    deleted_count += 1
                except OSError:
                    remaining_files.append((fp, mtime, size))
            else:
                remaining_files.append((fp, mtime, size))

        # 2. Disk quota cleanup (oldest first)
        current_mb = sum(size for _, _, size in remaining_files) / (1024.0 * 1024.0)
        if current_mb > storage_limit:
            # Sort by mtime ascending (oldest first)
            remaining_files.sort(key=lambda x: x[1])
            for fp, _, size in remaining_files:
                if current_mb <= storage_limit:
                    break
                try:
                    os.remove(fp)
                    deleted_count += 1
                    current_mb -= size / (1024.0 * 1024.0)
                except OSError:
                    pass

        return deleted_count
