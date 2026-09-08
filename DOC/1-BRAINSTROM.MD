Yes. Based on our whole discussion, the project can be defined as **one complete platform with three connected components**.

# AI-Based Missing Person Detection & Real-Time CCTV Monitoring System

### Core idea

A person reports someone missing through the **Flutter application**. Their photographs are converted into a searchable face representation. The **AI CCTV Edge Agent**, installed on a CCTV monitoring-room computer, connects to the organization's existing CCTV cameras and continuously analyzes live video. If a possible match is detected, the system records the camera, location, time, and evidence, then sends an alert to the user/operator.

---

# 1. Complete project workflow

```text
                    ┌──────────────────────┐
                    │      USER            │
                    │   Flutter App        │
                    └──────────┬───────────┘
                               │
                               │ Report Missing Person
                               ▼
                    ┌──────────────────────┐
                    │     BACKEND API      │
                    │       FastAPI        │
                    └──────────┬───────────┘
                               │
                    Process uploaded photos
                               │
                               ▼
                    ┌──────────────────────┐
                    │   FACE PROCESSING    │
                    │ Detection + Alignment│
                    │    + Embedding       │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │ Missing Person DB    │
                    │ + Face Embeddings    │
                    └──────────┬───────────┘
                               │
                     Active cases sync
                               │
                               ▼
       ╔════════════════════════════════════════════╗
       ║          CCTV MONITORING ROOM              ║
       ║                                            ║
       ║   Existing CCTV / NVR                     ║
       ║          │                                 ║
       ║       RTSP / ONVIF                         ║
       ║          │                                 ║
       ║          ▼                                 ║
       ║   ┌─────────────────────────────┐          ║
       ║   │    AI CCTV EDGE AGENT       │          ║
       ║   │                             │          ║
       ║   │ Video Ingestion             │          ║
       ║   │       ↓                     │          ║
       ║   │ Face Detection              │          ║
       ║   │       ↓                     │          ║
       ║   │ Tracking                    │          ║
       ║   │       ↓                     │          ║
       ║   │ Face Quality Check           │          ║
       ║   │       ↓                     │          ║
       ║   │ Face Recognition             │          ║
       ║   │       ↓                     │          ║
       ║   │ Vector Search                │          ║
       ║   │       ↓                     │          ║
       ║   │ Temporal Verification        │          ║
       ║   │       ↓                     │          ║
       ║   │ Possible Match               │          ║
       ║   └──────────────┬──────────────┘          ║
       ╚══════════════════╪═════════════════════════╝
                          │
                          │ Match Event
                          ▼
                 ┌───────────────────┐
                 │    BACKEND API     │
                 └─────────┬─────────┘
                           │
                    Save sighting
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
       Operator Dashboard         Notification
                                      │
                                      ▼
                               Flutter App
                                      │
                                      ▼
                              🚨 Possible Match
```

---

# 2. The project has 3 main applications/components

## Component 1 — Flutter Application

This is for the **general user/reporting person**.

### User workflow

```text
Open App
   ↓
Login/Register
   ↓
Home
   ↓
Report Missing Person
   ↓
Enter information
   ↓
Upload photos
   ↓
Submit Report
   ↓
Backend processes photos
   ↓
Report becomes ACTIVE
   ↓
Wait for possible sightings
   ↓
Receive notification
   ↓
View sighting
   ↓
View location/time/evidence
```

### Main screens

```text
Login
   │
   ├── Home
   │
   ├── Report Missing Person
   │
   ├── My Reports
   │
   ├── Possible Sightings
   │
   ├── Notifications
   │
   └── Profile
```

---

# 3. Component 2 — Backend

The backend is the **brain connecting the mobile application and CCTV agents**.

Recommended:

**Python + FastAPI + PostgreSQL**

It manages:

```text
Users
Missing-person reports
Photos
Face embeddings
CCTV cameras
Edge agents
Sightings
Match events
Evidence
Notifications
Audit logs
```

