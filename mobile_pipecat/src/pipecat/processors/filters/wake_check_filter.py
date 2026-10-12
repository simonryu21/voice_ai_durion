#
# Copyright (c) 2024-2026, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#

"""Wake phrase detection filter for Pipecat transcription processing.

.. deprecated:: 0.0.106
    Use :class:`~pipecat.turns.user_start.WakePhraseUserTurnStartStrategy` instead.

This module provides a frame processor that filters transcription frames,
only allowing them through after wake phrases have been detected. Includes
keepalive functionality to maintain conversation flow after wake detection.
"""

import re
import time
import warnings
from enum import Enum
from typing import Optional

from loguru import logger

from pipecat.frames.frames import Frame, TranscriptionFrame
from pipecat.processors.frame_processor import FrameDirection, FrameProcessor


class WakeCheckFilter(FrameProcessor):
    """Frame processor that filters transcription frames based on wake phrase detection.

    .. deprecated:: 0.0.106
        Use :class:`~pipecat.turns.user_start.WakePhraseUserTurnStartStrategy` instead,
        which integrates with the user turn strategy system and supports configurable
        timeouts and single-activation mode.

    This filter monitors transcription frames for configured wake phrases and only
    passes frames through after a wake phrase has been detected. Maintains a
    keepalive timeout to allow continued conversation after wake detection.
    """

    class WakeState(Enum):
        """Enumeration of wake detection states.

        Parameters:
            IDLE: No wake phrase detected, filtering active.
            AWAKE: Wake phrase detected, allowing frames through.
        """

        IDLE = 1
        AWAKE = 2

    class ParticipantState:
        """State tracking for individual participants.

        Parameters:
            participant_id: Unique identifier for the participant.
            state: Current wake state (IDLE or AWAKE).
            wake_timer: Timestamp of last wake phrase detection.
            accumulator: Accumulated text for wake phrase matching.
        """

        def __init__(
            self, participant_id: str, initial_state: Optional["WakeCheckFilter.WakeState"] = None
        ):
            """Initialize participant state.

            Args:
                participant_id: Unique identifier for the participant.
                initial_state: Initial wake state for this participant. Defaults to IDLE.
            """
            self.participant_id = participant_id
            self.state = initial_state or WakeCheckFilter.WakeState.IDLE
            self.wake_timer = time.time() if self.state == WakeCheckFilter.WakeState.AWAKE else 0.0
            self.accumulator = ""

    def __init__(
        self,
        wake_phrases: list[str],
        keepalive_timeout: float = 3,
        initial_state: Optional["WakeCheckFilter.WakeState"] = None,
    ):
        """Initialize the wake phrase filter.

        .. deprecated:: 0.0.106
            Use :class:`~pipecat.turns.user_start.WakePhraseUserTurnStartStrategy` instead.

        Args:
            wake_phrases: List of wake phrases to detect in transcriptions.
            keepalive_timeout: Duration in seconds to keep passing frames after
                wake detection. Defaults to 3 seconds.
            initial_state: Initial wake state for new participants. Defaults to IDLE.
                Set to WakeCheckFilter.WakeState.AWAKE to allow frames through immediately without wake word.
        """
        super().__init__()
        warnings.warn(
            "WakeCheckFilter is deprecated since v0.0.106. "
            "Use WakePhraseUserTurnStartStrategy instead.",
            DeprecationWarning,
            stacklevel=2,
        )
        self._participant_states = {}
        self._keepalive_timeout = keepalive_timeout
        self._initial_state = initial_state or WakeCheckFilter.WakeState.IDLE
        self._wake_patterns = []
        for name in wake_phrases:
            pattern = re.compile(
                r"\b" + r"\s*".join(re.escape(word) for word in name.split()) + r"\b", re.IGNORECASE
            )
            self._wake_patterns.append(pattern)

    def reset_to_idle(self, user_id: str | None = None):
        """Reset participant(s) to IDLE state.

        Forces one or all participants back to IDLE state, clearing any
        accumulated text and resetting the wake timer. Useful when the app
        goes to background or when you want to require a wake word again.

        Args:
            user_id: Optional user ID to reset. If None, resets all participants.
        """
        if user_id:
            # Reset specific user
            if user_id in self._participant_states:
                p = self._participant_states[user_id]
                p.state = WakeCheckFilter.WakeState.IDLE
                p.accumulator = ""
                p.wake_timer = 0.0
                logger.info(f"Reset user {user_id} to IDLE state")
            else:
                logger.warning(f"User {user_id} not found in participant states")
        else:
            # Reset all users
            for uid, p in self._participant_states.items():
                p.state = WakeCheckFilter.WakeState.IDLE
                p.accumulator = ""
                p.wake_timer = 0.0
            logger.info(f"Reset all {len(self._participant_states)} users to IDLE state")

    def set_to_awake(self, user_id: str | None = None):
        """Set participant(s) to AWAKE state.

        Forces one or all participants to AWAKE state, allowing frames to pass
        through without wake word detection. Useful when the app comes to
        foreground or when you want to allow conversation without wake word.

        Args:
            user_id: Optional user ID to set. If None, sets all participants.
        """
        if user_id:
            # Set specific user
            if user_id in self._participant_states:
                p = self._participant_states[user_id]
                p.state = WakeCheckFilter.WakeState.AWAKE
                p.wake_timer = time.time()
                p.accumulator = ""  # Clear accumulator when switching to AWAKE
                logger.info(f"Set user {user_id} to AWAKE state")
            else:
                # Create new participant state with AWAKE
                p = WakeCheckFilter.ParticipantState(user_id, WakeCheckFilter.WakeState.AWAKE)
                self._participant_states[user_id] = p
                logger.info(f"Created and set new user {user_id} to AWAKE state")
        else:
            # Set all users
            for uid, p in self._participant_states.items():
                p.state = WakeCheckFilter.WakeState.AWAKE
                p.wake_timer = time.time()
                p.accumulator = ""
            logger.info(f"Set all {len(self._participant_states)} users to AWAKE state")

    async def process_frame(self, frame: Frame, direction: FrameDirection):
        """Process incoming frames, filtering transcriptions based on wake detection.

        Args:
            frame: The frame to process.
            direction: The direction of frame flow in the pipeline.
        """
        await super().process_frame(frame, direction)

        try:
            if isinstance(frame, TranscriptionFrame):
                p = self._participant_states.get(frame.user_id)
                if p is None:
                    p = WakeCheckFilter.ParticipantState(frame.user_id, self._initial_state)
                    self._participant_states[frame.user_id] = p

                # If AWAKE, always pass frames through (ignore keepalive_timeout for continuous conversation)
                if p.state == WakeCheckFilter.WakeState.AWAKE:
                    logger.debug(
                        f"User {frame.user_id} is AWAKE. Pushing frame: '{frame.text[:50]}...'"
                    )
                    p.wake_timer = time.time()  # Update timer but don't check timeout
                    await self.push_frame(frame)
                    return

                # If IDLE, check for wake words
                if p.state == WakeCheckFilter.WakeState.IDLE:
                    logger.debug(
                        f"User {frame.user_id} is in IDLE state. Checking for wake words in: '{frame.text[:50]}...'"
                    )
                    p.accumulator += frame.text
                    for pattern in self._wake_patterns:
                        match = pattern.search(p.accumulator)
                        if match:
                            logger.info(f"Wake phrase triggered: {match.group()}")
                            # Found the wake word. Discard from the accumulator up to the start of the match
                            # and modify the frame in place.
                            p.state = WakeCheckFilter.WakeState.AWAKE
                            p.wake_timer = time.time()
                            frame.text = p.accumulator[match.start() :]
                            p.accumulator = ""
                            await self.push_frame(frame)
                            return
                    # No wake word found, frame is filtered out
                    logger.debug(
                        f"User {frame.user_id} is IDLE and no wake word detected. Filtering out frame: '{frame.text[:50]}...'"
                    )
                    return
            else:
                await self.push_frame(frame, direction)
        except Exception as e:
            await self.push_error(error_msg=f"Error in wake word filter: {e}", exception=e)
