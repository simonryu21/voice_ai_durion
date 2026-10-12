"""Build the real (non-mock) `AppInvokeDispatcher`.

Wires together:

- catalog lookup (resolve `app_id` → `CatalogApp`)
- URI template rendering (catalog + parameters → ready-to-launch URIs)
- handoff store (allocate `handoff_id`, register record)
- TTS push (`spoken_intro` via the pipeline TTS service)
- RTVI server-message push (envelope per design §3.3 / §4.2.1)

The factory takes its dependencies via keyword args so it can be
constructed inside the per-session ``run_bot()`` once all the pipeline
objects exist.
"""

from __future__ import annotations

import uuid
from datetime import datetime, timezone
from typing import Any, Awaitable, Callable, Optional, Protocol

from loguru import logger
from pipecat.frames.frames import TTSSpeakFrame
from pipecat.processors.frameworks.rtvi import RTVIProcessor, RTVIServerMessageFrame

from app_catalog.candidate_builder import CandidateSet
from app_catalog.schema import CatalogApp, CatalogCategory, Platform
from app_handoff.store import AppHandoffRecord, AppHandoffStore
from app_handoff.uri import render_execution_paths


class _FrameQueue(Protocol):
    """Subset of `PipelineTask` used here — declared as a protocol so
    tests can pass a lightweight stand-in instead of building a full task.
    """

    async def queue_frame(self, frame: Any) -> None:  # pragma: no cover - typing only
        ...


AppInvokeDispatcher = Callable[[dict[str, Any]], Awaitable[str]]
"""Async callable: ``(payload) -> ack_string``. Payload keys:
``app_id``, ``parameters`` (dict), ``spoken_intro`` (str)."""


# Default lifecycle timing (design §6.3 / §8.2).
_DEFAULT_DELAY_MS = 1000
_DEFAULT_TIMEOUT_MS = 300_000  # 5 min