### Backend workflow

```text
Flutter
   ↓
FastAPI
   ↓
Validate report
   ↓
Store photos
   ↓
Face processing
   ↓
Generate embeddings
   ↓
Store embeddings
   ↓
Mark report ACTIVE
```

The backend also manages communication with all deployed CCTV Edge Agents.

---

# 4. Component 3 — AI CCTV Edge Agent

This is the most important part of your product.

It is a **desktop application installed in an existing CCTV monitoring room**.

The organization doesn't need to replace its CCTV system.

### Operator workflow

```text
Download AI CCTV Agent
        ↓
Install
        ↓
Login/Register Device
        ↓
Automatically discover CCTV/NVR
        ↓
Select cameras
        ↓
Configure location
        ↓
Test cameras
        ↓
Start AI Monitoring
        ↓
AI continuously runs
```

After setup, the operator shouldn't need to manually interact with the AI every minute.

---

# 5. What happens inside the Edge Agent?

Suppose there are 16 CCTV cameras.

```text
Camera 01 ─┐
Camera 02 ─┤
Camera 03 ─┤
Camera 04 ─┤
Camera ... ─┤
Camera 16 ─┘
       │
       ▼
AI CCTV Edge Agent
```

Each stream goes through:

```text
Live CCTV
    ↓
Video Decoder
    ↓
Frame Sampling
    ↓
Face Detection
    ↓
Person/Face Tracking
    ↓
Face Quality Check
    ↓
Face Alignment
    ↓
Face Recognition Model
    ↓
Face Embedding
    ↓
Vector Similarity Search
    ↓
Candidate Ranking
    ↓
Temporal Verification
    ↓
Possible Match
```

---

# 6. AI model architecture

We should **not use OpenCV as the entire AI system**.

OpenCV is mainly useful for:

* Video capture
* Video decoding
* Image processing
* Frame handling

For the actual AI pipeline, our planned baseline is:

```text
                    CCTV Frame
                        ↓
                  Face Detector
                  SCRFD / similar
                        ↓
                  Face Alignment
                        ↓
                ArcFace Recognition
                        ↓
                  Face Embedding
                        ↓
                  Vector Search
                        ↓
                   Candidate
```

For tracking, evaluate something such as:

**ByteTrack / BoT-SORT / DeepSORT**

For vector search:

**FAISS initially**, potentially **Qdrant** for a more production-oriented deployment.

---

# 7. Why tracking is important

We don't want:

```text
25 FPS
×
Every face
×
Every missing person
```

That can become expensive very quickly.

Instead:

```text
Face detected
      ↓
Track ID = 123
      ↓
Track person across frames
      ↓
Run recognition periodically
      ↓
Collect several similarity scores
      ↓
Make one decision
```

Example:

```text
Track #123

Frame 1 → 91%
Frame 2 → 89%
Frame 3 → 93%
Frame 4 → 90%
Frame 5 → 92%

        ↓

Temporal verification

        ↓

Possible Match
```

This should reduce false alerts compared with relying on a single frame.

---

# 8. Very important: don't alert from one frame

Your system should **not** work like:

```text
One frame
   ↓
91%
   ↓
PERSON FOUND!
```

Instead:

```text
Detection
   ↓
Track
   ↓
Multiple frames
   ↓
Multiple similarity scores
   ↓
Temporal aggregation
   ↓
Possible match
   ↓
Human review
```

The system should say:

> **Possible Match**

not:

> **Person Definitely Found**

until an authorized human verifies it.

---

# 9. What information is generated when there is a match?

Suppose:

```text
Missing Person:
MP-102
```

Camera detects the person.

The Edge Agent creates:

```text
Sighting ID: S-9281

Person: MP-102
Camera: CAM-07
Location: Main Gate
Time: 14:32:17
Similarity: 92%

Evidence:
- Face image
- Optional short video
```

Then:

