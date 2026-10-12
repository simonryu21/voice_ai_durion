"""Tests for AppConcierge prompt rendering and session prompt composition."""

from datetime import datetime, timezone

from app_catalog.candidate_builder import AppCandidate, CandidateSet
from app_catalog.prompt import render_candidates_section
from app_catalog.schema import CatalogParam, ExecutionPath
from system_prompt_builder import build_session_system_prompt


def _candidate(
    app_id: str,
    display_name: str,
    category: str,
    *,
    source: str = "user_inventory",
    examples: list[str] | None = None,
    use_when: str = "이런 상황에 적합합니다.",
) -> AppCandidate:
    return AppCandidate(
        app_id=app_id,
        display_name=display_name,
        package_name=f"com.{app_id}",
        category=category,
        use_when=use_when,
        examples=examples or [],
        parameters=[CatalogParam(name="query", type="string", required=True)],
        execution={"android": [ExecutionPath(type="applink", priority=1, template="https://x")]},
        popularity_rank=1,
        source=source,  # type: ignore[arg-type]
        user_usage_score=1.0 if source == "user_inventory" else None,
    )


def _candidate_set(by_category: dict[str, list[AppCandidate]]) -> CandidateSet:
    return CandidateSet(
        user_id="u",
        platform="android",
        generated_at=datetime.now(timezone.utc),
        by_category=by_category,
    )


def test_render_empty_candidates_returns_empty_string():
    cs = _candidate_set({})
    assert render_candidates_section(cs) == ""


def test_render_lists_app_with_tag_and_examples():
    cs = _candidate_set(
        {
            "navigation": [
                _candidate(
                    "naver_map",
                    "네이버 지도",
                    "navigation",
                    examples=["강남역 가는 길"],
                ),
                _candidate(
                    "tmap",
                    "티맵",
                    "navigation",
                    source="popular_default",
                    examples=["내비 켜줘"],
                ),
            ]
        }
    )
    out = render_candidates_section(cs)
    assert "직접 실행 가능" in out
    assert "open_<app_id>" in out
    assert "네이버 지도" in out
    assert "naver_map" in out
    assert "[내 폰]" in out
    assert "[추천]" in out
    assert "티맵" in out
    assert "강남역 가는 길" in out


def test_render_collapses_multiline_use_when():
    cs = _candidate_set(
        {
            "navigation": [
                _candidate(
                    "naver_map",
                    "네이버 지도",
                    "navigation",
                    use_when="첫 번째 줄.\n   두 번째 줄.",
                )
            ]
        }
    )
    out = render_candidates_section(cs)
    assert "첫 번째 줄. 두 번째 줄." in out


def test_render_skips_empty_category():
    cs = _candidate_set({"empty": [], "navigation": [_candidate("a", "A", "navigation")]})
    out = render_candidates_section(cs)
    assert "navigation" in out
    # the empty category produces no header
    assert "#### empty" not in out


def test_session_prompt_appends_when_section_given():
    base = "STATIC_PROMPT"
    section = "[APP_SECTION]"
    composed = build_session_system_prompt(base, section)
    assert composed.startswith(base)
    assert section in composed
    assert "\n\n" in composed


def test_session_prompt_passes_through_when_section_empty():
    base = "STATIC_PROMPT"
    assert build_session_system_prompt(base, None) == base
    assert build_session_system_prompt(base, "") == base
