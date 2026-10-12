"""Voice-based HILT approval collector.

Listens for :class:`HumanApprovalRequestFrame` downstream and matches the
next user transcription against approve/reject keywords to resolve the
pending request.
"""

from typing import Optional

from loguru import logger
from pipecat.frames.frames import Frame, TranscriptionFrame
from pipecat.processors.frame_processor import FrameDirection, FrameProcessor
from pipecat.processors.frameworks.langgraph.frames import (
    HumanApprovalRequestFrame,
    HumanApprovalResponseFrame,
)

APPROVE_KEYWORDS = {"예", "네", "좋아", "승인", "허가", "yes", "ok", "okay", "approve"}
REJECT_KEYWORDS = {"아니", "아니오", "거절", "취소", "no", "cancel", "reject"}


class VoiceApprovalCollector(FrameProcessor):
    """Collect HILT approval/rejection from spoken user utterances.

    When a :class:`HumanApprovalRequestFrame` arrives, the collector enters
    *pending* mode.  The next :class:`TranscriptionFrame` is scanned for
    approval or rejection keywords.  On match a
    :class:`HumanApprovalResponseFrame` is pushed upstream; otherwise the
    transcription is forwarded as-is.
    """

    def __init__(self, **kwargs):
        """Initialize the collector.

        Args:
            **kwargs: Forwarded to :class:`FrameProcessor`.
        """
        super().__init__(**kwargs)
        self._pending_request: Optional[HumanApprovalRequestFrame] = None

    async def process_frame(self, frame: Frame, direction: FrameDirection):
        """Route frames through the approval logic.

        Args:
            frame: Incoming frame.
            direction: Direction of frame flow.
        """
        await super().process_frame(frame, direction)

        if isinstance(frame, HumanApprovalRequestFrame):
            self._pending_request = frame
            await self.push_frame(frame, direction)

        elif isinstance(frame, TranscriptionFrame) and self._pending_request:
            text = frame.text.strip().lower()
            approved = any(kw in text for kw in APPROVE_KEYWORDS)
            rejected = any(kw in text for kw in REJECT_KEYWORDS)

            if approved or rejected:
                response = HumanApprovalResponseFrame(
                    request_id=self._pending_request.request_id,
                    approved=approved and not rejected,
                )
                self._pending_request = None
                await self.push_frame(response, FrameDirection.UPSTREAM)
            else:
                # Unrecognised utterance — let it pass and keep waiting
                await self.push_frame(frame, direction)
        else:
            await self.push_frame(frame, direction)
