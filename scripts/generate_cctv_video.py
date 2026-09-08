"""Generates a realistic 15 FPS CCTV video feed containing real human subjects."""
import argparse
import datetime
import glob
import logging
import os
from pathlib import Path
from typing import Dict, List, Tuple

import cv2
import numpy as np

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("generate_cctv_video")


def create_cctv_background(width: int = 640, height: int = 480) -> np.ndarray:
    """Generates a realistic indoor hallway / corridor security camera background."""
    bg = np.zeros((height, width, 3), dtype=np.uint8)

    # Floor (lower 40%) - tiled perspective
    floor_y = int(height * 0.58)
    bg[floor_y:, :] = (55, 60, 65)  # Dark slate floor
    # Grid lines on floor
    for x in range(0, width, 50):
        cv2.line(bg, (x, floor_y), (int(width/2 + (x - width/2) * 2.2), height), (40, 45, 50), 1)
    for y in range(floor_y, height, 30):
        cv2.line(bg, (0, y), (width, y), (45, 50, 55), 1)

    # Walls
    cv2.rectangle(bg, (0, 0), (int(width * 0.22), floor_y), (85, 90, 95), -1)  # Left wall
    cv2.rectangle(bg, (int(width * 0.78), 0), (width, floor_y), (80, 85, 90), -1)  # Right wall
    cv2.rectangle(bg, (int(width * 0.22), 0), (int(width * 0.78), floor_y), (110, 115, 120), -1)  # Back wall

    # Ceiling lights
    cv2.rectangle(bg, (int(width * 0.35), 10), (int(width * 0.65), 35), (200, 205, 210), -1)
    cv2.ellipse(bg, (int(width * 0.5), 35), (int(width * 0.3), 30), 0, 0, 180, (140, 145, 150), -1)

    # Doorway in back wall
    door_w = int(width * 0.18)
    door_x = int(width * 0.5 - door_w / 2)
    door_y = int(floor_y * 0.35)
    cv2.rectangle(bg, (door_x, door_y), (door_x + door_w, floor_y), (40, 45, 50), -1)
    cv2.rectangle(bg, (door_x, door_y), (door_x + door_w, floor_y), (160, 165, 170), 2)

    return bg


def overlay_subject(
    canvas: np.ndarray,
    portrait_img: np.ndarray,
    center_x: int,
    center_y: int,
    scale: float = 0.5,
    alpha: float = 1.0,
) -> None:
    """Blends a real portrait subject realistically onto the CCTV frame."""
    h, w = canvas.shape[:2]
    ph, pw = portrait_img.shape[:2]

    target_w = int(pw * scale)
    target_h = int(ph * scale)
    if target_w <= 10 or target_h <= 10:
        return

    resized = cv2.resize(portrait_img, (target_w, target_h), interpolation=cv2.INTER_AREA)

    # Circular/oval mask for head and upper body
    mask = np.zeros((target_h, target_w), dtype=np.float32)
    mcx, mcy = target_w // 2, int(target_h * 0.45)
    rx = int(target_w * 0.42)
    ry = int(target_h * 0.48)
    cv2.ellipse(mask, (mcx, mcy), (rx, ry), 0, 0, 360, 1.0, -1)
    # Torso extension
    cv2.ellipse(mask, (mcx, int(target_h * 0.95)), (int(rx * 1.3), int(ry * 0.6)), 0, 0, 360, 1.0, -1)
    # Gaussian blur edges of mask for seamless blending
    mask = cv2.GaussianBlur(mask, (15, 15), 5)
    mask = mask * alpha

    x1 = int(center_x - target_w / 2)
    y1 = int(center_y - target_h / 2)
    x2 = x1 + target_w
    y2 = y1 + target_h

    # Clip to bounds
    src_x1 = max(0, -x1)
    src_y1 = max(0, -y1)
    src_x2 = target_w - max(0, x2 - w)
    src_y2 = target_h - max(0, y2 - h)

    dst_x1 = max(0, x1)
    dst_y1 = max(0, y1)
    dst_x2 = min(w, x2)
    dst_y2 = min(h, y2)

    if dst_x2 <= dst_x1 or dst_y2 <= dst_y1:
        return

    sub_mask = mask[src_y1:src_y2, src_x1:src_x2, np.newaxis]
    sub_resized = resized[src_y1:src_y2, src_x1:src_x2]
    roi = canvas[dst_y1:dst_y2, dst_x1:dst_x2]

    # Alpha blend
    blended = (roi * (1.0 - sub_mask) + sub_resized * sub_mask).astype(np.uint8)
    canvas[dst_y1:dst_y2, dst_x1:dst_x2] = blended


