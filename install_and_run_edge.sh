#!/usr/bin/env bash
set -e

echo "====================================================================="
echo "   FIND-MISSING-PEP: Edge CCTV Monitoring Agent (Linux/macOS)"
echo "   Connected to Production Cloud: http://65.20.80.222:8000"
echo "====================================================================="
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 1. Detect Python
if command -v python3 &>/dev/null; then
    PY_CMD="python3"
elif command -v python &>/dev/null; then
    PY_CMD="python"
else
    echo "[ERROR] Python 3 is not installed!"
    echo "On Ubuntu/Debian, install with: sudo apt update && sudo apt install -y python3 python3-pip python3-venv"
    exit 1
fi

echo "[OK] Found Python: $($PY_CMD --version)"

# 2. Virtual Environment
if [ ! -d ".venv" ]; then
    echo "[SETUP] Creating virtual environment (.venv)..."
    $PY_CMD -m venv .venv || {
        echo "[ERROR] python3-venv might be missing. Run: sudo apt install python3-venv"
        exit 1
    }
fi

source .venv/bin/activate

# 3. Dependencies
if [ ! -f ".venv/installed.flag" ]; then
    echo "[SETUP] Installing requirements from edge_agent/requirements.txt..."
    pip install --upgrade pip
    pip install -r edge_agent/requirements.txt || {
        echo "[WARNING] Retrying core packages..."
        pip install PySide6 onnxruntime numpy opencv-python insightface faiss-cpu httpx sseclient-py pydantic pydantic-settings pillow psutil
    }
    echo "installed" > .venv/installed.flag
    echo "[OK] Dependencies ready!"
fi

# 4. Config
if [ ! -f "edge_agent/config.ini" ]; then
    if [ -f "edge_agent/config.local.ini" ]; then
        cp "edge_agent/config.local.ini" "edge_agent/config.ini"
        echo "[OK] Initialized edge_agent/config.ini"
    fi
fi

# 5. Run GUI
echo ""
echo "====================================================================="
echo "   Launching Edge CCTV Agent Desktop GUI..."
echo "====================================================================="
echo ""

export PYTHONPATH="$SCRIPT_DIR:$PYTHONPATH"
$PY_CMD -m edge_agent.main
