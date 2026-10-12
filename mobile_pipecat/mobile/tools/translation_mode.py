"""Translation mode tool for real-time translation using OpenAI Realtime or Gemini Live."""

import asyncio
import json
import os
import sys
from pathlib import Path
from typing import Any, Dict, Optional

from loguru import logger

# Env keys for translation mode
ENV_TRANSLATION_LLM_PROVIDER = "TRANSLATION_LLM_PROVIDER"
ENV_TRANSLATION_TTS_PROVIDER = "TRANSLATION_TTS_PROVIDER"
ENV_OPENAI_API_KEY = "OPENAI_API_KEY"
ENV_GOOGLE_API_KEY = "GOOGLE_API_KEY"
ENV_GOOGLE_APPLICATION_CREDENTIALS = "GOOGLE_APPLICATION_CREDENTIALS"
ENV_GOOGLE_CLOUD_PROJECT_ID = "GOOGLE_CLOUD_PROJECT_ID"
ENV_GOOGLE_CLOUD_LOCATION = "GOOGLE_CLOUD_LOCATION"
DEFAULT_LLM_PROVIDER = "openai"
DEFAULT_SOURCE_LANG = "ko-KR"
DEFAULT_TARGET_LANG = "en-US"
from pipecat.adapters.schemas.function_schema import FunctionSchema
from pipecat.frames.frames import LLMRunFrame, OutputAudioRawFrame, STTMuteFrame, TTSAudioRawFrame
from pipecat.pipeline.pipeline import Pipeline
from pipecat.pipeline.runner import PipelineRunner
from pipecat.pipeline.task import PipelineParams, PipelineTask
from pipecat.processors.aggregators.llm_context import LLMContext
from pipecat.processors.aggregators.llm_response_universal import LLMContextAggregatorPair
from pipecat.processors.frame_processor import FrameDirection, FrameProcessor
from pipecat.processors.frameworks.rtvi import RTVIObserver, RTVIProcessor
from pipecat.runner.run import register_session_context
from pipecat.services.llm_service import FunctionCallParams
from pipecat.services.openai.realtime import events as openai_events
from pipecat.services.openai.realtime.llm import OpenAIRealtimeLLMService
from pipecat.transcriptions.language import Language

# Allow importing from mobile package when this module is used from pipecat runner
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

start_translation_mode_function = FunctionSchema(
    name="start_translation_mode",
    description="실시간 번역 모드를 시작합니다.",
    properties={
        "source_language": {"type": "string", "description": "입력 언어 (기본값: ko-KR)"},
        "target_language": {"type": "string", "description": "출력 언어 (기본값: en-US)"},
    },
    required=[],
)

end_translation_mode_function = FunctionSchema(
    name="end_translation_mode",
    description="실시간 번역 모드를 종료합니다.",
    properties={},
    required=[],
)

# 세션별 상태 저장
_translation_mode_states: Dict[str, bool] = {}
_translation_mode_services: Dict[str, Any] = {}
_translation_mode_configs: Dict[str, Dict[str, str]] = {}
_translation_mode_tasks: Dict[str, Any] = {}
_main_tasks: Dict[str, Any] = {}
_main_first_processors: Dict[str, Any] = {}
_main_runner_args: Dict[str, Any] = {}


def is_translation_mode_active(session_id: Optional[str]) -> bool:
    return session_id and _translation_mode_states.get(session_id, False)


def get_translation_service(session_id: Optional[str]):
    return _translation_mode_services.get(session_id) if session_id else None


def get_translation_config(session_id: Optional[str]) -> Optional[Dict[str, str]]:
    return _translation_mode_configs.get(session_id) if session_id else None


def set_translation_mode(session_id: Optional[str], active: bool):
    if session_id:
        _translation_mode_states[session_id] = active
        if not active:
            _translation_mode_services.pop(session_id, None)
            _translation_mode_configs.pop(session_id, None)