def render_cctv_hud(canvas: np.ndarray, frame_idx: int, fps: float = 15.0) -> None:
    """Draws realistic CCTV HUD telemetry overlay."""
    h, w = canvas.shape[:2]
    now = datetime.datetime.now()
    time_str = now.strftime("%Y-%m-%d  %H:%M:%S.") + f"{int((frame_idx % fps) * (1000 / fps)):03d}"

    # Top HUD bar
    cv2.putText(canvas, "CAM-01 [MAIN ENTRANCE]", (20, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (230, 230, 230), 2)
    cv2.putText(canvas, time_str, (w - 270, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (230, 230, 230), 2)

    # REC blinking indicator
    if (frame_idx // 8) % 2 == 0:
        cv2.circle(canvas, (w - 300, 25), 6, (0, 0, 240), -1)
        cv2.putText(canvas, "REC", (w - 340, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 240), 2)

    # Bottom HUD bar
    cv2.putText(canvas, f"FPS: {fps:.1f} | 640x480 | H.264", (20, h - 15), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (160, 160, 160), 1)
    cv2.putText(canvas, f"FRM: {frame_idx:06d}", (w - 120, h - 15), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (160, 160, 160), 1)


def generate_cctv_video(
    output_path: str = "test_cctv_feed.mp4",
    num_frames: int = 180,
    fps: float = 15.0,
    width: int = 640,
    height: int = 480,
) -> None:
    """Generates the CCTV video containing real portrait subjects."""
    artifact_dir = r"C:\Users\mksin\.gemini\antigravity-ide\brain\c4598e5a-62cd-4fea-964a-5c319a94991a"
    aarav_path = glob.glob(os.path.join(artifact_dir, "*aarav_sharma*.jpg"))
    priya_path = glob.glob(os.path.join(artifact_dir, "*priya_patel*.jpg"))

    aarav_img = cv2.imread(aarav_path[0]) if aarav_path else None
    priya_img = cv2.imread(priya_path[0]) if priya_path else None

    if aarav_img is None:
        raise FileNotFoundError("Could not find Aarav Sharma portrait photo.")

    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
    out = cv2.VideoWriter(output_path, fourcc, fps, (width, height))
    bg_base = create_cctv_background(width, height)

    logger.info(f"Generating realistic CCTV feed: {output_path} ({num_frames} frames @ {fps} FPS)...")

    # Animation trajectories:
    # Phase 1 (Frames 0..90): Aarav Sharma walks from distance (doorway) to front center, looks around, pauses
    # Phase 2 (Frames 90..180): Priya Patel walks from left to right across the corridor

    for i in range(num_frames):
        frame = bg_base.copy()

        if i < 100:
            # Aarav Sharma: walks from back door (cx=320, cy=240, scale=0.25) to foreground (cx=320, cy=290, scale=0.65)
            progress = min(1.0, i / 50.0)
            cur_scale = 0.28 + (0.62 - 0.28) * progress
            cur_cx = int(320 + np.sin(i * 0.1) * 8)
            cur_cy = int(220 + (290 - 220) * progress)

            overlay_subject(frame, aarav_img, cur_cx, cur_cy, scale=cur_scale)

        if i >= 80 and priya_img is not None:
            # Priya Patel: walks across from right to left
            p_prog = (i - 80) / 95.0
            p_scale = 0.55
            p_cx = int(width + 80 - p_prog * (width + 160))
            p_cy = int(285 + np.sin(i * 0.15) * 5)

            overlay_subject(frame, priya_img, p_cx, p_cy, scale=p_scale)

        # Add subtle sensor noise and realistic CCTV HUD
        noise = np.random.normal(0, 2, frame.shape).astype(np.int16)
        noisy_frame = np.clip(frame.astype(np.int16) + noise, 0, 255).astype(np.uint8)

        render_cctv_hud(noisy_frame, i, fps)
        out.write(noisy_frame)

    out.release()
    logger.info(f"Successfully generated {output_path} ({num_frames} frames, {os.path.getsize(output_path)} bytes)")


if __name__ == "__main__":
    generate_cctv_video("test_cctv_feed.mp4", num_frames=180, fps=15.0)
