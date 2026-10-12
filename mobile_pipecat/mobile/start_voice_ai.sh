#!/bin/bash

# Pipecat Mobile Bot 실행 스크립트
# Usage: ./start_voice_ai.sh [options]

set -e

# 스크립트가 위치한 디렉토리로 이동
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 프로젝트 루트 경로 설정 (mobile 디렉토리에서 1단계 위로)
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🚀 Starting Pipecat Mobile Bot..."
echo "📁 Working directory: $SCRIPT_DIR"
echo ""

# voice_ai용 패키지 동기화 (pipecat extras + mobile 앱 전용 dependency-group)
cd "$PROJECT_ROOT" && uv sync --extra mobile --group mobile --quiet 2>/dev/null || true
cd "$SCRIPT_DIR"

# PATH 설정 (uv를 위해)
export PATH="$HOME/.local/bin:$PATH"

# PYTHONPATH 설정
export PYTHONPATH="$PROJECT_ROOT/src:$PYTHONPATH"

# VIRTUAL_ENV 환경변수 제거 (uv 경고 방지)
# uv는 프로젝트의 .venv를 자동으로 사용하므로 VIRTUAL_ENV가 설정되어 있으면 경고가 발생합니다
if [ -n "$VIRTUAL_ENV" ]; then
    unset VIRTUAL_ENV
fi

# .env 파일 확인
ENV_FILE="$SCRIPT_DIR/.env"
if [ ! -f "$ENV_FILE" ]; then
    echo "⚠️  Warning: .env file not found at $ENV_FILE"
    echo "   Creating from env.example..."
    if [ -f "$SCRIPT_DIR/env.example" ]; then
        cp "$SCRIPT_DIR/env.example" "$ENV_FILE"
        echo "   ✓ Created .env file. Please update it with your API keys."
        echo ""
    else
        echo "   ✗ env.example file not found. Please create .env file manually."
        exit 1
    fi
fi

# .env 파일 로드
if [ -f "$ENV_FILE" ]; then
    set -a
    source "$ENV_FILE"
    set +a
fi

# Frontend dist 경로 설정 (voice-ui-kit/small-webrtc-prebuilt/client/dist 의 빌드 산출물)
# pipecat의 runner가 이 경로의 정적 파일을 /client 로 마운트함 (src/pipecat/runner/run.py 참조)
# .env 또는 shell 환경변수로 override 가능
FRONTEND_DIST_PATH="${FRONTEND_DIST_PATH:-$PROJECT_ROOT/../mobile_pipecat_frontend/client/dist}"
export FRONTEND_DIST_PATH

if [ -d "$FRONTEND_DIST_PATH" ]; then
    echo "✓ Frontend dist: $FRONTEND_DIST_PATH"
else
    echo "⚠️  Frontend dist not found: $FRONTEND_DIST_PATH"
    echo "   Build it: cd $(dirname "$(dirname "$FRONTEND_DIST_PATH")") && pnpm install && pnpm build:client"
    echo "   Falling back to pipecat-ai-small-webrtc-prebuilt package (may be stale)."
fi
echo ""

# SSL 인증서 파일 확인
SSL_KEY="$SCRIPT_DIR/key.pem"
SSL_CERT="$SCRIPT_DIR/cert.pem"
USE_HTTPS=false

if [ -f "$SSL_KEY" ] && [ -f "$SSL_CERT" ]; then
    USE_HTTPS=true
    echo "✓ SSL certificates found. Using HTTPS mode."
else
    echo "ℹ️  SSL certificates not found. Using HTTP mode."
    echo "   To enable HTTPS, generate certificates:"
    echo "   openssl req -x509 -newkey rsa:4096 -nodes -keyout key.pem -out cert.pem -days 365"
fi
echo ""

