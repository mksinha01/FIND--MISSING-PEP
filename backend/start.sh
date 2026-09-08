#!/bin/bash
set -e

export PYTHONPATH="/app:${PYTHONPATH}"

echo "╔══════════════════════════════════════════╗"
echo "║  FindMissingPerson Backend Starting...   ║"
echo "╠══════════════════════════════════════════╣"
echo "║  APP_ENV:    ${APP_ENV:-development}     ║"
echo "║  PORT:       ${PORT:-8000}               ║"
echo "║  WORKERS:    ${WORKERS:-1}               ║"
echo "║  HOST:       ${POSTGRES_HOST:-localhost} ║"
echo "║  REDIS:      ${REDIS_URL:-not set}       ║"
echo "║  UPLOAD_DIR: ${UPLOAD_DIR:-./uploads}    ║"
echo "╚══════════════════════════════════════════╝"

# Wait for PostgreSQL database to be ready
echo "Waiting for PostgreSQL database to be ready..."
python -c "
import asyncio, sys
from sqlalchemy.ext.asyncio import create_async_engine
from app.config import settings

async def check():
    for attempt in range(1, 31):
        try:
            engine = create_async_engine(settings.DATABASE_URL_ASYNC)
            async with engine.connect() as conn:
                print('Database connection established successfully.')
                await engine.dispose()
                return
        except Exception as e:
            print(f'Waiting for database connection (attempt {attempt}/30)...')
            await asyncio.sleep(2)
    print('Database connection failed after 30 attempts.')
    sys.exit(1)

asyncio.run(check())
"

# Run migrations
echo "Running database migrations..."
alembic upgrade head

# Create upload directories
mkdir -p ${UPLOAD_DIR:-./uploads}/{photos,faces,evidence}

echo "Starting uvicorn..."
exec uvicorn app.main:app \
    --host ${HOST:-0.0.0.0} \
    --port ${PORT:-8000} \
    --workers ${WORKERS:-1} \
    --log-level info