def make_app_invoke_dispatcher(
    *,
    rtvi: RTVIProcessor,
    task: _FrameQueue,
    store: AppHandoffStore,
    catalog: dict[str, CatalogCategory] | CandidateSet,
    session_pc_id: Optional[str],
    user_id: Optional[str] = None,
    platform: Platform = "android",
    delay_ms: int = _DEFAULT_DELAY_MS,
    timeout_ms: int = _DEFAULT_TIMEOUT_MS,
    track_screenshot_on_return: bool = True,  # Q5 (B): screenshot analysis enabled
) -> AppInvokeDispatcher:
    """Construct a dispatcher closing over the per-session pipeline objects.

    Args:
        rtvi: The session's RTVIProcessor; used to push the
            ``app_invoke`` server message to the client.
        task: Anything with ``queue_frame()`` — the dispatcher pushes a
            ``TTSSpeakFrame`` here for the spoken intro.
        store: Per-session handoff lifecycle store.
        catalog: Either the full catalog (``dict[category_id, CatalogCategory]``)
            or a per-user :class:`CandidateSet`. The latter is preferred
            because it restricts launches to apps the user actually has
            in the supervisor's prompt.
        session_pc_id: The WebRTC pc_id used as the lifecycle key in the
            store. May be ``None`` when running outside a real session
            (e.g. tests without a transport).
        user_id: Forwarded into ``AppHandoffRecord`` for observability.
        platform: Which execution paths to render (``"android"`` /
            ``"ios"``). Phase 3a always uses ``"android"``.
        delay_ms: How long the *client* should wait between TTS intro and
            actual launch (the user's cancel window).
        timeout_ms: How long the *client* should wait for ``onResume``
            before declaring a timeout.
        track_screenshot_on_return: Whether the client should capture and
            return a screenshot when the user comes back. Q5 default for
            Phase 3a is ``True`` (uses the existing vision analyzer).
    """
    catalog_index = _build_catalog_index(catalog)

    async def dispatcher(payload: dict[str, Any]) -> str:
        app_id = payload.get("app_id")
        params = dict(payload.get("parameters") or {})
        spoken_intro = (payload.get("spoken_intro") or "").strip()
        logger.info(
            "[app_invoke_dispatcher] enter app_id={!r} params={} spoken_intro={!r} "
            "session_pc_id={} known_app_ids={}",
            app_id,
            params,
            spoken_intro,
            session_pc_id,
            list(catalog_index.keys()),
        )

        if not app_id:
            logger.error("[app_invoke_dispatcher] missing app_id in payload={}", payload)
            return "[ERROR] invoke_app payload missing app_id"

        catalog_app = catalog_index.get(app_id)
        if catalog_app is None:
            logger.error(
                "[app_invoke_dispatcher] unknown app_id={!r} (known={})",
                app_id,
                list(catalog_index.keys()),
            )
            return f"[ERROR] unknown app_id: {app_id}"

        try:
            execution_paths = render_execution_paths(catalog_app, params, platform=platform)
        except ValueError as e:
            logger.error(
                "[app_invoke_dispatcher] render_execution_paths failed app_id={} params={} err={}",
                app_id,
                params,
                e,
            )
            return f"[ERROR] {e}"

        logger.info(
            "[app_invoke_dispatcher] rendered execution_paths app_id={}: {}",
            app_id,
            [
                {
                    "type": p.type,
                    "priority": p.priority,
                    "uri": p.uri,
                    "action": p.action,
                    "extras": p.extras,
                }
                for p in execution_paths
            ],
        )

        handoff_id = str(uuid.uuid4())
        record = AppHandoffRecord(
            handoff_id=handoff_id,
            session_pc_id=session_pc_id,
            user_id=user_id,
            app_id=app_id,
            package_name=catalog_app.package_name,
            parameters=params,
            spoken_intro=spoken_intro,
            rendered_uris=[p.uri for p in execution_paths if p.uri],
            requested_at=datetime.now(timezone.utc),
            status="requested",
        )
        dropped = store.register(record)
        if dropped is not None:
            logger.info(
                "AppConcierge: silently dropped previous handoff_id={} for session_pc_id={}",
                dropped.handoff_id,
                session_pc_id,
            )

        # Q2 (A) — Backend pushes the TTS frame so the user hears the
        # intro through the existing pipeline TTS service. The client
        # gets `spoken_intro_dispatched_via_tts: true` so it doesn't
        # double-speak.
        if spoken_intro:
            try:
                await task.queue_frame(TTSSpeakFrame(text=spoken_intro))
                logger.info(
                    "[app_invoke_dispatcher] TTSSpeakFrame queued for spoken_intro handoff_id={} text={!r}",
                    handoff_id,
                    spoken_intro,
                )
            except Exception as e:
                logger.warning(f"Failed to queue TTSSpeakFrame for spoken_intro: {e}")

        envelope = {
            "v": 1,
            "type": "app_invoke",
            "handoff_id": handoff_id,
            "ts": datetime.now(timezone.utc).isoformat(),
            "payload": {
                "app_id": app_id,
                "display_name": catalog_app.display_name,
                "package_name": catalog_app.package_name,
                "execution_paths": [p.model_dump(exclude_none=True) for p in execution_paths],
                "delay_ms": delay_ms,
                "spoken_intro": spoken_intro,
                "spoken_intro_dispatched_via_tts": True,  # Q2 (A)
                "timeout_ms": timeout_ms,
                "tracking": {
                    "track_resume": True,
                    "track_screenshot_on_return": track_screenshot_on_return,
                },
            },
        }

        # `RTVIServerMessageFrame` is a pipeline frame; the `RTVIObserver`
        # attached to the PipelineTask intercepts it and forwards it to the
        # client as an RTVI server-message. Pushing through `task.queue_frame`
        # keeps the path consistent with our TTS frame above.
        try:
            await task.queue_frame(RTVIServerMessageFrame(data=envelope))
        except Exception as e:
            logger.error(f"Failed to push app_invoke RTVI message: {e}")
            return f"[ERROR] failed to send to client: {e}"

        logger.info(
            "AppConcierge dispatched (handoff_id={}, app_id={}, paths={}, delay_ms={}, "
            "session_pc_id={})",
            handoff_id,
            app_id,
            [p.type for p in execution_paths],
            delay_ms,
            session_pc_id,
        )

        return (
            f"App `{catalog_app.display_name}` ({app_id}) invocation dispatched "
            f"(handoff_id={handoff_id})."
        )

    return dispatcher


def _build_catalog_index(
    catalog: dict[str, CatalogCategory] | CandidateSet,
) -> dict[str, CatalogApp]:
    """Build a flat ``app_id → CatalogApp`` map from either input shape.

    Accepting both keeps the dispatcher convenient: pass the full catalog
    if you want to allow any known app, or a CandidateSet to constrain
    launches to the per-user candidate list (so the AppConcierge LLM can't
    "hallucinate" apps outside its allowed pool).
    """
    index: dict[str, CatalogApp] = {}
    if isinstance(catalog, CandidateSet):
        # CandidateSet only carries the fields needed at the LLM layer; we
        # rebuild a CatalogApp from the candidate so render_execution_paths
        # can work uniformly.
        for cat_apps in catalog.by_category.values():
            for c in cat_apps:
                index[c.app_id] = CatalogApp(
                    app_id=c.app_id,
                    display_name=c.display_name,
                    package_name=c.package_name,
                    popularity_rank=c.popularity_rank,
                    use_when=c.use_when,
                    examples=c.examples,
                    parameters=c.parameters,
                    execution=c.execution,
                )
        return index

    for category in catalog.values():
        for app in category.apps:
            index[app.app_id] = app
    return index
