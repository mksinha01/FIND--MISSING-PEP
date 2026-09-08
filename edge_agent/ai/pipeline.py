"""Per-camera Edge AI pipeline orchestrating detection, tracking, recognition, and verification."""
from dataclasses import dataclass, field
import logging
import threading
import time
from typing import Any, Callable, Dict, List, Optional, Tuple, Union

import numpy as np

from edge_agent.ai.face_aligner import FaceAligner
from edge_agent.ai.face_detector import FaceDetector, SCRFDFaceDetector
from edge_agent.ai.face_quality import FaceQualityChecker, FaceQualityGate
from edge_agent.ai.face_recognizer import ArcFaceRecognizer
from edge_agent.ai.temporal_verifier import MatchEvent, TemporalVerifier
from edge_agent.ai.tracker import ByteTracker, ExtendedTrack
from edge_agent.ai.vector_search import VectorSearchEngine
from edge_agent.camera.circular_buffer import CircularFrameBuffer
from edge_agent.ai.evidence_collector import Evidence, EvidenceCollector

logger = logging.getLogger(__name__)


@dataclass
class PipelineTelemetry:
    """Telemetry counters for performance and health monitoring."""
    frames_processed: int = 0
    faces_detected: int = 0
    recognitions_attempted: int = 0
    recognitions_throttled: int = 0
    recognitions_quality_failed: int = 0
    matches_confirmed: int = 0
    last_frame_timestamp: float = 0.0


