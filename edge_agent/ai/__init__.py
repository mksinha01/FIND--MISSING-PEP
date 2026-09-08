"""AI pipeline package for face detection, tracking, alignment, ArcFace recognition, FAISS search, and temporal verification."""
from edge_agent.ai.face_aligner import (
    ARCFACE_REFERENCE_POINTS_112x112,
    FaceAligner,
    umeyama_similarity_transform,
)
from edge_agent.ai.face_detector import Detection, FaceDetector, SCRFDFaceDetector, nms
from edge_agent.ai.face_quality import (
    FaceQualityChecker,
    FaceQualityGate,
    QualityResult,
)
from edge_agent.ai.face_recognizer import ArcFaceRecognizer
from edge_agent.ai.vector_search import ThreadSafeFAISSIndex, VectorSearchEngine
from edge_agent.ai.temporal_verifier import MatchEvent, TemporalVerifier
from edge_agent.ai.evidence_collector import Evidence, EvidenceCollector
from edge_agent.ai.pipeline import AIPipeline, EdgeAIPipeline, PipelineTelemetry
from edge_agent.ai.track_state import TrackState, TrackStatus
from edge_agent.ai.tracker import (
    ByteTracker,
    ExtendedTrack,
    KalmanFilter,
    STrack,
)

__all__ = [
    "Detection",
    "FaceDetector",
    "SCRFDFaceDetector",
    "nms",
    "FaceQualityGate",
    "FaceQualityChecker",
    "QualityResult",
    "FaceAligner",
    "ARCFACE_REFERENCE_POINTS_112x112",
    "umeyama_similarity_transform",
    "ByteTracker",
    "ExtendedTrack",
    "STrack",
    "KalmanFilter",
    "TrackState",
    "TrackStatus",
    "ArcFaceRecognizer",
    "VectorSearchEngine",
    "ThreadSafeFAISSIndex",
    "TemporalVerifier",
    "MatchEvent",
    "EvidenceCollector",
    "Evidence",
    "EdgeAIPipeline",
    "AIPipeline",
    "PipelineTelemetry",
]

