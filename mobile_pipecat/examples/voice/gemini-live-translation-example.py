"""Example: Real-time speech translation using Gemini Live.

This example demonstrates how to use Gemini Live for real-time speech-to-speech
translation between different languages.
"""

import os
import sys
from pathlib import Path
from typing import Optional, Union

# Add src directory to path to import pipecat
project_root = Path(__file__).parent.parent.parent
src_path = project_root / "src"
sys.path.insert(0, str(src_path))
sys.path.insert(0, str(project_root))  # Also add project root for other imports

from loguru import logger
from pipecat.services.google.gemini_live.llm_vertex import (
    GeminiLiveVertexLLMService,
)

from pipecat.audio.vad.silero import SileroVADAnalyzer
from pipecat.audio.vad.vad_analyzer import VADParams
from pipecat.frames.frames import (
    LLMRunFrame,
)
from pipecat.pipeline.pipeline import Pipeline
from pipecat.pipeline.runner import PipelineRunner
from pipecat.pipeline.task import PipelineTask
from pipecat.processors.aggregators.llm_context import LLMContext
from pipecat.processors.aggregators.llm_response_universal import LLMContextAggregatorPair
from pipecat.services.google.gemini_live.llm import (
    GeminiLiveLLMService,
    GeminiModalities,
    InputParams,
    language_to_gemini_language,
)
from pipecat.transcriptions.language import Language
from pipecat.transports.daily.transport import DailyParams, DailyTransport
from pipecat.transports.daily.utils import DailyRESTHelper


def create_translation_system_instruction(
    source_language: Language, target_language: Language
) -> str:
    """Create a system instruction for real-time translation.

    Args:
        source_language: The input language
        target_language: The output language

    Returns:
        System instruction string for translation mode
    """
    source_name = source_language.name.replace("_", " ")
    target_name = target_language.name.replace("_", " ")

    return f"""You are a real-time speech translator. Your role is to translate spoken language in real-time.

Instructions:
1. Listen to the user's speech in {source_name}
2. Translate it accurately to {target_name}
3. Respond ONLY with the translated text in {target_name}
4. Maintain the original meaning, tone, and style
5. Keep translations natural and conversational

Rules:
- Do NOT add explanations or additional text
- Do NOT repeat the original text
- Do NOT provide multiple translations
- Only provide the translation in {target_name}
- Preserve the speaker's emotion and speaking style
- If the user speaks in {target_name}, translate to {source_name}
- If the user speaks in {source_name}, translate to {target_name}

Example:
User ({source_name}): "안녕하세요, 오늘 날씨가 좋네요"
You ({target_name}): "Hello, the weather is nice today"

User ({target_name}): "How are you doing?"
You ({source_name}): "어떻게 지내세요?"

Remember: You are ONLY a translator. Provide translations only."""


async def create_translation_service(
    api_key: str | None = None,
    source_language: Language = Language.KO_KR,
    target_language: Language = Language.EN_US,
    voice_id: str = "Charon",
    credentials_path: str | None = None,
    project_id: str | None = None,
    location: str | None = None,
) -> GeminiLiveLLMService | GeminiLiveVertexLLMService:
    """Create a Gemini Live service configured for real-time translation.

    Args:
        api_key: Google AI API key (optional if using Vertex AI)
        source_language: Input language
        target_language: Output language (for TTS)
        voice_id: TTS voice identifier
        credentials_path: Path to service account JSON file (for Vertex AI)
        project_id: Google Cloud project ID (for Vertex AI)
        location: GCP region (for Vertex AI)

    Returns:
        Configured GeminiLiveLLMService or GeminiLiveVertexLLMService instance
    """
    # Create translation system instruction
    system_instruction = create_translation_system_instruction(source_language, target_language)

    # Get language codes
    source_language_code = language_to_gemini_language(source_language) or "en-US"
    target_language_code = language_to_gemini_language(target_language) or "en-US"

    logger.info(f"Creating translation service: {source_language_code} -> {target_language_code}")

    # Use Vertex AI only if no API key is provided and credentials are available
    # Note: Vertex AI may have model availability issues, so prefer regular API if GOOGLE_API_KEY is available
    use_vertex = (credentials_path or os.getenv("GOOGLE_APPLICATION_CREDENTIALS")) and not api_key

    if use_vertex:
        if not project_id:
            project_id = os.getenv("GOOGLE_CLOUD_PROJECT_ID")
        if not location:
            # Try different regions if us-east4 doesn't work
            # Common regions: us-central1, us-east1, us-west1, asia-northeast1
            location = os.getenv("GOOGLE_CLOUD_LOCATION", "us-central1")
        if not credentials_path:
            credentials_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS")

        if not project_id:
            raise ValueError(
                "project_id is required when using Vertex AI. "
                "Set GOOGLE_CLOUD_PROJECT_ID environment variable or pass project_id parameter."
            )

        logger.info(f"Using Vertex AI with project_id={project_id}, location={location}")

        # Create Gemini Live Vertex service with translation mode
        llm = GeminiLiveVertexLLMService(
            credentials_path=credentials_path,
            project_id=project_id,
            location=location,
            system_instruction=system_instruction,
            voice_id=voice_id,
            params=InputParams(
                language=source_language,  # Input language for STT
                modalities=GeminiModalities.AUDIO,  # Audio output
            ),
        )
    else:
        if not api_key:
            raise ValueError(
                "api_key is required when not using Vertex AI. "
                "Set GOOGLE_API_KEY environment variable or pass api_key parameter. "
                "Alternatively, set GOOGLE_APPLICATION_CREDENTIALS for Vertex AI."
            )

        logger.info("Using Gemini API with API key")

        # Create Gemini Live service with translation mode
        llm = GeminiLiveLLMService(
            api_key=api_key,
            system_instruction=system_instruction,
            voice_id=voice_id,
            params=InputParams(
                language=source_language,  # Input language for STT
                modalities=GeminiModalities.AUDIO,  # Audio output
            ),
        )

    # Note: The output language is determined by the voice_id and the system instruction
    # The TTS will generate audio in the target language based on the translated text

    return llm


