FROM python:3.11-slim

# Install system dependencies (Debian 12 compatible)
# Fix #1: Use libgl1 and libglib2.0-0 instead of libgl1-mesa-glx
RUN apt-get update && apt-get install -y \
    build-essential \
    libgl1 \
    libglib2.0-0 \
    libpq-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Ensure models directory exists
RUN mkdir -p /app/ai_models

# Fix #2: Pre-warm InsightFace models during build
RUN python -c "from insightface.app import FaceAnalysis; app = FaceAnalysis(name='buffalo_l', root='/app/ai_models', providers=['CPUExecutionProvider']); app.prepare(ctx_id=-1, det_size=(640, 640))"

# Copy application code
COPY . .

# Ensure start.sh has executable permissions
RUN chmod +x start.sh

EXPOSE 8000

CMD ["./start.sh"]
