#
# Copyright (c) 2024-2026, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#

"""User mute strategy driven by an external callable."""

from typing import Callable

from pipecat.frames.frames import Frame
from pipecat.turns.user_mute.base_user_mute_strategy import BaseUserMuteStrategy


class CallableUserMuteStrategy(BaseUserMuteStrategy):
    """User mute strategy that delegates to a callable for the current mute state.

    Use this when mute state is controlled elsewhere (e.g. by another processor
    that sets a shared dict or calls a callback). The callable is invoked on
    each frame and should return ``True`` when the user should be muted.
    """

    def __init__(self, should_mute_fn: Callable[[], bool], **kwargs):
        """Initialize the strategy.

        Args:
            should_mute_fn: Callable that returns True when the user should be muted.
            **kwargs: Passed to the base class.
        """
        super().__init__(**kwargs)
        self._should_mute_fn = should_mute_fn

    async def process_frame(self, frame: Frame) -> bool:
        """Process an incoming frame.

        Args:
            frame: The frame to be processed.

        Returns:
            Whether the strategy is muted (from the callable).
        """
        await super().process_frame(frame)
        return self._should_mute_fn()
