"""Script to get available ElevenLabs voices."""

import os
import sys

import requests
from dotenv import load_dotenv

load_dotenv()


def get_elevenlabs_voices():
    """Get list of available ElevenLabs voices."""
    api_key = os.getenv("ELEVENLABS_API_KEY")

    if not api_key:
        print("❌ ELEVENLABS_API_KEY가 설정되지 않았습니다.")
        print("   .env 파일에 ELEVENLABS_API_KEY를 추가해주세요.")
        sys.exit(1)

    headers = {"xi-api-key": api_key}

    try:
        response = requests.get("https://api.elevenlabs.io/v1/voices", headers=headers)
        response.raise_for_status()

        data = response.json()
        voices = data.get("voices", [])

        print(f"\n✅ 사용 가능한 Voice 목록 ({len(voices)}개):\n")
        print("-" * 80)

        for voice in voices:
            voice_id = voice.get("voice_id", "")
            name = voice.get("name", "")
            category = voice.get("category", "")
            description = voice.get("description", "")

            print(f"Voice ID: {voice_id}")
            print(f"이름: {name}")
            print(f"카테고리: {category}")
            if description:
                print(f"설명: {description}")
            print("-" * 80)

        print(f"\n💡 사용 예시:")
        print(f"   .env 파일에 추가:")
        print(f"   ELEVENLABS_VOICE_ID={voices[0].get('voice_id') if voices else 'your_voice_id'}")

    except requests.exceptions.RequestException as e:
        print(f"❌ API 호출 실패: {e}")
        if hasattr(e, "response") and e.response is not None:
            print(f"   응답: {e.response.text}")
        sys.exit(1)


if __name__ == "__main__":
    get_elevenlabs_voices()
