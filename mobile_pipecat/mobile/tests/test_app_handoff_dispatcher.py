"""Tests for the real `AppInvokeDispatcher`.

Uses lightweight stand-ins for ``RTVIProcessor`` and ``PipelineTask`` —
the dispatcher only needs ``rtvi.push_transport_message(...)`` and
``task.queue_frame(...)``.
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

import pytest
from pipecat.frames.frames import TTSSpeakFrame
from pipecat.processors.frameworks.rtvi import RTVIServerMessageFrame

from app_catalog.candidate_builder import AppCandidate, CandidateSet
from app_catalog.schema import CatalogApp, CatalogCategory, CatalogParam, ExecutionPath
from app_handoff.dispatcher import make_app_invoke_dispatcher
from app_handoff.store import AppHandoffStore


class _FakeRtvi:
    def __init__(self) -> None:
        self.sent: list[Any] = []

    async def push_transport_message(self, frame: Any) -> None:
        self.sent.append(frame)


class _FakeTask:
    def __init__(self) -> None:
        self.queued: list[Any] = []

    async def queue_frame(self, frame: Any) -> None:
        self.queued.append(frame)


def _catalog() -> dict[str, CatalogCategory]:
    return {
        "navigation": CatalogCategory(
            category="navigation",
            display_name="지도",
            apps=[
                CatalogApp(
                    app_id="naver_map",
                    display_name="네이버 지도",
                    package_name="com.nhn.android.nmap",
                    popularity_rank=1,
                    use_when="x",
                    parameters=[CatalogParam(name="query", type="string", required=True)],
                    execution={
                        "android": [
                            ExecutionPath(
                                type="applink",
                                priority=1,
                                template="https://map.naver.com/v5/search/{query}",
                            ),
                            ExecutionPath(
                                type="deeplink",
                                priority=2,
                                template="nmap://search?query={query}",
                            ),
                        ]
                    },
                )
            ],
        )
    }


def _candidate_set() -> CandidateSet:
    return CandidateSet(
        user_id="u",
        platform="android",
        generated_at=datetime.now(timezone.utc),
        by_category={
            "navigation": [
                AppCandidate(
                    app_id="naver_map",
                    display_name="네이버 지도",
                    package_name="com.nhn.android.nmap",
                    category="navigation",
                    use_when="x",
                    examples=[],
                    parameters=[CatalogParam(name="query", type="string", required=True)],
                    execution={
                        "android": [
                            ExecutionPath(
                                type="applink",
                                priority=1,
                                template="https://map.naver.com/v5/search/{query}",
                            )
                        ]
                    },
                    popularity_rank=1,
                    source="user_inventory",
                    user_usage_score=1.0,
                )
            ]
        },
    )


@pytest.mark.asyncio
async def test_happy_path_dispatches_tts_and_rtvi():
    rtvi, task, store = _FakeRtvi(), _FakeTask(), AppHandoffStore()
    dispatch = make_app_invoke_dispatcher(
        rtvi=rtvi,
        task=task,
        store=store,
        catalog=_catalog(),
        session_pc_id="s1",
        user_id="u1",
    )
    ack = await dispatch(
        {
            "app_id": "naver_map",
            "parameters": {"query": "강남역"},
            "spoken_intro": "네이버 지도로 강남역 길찾기 열어드릴게요.",
        }
    )

    # Returns ack mentioning the handoff_id.
    assert "handoff_id=" in ack
    assert "naver_map" in ack

    # The dispatcher queues two frames on the task:
    # 1) TTSSpeakFrame for the spoken intro
    # 2) RTVIServerMessageFrame with the app_invoke envelope (RTVIObserver
    #    forwards this to the client as an RTVI server-message).
    assert len(task.queued) == 2
    tts = task.queued[0]
    assert isinstance(tts, TTSSpeakFrame)
    assert tts.text.startswith("네이버 지도로")

    msg = task.queued[1]
    assert isinstance(msg, RTVIServerMessageFrame)
    env = msg.data
    assert rtvi.sent == []  # rtvi.push_transport_message is no longer used
    assert env["v"] == 1
    assert env["type"] == "app_invoke"
    assert env["handoff_id"]
    payload = env["payload"]
    assert payload["app_id"] == "naver_map"
    assert payload["package_name"] == "com.nhn.android.nmap"
    assert payload["delay_ms"] == 1000
    assert payload["spoken_intro_dispatched_via_tts"] is True
    assert payload["tracking"]["track_screenshot_on_return"] is True

    # Execution paths are sorted, URIs are URL-encoded.
    paths = payload["execution_paths"]
    assert [p["type"] for p in paths] == ["applink", "deeplink"]
    assert all("%EA%B0%95%EB%82%A8%EC%97%AD" in p["uri"] for p in paths)

    # Store has the active record.
    rec = store.active("s1")
    assert rec is not None
    assert rec.app_id == "naver_map"
    assert rec.status == "requested"
    assert rec.user_id == "u1"
    assert rec.handoff_id == env["handoff_id"]


@pytest.mark.asyncio
async def test_unknown_app_id_returns_error_without_dispatch():
    rtvi, task, store = _FakeRtvi(), _FakeTask(), AppHandoffStore()
    dispatch = make_app_invoke_dispatcher(
        rtvi=rtvi, task=task, store=store, catalog=_catalog(), session_pc_id="s1"
    )
    ack = await dispatch({"app_id": "unknown_app", "spoken_intro": "x"})
    assert ack.startswith("[ERROR]")
    assert task.queued == []
    assert store.active("s1") is None


@pytest.mark.asyncio
async def test_missing_required_param_returns_error_without_dispatch():
    rtvi, task, store = _FakeRtvi(), _FakeTask(), AppHandoffStore()
    dispatch = make_app_invoke_dispatcher(
        rtvi=rtvi, task=task, store=store, catalog=_catalog(), session_pc_id="s1"
    )
    ack = await dispatch({"app_id": "naver_map", "parameters": {}, "spoken_intro": "x"})
    assert "missing required" in ack
    assert task.queued == []


@pytest.mark.asyncio
async def test_empty_spoken_intro_skips_tts_frame():
    rtvi, task, store = _FakeRtvi(), _FakeTask(), AppHandoffStore()
    dispatch = make_app_invoke_dispatcher(
        rtvi=rtvi, task=task, store=store, catalog=_catalog(), session_pc_id="s1"
    )
    await dispatch({"app_id": "naver_map", "parameters": {"query": "x"}, "spoken_intro": "  "})
    # No TTS frame for whitespace-only intro; only the RTVIServerMessageFrame.
    assert len(task.queued) == 1
    assert isinstance(task.queued[0], RTVIServerMessageFrame)


@pytest.mark.asyncio
async def test_second_invoke_silently_drops_previous():
    rtvi, task, store = _FakeRtvi(), _FakeTask(), AppHandoffStore()
    dispatch = make_app_invoke_dispatcher(
        rtvi=rtvi, task=task, store=store, catalog=_catalog(), session_pc_id="s1"
    )
    await dispatch({"app_id": "naver_map", "parameters": {"query": "a"}, "spoken_intro": "x"})
    first_id = store.active("s1").handoff_id
    await dispatch({"app_id": "naver_map", "parameters": {"query": "b"}, "spoken_intro": "y"})
    second_id = store.active("s1").handoff_id
    assert first_id != second_id
    # Q6: silent drop — no extra cancel message goes out, just two app_invokes.
    rtvi_frames = [f for f in task.queued if isinstance(f, RTVIServerMessageFrame)]
    assert len(rtvi_frames) == 2
    # First record is in history with status "dropped".
    history = store.history("s1")
    assert len(history) == 1
    assert history[0].handoff_id == first_id
    assert history[0].status == "dropped"


@pytest.mark.asyncio
async def test_candidate_set_constrains_dispatchable_apps():
    """The dispatcher accepts a CandidateSet so apps outside the user's
    candidate pool can't be launched even if the LLM hallucinates them."""
    rtvi, task, store = _FakeRtvi(), _FakeTask(), AppHandoffStore()
    dispatch = make_app_invoke_dispatcher(
        rtvi=rtvi,
        task=task,
        store=store,
        catalog=_candidate_set(),
        session_pc_id="s1",
    )
    # The candidate set has naver_map only.
    ok = await dispatch({"app_id": "naver_map", "parameters": {"query": "x"}, "spoken_intro": "x"})
    assert "handoff_id=" in ok

    queued_before = len(task.queued)
    err = await dispatch(
        {"app_id": "tmap", "parameters": {"destination": "x"}, "spoken_intro": "x"}
    )
    assert err.startswith("[ERROR] unknown app_id")
    # No new frames were queued for the rejected app.
    assert len(task.queued) == queued_before


