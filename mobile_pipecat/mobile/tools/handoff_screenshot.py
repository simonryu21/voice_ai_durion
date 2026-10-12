"""WebView handoff screenshot caching + vision analysis.

These helpers are framework-neutral — they don't depend on LangGraph or
Pipecat. They're consumed by:

- ``app_concierge_tools`` / ``tools.pipecat_adapters`` as the
  ``analyze_handoff_screenshot`` Pipecat function body.
- ``voice_ai.run_bot`` directly when an ``app_handoff_back_confirmed``
  client message arrives (caches the screenshot, then analyses it).
"""

from __future__ import annotations

import asyncio
import base64
import io
import json
import os
from typing import Dict, Optional, Tuple

from loguru import logger
from openai import AsyncOpenAI  # type: ignore[import-not-found]
from PIL import Image

# session_pc_id → screenshot base64 string (in-process cache).
_handoff_screenshots: Dict[str, str] = {}

SCREENSHOT_ANALYSIS_SYSTEM_PROMPT = """당신은 모바일 화면 스크린샷 분석 전문가입니다.
사용자가 외부 예약/결제 사이트에서 작업 후 돌아온 화면을 분석합니다.

반드시 아래 JSON 스키마로만 응답하세요:

{
  "status": "completed | in_progress | abandoned | failed | unknown",
  "confidence": 0.0~1.0,
  "evidence": ["화면에서 관찰한 근거1", "근거2", ...],
  "extracted": {
    "vendor": "사이트명 (판별 가능 시)",
    "reservation_id": "예약번호 (있으면)",
    "item_name": "호텔명/상품명/항공편 등",
    "dates": "날짜 정보 (있으면)",
    "total_price": "총 금액 (있으면)",
    "currency": "KRW/USD 등",
    "payment_method_hint": "결제수단 힌트 (카드사 로고 등, 있으면)"
  },
  "user_message": "사용자에게 전할 한 줄 요약"
}

분류 기준:
- completed: "예약 완료", "결제 완료", "Booking confirmed", 예약번호 표시 등
- in_progress: 결제 페이지, 인증 화면, 장바구니 등 아직 완료되지 않은 단계
- abandoned: 검색 결과 목록, 메인 페이지 등 예약/결제를 시작하지 않은 상태
- failed: 오류 메시지, 결제 실패, 타임아웃 등
- unknown: 판단 불가

보안 규칙:
- 카드번호, 주민번호, 전화번호 등 개인 식별 정보는 절대 추출하지 마세요.
- evidence에는 "문구/상태" 중심으로 작성하고 개인정보는 마스킹하세요."""


def _downscale_jpeg_base64(
    jpeg_b64: str,
    max_side: int,
    quality: int,
) -> Tuple[str, int, int]:
    """Decode JPEG base64, downscale if needed, re-encode as JPEG.

    Returns:
        (new_base64, original_approx_len, new_len)
    """
    raw = base64.b64decode(jpeg_b64, validate=False)
    orig_len = len(raw)
    im = Image.open(io.BytesIO(raw))
    if im.mode != "RGB":
        im = im.convert("RGB")
    w, h = im.size
    if max_side > 0 and max(w, h) > max_side:
        ratio = max_side / float(max(w, h))
        new_w = max(1, int(w * ratio))
        new_h = max(1, int(h * ratio))
        im = im.resize((new_w, new_h), Image.Resampling.LANCZOS)
    out = io.BytesIO()
    im.save(out, format="JPEG", quality=quality, optimize=True)
    new_bytes = out.getvalue()
    return base64.b64encode(new_bytes).decode("ascii"), orig_len, len(new_bytes)


