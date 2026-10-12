"""Meeting mode detector processor.

Detects meeting mode based on speaker changes from Soniox diarization:
1. Speaker changes detected from speaker_id in transcription frames
2. Conversation gap between speakers is within threshold
"""

import asyncio
from collections import deque
from datetime import datetime
from typing import Any, Awaitable, Callable, Deque, Dict, List, Optional

from loguru import logger
from pipecat.frames.frames import (
    Frame,
    InterimTranscriptionFrame,
    TranscriptionFrame,
    UserStartedSpeakingFrame,
    UserStoppedSpeakingFrame,
)
from pipecat.processors.frame_processor import FrameDirection, FrameProcessor


class MeetingModeDetector(FrameProcessor):
    """Detects meeting mode based on speaker changes from Soniox diarization.

    Detection criteria:
    1. Speaker changes detected from speaker_id in transcription frames
    2. Conversation gap between speakers is within threshold
    """

    # Buffer size constants
    MAX_SPEECH_SEGMENTS: int = 10
    MAX_SPEAKER_CHANGES: int = 20

    # Meeting detection constants
    MIN_UNIQUE_SPEAKERS: int = 2
    SECOND_LAST_SEGMENT_INDEX: int = -2

    def __init__(
        self,
        *,
        conversation_gap_threshold: float = 2.0,  # seconds between speakers
        min_speech_segments: int = 2,  # minimum speech segments required for meeting mode detection
        mute_duration_threshold: float = 90.0,  # seconds of silence to auto-end
        **kwargs,
    ):
        """Initialize meeting mode detector.

        Args:
            conversation_gap_threshold: Maximum gap (seconds) between speakers to consider as conversation (default: 2.0)
            min_speech_segments: Minimum number of speech segments required to detect meeting mode (default: 2)
            mute_duration_threshold: Duration (seconds) of silence to trigger meeting mode end (default: 90.0)
        """
        super().__init__(**kwargs)
        self._conversation_gap_threshold = conversation_gap_threshold
        self._min_speech_segments = min_speech_segments
        self._mute_duration_threshold = mute_duration_threshold

        # Track speech segments and timing
        self._speech_segments: Deque[Dict[str, Any]] = deque(maxlen=self.MAX_SPEECH_SEGMENTS)
        self._current_speech_start: Optional[datetime] = None
        self._last_speech_end: Optional[datetime] = None

        # Track speaker changes from Soniox diarization
        self._speaker_changes: Deque[tuple[str, datetime]] = deque(maxlen=self.MAX_SPEAKER_CHANGES)

        self._meeting_mode_detected = False
        self._detection_callback = None
        self._mute_check_task: Optional[asyncio.Task] = None
        self._end_callback = None
        self._meeting_messages: List[Dict[str, Any]] = []
        self._is_meeting_mode_active = False

    def set_detection_callback(self, callback: Optional[Callable[[str], Awaitable[None]]]):
        """Set callback function to be called when meeting mode is detected.

        Args:
            callback: Async function that takes a reason string argument
        """
        self._detection_callback = callback

    def set_end_callback(self, callback: Optional[Callable[[str], Awaitable[None]]]):
        """Set callback function to be called when meeting mode should end.

        Args:
            callback: Async function that takes a reason string argument
        """
        self._end_callback = callback

    def set_meeting_mode_active(self, active: bool):
        """Set meeting mode active state and manage message tracking.

        Args:
            active: True to start tracking, False to stop
        """
        self._is_meeting_mode_active = active
        if active:
            # Start tracking messages
            self._meeting_messages = []
            logger.debug("Meeting mode activated - started tracking messages")
        else:
            logger.debug(
                f"Meeting mode deactivated - tracked {len(self._meeting_messages)} messages"
            )

    def get_meeting_messages(self) -> List[Dict[str, Any]]:
        """Get all messages tracked during meeting mode.

        Returns:
            List of message dictionaries with role, content, timestamp, and speaker_id
        """
        return self._meeting_messages.copy()

    def clear_meeting_messages(self):
        """Clear tracked meeting messages."""
        self._meeting_messages = []
        logger.debug("Cleared meeting messages")

    async def process_frame(self, frame: Frame, direction: FrameDirection):
        """Process frames to detect meeting mode patterns."""
        await super().process_frame(frame, direction)

        if isinstance(frame, UserStartedSpeakingFrame):
            self._current_speech_start = datetime.now()
            await self._cancel_mute_check()

        elif isinstance(frame, UserStoppedSpeakingFrame):
            if self._current_speech_start:
                duration = (datetime.now() - self._current_speech_start).total_seconds()

                segment_data = {
                    "start": self._current_speech_start,
                    "end": datetime.now(),
                    "duration": duration,
                }

                self._speech_segments.append(segment_data)
                self._last_speech_end = datetime.now()
                self._current_speech_start = None

                await self._check_continuous_speech()
                await self._start_mute_check()

        elif isinstance(frame, (InterimTranscriptionFrame, TranscriptionFrame)):
            # Use both interim and final transcriptions for fast speaker-change detection.
            await self._process_transcription(frame)

            # Only store final transcriptions as meeting messages when active.
            if isinstance(frame, TranscriptionFrame) and self._is_meeting_mode_active:
                await self._store_meeting_message(frame)

        await self.push_frame(frame, direction)

    def _extract_speaker_id(self, frame: TranscriptionFrame) -> Optional[str]:
        """Extract speaker_id from transcription frame.

        When multiple tokens are present, returns the most frequent speaker_id
        to handle cases where speaker changes within a single transcription.

        Args:
            frame: TranscriptionFrame to extract speaker_id from

        Returns:
            speaker_id if found, None otherwise
        """
        if not (hasattr(frame, "result") and frame.result):
            return None

        # Try to extract from result
        if isinstance(frame.result, dict):
            return frame.result.get("speaker_id") or frame.result.get("speaker")
        elif isinstance(frame.result, list):
            # Count speaker occurrences to find the most frequent one
            speaker_counts = {}
            for token in frame.result:
                if isinstance(token, dict):
                    speaker_id = token.get("speaker_id") or token.get("speaker")
                    if speaker_id is not None:
                        speaker_counts[speaker_id] = speaker_counts.get(speaker_id, 0) + 1

            if speaker_counts:
                # Return the most frequent speaker_id
                most_frequent = max(speaker_counts.items(), key=lambda x: x[1])[0]
                # logger.debug(
                #    f"Extracted speaker_id from {len(frame.result)} tokens: "
                #    f"{most_frequent} (count: {speaker_counts[most_frequent]}, "
                #    f"all speakers: {speaker_counts})"
                # )
                return most_frequent

        # Try frame attributes
        if hasattr(frame, "speaker_id") and frame.speaker_id:
            return frame.speaker_id
        if hasattr(frame, "speaker") and frame.speaker:
            return frame.speaker

        return None

    async def _process_transcription(self, frame: TranscriptionFrame):
        """Process transcription frame to detect speaker changes from speaker_id."""
        speaker_id = self._extract_speaker_id(frame)
        if not speaker_id:
            return

        now = datetime.now()
        previous_speaker = None
        if self._speaker_changes:
            previous_speaker, _ = self._speaker_changes[-1]

        if previous_speaker and previous_speaker != speaker_id:
            logger.debug(f"Speaker change detected: {previous_speaker} -> {speaker_id}")
            self._speaker_changes.append((speaker_id, now))
            await self._check_speaker_changes()
        elif not previous_speaker:
            self._speaker_changes.append((speaker_id, now))

    async def _check_speaker_changes(self):
        """Check if speaker changes should trigger meeting mode detection.

        Detects meeting mode based on:
        1. Multiple unique speakers detected
        2. Speaker changes within conversation gap threshold
        """
        if self._meeting_mode_detected:
            return

        # Check if we have enough speaker changes to detect meeting mode
        unique_speakers = self._get_speaker_change_info()

        # If we have multiple unique speakers, trigger meeting mode as soon as the first change is observed.
        if len(unique_speakers) >= self.MIN_UNIQUE_SPEAKERS:
            if len(self._speaker_changes) >= 2:
                # Immediate trigger on the first detected change (e.g., user_1 -> user_2)
                if len(self._speaker_changes) == 2:
                    logger.info(
                        f"✅ Meeting mode detected: First speaker change "
                        f"with {len(unique_speakers)} speakers: {unique_speakers}"
                    )
                    await self._trigger_meeting_mode_detection("speaker_diarization")
                    return

                # For subsequent changes, keep the gap-based guard to avoid stale triggers.
                last_change = self._speaker_changes[-1]
                second_last_change = self._speaker_changes[-2]

                gap = (last_change[1] - second_last_change[1]).total_seconds()

                if gap <= self._conversation_gap_threshold:
                    logger.info(
                        f"✅ Meeting mode detected: Speaker change from Soniox diarization "
                        f"(gap={gap:.2f}s, {len(unique_speakers)} speakers: {unique_speakers})"
                    )
                    await self._trigger_meeting_mode_detection("speaker_diarization")
                    return

        # Fallback to continuous speech check
        await self._check_continuous_speech()

    def _has_speaker_changes(self) -> bool:
        """Check if we have any speaker changes detected.

        Returns:
            True if speaker changes exist, False otherwise
        """
        return len(self._speaker_changes) > 0

    def _get_speaker_change_info(self) -> set[str]:
        """Get information about detected speaker changes.

        Returns:
            Set of unique speaker IDs
        """
        return set(speaker for speaker, _ in self._speaker_changes)

    async def _check_continuous_speech(self):
        """Check if continuous speech pattern is detected with speaker changes."""
        if (
            self._meeting_mode_detected
            or len(self._speech_segments) < self._min_speech_segments
            or not self._has_speaker_changes()
        ):
            return

        recent_segments = list(self._speech_segments)[-self._min_speech_segments :]
        if len(recent_segments) < self._min_speech_segments:
            return

        # Check gap between last two segments
        last_segment = recent_segments[-1]
        second_last_segment = recent_segments[self.SECOND_LAST_SEGMENT_INDEX]
        gap = (last_segment["start"] - second_last_segment["end"]).total_seconds()

        if gap > self._conversation_gap_threshold:
            return

        unique_speakers = self._get_speaker_change_info()

        if len(unique_speakers) >= self.MIN_UNIQUE_SPEAKERS:
            logger.info(
                f"✅ Meeting mode detected: Continuous speech pattern "
                f"(gap={gap:.2f}s, {len(unique_speakers)} speakers)"
            )
            await self._trigger_meeting_mode_detection("continuous_speech")
        else:
            logger.debug(
                f"Continuous speech pattern detected but only {len(unique_speakers)} speaker(s) found, "
                f"need at least {self.MIN_UNIQUE_SPEAKERS} for meeting mode"
            )

    async def _trigger_meeting_mode_detection(self, reason: str):
        """Trigger meeting mode detection callback."""
        if self._meeting_mode_detected:
            return

        self._meeting_mode_detected = True
        logger.info(f"Meeting mode detection triggered: {reason}")

        if self._detection_callback:
            try:
                await self._detection_callback(reason)
            except Exception as e:
                logger.error(f"Error in meeting mode detection callback: {e}")

    async def _cancel_mute_check(self):
        """Cancel the mute duration check task."""
        if self._mute_check_task and not self._mute_check_task.done():
            self._mute_check_task.cancel()
            try:
                await self._mute_check_task
            except asyncio.CancelledError:
                pass
            self._mute_check_task = None

    async def _start_mute_check(self):
        """Start a task to check if mute duration exceeds threshold."""
        # Cancel any existing task
        await self._cancel_mute_check()

        # Only start mute check if meeting mode is active
        if not self._is_meeting_mode_active:
            return

        async def _check_mute_duration():
            """Wait for mute duration threshold and trigger end callback if exceeded."""
            try:
                await asyncio.sleep(self._mute_duration_threshold)
                if self._is_meeting_mode_active and self._end_callback:
                    logger.info(
                        f"Mute duration ({self._mute_duration_threshold}s) exceeded - ending meeting mode"
                    )
                    try:
                        await self._end_callback("mute_timeout")
                    except Exception as e:
                        logger.error(f"Error in meeting mode end callback: {e}")
            except asyncio.CancelledError:
                pass

        self._mute_check_task = asyncio.create_task(_check_mute_duration())

    async def _store_meeting_message(self, frame: TranscriptionFrame):
        """Store a transcription frame as a meeting message."""
        if not hasattr(frame, "text") or not frame.text:
            return

        speaker_id = self._extract_speaker_id(frame)
        message = {
            "role": "user",
            "content": frame.text,
            "timestamp": datetime.now().isoformat(),
        }
        if speaker_id:
            message["speaker_id"] = speaker_id

        self._meeting_messages.append(message)
        logger.debug(f"Stored meeting message: {len(self._meeting_messages)} total messages")

    def reset(self):
        """Reset detector state."""
        self._speaker_changes.clear()
        self._speech_segments.clear()
        self._current_speech_start = None
        self._last_speech_end = None
        self._meeting_mode_detected = False

        if self._mute_check_task and not self._mute_check_task.done():
            self._mute_check_task.cancel()
            self._mute_check_task = None
