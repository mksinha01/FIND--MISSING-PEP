"""Thread-safe FAISS vector search engine with double-buffering pointer swap for concurrent searches."""
import logging
import os
import threading
from typing import Any, Dict, List, Optional, Tuple, Union

import faiss
import numpy as np

logger = logging.getLogger(__name__)


class VectorSearchEngine:
    """
    FAISS IndexFlatIP vector engine for cosine similarity matching on 512-D L2-normalized embeddings.

    Thread-Safe Concurrency Architecture:
    Protects against fatal C++ segmentation faults (SIGSEGV / EXCEPTION_ACCESS_VIOLATION)
    when camera ingestion threads perform concurrent read searches while a background worker
    updates or rebuilds the index.

    Achieved via Double-Buffering:
    The new FAISS index is completely assembled offline in memory, and the pointer swap
    occurs atomically under threading.Lock.
    """

    def __init__(self, dimension: int = 512):
        self.dimension = dimension
        self.lock = threading.Lock()

        # Active buffers
        self.index: faiss.IndexFlatIP = faiss.IndexFlatIP(self.dimension)
        self.person_ids: List[str] = []
        self.embedding_ids: List[str] = []

    @property
    def size(self) -> int:
        """Returns the current number of indexed vector embeddings."""
        with self.lock:
            return self.index.ntotal if self.index else 0

    def __len__(self) -> int:
        return self.size

    def search(
        self,
        query: Union[np.ndarray, List[float], bytes],
        top_k: int = 5,
        cutoff: float = 0.50,
    ) -> List[Tuple[str, float]]:
        """
        Executes thread-safe nearest-neighbor search for candidate matches.

        Args:
            query: 512-D query embedding vector.
            top_k: Maximum number of nearest neighbors to retrieve.
            cutoff: Minimum cosine similarity threshold (Inner Product).

        Returns:
            List of (person_id, similarity) tuples ordered by descending similarity score.
        """
        if isinstance(query, bytes):
            vec = np.frombuffer(query, dtype=np.float32)
        elif isinstance(query, (list, tuple)):
            vec = np.array(query, dtype=np.float32)
        elif isinstance(query, np.ndarray):
            vec = query.astype(np.float32).flatten()
        else:
            logger.warning(f"Unsupported query vector type: {type(query)}")
            return []

        if vec.shape[0] != self.dimension:
            logger.warning(
                f"Query vector dimension mismatch: expected {self.dimension}, got {vec.shape[0]}"
            )
            return []

        # L2-normalize query vector for cosine similarity
        norm = float(np.linalg.norm(vec))
        if norm > 1e-6:
            vec = vec / norm
        else:
            return []

        query_matrix = vec.reshape(1, -1).astype(np.float32)

        # Thread-safe search under lock
        with self.lock:
            if self.index.ntotal == 0:
                return []

            k = min(int(top_k), self.index.ntotal)
            if k <= 0:
                return []

            distances, indices = self.index.search(query_matrix, k)

            results: List[Tuple[str, float]] = []
            for dist, idx in zip(distances[0], indices[0]):
                if idx != -1 and 0 <= idx < len(self.person_ids):
                    similarity = float(dist)
                    if similarity >= cutoff:
                        results.append((self.person_ids[idx], similarity))

            return results

    def rebuild_index(
        self,
        data: List[Union[Tuple[str, np.ndarray], Dict[str, Any]]],
    ) -> int:
        """
        Double Buffering index update (Rule 7, Fix #6).
        Builds a brand-new FAISS index offline, then atomically swaps the pointer under lock.
        Completely eliminates thread contention and C++ memory corruption.

        Args:
            data: List of items either as (person_id, embedding_vector) tuples
                  or dicts with keys 'person_id', 'embedding_data', and optional 'id'.

        Returns:
            Total count of successfully indexed vectors.
        """
        # Step 1: Build offline outside the lock
        new_index = faiss.IndexFlatIP(self.dimension)
        new_person_ids: List[str] = []
        new_embedding_ids: List[str] = []
        vectors_list: List[np.ndarray] = []

        if data:
            for item in data:
                if isinstance(item, tuple) and len(item) >= 2:
                    person_id = str(item[0])
                    raw_emb = item[1]
                    emb_id = str(item[2]) if len(item) >= 3 else ""
                elif isinstance(item, dict):
                    person_id = str(item.get("person_id", ""))
                    raw_emb = item.get("embedding_data")
                    emb_id = str(item.get("id", ""))
                else:
                    continue

                if isinstance(raw_emb, bytes):
                    vec = np.frombuffer(raw_emb, dtype=np.float32)
                elif isinstance(raw_emb, np.ndarray):
                    vec = raw_emb.astype(np.float32).flatten()
                elif isinstance(raw_emb, (list, tuple)):
                    vec = np.array(raw_emb, dtype=np.float32)
                else:
                    continue

                if vec.shape[0] != self.dimension:
                    logger.warning(
                        f"Skipping vector for person {person_id}: invalid dimension {vec.shape[0]}"
                    )
                    continue

                norm = float(np.linalg.norm(vec))
                if norm > 1e-6:
                    vec = vec / norm
                else:
                    continue

                vectors_list.append(vec)
                new_person_ids.append(person_id)
                new_embedding_ids.append(emb_id)

            if vectors_list:
                matrix = np.vstack(vectors_list).astype(np.float32)
                new_index.add(matrix)

        # Step 2: Atomic pointer swap under lock
        with self.lock:
            self.index = new_index
            self.person_ids = new_person_ids
            self.embedding_ids = new_embedding_ids

        logger.info(
            f"FAISS index atomically swapped with {len(new_person_ids)} embeddings via double-buffering."
        )
        return len(new_person_ids)

    def rebuild(
        self,
        items: List[Union[Tuple[str, np.ndarray], Dict[str, Any]]],
    ) -> int:
        """Alias for rebuild_index to match FAISSStore interface."""
        return self.rebuild_index(items)

    def update_index(
        self,
        new_data: List[Union[Tuple[str, np.ndarray], Dict[str, Any]]],
    ) -> int:
        """Alias for rebuild_index."""
        return self.rebuild_index(new_data)

    def add_vector(
        self,
        person_id: str,
        vector: np.ndarray,
        embedding_id: str = "",
    ) -> bool:
        """
        Thread-safe dynamic addition of a single vector.
        Normalizes vector and appends to index under lock.
        """
        vec = vector.astype(np.float32).flatten()
        if vec.shape[0] != self.dimension:
            return False

        norm = float(np.linalg.norm(vec))
        if norm > 1e-6:
            vec = vec / norm
        else:
            return False

        matrix = vec.reshape(1, -1).astype(np.float32)
        with self.lock:
            self.index.add(matrix)
            self.person_ids.append(str(person_id))
            self.embedding_ids.append(str(embedding_id))
        return True

    def clear(self) -> None:
        """Thread-safely clears all indexed vectors."""
        with self.lock:
            self.index = faiss.IndexFlatIP(self.dimension)
            self.person_ids.clear()
            self.embedding_ids.clear()

    def get_indexed_person_ids(self) -> List[str]:
        """Returns a copy of indexed person IDs."""
        with self.lock:
            return list(self.person_ids)


# Compatibility alias
ThreadSafeFAISSIndex = VectorSearchEngine