# 기본 설정값
HOST="${BOT_HOST:-0.0.0.0}"
PORT="${BOT_PORT:-7860}"
STT_PROVIDER="${STT_PROVIDER:-soniox}"
TTS_PROVIDER="${TTS_PROVIDER:-openai}"
DEBUG_MODE=false
DEBUGGER_TYPE="pdb"  # pdb, debugpy, ipdb
DEBUGPY_PORT=5678
VERBOSE_LEVEL=0
FORCE_DEBUG_LOG_LEVEL=false
PRODUCTION_MODE=false
WORKERS=""
NO_ACCESS_LOG=false

# 명령줄 인수 파싱
while [[ $# -gt 0 ]]; do
    case $1 in
        --host)
            HOST="$2"
            shift 2
            ;;
        --port)
            PORT="$2"
            shift 2
            ;;
        --stt)
            STT_PROVIDER="$2"
            if [ "$STT_PROVIDER" != "google" ] && [ "$STT_PROVIDER" != "soniox" ]; then
                echo "✗ Error: --stt must be either 'google' or 'soniox'"
                exit 1
            fi
            shift 2
            ;;
        --tts)
            TTS_PROVIDER="$2"
            if [ "$TTS_PROVIDER" != "openai" ] && [ "$TTS_PROVIDER" != "cartesia" ]; then
                echo "✗ Error: --tts must be either 'openai' or 'cartesia'"
                exit 1
            fi
            shift 2
            ;;
        --http)
            USE_HTTPS=false
            shift
            ;;
        --https)
            if [ -f "$SSL_KEY" ] && [ -f "$SSL_CERT" ]; then
                USE_HTTPS=true
            else
                echo "✗ Error: SSL certificates not found. Cannot use HTTPS mode."
                exit 1
            fi
            shift
            ;;
        --debug|-d)
            FORCE_DEBUG_LOG_LEVEL=true
            shift
            ;;
        --debugger)
            DEBUG_MODE=true
            DEBUGGER_TYPE="$2"
            if [ "$DEBUGGER_TYPE" != "pdb" ] && [ "$DEBUGGER_TYPE" != "debugpy" ] && [ "$DEBUGGER_TYPE" != "ipdb" ]; then
                echo "✗ Error: --debugger must be 'pdb', 'debugpy', or 'ipdb'"
                exit 1
            fi
            shift 2
            ;;
        --debugpy-port)
            DEBUGPY_PORT="$2"
            shift 2
            ;;
        --verbose|-v)
            VERBOSE_LEVEL=$((VERBOSE_LEVEL + 1))
            shift
            ;;
        --production|-p)
            PRODUCTION_MODE=true
            # Production mode: HTTPS 강제, 디버그 모드 비활성화, 최소 로깅, 성능 최적화
            if [ ! -f "$SSL_KEY" ] || [ ! -f "$SSL_CERT" ]; then
                echo "✗ Error: Production mode requires SSL certificates (key.pem and cert.pem)"
                echo "   Generate certificates:"
                echo "   openssl req -x509 -newkey rsa:4096 -nodes -keyout key.pem -out cert.pem -days 365"
                exit 1
            fi
            USE_HTTPS=true
            DEBUG_MODE=false
            VERBOSE_LEVEL=0
            # Production 성능 최적화: 워커 수 (기본값: CPU 코어 수)
            WORKERS="${UVICORN_WORKERS:-$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)}"
            NO_ACCESS_LOG=true
            shift
            ;;
        --workers)
            WORKERS="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --host HOST     Server host address (default: 0.0.0.0)"
            echo "  --port PORT     Server port (default: 7860)"
            echo "  --stt PROVIDER  STT provider: 'google' or 'soniox' (default: soniox)"
            echo "  --tts PROVIDER  TTS provider: 'openai' or 'cartesia' (default: openai)"
            echo "  --http          Force HTTP mode (default if no SSL certs)"
            echo "  --https         Force HTTPS mode (requires key.pem and cert.pem)"
            echo "  --debug, -d     Set log level to debug (without debugger)"
            echo "  --debugger TYPE Enable debugger mode: 'pdb', 'debugpy', or 'ipdb' (default: pdb)"
            echo "  --debugpy-port  Port for debugpy remote debugging (default: 5678)"
            echo "  --verbose, -v   Increase logging verbosity (can be used multiple times: -v, -vv, -vvv)"
            echo "  --production, -p Production mode (HTTPS required, debug disabled, minimal logging, performance optimized)"
            echo "  --workers N     Number of worker processes (for production, default: CPU cores)"
            echo "  -h, --help      Show this help message"
            echo ""
            echo "Environment variables:"
            echo "  BOT_HOST        Server host address"
            echo "  BOT_PORT        Server port"
            echo "  STT_PROVIDER    STT provider: 'google' or 'soniox' (default: soniox)"
            echo "  TTS_PROVIDER    TTS provider: 'openai' or 'cartesia' (default: openai)"
            echo ""
            echo "Examples:"
            echo "  $0 --production                # Production mode (HTTPS, no debug, minimal logging)"
            echo "  $0 --debug --https              # Debug log level with HTTPS (no debugger)"
            echo "  $0 --debugger debugpy --https   # VS Code remote debugging with HTTPS"
            echo "  $0 --stt google --tts cartesia  # Use Google STT and Cartesia TTS"
            echo "  $0 --tts openai --https         # Use OpenAI TTS with HTTPS"
            echo "  $0 -vv --https                  # Very verbose with HTTPS"
            echo ""
            echo "Debugger Usage:"
            echo "  - pdb: Use 'breakpoint()' in code or 'python -m pdb'"
            echo "  - debugpy: Connect VS Code debugger to port $DEBUGPY_PORT"
            echo "  - ipdb: Enhanced IPython debugger"
            echo ""
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# STT_PROVIDER 및 TTS_PROVIDER 환경변수 설정
export STT_PROVIDER
export TTS_PROVIDER