class TranslationAudioForwarder(FrameProcessor):
    """번역 서비스의 오디오를 메인 transport로 전달하는 프로세서."""

    def __init__(self, main_transport_output, **kwargs):
        super().__init__(**kwargs)
        self._main_transport_output = main_transport_output
        self._audio_buffer = bytearray()
        self._sample_rate = None
        self._num_channels = None
        self._bytes_per_10ms = None

    def _update_sample_rate(self, sample_rate: int, num_channels: int):
        """Update sample rate and calculate bytes per 10ms."""
        if self._sample_rate != sample_rate or self._num_channels != num_channels:
            self._sample_rate = sample_rate
            self._num_channels = num_channels
            # Calculate bytes per 10ms: (sample_rate * 10 / 1000) samples * 2 bytes per sample (16-bit)
            samples_per_10ms = sample_rate * 10 // 1000
            self._bytes_per_10ms = samples_per_10ms * 2 * num_channels
            logger.debug(
                f"TranslationAudioForwarder: sample_rate={sample_rate}, num_channels={num_channels}, bytes_per_10ms={self._bytes_per_10ms}"
            )

    async def process_frame(self, frame, direction: FrameDirection):
        await super().process_frame(frame, direction)
        if isinstance(frame, TTSAudioRawFrame) and self._main_transport_output:
            try:
                # Update sample rate if needed
                self._update_sample_rate(frame.sample_rate, frame.num_channels)

                # Add audio to buffer
                self._audio_buffer.extend(frame.audio)

                # Send chunks that are multiples of 10ms
                if self._bytes_per_10ms and len(self._audio_buffer) >= self._bytes_per_10ms:
                    # Calculate how many complete 10ms chunks we can send
                    num_chunks = len(self._audio_buffer) // self._bytes_per_10ms
                    bytes_to_send = num_chunks * self._bytes_per_10ms

                    # Send complete chunks
                    audio_chunk = bytes(self._audio_buffer[:bytes_to_send])
                    self._audio_buffer = self._audio_buffer[bytes_to_send:]

                    await self._main_transport_output.write_audio_frame(
                        OutputAudioRawFrame(
                            audio=audio_chunk,
                            sample_rate=self._sample_rate,
                            num_channels=self._num_channels,
                        )
                    )
            except Exception as e:
                logger.warning(f"Failed to forward audio: {e}")
        await self.push_frame(frame, direction)


def _create_translation_system_instruction(source: str, target: str) -> str:
    """번역용 시스템 지시문 생성"""
    lang_map = {"ko-KR": "한국어", "en-US": "영어", "ja-JP": "일본어", "zh-CN": "중국어"}
    source_name = lang_map.get(source, source)
    target_name = lang_map.get(target, target)
    return f"""You are a real-time translator. Translate speech from {source_name} to {target_name}.
Rules:
1. Translate accurately and naturally
2. Maintain original meaning and tone
3. No commentary or explanation
4. Only output the translation
5. If user speaks in {target_name}, translate back to {source_name}"""


