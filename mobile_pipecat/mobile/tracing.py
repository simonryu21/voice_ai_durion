"""Langfuse tracing helpers for the mobile bot.

Mirrors the gportal-ax-backend `core/tracing.py` pattern:

- A single :class:`Langfuse` instance bound to a *dedicated* OpenTelemetry
  ``TracerProvider`` so it does not collide with Pipecat's existing OTLP
  exporter (which already targets the same Langfuse host but operates on the
  global tracer provider).
- :func:`get_langfuse_handler` returns a per-turn ``CallbackHandler`` for any
  callers that want to forward LangChain callback events to Langfuse. When
  ``LANGFUSE_ENABLED`` is false (or the SDK is not installed) it returns
  ``None`` and the caller silently no-ops. (Currently unused in the mobile
  pipeline — kept for potential future LangChain integration points.)

Why a separate provider?
  Pipecat's OTLP HTTP exporter (set up in
  :func:`pipecat.utils.tracing.langfuse.setup_langfuse_tracing_from_env`)
  also pushes to Langfuse. If we let the Langfuse SDK reuse that same
  provider, every Pipecat span gets duplicated into Langfuse via two
  routes. Giving Langfuse its own provider keeps the two pipelines
  separate (Pipecat → manual spans; Langfuse SDK → LangChain callback
  spans) but they end up under the same trace because we explicitly thread
  ``langfuse_session_id`` through metadata.
"""

from __future__ import annotations

import os
from typing import Any, Optional

from loguru import logger

_LANGFUSE_ENABLED_ENV = "LANGFUSE_ENABLED"

# Lazily resolved singletons. Failure to import langfuse should not break
# bot startup, so we degrade to a no-op factory.
_langfuse: Optional[Any] = None
_callback_handler_cls: Optional[Any] = None
_initialized: bool = False


def _strip(value: Optional[str]) -> str:
    return (value or "").strip()


def langfuse_enabled() -> bool:
    """Returns True if Langfuse tracing should be active."""
    flag = os.getenv(_LANGFUSE_ENABLED_ENV, "true").strip().lower()
    if flag in ("0", "false", "no", "off"):
        return False
    return bool(_strip(os.getenv("LANGFUSE_PUBLIC_KEY"))) and bool(
        _strip(os.getenv("LANGFUSE_SECRET_KEY"))
    )


def _initialize() -> None:
    """Build the Langfuse singleton on first use; safe to call repeatedly."""
    global _langfuse, _callback_handler_cls, _initialized
    if _initialized:
        return
    _initialized = True

    if not langfuse_enabled():
        logger.info(
            "Langfuse callback handler: disabled "
            "(LANGFUSE_ENABLED off or LANGFUSE_PUBLIC_KEY/SECRET_KEY missing)"
        )
        return

    try:
        from langfuse import Langfuse  # type: ignore
        from langfuse.langchain import CallbackHandler  # type: ignore
        from opentelemetry.sdk.trace import TracerProvider  # type: ignore
    except Exception as e:  # pragma: no cover - defensive against missing deps
        logger.warning(
            "Langfuse callback handler: SDK not installed ({}); "
            "rich graph tracing disabled. Run `uv sync --extra mobile`.",
            e,
        )
        return

    # Some envs use the older `LANGFUSE_BASE_URL` while the v3 SDK reads
    # `LANGFUSE_HOST`. Mirror so either works without per-developer fiddling.
    if not os.getenv("LANGFUSE_HOST") and os.getenv("LANGFUSE_BASE_URL"):
        os.environ["LANGFUSE_HOST"] = _strip(os.getenv("LANGFUSE_BASE_URL"))

    try:
        provider = TracerProvider()
        _langfuse = Langfuse(
            tracer_provider=provider,
            blocked_instrumentation_scopes=[
                # Web/server frameworks — Pipecat already covers these via
                # its own OTLP pipeline; we don't want them duplicated
                # under the Langfuse callback spans.
                "fastapi",
                "opentelemetry.instrumentation.fastapi",
                "uvicorn",
                "starlette",
                # DB / network instrumentations
                "sqlalchemy",
                "psycopg",
                "psycopg2",
                "asyncpg",
                "opentelemetry.instrumentation.requests",
                "opentelemetry.instrumentation.urllib3",
                "opentelemetry.instrumentation.aiohttp",
                "opentelemetry.instrumentation.httpx",
                "opentelemetry.instrumentation.redis",
                # Misc
                "logfire",
            ],
        )
        _callback_handler_cls = CallbackHandler
        logger.info(
            "Langfuse callback handler: initialized (host={}, public_key=***{})",
            os.getenv("LANGFUSE_HOST") or os.getenv("LANGFUSE_BASE_URL") or "<default>",
            (_strip(os.getenv("LANGFUSE_PUBLIC_KEY")) or "")[-4:],
        )
    except Exception as e:  # pragma: no cover
        logger.warning("Langfuse callback handler: init failed ({})", e)
        _langfuse = None
        _callback_handler_cls = None


def get_langfuse_handler() -> Optional[Any]:
    """Returns a fresh ``CallbackHandler`` per call, or ``None`` if disabled.

    A new handler per turn is intentional: the LangChain CallbackHandler
    keeps state (current run id, span context) for the lifetime of the
    runnable invocation, so reusing one across concurrent requests can
    leak/collide spans across sessions.
    """
    _initialize()
    if _callback_handler_cls is None:
        return None
    try:
        return _callback_handler_cls(update_trace=True)
    except Exception as e:  # pragma: no cover
        logger.warning("Langfuse callback handler: factory failed ({})", e)
        return None


def get_langfuse() -> Optional[Any]:
    """Returns the underlying ``Langfuse`` singleton, or ``None`` if disabled.

    Useful for callers that want to wrap a custom span with
    ``langfuse.start_as_current_span(...)``.
    """
    _initialize()
    return _langfuse