# STT 제공자 확인
echo "📢 STT Provider: $STT_PROVIDER"
if [ "$STT_PROVIDER" = "soniox" ]; then
    if [ -z "$SONIOX_API_KEY" ] || [ "$SONIOX_API_KEY" = "your_soniox_api_key" ] || [ -z "${SONIOX_API_KEY// }" ]; then
        echo "✗ Error: SONIOX_API_KEY is required when using Soniox STT"
        echo "   Please set SONIOX_API_KEY in your .env file"
        echo "   Get your API key from: https://soniox.com"
        exit 1
    fi
    echo "✓ Soniox API key found"
fi

# TTS 제공자 확인
echo "🔊 TTS Provider: $TTS_PROVIDER"
if [ "$TTS_PROVIDER" = "cartesia" ]; then
    if [ -z "$CARTESIA_API_KEY" ] || [ "$CARTESIA_API_KEY" = "your_cartesia_api_key" ] || [ -z "${CARTESIA_API_KEY// }" ]; then
        echo "⚠️  Warning: CARTESIA_API_KEY not set or invalid"
        echo "   Cartesia TTS requires a valid API key and credits"
        echo "   Get your API key from: https://cartesia.ai"
        echo "   Consider using --tts openai for a free alternative"
    else
        echo "✓ Cartesia API key found"
    fi
elif [ "$TTS_PROVIDER" = "openai" ]; then
    if [ -z "$OPENAI_API_KEY" ] || [ "$OPENAI_API_KEY" = "your_openai_api_key" ] || [ -z "${OPENAI_API_KEY// }" ]; then
        echo "✗ Error: OPENAI_API_KEY is required when using OpenAI TTS"
        echo "   Please set OPENAI_API_KEY in your .env file"
        echo "   Get your API key from: https://platform.openai.com/api-keys"
        exit 1
    fi
    echo "✓ OpenAI API key found"
fi
echo ""

# 프로젝트 루트에서 실행해 uv가 .venv(langgraph 등)를 사용하도록 함
cd "$PROJECT_ROOT"