def _create_translation_service(
    source_language: str,
    target_language: str,
    use_external_tts: bool = False,
    llm_provider: Optional[str] = None,
):
    """번역 서비스 생성

    Args:
        source_language: 입력 언어
        target_language: 출력 언어
        use_external_tts: 외부 TTS 사용 여부 (True면 LLM은 텍스트만 출력)
        llm_provider: 사용할 LLM provider. None이면 ``TRANSLATION_LLM_PROVIDER`` env를 사용.
    """
    if llm_provider is None:
        llm_provider = os.getenv(ENV_TRANSLATION_LLM_PROVIDER, DEFAULT_LLM_PROVIDER)
    llm_provider = llm_provider.lower()
    system_instruction = _create_translation_system_instruction(source_language, target_language)

    if llm_provider == "openai":
        api_key = os.getenv(ENV_OPENAI_API_KEY)
        if not api_key:
            raise ValueError(f"{ENV_OPENAI_API_KEY} required")

        # 외부 TTS 사용 시 텍스트만 출력하도록 설정
        output_modalities = ["text"] if use_external_tts else ["audio", "text"]

        return OpenAIRealtimeLLMService(
            api_key=api_key,
            model="gpt-4o-realtime-preview-2024-12-17",
            session_properties=openai_events.SessionProperties(
                instructions=system_instruction,
                output_modalities=output_modalities,
                voice="alloy" if not use_external_tts else None,
                input_audio_transcription=openai_events.InputAudioTranscription(
                    model="gpt-4o-transcribe",
                    language=source_language,
                ),
            ),
            start_audio_paused=False,
        )

    elif llm_provider == "gemini_google":
        from pipecat.services.google.gemini_live.llm import (
            GeminiLiveLLMService,
            GeminiModalities,
            InputParams,
        )
        from pipecat.services.google.gemini_live.vertex.llm import GeminiLiveVertexLLMService

        api_key = os.getenv(ENV_GOOGLE_API_KEY)
        credentials_path = os.getenv(ENV_GOOGLE_APPLICATION_CREDENTIALS)

        # 외부 TTS 사용 시 텍스트만 출력하도록 설정
        modalities = GeminiModalities.TEXT if use_external_tts else GeminiModalities.AUDIO

        if credentials_path and not api_key:
            # Vertex AI
            creds_path = Path(credentials_path)
            if not creds_path.is_absolute():
                creds_path = Path(__file__).parent.parent.parent / creds_path
            if not creds_path.exists():
                raise FileNotFoundError(f"Credentials file not found: {creds_path}")

            with open(creds_path) as f:
                project_id = os.getenv(ENV_GOOGLE_CLOUD_PROJECT_ID) or json.load(f).get(
                    "project_id"
                )
            if not project_id:
                raise ValueError(f"{ENV_GOOGLE_CLOUD_PROJECT_ID} required")

            # TEXT 모드일 때는 텍스트를 지원하는 모델 사용
            model = "models/gemini-2.0-flash-exp" if modalities == GeminiModalities.TEXT else None
            return GeminiLiveVertexLLMService(
                credentials_path=str(creds_path.resolve()),
                project_id=project_id,
                location=os.getenv(ENV_GOOGLE_CLOUD_LOCATION, "us-central1"),
                model=model,  # TEXT 모드일 때 텍스트 지원 모델 사용
                system_instruction=system_instruction,
                voice_id="Charon",  # TEXT 모드에서도 voice_id는 필요하지만 사용되지 않음
                params=InputParams(language=source_language, modalities=modalities),
            )
        else:
            if not api_key:
                raise ValueError(f"{ENV_GOOGLE_API_KEY} required")
            # TEXT 모드일 때는 텍스트를 지원하는 모델 사용
            model = "models/gemini-2.0-flash-exp" if modalities == GeminiModalities.TEXT else None
            return GeminiLiveLLMService(
                api_key=api_key,
                model=model,  # TEXT 모드일 때 텍스트 지원 모델 사용
                system_instruction=system_instruction,
                voice_id="Charon",  # TEXT 모드에서도 voice_id는 필요하지만 사용되지 않음
                params=InputParams(language=source_language, modalities=modalities),
            )

    elif llm_provider == "gemini_google_native":
        # Native 앱에서 처리
        logger.info("Using Gemini Live Native (client-side)")
        return None

    raise ValueError(f"Invalid LLM provider: {llm_provider}")


def _create_translation_tts_service(target_language: str):
    """번역용 TTS 서비스 생성

    Args:
        target_language: 출력 언어 (예: "en-US", "ko-KR")

    Returns:
        TTS 서비스 인스턴스 또는 None (LLM 내장 TTS 사용 시)
    """
    tts_provider = os.getenv(ENV_TRANSLATION_TTS_PROVIDER, "").lower()

    # TTS 제공자가 설정되지 않으면 None 반환 (LLM 내장 TTS 사용)
    if not tts_provider:
        return None

    if tts_provider == "elevenlabs":
        from pipecat.services.elevenlabs.tts import ElevenLabsTTSService

        api_key = os.getenv("ELEVENLABS_API_KEY")
        if not api_key:
            raise ValueError("ELEVENLABS_API_KEY required")

        voice_id = os.getenv(
            "TRANSLATION_ELEVENLABS_VOICE_ID", os.getenv("ELEVENLABS_VOICE_ID", "")
        )
        if not voice_id:
            raise ValueError("TRANSLATION_ELEVENLABS_VOICE_ID or ELEVENLABS_VOICE_ID required")

        # 언어 코드 매핑
        lang_map = {
            "ko-KR": Language.KO,
            "en-US": Language.EN_US,
            "ja-JP": Language.JA,
            "zh-CN": Language.ZH_CN,
        }
        language = lang_map.get(target_language, Language.EN_US)

        tts_params = ElevenLabsTTSService.InputParams(
            language=language,
            stability=float(os.getenv("TRANSLATION_ELEVENLABS_STABILITY", "0.5")),
            similarity_boost=float(os.getenv("TRANSLATION_ELEVENLABS_SIMILARITY_BOOST", "0.75")),
            speed=float(os.getenv("TRANSLATION_ELEVENLABS_SPEED", "1.0")),
        )

        model = os.getenv("TRANSLATION_ELEVENLABS_MODEL", "eleven_turbo_v2_5")

        return ElevenLabsTTSService(
            api_key=api_key,
            voice_id=voice_id,
            model=model,
            params=tts_params,
        )

    raise ValueError(f"Unsupported TTS provider: {tts_provider}")


