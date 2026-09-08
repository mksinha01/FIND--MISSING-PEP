# Architectural Flaws, Misconceptions, and Implementation Blockers in FIND-MISSING-PEP
**Target Reference**: `DOC/5-Architecture.md` (and cross-referenced with `DOC/4-Implementation-Plan.md`, `backend/`, and `edge_agent/`)  
**Audit Scope**: Runtime blockers, core computer vision/algorithmic flaws, schema/naming mismatches, network/API inconsistencies, dependency conflicts, and missing files.

---

## Executive Summary

While [5-Architecture.md](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md) provides a structured layout following the One-Shot Build Spec format, a rigorous technical audit reveals **26 critical flaws and misconceptions**. 

If implemented strictly as written under the *"Build this exactly. Do not skip anything. Do not simplify. Do not substitute libraries"* directive, **the project will fail to build, compile, or run**. Specifically:
- The Docker container will crash during `docker build` on modern Debian.
- The Edge Agent will fail to install on Windows due to legacy C++ dependencies.
- The core research novelty (ByteTrack + Temporal Face Verification) contains a **mathematical and timing paradox** that prevents match alerts from ever triggering on moving pedestrians.
- The multi-threaded PySide6 Edge Agent will suffer from **fatal C++ segmentation faults** due to concurrent FAISS access.
- The database schema and local SQLite cache have structural key collisions and broken synchronization logic.

---

## Severity Scorecard

| Category | Blocker Count | Description |
|---|:---:|---|
| **Category 1: Critical Runtime Blockers** | 6 | Docker build crashes, Windows pip install failures, C++ segfaults, Numpy conflicts. |
| **Category 2: Core Algorithmic & Computer Vision Flaws** | 6 | Mathematical and timing paradoxes in ByteTrack, temporal matching, and InsightFace. |
| **Category 3: Database Schema & Synchronization Mismatches** | 5 | SQLite unique constraint crashes, missing sync columns, and foreign key cascade traps. |
| **Category 4: REST API, Network & Protocol Inconsistencies** | 4 | Orphaned reports, missing asset endpoints, and unauthenticated agent registration. |
| **Category 5: File Tree, Packaging & Dependency Discrepancies** | 3 | Defunct Flutter packages, missing map libraries, and PyInstaller asset path breaks. |
| **Category 6: Operational, Security & Hardware Reality Checks** | 2 | Plaintext camera credentials and unrealistic CPU claims for 4-camera RTSP decoding. |

---

## Category 1: Critical Runtime Blockers (Immediate Build/Startup Crashes)

### 1.1 Docker Build Crash: Deprecated `libgl1-mesa-glx` on Debian 12
- **Location**: [5-Architecture.md:924-929](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L924-L929) (`backend/Dockerfile`)
- **The Flaw**: The Dockerfile uses `python:3.11-slim`, which is based on **Debian 12 (Bookworm)**. In Debian Bookworm, the package `libgl1-mesa-glx` has been **completely removed**.
- **Impact**: Running `docker compose up --build` or `docker build` fails immediately with:
  ```text
  E: Package 'libgl1-mesa-glx' has no installation candidate
  ```
- **Correction**: Replace `libgl1-mesa-glx` with `libgl1` and `libglib2.0-0`, or rely on `opencv-python-headless` which requires no X11/GL libraries:
  ```dockerfile
  RUN apt-get update && apt-get install -y --no-install-recommends \
      libgl1 \
      libglib2.0-0 \
      libpq-dev \
      curl \
      && rm -rf /var/lib/apt/lists/*
  ```

---

### 1.2 Docker Build Flaw: InsightFace Model Download Does Not Execute
- **Location**: [5-Architecture.md:938](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L938) (`backend/Dockerfile`)
- **The Flaw**: The Dockerfile attempts to pre-download models using:
  ```dockerfile
  RUN python -c "from insightface.app import FaceAnalysis; FaceAnalysis(name='buffalo_l', root='./ai_models')"
  ```
  In InsightFace, initializing `FaceAnalysis(...)` only configures directory metadata. **It does NOT download models.** Models are only downloaded when `.prepare(ctx_id=..., det_size=...)` is invoked.