# 실행 명령어 구성
if [ "$DEBUG_MODE" = true ]; then
    # 디버거 모드
    case "$DEBUGGER_TYPE" in
        pdb)
            # Python 기본 디버거
            CMD="uv run python -m pdb mobile/voice_ai.py --host $HOST --port $PORT --log-level debug"
            echo "🐛 Debugger: Python pdb (breakpoint() supported)"
            ;;
        debugpy)
            # VS Code 원격 디버깅
            CMD="uv run python -m debugpy --listen 0.0.0.0:$DEBUGPY_PORT --wait-for-client mobile/voice_ai.py --host $HOST --port $PORT --log-level debug"
            echo "🐛 Debugger: debugpy (VS Code remote debugging on port $DEBUGPY_PORT)"
            echo "   Connect VS Code debugger to: localhost:$DEBUGPY_PORT"
            ;;
        ipdb)
            # IPython 디버거
            CMD="uv run python -m ipdb mobile/voice_ai.py --host $HOST --port $PORT --log-level debug"
            echo "🐛 Debugger: IPython ipdb (enhanced debugger)"
            ;;
    esac
else
    # 일반 실행 모드
    CMD="uv run python mobile/voice_ai.py --host $HOST --port $PORT"
fi

# debug 로그레벨 설정 (-d/--debug)
if [ "$FORCE_DEBUG_LOG_LEVEL" = true ] && [ "$PRODUCTION_MODE" != true ]; then
    CMD="$CMD --log-level debug"
fi

# Production 모드 성능 최적화 옵션 추가
if [ "$PRODUCTION_MODE" = true ]; then
    if [ -n "$WORKERS" ]; then
        CMD="$CMD --workers $WORKERS"
    fi
    if [ "$NO_ACCESS_LOG" = true ]; then
        CMD="$CMD --no-access-log"
    fi
    CMD="$CMD --log-level info"
fi

# HTTPS 설정
if [ "$USE_HTTPS" = true ]; then
    CMD="$CMD --ssl-keyfile $SSL_KEY --ssl-certfile $SSL_CERT"
    PROTOCOL="https"
else
    PROTOCOL="http"
fi

# Verbose 모드 설정 (디버거 모드가 아닐 때만)
if [ "$DEBUG_MODE" != true ] && [ "$VERBOSE_LEVEL" -gt 0 ]; then
    for ((i=0; i<VERBOSE_LEVEL; i++)); do
        CMD="$CMD -v"
    done
fi

# 실행
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ "$PRODUCTION_MODE" = true ]; then
    echo "🚀 PRODUCTION MODE"
    echo "   - HTTPS: Enabled (required)"
    echo "   - Debug: Disabled"
    echo "   - Logging: Minimal (info level)"
    if [ -n "$WORKERS" ]; then
        echo "   - Workers: $WORKERS processes"
    fi
    if [ "$NO_ACCESS_LOG" = true ]; then
        echo "   - Access Log: Disabled (performance optimized)"
    fi
else
    echo "🔧 DEVELOPMENT MODE"
fi
echo "📡 Server will start at: $PROTOCOL://$HOST:$PORT"
echo "📱 Client URL: $PROTOCOL://$HOST:$PORT/client"
if [ "$HOST" = "0.0.0.0" ] || [ "$HOST" = "localhost" ]; then
    echo "🌐 Local access: $PROTOCOL://localhost:$PORT/client"
fi
echo "🎤 STT Provider: $STT_PROVIDER"
if [ "$VERBOSE_LEVEL" -gt 0 ] && [ "$DEBUG_MODE" != true ]; then
    echo "📝 Verbose mode: Enabled (level: $VERBOSE_LEVEL)"
fi
if [ "$DEBUG_MODE" = true ]; then
    echo "🐛 Debug mode: Enabled ($DEBUGGER_TYPE)"
fi
if [ "$FORCE_DEBUG_LOG_LEVEL" = true ] && [ "$PRODUCTION_MODE" != true ]; then
    echo "🪵 Log level: debug"
fi
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

exec $CMD

