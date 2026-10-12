#
# Copyright (c) 2024–2025, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#

"""Chatterbox Multilingual TTS service implementation.

This module provides integration with Chatterbox Multilingual TTS API for
generating high-quality synthetic speech from text input in 22 languages.
Chatterbox TTS is OpenAI-compatible, making it easy to integrate.
"""

import os
from typing import AsyncGenerator, Dict, Literal, Optional

from loguru import logger
from openai import AsyncOpenAI, BadRequestError
from pydantic import BaseModel

from pipecat.frames.frames import (
    ErrorFrame,
    Frame,
    StartFrame,
    TTSAudioRawFrame,
    TTSStartedFrame,
    TTSStoppedFrame,
)
from pipecat.services.tts_service import TTSService
from pipecat.transcriptions.language import Language, resolve_language
from pipecat.utils.tracing.service_decorators import traced_tts

# Supported languages by Chatterbox TTS
# Based on: https://chatterboxtts.com/docs/multilingual
CHATTERBOX_SUPPORTED_LANGUAGES = {
    Language.AR: "ar",  # Arabic
    Language.DA: "da",  # Danish
    Language.DE: "de",  # German
    Language.EL: "el",  # Greek
    Language.EN: "en",  # English
    Language.ES: "es",  # Spanish
    Language.FI: "fi",  # Finnish
    Language.FR: "fr",  # French
    Language.HE: "he",  # Hebrew
    Language.HI: "hi",  # Hindi
    Language.IT: "it",  # Italian
    Language.JA: "ja",  # Japanese
    Language.KO: "ko",  # Korean
    Language.MS: "ms",  # Malay
    Language.NL: "nl",  # Dutch
    Language.NO: "no",  # Norwegian
    Language.PL: "pl",  # Polish
    Language.PT: "pt",  # Portuguese
    Language.RU: "ru",  # Russian
    Language.SV: "sv",  # Swedish
    Language.SW: "sw",  # Swahili
    Language.TR: "tr",  # Turkish
}

# Default sample rate for Chatterbox TTS (typically 24kHz, but can vary)
CHATTERBOX_DEFAULT_SAMPLE_RATE = 24000


def language_to_chatterbox_language(language: Language) -> Optional[str]:
    """Convert a Language enum to Chatterbox language code.

    Args:
        language: The Language enum value to convert.

    Returns:
        The corresponding Chatterbox language code, or None if not supported.
    """
    return resolve_language(language, CHATTERBOX_SUPPORTED_LANGUAGES, use_base_code=True)