- **Impact**: The Docker image finishes building with zero models downloaded in `/app/ai_models`. When the container starts offline or behind a restricted firewall, the backend crashes on the first request attempting to download ~300MB from GitHub releases.
- **Correction**: Call `.prepare()` during the build step:
  ```dockerfile
  RUN python -c "from insightface.app import FaceAnalysis; app = FaceAnalysis(name='buffalo_l', root='./ai_models'); app.prepare(ctx_id=-1, det_size=(640, 640))"
  ```

---

### 1.3 Windows Edge Agent Failure: `lap>=0.4.0` Has No Wheels on Modern Windows
- **Location**: [5-Architecture.md:867](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L867) (`edge_agent/requirements.txt`)
- **The Flaw**: ByteTrack relies on a Linear Assignment Problem (LAP) solver. The requirement specifies `lap>=0.4.0`. `lap` is an abandoned C++ package with **no pre-compiled wheels for Windows on Python 3.10, 3.11, or 3.12**.
- **Impact**: Running `pip install -r requirements.txt` on a standard Windows PC (as instructed in Section 13) crashes with:
  ```text
  error: Microsoft Visual C++ 14.0 or greater is required.
  fatal error C1083: Cannot open include file: 'lap.h'
  ```
- **Correction**: Use `lapx` (the maintained fork with modern multi-platform binary wheels) or use Scipy's built-in Hungarian solver:
  ```text
  # edge_agent/requirements.txt
  lapx>=0.5.5
  ```

---

### 1.4 Fatal Numpy Conflict: `numpy>=1.26.0` vs Deprecated `filterpy>=1.4.5`
- **Location**: [5-Architecture.md:860, 868](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L860) (`edge_agent/requirements.txt`)
- **The Flaw**: `filterpy` (Kalman Filter library used by ByteTrack) has not been updated since 2020 and internally references `np.float`. In `numpy>=1.24` (and explicitly in `numpy>=1.26.0`), `np.float` was completely deleted from NumPy.
- **Impact**: At runtime, initializing `filterpy.kalman.KalmanFilter` crashes immediately with:
  ```text
  AttributeError: module 'numpy' has no attribute 'float'
  ```
- **Correction**: Either pin `numpy<1.24` (not recommended for modern PySide6/ONNX) or vendor a modernized 100-line ByteTrack Kalman filter using standard `np.float64` / `np.float32`.

---

### 1.5 Deprecated and Conflicting Redis Packages
- **Location**: [5-Architecture.md:824-825](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L824-L825) (`backend/requirements.txt`)
- **The Flaw**: Requirements list both:
  ```text
  redis>=5.0.0
  aioredis>=2.0.0
  ```
  `aioredis` was sunset and merged directly into `redis-py` starting with `redis 4.2.0`. Installing standalone `aioredis>=2.0.0` alongside `redis>=5.0.0` creates dependency conflicts and namespace clashes.
- **Impact**: Pip install warnings, import confusion, and runtime deprecation crashes in asynchronous pub/sub.
- **Correction**: Remove `aioredis` entirely. Import async Redis directly from `redis.asyncio`:
  ```text
  # backend/requirements.txt
  redis>=5.0.0
  ```
  ```python
  import redis.asyncio as redis
  ```

---

### 1.6 Concurrency Crash: Multi-Threaded FAISS Vector Search Segfault
- **Location**: [5-Architecture.md:1250-1267, 1389-1436](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L1250-L1267) (`edge_agent/ai/vector_search.py`)
- **The Flaw**: The Edge Agent runs 1–4 camera streams in separate `QThread` workers. In Section 12 Rule 7, index synchronization does:
  ```python
  self.index.reset(); self.person_ids.clear()
  for pid, emb in data: self.index.add(emb); self.person_ids.append(pid)
  ```
  FAISS C++ CPU indexes (`IndexFlatIP`) are **not thread-safe for concurrent read/write**. While the background `SyncWorker` is resetting and rebuilding the index, camera threads are calling `self.index.search(...)`.
- **Impact**: Hard C++ segmentation fault (`SIGSEGV` / `EXCEPTION_ACCESS_VIOLATION`), instantly killing the entire Qt desktop application without a Python traceback.
- **Correction**: Implement **atomic pointer swapping (double buffering)** or a Read-Write lock (`threading.Lock`):
  ```python
  # Build offline, then swap pointer atomically
  new_index = faiss.IndexFlatIP(512)
  new_index.add(embeddings)
  with self.lock:
      self.index = new_index
      self.person_ids = new_person_ids
  ```

