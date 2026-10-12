"""HILT (Human-in-the-Loop) frames for the LangGraph integration.

These frames enable asynchronous approval flows between the LangGraph
processor and downstream consumers (TTS, RTVI client, etc.).
"""

from dataclasses import dataclass
from typing import Any, Optional

from pipecat.frames.frames import ControlFrame, DataFrame


@dataclass
class HumanApprovalRequestFrame(ControlFrame):
    """Request for human approval before executing a tool.

    Pushed downstream by the LangGraph processor when a LangGraph
    ``interrupt()`` fires.  Downstream consumers (TTS, RTVI client)
    present the request to the user.

    Parameters:
        request_id: Unique identifier for this request (used to match the response).
        tool_name: Name of the tool pending approval.
        action_summary: Human-readable summary shown to the user.
        interrupt_value: Raw payload from LangGraph ``interrupt()``.
        thread_id: LangGraph thread ID for the current session.
    """

    request_id: str
    tool_name: str
    action_summary: str
    interrupt_value: Any = None
    thread_id: str = ""


@dataclass
class HumanApprovalResponseFrame(DataFrame):
    """Response to a human approval request.

    Created by a downstream collector (voice or RTVI) and pushed upstream
    so that the LangGraph processor can resume the interrupted graph.

    Parameters:
        request_id: Matching :attr:`HumanApprovalRequestFrame.request_id`.
        approved: ``True`` to approve, ``False`` to reject.
        reason: Optional rejection reason.
    """

    request_id: str
    approved: bool = True
    reason: Optional[str] = None
