@echo off
setlocal enabledelayedexpansion
title FIND-MISSING-PEP Edge CCTV Agent - Auto Installer & Runner

echo =====================================================================
echo    FIND-MISSING-PEP: Edge CCTV Monitoring Agent
echo    Connected to Production Cloud: http://65.20.80.222:8000
echo =====================================================================
echo.

cd /d "%~dp0"

:: 1. Detect Python Installation
set "PY_CMD="
py -3 --version >nul 2>&1 && set "PY_CMD=py -3"
if not defined PY_CMD (
    python --version >nul 2>&1 && set "PY_CMD=python"
)
if not defined PY_CMD (
    python3 --version >nul 2>&1 && set "PY_CMD=python3"
)

if not defined PY_CMD (
    echo [ERROR] Python is not installed or not in your system PATH!
    echo.
    echo Please install Python 3.10, 3.11, or 3.12 from:
    echo    https://www.python.org/downloads/
    echo.
    echo IMPORTANT: Make sure to check the box "Add Python to PATH" during installation.
    echo.
    pause
    exit /b 1
)

echo [OK] Detected Python:
%PY_CMD% --version
echo.

:: 2. Setup Virtual Environment (.venv)
if not exist ".venv\Scripts\activate.bat" (
    echo [SETUP] Creating isolated Python virtual environment (.venv)...
    %PY_CMD% -m venv .venv
    if errorlevel 1 (
        echo [ERROR] Failed to create virtual environment.
        pause
        exit /b 1
    )
    echo [OK] Virtual environment created.
)

:: Activate the venv
call .venv\Scripts\activate.bat

:: 3. Install Dependencies (if not already installed)
if not exist ".venv\installed.flag" (
    echo [SETUP] Installing required AI & CCTV packages from edge_agent/requirements.txt...
    echo (This only happens on the first run, may take a few minutes...)
    echo.
    python -m pip install --upgrade pip
    pip install -r edge_agent\requirements.txt
    if errorlevel 1 (
        echo.
        echo [WARNING] Some dependencies failed to install. Retrying critical core packages...
        pip install PySide6 onnxruntime numpy opencv-python insightface faiss-cpu httpx sseclient-py pydantic pydantic-settings pillow psutil
    )
    echo installed > .venv\installed.flag
    echo.
    echo [OK] All dependencies installed successfully!
) else (
    echo [OK] Dependencies verified.
)

:: 4. Verify config.ini exists
if not exist "edge_agent\config.ini" (
    if exist "edge_agent\config.local.ini" (
        copy /y "edge_agent\config.local.ini" "edge_agent\config.ini" >nul
        echo [OK] Created edge_agent\config.ini from local template.
    )
)

:: 5. Launch Edge CCTV Agent GUI
echo.
echo =====================================================================
echo    Launching FIND-MISSING-PEP Edge CCTV Agent Desktop GUI...
echo =====================================================================
echo.

set PYTHONPATH=%~dp0;%PYTHONPATH%
python -m edge_agent.main

if errorlevel 1 (
    echo.
    echo [INFO] Edge Agent exited.
)

pause
