# FIND-MISSING-PEP: Edge CCTV Agent — Deployment & Setup Guide

This guide explains how to start and run the **Edge CCTV Monitoring Agent** on **any computer** (Windows, Linux, or macOS).

---

## What is the Edge Agent?
The Edge Agent is a desktop AI surveillance application that connects local CCTV streams, USB webcams, or ONVIF IP cameras to the **FIND-MISSING-PEP** production cloud (`http://65.20.80.222:8000`).

It runs on-device:
- **YOLOv8 + ByteTrack**: Real-time multi-person detection and trajectory tracking.
- **ArcFace (512-D)**: Biometric face recognition against the cloud gallery.
- **Automatic Sighting Sync**: Posts verified sightings and HD video evidence to the cloud dashboard and Android app in real time.

---

## Method 1: 1-Click Auto Installer (Recommended)

### On Windows
1. Copy or clone the project folder to the computer.
2. Ensure **Python 3.10, 3.11, or 3.12** is installed ([python.org/downloads](https://www.python.org/downloads/)).  
   *(Make sure to check the box **"Add Python to PATH"** during installation).*
3. Double-click:
   ```cmd
   install_and_run_edge.bat
   ```
4. **What this script does automatically**:
   - Detects your Python installation.
   - Creates an isolated virtual environment (`.venv`).
   - Installs all dependencies (`PySide6`, `onnxruntime`, `insightface`, `opencv`, `faiss-cpu`, etc.).
   - Connects to production cloud (`http://65.20.80.222:8000`).
   - Launches the Edge Agent GUI window!

### On Linux / Ubuntu
1. Open a terminal in the project directory.
2. Make the script executable and run:
   ```bash
   chmod +x install_and_run_edge.sh
   ./install_and_run_edge.sh
   ```

---

## Method 2: Standalone Portable Package (.EXE — No Python Needed)
If you want to run the Edge Agent on computers **without installing Python**:

1. On your development PC, run:
   ```cmd
   build_edge_exe.bat
   ```
2. This creates a standalone folder:
   ```
   dist/FindMissingPerson-Agent/
   ```
3. Zip that folder and copy it to any Windows PC via USB or network drive.
4. On the target computer, open the folder and double-click:
   ```
   FindMissingPerson-Agent.exe
   ```

---

## Configuration Settings (`edge_agent/config.ini`)

The edge agent comes pre-configured for the production cloud:

```ini
[backend]
url = http://65.20.80.222:8000
enrollment_key = your_secure_admin_enrollment_key
api_key = 6e_oD7PUWNxHIQ1uwJgunfJqbp0YfU8J2RJWl2HK5jQYKntEChsbsNjC63iiuv86

[agent]
device_id = EDGE-STATION-01
location_name = Main Entrance
sync_interval_seconds = 60
heartbeat_interval_seconds = 30
```

- To change the station name or camera location, simply edit `device_id` and `location_name`.

---

## Connecting Cameras in the Edge Agent GUI

Once the desktop window opens:
1. **USB Webcam**: Enter `0` or `1` in the Camera URL field and click **Start Feed**.
2. **IP CCTV Camera (RTSP)**: Enter your RTSP stream URL:
   ```
   rtsp://admin:password@192.168.1.100:554/h264Preview_01_main
   ```
3. **ONVIF Discovery**: Click **Discover Cameras** to automatically detect ONVIF-compatible CCTV cameras on your local network.
4. **Test Video File**: You can also enter the path to any local `.mp4` video to simulate a CCTV feed.
