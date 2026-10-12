#
# Copyright (c) 2024–2025, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#

"""Chatterbox Multilingual TTS service implementation.

This module provides integration with Chatterbox Multilingual TTS, an open-source
text-to-speech service that supports 22 languages with OpenAI-compatible API.
"""

from pipecat.services.chatterbox.tts import ChatterboxTTSService

__all__ = ["ChatterboxTTSService"]
