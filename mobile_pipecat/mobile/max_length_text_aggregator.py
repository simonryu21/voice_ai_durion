from __future__ import annotations

from typing import AsyncIterator, Optional

from pipecat.utils.string import SENTENCE_ENDING_PUNCTUATION
from pipecat.utils.text.base_text_aggregator import Aggregation, AggregationType, BaseTextAggregator


class MaxLengthTextAggregator(BaseTextAggregator):
    """Aggregate text into larger chunks to reduce TTS fragmentation.

    This aggregator buffers streaming text and yields a chunk when either:
    - a paragraph boundary (double newline) is detected, or
    - a sentence boundary is detected and the buffer is large enough, or
    - the buffer exceeds a configured maximum size.
    """

    def __init__(
        self,
        *,
        min_chars_before_yield: int = 240,
        max_chars_before_yield: int = 700,
    ):
        super().__init__(aggregation_type=AggregationType.SENTENCE)
        self._buffer: str = ""
        self._min_chars_before_yield = min_chars_before_yield
        self._max_chars_before_yield = max_chars_before_yield

    @property
    def text(self) -> Aggregation:
        return Aggregation(text=self._buffer.strip(" "), type=AggregationType.SENTENCE)

    async def aggregate(self, text: str) -> AsyncIterator[Aggregation]:
        if not text:
            return

        for char in text:
            self._buffer += char

            if "\n\n" in self._buffer and len(self._buffer.strip()) >= 1:
                yield await self._pop_until_paragraph_boundary()
                continue

            if len(self._buffer) >= self._max_chars_before_yield:
                yield await self._pop_safely()
                continue

            if (
                char in SENTENCE_ENDING_PUNCTUATION
                and len(self._buffer.strip()) >= self._min_chars_before_yield
            ):
                yield await self._pop_safely()

    async def flush(self) -> Optional[Aggregation]:
        if not self._buffer.strip():
            return None
        return await self._pop_all()

    async def handle_interruption(self):
        await self.reset()

    async def reset(self):
        self._buffer = ""

    async def _pop_until_paragraph_boundary(self) -> Aggregation:
        paragraph_index = self._buffer.find("\n\n")
        if paragraph_index == -1:
            return await self._pop_safely()

        chunk = self._buffer[: paragraph_index + 2]
        self._buffer = self._buffer[paragraph_index + 2 :]
        return Aggregation(text=chunk.strip(" "), type=AggregationType.SENTENCE)

    async def _pop_safely(self) -> Aggregation:
        # Prefer to cut at the last newline/space to avoid chopping words mid-way.
        buffer_stripped = self._buffer.strip(" ")
        if not buffer_stripped:
            return await self._pop_all()

        cut_candidates = [
            self._buffer.rfind("\n"),
            self._buffer.rfind(" "),
        ]
        cut_at = max(cut_candidates)
        if cut_at <= 0:
            return await self._pop_all()

        chunk = self._buffer[: cut_at + 1]
        self._buffer = self._buffer[cut_at + 1 :]
        return Aggregation(text=chunk.strip(" "), type=AggregationType.SENTENCE)

    async def _pop_all(self) -> Aggregation:
        chunk = self._buffer
        self._buffer = ""
        return Aggregation(text=chunk.strip(" "), type=AggregationType.SENTENCE)