async def run_translation_bot(
    api_key: str | None = None,
    daily_api_key: str = "",
    daily_api_url: str = "",
    room_url: str = "",
    source_language: Language = Language.KO_KR,
    target_language: Language = Language.EN_US,
):
    """Run a real-time translation bot.

    Args:
        api_key: Google AI API key (optional if using Vertex AI with GOOGLE_APPLICATION_CREDENTIALS)
        daily_api_key: Daily.co API key
        daily_api_url: Daily.co API URL
        room_url: Daily.co room URL
        source_language: Input language (default: Korean)
        target_language: Output language (default: English)
    """
    import aiohttp

    logger.info("Starting translation bot...")

    # Generate meeting token for the room
    async with aiohttp.ClientSession() as session:
        # Ensure daily_api_url is properly formatted
        if not daily_api_url:
            daily_api_url = "https://api.daily.co/v1"
        elif not daily_api_url.endswith("/v1"):
            if daily_api_url.endswith("/"):
                daily_api_url = daily_api_url + "v1"
            else:
                daily_api_url = daily_api_url + "/v1"

        logger.debug(f"Using Daily.co API URL: {daily_api_url}")

        daily_rest_helper = DailyRESTHelper(
            daily_api_key=daily_api_key,
            daily_api_url=daily_api_url,
            aiohttp_session=session,
        )

        # Generate a meeting token for the room (valid for 1 hour)
        try:
            meeting_token = await daily_rest_helper.get_token(
                room_url=room_url,
                expiry_time=60 * 60,  # 1 hour
                owner=True,  # Bot needs owner privileges
            )
            logger.info("Generated Daily.co meeting token")
        except Exception as e:
            logger.error(f"Failed to generate Daily.co meeting token: {e}")
            logger.error(f"API URL: {daily_api_url}, Room URL: {room_url}")
            logger.error("Make sure DAILY_API_KEY is correct and you have access to the room.")
            raise

    # Create transport with the meeting token
    # Configure DailyParams to enable audio input/output and VAD
    # Note: turn_analyzer를 None으로 설정하여 interrupt를 비활성화
    # 이렇게 하면 STT 입력이 와도 TTS 출력이 중단되지 않습니다
    transport = DailyTransport(
        room_url=room_url,
        token=meeting_token,  # Use meeting token, not API key
        bot_name="Translation Bot",
        params=DailyParams(
            audio_in_enabled=True,  # Enable audio input to receive user speech
            audio_out_enabled=True,  # Enable audio output to send bot speech
            transcription_enabled=False,  # Gemini Live handles transcription internally
            vad_analyzer=SileroVADAnalyzer(
                params=VADParams(
                    stop_secs=0.5,  # Wait 0.5 seconds of silence before processing
                )
            ),
            # turn_analyzer를 None으로 설정하여 interrupt 비활성화
            # 이렇게 하면 사용자가 말해도 봇의 TTS가 중단되지 않음
            turn_analyzer=None,
        ),
    )

    # Create translation service
    # Will use Vertex AI if GOOGLE_APPLICATION_CREDENTIALS is set
    llm = await create_translation_service(
        api_key=api_key,
        source_language=source_language,
        target_language=target_language,
    )

    # Set up context
    context = LLMContext(messages=[])
    context_aggregator = LLMContextAggregatorPair(context)

    # Create pipeline
    # turn_analyzer=None으로 설정했으므로 interrupt frame이 생성되지 않음
    # 따라서 별도의 필터가 필요 없음
    pipeline = Pipeline(
        [
            transport.input(),
            context_aggregator.user(),
            llm,
            transport.output(),
            context_aggregator.assistant(),
        ]
    )

    # Create task
    task = PipelineTask(
        pipeline,
        params=None,
    )

    @transport.event_handler("on_first_participant_joined")
    async def on_first_participant_joined(transport, participant):
        """Handle when first participant joins the room."""
        participant_id = participant.get("id", "")
        logger.info(f"First participant joined: {participant_id}")

        # Capture participant audio to receive their speech
        await transport.capture_participant_audio(participant_id)
        logger.info(f"Started capturing audio from participant: {participant_id}")

        # Start translation
        await task.queue_frames([LLMRunFrame()])

    @transport.event_handler("on_client_connected")
    async def on_client_connected(transport, client):
        logger.info("Client connected - waiting for participant to join")

    @transport.event_handler("on_client_disconnected")
    async def on_client_disconnected(transport, client):
        logger.info("Client disconnected")
        await task.cancel()

    # Run the bot
    runner = PipelineRunner()
    await runner.run(task)