def create_start_translation_mode(
    context_aggregator: LLMContextAggregatorPair,
    pipeline: Any,
    session_id: Optional[str] = None,
    rtvi=None,
    stt=None,
    transport=None,
    task=None,
    runner_args=None,
):
    async def start_translation_mode(params: FunctionCallParams):
        if not session_id:
            return await params.result_callback({"status": "error", "message": "세션 ID 없음"})

        if is_translation_mode_active(session_id):
            return await params.result_callback(
                {"status": "already_active", "message": "이미 활성화됨"}
            )

        args = params.arguments or {}
        source_language = args.get("source_language", DEFAULT_SOURCE_LANG)
        target_language = args.get("target_language", DEFAULT_TARGET_LANG)
        # 요청에 provider가 실려 오면 env 기본값보다 우선한다. 이렇게 해야
        # 웹 "번역 Agent"(gemini_google)와 네이티브 "동시통역 Agent"
        # (gemini_google_native)를 별도 버튼으로 동시에 운용할 수 있다.
        llm_provider = (
            args.get("llm_provider")
            or args.get("provider")
            or os.getenv(ENV_TRANSLATION_LLM_PROVIDER, DEFAULT_LLM_PROVIDER)
        ).lower()

        try:
            tts_provider = os.getenv(ENV_TRANSLATION_TTS_PROVIDER, "").lower()
            use_external_tts = bool(tts_provider)

            # Gemini Live API는 더 이상 TEXT 응답 모달리티를 지원하지 않는다
            # (이 키로 쓸 수 있는 Live 모델이 전부 오디오 출력 전용). 따라서 웹
            # "번역 Agent"(gemini_google)는 외부 TTS로 보낼 텍스트를 얻을 수
            # 없고, Gemini Live가 번역을 오디오로 직접 출력한다. native(앱 처리)
            # 와 openai(Realtime, 별도 경로)는 영향받지 않는다.
            if llm_provider == "gemini_google" and use_external_tts:
                logger.info(
                    f"gemini_google: Gemini Live가 TEXT 모달리티를 지원하지 않아 "
                    f"외부 TTS({tts_provider})를 건너뛰고 Gemini 오디오 출력을 사용합니다."
                )
                tts_provider = ""
                use_external_tts = False

            # 번역 서비스 생성 (외부 TTS 사용 시 텍스트만 출력하도록 설정)
            translation_service = _create_translation_service(
                source_language,
                target_language,
                use_external_tts=use_external_tts,
                llm_provider=llm_provider,
            )
            is_native_mode = llm_provider == "gemini_google_native"

            # TTS 서비스 생성 (외부 TTS 사용 시)
            translation_tts = None
            if use_external_tts:
                translation_tts = _create_translation_tts_service(target_language)
                logger.info(f"Using external TTS provider: {tts_provider}")

            # 상태 저장
            if translation_service:
                _translation_mode_services[session_id] = translation_service
            _translation_mode_configs[session_id] = {
                "source_language": source_language,
                "target_language": target_language,
                "tts_provider": tts_provider if use_external_tts else "builtin",
            }
            set_translation_mode(session_id, True)

            # Native 모드가 아닌 경우에만 번역 파이프라인 생성
            if not is_native_mode:
                if not translation_service:
                    raise ValueError("Translation service creation failed")

                # 번역 파이프라인 생성
                translation_context = LLMContext(messages=[])
                translation_context_aggregator = LLMContextAggregatorPair(translation_context)
                main_transport_output = (
                    transport.output() if transport and hasattr(transport, "output") else None
                )

                # 파이프라인 구성: transport -> rtvi -> context -> LLM -> TTS -> AudioForwarder
                pipeline_components = [
                    transport.input(),
                    rtvi,
                    translation_context_aggregator.user(),
                    translation_service,
                ]

                # 외부 TTS 사용 시 TTS 서비스를 파이프라인에 추가
                if translation_tts:
                    pipeline_components.append(translation_tts)

                pipeline_components.extend(
                    [
                        TranslationAudioForwarder(main_transport_output),
                        translation_context_aggregator.assistant(),
                    ]
                )

                translation_pipeline = Pipeline(pipeline_components)

                # 번역 task 생성 및 실행
                translation_task = PipelineTask(
                    translation_pipeline,
                    params=PipelineParams(enable_metrics=True, enable_usage_metrics=True),
                    observers=[RTVIObserver(rtvi=rtvi)] if rtvi else [],
                    idle_timeout_secs=None,
                )
                _translation_mode_tasks[session_id] = translation_task

                pipeline_started = asyncio.Event()

                @translation_task.event_handler("on_pipeline_started")
                async def on_pipeline_started(task, frame):
                    # 번역 모드에서는 초기 인사말을 생성하지 않음
                    # 사용자가 말을 시작하면 자동으로 응답을 생성함
                    pipeline_started.set()

                async def run_translation_task():
                    register_session_context(session_id, translation_task)
                    await PipelineRunner().run(translation_task)

                asyncio.create_task(run_translation_task())

                # 파이프라인 시작 대기 (타임아웃만 확인, LLMRunFrame은 보내지 않음)
                try:
                    await asyncio.wait_for(pipeline_started.wait(), timeout=5.0)
                except asyncio.TimeoutError:
                    logger.warning("Translation pipeline startup timeout, continuing anyway")

                await asyncio.sleep(0.5)
            else:
                # Native 모드: 파이프라인 생성하지 않음, 앱에서 번역 처리
                logger.info("Translation mode started (native)")

            # 메인 파이프라인 정보 저장
            if task:
                _main_tasks[session_id] = task
                if rtvi:
                    _main_first_processors[session_id] = rtvi
                if runner_args:
                    _main_runner_args[session_id] = runner_args

            # 클라이언트 알림
            if rtvi:
                try:
                    await rtvi.send_server_message(
                        {
                            "type": "translation_mode_started",
                            "message": f"번역 모드 시작: {source_language} -> {target_language}",
                            "llm_provider": llm_provider,
                            "tts_provider": tts_provider if use_external_tts else "builtin",
                        }
                    )
                except Exception as e:
                    logger.error(f"Failed to send notification: {e}")

            await params.result_callback(
                {
                    "status": "success",
                    "message": f"번역 모드 시작: {source_language} -> {target_language}",
                    "llm_provider": llm_provider,
                    "tts_provider": tts_provider if use_external_tts else "builtin",
                }
            )

        except Exception as e:
            logger.error(f"Failed to start translation mode: {e}")
            await params.result_callback({"status": "error", "message": f"시작 실패: {str(e)}"})

    return start_translation_mode


