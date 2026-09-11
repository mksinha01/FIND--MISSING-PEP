"""FIND-MISSING-PEP — Unified Multi-Process Runner.

Launches all system components in a single command with unified logging,
health checks, graceful termination, and optional browser auto-launch:
1. FastAPI Backend Server (http://127.0.0.1:8000)
2. React/Vite Web Application (http://127.0.0.1:5173)
3. PySide6 Edge Agent Desktop Application
"""
import argparse
import os
import signal
import subprocess
import sys
import time
import urllib.request
import webbrowser
from pathlib import Path
from typing import List, Optional

# Root directory of the repository
ROOT_DIR = Path(__file__).resolve().parent
BACKEND_DIR = ROOT_DIR / "backend"
WEB_APP_DIR = ROOT_DIR / "web_app"
UPLOAD_DIR = ROOT_DIR / "uploads"
EVIDENCE_DIR = ROOT_DIR / "evidence"

# Colors for terminal output
class Colors:
    HEADER = "\033[95m"
    OKBLUE = "\033[94m"
    OKCYAN = "\033[96m"
    OKGREEN = "\033[92m"
    WARNING = "\033[93m"
    FAIL = "\033[91m"
    ENDC = "\033[0m"
    BOLD = "\033[1m"


def log_banner():
    banner = f"""
{Colors.OKCYAN}{Colors.BOLD}╔═══════════════════════════════════════════════════════════════════════════╗
║                      FIND-MISSING-PEP SYSTEM RUNNER                       ║
║        AI-Based Missing Person Detection & Real-Time CCTV Monitoring       ║
╚═══════════════════════════════════════════════════════════════════════════╝{Colors.ENDC}
"""
    print(banner)


def ensure_directories():
    """Ensure all required media directories exist."""
    print(f"{Colors.OKBLUE}[INIT]{Colors.ENDC} Ensuring media storage directories exist...")
    dirs = [
        UPLOAD_DIR / "photos",
        UPLOAD_DIR / "faces",
        UPLOAD_DIR / "evidence",
        EVIDENCE_DIR,
    ]
    for d in dirs:
        d.mkdir(parents=True, exist_ok=True)
    print(f"{Colors.OKGREEN}[INIT]{Colors.ENDC} Storage directories ready.")


def find_python_executable() -> str:
    """Find the best Python executable."""
    return sys.executable


def find_npm_executable() -> Optional[str]:
    """Find the npm executable for web_app."""
    npm_cmd = "npm.cmd" if sys.platform == "win32" else "npm"
    try:
        subprocess.run([npm_cmd, "--version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
        return npm_cmd
    except (subprocess.SubprocessError, FileNotFoundError):
        return None


def wait_for_backend(url: str = "http://127.0.0.1:8000/health", timeout: float = 30.0) -> bool:
    """Wait until backend health endpoint responds with 200 OK."""
    print(f"{Colors.OKCYAN}[BACKEND]{Colors.ENDC} Waiting for backend to become healthy at {url}...")
    start_time = time.time()
    while time.time() - start_time < timeout:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "HealthCheck"})
            with urllib.request.urlopen(req, timeout=1.5) as response:
                if response.status == 200:
                    print(f"{Colors.OKGREEN}[BACKEND]{Colors.ENDC} Backend is ONLINE and healthy! (took {time.time() - start_time:.1f}s)")
                    return True
        except Exception:
            time.sleep(0.8)
    print(f"{Colors.WARNING}[BACKEND]{Colors.ENDC} Backend health check timed out; continuing anyway...")
    return False


