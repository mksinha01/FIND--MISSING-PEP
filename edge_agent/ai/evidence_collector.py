"""Evidence collection engine: face crop, telemetry-annotated full frame, and 15 FPS video clips."""
from dataclasses import dataclass, field
from datetime import datetime, timezone
import logging
import os
import time
from typing import Any, Dict, Optional, Tuple, Union
import uuid

import cv2
import numpy as np

from edge_agent.camera.circular_buffer import CircularFrameBuffer
from edge_agent.storage.evidence_store import EvidenceStore

logger = logging.getLogger(__name__)


@dataclass
class Evidence:
    """
    Encapsulates all evidentiary assets and metadata generated from a confirmed match.
    """
    crop_path: str
    frame_path: str
    clip_path: Optional[str]
    person_id: str
    track_id: int
    similarity: float
    timestamp: float
    camera_id: str
    metadata: Dict[str, Any] = field(default_factory=dict)


class EvidenceCollector:
    """
    Captures, annotates, and persists multi-modal biometric sighting evidence:
    1. Aligned 112x112 facial crop.
    2. Full CCTV scene frame with bounding box and HUD telemetry overlay.
    3. 15 FPS MP4 temporal video clip extracted from CircularFrameBuffer.
    4. Structured metadata for offline synchronization to backend.
    """

    def __init__(
        self,
        base_dir: str = "evidence",
        evidence_store: Optional[EvidenceStore] = None,
        camera_id: str = "",
        annotated_frames: bool = True,
    ):
        self.base_dir = os.path.abspath(base_dir)
        self.crops_dir = os.path.join(self.base_dir, "crops")
        self.frames_dir = os.path.join(self.base_dir, "frames")
        self.clips_dir = os.path.join(self.base_dir, "clips")
        self.camera_id = camera_id
        self.annotated_frames = annotated_frames

        os.makedirs(self.crops_dir, exist_ok=True)
        os.makedirs(self.frames_dir, exist_ok=True)
        os.makedirs(self.clips_dir, exist_ok=True)

        self.evidence_store = evidence_store or EvidenceStore(base_dir=self.base_dir)

    def _generate_filename(self, prefix: str, ext: str, person_id: str = "") -> str:
        """Generates collision-free timestamped filename."""
        ts = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
        uid = uuid.uuid4().hex[:6]
        pid_clean = "".join(c for c in person_id if c.isalnum() or c in "-_")[:16]
        if pid_clean:
            return f"{prefix}_{pid_clean}_{ts}_{uid}.{ext.lstrip('.')}"
        return f"{prefix}_{ts}_{uid}.{ext.lstrip('.')}"

    def annotate_frame(
        self,
        frame: np.ndarray,
        bbox: Optional[np.ndarray],
        person_id: str,
        similarity: float,
        track_id: int,
        timestamp: float,
    ) -> np.ndarray:
        """
        Renders HUD bounding box and evidentiary telemetry banner onto full frame.
        """
        canvas = frame.copy()
        h, w = canvas.shape[:2]

        # Draw bounding box
        if bbox is not None and len(bbox) >= 4:
            x1 = max(0, int(round(float(bbox[0]))))
            y1 = max(0, int(round(float(bbox[1]))))
            x2 = min(w, int(round(float(bbox[2]))))
            y2 = min(h, int(round(float(bbox[3]))))

            # Red/Green target bounding box
            color = (0, 215, 255) if similarity >= 0.60 else (0, 165, 255)  # Gold or Amber
            cv2.rectangle(canvas, (x1, y1), (x2, y2), color, 2)

            # Label banner
            label = f"{person_id} | Sim: {similarity:.2f} | Track: #{track_id}"
            (lw, lh), baseline = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1)
            label_y1 = max(0, y1 - lh - baseline - 4)
            label_y2 = y1
            cv2.rectangle(canvas, (x1, label_y1), (x1 + lw + 8, label_y2), (0, 0, 0), -1)
            cv2.putText(
                canvas,
                label,
                (x1 + 4, label_y2 - baseline),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.5,
                (255, 255, 255),
                1,
                cv2.LINE_AA,
            )

        # Header telemetry banner
        utc_str = datetime.fromtimestamp(timestamp, tz=timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
        header = f"MATCH DETECTED | CAM: {self.camera_id or 'LOCAL'} | TIME: {utc_str}"
        cv2.rectangle(canvas, (0, 0), (w, 24), (20, 20, 20), -1)
        cv2.putText(
            canvas,
            header,
            (10, 17),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            (0, 255, 255),
            1,
            cv2.LINE_AA,
        )

        return canvas

    def capture(
        self,
        frame: np.ndarray,
        track: Any,
        person_id: str,
        similarity: float,
        timestamp: float,
        circ_buffer: Optional[CircularFrameBuffer] = None,
        aligned_face: Optional[np.ndarray] = None,
        camera_id: Optional[str] = None,
        video_clip: Optional[Union[str, bytes]] = None,
        extra_metadata: Optional[Dict[str, Any]] = None,
    ) -> Evidence:
        """
        Gathers and saves all evidence artifacts.

        Args:
            frame: Full video frame at moment of match.
            track: ExtendedTrack or TrackState instance.
            person_id: Missing person ID.
            similarity: Average or confirmed similarity score.
            timestamp: Event timestamp.
            circ_buffer: Optional CircularFrameBuffer to extract 15 FPS MP4 clip.
            aligned_face: Optional pre-aligned 112x112 face crop.
            camera_id: Optional camera UUID override.
            video_clip: Optional pre-saved video clip path or raw bytes.
            extra_metadata: Additional telemetry key-values.

        Returns:
            Evidence object containing paths to saved files and match metadata.
        """
        cam_id = camera_id or self.camera_id or "LOCAL_CAM"
        track_id = int(getattr(track, "track_id", 0))
        bbox = getattr(track, "bbox", None)

        # 1. Save 112x112 face crop
        crop_filename = self._generate_filename("crop", "jpg", person_id=person_id)
        crop_path = os.path.join(self.crops_dir, crop_filename)

        if aligned_face is not None and aligned_face.size > 0:
            face_img = aligned_face
        elif bbox is not None and len(bbox) >= 4:
            h, w = frame.shape[:2]
            x1 = max(0, int(round(float(bbox[0]))))
            y1 = max(0, int(round(float(bbox[1]))))
            x2 = min(w, int(round(float(bbox[2]))))
            y2 = min(h, int(round(float(bbox[3]))))
            if (x2 - x1) > 0 and (y2 - y1) > 0:
                face_img = cv2.resize(frame[y1:y2, x1:x2], (112, 112))
            else:
                face_img = cv2.resize(frame, (112, 112))
        else:
            face_img = cv2.resize(frame, (112, 112))

        cv2.imwrite(crop_path, face_img, [int(cv2.IMWRITE_JPEG_QUALITY), 95])

        # 2. Save full scene frame with HUD annotations
        frame_filename = self._generate_filename("frame", "jpg", person_id=person_id)
        frame_path = os.path.join(self.frames_dir, frame_filename)

        if self.annotated_frames:
            annotated = self.annotate_frame(
                frame=frame,
                bbox=bbox,
                person_id=person_id,
                similarity=similarity,
                track_id=track_id,
                timestamp=timestamp,
            )
            cv2.imwrite(frame_path, annotated, [int(cv2.IMWRITE_JPEG_QUALITY), 90])
        else:
            cv2.imwrite(frame_path, frame, [int(cv2.IMWRITE_JPEG_QUALITY), 90])

        # 3. Dump 15 FPS video clip from CircularFrameBuffer
        clip_path: Optional[str] = None
        if circ_buffer is not None:
            clip_filename = self._generate_filename("clip", "mp4", person_id=person_id)
            target_clip_path = os.path.join(self.clips_dir, clip_filename)
            success = circ_buffer.dump_video(target_clip_path, fps=15.0)
            if success and os.path.isfile(target_clip_path) and os.path.getsize(target_clip_path) > 0:
                clip_path = target_clip_path
                logger.info(f"Dumped 15 FPS evidence video clip to: {clip_path}")
            else:
                logger.warning(f"CircularFrameBuffer failed to dump video to: {target_clip_path}")
        elif isinstance(video_clip, str) and os.path.isfile(video_clip):
            clip_path = os.path.abspath(video_clip)
        elif isinstance(video_clip, (bytes, bytearray)):
            clip_path = self.evidence_store.save_video_clip(video_clip, filename_prefix="clip")

        metadata: Dict[str, Any] = {
            "person_id": person_id,
            "track_id": track_id,
            "similarity": float(similarity),
            "timestamp": float(timestamp),
            "camera_id": cam_id,
            "crop_path": crop_path,
            "frame_path": frame_path,
            "clip_path": clip_path,
        }
        if bbox is not None:
            metadata["bbox"] = [float(b) for b in bbox]
        if extra_metadata:
            metadata.update(extra_metadata)

        evidence = Evidence(
            crop_path=crop_path,
            frame_path=frame_path,
            clip_path=clip_path,
            person_id=person_id,
            track_id=track_id,
            similarity=float(similarity),
            timestamp=float(timestamp),
            camera_id=cam_id,
            metadata=metadata,
        )

        logger.info(
            f"Evidence captured for {person_id} (sim={similarity:.2f}): "
            f"crop={crop_path}, frame={frame_path}, clip={clip_path}"
        )
        return evidence
