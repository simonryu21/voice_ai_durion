#
# Copyright (c) 2024-2026, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#

"""Mem0 Personalized Voice Agent Example with Pipecat.

This example demonstrates how to create a conversational AI assistant with memory capabilities
using Mem0 integration. It shows how to build an agent that remembers previous interactions
and personalizes responses based on conversation history.

The example:
    1. Sets up a video/audio conversation between a user and an AI assistant
    2. Uses Mem0 to store and retrieve memories from conversations
    3. Creates personalized greetings based on previous interactions
    4. Handles multi-modal interaction through audio
    5. Demonstrates two approaches for memory management:
       - Using Mem0 API (cloud-based memory storage)
       - Using local configuration with custom LLM (self-hosted memory)

Requirements:
    - OpenAI API key
    - ElevenLabs API key (for text-to-speech)
    - Daily API key (for video/audio transport)
    - Mem0 API key (for cloud-based memory storage)
    - [Optional] Anthropic API key (if using Claude with local config)

    Environment variables (set in .env or in your terminal using `export`):
        DAILY_ROOM_URL=daily_room_url
        DAILY_API_KEY=daily_api_key
        OPENAI_API_KEY=openai_api_key
        ELEVENLABS_API_KEY=elevenlabs_api_key
        MEM0_API_KEY=mem0_api_key
        ANTHROPIC_API_KEY=anthropic_api_key (if using Claude with local config)

The bot runs as part of a pipeline that processes audio frames and manages the conversation flow.
"""

import os
from datetime import datetime
from pathlib import Path
from typing import Union

from dotenv import load_dotenv
from loguru import logger

from pipecat.audio.vad.silero import SileroVADAnalyzer
from pipecat.frames.frames import LLMRunFrame
from pipecat.pipeline.pipeline import Pipeline
from pipecat.pipeline.runner import PipelineRunner
from pipecat.pipeline.task import PipelineParams, PipelineTask
from pipecat.processors.aggregators.llm_context import LLMContext
from pipecat.processors.aggregators.llm_response_universal import (
    LLMContextAggregatorPair,
    LLMUserAggregatorParams,
)
from pipecat.runner.types import RunnerArguments
from pipecat.runner.utils import create_transport
from pipecat.services.elevenlabs.tts import ElevenLabsTTSService
from pipecat.services.llm_service import FunctionCallParams
from pipecat.services.mem0.memory import Mem0MemoryService
from pipecat.services.openai.llm import OpenAILLMService
from pipecat.services.soniox.stt import SonioxInputParams, SonioxSTTService
from pipecat.transcriptions.language import Language
from pipecat.transports.base_transport import BaseTransport, TransportParams
from pipecat.transports.daily.transport import DailyParams
from pipecat.transports.websocket.fastapi import FastAPIWebsocketParams

load_dotenv(override=True)


async def get_current_datetime(params: FunctionCallParams):
    """현재 날짜와 시간 정보를 반환하는 함수"""
    now = datetime.now()
    current_date = now.strftime("%Y년 %m월 %d일")
    current_time = now.strftime("%H시 %M분")
    current_day_of_week = now.strftime("%A")  # 영어 요일
    day_of_week_kr = {
        "Monday": "월요일",
        "Tuesday": "화요일",
        "Wednesday": "수요일",
        "Thursday": "목요일",
        "Friday": "금요일",
        "Saturday": "토요일",
        "Sunday": "일요일",
    }
    current_day_kr = day_of_week_kr.get(current_day_of_week, current_day_of_week)

    result = {
        "date": current_date,
        "time": current_time,
        "day_of_week": current_day_kr,
        "iso_datetime": now.isoformat(),
    }
    await params.result_callback(result)


try:
    from mem0 import Memory, MemoryClient  # noqa: F401
except ModuleNotFoundError as e:
    logger.error(f"Exception: {e}")
    logger.error(
        "In order to use Mem0, you need to `pip install mem0ai`. Also, set the environment variable MEM0_API_KEY."
    )
    raise Exception(f"Missing module: {e}")


async def get_initial_greeting(memory_service: Mem0MemoryService) -> str:
    """Fetch all memories for the user and create a personalized greeting.

    Args:
        memory_service: The Mem0 memory service instance.

    Returns:
        A personalized greeting based on user memories.
    """
    try:
        results = await memory_service.get_memories()
        if not results:
            logger.debug("No memories found for this user.")
            return "Hello! It's nice to meet you. How can I help you today?"

        # Create a personalized greeting based on memories
        greeting = "Hello! It's great to see you again. "
        greeting += "Based on our previous conversations, I remember: "
        for i, memory in enumerate(results[:3], 1):
            memory_content = memory.get("memory", "")
            # Keep memory references brief
            if len(memory_content) > 100:
                memory_content = memory_content[:97] + "..."
            greeting += f"{memory_content} "

        greeting += "How can I help you today?"

        logger.debug(f"Created personalized greeting from {len(results)} memories")
        return greeting

    except Exception as e:
        logger.error(f"Error retrieving initial memories from Mem0: {e}")
        return "Hello! How can I help you today?"


# We use lambdas to defer transport parameter creation until the transport
# type is selected at runtime.
transport_params = {
    "daily": lambda: DailyParams(
        audio_in_enabled=True,
        audio_out_enabled=True,
    ),
    "twilio": lambda: FastAPIWebsocketParams(
        audio_in_enabled=True,
        audio_out_enabled=True,
    ),
    "webrtc": lambda: TransportParams(
        audio_in_enabled=True,
        audio_out_enabled=True,
    ),
}