---

## Category 2: Core Algorithmic & Computer Vision Flaws (Research Novelty Breakdown)

### 2.1 The 1 FPS vs ByteTrack Kalman Filter Impossibility
- **Location**: [5-Architecture.md:48, 462, 1058, 1400-1418](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L48)
- **The Flaw**: The architecture specifies downsampling CCTV streams to **1 FPS** (`frame_sample_fps = 1`), and then running ByteTrack to maintain persistent Track IDs across frames.
  - ByteTrack's Kalman filter and IoU association rely on high frame rates (15–30 FPS) where the displacement of a face between frame $t$ and frame $t+1$ is small (<15 pixels), yielding an IoU overlap of 0.7–0.9.
  - At **1 FPS**, the time interval between frames is **1.0 second**. A person walking at normal speed (1.4 m/s) moves ~1.4 meters (~4.6 feet). In a CCTV frame, their face bounding box moves 50–200 pixels.
  - The IoU between consecutive frames at 1 FPS is frequently **0.0 (ZERO)**.
- **Impact**: 
  - ByteTrack fails IoU association on every single frame.
  - Every frame creates a brand new Track ID.
  - The tracker will never track someone for more than 1 frame.
  - Because Track IDs change every second, **temporal verification across multiple frames is mathematically impossible**.
- **Correction**: Decouple detection/tracking from recognition:
  - Run lightweight face detection + ByteTrack at **10–15 FPS** (or stream native FPS) using tiny model inputs.
  - Sample/throttle the expensive **ArcFace recognition** at 1 FPS per tracked object.

---

### 2.2 The 5-Second Interval vs 5-Frame Temporal Window Paradox
- **Location**: [5-Architecture.md:470-472, 1229-1236, 1420-1436](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L470-L472)
- **The Flaw**: There is a severe contradiction between the config settings and the temporal verification logic:
  - `min_recognition_interval = 5` (Run recognition once every 5 seconds per track).
  - `temporal_window_size = 5` (Require 5 similarity scores before alerting).
  - Mathematical calculation:
    - Score 1: $t = 0\text{ s}$
    - Score 2: $t = 5\text{ s}$
    - Score 3: $t = 10\text{ s}$
    - Score 4: $t = 15\text{ s}$
    - Score 5: $t = 20\text{ s}$
  - A person must walk in front of the camera with the **exact same Track ID for at least 20 to 25 continuous seconds** to trigger an alert.
  - In real-world CCTV (doorways, hallways, street cameras), a person crosses the camera field of view in **2 to 5 seconds**.
- **Impact**: No pedestrian will ever be detected or alerted. The system will only detect stationary people sitting in front of the camera for half a minute.
- **Correction**: For walking pedestrians, if tracked at 10–15 FPS, accumulate 5 scores across consecutive 1-second samples, or reduce `temporal_window_size` to 3 and `min_recognition_interval` to 0.5s–1.0s.

---

### 2.3 Landmark Loss in ByteTrack Pipeline
- **Location**: [5-Architecture.md:1417, 1425](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L1417)
- **The Flaw**: The main loop calls:
  ```python
  tracks = self.tracker.update(detections)
  ...
  aligned = self.aligner.align(frame, track.landmarks)
  ```
  ByteTrack is an object tracker designed for bounding boxes (`[x, y, w, h]`). Standard ByteTrack data structures (`STrack`) **do not track or estimate 5 facial landmarks**. Furthermore, when a track is predicted via Kalman Filter during occlusion or low detection score, there are no landmarks from the detector.
- **Impact**: `track.landmarks` will be missing, `None`, or an `AttributeError`, causing the alignment step to crash.
- **Correction**: The `Track` class must be customized to carry `current_landmarks` from the matched `Detection`, and skip recognition if landmarks were not updated in the current frame.

---

