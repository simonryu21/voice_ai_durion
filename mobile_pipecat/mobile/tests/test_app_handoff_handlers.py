"""Tests for the LLM-context text builders for handoff lifecycle events."""

from datetime import datetime, timezone

from app_handoff.handlers import (
    build_back_confirmed_event_text,
    build_failed_event_text,
    build_timeout_event_text,
)
from app_handoff.store import AppHandoffRecord


def _record(**kwargs) -> AppHandoffRecord:
    base = dict(
        handoff_id="h1",
        session_pc_id="s1",
        app_id="naver_map",
        package_name="com.nhn.android.nmap",
        spoken_intro="네이버 지도 열어드릴게요.",
        requested_at=datetime.now(timezone.utc),
    )
    base.update(kwargs)
    return AppHandoffRecord(**base)


# ── back_confirmed ──


def test_back_confirmed_includes_duration_and_handoff_id():
    text = build_back_confirmed_event_text(
        _record(),
        payload={"handoff_id": "h1", "duration_ms": 32000, "return_kind": "auto_resume"},
        app_display_name="네이버 지도",
    )
    assert "[APP_EVENT: app_handoff_back_confirmed]" in text
    assert "네이버 지도" in text
    assert "naver_map" in text
    assert "32초" in text
    assert "h1" in text


def test_back_confirmed_with_analysis_inserts_section():
    text = build_back_confirmed_event_text(
        _record(),
        payload={"handoff_id": "h1", "duration_ms": 1000},
        app_display_name="네이버 지도",
        analysis_json='{"status":"completed","amount":"$50"}',
    )
    assert "복귀 시점 화면" in text
    assert '{"status":"completed"' in text


def test_back_confirmed_handles_missing_record():
    text = build_back_confirmed_event_text(
        None,
        payload={"handoff_id": "h1", "duration_ms": 0, "app_id": "tmap"},
        app_display_name="티맵",
    )
    assert "티맵" in text
    assert "tmap" in text  # falls back to payload's app_id


def test_back_confirmed_handles_missing_duration():
    text = build_back_confirmed_event_text(
        _record(),
        payload={"handoff_id": "h1"},
        app_display_name="네이버 지도",
    )
    assert "0초" in text or "?초" in text  # graceful handling


# ── failed ──


def test_failed_includes_reason():
    text = build_failed_event_text(
        _record(),
        payload={"handoff_id": "h1", "reason": "no_handler"},
        app_display_name="네이버 지도",
    )
    assert "[APP_EVENT: app_handoff_failed]" in text
    assert "no_handler" in text
    assert "네이버 지도" in text
    assert "사과" in text  # rule mentions 사과


def test_failed_unknown_reason_fallback():
    text = build_failed_event_text(
        _record(),
        payload={"handoff_id": "h1"},
        app_display_name="네이버 지도",
    )
    assert "unknown" in text


# ── timeout ──


def test_timeout_includes_elapsed():
    text = build_timeout_event_text(
        _record(),
        payload={"handoff_id": "h1", "elapsed_ms": 300000},
        app_display_name="네이버 지도",
    )
    assert "[APP_EVENT: app_handoff_timeout]" in text
    assert "300초" in text
    assert "복귀를 감지하지 못했" in text
