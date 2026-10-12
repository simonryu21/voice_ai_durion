#!/bin/bash

# Pipecat Quickstart Bot 실행 스크립트
# Usage: ./start_bot.sh [options]

set -e

# 스크립트가 위치한 디렉토리로 이동
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 프로젝트 루트 경로 설정
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "🚀 Starting Pipecat Bot..."
echo "📁 Working directory: $SCRIPT_DIR"
echo ""

# PATH 설정 (uv를 위해)
export PATH="$HOME/.local/bin:$PATH"

# PYTHONPATH 설정
export PYTHONPATH="$PROJECT_ROOT/src:$PYTHONPATH"

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
        -h|--help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --host HOST     Server host address (default: 0.0.0.0)"
            echo "  --port PORT     Server port (default: 7860)"
            echo "  --stt PROVIDER  STT provider: 'google' or 'soniox' (default: google)"
            echo "  --http          Force HTTP mode (default if no SSL certs)"
            echo "  --https         Force HTTPS mode (requires key.pem and cert.pem)"
            echo "  -h, --help      Show this help message"
            echo ""
            echo "Environment variables:"
            echo "  BOT_HOST        Server host address"
            echo "  BOT_PORT        Server port"
            echo "  STT_PROVIDER    STT provider: 'google' or 'soniox'"
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

# STT_PROVIDER 환경변수 설정
export STT_PROVIDER

# Soniox 사용 시 API 키 검증
if [ "$STT_PROVIDER" = "soniox" ]; then
    if [ -z "$SONIOX_API_KEY" ] || [ "$SONIOX_API_KEY" = "your_soniox_api_key" ] || [ -z "${SONIOX_API_KEY// }" ]; then
        echo "✗ Error: SONIOX_API_KEY is required when using Soniox STT"
        echo "   Please set SONIOX_API_KEY in your .env file"
        echo "   Get your API key from: https://soniox.com"
        exit 1
    fi
    echo "✓ Soniox API key found"
fi

# 실행 명령어 구성
CMD="uv run bot.py --host $HOST --port $PORT"

if [ "$USE_HTTPS" = true ]; then
    CMD="$CMD --ssl-keyfile $SSL_KEY --ssl-certfile $SSL_CERT"
    PROTOCOL="https"
else
    PROTOCOL="http"
fi

# 실행
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📡 Server will start at: $PROTOCOL://$HOST:$PORT"
echo "📱 Client URL: $PROTOCOL://$HOST:$PORT/client"
if [ "$HOST" = "0.0.0.0" ] || [ "$HOST" = "localhost" ]; then
    echo "🌐 Local access: $PROTOCOL://localhost:$PORT/client"
fi
echo "🎤 STT Provider: $STT_PROVIDER"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

exec $CMD