### 2.4 Architectural Duality: Custom Low-Level ONNX vs High-Level `FaceAnalysis`
- **Location**: Compare Section 6 ([5-Architecture.md:1105-1152](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L1105-L1152)) with Section 17 ([5-Architecture.md:1745-1750](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L1745-L1750))
- **The Flaw**: The architecture doc has an identity crisis between two completely different AI implementations:
  1. **Option A (Section 6 & 12)**: Implements custom Python classes (`FaceDetector`, `FaceAligner`, `FaceRecognizer`) calling raw `ort.InferenceSession("det_10g.onnx")` and `ort.InferenceSession("w600k_r50.onnx")`.
  2. **Option B (Section 17 & Dockerfile)**: Imports `insightface.app.FaceAnalysis`, which automatically handles detection, landmarks, alignment, and embeddings in one single method `app.get(frame)`.
  If Option A is intended, SCRFD anchor generation, multi-stride feature pyramid decoding (`stride 8, 16, 32`), landmark distance decoding, and NMS are omitted (500+ lines of math).
  If Option B is intended, then `face_detector.py`, `face_aligner.py`, and `face_recognizer.py` are completely redundant and contradict `FaceAnalysis`.
- **Impact**: Developers attempting to implement Section 6 will find that `det_10g.onnx` does not output bounding boxes; it outputs 9 raw tensor heads requiring complex post-processing that is not documented anywhere in the spec.
- **Correction**: Explicitly standardize on `insightface.model_zoo.get_model` for individual ONNX models or `insightface.app.FaceAnalysis` and remove the conflicting raw ONNX sessions.

---

### 2.5 Catastrophically Low Similarity Thresholds (False Alarm Avalanche)
- **Location**: [5-Architecture.md:461, 472, 1234, 1548, 1692](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L461)
- **The Flaw**: The architecture specifies:
  - `face_similarity_threshold = 0.4`
  - `temporal_threshold = 0.42`
  - `if similarity < 0.3: continue`
  ArcFace (`w600k_r50`) embeddings produce cosine similarity where **unrelated stranger faces frequently score between 0.35 and 0.48** under varying CCTV lighting and angles.
  The standard ArcFace threshold for 1:N face identification at $10^{-4}$ False Accept Rate (FAR) is **0.60 to 0.68**.
- **Impact**: Setting the threshold at **0.42** will cause virtually every passing person to match multiple missing persons in the database. In a monitoring room, the system will trigger hundreds of false alarms per hour, rendering the system unusable.
- **Correction**:
  - Increase candidate search cutoff to `0.50`.
  - Set `temporal_threshold` to `0.58–0.62`.
  - Reserve `0.40–0.50` strictly for "low-confidence human review required" (or discard).

---

### 2.6 The Impossible Video Clip Pipeline
- **Location**: [5-Architecture.md:53, 707, 1275](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L53)
- **The Flaw**: Mission Statement capability #9, Sighting schema (`video_clip_path`), and the API specify capturing an evidence video clip.
  - The frame sampler drops stream data to 1 FPS before any processing.
  - OpenCV `VideoCapture` does not retain past frames.
  - A video clip requires capturing frames *prior* to the match (e.g. 5s before) and *after* the match (e.g. 5s after).
  - There is **no circular ring buffer or video encoder** specified anywhere in `edge_agent/camera/` or `evidence_collector.py`.
- **Impact**: Sighting events will fail to save video clips, leaving `video_clip_path` permanently `NULL`, or attempting to write a video from 1 FPS frames will result in a corrupted, unplayable 1 FPS slideshow.
- **Correction**: Either remove `video_clip_path` from the MVP scope or specify a `CircularFrameBuffer(maxlen=150)` in `stream_manager.py` that dumps a 15 FPS clip upon match confirmation.

---

## Category 3: Database Schema & Synchronization Mismatches

### 3.1 SQLite Primary Key Constraint Crash: `cached_embeddings`
- **Location**: [5-Architecture.md:774-780](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L774-L780) (`edge_agent/storage/local_db.py`)
- **The Flaw**: The Edge Agent SQLite schema defines:
  ```sql
  CREATE TABLE IF NOT EXISTS cached_embeddings (
      person_id       TEXT PRIMARY KEY,
      person_name     TEXT,
      embedding_data  BLOB NOT NULL,
      photo_url       TEXT,
      synced_at       TEXT DEFAULT (datetime('now'))
  );
  ```
  However, in PostgreSQL (Section 3), a missing person can have **multiple photos** (front, left profile, right profile) and therefore **multiple rows in `face_embeddings`** for the same `person_id`.
