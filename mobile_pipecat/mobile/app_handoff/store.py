"""In-memory store for app handoff lifecycle records.

One record per active app launch. Per Q6 (Phase 3a decisions): when a new
``invoke_app`` is dispatched while another handoff is still active for the
same session, the previous record is **silently dropped** (no cancel
message is sent to the client).

Storage layout: ``Dict[session_pc_id, AppHandoffRecord]``. The store keeps
at most one *active* (non-terminal) record per session; terminal records
(returned / failed / timeout) move into a per-session history list.
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Literal, Optional

from loguru import logger
from pydantic import BaseModel, ConfigDict, Field

HandoffStatus = Literal[
    "requested",  # dispatcher fired, waiting for client to ack
    "opened",  # client successfully started the app
    "returned",  # user came back from the external app
    "failed",  # client could not launch (no_handler, user_cancelled, ...)
    "timeout",  # client never reported back within timeout_ms
    "dropped",  # superseded by a newer invoke (see Q6)
]


_TERMINAL: frozenset[str] = frozenset({"returned", "failed", "timeout", "dropped"})


class AppHandoffRecord(BaseModel):
    """One handoff lifecycle entry."""

    model_config = ConfigDict(extra="forbid")

    handoff_id: str
    session_pc_id: Optional[str] = None
    user_id: Optional[str] = None
    app_id: str
    package_name: str
    parameters: dict[str, Any] = Field(default_factory=dict)
    spoken_intro: str = ""
    rendered_uris: list[str] = Field(default_factory=list)
    status: HandoffStatus = "requested"

    requested_at: datetime
    opened_at: Optional[datetime] = None
    returned_at: Optional[datetime] = None
    failed_at: Optional[datetime] = None

    # Free-form payloads from the client for opened / failed / returned.
    last_payload: Optional[dict[str, Any]] = None


class AppHandoffStore:
    """Per-session in-memory tracker.

    Thread-/coroutine-safe is **not** required: voice_ai.py runs all
    handler callbacks on the asyncio event loop, no concurrent calls per
    session.
    """

    def __init__(self) -> None:
        self._active: dict[str, AppHandoffRecord] = {}
        self._history: dict[str, list[AppHandoffRecord]] = {}

    # ── write side ─────────────────────────────────────────────────────

    def register(self, record: AppHandoffRecord) -> Optional[AppHandoffRecord]:
        """Register a new active handoff for a session.

        If a previous handoff is still active for this session it is moved
        to history with status ``"dropped"`` and returned (Q6: silent drop).
        """
        dropped: Optional[AppHandoffRecord] = None
        sid = record.session_pc_id
        if sid:
            existing = self._active.pop(sid, None)
            if existing is not None and existing.status not in _TERMINAL:
                existing.status = "dropped"
                self._history.setdefault(sid, []).append(existing)
                dropped = existing
            self._active[sid] = record
        logger.info(
            "[AppHandoffStore] register handoff_id={} app_id={} session_pc_id={} dropped_prev={}",
            record.handoff_id,
            record.app_id,
            sid,
            dropped.handoff_id if dropped else None,
        )
        return dropped

    def mark_opened(
        self, session_pc_id: Optional[str], handoff_id: str, payload: Optional[dict] = None
    ) -> Optional[AppHandoffRecord]:
        rec = self._get(session_pc_id, handoff_id)
        if rec is None:
            logger.info(
                "[AppHandoffStore] mark_opened: no active record handoff_id={} session_pc_id={}",
                handoff_id,
                session_pc_id,
            )
            return None
        rec.status = "opened"
        rec.opened_at = datetime.now(timezone.utc)
        rec.last_payload = payload
        logger.info(
            "[AppHandoffStore] mark_opened handoff_id={} app_id={} session_pc_id={}",
            handoff_id,
            rec.app_id,
            session_pc_id,
        )
        return rec

    def mark_returned(
        self, session_pc_id: Optional[str], handoff_id: str, payload: Optional[dict] = None
    ) -> Optional[AppHandoffRecord]:
        return self._terminate(session_pc_id, handoff_id, "returned", payload, "returned_at")

    def mark_failed(
        self, session_pc_id: Optional[str], handoff_id: str, payload: Optional[dict] = None
    ) -> Optional[AppHandoffRecord]:
        return self._terminate(session_pc_id, handoff_id, "failed", payload, "failed_at")

    def mark_timeout(
        self, session_pc_id: Optional[str], handoff_id: str, payload: Optional[dict] = None
    ) -> Optional[AppHandoffRecord]:
        return self._terminate(session_pc_id, handoff_id, "timeout", payload, "failed_at")

    # ── read side ──────────────────────────────────────────────────────

    def active(self, session_pc_id: Optional[str]) -> Optional[AppHandoffRecord]:
        if not session_pc_id:
            return None
        return self._active.get(session_pc_id)

    def history(self, session_pc_id: Optional[str]) -> list[AppHandoffRecord]:
        if not session_pc_id:
            return []
        return list(self._history.get(session_pc_id, []))

    # ── internals ──────────────────────────────────────────────────────

    def _get(self, session_pc_id: Optional[str], handoff_id: str) -> Optional[AppHandoffRecord]:
        """Return the active record only if its handoff_id matches.

        Lifecycle messages must reference the *current* active handoff;
        out-of-order or unknown ids are ignored (the caller logs them).
        """
        if not session_pc_id:
            return None
        rec = self._active.get(session_pc_id)
        if rec is None or rec.handoff_id != handoff_id:
            return None
        return rec

    def _terminate(
        self,
        session_pc_id: Optional[str],
        handoff_id: str,
        status: HandoffStatus,
        payload: Optional[dict],
        ts_field: str,
    ) -> Optional[AppHandoffRecord]:
        rec = self._get(session_pc_id, handoff_id)
        if rec is None or session_pc_id is None:
            logger.info(
                "[AppHandoffStore] _terminate: no active record status={} handoff_id={} session_pc_id={}",
                status,
                handoff_id,
                session_pc_id,
            )
            return None
        rec.status = status
        setattr(rec, ts_field, datetime.now(timezone.utc))
        rec.last_payload = payload
        # move to history
        self._active.pop(session_pc_id, None)
        self._history.setdefault(session_pc_id, []).append(rec)
        logger.info(
            "[AppHandoffStore] _terminate status={} handoff_id={} app_id={} session_pc_id={}",
            status,
            handoff_id,
            rec.app_id,
            session_pc_id,
        )
        return rec
