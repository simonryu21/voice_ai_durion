#
# Copyright (c) 2024-2026, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#

"""User turn stop strategy based on turn detection analyzers."""

import asyncio

from loguru import logger

from pipecat.audio.turn.base_turn_analyzer import BaseTurnAnalyzer, EndOfTurnState
from pipecat.audio.vad.vad_analyzer import VAD_STOP_SECS
from pipecat.frames.frames import (
    Frame,
    InputAudioRawFrame,
    MetricsFrame,
    SpeechControlParamsFrame,
    StartFrame,
    STTMetadataFrame,
    TranscriptionFrame,
    VADUserStartedSpeakingFrame,
    VADUserStoppedSpeakingFrame,
)
from pipecat.metrics.metrics import MetricsData
from pipecat.turns.types import ProcessFrameResult
from pipecat.turns.user_stop.base_user_turn_stop_strategy import BaseUserTurnStopStrategy
from pipecat.utils.asyncio.task_manager import BaseTaskManager


class TurnAnalyzerUserTurnStopStrategy(BaseUserTurnStopStrategy):
    """User turn stop strategy that uses a turn detection model to determine if the user is done speaking.

    This strategy feeds audio, VAD, and transcription frames to a turn
    detection model (``BaseTurnAnalyzer``) that predicts when the user has
    finished their turn. Once the model indicates the turn is complete, the
    strategy waits for a final transcription before triggering the end of
    the user's turn.

    For services that support finalization (TranscriptionFrame.finalized=True),
    the turn can be triggered immediately once the finalized transcript is
    received. Otherwise, an STT timeout (adjusted by VAD stop_secs) is used
    as a fallback.

    Optional ``trigger_delay_secs`` adds a short delay before triggering after
    the analyzer says COMPLETE and transcript is finalized. If the user starts
    speaking again during this delay (e.g. brief pause between phrases), the
    trigger is cancelled so the turn is not cut off.
    """

    def __init__(
        self,
        *,
        turn_analyzer: BaseTurnAnalyzer,
        trigger_delay_secs: float = 0.0,
        **kwargs,
    ):
        """Initialize the user turn stop strategy.

        Args:
            turn_analyzer: The turn detection analyzer instance to detect end of user turn.
            trigger_delay_secs: If > 0, wait this many seconds after COMPLETE + finalized
                before triggering. If the user starts speaking again during the delay,
                the trigger is cancelled. Helps avoid cutting off users who pause briefly.
            **kwargs: Additional keyword arguments.
        """
        super().__init__(**kwargs)
        self._turn_analyzer = turn_analyzer
        self._trigger_delay_secs = max(0.0, trigger_delay_secs)
        self._stt_timeout: float = 0.0  # STT P99 latency from STTMetadataFrame
        self._stop_secs: float = 0.0  # VAD stop_secs from VADUserStoppedSpeakingFrame

        self._stop_secs_warned: bool = False

        self._text = ""
        self._turn_complete = False
        self._vad_user_speaking = False
        self._vad_stopped_time: float | None = None  # Track when VAD stopped was received
        self._transcript_finalized = False
        self._timeout_task: Optional[asyncio.Task] = None
        self._timeout_expired: bool = False
        self._trigger_delay_task: Optional[asyncio.Task] = None
        # True after VAD start until we trigger; used to avoid assuming complete in fallback
        # when the turn was started by VAD (we must wait for VAD stop + analyzer COMPLETE).
        self._had_vad_start_in_turn = False

    async def reset(self):
        """Reset the strategy to its initial state."""
        await super().reset()
        self._text = ""
        self._turn_complete = False
        self._vad_user_speaking = False
        self._vad_stopped_time = None
        self._transcript_finalized = False
        self._had_vad_start_in_turn = False
        self._timeout_expired = False
        if self._timeout_task:
            await self.task_manager.cancel_task(self._timeout_task)
            self._timeout_task = None
        if self._trigger_delay_task:
            await self.task_manager.cancel_task(self._trigger_delay_task)
            self._trigger_delay_task = None
            logger.debug(f"{self}: trigger delay cancelled (reset)")

    async def setup(self, task_manager: BaseTaskManager):
        """Initialize the strategy with the given task manager.

        Args:
            task_manager: The task manager to be associated with this instance.
        """
        await super().setup(task_manager)

    async def cleanup(self):
        """Cleanup the strategy."""
        await super().cleanup()
        await self._turn_analyzer.cleanup()
        if self._timeout_task:
            await self.task_manager.cancel_task(self._timeout_task)
            self._timeout_task = None
        if self._trigger_delay_task:
            await self.task_manager.cancel_task(self._trigger_delay_task)
            self._trigger_delay_task = None
            logger.debug(f"{self}: trigger delay cancelled (user started speaking)")

    async def process_frame(self, frame: Frame) -> ProcessFrameResult:
        """Process an incoming frame to update the turn analyzer and strategy state.

        Args:
            frame: The frame to be analyzed.

        Returns:
            Always returns CONTINUE so subsequent stop strategies are evaluated.
        """
        await super().process_frame(frame)

        if isinstance(frame, StartFrame):
            await self._start(frame)
        elif isinstance(frame, STTMetadataFrame):
            self._stt_timeout = frame.ttfs_p99_latency
            self._stop_secs_warned = False
        elif isinstance(frame, VADUserStartedSpeakingFrame):
            await self._handle_vad_user_started_speaking(frame)
        elif isinstance(frame, VADUserStoppedSpeakingFrame):
            await self._handle_vad_user_stopped_speaking(frame)
        elif isinstance(frame, InputAudioRawFrame):
            await self._handle_input_audio(frame)
        elif isinstance(frame, TranscriptionFrame):
            await self._handle_transcription(frame)

        return ProcessFrameResult.CONTINUE

    async def _start(self, frame: StartFrame):
        """Process the start frame to configure the turn analyzer."""
        self._turn_analyzer.set_sample_rate(frame.audio_in_sample_rate)
        await self.broadcast_frame(SpeechControlParamsFrame, turn_params=self._turn_analyzer.params)

    async def _handle_input_audio(self, frame: InputAudioRawFrame):
        """Handle input audio to check if the turn is completed."""
        state = self._turn_analyzer.append_audio(frame.audio, self._vad_user_speaking)

        # Streaming analyzers (e.g. KrispVivaTurn) detect turn completion
        # frame-by-frame inside append_audio, so COMPLETE is returned here
        # rather than in analyze_end_of_turn. Batch analyzers (BaseSmartTurn)
        # return COMPLETE here only on a silence timeout. In either case we
        # consume and push metrics immediately while they're fresh.
        if state == EndOfTurnState.COMPLETE:
            _, prediction = await self._turn_analyzer.analyze_end_of_turn()
            await self._handle_prediction_result(prediction)
            self._turn_complete = True
            await self._maybe_trigger_user_turn_stopped()

    async def _handle_vad_user_started_speaking(self, frame: VADUserStartedSpeakingFrame):
        """Handle when the VAD indicates the user is speaking."""
        # Sync Smart Turn pre-speech buffering with VAD start delay
        self._turn_analyzer.update_vad_start_secs(frame.start_secs)
        self._turn_complete = False
        self._vad_user_speaking = True
        self._vad_stopped_time = None
        self._transcript_finalized = False
        self._had_vad_start_in_turn = True
        self._timeout_expired = False
        # Cancel any pending timeout or trigger delay (user continued speaking)
        if self._timeout_task:
            await self.task_manager.cancel_task(self._timeout_task)
            self._timeout_task = None
        if self._trigger_delay_task:
            await self.task_manager.cancel_task(self._trigger_delay_task)
            self._trigger_delay_task = None

    async def _handle_vad_user_stopped_speaking(self, frame: VADUserStoppedSpeakingFrame):
        """Handle when the VAD indicates the user has stopped speaking."""
        self._vad_user_speaking = False
        self._stop_secs = frame.stop_secs
        self._vad_stopped_time = frame.timestamp

        state, prediction = await self._turn_analyzer.analyze_end_of_turn()
        await self._handle_prediction_result(prediction)

        # The user stopped speaking and the turn is complete, we now need to
        # wait for transcriptions.
        self._turn_complete = state == EndOfTurnState.COMPLETE

        # Start the STT timeout (adjusted by VAD stop_secs since that time already elapsed)
        timeout = max(0, self._stt_timeout - self._stop_secs)

        if not self._stop_secs_warned:
            if self._stop_secs != VAD_STOP_SECS:
                self._stop_secs_warned = True
                logger.warning(
                    f"{self}: VAD stop_secs ({self._stop_secs}s) differs from the "
                    f"recommended default ({VAD_STOP_SECS}s). Built-in p99 latency "
                    f"values assume stop_secs={VAD_STOP_SECS}. Re-run "
                    f"https://github.com/pipecat-ai/stt-benchmark with your settings "
                    f"and pass the TTFS P99 latency result as ttfs_p99_latency to "
                    f"your STT service."
                )
            if self._stt_timeout > 0 and self._stop_secs >= self._stt_timeout:
                self._stop_secs_warned = True
                logger.warning(
                    f"{self}: VAD stop_secs ({self._stop_secs}s) >= STT p99 latency "
                    f"({self._stt_timeout}s). STT wait timeout collapsed to 0s, which "
                    f"may cause delayed turn detection specified by the "
                    f"user_turn_stop_timeout parameter in the LLMUserAggregatorParams."
                )

        self._timeout_task = self.task_manager.create_task(
            self._timeout_handler(timeout), f"{self}::_timeout_handler"
        )
        # Make sure the task is scheduled.
        await asyncio.sleep(0)

    async def _handle_transcription(self, frame: TranscriptionFrame):
        """Handle user transcription."""
        # We don't really care about the content.
        self._text = frame.text
        if frame.finalized:
            self._transcript_finalized = True
            # For finalized transcripts, trigger immediately if turn is complete
            await self._maybe_trigger_user_turn_stopped()
        elif self._timeout_expired and self._turn_complete:
            # The p99 timeout already elapsed without a transcript. Now that
            # we have one, trigger the turn stop immediately. This handles the
            # case where the transcript is slower to arrive than the p99 timeout,
            # trigger the user turn to stop immediately.
            await self.trigger_user_turn_stopped()
            return

        # Fallback: handle transcripts when no VAD stop was received.
        # This handles edge cases where transcripts arrive without VAD firing.
        # _vad_stopped_time is None means VAD stopped hasn't been received yet.
        # Do not assume complete when this turn was started by VAD: we must wait for
        # VAD stop and the turn analyzer (COMPLETE vs INCOMPLETE) to avoid cutting off
        # the user mid-sentence.
        if not self._vad_user_speaking and self._vad_stopped_time is None:
            if self._had_vad_start_in_turn:
                # VAD started this turn; wait for VAD stop and analyzer result.
                return
            # Cancel existing fallback timeout if any
            if self._timeout_task:
                await self.task_manager.cancel_task(self._timeout_task)
            # Without VAD/turn analyzer data, assume turn is complete
            self._turn_complete = True
            timeout = max(0, self._stt_timeout - self._stop_secs)
            self._timeout_task = self.task_manager.create_task(
                self._timeout_handler(timeout), f"{self}::_timeout_handler"
            )
            # Make sure the task is scheduled.
            await asyncio.sleep(0)

    async def _handle_prediction_result(self, result: MetricsData | None):
        """Handle a prediction result event from the turn analyzer."""
        if result:
            await self.push_frame(MetricsFrame(data=[result]))

    async def _trigger_delay_handler(self):
        """Wait for trigger_delay_secs then trigger if user did not start speaking again."""
        logger.debug(f"{self}: trigger delay scheduled ({self._trigger_delay_secs}s)")
        try:
            await asyncio.sleep(self._trigger_delay_secs)
        except asyncio.CancelledError:
            return
        finally:
            self._trigger_delay_task = None

        logger.debug(f"{self}: trigger delay fired, triggering user turn stopped")
        await self._maybe_trigger_user_turn_stopped(after_delay=True)

    async def _timeout_handler(self, timeout: float):
        """Wait for the timeout then trigger user turn stopped if conditions met.

        Args:
            timeout: The timeout in seconds to wait.
        """
        try:
            await asyncio.sleep(timeout)
        except asyncio.CancelledError:
            return
        finally:
            self._timeout_task = None

        self._timeout_expired = True
        await self._maybe_trigger_user_turn_stopped()

    async def _maybe_trigger_user_turn_stopped(self, *, after_delay: bool = False):
        """Trigger user turn stopped if conditions are met.

        Conditions:
        - We have transcription text
        - Turn analyzer indicates turn is complete
        - Either the timeout has elapsed OR we have a finalized transcript

        Args:
            after_delay: If True, called from _trigger_delay_handler after delay;
                trigger immediately and do not schedule another delay.
        """
        if not self._text or not self._turn_complete:
            return

        if after_delay:
            self._had_vad_start_in_turn = False
            await self.trigger_user_turn_stopped()
            return

        # For finalized transcripts, trigger (optionally after a short delay)
        if self._transcript_finalized:
            # Cancel any remaining timeout since we're handling trigger
            if self._timeout_task:
                await self.task_manager.cancel_task(self._timeout_task)
                self._timeout_task = None
            if self._trigger_delay_secs > 0:
                # Wait before triggering; if user starts speaking again, delay is cancelled
                if self._trigger_delay_task:
                    return  # already scheduled
                self._trigger_delay_task = self.task_manager.create_task(
                    self._trigger_delay_handler(), f"{self}::_trigger_delay_handler"
                )
                return
            self._had_vad_start_in_turn = False
            await self.trigger_user_turn_stopped()
            return

        # For non-finalized, only trigger if timeout task has completed.
        # Apply the same trigger_delay_secs so brief pauses don't cut off the user.
        if self._timeout_task is None:
            if self._trigger_delay_secs > 0:
                if self._trigger_delay_task:
                    return
                self._trigger_delay_task = self.task_manager.create_task(
                    self._trigger_delay_handler(), f"{self}::_trigger_delay_handler"
                )
                return
            self._had_vad_start_in_turn = False
            await self.trigger_user_turn_stopped()