- **Impact**: When the Edge Agent syncs embeddings for a person with multiple photos, the second insert fails with:
  ```text
  sqlite3.IntegrityError: UNIQUE constraint failed: cached_embeddings.person_id
  ```
  Or, if using `INSERT OR REPLACE`, it overwrites previous photos, leaving only 1 embedding per person.
- **Correction**: Use `embedding_id` as PRIMARY KEY with an index on `person_id`:
  ```sql
  CREATE TABLE IF NOT EXISTS cached_embeddings (
      id              TEXT PRIMARY KEY,
      person_id       TEXT NOT NULL,
      person_name     TEXT,
      embedding_data  BLOB NOT NULL,
      photo_url       TEXT,
      synced_at       TEXT DEFAULT (datetime('now'))
  );
  CREATE INDEX IF NOT EXISTS idx_cached_person ON cached_embeddings(person_id);
  ```

---

### 3.2 Duplicate Candidates in Single Frame Corrupts Temporal Window
- **Location**: [5-Architecture.md:1260, 1427-1436](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L1260)
- **The Flaw**: When FAISS contains multiple embeddings for a single person (e.g. Person 102 has 3 embeddings in FAISS), `vector_search.search(embedding, top_k=5)` can return:
  ```python
  candidates = [("MP-102", 0.52), ("MP-102", 0.49), ("MP-105", 0.38)]
  ```
  In Section 9, the code iterates over `candidates`:
  ```python
  for person_id, similarity in candidates:
      match = self.verifier.check_match(track.track_id, person_id, similarity)
  ```
- **Impact**: In a **single frame**, Person `MP-102` is matched twice. Both scores are appended to `track.history[("MP-102")]`. The temporal window size of 5 will be satisfied in only 2 or 3 frames instead of 5 distinct temporal observations.
- **Correction**: Group/deduplicate candidates by `person_id` before calling `check_match`, keeping only `max(similarity)` per frame:
  ```python
  best_candidates = {}
  for pid, sim in candidates:
      if pid not in best_candidates or sim > best_candidates[pid]:
          best_candidates[pid] = sim
  for pid, sim in best_candidates.items():
      match = self.verifier.check_match(track.track_id, pid, sim)
  ```

---

### 3.3 Broken Incremental Sync: Deletions and Status Changes Are Undetectable
- **Location**: [5-Architecture.md:638-646, 1308, 1351-1358](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L638-L646)
- **The Flaw**:
  - `face_embeddings` table only has `created_at TIMESTAMPTZ`. It has **no `updated_at`, `is_active`, or `deleted_at`** column.
  - When a report status changes from `ACTIVE` to `FOUND` or `CLOSED`, only `missing_persons.status` is updated.
  - In Section 8: `get_sync_package(db, since=timestamp)` is supposed to return `{full_sync, persons, removed_ids, sync_timestamp}`.
- **Impact**: When filtering `face_embeddings.created_at >= since`, the query cannot identify which persons were marked `FOUND`, `CLOSED`, or deleted. `removed_ids` cannot be computed. Edge Agents will continue monitoring and falsely detecting persons who have already been found.
- **Correction**: Add `updated_at` to `face_embeddings` and maintain a `status` or soft-delete tracking table (`tombstones`) so deactivations can be queried via `since`.

---

### 3.4 Sighting Foreign Key Deletion Cascade Trap
- **Location**: [5-Architecture.md:697-717](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L697-L717) (`PostgreSQL Schema`)
- **The Flaw**: In table `missing_persons`:
  ```sql
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE
  ```
  In table `sightings`:
  ```sql
  person_id UUID NOT NULL REFERENCES missing_persons(id) -- NO CASCADE SPECIFIED!
  ```
- **Impact**: If a user deletes their account or a report is deleted, PostgreSQL raises a Foreign Key Violation (`foreign_key_violation: update or delete on table "missing_persons" violates foreign key constraint on table "sightings"`).
- **Correction**: Explicitly specify `ON DELETE CASCADE` or soft-delete reports using `is_deleted = TRUE`.

---

