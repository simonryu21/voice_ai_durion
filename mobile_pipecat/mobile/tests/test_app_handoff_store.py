"""Tests for AppHandoffStore lifecycle."""

from datetime import datetime, timezone

from app_handoff.store import AppHandoffRecord, AppHandoffStore


def _record(handoff_id: str = "h1", session_pc_id: str = "s1", **kwargs) -> AppHandoffRecord:
    base = dict(
        handoff_id=handoff_id,
        session_pc_id=session_pc_id,
        app_id="naver_map",
        package_name="com.nhn.android.nmap",
        requested_at=datetime.now(timezone.utc),
    )
    base.update(kwargs)
    return AppHandoffRecord(**base)


def test_register_then_active():
    store = AppHandoffStore()
    rec = _record("h1")
    store.register(rec)
    assert store.active("s1").handoff_id == "h1"


def test_register_silently_drops_previous():
    store = AppHandoffStore()
    store.register(_record("h1"))
    dropped = store.register(_record("h2"))
    assert dropped is not None and dropped.handoff_id == "h1"
    assert dropped.status == "dropped"
    assert store.active("s1").handoff_id == "h2"
    # history retains the dropped one
    hist = store.history("s1")
    assert len(hist) == 1 and hist[0].handoff_id == "h1" and hist[0].status == "dropped"


def test_mark_opened():
    store = AppHandoffStore()
    store.register(_record("h1"))
    rec = store.mark_opened("s1", "h1", payload={"execution_path_used": "applink"})
    assert rec.status == "opened"
    assert rec.opened_at is not None
    assert rec.last_payload == {"execution_path_used": "applink"}


def test_mark_returned_moves_to_history():
    store = AppHandoffStore()
    store.register(_record("h1"))
    rec = store.mark_returned("s1", "h1", payload={"duration_ms": 1234})
    assert rec.status == "returned"
    assert rec.returned_at is not None
    assert store.active("s1") is None
    assert store.history("s1")[-1].handoff_id == "h1"


def test_mark_failed_moves_to_history():
    store = AppHandoffStore()
    store.register(_record("h1"))
    rec = store.mark_failed("s1", "h1", payload={"reason": "no_handler"})
    assert rec.status == "failed"
    assert rec.failed_at is not None
    assert store.active("s1") is None


def test_mark_timeout_moves_to_history():
    store = AppHandoffStore()
    store.register(_record("h1"))
    rec = store.mark_timeout("s1", "h1", payload={"elapsed_ms": 300000})
    assert rec.status == "timeout"
    assert rec.failed_at is not None
    assert store.active("s1") is None


def test_unknown_handoff_id_returns_none():
    store = AppHandoffStore()
    store.register(_record("h1"))
    assert store.mark_opened("s1", "different-id") is None
    # active record is unaffected
    assert store.active("s1").handoff_id == "h1"
    assert store.active("s1").status == "requested"


def test_no_session_pc_id_is_safe():
    store = AppHandoffStore()
    rec = _record("h1", session_pc_id=None)
    assert store.register(rec) is None
    assert store.active(None) is None
    assert store.mark_opened(None, "h1") is None


def test_history_isolated_per_session():
    store = AppHandoffStore()
    store.register(_record("a", session_pc_id="s1"))
    store.register(_record("b", session_pc_id="s2"))
    store.mark_returned("s1", "a")
    assert len(store.history("s1")) == 1
    assert len(store.history("s2")) == 0
    assert store.active("s2").handoff_id == "b"
