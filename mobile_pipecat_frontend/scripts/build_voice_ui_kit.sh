#!/bin/bash

# Voice-UI-Kit 수동 빌드 및 반영 스크립트

set -e

VOICE_UI_KIT_DIR="/Users/ds.ryu/project/voice-ui-kit"
CLIENT_DIR="/Users/ds.ryu/project/small-webrtc-prebuilt/client"
PACKAGE_NAME="@pipecat-ai/voice-ui-kit"

echo "🔨 Voice-UI-Kit 빌드 중..."
cd "$VOICE_UI_KIT_DIR"
pnpm build

echo "📦 빌드 결과물 복사 중..."
PACKAGE_DIR="$CLIENT_DIR/node_modules/$PACKAGE_NAME"

# dist 폴더 복사
if [ -d "$VOICE_UI_KIT_DIR/package/dist" ]; then
    cp -r "$VOICE_UI_KIT_DIR/package/dist" "$PACKAGE_DIR/"
    echo "✅ dist 폴더 복사 완료"
else
    echo "❌ 빌드 결과물을 찾을 수 없습니다: $VOICE_UI_KIT_DIR/package/dist"
    exit 1
fi

# package.json도 업데이트 (필요한 경우)
if [ -f "$VOICE_UI_KIT_DIR/package/package.json" ]; then
    cp "$VOICE_UI_KIT_DIR/package/package.json" "$PACKAGE_DIR/"
    echo "✅ package.json 복사 완료"
fi

echo ""
echo "✨ 빌드 및 반영 완료!"
echo ""
echo "다음 단계:"
echo "- build_production.sh 실행하여 프로덕션 모드로 빌드"

