# Pipecat Mobile Bot Dockerfile
# Production-optimized Docker image for the mobile bot

FROM python:3.11-slim

# Install system dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install uv
RUN pip install --no-cache-dir uv

# Set working directory
WORKDIR /app

# Copy root project files (build context is project root)
COPY pyproject.toml uv.lock* ./

# Copy source code
COPY src ./src

# Copy mobile-specific files
COPY mobile/pyproject.toml mobile/
COPY mobile/uv.lock* mobile/
COPY mobile/*.py mobile/
COPY mobile/tools mobile/tools/
COPY mobile/processors mobile/processors/
COPY mobile/utils mobile/utils/
COPY mobile/auth mobile/auth/
COPY mobile/app_inventory mobile/app_inventory/
COPY mobile/integrations mobile/integrations/
COPY mobile/starting_prompt.py mobile/
COPY mobile/modify_pyproject.py mobile/

# Enable bytecode compilation for better performance
ENV UV_COMPILE_BYTECODE=1
ENV UV_LINK_MODE=copy

# Install root pipecat-ai package first (editable mode to use local source)
RUN uv sync --locked --no-dev && \
    uv pip install --system -e .

# Install mobile bot dependencies
# We need to modify mobile's pyproject.toml to use local pipecat instead of PyPI
RUN cd mobile && \
    cp pyproject.toml pyproject.toml.bak && \
    pip install tomli-w && \
    python3 modify_pyproject.py && \
    uv lock && \
    uv sync --no-dev && \
    uv pip install --system -e . && \
    # Re-install root pipecat-ai in editable mode in the venv to ensure it takes precedence
    # This is needed because uv run uses the venv, not system Python
    cd .. && \
    uv pip install --system --force-reinstall --no-deps -e . && \
    cd mobile && \
    # Also install in the venv used by uv run using uv pip with venv's Python
    uv pip install --python .venv/bin/python --force-reinstall --no-deps -e ../ && \
    # Verify using venv's Python directly (not uv run) to avoid dependency re-resolution
    .venv/bin/python -c "import pipecat.runner.run; f=pipecat.runner.run.__file__; print('pipecat location:', f); assert '/app/src' in f, f'Expected local source, got {f}'" && \
    mv pyproject.toml.bak pyproject.toml

# Create directory for SSL certificates
RUN mkdir -p /app/mobile

# Expose port
EXPOSE 7860

# Set working directory to mobile
WORKDIR /app/mobile

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:7860/health || exit 1

# Run the bot with production optimizations
# Note: Environment variables are passed at runtime via docker-compose or docker run
# SSL certificates are optional - only add if they exist
CMD ["sh", "-c", "if [ -f key.pem ] && [ -f cert.pem ]; then uv run voice_ai.py --host 0.0.0.0 --port 7860 --workers ${UVICORN_WORKERS:-4} --no-access-log --log-level info --ssl-keyfile key.pem --ssl-certfile cert.pem; else uv run voice_ai.py --host 0.0.0.0 --port 7860 --workers ${UVICORN_WORKERS:-4} --no-access-log --log-level info; fi"]

