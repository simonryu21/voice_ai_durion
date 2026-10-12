"""Pure helpers that build the LLM-context text for each handoff lifecycle event.

The actual frame plumbing (LLMMessagesAppendFrame push, run_llm trigger,
screenshot caching, …) lives in ``voice_ai.py``'s ``on_client_message``
handler. Splitting the **text** building out keeps that handler small and
makes the wording testable without mocking Pipecat plumbing.

All builders return strings prefixed with ``[APP_EVENT: app_handoff_*]``
so the supervisor's ``_APP_EVENT_RULES`` block in
``system_prompt_builder.py`` recognizes them as app events (don't read
aloud, follow the instructions).
"""

from __future__ import annotations

from typing import Optional

from app_handoff.store import AppHandoffRecord

_BACK_TEMPLATE = (
    "[APP_EVENT: app_handoff_back_confirmed] "
    "사용자가 `{display_name}`({app_id}) 앱에서 {duration_sec}초 사용 후 돌아왔습니다. "
    "handoff_id={handoff_id}, return_kind={return_kind}.\n"
    "{analysis_section}"
    "규칙: 위 내용을 근거로 짧게 결과를 확인하는 후속 발화 1문장만 하세요. "
    "예: '길은 잘 찾으셨나요?' / '메시지 보내셨어요?' "
    "사용자가 다음 행동을 말하기 전엔 새 작업을 시작하지 마세요."
)


_FAILED_TEMPLATE = (
    "[APP_EVENT: app_handoff_failed] "
    "`{display_name}`({app_id}) 앱 실행이 실패했습니다 (reason={reason}). "
    "handoff_id={handoff_id}.\n"
    "규칙: 짧게 사과한 뒤 (a) 다른 카테고리 도구로 폴백할지 (b) 사용자에게 "
    "다른 방식을 제안할지 1문장으로 결정해 답하세요."
)


_TIMEOUT_TEMPLATE = (
    "[APP_EVENT: app_handoff_timeout] "
    "`{display_name}`({app_id}) 앱을 열었지만 {elapsed_sec}초 동안 사용자 복귀를 감지하지 못했습니다. "
    "handoff_id={handoff_id}.\n"
    "규칙: 사용자가 아직 앱 안에 있을 가능성이 큽니다. 짧게 후속 안부 1문장만 하세요."
)


_ANALYSIS_INTRO = (
    "아래는 복귀 시점 화면을 서버에서 직접 분석한 결과(JSON)입니다:\n{analysis_json}\n"
)


def build_back_confirmed_event_text(
    record: Optional[AppHandoffRecord],
    payload: dict,
    *,
    app_display_name: str,
    analysis_json: Optional[str] = None,
) -> str:
    """Compose the user-message text injected when the user comes back.

    Args:
        record: Active handoff record from the store. Used for app_id /
            spoken_intro context. May be ``None`` if the lifecycle is
            already terminated; in that case identifying fields fall back
            to the payload.
        payload: The client's ``app_handoff_back_confirmed`` payload
            (``duration_ms``, ``return_kind``, ``user_summary``, ...).
        app_display_name: Pre-resolved display name (callers usually have
            it from the catalog or the record).
        analysis_json: Optional Vision-LLM analysis result as a JSON
            string. If provided, it is inserted between the lifecycle
            line and the ruleset so the supervisor can use it to ground
            its follow-up question.
    """
    handoff_id = payload.get("handoff_id") or (record.handoff_id if record else "")
    duration_ms = payload.get("duration_ms")
    duration_sec = (
        round((duration_ms or 0) / 1000) if isinstance(duration_ms, (int, float)) else "?"
    )
    return_kind = payload.get("return_kind") or "auto_resume"
    app_id = (record.app_id if record else "") or payload.get("app_id") or "unknown"

    analysis_section = ""
    if analysis_json:
        analysis_section = _ANALYSIS_INTRO.format(analysis_json=analysis_json) + "\n"

    return _BACK_TEMPLATE.format(
        display_name=app_display_name,
        app_id=app_id,
        duration_sec=duration_sec,
        handoff_id=handoff_id,
        return_kind=return_kind,
        analysis_section=analysis_section,
    )


def build_failed_event_text(
    record: Optional[AppHandoffRecord],
    payload: dict,
    *,
    app_display_name: str,
) -> str:
    handoff_id = payload.get("handoff_id") or (record.handoff_id if record else "")
    reason = payload.get("reason") or "unknown"
    app_id = (record.app_id if record else "") or payload.get("app_id") or "unknown"
    return _FAILED_TEMPLATE.format(
        display_name=app_display_name,
        app_id=app_id,
        reason=reason,
        handoff_id=handoff_id,
    )


def build_timeout_event_text(
    record: Optional[AppHandoffRecord],
    payload: dict,
    *,
    app_display_name: str,
) -> str:
    handoff_id = payload.get("handoff_id") or (record.handoff_id if record else "")
    elapsed_ms = payload.get("elapsed_ms")
    elapsed_sec = round((elapsed_ms or 0) / 1000) if isinstance(elapsed_ms, (int, float)) else "?"
    app_id = (record.app_id if record else "") or payload.get("app_id") or "unknown"
    return _TIMEOUT_TEMPLATE.format(
        display_name=app_display_name,
        app_id=app_id,
        elapsed_sec=elapsed_sec,
        handoff_id=handoff_id,
    )
