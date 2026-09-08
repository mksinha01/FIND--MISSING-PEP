"""File storage utilities for secure asynchronous media handling."""
import os
import uuid
from pathlib import Path
from typing import Optional
import aiofiles
from fastapi import UploadFile

from app.config import settings

# Standard media subdirectories
STANDARD_SUBDIRECTORIES = ("photos", "faces", "evidence", "clips")


def ensure_upload_dirs(upload_dir: Optional[str] = None) -> None:
    """Ensure all required upload subdirectories exist on disk."""
    base_dir = Path(upload_dir or settings.UPLOAD_DIR)
    base_dir.mkdir(parents=True, exist_ok=True)
    for subfolder in STANDARD_SUBDIRECTORIES:
        (base_dir / subfolder).mkdir(parents=True, exist_ok=True)


def get_absolute_path(relative_url: str, upload_dir: Optional[str] = None) -> Path:
    """
    Resolves a relative URL (e.g. /uploads/photos/xyz.jpg) to an absolute filesystem path.
    Guards against path traversal attacks.
    """
    base_dir = Path(upload_dir or settings.UPLOAD_DIR).resolve()
    clean_path = relative_url.lstrip("/")
    if clean_path.startswith("uploads/"):
        clean_path = clean_path[len("uploads/"):]
    target_path = (base_dir / clean_path).resolve()

    # Prevent path traversal outside upload_dir
    try:
        target_path.relative_to(base_dir)
    except ValueError as e:
        raise ValueError(f"Path traversal detected: {relative_url}") from e

    return target_path


async def save_upload_file(
    file: UploadFile,
    subfolder: str,
    upload_dir: Optional[str] = None,
) -> str:
    """
    Saves an uploaded file to the specified subfolder securely with a UUID filename.
    Returns the relative URL path (e.g. /uploads/photos/uuid.jpg).
    """
    base_dir = Path(upload_dir or settings.UPLOAD_DIR)
    target_dir = base_dir / subfolder
    target_dir.mkdir(parents=True, exist_ok=True)

    ext = ""
    if file.filename and "." in file.filename:
        ext = f".{file.filename.split('.')[-1].lower()}"

    file_name = f"{uuid.uuid4().hex}{ext}"
    file_path = target_dir / file_name

    async with aiofiles.open(file_path, "wb") as out_file:
        content = await file.read()
        await out_file.write(content)

    return f"/uploads/{subfolder}/{file_name}"


async def save_bytes(
    data: bytes,
    subfolder: str,
    filename_prefix: str = "",
    extension: str = ".jpg",
    upload_dir: Optional[str] = None,
) -> str:
    """
    Saves raw bytes asynchronously to the specified subfolder with a UUID filename.
    Returns the relative URL path (e.g. /uploads/faces/uuid.jpg).
    """
    base_dir = Path(upload_dir or settings.UPLOAD_DIR)
    target_dir = base_dir / subfolder
    target_dir.mkdir(parents=True, exist_ok=True)

    if not extension.startswith("."):
        extension = f".{extension}"

    prefix = f"{filename_prefix}_" if filename_prefix else ""
    file_name = f"{prefix}{uuid.uuid4().hex}{extension}"
    file_path = target_dir / file_name

    async with aiofiles.open(file_path, "wb") as out_file:
        await out_file.write(data)

    return f"/uploads/{subfolder}/{file_name}"


async def delete_file(relative_url: str, upload_dir: Optional[str] = None) -> bool:
    """
    Safely deletes a file given its relative URL.
    Returns True if deleted, False if file did not exist.
    """
    try:
        abs_path = get_absolute_path(relative_url, upload_dir)
        if abs_path.exists() and abs_path.is_file():
            abs_path.unlink()
            return True
        return False
    except Exception:
        return False
