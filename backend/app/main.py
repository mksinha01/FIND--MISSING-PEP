"""FIND-MISSING-PEP Backend Application Entrypoint."""
import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.api import api_router
from app.config import settings
from app.database import close_db, init_db

logger = logging.getLogger(__name__)


def ensure_upload_directories():
    """Ensure upload media directories exist on disk."""
    base_dir = Path(settings.UPLOAD_DIR)
    base_dir.mkdir(parents=True, exist_ok=True)
    (base_dir / "photos").mkdir(parents=True, exist_ok=True)
    (base_dir / "faces").mkdir(parents=True, exist_ok=True)
    (base_dir / "evidence").mkdir(parents=True, exist_ok=True)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan context manager for startup and graceful shutdown."""
    logger.info("Initializing FIND-MISSING-PEP Backend Services...")
    ensure_upload_directories()
    await init_db()
    yield
    logger.info("Shutting down FIND-MISSING-PEP Backend Services...")
    await close_db()


def create_app() -> FastAPI:
    """FastAPI application factory."""
    ensure_upload_directories()

    application = FastAPI(
        title="FIND-MISSING-PEP Backend API",
        description="AI-Based Missing Person Detection & Real-Time CCTV Monitoring System",
        version="1.0.0",
        lifespan=lifespan,
    )

    # 1. CORS Middleware
    application.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # 2. Static File Mount for uploaded media
    application.mount(
        "/uploads",
        StaticFiles(directory=settings.UPLOAD_DIR),
        name="uploads",
    )

    # 3. Static File Mount for Web App Interface
    static_dir = Path(__file__).parent / "static"
    if static_dir.exists():
        application.mount(
            "/static",
            StaticFiles(directory=str(static_dir)),
            name="static",
        )

    # 4. Web App Entrypoint routes
    from fastapi.responses import FileResponse

    @application.get("/", response_class=FileResponse, tags=["web-app"])
    @application.get("/app", response_class=FileResponse, tags=["web-app"])
    async def serve_web_app():
        index_file = static_dir / "index.html"
        if index_file.exists():
            return FileResponse(str(index_file))
        return {
            "status": "ok",
            "version": "1.0.0",
            "service": "FIND-MISSING-PEP Backend",
            "docs": "/docs",
        }

    # 5. Health Check endpoint (No Auth)
    @application.get("/health", tags=["health"])
    async def health_check():
        return {
            "status": "ok",
            "version": "1.0.0",
            "service": "FIND-MISSING-PEP Backend",
        }

    # 6. Include REST API Routers
    application.include_router(api_router)

    return application


app = create_app()