class ChatterboxTTSService(TTSService):
    """Chatterbox Multilingual Text-to-Speech service.

    This service uses the Chatterbox TTS API (OpenAI-compatible) to generate
    high-quality synthetic speech from text input. Supports 22 languages with
    voice cloning capabilities.

    Chatterbox TTS is an open-source TTS model that can be run locally or
    accessed via API. It provides OpenAI-compatible endpoints, making it
    easy to integrate with existing OpenAI TTS code.

    Key Features:
    - Support for 22 languages
    - Voice cloning with custom voice samples
    - OpenAI-compatible API
    - High-quality speech synthesis
    - Streaming audio output

    Example::

        # Using local Chatterbox TTS server
        tts = ChatterboxTTSService(
            base_url="http://localhost:8000/v1",  # Local server
            voice="default",  # or custom voice ID
            model="chatterbox-multilingual",
        )

        # Using remote Chatterbox TTS API
        tts = ChatterboxTTSService(
            base_url="https://api.chatterboxtts.com/v1",  # Remote API
            api_key="your-api-key",  # If required
            voice="custom-voice-id",
            language=Language.KO,  # Korean
        )
    """

    class InputParams(BaseModel):
        """Input parameters for Chatterbox TTS configuration.

        Parameters:
            language: Language code for synthesis (e.g., "ko", "en", "ja").
                     If not specified, language is auto-detected from text.
            speed: Voice speed control (0.25 to 4.0, default 1.0).
        """

        language: Optional[str] = None
        speed: Optional[float] = None

    def __init__(
        self,
        *,
        api_key: Optional[str] = None,
        base_url: Optional[str] = None,
        voice: Optional[str] = None,
        model: str = "chatterbox-multilingual",
        sample_rate: Optional[int] = None,
        language: Optional[Language] = None,
        params: Optional[InputParams] = None,
        **kwargs,
    ):
        """Initialize Chatterbox TTS service.

        Args:
            api_key: API key for authentication. If None, uses CHATTERBOX_TTS_API_KEY environment variable.
            base_url: Base URL for Chatterbox TTS API. If None, uses CHATTERBOX_TTS_BASE_URL or defaults to "http://localhost:8000/v1".
            voice: Voice ID to use for synthesis. If None, uses CHATTERBOX_TTS_VOICE environment variable.
            model: TTS model to use. Defaults to "chatterbox-multilingual".
            sample_rate: Output audio sample rate in Hz. If None, uses default (24kHz).
            language: Language for synthesis. If None, language is auto-detected.
            params: Optional synthesis parameters (language, speed).
            **kwargs: Additional keyword arguments passed to TTSService.

        Note:
            To run Chatterbox TTS locally:
            1. Clone the repository: git clone https://github.com/travisvn/chatterbox-tts-api
            2. Install dependencies: uv sync
            3. Run the server: uv run main.py
            4. Use base_url="http://localhost:8000/v1" (or your configured port)

            Environment variables (optional):
            - CHATTERBOX_TTS_BASE_URL: Base URL for the TTS server
            - CHATTERBOX_TTS_VOICE: Voice ID to use
            - CHATTERBOX_TTS_API_KEY: API key for authentication (if needed)
        """
        if sample_rate is None:
            sample_rate = CHATTERBOX_DEFAULT_SAMPLE_RATE

        super().__init__(sample_rate=sample_rate, **kwargs)

        # Load from environment variables if not provided
        api_key = api_key or os.getenv("CHATTERBOX_TTS_API_KEY")
        base_url = base_url or os.getenv("CHATTERBOX_TTS_BASE_URL", "http://localhost:8000/v1")
        # Note: voice parameter is optional - if None or empty, server will use default voice
        # Some servers may not support OpenAI voice names like "alloy" or "default"
        # In that case, leave voice as None/empty to use server's default
        voice = voice or os.getenv("CHATTERBOX_TTS_VOICE") or None
        if voice == "default" or (voice and not voice.strip()):
            voice = None  # None means omit the parameter, let server use default

        self.set_model_name(model)
        self.set_voice(voice)

        # Initialize OpenAI client with Chatterbox base URL
        # Chatterbox TTS is OpenAI-compatible, so we can use the OpenAI client
        self._client = AsyncOpenAI(api_key=api_key, base_url=base_url)

        params = params or ChatterboxTTSService.InputParams()

        # Determine language code
        language_code = None
        if params.language:
            language_code = params.language
        elif language:
            language_code = language_to_chatterbox_language(language)

        self._settings = {
            "language": language_code,
            "speed": params.speed,
        }

        logger.info(
            f"ChatterboxTTSService initialized: base_url={base_url}, "
            f"voice={voice}, model={model}, language={language_code}"
        )

    def can_generate_metrics(self) -> bool:
        """Check if this service can generate processing metrics.

        Returns:
            True, as Chatterbox TTS service supports metrics generation.
        """
        return True

    def language_to_service_language(self, language: Language) -> Optional[str]:
        """Convert a Language enum to Chatterbox language code.

        Args:
            language: The language to convert.

        Returns:
            The Chatterbox-specific language code, or None if not supported.
        """
        return language_to_chatterbox_language(language)

    async def start(self, frame: StartFrame):
        """Start the Chatterbox TTS service.

        Args:
            frame: The start frame containing initialization parameters.
        """
        await super().start(frame)
        logger.debug(f"ChatterboxTTSService started with sample_rate={self.sample_rate}Hz")

    @traced_tts
    async def run_tts(self, text: str) -> AsyncGenerator[Frame, None]:
        """Generate speech from text using Chatterbox TTS API.

        Args:
            text: The text to synthesize into speech.

        Yields:
            Frame: Audio frames containing the synthesized speech data.
        """
        logger.debug(f"{self}: Generating TTS [{text}]")
        try:
            await self.start_ttfb_metrics()

            # Setup API parameters
            # Chatterbox TTS uses OpenAI-compatible API format
            # Note: language is auto-detected from text, not passed as a parameter
            # Note: OpenAI TTS API requires 'voice' parameter (it's a required keyword-only argument)
            # Chatterbox server will use default voice if the provided voice name is not found
            create_params = {
                "input": text,
                "model": self.model_name,
                "response_format": "pcm",
            }

            # OpenAI TTS API requires voice parameter (required keyword-only argument)
            # If voice_id is not provided, use "alloy" as default (OpenAI's default voice)
            # Server will handle it by using default voice if "alloy" is not found
            if self._voice_id and self._voice_id.strip():
                create_params["voice"] = self._voice_id.strip()
            else:
                # Use "alloy" as default - server will use its default voice if not found
                # This satisfies OpenAI client's requirement while letting server handle fallback
                create_params["voice"] = "alloy"

            # Add optional speed parameter if specified
            if self._settings["speed"]:
                create_params["speed"] = self._settings["speed"]

            # Note: language parameter is not supported by OpenAI-compatible TTS API
            # Chatterbox TTS automatically detects language from the input text

            # Make streaming request to Chatterbox TTS API
            try:
                async with self._client.audio.speech.with_streaming_response.create(
                    **create_params
                ) as r:
                    if r.status_code != 200:
                        error_text = await r.text()
                        logger.error(
                            f"{self} error getting audio (status: {r.status_code}, error: {error_text})"
                        )

                        # Provide helpful error message for 500 errors
                        if r.status_code == 500:
                            # Try to parse JSON error response
                            error_detail = error_text
                            try:
                                import json

                                error_json = json.loads(error_text)
                                if isinstance(error_json, dict):
                                    if "error" in error_json:
                                        error_obj = error_json["error"]
                                        if isinstance(error_obj, dict):
                                            error_detail = error_obj.get("message", error_text)
                                        else:
                                            error_detail = str(error_obj)
                                    elif "detail" in error_json:
                                        if (
                                            isinstance(error_json["detail"], dict)
                                            and "error" in error_json["detail"]
                                        ):
                                            error_obj = error_json["detail"]["error"]
                                            if isinstance(error_obj, dict):
                                                error_detail = error_obj.get("message", error_text)
                                            else:
                                                error_detail = str(error_obj)
                                        else:
                                            error_detail = str(error_json["detail"])
                            except (json.JSONDecodeError, KeyError, AttributeError):
                                pass  # Use original error_text

                            error_msg = (
                                f"Chatterbox TTS server error (500): {error_detail}. "
                                f"This usually indicates a server-side issue. Common causes: "
                                f"1) Model not initialized (check server logs for 'Failed to initialize model'), "
                                f"2) CUDA/CPU device mismatch, "
                                f"3) Missing model files. "
                                f"Please check the server logs for detailed error information."
                            )
                        else:
                            error_msg = f"Chatterbox TTS error (status: {r.status_code}, error: {error_text})"

                        yield ErrorFrame(error=error_msg)
                        return

                    await self.start_tts_usage_metrics(text)

                    CHUNK_SIZE = self.chunk_size

                    yield TTSStartedFrame()

                    # Stream audio chunks
                    async for chunk in r.iter_bytes(CHUNK_SIZE):
                        if len(chunk) > 0:
                            await self.stop_ttfb_metrics()
                            frame = TTSAudioRawFrame(chunk, self.sample_rate, 1)
                            yield frame

                    yield TTSStoppedFrame()
            except Exception as stream_error:
                # Handle connection errors more specifically
                error_type = type(stream_error).__name__
                error_msg = str(stream_error)

                # Log the full exception for debugging
                logger.debug(
                    f"Chatterbox TTS streaming exception details: {error_type}: {error_msg}",
                    exc_info=True,
                )

                # Check if it's a connection error
                if (
                    "Connection" in error_msg
                    or "connection" in error_msg.lower()
                    or "refused" in error_msg.lower()
                ):
                    detailed_error = (
                        f"Chatterbox TTS connection error: Cannot connect to {self._client.base_url}. "
                        f"Please ensure the Chatterbox TTS server is running. "
                        f"To start the server: git clone https://github.com/travisvn/chatterbox-tts-api && "
                        f"cd chatterbox-tts-api && uv sync && uv run main.py"
                    )
                    logger.error(detailed_error)
                    yield ErrorFrame(error=detailed_error)
                else:
                    logger.error(
                        f"Chatterbox TTS streaming error ({error_type}): {error_msg}", exc_info=True
                    )
                    yield ErrorFrame(error=f"Chatterbox TTS error ({error_type}): {error_msg}")

        except BadRequestError as e:
            # Handle OpenAI API error format
            error_msg = str(e)
            try:
                # Try to get more detailed error information
                error_body = getattr(e, "body", None)
                if error_body:
                    if isinstance(error_body, dict):
                        # Try different possible error formats
                        if "error" in error_body:
                            error_obj = error_body["error"]
                            if isinstance(error_obj, dict):
                                error_msg = error_obj.get("message", error_obj.get("code", str(e)))
                            else:
                                error_msg = str(error_obj)
                        elif "message" in error_body:
                            error_msg = error_body["message"]
                        else:
                            error_msg = str(error_body)
                    elif isinstance(error_body, str):
                        error_msg = error_body
                    else:
                        error_msg = str(error_body)
            except Exception as parse_error:
                # If parsing fails, use the original error message
                logger.debug(f"Failed to parse BadRequestError body: {parse_error}")
                error_msg = str(e)

            logger.error(f"Chatterbox TTS API error: {error_msg}", exc_info=True)
            yield ErrorFrame(error=f"Chatterbox TTS API error: {error_msg}")
        except Exception as e:
            error_type = type(e).__name__
            error_msg = str(e)

            # Provide more helpful error messages
            if (
                "Connection" in error_msg
                or "connection" in error_msg.lower()
                or "refused" in error_msg.lower()
            ):
                detailed_error = (
                    f"Chatterbox TTS connection error: Cannot connect to {self._client.base_url}. "
                    f"Please ensure the Chatterbox TTS server is running at the specified URL. "
                    f"Check: curl {self._client.base_url}/models"
                )
                logger.error(detailed_error)
                yield ErrorFrame(error=detailed_error)
            elif "KeyError" in error_type:
                # Handle KeyError more gracefully
                logger.error(
                    f"Chatterbox TTS parsing error ({error_type}): {error_msg}", exc_info=True
                )
                yield ErrorFrame(
                    error=f"Chatterbox TTS error: Failed to parse server response. "
                    f"Please check if the server is running correctly at {self._client.base_url}"
                )
            else:
                logger.error(f"Chatterbox TTS error ({error_type}): {error_msg}", exc_info=True)
                yield ErrorFrame(error=f"Chatterbox TTS error ({error_type}): {error_msg}")
