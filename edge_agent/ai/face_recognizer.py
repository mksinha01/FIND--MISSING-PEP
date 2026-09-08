"""ArcFace face recognition feature extraction using ONNX runtime and L2 normalization."""
import logging
import os
from typing import Any, List, Optional, Union

import cv2
import numpy as np

try:
    import onnxruntime as ort
except ImportError:
    ort = None  # type: ignore

from edge_agent.utils.path_resolver import get_resource_path

logger = logging.getLogger(__name__)


class ArcFaceRecognizer:
    """
    ArcFace deep feature extractor based on w600k_r50.onnx architecture.
    Transforms aligned 112x112 facial images into 512-dimensional L2-normalized unit embeddings.
    """

    DEFAULT_MODEL_NAME = "w600k_r50.onnx"
    EMBEDDING_DIM = 512

    def __init__(
        self,
        model_path: Optional[str] = None,
        providers: Optional[List[str]] = None,
        session: Optional[Any] = None,
    ):
        """
        Initializes ArcFace inference session.

        Args:
            model_path: Path to w600k_r50.onnx file.
            providers: List of execution providers (e.g. ['CUDAExecutionProvider', 'CPUExecutionProvider']).
            session: Optional pre-configured onnxruntime.InferenceSession or mock session.
        """
        self.model_path = self._resolve_model_path(model_path)
        self.session = session
        self.input_name: Optional[str] = None
        self.output_name: Optional[str] = None

        if self.session is not None:
            self._init_io_names()
        elif self.model_path and os.path.isfile(self.model_path):
            self._init_session(providers)
        else:
            logger.warning(
                f"ArcFace model file not found at '{self.model_path}'. "
                "Inference will require an injected session or explicit model path."
            )

    def _resolve_model_path(self, model_path: Optional[str]) -> Optional[str]:
        """Resolves model path searching common directories and package resources."""
        if model_path and os.path.isfile(model_path):
            return os.path.abspath(model_path)

        candidates = [
            model_path,
            os.path.join(os.getcwd(), "models", self.DEFAULT_MODEL_NAME),
            os.path.join(os.getcwd(), "ai_models", "models", "buffalo_l", self.DEFAULT_MODEL_NAME),
            os.path.join(os.getcwd(), "ai_models", self.DEFAULT_MODEL_NAME),
            os.path.join(os.getcwd(), "edge_agent", "models", self.DEFAULT_MODEL_NAME),
            os.path.expanduser(f"~/.insightface/models/buffalo_l/{self.DEFAULT_MODEL_NAME}"),
            get_resource_path(os.path.join("models", self.DEFAULT_MODEL_NAME)),
            get_resource_path(self.DEFAULT_MODEL_NAME),
        ]

        for path in candidates:
            if path and os.path.isfile(path):
                return os.path.abspath(path)

        return model_path

    def _init_session(self, providers: Optional[List[str]] = None) -> None:
        """Configures and initializes ONNX Runtime InferenceSession."""
        if ort is None:
            raise ImportError(
                "onnxruntime is required for ArcFaceRecognizer but is not installed."
            )

        if providers is None:
            available_providers = ort.get_available_providers()
            if "CUDAExecutionProvider" in available_providers:
                providers = ["CUDAExecutionProvider", "CPUExecutionProvider"]
            else:
                providers = ["CPUExecutionProvider"]

        opts = ort.SessionOptions()
        opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        opts.inter_op_num_threads = 2
        opts.intra_op_num_threads = 2

        self.session = ort.InferenceSession(
            self.model_path,
            sess_options=opts,
            providers=providers,
        )
        self._init_io_names()
        logger.info(
            f"ArcFace session initialized from '{self.model_path}' with providers {providers}"
        )

    def _init_io_names(self) -> None:
        """Retrieves input and output tensor names from the session."""
        if hasattr(self.session, "get_inputs"):
            self.input_name = self.session.get_inputs()[0].name
        if hasattr(self.session, "get_outputs"):
            self.output_name = self.session.get_outputs()[0].name

    def preprocess(self, face_image: np.ndarray) -> np.ndarray:
        """
        Prepares 112x112 face crop for ArcFace model input:
        1. Resizes to (112, 112) if dimensions vary slightly.
        2. Converts BGR to RGB.
        3. Normalizes pixel values via (x - 127.5) / 127.5.
        4. Transposes from HWC (112, 112, 3) to NCHW (1, 3, 112, 112) float32.
        """
        if face_image is None or face_image.size == 0:
            raise ValueError("Invalid face image: image is None or empty.")

        h, w = face_image.shape[:2]
        if (h, w) != (112, 112):
            face_image = cv2.resize(face_image, (112, 112), interpolation=cv2.INTER_LINEAR)

        # Standard ArcFace expects RGB input
        if len(face_image.shape) == 3 and face_image.shape[2] == 3:
            img_rgb = cv2.cvtColor(face_image, cv2.COLOR_BGR2RGB)
        elif len(face_image.shape) == 2:
            img_rgb = cv2.cvtColor(face_image, cv2.COLOR_GRAY2RGB)
        else:
            img_rgb = face_image

        # Scale to [-1.0, 1.0] float32
        blob = (img_rgb.astype(np.float32) - 127.5) / 127.5

        # Transpose HWC -> CHW and add batch dimension -> (1, 3, 112, 112)
        blob = np.transpose(blob, (2, 0, 1))
        blob = np.expand_dims(blob, axis=0).astype(np.float32)
        return blob

    def get_embedding(self, face_image: np.ndarray) -> np.ndarray:
        """
        Extracts a 512-D L2-normalized feature embedding vector from an aligned face image.

        Args:
            face_image: Aligned face crop (typically 112x112), shape (H, W, 3).

        Returns:
            np.ndarray of shape (512,) float32 with unit L2 norm (||v||_2 = 1.0).
        """
        if self.session is None:
            raise RuntimeError(
                "ArcFaceRecognizer session is not initialized. Model path: "
                f"{self.model_path}"
            )

        blob = self.preprocess(face_image)

        input_name = self.input_name or self.session.get_inputs()[0].name
        output_name = self.output_name or self.session.get_outputs()[0].name

        outputs = self.session.run([output_name], {input_name: blob})
        embedding = outputs[0].flatten().astype(np.float32)

        if embedding.shape[0] != self.EMBEDDING_DIM:
            raise ValueError(
                f"Expected embedding dimension {self.EMBEDDING_DIM}, got {embedding.shape[0]}"
            )

        # L2-normalize to unit hypersphere: v_hat = v / ||v||_2
        norm = float(np.linalg.norm(embedding))
        if norm > 1e-6:
            embedding = embedding / norm
        else:
            logger.warning("Extracted embedding vector had zero norm.")
            embedding = np.zeros(self.EMBEDDING_DIM, dtype=np.float32)

        return embedding

    def extract_embedding(self, face_image: np.ndarray) -> np.ndarray:
        """Convenience alias for get_embedding."""
        return self.get_embedding(face_image)

    def get_embeddings(self, face_images: List[np.ndarray]) -> np.ndarray:
        """
        Batch extraction of embeddings for multiple face images.

        Args:
            face_images: List of aligned face crops.

        Returns:
            Array of shape (N, 512) float32 L2-normalized embeddings.
        """
        if not face_images:
            return np.empty((0, self.EMBEDDING_DIM), dtype=np.float32)

        embeddings = [self.get_embedding(img) for img in face_images]
        return np.vstack(embeddings).astype(np.float32)
