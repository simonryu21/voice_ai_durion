"""First-sentence TTS filter.

Marks every text chunk that comes after the first sentence of an LLM response
with ``skip_tts=True`` so the TTS service skips them, while the frame still
flows downstream to the assistant context aggregator. This lets the user
*hear* only the headline sentence while still *seeing* the full reply on
screen.

The filter operates on :class:`AggregatedTextFrame` produced by
``LLMTextProcessor`` (i.e., it must be placed *after* ``LLMTextProcessor``
and before the TTS service). Operating on aggregated chunks (rather than raw
``LLMTextFrame`` tokens) avoids races with the upstream text aggregator,
which buffers tokens until a sentence/paragraph/length boundary and would
otherwise drop our per-token ``skip_tts`` marks during its flush.
"""

from __future__ import annotations

import re

from pipecat.frames.frames import (
    AggregatedTextFrame,
    Frame,
    LLMFullResponseEndFrame,
    LLMFullResponseStartFrame,
)
from pipecat.processors.frame_processor import FrameDirection, FrameProcessor

# Sentence-ending punctuation. Includes the half-width and full-width variants
# the supervisor LLM commonly emits in Korean responses.
_SENTENCE_END_RE = re.compile(r"[.?!…。?!]")


def _clone_aggregated(src: AggregatedTextFrame, new_text: str) -> AggregatedTextFrame:
    """Return a new AggregatedTextFrame with replaced text, preserving flags."""
    new = AggregatedTextFrame(text=new_text, aggregated_by=src.aggregated_by)
    new.skip_tts = src.skip_tts
    new.includes_inter_frame_spaces = src.includes_inter_frame_spaces
    new.append_to_context = src.append_to_context
    new.context_id = src.context_id
    return new


class FirstSentenceTTSFilter(FrameProcessor):
    """Suppress TTS for everything after the first sentence of each LLM response.

    Strategy:
      * Reset state on ``LLMFullResponseStartFrame``.
      * For each ``AggregatedTextFrame`` flowing downstream:
          - If the first sentence is already complete, forward the frame
            with ``skip_tts=True``.
          - Otherwise scan the text for the first sentence-ending punctuation
            (``.``/``?``/``!`` and full-width variants). If found, split the
            chunk: forward the leading part untouched (TTS speaks it),
            mark the first sentence as done, then forward the trailing
            part with ``skip_tts=True``. If no boundary is found yet, forward
            unchanged and keep scanning subsequent chunks.
      * Reset state on ``LLMFullResponseEndFrame``.

    Non-text frames pass through untouched. ``skip_tts`` does not affect the
    downstream assistant context aggregator, so the full reply still ends up
    in the LLM context.
    """

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._first_sentence_done: bool = False

    async def process_frame(self, frame: Frame, direction: FrameDirection):
        await super().process_frame(frame, direction)

        if isinstance(frame, LLMFullResponseStartFrame):
            self._first_sentence_done = False
            await self.push_frame(frame, direction)
            return

        if isinstance(frame, LLMFullResponseEndFrame):
            self._first_sentence_done = False
            await self.push_frame(frame, direction)
            return

        if isinstance(frame, AggregatedTextFrame) and direction == FrameDirection.DOWNSTREAM:
            text = frame.text or ""

            if self._first_sentence_done:
                frame.skip_tts = True
                await self.push_frame(frame, direction)
                return

            match = _SENTENCE_END_RE.search(text)
            if match is None:
                # Still inside the first sentence; pass through.
                await self.push_frame(frame, direction)
                return

            split_idx = match.end()
            head = text[:split_idx]
            tail = text[split_idx:]

            if head:
                head_frame = _clone_aggregated(frame, head)
                await self.push_frame(head_frame, direction)

            self._first_sentence_done = True

            if tail.strip():
                tail_frame = _clone_aggregated(frame, tail)
                tail_frame.skip_tts = True
                await self.push_frame(tail_frame, direction)
            return

        await self.push_frame(frame, direction)
