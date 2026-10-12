"""AppConcierge handoff lifecycle (Phase 3a — server side).

Pieces:

- :class:`AppHandoffRecord` / :class:`AppHandoffStore` — per-session
  in-memory tracking of `requested → opened → returned | failed | timeout`.
- :func:`render_execution_paths` — fills catalog templates with parameter
  values (URL-encoded) so the client receives ready-to-launch URIs.
- :func:`make_app_invoke_dispatcher` — produces an
  :data:`AppInvokeDispatcher` that pushes the TTS intro + RTVI server
  message and registers the record. Plugged into the per-app
  ``open_<app_id>`` Pipecat functions in
  :mod:`app_concierge_tools`.
- :func:`build_back_confirmed_event_text` etc. — pure helpers that build
  the LLM context text for each lifecycle event so the wiring code in
  ``voice_ai.py`` stays small and testable.
"""

from app_handoff.dispatcher import make_app_invoke_dispatcher
from app_handoff.handlers import (
    build_back_confirmed_event_text,
    build_failed_event_text,
    build_timeout_event_text,
)
from app_handoff.store import AppHandoffRecord, AppHandoffStore
from app_handoff.uri import RenderedExecutionPath, render_execution_paths

__all__ = [
    "AppHandoffRecord",
    "AppHandoffStore",
    "RenderedExecutionPath",
    "build_back_confirmed_event_text",
    "build_failed_event_text",
    "build_timeout_event_text",
    "make_app_invoke_dispatcher",
    "render_execution_paths",
]