```text
Edge Agent
     ↓
Backend API
     ↓
Database
     ↓
Notification
```

---

# 10. What the CCTV operator sees

The application should show something like:

```text
╔══════════════════════════════════════╗
║ 🚨 POSSIBLE MATCH                    ║
╠══════════════════════════════════════╣
║ Missing Person: MP-102               ║
║ Camera: CAM-07                       ║
║ Location: Main Gate                  ║
║ Time: 14:32:17                       ║
║ Similarity: 92%                      ║
║                                      ║
║ [ Evidence Image ]                   ║
║                                      ║
║ [ CONFIRM ]       [ REJECT ]         ║
╚══════════════════════════════════════╝
```

---

# 11. User receives the result

Flutter:

```text
🚨 Possible Sighting

A possible match for your
missing-person report was detected.

Camera:
CAM-07

Location:
Main Gate

Time:
2:32 PM

[View Evidence]
```

If the operator confirms:

```text
Operator
    ↓
Confirm
    ↓
Backend
    ↓
Flutter
    ↓
Confirmed notification
```

---

# 12. The "last seen" feature

This can become one of your strongest features.

Suppose the person appears on:

```text
CAM-01 → 10:30
CAM-03 → 10:42
CAM-08 → 10:51
CAM-12 → 11:05
```

Your system can generate:

```text
MISSING PERSON TIMELINE

10:30
CAM-01
   ↓
10:42
CAM-03
   ↓
10:51
CAM-08
   ↓
11:05
CAM-12

Last possible sighting:
CAM-12
11:05 AM
```

So you're not just building **face recognition**.

You're building a:

> **Real-time missing-person sighting and tracking platform across connected CCTV cameras.**

---

# 13. Why Edge AI?

Instead of:

```text
CCTV
 ↓
Internet
 ↓
Cloud
 ↓
AI
```

we want:

```text
CCTV
 ↓
LOCAL EDGE AGENT
 ↓
AI processing
 ↓
Only possible events
 ↓
Backend
```

Advantages:

* Lower latency
* Less bandwidth
* Better operation during temporary internet outages
* More local processing
* Easier scaling
* Better privacy architecture

The Edge Agent can continue processing locally if the backend temporarily becomes unavailable, then synchronize events later.

---

# 14. Installation should be extremely simple

The entire CCTV-side experience should be:

```text
                    INSTALL
                       ↓
                     LOGIN
                       ↓
              DISCOVER CAMERAS
                       ↓
                SELECT CAMERAS
                       ↓
              CONFIGURE LOCATION
                       ↓
                TEST CONNECTION
                       ↓
               START AI MONITORING
                       ↓
              ┌─────────────────┐
              │   AI RUNNING    │
              │     24 / 7      │
              └─────────────────┘
```

This is why we chose the **standalone Edge Agent** approach instead of trying to build a plugin separately for every CCTV/VMS vendor.

---

# 15. Technology stack

### Mobile

```text
Flutter
Dart
Push Notifications
```

### Backend

```text
Python
FastAPI
PostgreSQL
Redis/background workers where needed
```

### AI

```text
Python
PyTorch
InsightFace / ArcFace
OpenCV
ONNX Runtime
TensorRT where appropriate
```

### Tracking

```text
ByteTrack / BoT-SORT
```

### Vector search

```text
FAISS → MVP
Qdrant → production candidate
```

### CCTV

```text
RTSP
ONVIF
NVR/DVR integration
```

### Deployment

```text
Windows Edge Agent initially
Docker for backend
GPU acceleration where available
```

---

# 16. Development order

Don't build everything at once.

### Phase 1 — Face recognition

```text
Image A
   ↓
Embedding

Image B
   ↓
Embedding

Embedding A ↔ Embedding B
   ↓
Similarity
```

First prove that recognition works.

---

### Phase 2 — Real-time CCTV

```text
CCTV video
   ↓
Face detection
   ↓
Tracking
   ↓
Recognition
   ↓
Possible match
```