if __name__ == "__main__":
    import asyncio

    from dotenv import load_dotenv

    # Load environment variables from mobile/.env
    env_path = Path(__file__).parent.parent.parent / "mobile" / ".env"
    load_dotenv(env_path)
    logger.info(f"Loaded environment variables from: {env_path}")

    # Configuration
    # Google API authentication - use either GOOGLE_API_KEY or GOOGLE_APPLICATION_CREDENTIALS
    GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")
    GOOGLE_APPLICATION_CREDENTIALS = os.getenv("GOOGLE_APPLICATION_CREDENTIALS")

    # Daily.co configuration
    # ROOM_URL: Daily.co room URL where the bot will join (e.g., "https://example.daily.co/my-room")
    # You can create a room at https://dashboard.daily.co and copy the room URL
    DAILY_API_KEY = os.getenv("DAILY_API_KEY")
    # Daily.co API requires /v1 path
    daily_api_url_env = os.getenv("DAILY_API_URL", "https://api.daily.co/v1")
    if not daily_api_url_env.endswith("/v1"):
        if daily_api_url_env.endswith("/"):
            DAILY_API_URL = daily_api_url_env + "v1"
        else:
            DAILY_API_URL = daily_api_url_env + "/v1"
    else:
        DAILY_API_URL = daily_api_url_env
    ROOM_URL = os.getenv("ROOM_URL")

    # Check authentication method
    # Prefer GOOGLE_API_KEY over Vertex AI for better compatibility
    if GOOGLE_API_KEY:
        logger.info("Using Gemini API with API key (preferred for compatibility)")
        # Clear Vertex AI credentials to force API key usage
        if GOOGLE_APPLICATION_CREDENTIALS:
            logger.info("GOOGLE_API_KEY found, using regular Gemini API instead of Vertex AI")
            GOOGLE_APPLICATION_CREDENTIALS = None
    elif GOOGLE_APPLICATION_CREDENTIALS:
        logger.info(f"Using Vertex AI with credentials from: {GOOGLE_APPLICATION_CREDENTIALS}")

        # Try to get project_id from environment or credentials file
        project_id = os.getenv("GOOGLE_CLOUD_PROJECT_ID")
        if not project_id and GOOGLE_APPLICATION_CREDENTIALS:
            try:
                import json

                creds_path = Path(GOOGLE_APPLICATION_CREDENTIALS)
                if not creds_path.is_absolute():
                    # If relative path, try relative to project root
                    creds_path = project_root / creds_path

                if creds_path.exists():
                    with open(creds_path) as f:
                        creds_data = json.load(f)
                        project_id = creds_data.get("project_id")
                        if project_id:
                            logger.info(f"Found project_id '{project_id}' from credentials file")
            except Exception as e:
                logger.debug(f"Could not read project_id from credentials: {e}")

        if not project_id:
            logger.error(
                "GOOGLE_CLOUD_PROJECT_ID environment variable is required when using Vertex AI. "
                "Set it in mobile/.env or it will be read from the credentials JSON file."
            )
            sys.exit(1)

        # Store for later use
        os.environ["GOOGLE_CLOUD_PROJECT_ID"] = project_id
    else:
        logger.error(
            "Either GOOGLE_API_KEY or GOOGLE_APPLICATION_CREDENTIALS environment variable is required"
        )
        sys.exit(1)

    if not DAILY_API_KEY:
        logger.error("DAILY_API_KEY environment variable is required")
        sys.exit(1)

    if not ROOM_URL:
        logger.error(
            "ROOM_URL environment variable is required. "
            "This should be a Daily.co room URL (e.g., 'https://example.daily.co/my-room'). "
            "Create a room at https://dashboard.daily.co and copy the room URL."
        )
        sys.exit(1)

    # Run translation bot
    # Korean -> English translation
    asyncio.run(
        run_translation_bot(
            api_key=GOOGLE_API_KEY,
            daily_api_key=DAILY_API_KEY,
            daily_api_url=DAILY_API_URL,
            room_url=ROOM_URL,
            source_language=Language.KO_KR,
            target_language=Language.EN_US,
        )
    )