async def end_translation_mode(session_id: Optional[str], stt=None, transport=None, rtvi=None):
    """번역 모드 직접 종료"""
    if not session_id:
        return {"status": "error", "message": "세션 ID 없음"}

    try:
        # 번역 task 종료 (native 모드가 아닌 경우)
        # pop을 사용하여 딕셔너리에서 가져오면서 동시에 제거 (반환값: translation_task 또는 None)
        translation_task = _translation_mode_tasks.pop(session_id, None)
        if translation_task:
            await translation_task.stop_when_done()
            logger.info("Translation mode task stop requested")

        # 번역 모드 상태 해제
        set_translation_mode(session_id, False)

        # 클라이언트 알림
        if rtvi:
            try:
                await rtvi.send_server_message(
                    {
                        "type": "translation_mode_ended",
                        "message": "번역 모드가 종료되었습니다.",
                    }
                )
            except Exception as e:
                logger.error(f"Failed to send translation mode end notification: {e}")

        return {"status": "success", "message": "번역 모드 종료"}
    except Exception as e:
        logger.error(f"Failed to end translation mode: {e}")
        return {"status": "error", "message": f"종료 실패: {str(e)}"}


def create_end_translation_mode(
    context_aggregator: LLMContextAggregatorPair,
    pipeline: Any,
    session_id: Optional[str] = None,
    rtvi=None,
    stt=None,
    transport=None,
    runner_args=None,
):
    async def end_translation_mode_func(params: FunctionCallParams):
        result = await end_translation_mode(session_id, stt=stt, transport=transport, rtvi=rtvi)
        await params.result_callback(result)

    return end_translation_mode_func