Use recorded CCTV footage first.

---

### Phase 3 — Edge Agent

Turn the prototype into:

```text
Installable Windows application
```

with:

* Camera discovery
* Camera configuration
* Live monitoring
* AI status
* Alerts
* Logs
* Automatic restart

---

### Phase 4 — Backend

Connect:

```text
Edge Agent
      ↕
FastAPI
      ↕
PostgreSQL
```

---

### Phase 5 — Flutter

Connect:

```text
Flutter
   ↕
FastAPI
   ↕
AI Edge Agents
```

---

### Phase 6 — Real-time integration

Complete:

```text
User report
   ↓
Face embedding
   ↓
CCTV Edge Agent
   ↓
Real-time detection
   ↓
Match
   ↓
Evidence
   ↓
Backend
   ↓
Notification
```

---

# 17. Research/Paper direction

After the application works, don't make the paper simply:

> "We developed a missing-person application."

The research should investigate something measurable.

A strong direction is:

### **Temporal Face Matching for Real-Time Missing-Person Detection in CCTV Networks**

Compare:

```text
Single-frame matching
        VS
Track-level / temporal matching
```

Measure:

* Precision
* Recall
* F1
* False Acceptance Rate
* False Rejection Rate
* Recognition accuracy
* FPS
* GPU/CPU usage
* End-to-end alert latency

You can also compare different face-recognition models and evaluate difficult CCTV conditions such as low resolution, blur, lighting changes, side faces and occlusion.

---

# 18. Final project architecture

This is the version I would use as the **master architecture**:

```text
                     ┌─────────────────────┐
                     │     FLUTTER APP     │
                     │                     │
                     │ Report Missing      │
                     │ Upload Photos       │
                     │ View Sightings      │
                     │ Notifications       │
                     └──────────┬──────────┘
                                │
                              HTTPS
                                │
                                ▼
                     ┌─────────────────────┐
                     │     FASTAPI         │
                     │     BACKEND         │
                     │                     │
                     │ Users               │
                     │ Reports             │
                     │ Embeddings          │
                     │ Cameras             │
                     │ Sightings            │
                     │ Notifications       │
                     └───────┬─────┬───────┘
                             │     │
                     Embeddings   Events
                             │     │
                             ▼     ▲
                       ┌───────────────┐
                       │ Vector Search │
                       └───────────────┘
                             ▲
                             │
              ┌──────────────┴──────────────┐
              │                             │
              │     CCTV MONITORING ROOM    │
              │                             │
              │ Existing CCTV / NVR         │
              │        │                    │
              │     RTSP/ONVIF              │
              │        ↓                    │
              │ ┌────────────────────────┐  │
              │ │   AI CCTV EDGE AGENT   │  │
              │ │                        │  │
              │ │ Stream Manager         │  │
              │ │       ↓                │  │
              │ │ Face Detection         │  │
              │ │       ↓                │  │
              │ │ Tracking               │  │
              │ │       ↓                │  │
              │ │ Quality Filter         │  │
              │ │       ↓                │  │
              │ │ ArcFace                │  │
              │ │       ↓                │  │
              │ │ Vector Search          │  │
              │ │       ↓                │  │
              │ │ Temporal Verification  │  │
              │ │       ↓                │  │
              │ │ Possible Match         │  │
              │ │       ↓                │  │
              │ │ Evidence Capture       │  │
              │ └───────────┬────────────┘  │
              │             │               │
              │        Operator Review      │
              └─────────────┼───────────────┘
                            │
                            ▼
                     POSSIBLE SIGHTING
                            │
                            ▼
                       USER ALERT
```

## In one sentence

**Your project is an installable Edge-AI CCTV application that connects to existing CCTV infrastructure, continuously detects and recognizes faces against active missing-person reports, identifies possible sightings in real time, records where and when they occurred, and sends the evidence to the user through the central platform.**

That should be the **base definition of the project** going forward.
