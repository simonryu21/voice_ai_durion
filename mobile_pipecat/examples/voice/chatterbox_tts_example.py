#
# Copyright (c) 2024–2025, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#

"""Example: Using Chatterbox Multilingual TTS with Pipecat.

This example demonstrates how to integrate Chatterbox TTS into a Pipecat pipeline.
Chatterbox TTS supports 22 languages and can be run locally or accessed via API.

Prerequisites:
    1. Install Chatterbox TTS API:
       git clone https://github.com/travisvn/chatterbox-tts-api
       cd chatterbox-tts-api
       uv sync
       uv run main.py

    2. Ensure the server is running on http://localhost:8000

Usage:
    python examples/foundational/chatterbox_tts_example.py
"""

import asyncio
import os
from pathlib import Path

from dotenv import load_dotenv
from loguru import logger

from pipecat.frames.frames import EndFrame, LLMMessagesFrame, TextFrame
from pipecat.pipeline.pipeline import Pipeline
from pipecat.pipeline.runner import PipelineRunner
from pipecat.pipeline.task import PipelineParams, PipelineTask
from pipecat.processors.aggregators.llm_response import LLMUserResponseAggregator
from pipecat.services.chatterbox.tts import ChatterboxTTSService
from pipecat.services.openai import OpenAILLMService
from pipecat.transcriptions.language import Language
from pipecat.transports.local.audio import LocalAudioTransport, LocalAudioTransportParams

# Load environment variables from mobile/.env file
env_path = Path(__file__).parent.parent.parent / "mobile" / ".env"
if env_path.exists():
    load_dotenv(env_path, override=True)
    logger.info(f"Loaded .env file from: {env_path}")
else:
    # Fallback: try to load from project root .env
    root_env_path = Path(__file__).parent.parent.parent / ".env"
    if root_env_path.exists():
        load_dotenv(root_env_path, override=True)
        logger.info(f"Loaded .env file from: {root_env_path}")
    else:
        # Last fallback: try to load from current directory
        load_dotenv(override=True)
        logger.info("Loaded .env file from current directory (if exists)")


async def main():
    """Run the Chatterbox TTS example."""

    # Get API keys from environment
    openai_api_key = os.getenv("OPENAI_API_KEY")
    if not openai_api_key:
        logger.error("OPENAI_API_KEY environment variable is required")
        return

    # Create audio transport for speaker output
    transport = LocalAudioTransport(LocalAudioTransportParams(audio_out_enabled=True))

    # Create services
    llm = OpenAILLMService(
        api_key=openai_api_key,
        model="gpt-4o-mini",
    )

    # Create Chatterbox TTS service with Korean language support
    # base_url, voice, and api_key are automatically loaded from environment variables:
    # - CHATTERBOX_TTS_BASE_URL (default: `http://localhost:8000/v1`)
    # - CHATTERBOX_TTS_VOICE (default: default)
    # - CHATTERBOX_TTS_API_KEY (optional, for remote API)
    tts = ChatterboxTTSService(
        model="chatterbox-multilingual",
        language=Language.KO,  # 한국어
    )

    # Log the configuration being used
    logger.info(
        f"Chatterbox TTS configured: "
        f"base_url={os.getenv('CHATTERBOX_TTS_BASE_URL', 'http://localhost:8000/v1')}, "
        f"voice={os.getenv('CHATTERBOX_TTS_VOICE', 'default')}"
    )

    # Create aggregators
    user_aggregator = LLMUserResponseAggregator()

    # Create pipeline with audio output
    # The transport.output() processor sends audio frames to the speaker
    pipeline = Pipeline(
        [
            llm,
            user_aggregator,
            tts,
            transport.output(),  # Add audio output to play sound through speakers
        ]
    )

    # Create task
    task = PipelineTask(
        pipeline,
        params=PipelineParams(
            enable_metrics=True,
        ),
    )

    # Run the pipeline
    runner = PipelineRunner()

    # Send a test message
    messages = [
        {
            "role": "user",
            "content": "안녕하세요! Chatterbox TTS를 사용한 한국어 음성 합성 테스트입니다.",
        }
    ]

    await task.queue_frames([LLMMessagesFrame(messages), EndFrame()])

    # Run the task
    await runner.run(task)


if __name__ == "__main__":
    asyncio.run(main())
