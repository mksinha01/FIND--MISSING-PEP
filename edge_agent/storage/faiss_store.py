"""FAISS vector store for fast, thread-safe ArcFace face recognition matching with double-buffering."""
import json
import logging
import os
import threading
from typing import Any, Dict, List, Optional, Tuple, Union

import faiss
import numpy as np

logger = logging.getLogger(__name__)


class FAISSStore:
    """
    Thread-safe FAISS vector index wrapper supporting atomic double-buffering swap.
    Uses IndexFlatIP (Inner Product) for cosine similarity on L2-normalized 512-D embeddings.
    """

    def __init__(self, index_path: Optional[str] = None, dim: int = 512):
        self.dim = dim
        self.index_path = os.path.abspath(index_path) if index_path else None
        self._lock = threading.RLock()

        # Internal state
        self._index: faiss.IndexFlatIP = faiss.IndexFlatIP(self.dim)
        self._person_ids: List[str] = []
        self._embedding_ids: List[str] = []

        if self.index_path and os.path.exists(self.index_path):
            self.load(self.index_path)

    @property
    def size(self) -> int:
        """Return the number of vectors currently indexed."""
        with self._lock:
            return self._index.ntotal if self._index else 0

    def rebuild(self, items: List[Dict[str, Any]]) -> int:
        """
        Thread-safe double-buffering index rebuild (Rule 7).
        Builds a brand-new FAISS index in memory and atomically swaps pointer under lock.
        Accepts items with keys: 'id' (embedding_id), 'person_id', 'embedding_data' (bytes or np.ndarray).
        """
        new_index = faiss.IndexFlatIP(self.dim)
        new_person_ids: List[str] = []
        new_embedding_ids: List[str] = []

        if items:
            vectors_list = []
            for item in items:
                raw_data = item.get("embedding_data")
                if isinstance(raw_data, bytes):
                    vec = np.frombuffer(raw_data, dtype=np.float32)
                elif isinstance(raw_data, np.ndarray):
                    vec = raw_data.astype(np.float32).flatten()
                elif isinstance(raw_data, (list, tuple)):
                    vec = np.array(raw_data, dtype=np.float32)
                else:
                    logger.warning(f"Skipping invalid embedding item: {item.get('id')}")
                    continue

                if vec.shape[0] != self.dim:
                    logger.warning(
                        f"Embedding dimension mismatch: expected {self.dim}, got {vec.shape[0]}. Skipping."
                    )
                    continue

                # Ensure L2 normalized for cosine similarity
                norm = np.linalg.norm(vec)
                if norm > 1e-6:
                    vec = vec / norm
                else:
                    logger.warning(f"Zero-norm vector detected for item {item.get('id')}. Skipping.")
                    continue

                vectors_list.append(vec)
                new_person_ids.append(str(item["person_id"]))
                new_embedding_ids.append(str(item.get("id", "")))

            if vectors_list:
                matrix = np.vstack(vectors_list).astype(np.float32)
                new_index.add(matrix)

        # Atomic pointer swap under lock
        with self._lock:
            self._index = new_index
            self._person_ids = new_person_ids
            self._embedding_ids = new_embedding_ids

        logger.info(f"FAISS index rebuilt with {len(new_person_ids)} embeddings")
        return len(new_person_ids)

    def search(
        self,
        query_vector: np.ndarray,
        top_k: int = 5,
        threshold: float = 0.50,
    ) -> List[Dict[str, Any]]:
        """
        Search the FAISS index for nearest matches.
        Returns matches with similarity >= threshold, sorted by score descending.
        Result format: [{'person_id': ..., 'embedding_id': ..., 'similarity': float, 'index': int}]
        """
        if isinstance(query_vector, (list, tuple)):
            query_vector = np.array(query_vector, dtype=np.float32)
        elif isinstance(query_vector, bytes):
            query_vector = np.frombuffer(query_vector, dtype=np.float32)

        vec = query_vector.astype(np.float32).flatten()
        if vec.shape[0] != self.dim:
            logger.warning(f"Query vector dimension mismatch: expected {self.dim}, got {vec.shape[0]}")
            return []

        # L2 normalize
        norm = np.linalg.norm(vec)
        if norm > 1e-6:
            vec = vec / norm
        else:
            return []

        # Snapshot current index and metadata under lock
        with self._lock:
            if self._index.ntotal == 0:
                return []
            current_index = self._index
            person_ids = list(self._person_ids)
            embedding_ids = list(self._embedding_ids)

        k = min(top_k, current_index.ntotal)
        if k <= 0:
            return []

        query_matrix = vec.reshape(1, -1).astype(np.float32)
        scores, indices = current_index.search(query_matrix, k)

        results: List[Dict[str, Any]] = []
        for score, idx in zip(scores[0], indices[0]):
            if idx < 0 or idx >= len(person_ids):
                continue
            sim = float(score)
            if sim >= threshold:
                results.append(
                    {
                        "person_id": person_ids[idx],
                        "embedding_id": embedding_ids[idx],
                        "similarity": round(sim, 4),
                        "index": int(idx),
                    }
                )

        return results

    def clear(self) -> None:
        """Clear all vectors from the index."""
        with self._lock:
            self._index = faiss.IndexFlatIP(self.dim)
            self._person_ids.clear()
            self._embedding_ids.clear()

    def save(self, path: Optional[str] = None) -> str:
        """
        Persist FAISS index and metadata to disk.
        Generates <path>.index and <path>.meta.json.
        """
        target_path = path or self.index_path or "faiss_index.bin"
        os.makedirs(os.path.dirname(os.path.abspath(target_path)), exist_ok=True) if os.path.dirname(target_path) else None

        meta_path = f"{target_path}.meta.json"

        with self._lock:
            faiss.write_index(self._index, target_path)
            meta = {
                "dim": self.dim,
                "person_ids": self._person_ids,
                "embedding_ids": self._embedding_ids,
                "count": len(self._person_ids),
            }
            with open(meta_path, "w", encoding="utf-8") as f:
                json.dump(meta, f, indent=2)

        self.index_path = target_path
        return target_path

    def load(self, path: Optional[str] = None) -> bool:
        """
        Load FAISS index and metadata from disk with atomic pointer swap.
        """
        target_path = path or self.index_path
        if not target_path or not os.path.isfile(target_path):
            logger.warning(f"FAISS index file not found: {target_path}")
            return False

        meta_path = f"{target_path}.meta.json"
        if not os.path.isfile(meta_path):
            logger.warning(f"FAISS metadata file not found: {meta_path}")
            return False

        try:
            loaded_index = faiss.read_index(target_path)
            with open(meta_path, "r", encoding="utf-8") as f:
                meta = json.load(f)

            loaded_person_ids = meta.get("person_ids", [])
            loaded_embedding_ids = meta.get("embedding_ids", [])

            # Atomic swap under lock
            with self._lock:
                self._index = loaded_index
                self._person_ids = loaded_person_ids
                self._embedding_ids = loaded_embedding_ids
                self.dim = meta.get("dim", self.dim)

            logger.info(f"Loaded FAISS index from {target_path} ({len(loaded_person_ids)} items)")
            return True
        except Exception as e:
            logger.error(f"Failed to load FAISS index from {target_path}: {e}")
            return False
