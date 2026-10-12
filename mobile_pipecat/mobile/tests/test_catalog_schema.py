"""Tests for app_catalog.schema validation."""

import pytest
from pydantic import ValidationError

from app_catalog.schema import (
    CatalogApp,
    CatalogCategory,
    CatalogParam,
    ExecutionPath,
)


def _make_valid_app(app_id: str = "naver_map", **overrides) -> CatalogApp:
    base = dict(
        app_id=app_id,
        display_name="네이버 지도",
        package_name="com.nhn.android.nmap",
        popularity_rank=1,
        use_when="길찾기, 대중교통 경로, 주변 장소 검색이 필요할 때.",
        examples=["강남역 가는 길"],
        parameters=[CatalogParam(name="query", type="string", required=True, description="검색어")],
        execution={
            "android": [
                ExecutionPath(
                    type="applink", priority=1, template="https://map.naver.com/v5/search/{query}"
                ),
                ExecutionPath(type="deeplink", priority=2, template="nmap://search?query={query}"),
            ]
        },
    )
    base.update(overrides)
    return CatalogApp(**base)


def test_valid_app_round_trip():
    app = _make_valid_app()
    assert app.app_id == "naver_map"
    assert len(app.execution["android"]) == 2


def test_intent_requires_action():
    with pytest.raises(ValidationError, match="action"):
        ExecutionPath(type="intent", priority=1, template="ignored")


def test_applink_requires_template():
    with pytest.raises(ValidationError, match="template"):
        ExecutionPath(type="applink", priority=1)


def test_intent_with_action_only_is_ok():
    p = ExecutionPath(type="intent", priority=1, action="android.intent.action.VIEW")
    assert p.action == "android.intent.action.VIEW"


def test_app_must_have_execution_paths():
    with pytest.raises(ValidationError, match="execution"):
        _make_valid_app(execution={})


def test_app_rejects_empty_platform_list():
    with pytest.raises(ValidationError, match="empty execution"):
        _make_valid_app(execution={"android": []})


def test_app_rejects_duplicate_priorities():
    with pytest.raises(ValidationError, match="duplicate priorities"):
        _make_valid_app(
            execution={
                "android": [
                    ExecutionPath(type="applink", priority=1, template="https://x"),
                    ExecutionPath(type="deeplink", priority=1, template="x://y"),
                ]
            }
        )


def test_category_rejects_duplicate_app_ids():
    a = _make_valid_app(app_id="dup")
    b = _make_valid_app(app_id="dup", display_name="다른 앱", package_name="com.other")
    with pytest.raises(ValidationError, match="duplicate app_ids"):
        CatalogCategory(category="navigation", display_name="지도", apps=[a, b])


def test_category_extra_fields_forbidden():
    with pytest.raises(ValidationError):
        CatalogCategory(
            category="navigation",
            display_name="지도",
            apps=[_make_valid_app()],
            unknown_field="x",
        )