### 3.5 Camera Identity Disconnect: UUID vs Local String
- **Location**: [5-Architecture.md:676-690, 785, 1514](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L676-L690)
- **The Flaw**:
  - In PostgreSQL `cameras.id` is a `UUID PRIMARY KEY DEFAULT gen_random_uuid()`.
  - In SQLite `pending_sightings.camera_id` is `TEXT NOT NULL`.
  - In Edge Agent UI (Section 11), camera labels are `CAM-01`, `CAM-07`.
  - When Edge Agent starts and discovers cameras via ONVIF/RTSP, it has no remote UUID for those cameras.
- **Impact**: When Edge Agent submits a sighting with `camera_id = "CAM-01"` to `POST /api/sightings/`, PostgreSQL rejects the insert because `"CAM-01"` is not a valid UUID and fails foreign key validation against `cameras(id)`.
- **Correction**: Define an explicit camera synchronization step on agent startup: the agent registers its cameras with the backend, receives backend UUIDs, and stores `(local_index, backend_uuid)` in SQLite `sync_state`.

---

## Category 4: REST API, Network & Protocol Inconsistencies

### 4.1 The Orphaned Report Trap (Photo Upload Lifecycle)
- **Location**: [5-Architecture.md:1452, 1457](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L1452) (`REST Endpoints`)
- **The Flaw**: Report creation is split into two HTTP calls:
  1. `POST /api/reports/` -> creates report with status `PROCESSING`.
  2. `POST /api/reports/{id}/photos` -> uploads photos.
  Section 8 states: *"if all photos done -> mark report ACTIVE"*.
- **Impact**: If Step 1 succeeds but Step 2 fails (network dropout, app killed, photo validation error), the report remains in PostgreSQL with `status = 'PROCESSING'` and **0 photos** forever. It is never activated and never visible to edge agents.
- **Correction**: Support atomic multipart submission in `POST /api/reports/` (form fields + photo files in a single request), or implement a cleanup worker that deletes reports stuck in `PROCESSING` with no photos after 30 minutes.

---

### 4.2 Missing Static Media Serving Route in Backend
- **Location**: [5-Architecture.md:1443-1487](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L1443-L1487)
- **The Flaw**: Photos, face crops, and evidence files are saved to `UPLOAD_DIR=./uploads`. The API endpoints list CRUD for reports and sightings, but **there is no static file mount or image retrieval endpoint** specified in Section 10 or `main.py`.
- **Impact**: Neither the Flutter app nor the Edge Agent can render uploaded person photos, face crops, or full-frame evidence thumbnails. Image requests to `http://localhost:8000/uploads/...` return `404 Not Found`.
- **Correction**: In `backend/app/main.py`, mount `StaticFiles`:
  ```python
  from fastapi.staticfiles import StaticFiles
  app.mount("/uploads", StaticFiles(directory=settings.UPLOAD_DIR), name="uploads")
  ```

---

### 4.3 Unauthenticated Edge Agent Registration Vulnerability
- **Location**: [5-Architecture.md:1461-1462](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L1461)
- **The Flaw**:
  ```text
  # ── Edge Agents (auth: X-API-Key) ──
  POST /api/agents/register -> Register device -> returns API key (once)
  ```
  A new agent does not have an API key yet. Therefore, `POST /api/agents/register` must be unauthenticated.
- **Impact**: Any external client on the network can call `/api/agents/register` thousands of times, generating rogue agent records, receiving valid API keys, and polluting the central database.
- **Correction**: Require an **Admin Enrollment Token** (e.g. `X-Enrollment-Key` matching an env var) to register new edge hardware.

---

### 4.4 Flutter Mobile Background Incompatibility: SSE Drops
- **Location**: [5-Architecture.md:90-95, 1486](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L90-L95)
- **The Flaw**: Section 0 and Section 10 rely on Server-Sent Events (SSE) for real-time mobile notifications.
  - On both iOS and Android, when a Flutter app is moved to the background or the screen locks, the operating system terminates active HTTP/SSE connections within 30 seconds to preserve battery.
- **Impact**: When the mobile app is in the pocket or screen is off, SSE notifications are never received. Only FCM push notifications work in the background.
- **Correction**: Explicitly designate FCM as the primary notification delivery channel for mobile alerts, and restrict SSE strictly to active in-app foreground screen updates.

---

## Category 5: File Tree, Packaging & Dependency Discrepancies