async def run_bot(transport: BaseTransport, runner_args: RunnerArguments):
    """Main bot execution function.

    Sets up and runs the bot pipeline including:
    - Daily video transport
    - Speech-to-text and text-to-speech services
    - Language model integration
    - Mem0 memory service (using either API or local configuration)
    - RTVI event handling
    """
    # Note: You can pass the user_id as a parameter in API call
    USER_ID = "pipecat-demo-user"

    logger.info(f"Starting bot")

    # Soniox STT 설정 (한국어 지원)
    logger.info("Using Soniox STT service")
    soniox_api_key = os.getenv("SONIOX_API_KEY")
    if soniox_api_key:
        soniox_api_key = soniox_api_key.strip()  # 공백 제거

    if not soniox_api_key or soniox_api_key == "your_soniox_api_key":
        logger.error("SONIOX_API_KEY is not set or is invalid.")
        logger.error("Please set SONIOX_API_KEY in your .env file.")
        logger.error("Get your API key from: https://soniox.com")
        raise ValueError(
            "SONIOX_API_KEY environment variable is required when using Soniox STT. "
            "Please set it in your .env file. Get your API key from https://soniox.com"
        )

    logger.info(f"Soniox API key loaded (length: {len(soniox_api_key)})")

    stt_params = SonioxInputParams(
        language_hints=[Language.KO_KR],  # 한국어 설정
        model="stt-rt-preview",
    )

    stt = SonioxSTTService(
        api_key=soniox_api_key,
        params=stt_params,
    )

    # Initialize text-to-speech service
    tts = ElevenLabsTTSService(
        api_key=os.environ["ELEVENLABS_API_KEY"],
        settings=ElevenLabsTTSService.Settings(
            voice="pNInz6obpgDQGcFmaJgB",
        ),
    )

    # =====================================================================
    # OPTION 1: Using Mem0 API (cloud-based approach)
    # This approach uses Mem0's cloud service for memory management
    # Requires: MEM0_API_KEY set in your environment
    # =====================================================================
    memory = Mem0MemoryService(
        api_key=os.getenv("MEM0_API_KEY"),  # Your Mem0 API key
        user_id=USER_ID,  # Unique identifier for the user
        agent_id="agent1",  # Optional identifier for the agent
        run_id="session1",  # Optional identifier for the run
        params=Mem0MemoryService.InputParams(
            search_limit=10,
            search_threshold=0.3,
            api_version="v2",
            system_prompt="Based on previous conversations, I recall: \n\n",
            add_as_system_message=True,
            position=1,
        ),
    )

    # =====================================================================
    # OPTION 2: Using Mem0 with local configuration (self-hosted approach)
    # This approach uses a local LLM configuration for memory management
    # Requires: Anthropic API key if using Claude model
    # =====================================================================
    # Uncomment the following code and comment out the previous memory initialization to use local config

    # local_config = {
    #     "llm": {
    #         "provider": "anthropic",
    #         "config": {
    #             "model": "claude-3-5-sonnet-20240620",
    #             "api_key": os.getenv("ANTHROPIC_API_KEY"),  # Make sure to set this in your .env
    #         }
    #     },
    #     "embedder": {
    #         "provider": "openai",
    #         "config": {
    #             "model": "text-embedding-3-large"
    #         }
    #     }
    # }

    # # Initialize Mem0 memory service with local configuration
    # memory = Mem0MemoryService(
    #     local_config=local_config,  # Use local LLM for memory processing
    #     user_id=USER_ID,            # Unique identifier for the user
    #     # agent_id="agent1",        # Optional identifier for the agent
    #     # run_id="session1",        # Optional identifier for the run
    # )

    # Initialize LLM service
    llm = OpenAILLMService(
        api_key=os.environ["OPENAI_API_KEY"],
        settings=OpenAILLMService.Settings(
            system_instruction="""You are a personal assistant. You can remember things about the person you are talking to.
                        Some Guidelines:
                        - Make sure your responses are friendly yet short and concise.
                        - If the user asks you to remember something, make sure to remember it.
                        - Greet the user by their name if you know about it.
                    """,
        ),
    )

    # Set up conversation context and management
    # The context_aggregator will automatically collect conversation context
    context = LLMContext()
    user_aggregator, assistant_aggregator = LLMContextAggregatorPair(
        context,
        user_params=LLMUserAggregatorParams(vad_analyzer=SileroVADAnalyzer()),
    )

    pipeline = Pipeline(
        [
            transport.input(),
            stt,
            user_aggregator,
            memory,
            llm,
            tts,
            transport.output(),
            assistant_aggregator,
        ]
    )

    task = PipelineTask(
        pipeline,
        params=PipelineParams(
            enable_metrics=True,
            enable_usage_metrics=True,
        ),
        idle_timeout_secs=runner_args.pipeline_idle_timeout_secs,
    )

    @transport.event_handler("on_client_connected")
    async def on_client_connected(transport, client):
        logger.info(f"Client connected")
        # Get personalized greeting based on user memories
        greeting = await get_initial_greeting(memory)

        # Add the greeting as an assistant message to start the conversation
        context.add_message({"role": "developer", "content": greeting})

        # Queue the context frame to start the conversation
        await task.queue_frames([LLMRunFrame()])

    @transport.event_handler("on_client_disconnected")
    async def on_client_disconnected(transport, client):
        logger.info(f"Client disconnected")
        await task.cancel()

    runner = PipelineRunner(handle_sigint=runner_args.handle_sigint)
    await runner.run(task)


async def bot(runner_args: RunnerArguments):
    """Main bot entry point compatible with Pipecat Cloud."""
    transport = await create_transport(runner_args, transport_params)
    await run_bot(transport, runner_args)


if __name__ == "__main__":
    from pipecat.runner.run import main

    main()
