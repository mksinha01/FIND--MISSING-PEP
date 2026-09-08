"""SCRFD face detector with 5 facial landmarks using ONNX Runtime / InsightFace."""
from dataclasses import dataclass
import logging
import os
from typing import Any, Dict, List, Optional, Tuple, Union

import cv2
import numpy as np

try:
    import onnxruntime
except ImportError:
    onnxruntime = None  # type: ignore

try:
    from insightface.model_zoo import get_model as insightface_get_model
except ImportError:
    insightface_get_model = None

from edge_agent.utils.path_resolver import get_resource_path

logger = logging.getLogger(__name__)


@dataclass
class Detection:
    """Represents a detected face with bounding box, confidence score, and 5 landmarks."""
    bbox: np.ndarray        # shape (4,) [x1, y1, x2, y2] float32
    score: float            # detection confidence [0.0, 1.0]
    landmarks: np.ndarray   # shape (5, 2) [[x, y], ...] float32


def nms(boxes: np.ndarray, scores: np.ndarray, iou_threshold: float = 0.4) -> List[int]:
    """
    Standard vectorized Non-Maximum Suppression (NMS) in pure NumPy.

    Args:
        boxes: Array of bounding boxes [x1, y1, x2, y2], shape (N, 4).
        scores: Detection confidence scores, shape (N,).
        iou_threshold: Intersection-over-Union cutoff threshold.

    Returns:
        List of retained indices sorted by descending confidence.
    """
    if len(boxes) == 0:
        return []

    x1 = boxes[:, 0]
    y1 = boxes[:, 1]
    x2 = boxes[:, 2]
    y2 = boxes[:, 3]

    areas = np.maximum(0.0, x2 - x1) * np.maximum(0.0, y2 - y1)
    order = scores.argsort()[::-1]

    keep: List[int] = []
    while order.size > 0:
        i = order[0]
        keep.append(int(i))
        if order.size == 1:
            break

        xx1 = np.maximum(x1[i], x1[order[1:]])
        yy1 = np.maximum(y1[i], y1[order[1:]])
        xx2 = np.minimum(x2[i], x2[order[1:]])
        yy2 = np.minimum(y2[i], y2[order[1:]])

        w = np.maximum(0.0, xx2 - xx1)
        h = np.maximum(0.0, yy2 - yy1)
        inter = w * h

        union = areas[i] + areas[order[1:]] - inter
        iou = inter / np.maximum(union, 1e-10)

        inds = np.where(iou <= iou_threshold)[0]
        order = order[inds + 1]

    return keep