class EdgeAIPipeline:
    """
    Per-camera processing pipeline executing decoupled multi-object tracking and biometric recognition.

    Critical Architectural Guarantees:
    1. 10-15 FPS fast detection and ByteTrack tracking preserving 5 facial landmarks.
    2. Throttled ArcFace recognition to once per 1.0 second per Track ID.
    3. Kalman-predicted tracks without fresh landmarks cleanly skip recognition.
    4. Quality gate filters motion blur, extreme pose angles, and miniature face crops.
    5. Per-frame candidate deduplication: collapses multi-photo candidate hits to max(similarity).
    6. Multi-observation temporal verification: requires N=3 scores >= 0.60 within 5 seconds.
    7. Automated 15 FPS MP4 video clip dump from CircularFrameBuffer upon match confirmation.
    """

    def __init__(
        self,
        camera_id: str,
        detector: Optional[FaceDetector] = None,
        tracker: Optional[ByteTracker] = None,
        quality_checker: Optional[FaceQualityGate] = None,
        aligner: Optional[FaceAligner] = None,
        recognizer: Optional[ArcFaceRecognizer] = None,
        vector_search: Optional[VectorSearchEngine] = None,
        verifier: Optional[TemporalVerifier] = None,
        evidence_collector: Optional[EvidenceCollector] = None,
        circ_buffer: Optional[CircularFrameBuffer] = None,
        recognition_interval: float = 1.0,
        candidate_cutoff: float = 0.50,
        top_k: int = 5,
        on_match: Optional[Callable[[MatchEvent, Evidence], None]] = None,
        on_frame_processed: Optional[Callable[[np.ndarray, List[Any]], None]] = None,
    ):
        self.camera_id = camera_id
        self.detector = detector or SCRFDFaceDetector()
        self.tracker = tracker or ByteTracker(frame_rate=15)
        self.quality_checker = quality_checker or FaceQualityGate()
        self.aligner = aligner or FaceAligner()
        self.recognizer = recognizer or ArcFaceRecognizer()
        self.vector_search = vector_search or VectorSearchEngine()
        self.verifier = verifier or TemporalVerifier(window_size=3, threshold=0.60, max_time_span=5.0)
        self.evidence_collector = evidence_collector or EvidenceCollector(camera_id=camera_id)
        self.circ_buffer = circ_buffer or CircularFrameBuffer(maxlen=150)  # 10s at 15 FPS

        self.recognition_interval = recognition_interval
        self.candidate_cutoff = candidate_cutoff
        self.top_k = top_k
        self.on_match_callback = on_match
        self.on_frame_processed_callback = on_frame_processed

        self.telemetry = PipelineTelemetry()
        self._stop_event = threading.Event()
        self._lock = threading.Lock()

    def stop(self) -> None:
        """Signals cooperative pipeline shutdown."""
        self._stop_event.set()

    def reset(self) -> None:
        """Resets tracker, verifier, and telemetry state."""
        with self._lock:
            self._stop_event.clear()
            if hasattr(self.tracker, "reset"):
                self.tracker.reset()
            if hasattr(self.verifier, "reset"):
                self.verifier.reset()
            if hasattr(self.circ_buffer, "clear"):
                self.circ_buffer.clear()
            self.telemetry = PipelineTelemetry()

    @property
    def is_running(self) -> bool:
        """Returns True if pipeline has not received stop signal."""
        return not self._stop_event.is_set()

    def process_frame(
        self,
        frame: np.ndarray,
        timestamp: Optional[float] = None,
    ) -> List[Tuple[MatchEvent, Evidence]]:
        """
        Executes complete edge recognition sequence on incoming video frame:
        
        Sequence:
          1. Buffer frame in 15 FPS circular buffer
          2. Run SCRFD face detection
          3. Update ByteTrack tracker with landmark preservation
          4. For each track:
             a. Throttle ArcFace to 1.0s interval per Track ID
             b. Validate facial landmarks (skip if Kalman predicted without detection)
             c. Evaluate FaceQualityGate (blur, size, aspect, pose)
             d. Crop and align 112x112 canonical face
             e. Extract 512-D ArcFace embedding
             f. Search FAISS index (top_k=5, cutoff=0.50)
             g. Deduplicate candidates to max(similarity) per person ID in this frame
             h. Evaluate TemporalVerifier multi-observation criteria
             i. If match confirmed -> dump 15 FPS video clip -> capture evidence -> alert!

        Returns:
            List of (MatchEvent, Evidence) tuples confirmed in this frame.
        """
        if self._stop_event.is_set():
            return []

        if frame is None or frame.size == 0:
            return []

        now = float(timestamp) if timestamp is not None else time.time()

        with self._lock:
            self.telemetry.frames_processed += 1
            self.telemetry.last_frame_timestamp = now

            # 1. Circular Frame Buffer (15 FPS rolling window for video evidence)
            self.circ_buffer.append(frame, now)

            # 2. Fast 10-15 FPS Face Detection
            detections = self.detector.detect(frame)
            num_detected = len(detections) if detections is not None else 0
            self.telemetry.faces_detected += num_detected

            # 3. Landmark-Preserving Multi-Object Tracker (ByteTrack)
            tracks: List[ExtendedTrack] = self.tracker.update(detections)

            matches_in_frame: List[Tuple[MatchEvent, Evidence]] = []

            for track in tracks:
                # a. 1.0s Recognition Throttle per Track ID (Fix #8)
                has_recog = getattr(track, "_has_recognized", False)
                last_recog = getattr(track, "last_recognition_time", 0.0)
                if (has_recog or last_recog > 0.0) and (now - last_recog) < self.recognition_interval:
                    self.telemetry.recognitions_throttled += 1
                    continue

                # b. Validate Landmarks (Skip Kalman-predicted tracks without fresh detection)
                landmarks = getattr(track, "landmarks", None)
                if landmarks is None:
                    continue

                # c. Quality Filter (Laplacian blur, resolution, aspect ratio, pose)
                bbox = getattr(track, "bbox", None)
                if bbox is None or len(bbox) < 4:
                    continue

                passed, _ = self.quality_checker.is_quality_sufficient(frame, bbox, landmarks)
                if not passed:
                    self.telemetry.recognitions_quality_failed += 1
                    continue

                # Record recognition attempt timestamp on track
                track.last_recognition_time = now
                track._has_recognized = True
                if hasattr(track, "state_data") and track.state_data is not None:
                    track.state_data.record_recognition(now)

                self.telemetry.recognitions_attempted += 1

                # d. 5-Point Umeyama Affine Alignment -> 112x112 Canonical Face Crop
                aligned = self.aligner.align(frame, landmarks)
                if aligned is None:
                    continue

                # e. ArcFace 512-D L2-Normalized Feature Vector Extraction
                embedding = self.recognizer.get_embedding(aligned)

                # f. Concurrency-Safe FAISS Vector Search (cutoff >= 0.50)
                candidates: List[Tuple[str, float]] = self.vector_search.search(
                    embedding,
                    top_k=self.top_k,
                    cutoff=self.candidate_cutoff,
                )

                if not candidates:
                    continue

                # g. Per-Frame Candidate Deduplication (Fix #14)
                # When a person has multiple photos in FAISS, take only max(similarity) per frame
                best_candidates: Dict[str, float] = {}
                for person_id, similarity in candidates:
                    if person_id not in best_candidates or similarity > best_candidates[person_id]:
                        best_candidates[person_id] = similarity

                # h. Multi-Observation Temporal Verification (N=3, >=0.60 within 5s)
                for person_id, similarity in best_candidates.items():
                    match_event = self.verifier.check_match(
                        track_id=track.track_id,
                        person_id=person_id,
                        similarity=similarity,
                        timestamp=now,
                    )

                    if match_event is not None:
                        self.telemetry.matches_confirmed += 1

                        # i. Capture Evidence: 112x112 crop, annotated frame, and 15 FPS video clip
                        evidence = self.evidence_collector.capture(
                            frame=frame,
                            track=track,
                            person_id=person_id,
                            similarity=match_event.score,
                            timestamp=now,
                            circ_buffer=self.circ_buffer,
                            aligned_face=aligned,
                            camera_id=self.camera_id,
                            extra_metadata={
                                "observed_scores": match_event.scores,
                                "window_frames": match_event.frames,
                            },
                        )

                        matches_in_frame.append((match_event, evidence))

                        # Dispatch match callback / signal
                        if self.on_match_callback is not None:
                            try:
                                self.on_match_callback(match_event, evidence)
                            except Exception as e:
                                logger.error(f"Error in on_match_callback: {e}", exc_info=True)

            # Optional per-frame telemetry callback
            if self.on_frame_processed_callback is not None:
                try:
                    self.on_frame_processed_callback(frame, tracks)
                except Exception as e:
                    logger.debug(f"Error in on_frame_processed_callback: {e}")

            return matches_in_frame


# Compatibility alias
AIPipeline = EdgeAIPipeline
