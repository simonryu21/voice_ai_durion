#!/bin/bash

# 프로덕션 빌드 스크립트
# 클라이언트를 빌드하여 프로덕션 모드로 사용할 수 있도록 합니다.

set -e

CLIENT_DIR="$(cd "$(dirname "$0")/../client" && pwd)"

echo "🔨 클라이언트 프로덕션 빌드 중..."
cd "$CLIENT_DIR"

echo "🧹 캐시/빌드 산출물 정리 중..."
rm -rf dist
rm -rf node_modules/.vite

# 의존성 설치 (필요한 경우)
if [ ! -d "node_modules" ]; then
    echo "📦 npm 의존성 설치 중..."
    npm install
fi

# 프로덕션 빌드 실행
echo "🏗️  빌드 실행 중..."
npm run build

echo ""
echo "✅ 프로덕션 빌드 완료!"
echo ""
echo "빌드된 파일 위치: $CLIENT_DIR/dist"
echo ""
echo "이제 백엔드 서버를 실행하면 프로덕션 모드로 동작합니다."

