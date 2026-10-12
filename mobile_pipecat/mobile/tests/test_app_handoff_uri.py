"""Tests for app_handoff.uri.render_execution_paths."""

import pytest

from app_catalog.schema import CatalogApp, CatalogParam, ExecutionPath
from app_handoff.uri import render_execution_paths


def _app(execution=None, parameters=None) -> CatalogApp:
    if execution is None:
        execution = {
            "android": [
                ExecutionPath(
                    type="applink",
                    priority=2,
                    template="https://map.naver.com/v5/search/{query}",
                ),
                ExecutionPath(
                    type="deeplink",
                    priority=1,
                    template="nmap://search?query={query}&app=voice",
                ),
            ]
        }
    return CatalogApp(
        app_id="naver_map",
        display_name="네이버 지도",
        package_name="com.nhn.android.nmap",
        popularity_rank=1,
        use_when="x",
        parameters=(
            parameters
            if parameters is not None
            else [CatalogParam(name="query", type="string", required=True)]
        ),
        execution=execution,
    )


def test_renders_in_priority_order():
    paths = render_execution_paths(_app(), {"query": "강남역"}, platform="android")
    assert [p.type for p in paths] == ["deeplink", "applink"]


def test_url_encodes_korean_query():
    paths = render_execution_paths(_app(), {"query": "강남역"}, platform="android")
    deeplink = paths[0]
    assert "%EA%B0%95%EB%82%A8%EC%97%AD" in deeplink.uri
    assert "&app=voice" in deeplink.uri  # static parts preserved


def test_unknown_placeholder_becomes_empty_string():
    paths = render_execution_paths(
        _app(parameters=[]),  # no required params
        {},
        platform="android",
    )
    # `{query}` is replaced with "" — the URI ends with a trailing slash.
    assert paths[1].uri == "https://map.naver.com/v5/search/"


def test_missing_required_parameter_raises():
    with pytest.raises(ValueError, match="missing required parameters"):
        render_execution_paths(_app(), {}, platform="android")


def test_unknown_platform_raises():
    with pytest.raises(ValueError, match="no execution paths for platform"):
        render_execution_paths(_app(), {"query": "x"}, platform="ios")


def test_intent_path_renders_action_and_uri_template():
    app = _app(
        execution={
            "android": [
                ExecutionPath(
                    type="intent",
                    priority=1,
                    action="android.intent.action.VIEW",
                    uri_template="geo:0,0?q={query}",
                )
            ]
        }
    )
    paths = render_execution_paths(app, {"query": "강남역"}, platform="android")
    intent = paths[0]
    assert intent.type == "intent"
    assert intent.action == "android.intent.action.VIEW"
    assert intent.uri == "geo:0,0?q=%EA%B0%95%EB%82%A8%EC%97%AD"


def test_url_encodes_special_chars():
    paths = render_execution_paths(
        _app(),
        {"query": "맛집/카페?"},
        platform="android",
    )
    # `/` and `?` must both be percent-encoded.
    assert "%2F" in paths[0].uri or "%2f" in paths[0].uri
    assert "%3F" in paths[0].uri or "%3f" in paths[0].uri


def test_numeric_parameter_is_stringified_and_encoded():
    app = _app(
        parameters=[CatalogParam(name="lat", type="number", required=False)],
        execution={
            "android": [ExecutionPath(type="applink", priority=1, template="https://x?lat={lat}")]
        },
    )
    paths = render_execution_paths(app, {"lat": 37.5}, platform="android")
    assert paths[0].uri == "https://x?lat=37.5"