class SCRFDFaceDetector:
    """
    Sample and Computation Redistribution for Efficient Face Detection (SCRFD).
    Executes ONNX Runtime session on CPU with 5-point facial landmark regression.
    """

    DEFAULT_MODEL_NAMES = [
        "det_10g.onnx",
        "det_2.5g.onnx",
        "det_500m.onnx",
        "scrfd_10g_bnkps.onnx",
        "scrfd_2.5g_bnkps.onnx",
        "scrfd_500m_bnkps.onnx",
    ]

    def __init__(
        self,
        model_path: Optional[str] = None,
        conf_threshold: float = 0.5,
        nms_threshold: float = 0.4,
        input_size: Tuple[int, int] = (640, 640),
        session: Optional[Any] = None,
        strides: Tuple[int, ...] = (8, 16, 32),
        threshold: Optional[float] = None,
    ):
        if threshold is not None:
            conf_threshold = threshold
        self.conf_threshold = conf_threshold
        self.nms_threshold = nms_threshold
        self.input_size = input_size
        self.strides = strides

        self.model_path = self._resolve_model_path(model_path)
        self.session = session
        self._if_model = None

        if self.session is None and self.model_path and os.path.isfile(self.model_path):
            self._init_session()
        elif self.session is not None:
            self._init_io_names()

    def _resolve_model_path(self, model_path: Optional[str]) -> Optional[str]:
        """Resolves SCRFD ONNX model path across standard local and installed directories."""
        if model_path and os.path.isfile(model_path):
            return os.path.abspath(model_path)

        candidates = []
        if model_path:
            candidates.append(model_path)

        for name in self.DEFAULT_MODEL_NAMES:
            candidates.extend([
                os.path.join(os.getcwd(), "models", name),
                os.path.join(os.getcwd(), "ai_models", "models", "buffalo_l", name),
                os.path.join(os.getcwd(), "ai_models", name),
                os.path.join(os.getcwd(), "edge_agent", "models", name),
                os.path.expanduser(f"~/.insightface/models/buffalo_l/{name}"),
                get_resource_path(os.path.join("models", name)),
                get_resource_path(name),
            ])

        for path in candidates:
            if path and os.path.isfile(path):
                logger.info(f"Resolved SCRFD model to '{os.path.abspath(path)}'")
                return os.path.abspath(path)

        return model_path

    def _init_session(self) -> None:
        """Initializes InsightFace SCRFD model wrapper or raw ONNX Runtime InferenceSession."""
        if insightface_get_model is not None:
            try:
                model = insightface_get_model(
                    self.model_path,
                    providers=["CPUExecutionProvider"],
                )
                model.prepare(
                    ctx_id=-1,
                    det_thresh=self.conf_threshold,
                    input_size=self.input_size,
                )
                self._if_model = model
                self.session = getattr(model, "session", None)
                logger.info(f"SCRFD initialized via insightface model_zoo from '{self.model_path}'")
                return
            except Exception as e:
                logger.debug(f"insightface model_zoo init failed: {e}; falling back to raw ONNX session")

        if onnxruntime is None:
            raise RuntimeError("onnxruntime is not installed in the environment.")

        opts = onnxruntime.SessionOptions()
        opts.intra_op_num_threads = 2
        opts.inter_op_num_threads = 1
        opts.graph_optimization_level = onnxruntime.GraphOptimizationLevel.ORT_ENABLE_ALL

        self.session = onnxruntime.InferenceSession(
            self.model_path,
            sess_options=opts,
            providers=["CPUExecutionProvider"],
        )
        self._init_io_names()
        logger.info(f"SCRFD session initialized via raw ONNX from '{self.model_path}'")

    def _init_io_names(self) -> None:
        if self.session is not None and hasattr(self.session, "get_inputs"):
            self.input_name = self.session.get_inputs()[0].name
            self.output_names = [o.name for o in self.session.get_outputs()]
        else:
            self.input_name = "input.1"
            self.output_names = []

    def preprocess(self, image: np.ndarray) -> Tuple[np.ndarray, float, Tuple[int, int]]:
        """
        Preprocesses an image for SCRFD inference:
        - Maintains aspect ratio via proportional scaling
        - Places resized image onto (input_size[1], input_size[0]) canvas
        - Normalizes RGB values: (x - 127.5) / 128.0

        Returns:
            blob: shape (1, 3, H, W) float32
            det_scale: float scaling factor
            pad: (new_w, new_h) valid canvas dimensions
        """
        im_ratio = float(image.shape[0]) / float(image.shape[1])
        model_ratio = float(self.input_size[1]) / float(self.input_size[0])

        if im_ratio > model_ratio:
            new_height = self.input_size[1]
            new_width = int(new_height / im_ratio)
        else:
            new_width = self.input_size[0]
            new_height = int(new_width * im_ratio)

        det_scale = float(new_height) / float(image.shape[0])
        resized_img = cv2.resize(image, (new_width, new_height))

        canvas = np.zeros((self.input_size[1], self.input_size[0], 3), dtype=np.uint8)
        canvas[:new_height, :new_width, :] = resized_img

        # BGR to RGB, normalize, and transpose to CHW
        rgb_img = cv2.cvtColor(canvas, cv2.COLOR_BGR2RGB)
        normalized = (rgb_img.astype(np.float32) - 127.5) / 128.0
        blob = normalized.transpose(2, 0, 1)[np.newaxis, ...]
        return blob, det_scale, (new_width, new_height)

    @staticmethod
    def generate_anchor_centers(
        height: int,
        width: int,
        stride: int,
        num_anchors: int = 2,
    ) -> np.ndarray:
        """
        Generates spatial anchor centers for feature map grid at given stride.
        Shape: (height * width * num_anchors, 2)
        """
        grid_y, grid_x = np.meshgrid(
            np.arange(height, dtype=np.float32),
            np.arange(width, dtype=np.float32),
            indexing="ij",
        )
        anchor_centers = np.stack([grid_x, grid_y], axis=-1) * stride
        if num_anchors > 1:
            anchor_centers = np.repeat(anchor_centers[:, :, np.newaxis, :], num_anchors, axis=2)
            anchor_centers = anchor_centers.reshape(-1, 2)
        else:
            anchor_centers = anchor_centers.reshape(-1, 2)
        return anchor_centers

    @staticmethod
    def decode_single_stride(
        score_map: np.ndarray,
        bbox_map: np.ndarray,
        kps_map: np.ndarray,
        stride: int,
        conf_threshold: float,
        det_scale: float,
        input_size: Tuple[int, int] = (640, 640),
    ) -> Tuple[np.ndarray, np.ndarray, np.ndarray]:
        """
        Decodes raw model outputs for a single stride into candidate bounding boxes and landmarks.
        Supports both 2D flattened and 4D/3D tensor outputs.
        """
        if score_map.ndim == 3:
            h, w = score_map.shape[0], score_map.shape[1]
            num_anchors = score_map.shape[2]
        elif score_map.ndim == 4:
            h, w = score_map.shape[2], score_map.shape[3]
            num_anchors = score_map.shape[1]
        elif score_map.ndim == 2 and score_map.shape[0] > 1 and score_map.shape[1] > 1:
            h, w = score_map.shape[0], score_map.shape[1]
            num_anchors = 1
        else:
            h = input_size[1] // stride
            w = input_size[0] // stride
            total_anchors = score_map.size
            num_anchors = max(1, total_anchors // (h * w))

        # Reshape score map to 1D
        scores = score_map.reshape(-1)

        # Sigmoid activation if raw logits
        if np.any(scores < 0.0) or np.any(scores > 1.0):
            scores = 1.0 / (1.0 + np.exp(-scores))

        mask = scores >= conf_threshold
        if not np.any(mask):
            return (
                np.empty((0, 4), dtype=np.float32),
                np.empty((0,), dtype=np.float32),
                np.empty((0, 5, 2), dtype=np.float32),
            )

        passed_scores = scores[mask]
        anchor_centers = SCRFDFaceDetector.generate_anchor_centers(h, w, stride, num_anchors)[mask]

        # Decode bounding boxes [dl, dt, dr, db] * stride
        bbox_preds = bbox_map.reshape(-1, 4)[mask] * stride
        x1 = (anchor_centers[:, 0] - bbox_preds[:, 0]) / det_scale
        y1 = (anchor_centers[:, 1] - bbox_preds[:, 1]) / det_scale
        x2 = (anchor_centers[:, 0] + bbox_preds[:, 2]) / det_scale
        y2 = (anchor_centers[:, 1] + bbox_preds[:, 3]) / det_scale
        boxes = np.stack([x1, y1, x2, y2], axis=-1).astype(np.float32)

        # Decode 5 facial landmarks [dx, dy] * stride
        kps_preds = kps_map.reshape(-1, 5, 2)[mask] * stride
        kps_x = (anchor_centers[:, 0:1] + kps_preds[:, :, 0]) / det_scale
        kps_y = (anchor_centers[:, 1:2] + kps_preds[:, :, 1]) / det_scale
        landmarks = np.stack([kps_x, kps_y], axis=-1).astype(np.float32)

        return boxes, passed_scores, landmarks

    def _organize_outputs(
        self,
        raw_outputs: List[np.ndarray],
    ) -> List[Tuple[np.ndarray, np.ndarray, np.ndarray, int]]:
        """
        Organizes ONNX output tensors into (score, bbox, kps, stride) per stride.
        Works across standard SCRFD variants (det_10g, det_2.5g, det_0.5g).
        """
        strides = list(self.strides)
        num_strides = len(strides)

        if len(raw_outputs) == num_strides * 3:
            # Check layout
            shapes = [o.shape for o in raw_outputs]
            # In det_10g.onnx: outputs 0..2 are scores, 3..5 are bboxes, 6..8 are kpss
            scores = raw_outputs[:num_strides]
            bboxes = raw_outputs[num_strides : 2 * num_strides]
            kpss = raw_outputs[2 * num_strides :]
            return [
                (scores[i], bboxes[i], kpss[i], strides[i])
                for i in range(num_strides)
            ]

        # Generic shape classification by channel/second dim
        by_stride: Dict[int, Dict[str, np.ndarray]] = {s: {} for s in strides}
        for out in raw_outputs:
            shape = out.shape
            if len(shape) == 4:
                c, h, w = shape[1], shape[2], shape[3]
                s = int(round(self.input_size[1] / float(h)))
                if s in by_stride:
                    if c in (1, 2):
                        by_stride[s]["score"] = out
                    elif c in (4, 8):
                        by_stride[s]["bbox"] = out
                    elif c in (10, 20):
                        by_stride[s]["kps"] = out
            elif len(shape) == 2:
                total_pts, c = shape[0], shape[1]
                for s in strides:
                    exp_pts = (self.input_size[1] // s) * (self.input_size[0] // s)
                    if total_pts % exp_pts == 0:
                        if c in (1, 2):
                            by_stride[s]["score"] = out
                        elif c in (4, 8):
                            by_stride[s]["bbox"] = out
                        elif c in (10, 20):
                            by_stride[s]["kps"] = out
                        break

        groups = []
        for s in strides:
            m = by_stride[s]
            if "score" in m and "bbox" in m and "kps" in m:
                groups.append((m["score"], m["bbox"], m["kps"], s))

        return groups

    def detect(
        self,
        frame: np.ndarray,
        conf_threshold: Optional[float] = None,
    ) -> List[Detection]:
        """
        Runs SCRFD face detection and 5-point landmark localization on input frame.

        Args:
            frame: Input image (BGR), shape (H, W, 3).
            conf_threshold: Optional override for confidence threshold.

        Returns:
            List of Detection objects passing confidence and NMS thresholds.
        """
        if frame is None or frame.size == 0:
            return []

        thresh = conf_threshold if conf_threshold is not None else self.conf_threshold

        # Option A: insightface model_zoo wrapper if active
        if self._if_model is not None:
            try:
                # insightface SCRFD detect method
                bboxes, kpss = self._if_model.detect(
                    frame,
                    input_size=self.input_size,
                )
                if len(bboxes) == 0:
                    return []

                detections: List[Detection] = []
                for i in range(len(bboxes)):
                    box = bboxes[i]
                    score = float(box[4]) if len(box) >= 5 else 1.0
                    if score < thresh:
                        continue
                    kps = kpss[i] if kpss is not None and i < len(kpss) else np.zeros((5, 2), dtype=np.float32)
                    detections.append(
                        Detection(
                            bbox=box[:4].astype(np.float32),
                            score=score,
                            landmarks=kps.astype(np.float32),
                        )
                    )
                return detections
            except Exception as e:
                logger.debug(f"Insightface detect error: {e}; falling back to raw ONNX decode")

        if self.session is None:
            return []

        # Option B: Raw ONNX Runtime inference
        blob, det_scale, _ = self.preprocess(frame)
        raw_outputs = self.session.run(self.output_names, {self.input_name: blob})

        organized = self._organize_outputs(raw_outputs)
        all_boxes: List[np.ndarray] = []
        all_scores: List[np.ndarray] = []
        all_landmarks: List[np.ndarray] = []

        for score_map, bbox_map, kps_map, stride in organized:
            boxes, scores, landmarks = self.decode_single_stride(
                score_map, bbox_map, kps_map, stride, thresh, det_scale, self.input_size
            )
            if len(boxes) > 0:
                all_boxes.append(boxes)
                all_scores.append(scores)
                all_landmarks.append(landmarks)

        if not all_boxes:
            return []

        boxes_arr = np.concatenate(all_boxes, axis=0)
        scores_arr = np.concatenate(all_scores, axis=0)
        landmarks_arr = np.concatenate(all_landmarks, axis=0)

        # Clip bounding boxes and landmarks to image boundaries
        h_orig, w_orig = frame.shape[:2]
        boxes_arr[:, 0] = np.clip(boxes_arr[:, 0], 0, w_orig)
        boxes_arr[:, 1] = np.clip(boxes_arr[:, 1], 0, h_orig)
        boxes_arr[:, 2] = np.clip(boxes_arr[:, 2], 0, w_orig)
        boxes_arr[:, 3] = np.clip(boxes_arr[:, 3], 0, h_orig)

        landmarks_arr[:, :, 0] = np.clip(landmarks_arr[:, :, 0], 0, w_orig)
        landmarks_arr[:, :, 1] = np.clip(landmarks_arr[:, :, 1], 0, h_orig)

        # Non-Maximum Suppression (NMS)
        keep_indices = nms(boxes_arr, scores_arr, self.nms_threshold)

        detections: List[Detection] = []
        for idx in keep_indices:
            detections.append(
                Detection(
                    bbox=boxes_arr[idx],
                    score=float(scores_arr[idx]),
                    landmarks=landmarks_arr[idx],
                )
            )

        return detections


# Architectural specification alias
FaceDetector = SCRFDFaceDetector