@pytest.mark.asyncio
async def test_session_pc_id_none_does_not_register_in_store():
    rtvi, task, store = _FakeRtvi(), _FakeTask(), AppHandoffStore()
    dispatch = make_app_invoke_dispatcher(
        rtvi=rtvi, task=task, store=store, catalog=_catalog(), session_pc_id=None
    )
    ack = await dispatch({"app_id": "naver_map", "parameters": {"query": "x"}, "spoken_intro": "x"})
    # RTVI message still goes out (so the client can launch), but nothing
    # is tracked since we have no session key.
    assert "handoff_id=" in ack
    rtvi_frames = [f for f in task.queued if isinstance(f, RTVIServerMessageFrame)]
    assert len(rtvi_frames) == 1
    assert store.active(None) is None


@pytest.mark.asyncio
async def test_track_screenshot_on_return_can_be_disabled():
    rtvi, task, store = _FakeRtvi(), _FakeTask(), AppHandoffStore()
    dispatch = make_app_invoke_dispatcher(
        rtvi=rtvi,
        task=task,
        store=store,
        catalog=_catalog(),
        session_pc_id="s1",
        track_screenshot_on_return=False,
    )
    await dispatch({"app_id": "naver_map", "parameters": {"query": "x"}, "spoken_intro": "x"})
    rtvi_frame = next(f for f in task.queued if isinstance(f, RTVIServerMessageFrame))
    assert rtvi_frame.data["payload"]["tracking"]["track_screenshot_on_return"] is False