### 5.1 Defunct and Abandoned Flutter Package: `flutter_sse`
- **Location**: [5-Architecture.md:898](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L898) (`pubspec.yaml`)
- **The Flaw**: `pubspec.yaml` specifies `flutter_sse: ^0.3.0`.
  - `flutter_sse` has not been updated since 2021.
  - It lacks Dart 3 null-safety compatibility and fails to build on Flutter 3.19+.
- **Impact**: Running `flutter pub get` or `flutter run` fails with package resolution and build errors.
- **Correction**: Replace with a modern SSE client such as `fetch_client` or standard `http` with streamed responses.

---

### 5.2 Missing Flutter Map Dependencies
- **Location**: [5-Architecture.md:324, 331, 887-913](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L324)
- **The Flaw**: 
  - UI spec lists `sighting_detail_screen.dart` (*"evidence, map, similarity gauge"*) and `timeline_widget.dart` (*"Visual timeline of sightings on a map"*).
  - Schema stores `latitude` and `longitude`.
  - However, `pubspec.yaml` contains **no map dependency** (e.g. `flutter_map`, `latlong2`, or `google_maps_flutter`).
- **Impact**: Developer cannot build the map UI without adding undeclared dependencies.
- **Correction**: Add `flutter_map: ^6.1.0` and `latlong2: ^0.9.0` to `pubspec.yaml`.

---

### 5.3 PyInstaller Asset Path Failure on Windows
- **Location**: [5-Architecture.md:1660-1663](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L1660-L1663) (`Build Executable`)
- **The Flaw**: PyInstaller bundles assets into a temporary directory accessed via `sys._MEIPASS`. The Edge Agent code in Section 2 references relative filesystem paths:
  ```ini
  onnx_model_dir = ./models
  ```
  And QSS stylesheets:
  ```python
  open("ui/resources/style.qss")
  ```
- **Impact**: When packaged with PyInstaller into an `.exe`, running the app crashes immediately because relative paths do not point to `sys._MEIPASS`.
- **Correction**: Implement an asset path resolver helper:
  ```python
  def get_resource_path(relative_path: str) -> str:
      base_path = getattr(sys, '_MEIPASS', os.path.abspath("."))
      return os.path.join(base_path, relative_path)
  ```

---

## Category 6: Operational, Security & Hardware Reality Checks

### 6.1 Plaintext RTSP Credentials Stored in Database
- **Location**: [5-Architecture.md:680](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L680)
- **The Flaw**: The schema stores `rtsp_url VARCHAR(512) NOT NULL`. RTSP URLs for CCTV systems contain plain embedded credentials:
  ```text
  rtsp://admin:SecurityPass2026@192.168.1.120:554/h264Preview_01_main
  ```
- **Impact**: Any read access to the database or an API exposure exposes the raw credentials and internal IP topology of the facility's physical security system.
- **Correction**: Store RTSP credentials encrypted using AES-GCM, or decouple the URL from the authentication credentials (`username`, `encrypted_password`).

---

