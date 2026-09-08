"""Synthetic CCTV Video Stream Simulator and Video Generator at 15 FPS.

Generates realistic CCTV video streams containing moving synthetic subjects with
detectable face structures and 5 canonical facial landmarks, annotated with CCTV HUD
telemetry (camera name, timestamps, frame counters, and recording indicators).
Can write to MP4 files or act as an in-memory frame feeder at 15 FPS.
"""
import argparse
import datetime
import logging
import os
import sys
import time
from typing import Callable, Generator, List, Optional, Tuple, Union

import cv2
import numpy as np

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)


def draw_synthetic_face(
    canvas: np.ndarray,
    center_x: int,
    center_y: int,
    radius: int = 40,
    name: str = "Subject",
) -> Tuple[np.ndarray, np.ndarray]:
    """
    Renders a synthetic facial portrait with standard geometric landmarks.
    
    Returns:
        Tuple of (bbox, landmarks) where:
            bbox: np.ndarray of [x1, y1, x2, y2]
            landmarks: np.ndarray of shape (5, 2) with [left_eye, right_eye, nose, left_mouth, right_mouth]
    """
    h, w = canvas.shape[:2]
    cx, cy = int(center_x), int(center_y)
    r = int(radius)

    # 1. Head / Skin contour
    skin_tone = (195, 215, 235)  # Warm BGR skin tone
    cv2.ellipse(canvas, (cx, cy), (r, int(r * 1.25)), 0, 0, 360, skin_tone, -1)

    # 2. Hair cap
    hair_color = (30, 35, 45)
    cv2.ellipse(canvas, (cx, cy - int(r * 0.45)), (int(r * 1.05), int(r * 0.7)), 0, 180, 360, hair_color, -1)

    # 3. Eyes
    eye_offset_x = int(r * 0.38)
    eye_offset_y = int(r * 0.18)
    left_eye = (cx - eye_offset_x, cy - eye_offset_y)
    right_eye = (cx + eye_offset_x, cy - eye_offset_y)
    eye_radius = max(3, int(r * 0.12))

    # Eye sclera
    cv2.circle(canvas, left_eye, eye_radius, (250, 250, 250), -1)
    cv2.circle(canvas, right_eye, eye_radius, (250, 250, 250), -1)
    # Pupil
    cv2.circle(canvas, left_eye, max(1, eye_radius // 2), (40, 25, 20), -1)
    cv2.circle(canvas, right_eye, max(1, eye_radius // 2), (40, 25, 20), -1)

    # Eyebrows
    brow_offset_y = eye_offset_y + int(r * 0.16)
    cv2.line(
        canvas,
        (left_eye[0] - eye_radius, cy - brow_offset_y),
        (left_eye[0] + eye_radius + 2, cy - brow_offset_y),
        hair_color,
        2,
    )
    cv2.line(
        canvas,
        (right_eye[0] - eye_radius - 2, cy - brow_offset_y),
        (right_eye[0] + eye_radius, cy - brow_offset_y),
        hair_color,
        2,
    )

    # 4. Nose tip
    nose_y = cy + int(r * 0.15)
    nose = (cx, nose_y)
    cv2.circle(canvas, nose, max(2, int(r * 0.08)), (160, 180, 205), -1)

    # 5. Mouth
    mouth_y = cy + int(r * 0.55)
    mouth_w = int(r * 0.32)
    left_mouth = (cx - mouth_w, mouth_y)
    right_mouth = (cx + mouth_w, mouth_y)
    cv2.line(canvas, left_mouth, right_mouth, (80, 80, 160), 2)
    cv2.ellipse(canvas, (cx, mouth_y + 2), (mouth_w // 2, 4), 0, 0, 180, (70, 70, 150), -1)

    # Construct standard bbox and 5 canonical landmarks
    x1 = max(0, cx - r)
    y1 = max(0, cy - int(r * 1.25))
    x2 = min(w - 1, cx + r)
    y2 = min(h - 1, cy + int(r * 1.25))
    bbox = np.array([x1, y1, x2, y2], dtype=np.float32)

    landmarks = np.array(
        [
            [float(left_eye[0]), float(left_eye[1])],
            [float(right_eye[0]), float(right_eye[1])],
            [float(nose[0]), float(nose[1])],
            [float(left_mouth[0]), float(left_mouth[1])],
            [float(right_mouth[0]), float(right_mouth[1])],
        ],
        dtype=np.float32,
    )

    return bbox, landmarks


def draw_cctv_hud(
    canvas: np.ndarray,
    camera_name: str,
    frame_idx: int,
    fps: float,
    timestamp: float,
) -> None:
    """Draws realistic CCTV telemetry overlay (HUD)."""
    h, w = canvas.shape[:2]
    dt_str = datetime.datetime.fromtimestamp(timestamp).strftime("%Y-%m-%d  %H:%M:%S.%f")[:-3]

    # Top header bar (semi-transparent)
    header_overlay = canvas[:36, :].copy()
    cv2.rectangle(header_overlay, (0, 0), (w, 36), (15, 15, 15), -1)
    cv2.addWeighted(header_overlay, 0.7, canvas[:36, :], 0.3, 0, canvas[:36, :])

    # Blinking REC dot
    blink = int(timestamp * 2) % 2 == 0
    if blink:
        cv2.circle(canvas, (20, 18), 6, (0, 0, 220), -1)
        cv2.putText(canvas, "REC", (32, 23), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 0, 255), 1)

    # Camera label & timestamp
    cv2.putText(canvas, camera_name, (80, 23), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (230, 230, 230), 1)
    cv2.putText(canvas, dt_str, (w - 230, 23), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (230, 230, 230), 1)

    # Bottom telemetry (Frame index and FPS)
    telemetry_str = f"FRAME: {frame_idx:05d} | {fps:.1f} FPS | CODEC: H.264/MP4V"
    cv2.putText(canvas, telemetry_str, (16, h - 14), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (180, 180, 180), 1)


def generate_synthetic_video(
    output_path: str,
    num_frames: int = 150,
    fps: float = 15.0,
    width: int = 640,
    height: int = 480,
    camera_name: str = "CAM-01 [ENTRANCE]",
    subject_name: str = "Aarav Sharma",
) -> str:
    """
    Generates a synthetic CCTV video clip at 15 FPS and writes to MP4 file.
    
    Args:
        output_path: Target .mp4 file path.
        num_frames: Total frames to generate (150 = 10s at 15 FPS).
        fps: Target frame rate (default 15.0).
        width: Video width.
        height: Video height.
        camera_name: CCTV camera HUD title.
        subject_name: Name of synthetic subject.
        
    Returns:
        Absolute path to generated video file.
    """
    abs_output = os.path.abspath(output_path)
    os.makedirs(os.path.dirname(abs_output) or ".", exist_ok=True)

    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
    writer = cv2.VideoWriter(abs_output, fourcc, float(fps), (width, height))
    if not writer.isOpened():
        raise RuntimeError(f"OpenCV failed to initialize VideoWriter for '{abs_output}'")

    start_time = time.time()
    dt_step = 1.0 / fps

    try:
        for idx in range(num_frames):
            frame = np.full((height, width, 3), fill_value=50, dtype=np.uint8)

            # Draw background architectural perspective (corridor/floor lines)
            cv2.line(frame, (0, height), (width // 3, height // 2), (75, 75, 75), 2)
            cv2.line(frame, (width, height), (2 * width // 3, height // 2), (75, 75, 75), 2)
            cv2.line(frame, (0, height // 2), (width, height // 2), (65, 65, 65), 1)

            # Animate subject walking across screen
            progress = idx / max(1, num_frames - 1)
            cx = int(width * 0.2 + progress * (width * 0.6))
            cy = int(height * 0.45 + np.sin(progress * np.pi * 4) * 8)
            radius = int(35 + np.sin(progress * np.pi) * 8)

            # Render face and body
            # Torso
            cv2.ellipse(frame, (cx, cy + int(radius * 2.2)), (int(radius * 1.5), int(radius * 2.0)), 0, 0, 360, (70, 80, 110), -1)
            draw_synthetic_face(frame, cx, cy, radius=radius, name=subject_name)

            # Telemetry HUD
            current_timestamp = start_time + (idx * dt_step)
            draw_cctv_hud(frame, camera_name, idx, fps, current_timestamp)

            writer.write(frame)

        logger.info(f"Generated {num_frames} frames ({num_frames/fps:.1f}s at {fps} FPS) -> {abs_output}")
    finally:
        writer.release()

    return abs_output


class SyntheticVideoStreamer:
    """
    Feeds synthetic frames at 15 FPS, supporting both procedural synthesis and looping from video files.
    """

    def __init__(
        self,
        camera_id: str = "CAM-01",
        camera_name: str = "CAM-01 [NORTH GATE]",
        fps: float = 15.0,
        width: int = 640,
        height: int = 480,
        video_source: Optional[str] = None,
        subject_name: str = "Aarav Sharma",
    ):
        self.camera_id = camera_id
        self.camera_name = camera_name
        self.fps = fps
        self.width = width
        self.height = height
        self.video_source = video_source
        self.subject_name = subject_name

        self._frame_interval = 1.0 / fps
        self._frame_count = 0
        self._cap: Optional[cv2.VideoCapture] = None
        self._simulated_time_cursor = time.time()

        if self.video_source and os.path.isfile(self.video_source):
            self._cap = cv2.VideoCapture(self.video_source)

    def get_next_frame(
        self,
        simulated_time: Optional[float] = None,
    ) -> Tuple[bool, np.ndarray, float, Optional[np.ndarray], Optional[np.ndarray]]:
        """
        Retrieves the next frame at 15 FPS.
        
        Returns:
            Tuple of (success, frame, timestamp, bbox, landmarks)
        """
        if simulated_time is not None:
            ts = simulated_time
        else:
            ts = self._simulated_time_cursor
            self._simulated_time_cursor += self._frame_interval

        self._frame_count += 1

        # File-backed playback
        if self._cap is not None and self._cap.isOpened():
            ret, frame = self._cap.read()
            if not ret or frame is None:
                # Loop back to beginning
                self._cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
                ret, frame = self._cap.read()

            if ret and frame is not None:
                if frame.shape[1] != self.width or frame.shape[0] != self.height:
                    frame = cv2.resize(frame, (self.width, self.height))
                draw_cctv_hud(frame, self.camera_name, self._frame_count, self.fps, ts)
                return True, frame, ts, None, None

        # Procedural synthesis
        frame = np.full((self.height, self.width, 3), fill_value=45, dtype=np.uint8)

        # Floor grid perspective
        cv2.line(frame, (0, self.height), (self.width // 3, self.height // 2), (70, 70, 70), 2)
        cv2.line(frame, (self.width, self.height), (2 * self.width // 3, self.height // 2), (70, 70, 70), 2)
        cv2.line(frame, (0, self.height // 2), (self.width, self.height // 2), (60, 60, 60), 1)

        # Moving trajectory
        cycle = (self._frame_count % 150) / 150.0
        cx = int(self.width * 0.2 + cycle * (self.width * 0.6))
        cy = int(self.height * 0.45 + np.sin(cycle * np.pi * 4) * 8)
        radius = int(35 + np.sin(cycle * np.pi) * 6)

        # Draw torso
        cv2.ellipse(frame, (cx, cy + int(radius * 2.2)), (int(radius * 1.5), int(radius * 2.0)), 0, 0, 360, (65, 75, 100), -1)
        bbox, landmarks = draw_synthetic_face(frame, cx, cy, radius=radius, name=self.subject_name)

        # Overlay HUD
        draw_cctv_hud(frame, self.camera_name, self._frame_count, self.fps, ts)

        return True, frame, ts, bbox, landmarks

    def frame_generator(
        self,
        max_frames: Optional[int] = None,
        real_time: bool = False,
    ) -> Generator[Tuple[np.ndarray, float], None, None]:
        """
        Yields frames sequentially. If real_time is True, throttles yields to 15 FPS.
        """
        frames_yielded = 0
        while max_frames is None or frames_yielded < max_frames:
            t0 = time.time()
            ok, frame, ts, _, _ = self.get_next_frame()
            if not ok:
                break
            yield frame, ts
            frames_yielded += 1

            if real_time:
                elapsed = time.time() - t0
                delay = max(0.0, self._frame_interval - elapsed)
                if delay > 0:
                    time.sleep(delay)

    def close(self) -> None:
        if self._cap is not None:
            self._cap.release()
            self._cap = None


def main() -> None:
    parser = argparse.ArgumentParser(description="Synthetic CCTV Stream Simulator (15 FPS)")
    parser.add_argument("--output", type=str, default="test_cctv_feed.mp4", help="Output MP4 file path")
    parser.add_argument("--fps", type=float, default=15.0, help="Frames per second (default: 15.0)")
    parser.add_argument("--duration", type=float, default=10.0, help="Duration in seconds (default: 10.0)")
    parser.add_argument("--width", type=int, default=640, help="Frame width (default: 640)")
    parser.add_argument("--height", type=int, default=480, help="Frame height (default: 480)")
    parser.add_argument("--camera", type=str, default="CAM-01 [ENTRANCE]", help="Camera name in HUD")
    parser.add_argument("--subject", type=str, default="Aarav Sharma", help="Subject name")
    parser.add_argument("--preview", action="store_true", help="Preview stream in GUI window")

    args = parser.parse_args()
    num_frames = int(args.fps * args.duration)

    if args.preview:
        logger.info(f"Starting real-time 15 FPS preview ({num_frames} frames)... Press 'q' to quit.")
        streamer = SyntheticVideoStreamer(
            camera_name=args.camera,
            fps=args.fps,
            width=args.width,
            height=args.height,
            subject_name=args.subject,
        )
        try:
            for frame, _ in streamer.frame_generator(max_frames=num_frames, real_time=True):
                cv2.imshow("Synthetic CCTV Feed (15 FPS)", frame)
                if cv2.waitKey(1) & 0xFF == ord("q"):
                    break
        finally:
            cv2.destroyAllWindows()
            streamer.close()
    else:
        generate_synthetic_video(
            output_path=args.output,
            num_frames=num_frames,
            fps=args.fps,
            width=args.width,
            height=args.height,
            camera_name=args.camera,
            subject_name=args.subject,
        )


if __name__ == "__main__":
    main()