def kill_process_tree(pid: int):
    """Gracefully or forcefully kill a process and all its children on Windows/Unix."""
    if sys.platform == "win32":
        try:
            subprocess.run(["taskkill", "/F", "/T", "/PID", str(pid)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        except Exception:
            pass
    else:
        try:
            os.killpg(os.getpgid(pid), signal.SIGTERM)
        except Exception:
            pass


def main():
    parser = argparse.ArgumentParser(description="Run all FIND-MISSING-PEP components in one command.")
    parser.add_argument("--no-gui", action="store_true", help="Do not launch Edge Agent PySide6 GUI (run backend + web app only)")
    parser.add_argument("--no-web", action="store_true", help="Do not launch React Web App (run backend + edge agent only)")
    parser.add_argument("--no-browser", action="store_true", help="Do not automatically open web browser")
    parser.add_argument("--seed", action="store_true", help="Seed database with sample test cases and cameras before launch")
    parser.add_argument("--host", default="127.0.0.1", help="Backend host (default: 127.0.0.1)")
    parser.add_argument("--port", type=int, default=8000, help="Backend port (default: 8000)")
    args = parser.parse_args()

    log_banner()
    ensure_directories()

    py_exe = find_python_executable()
    npm_exe = find_npm_executable()

    env = os.environ.copy()
    env["PYTHONPATH"] = f"{ROOT_DIR}{os.pathsep}{BACKEND_DIR}{os.pathsep}{env.get('PYTHONPATH', '')}"

    # 1. Seed database if requested
    if args.seed:
        print(f"{Colors.OKCYAN}[SEED]{Colors.ENDC} Seeding sample data...")
        seed_script = ROOT_DIR / "scripts" / "seed_test_data.py"
        if seed_script.exists():
            subprocess.run([py_exe, str(seed_script), "--count", "5"], cwd=str(ROOT_DIR), env=env)
        print(f"{Colors.OKGREEN}[SEED]{Colors.ENDC} Seeding finished.")

    processes: List[subprocess.Popen] = []

    def shutdown(signum=None, frame=None):
        print(f"\n{Colors.WARNING}[STOPPING]{Colors.ENDC} Shutting down all FIND-MISSING-PEP services cleanly...")
        for p in processes:
            if p.poll() is None:
                kill_process_tree(p.pid)
        print(f"{Colors.OKGREEN}[STOPPED]{Colors.ENDC} All processes stopped.")
        sys.exit(0)

    # Register termination signals
    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    # 2. Launch FastAPI Backend
    print(f"\n{Colors.OKBLUE}[1/3]{Colors.ENDC} Launching FastAPI Backend on http://{args.host}:{args.port}...")
    backend_cmd = [
        py_exe,
        "-m",
        "uvicorn",
        "app.main:app",
        "--app-dir",
        str(BACKEND_DIR),
        "--host",
        args.host,
        "--port",
        str(args.port),
        "--reload",
    ]
    backend_proc = subprocess.Popen(
        backend_cmd,
        cwd=str(ROOT_DIR),
        env=env,
    )
    processes.append(backend_proc)

    # Wait for backend to be ready
    wait_for_backend(f"http://{args.host}:{args.port}/health")

    # 3. Launch React/Vite Web App
    web_proc = None
    if not args.no_web:
        if npm_exe and WEB_APP_DIR.exists():
            print(f"\n{Colors.OKBLUE}[2/3]{Colors.ENDC} Launching React Web App on http://localhost:5173...")
            web_cmd = [npm_exe, "run", "dev"]
            web_proc = subprocess.Popen(
                web_cmd,
                cwd=str(WEB_APP_DIR),
                env=env,
            )
            processes.append(web_proc)
        else:
            print(f"{Colors.WARNING}[WEB-APP]{Colors.ENDC} npm not found or web_app missing; web app skipped.")

    # 4. Launch Edge Agent GUI
    gui_proc = None
    if not args.no_gui:
        print(f"\n{Colors.OKBLUE}[3/3]{Colors.ENDC} Launching Edge Agent Desktop GUI (PySide6)...")
        gui_cmd = [
            py_exe,
            "-m",
            "edge_agent.main",
        ]
        gui_proc = subprocess.Popen(
            gui_cmd,
            cwd=str(ROOT_DIR),
            env=env,
        )
        processes.append(gui_proc)

    # 5. Open browser if requested
    if not args.no_browser:
        time.sleep(1.5)
        web_target = "http://localhost:5173" if (not args.no_web and web_proc) else f"http://{args.host}:{args.port}"
        print(f"\n{Colors.OKGREEN}[BROWSER]{Colors.ENDC} Opening Web App: {web_target}")
        print(f"{Colors.OKGREEN}[BROWSER]{Colors.ENDC} API Swagger Docs: http://{args.host}:{args.port}/docs")
        try:
            webbrowser.open(web_target)
        except Exception:
            pass

    print(f"\n{Colors.OKGREEN}{Colors.BOLD}═══════════════════════════════════════════════════════════════════════════")
    print(f" ALL SERVICES ARE ACTIVE AND RUNNING!")
    print(f" • Web App URL:      http://localhost:5173  (or http://{args.host}:{args.port}/)")
    print(f" • Backend API Docs: http://{args.host}:{args.port}/docs")
    print(f" • Edge Agent GUI:   Running (Desktop Window)")
    print(f" Press Ctrl+C at any time to stop all services.")
    print(f"═══════════════════════════════════════════════════════════════════════════{Colors.ENDC}\n")

    # Keep orchestrator alive while monitoring child processes
    try:
        while True:
            # If backend or gui dies, check status
            for p in processes:
                exit_code = p.poll()
                if exit_code is not None and exit_code != 0:
                    print(f"{Colors.WARNING}[MONITOR]{Colors.ENDC} A child process (PID {p.pid}) exited with code {exit_code}.")
            time.sleep(1.0)
    except KeyboardInterrupt:
        shutdown()


if __name__ == "__main__":
    main()