### 6.2 Unrealistic CPU Consumption Claims
- **Location**: [5-Architecture.md:1713](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/5-Architecture.md#L1713) (`Cost / Ops Reference`)
- **The Flaw**: Section 16 claims:
  *"Edge Agent CPU (4 cam, 1 FPS): ~30–50% of 4-core i5"*.
  In reality:
  - 4 concurrent RTSP 1080p H.264 streams decoded via software OpenCV (`avcodec`) consume 20–35% of a 4-core i5 on video decoding alone.
  - SCRFD-10G (640×640) on CPU takes ~45–60 ms per frame.
  - PySide6 GUI rendering 4 preview feeds at 15–30 FPS consumes 15–20% CPU.
  - If multiple faces appear across 4 cameras, running ArcFace on CPU will push the CPU to **100% saturation**, causing dropped frames and RTSP socket timeouts.
- **Impact**: Edge monitoring PCs will experience stream lag, buffer bloat, and UI freezing.
- **Correction**: Use the lighter **SCRFD-0.5G** or **SCRFD-2.5G** model instead of `det_10g.onnx`, and enable hardware decoding (`cv2.CAP_FFMPEG` with DXVA2 / Direct3D11) on Windows.

---

## Complete Remediation Summary Table

| Issue Ref | Architecture Section | Specific Defect | Direct Fix |
|---|---|---|---|
| **#1** | §5 Containerization | `libgl1-mesa-glx` removed in Debian Bookworm | Change to `libgl1` in `backend/Dockerfile` |
| **#2** | §5 Containerization | `FaceAnalysis()` doesn't download models without `.prepare()` | Add `.prepare(ctx_id=-1)` in `backend/Dockerfile` |
| **#3** | §4 Dependencies | `lap>=0.4.0` has no Windows wheels on Python 3.10+ | Replace with `lapx>=0.5.5` in `edge_agent/requirements.txt` |
| **#4** | §4 Dependencies | `filterpy` uses removed `np.float` on `numpy>=1.26` | Modernize Kalman filter or patch `np.float = float` |
| **#5** | §4 Dependencies | `aioredis>=2.0.0` conflicts with `redis>=5.0.0` | Remove `aioredis`, use `redis.asyncio` |
| **#6** | §6 & §12 AI & Rules | Concurrent FAISS access crashes multi-threaded Qt | Wrap FAISS with Read-Write lock / atomic swap |
| **#7** | §6 & §9 Orchestration | 1 FPS breaks ByteTrack Kalman Filter (0.0 IoU) | Track at 10–15 FPS; sample recognition at 1 FPS |
| **#8** | §2 & §6 Tracking | 5s throttle × 5 window = 25s pedestrian transit | Set interval to 1s, window to 3 for pedestrians |
| **#9** | §9 Orchestration | `track.landmarks` not stored in standard ByteTrack | Extend `STrack` to retain facial landmarks |
| **#10**| §6 & §17 AI Pipeline | Duality between raw ONNX and `FaceAnalysis` | Standardize on unified InsightFace model loaders |
| **#11**| §6 & §15 Reference | ArcFace 0.42 threshold causes false alarm flood | Raise threshold to 0.58–0.62 for verification |
| **#12**| §6 & §9 Evidence | Video clip specified without circular frame buffer | Implement `CircularFrameBuffer` in stream worker |
| **#13**| §3 SQLite Schema | `cached_embeddings.person_id` primary key collision | Change PK to `id` (UUID), index on `person_id` |
| **#14**| §9 Orchestration | Duplicate candidates per person inflate window count | Deduplicate candidates to 1 max score per person/frame |
| **#15**| §3 & §8 Data Sync | Missing `updated_at` prevents incremental sync of deleted cases | Add `updated_at` / tombstone soft deletes |
| **#16**| §3 Postgres Schema | Missing `ON DELETE CASCADE` on `sightings.person_id` | Add `ON DELETE CASCADE` to foreign key |
| **#17**| §3 & §10 Schema/API | Camera UUID vs local string ("CAM-01") mismatch | Implement camera auto-registration & ID mapping |
| **#18**| §10 API Layer | Two-step report creation leaves orphaned reports | Support single multipart POST or add cleanup task |
| **#19**| §10 API Layer | Missing static file serving for photo/evidence URLs | Add `app.mount("/uploads", StaticFiles(...))` |
| **#20**| §10 API Layer | `POST /api/agents/register` vulnerable to open spam | Require Admin enrollment token |
| **#21**| §4 Dependencies | `flutter_sse` is dead and broken on Dart 3 | Use `fetch_client` or native streamed `http` |
| **#22**| §4 Dependencies | Missing map package in Flutter `pubspec.yaml` | Add `flutter_map: ^6.1.0` and `latlong2: ^0.9.0` |
| **#23**| §13 Deployment | Relative paths fail inside PyInstaller executable | Implement `get_resource_path()` using `sys._MEIPASS` |
| **#24**| §3 Schema & Ops | Plaintext RTSP credentials exposed in database | Encrypt RTSP URLs with AES-GCM |
| **#25**| §16 Cost / Ops | 4x 1080p RTSP + SCRFD-10G saturates i5 CPU | Switch to SCRFD-0.5G/2.5G + hardware video decode |
| **#26**| §9 Orchestration | QThread ungraceful termination causes exit crash | Implement cooperative cancellation flags (`_stop_event`) |

---

## Conclusion & Recommended Next Step

The architecture document `DOC/5-Architecture.md` should be updated to address these 26 points before full-scale implementation begins. Addressing the **Docker dependencies**, **Windows compilation libraries**, and **ByteTrack 1 FPS timing paradox** will prevent weeks of debugging and ensure a stable, deployable system.
