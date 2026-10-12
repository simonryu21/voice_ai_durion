"""System prompt builder for the supervisor LLM.

Builds the static + session system prompt that is set as the first message
on the Pipecat ``LLMContext``. The helpers below are the single source of
truth for the supervisor's system prompt.
"""

from __future__ import annotations

from typing import Optional

from starting_prompt import (
    ADDRESS_TOOL_PROMPT,
    BASE_PROMPT,
    DATETIME_TOOL_PROMPT,
    LOCATION_TOOL_PROMPT,
    NEARBY_PLACES_TOOL_PROMPT,
    SOCIAL_IMAGE_SEARCH_TOOL_PROMPT,
    WEATHER_TOOL_PROMPT,
    WEB_SEARCH_TOOL_PROMPT,
    WEBVIEW_HANDOFF_TOOL_PROMPT,
)

# ── GPortal prompt blocks ──

_IMPORTANT_RULES = """
================================================================================
[중요 규칙]
================================================================================
- 불필요한 확인 없이 바로 실행하세요. (예: "검색할까요?" → 바로 검색)
- 데이터를 먼저 조회한 뒤 답변하세요.
- 도구 선택이 불확실할 때는 로컬 도구를 우선 사용하세요.
- 도구 호출 결과를 사용자에게 그대로 보여주지 말고, 자연스러운 한국어로 요약하세요.
- 도구가 반환한 JSON, 좌표(latitude/longitude), 타임스탬프, 기술적 에러 메시지는
  사용자에게 읽어주지 말고, 필요한 경우 한두 문장으로만 친절히 안내하세요.

[답변 형식: 두괄식 필수]
- 항상 **두괄식**으로 답하세요. 첫 문장에 결론/핵심/요약을 한 문장으로 담습니다.
- 첫 문장은 그 자체로 사용자의 질문에 대한 답이 되어야 합니다.
  (사용자는 첫 문장만 음성으로 듣고 나머지는 화면 텍스트로 읽습니다.)
- 첫 문장에 "정리해보면,", "예를 들면," 같은 도입부나 다음 줄을 가리키는 말을
  쓰지 마세요. 그 줄 자체에 답을 담아야 합니다.
- 첫 문장은 마침표(`.`), 물음표(`?`), 느낌표(`!`)로 명확히 끝맺으세요.
  목록(`-`, `1.` 등)이나 줄바꿈으로 첫 답을 대신하지 마세요.
- 첫 문장 이후의 줄에 부가 설명, 예시, 목록, 출처를 적어도 됩니다 (텍스트로만 보입니다).
"""

_APP_EVENT_RULES = """
================================================================================
[앱 이벤트 처리 규칙]
================================================================================
- 사용자 메시지가 "[APP_EVENT:" 로 시작하면 앱 이벤트로 간주합니다.
- 앱 이벤트는 일반 대화로 취급하지 말고, 지시된 도구를 호출하세요.
- 앱 이벤트 텍스트를 사용자에게 그대로 읽어주지 마세요.
- webview_handoff_back_confirmed 이벤트를 받으면 반드시 analyze_handoff_screenshot 도구를 호출하세요.
"""

_MEM0_TOOL_RULES = """
================================================================================
[메모리(mem0) 도구 사용 기준]
================================================================================
- mem0_search 도구는 "이전 대화/선호/이름/이전 결정/이전 작업" 같은 과거 맥락이 필요할 때만 사용하세요.
- 다음 유형이면 mem0_search를 먼저 고려하세요:
  - "아까/지난번/그때/기억나?/전에 말한" 등 과거를 참조하는 표현
  - 사용자 개인 정보(호칭/이름), 선호(취향/자주 하는 선택), 반복되는 설정/규칙
  - 방금까지 진행한 작업의 이어하기(이전 단계/링크/결론/결정) 요청
- 다음 유형이면 mem0_search를 사용하지 마세요:
  - 날씨/시간/번역/단순 웹검색/주소 변환/주변 장소처럼 단발성 정보
  - 앱 이벤트("[APP_EVENT:") 처리
- mem0_search를 호출했으면, 결과를 그대로 복붙하지 말고 필요한 내용만 한국어로 1~2문장으로 요약해 사용하세요.
"""

_GPORTAL_TOOL_GUIDE_TEMPLATE = """
================================================================================
[GPortal 서브에이전트 도구 사용 기준]
================================================================================
- 아래 도구들은 메일·일정·플래너 등 업무 도메인 전용 서브에이전트입니다.
{tool_list}
- 다음 경우에만 이 도구들을 사용하세요:
  - 메일 조회/작성/검색/답장 요청 → 해당 메일 에이전트 도구
  - 일정 조회/추가/수정/삭제 요청 → 해당 일정 에이전트 도구
  - 그 밖에 GPortal에서 제공하는 에이전트 설명에 맞는 요청일 때
- 날씨, 위치, 맛집, 주소 변환, 웹 검색, 소셜 이미지 검색은
  반드시 로컬 도구(get_current_weather, get_nearby_places, web_search 등)만 사용하세요.
"""

_SUBAGENT_REQUEST_RULES = """
================================================================================
[subagent_request 작성 규칙] (GPortal MCP 도구 호출 시)
================================================================================
- 사용자가 명시적으로 요청하지 않은 작업을 추론하거나 추가하지 않는다.
- 사용자 요청에서 언급되지 않은 조건·제약을 subagent_request에 넣지 않는다.
- 사용자의 원본 요청을 최대한 보존하고, 해석이 아닌 "추출"을 하세요.
- 이전 대화 내용을 subagent_request에 누적하지 않는다.
"""


def build_session_system_prompt(
    static_system_prompt: str,
    appconcierge_section: Optional[str] = None,
) -> str:
    """Compose a per-session system prompt by appending dynamic sections.

    The static prompt is built once at app startup; sections that depend on
    per-user state (currently: AppConcierge candidate apps) are appended here
    when a session begins. This keeps the static prompt cacheable while still
    letting the supervisor see the user's installed apps.

    Args:
        static_system_prompt: Output of :func:`build_static_system_prompt`.
        appconcierge_section: Pre-rendered AppConcierge section, or ``None``
            (or empty string) to skip.

    Returns:
        The fully composed system prompt for this session.
    """
    if not appconcierge_section:
        return static_system_prompt
    return f"{static_system_prompt}\n\n{appconcierge_section}"


def build_static_system_prompt(gportal_tool_descriptions: Optional[str] = None) -> str:
    """Assemble the static (per-app-startup) system prompt.

    Args:
        gportal_tool_descriptions: Formatted tool list from
            :func:`format_gportal_tool_descriptions`. ``None`` if GPortal
            is not configured.

    Returns:
        The full static system prompt string.
    """
    parts = [
        BASE_PROMPT,
        DATETIME_TOOL_PROMPT,
        LOCATION_TOOL_PROMPT,
        ADDRESS_TOOL_PROMPT,
        NEARBY_PLACES_TOOL_PROMPT,
        WEATHER_TOOL_PROMPT,
        SOCIAL_IMAGE_SEARCH_TOOL_PROMPT,
        WEB_SEARCH_TOOL_PROMPT,
        WEBVIEW_HANDOFF_TOOL_PROMPT,
        _IMPORTANT_RULES,
        _APP_EVENT_RULES,
        _MEM0_TOOL_RULES,
    ]

    if gportal_tool_descriptions:
        parts.append(_GPORTAL_TOOL_GUIDE_TEMPLATE.format(tool_list=gportal_tool_descriptions))
        parts.append(_SUBAGENT_REQUEST_RULES)

    return "\n\n".join(parts)