async def analyze_handoff_screenshot_for_session(session_pc_id: Optional[str]) -> str:
    """Analyze a cached WebView handoff screenshot for a specific session."""
    if not session_pc_id:
        logger.warning("Handoff screenshot analysis skipped: missing session_pc_id")
        return json.dumps(
            {
                "status": "unknown",
                "confidence": 0.0,
                "evidence": ["세션 ID가 없어 스크린샷을 찾을 수 없습니다."],
                "extracted": {},
                "user_message": "스크린샷을 확인할 수 없었습니다. 결과를 직접 알려주시겠어요?",
            },
            ensure_ascii=False,
        )

    # Keep screenshot cached until analysis succeeds so callers can retry.
    screenshot_b64 = _handoff_screenshots.get(session_pc_id)
    if not screenshot_b64:
        logger.warning(
            "Handoff screenshot analysis skipped: no cached screenshot (session_pc_id={})",
            session_pc_id,
        )
        return json.dumps(
            {
                "status": "unknown",
                "confidence": 0.0,
                "evidence": ["스크린샷을 찾을 수 없습니다."],
                "extracted": {},
                "user_message": "스크린샷을 확인할 수 없었습니다. 결과를 직접 알려주시겠어요?",
            },
            ensure_ascii=False,
        )

    context_text = "사용자 요청: (알 수 없음)"

    try:
        openai_client = AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY"))
        model = os.getenv("OPENAI_VISION_MODEL", "gpt-4o")
        timeout_seconds = float(os.getenv("HANDOFF_VISION_TIMEOUT_SECONDS", "25"))
        max_side = int(os.getenv("HANDOFF_VISION_MAX_SIDE", "1280"))
        jpeg_quality = int(os.getenv("HANDOFF_VISION_JPEG_QUALITY", "82"))
        image_detail = os.getenv("HANDOFF_VISION_IMAGE_DETAIL", "auto").strip().lower()
        if image_detail not in ("auto", "low", "high"):
            image_detail = "auto"

        try:
            vision_b64, raw_jpeg_len, scaled_len = _downscale_jpeg_base64(
                screenshot_b64,
                max_side=max_side,
                quality=jpeg_quality,
            )
        except Exception as resize_err:
            logger.warning(
                "Handoff screenshot resize failed (session_pc_id={}), using original: {}",
                session_pc_id,
                resize_err,
            )
            vision_b64 = screenshot_b64
            raw_jpeg_len = scaled_len = 0

        logger.info(
            "Starting handoff screenshot analysis (session_pc_id={}, b64_chars={}, model={}, timeout={}s, "
            "jpeg_bytes~{}/{}, max_side={}, detail={})",
            session_pc_id,
            len(screenshot_b64),
            model,
            timeout_seconds,
            raw_jpeg_len,
            scaled_len,
            max_side,
            image_detail,
        )
        response = await asyncio.wait_for(
            openai_client.chat.completions.create(
                model=model,
                messages=[
                    {"role": "system", "content": SCREENSHOT_ANALYSIS_SYSTEM_PROMPT},
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "text",
                                "text": (
                                    "아래 컨텍스트를 참고하여 이 스크린샷을 분석해주세요.\n\n"
                                    f"{context_text}"
                                ),
                            },
                            {
                                "type": "image_url",
                                "image_url": {
                                    "url": f"data:image/jpeg;base64,{vision_b64}",
                                    "detail": image_detail,
                                },
                            },
                        ],
                    },
                ],
                response_format={"type": "json_object"},
                max_tokens=600,
                temperature=0.1,
            ),
            timeout=timeout_seconds,
        )
        analysis_json = response.choices[0].message.content or ""
        if analysis_json.strip():
            _handoff_screenshots.pop(session_pc_id, None)
            logger.info(
                "Handoff screenshot analysis completed (session_pc_id={}, response_len={})",
                session_pc_id,
                len(analysis_json),
            )
            return analysis_json
        raise RuntimeError("Empty analysis response")
    except asyncio.TimeoutError:
        logger.error(
            "Handoff screenshot analysis timed out (session_pc_id={})",
            session_pc_id,
        )
        return json.dumps(
            {
                "status": "unknown",
                "confidence": 0.0,
                "evidence": ["화면 분석 시간이 초과되었습니다."],
                "extracted": {},
                "user_message": "화면 분석이 지연되고 있어요. 중요한 내용만 먼저 알려주시면 이어서 도와드릴게요.",
            },
            ensure_ascii=False,
        )
    except Exception as e:
        logger.exception("Screenshot analysis failed: {}", e)
        return json.dumps(
            {
                "status": "unknown",
                "confidence": 0.0,
                "evidence": ["분석 중 오류가 발생했습니다."],
                "extracted": {},
                "user_message": "화면 분석 중 오류가 발생했습니다. 결과를 직접 알려주시겠어요?",
            },
            ensure_ascii=False,
        )


__all__ = [
    "_handoff_screenshots",
    "analyze_handoff_screenshot_for_session",
]
