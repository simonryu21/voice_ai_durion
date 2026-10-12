"""Voice AI with Pipecat.

Required AI services:
- Soniox (Speech-to-Text)
- OpenAI (LLM)
- Cartesia or OpenAI or ElevenLabs (Text-to-Speech)

Run the bot using::

    uv run voice_ai.py

"""

# Standard library imports
import asyncio
import os
from pathlib import Path
from typing import Any, Dict, List, Optional, Union

# Load .env early so MEM0_TELEMETRY etc. are set before mem0/posthog are imported
from dotenv import load_dotenv

_env_path = Path(__file__).resolve().parent / ".env"
load_dotenv(_env_path, override=True)

# Disable PostHog (used by mem0) when telemetry off or behind corporate proxy (SSL errors)
if os.getenv("MEM0_TELEMETRY", "true").lower() in ("false", "0", "no") or os.getenv(
    "DISABLE_POSTHOG", ""
).lower() in ("true", "1", "yes"):
    try:
        import posthog

        posthog.disabled = True
    except Exception:
        pass

# Third-party imports
from loguru import logger
from mem0 import Memory, MemoryClient

# Pipecat imports
from pipecat.adapters.schemas.function_schema import FunctionSchema
from pipecat.adapters.schemas.tools_schema import ToolsSchema
from pipecat.audio.vad.silero import SileroVADAnalyzer
from pipecat.audio.vad.vad_analyzer import VADParams
from pipecat.frames.frames import (
    CancelFrame,
    EndFrame,
    InterruptionFrame,
    LLMContextFrame,
    LLMFullResponseEndFrame,
    LLMFullResponseStartFrame,
    LLMMessagesAppendFrame,
    LLMRunFrame,
    LLMTextFrame,
    TTSSpeakFrame,
)
from pipecat.pipeline.pipeline import Pipeline, PipelineSource
from pipecat.pipeline.runner import PipelineRunner
from pipecat.pipeline.task import PipelineParams, PipelineTask
from pipecat.processors.aggregators.llm_context import LLMContext
from pipecat.processors.aggregators.llm_response_universal import (
    LLMContextAggregatorPair,
    LLMUserAggregatorParams,
)
from pipecat.processors.aggregators.llm_text_processor import LLMTextProcessor
from pipecat.processors.filters.wake_check_filter import WakeCheckFilter
from pipecat.processors.frame_processor import FrameDirection
from pipecat.processors.frameworks.rtvi import RTVIObserver, RTVIProcessor
from pipecat.runner.run import (
    create_app,
    main,
    register_session_context,
    unregister_session_context,
)
from pipecat.runner.types import (
    DailyRunnerArguments,
    LiveKitRunnerArguments,
    RunnerArguments,
    SmallWebRTCRunnerArguments,
    WebSocketRunnerArguments,
)
from pipecat.runner.utils import create_transport
from pipecat.services.llm_service import LLMService
from pipecat.services.mem0.memory import Mem0MemoryService
from pipecat.services.openai.llm import OpenAILLMService
from pipecat.services.openai.tts import OpenAITTSService
from pipecat.services.soniox.stt import SonioxSTTService
from pipecat.services.tts_service import TextAggregationMode
from pipecat.transcriptions.language import Language
from pipecat.transports.base_transport import BaseTransport, TransportParams
from pipecat.transports.daily.transport import DailyParams
from pipecat.utils.tracing.langfuse import setup_langfuse_tracing_from_env

from app_catalog import (
    CandidateBuilder,
    CandidateSet,
    CatalogLoader,
    render_candidates_section,
)
from app_concierge_tools import create_per_app_pipecat_tools
from app_handoff import (
    AppHandoffStore,
    build_back_confirmed_event_text,
    build_failed_event_text,
    build_timeout_event_text,
    make_app_invoke_dispatcher,
)
from app_inventory.db import app_inventory_db
from auth import db as auth_db
from auth.app_setup import setup_auth_for_app
from first_sentence_tts_filter import FirstSentenceTTSFilter

# Local imports
from integrations.gportal_mcp.client import GportalMCPClient
from integrations.gportal_mcp.pipecat_tools import (
    create_gportal_pipecat_tools,
    format_gportal_tool_descriptions,
)
from integrations.gportal_mcp.session import SessionThreadStore
from integrations.gportal_mcp.types import GportalMCPConfig
from max_length_text_aggregator import MaxLengthTextAggregator
from starting_prompt import get_starting_messages
from system_prompt_builder import build_session_system_prompt, build_static_system_prompt
from tools.handoff_screenshot import analyze_handoff_screenshot_for_session
from tools.meeting_mode import (
    create_end_meeting_mode,
    create_start_meeting_mode,
    end_meeting_mode_function,
    get_meeting_mode_start_message_count,
    is_meeting_mode_active,
    set_meeting_mode,
    set_meeting_mode_start_message_count,
)
from tools.pipecat_adapters import create_pipecat_local_tools
from tools.translation_mode import (
    _main_first_processors,
    _main_tasks,
    create_start_translation_mode,
    end_translation_mode,
    is_translation_mode_active,
)

# OAuth imports
from utils.token_controller import get_token_controller

load_dotenv(override=True)

# Langfuse 트레이싱 (LANGFUSE_ENABLED=true 및 키 설정 시 Langfuse로 span 전송)
_, _langfuse_export = setup_langfuse_tracing_from_env()
if _langfuse_export:
    _otlp_url = os.getenv("LANGFUSE_OTEL_ENDPOINT") or (
        f"{os.getenv('LANGFUSE_BASE_URL', '').rstrip('/')}/api/public/otel/v1/traces"
        if os.getenv("LANGFUSE_BASE_URL")
        else ""
    )
    logger.info("Langfuse tracing: enabled (OTLP → {})", _otlp_url or "Langfuse")
else:
    logger.info("Langfuse tracing: disabled or misconfigured (check LANGFUSE_* in .env)")

print("🚀 Starting Pipecat bot...")
print("⏳ Loading models and imports (20 seconds, first run only)\n")


# ── AppConcierge: load catalog once at module init ──
# Phase 1 only injects per-user candidates into the supervisor system prompt.
# The actual app_invoke routing/execution is added in later phases.
try:
    _APPCONCIERGE_CATALOG = CatalogLoader().load()
    _APPCONCIERGE_BUILDER: Optional[CandidateBuilder] = (
        CandidateBuilder(_APPCONCIERGE_CATALOG) if _APPCONCIERGE_CATALOG else None
    )
    if _APPCONCIERGE_CATALOG:
        logger.info(
            "AppConcierge catalog loaded ({} categories: {})",
            len(_APPCONCIERGE_CATALOG),
            ", ".join(sorted(_APPCONCIERGE_CATALOG.keys())),
        )
    else:
        logger.info("AppConcierge catalog is empty — section will not be injected")
except Exception as e:
    logger.exception(f"Failed to load AppConcierge catalog; feature disabled: {e}")
    _APPCONCIERGE_CATALOG = {}
    _APPCONCIERGE_BUILDER = None


async def _build_appconcierge_for_user(
    user_id: Optional[str],
) -> tuple[Optional[CandidateSet], Optional[str]]:
    """Build per-user AppConcierge candidates and the supervisor prompt section.

    The candidate set is fed to :func:`create_per_app_pipecat_tools` so the
    supervisor LLM gets one ``open_<app_id>`` Pipecat function per
    candidate app. The section is appended to the supervisor system prompt
    so the LLM knows which apps are eligible.

    Returns:
        Tuple ``(candidates, section)``. Either may be ``None`` if the
        catalog could not be loaded or the build failed — callers should
        skip AppConcierge tool registration in that case.
    """
    if _APPCONCIERGE_BUILDER is None:
        return None, None
    try:
        items: list = []
        if user_id:
            snapshot = await app_inventory_db.get_latest_snapshot(user_id)
            if snapshot:
                items = await app_inventory_db.get_snapshot_items(snapshot["id"])
        # Phase 1/2 only ships Android execution paths; iOS schema is in
        # catalog but execution is gated for a later phase per decision #9.
        candidates = _APPCONCIERGE_BUILDER.build(user_id, "android", items)
        section = render_candidates_section(candidates) or None
        if section:
            logger.info(
                "AppConcierge built (user_id={}, inventory_items={}, categories={})",
                user_id,
                len(items),
                len(candidates.by_category),
            )
        return candidates, section
    except Exception as e:
        logger.warning(f"AppConcierge build failed for user {user_id}: {e}")
        return None, None


async def _handle_app_handoff_message(
    *,
    msg_type: str,
    msg_data: dict,
    store: "AppHandoffStore",
    session_pc_id: Optional[str],
    context_aggregator,
    task,
) -> None:
    """Process an `app_handoff_*` lifecycle message from the client.

    For `opened` we just update the lifecycle store (no LLM trigger).
    For `back_confirmed` we cache the screenshot, run the existing vision
    analyzer, then push an `LLMMessagesAppendFrame(run_llm=True)` so the
    supervisor follows up naturally. `failed` and `timeout` push a
    similar event message but skip the screenshot path.
    """
    handoff_id = msg_data.get("handoff_id") or ""

    if msg_type == "app_handoff_opened":
        rec = store.mark_opened(session_pc_id, handoff_id, payload=msg_data)
        if rec is None:
            logger.warning(
                "app_handoff_opened ignored — unknown handoff_id={} for session_pc_id={}",
                handoff_id,
                session_pc_id,
            )
        else:
            logger.info(
                "AppConcierge opened (handoff_id={}, app_id={}, path={})",
                rec.handoff_id,
                rec.app_id,
                msg_data.get("execution_path_used"),
            )
        return

    if msg_type == "app_handoff_back_confirmed":
        # Q5 (B): cache screenshot, run vision analyzer, then trigger LLM.
        screenshot_b64 = msg_data.get("screenshot_base64") or ""
        if screenshot_b64 and session_pc_id:
            try:
                from tools.handoff_screenshot import _handoff_screenshots

                _handoff_screenshots[session_pc_id] = screenshot_b64
                logger.info(
                    "AppConcierge stored back screenshot (handoff_id={}, size={})",
                    handoff_id,
                    len(screenshot_b64),
                )
            except Exception as e:
                logger.warning(f"Failed to store app handoff screenshot: {e}")

        rec = store.mark_returned(session_pc_id, handoff_id, payload=msg_data)
        if rec is None:
            logger.warning(
                "app_handoff_back_confirmed for unknown handoff_id={} session_pc_id={} "
                "— still injecting event so the LLM can follow up.",
                handoff_id,
                session_pc_id,
            )

        analysis_json: Optional[str] = None
        if screenshot_b64 and session_pc_id:
            try:
                analysis_json = await analyze_handoff_screenshot_for_session(session_pc_id)
            except Exception as e:
                logger.error(f"App handoff screenshot analysis failed: {e}")

        # Best-effort display name fallback.
        display_name = (rec.app_id if rec else msg_data.get("app_id")) or "앱"

        try:
            await task.queue_frames([InterruptionFrame()])
            event_text = build_back_confirmed_event_text(
                rec,
                payload=msg_data,
                app_display_name=display_name,
                analysis_json=analysis_json,
            )
            await context_aggregator.user().push_frame(
                LLMMessagesAppendFrame(
                    messages=[{"role": "user", "content": event_text}],
                    run_llm=True,
                )
            )
            logger.info(
                "AppConcierge back event injected (handoff_id={}, analysis_len={})",
                handoff_id,
                len(analysis_json or ""),
            )
        except Exception as e:
            logger.error(f"Failed to inject app handoff back event: {e}")
        return

    if msg_type == "app_handoff_failed":
        rec = store.mark_failed(session_pc_id, handoff_id, payload=msg_data)
        display_name = (rec.app_id if rec else msg_data.get("app_id")) or "앱"
        try:
            event_text = build_failed_event_text(
                rec, payload=msg_data, app_display_name=display_name
            )
            await context_aggregator.user().push_frame(
                LLMMessagesAppendFrame(
                    messages=[{"role": "user", "content": event_text}],
                    run_llm=True,
                )
            )
            logger.info(
                "AppConcierge failed event injected (handoff_id={}, reason={})",
                handoff_id,
                msg_data.get("reason"),
            )
        except Exception as e:
            logger.error(f"Failed to inject app handoff failed event: {e}")
        return

    if msg_type == "app_handoff_timeout":
        rec = store.mark_timeout(session_pc_id, handoff_id, payload=msg_data)
        display_name = (rec.app_id if rec else msg_data.get("app_id")) or "앱"
        try:
            event_text = build_timeout_event_text(
                rec, payload=msg_data, app_display_name=display_name
            )
            await context_aggregator.user().push_frame(
                LLMMessagesAppendFrame(
                    messages=[{"role": "user", "content": event_text}],
                    run_llm=True,
                )
            )
            logger.info("AppConcierge timeout event injected (handoff_id={})", handoff_id)
        except Exception as e:
            logger.error(f"Failed to inject app handoff timeout event: {e}")
        return


async def fetch_recent_memories(
    memory_client: Union[MemoryClient, Memory],
    user_id: Optional[str],
    agent_id: Optional[str] = None,
    limit: int = 20,
    search_timeout: float = 5.0,
) -> List[str]:
    """Fetch recent memory contents for a user.

    Uses ``get_all`` (recency) rather than ``search("")`` because the OSS Mem0
    server's semantic search requires a non-empty query and returns no results
    for an empty one.

    Returns:
        A list of de-duplicated memory text strings (most recent first).
        Returns an empty list when the client is missing, the call fails, or
        no memories are stored for the user.
    """
    if memory_client is None:
        return []

    params: Dict[str, Any] = {"limit": limit}
    if user_id:
        params["user_id"] = user_id
    if agent_id:
        params["agent_id"] = agent_id

    try:
        if isinstance(memory_client, Memory):
            raw = memory_client.get_all(**params)
        else:
            try:
                raw = await asyncio.wait_for(
                    asyncio.to_thread(memory_client.get_all, **params),
                    timeout=search_timeout,
                )
            except asyncio.TimeoutError:
                logger.warning(f"Memory get_all timed out for user {user_id}")
                return []

        memory_list: List[Any] = []
        if isinstance(raw, dict):
            memory_list = raw.get("results") or raw.get("items") or raw.get("data") or []
        elif isinstance(raw, list):
            memory_list = raw

        logger.debug(
            f"fetch_recent_memories: user_id={user_id} agent_id={agent_id} "
            f"raw_type={type(raw).__name__} count={len(memory_list)}"
        )

        unique_memories: List[str] = []
        seen_contents: set[str] = set()

        for m in memory_list:
            content = (
                m.get("memory", m.get("content", "")) if isinstance(m, dict) else str(m)
            ).strip()

            if len(content) > 5 and content not in seen_contents:
                if len(content) > 200:
                    content = content[:197] + "..."
                unique_memories.append(content)
                seen_contents.add(content)

            if len(unique_memories) >= 10:
                break

        return unique_memories

    except Exception as e:
        logger.error(f"Error retrieving memories from Mem0: {e}")
        return []


def _insert_memory_system_message(memory_text: str):
    """Build a transform that inserts a system-role memory message after existing system messages.

    Why: keeps the memory block at the top of the prompt (right after the main system prompt)
    so the model treats it as context, not as conversation history.
    """

    def _transform(msgs):
        insert_idx = 0
        for i, m in enumerate(msgs):
            if isinstance(m, dict) and m.get("role") == "system":
                insert_idx = i + 1
            else:
                break
        return msgs[:insert_idx] + [{"role": "system", "content": memory_text}] + msgs[insert_idx:]

    return _transform


async def _hydrate_context_with_memories(
    context: LLMContext,
    memory_client: Union[MemoryClient, Memory],
    user_id: str,
    agent_id: str = "voice-ai",
) -> None:
    """Fetch user memories and inject them into the context as a system message.

    Runs in the background after the initial greeting so the first audio response is
    not delayed by the Mem0 round-trip.
    """
    try:
        memories = await asyncio.wait_for(
            fetch_recent_memories(memory_client, user_id, agent_id=agent_id),
            timeout=10.0,
        )
    except asyncio.TimeoutError:
        logger.warning(f"Background memory hydration timed out for user {user_id}")
        return
    except Exception as e:
        logger.warning(f"Background memory hydration failed (non-fatal): {e}")
        return

    if not memories:
        logger.debug(f"No memories to hydrate for user {user_id}")
        return

    memory_text = (
        "사용자에 대한 이전 대화에서 알게 된 정보 (필요할 때 자연스럽게 참고):\n"
        + "\n".join(f"- {m}" for m in memories)
    )
    context.transform_messages(_insert_memory_system_message(memory_text))
    logger.info(f"Hydrated context with {len(memories)} memories for user {user_id}")


async def _run_translation_bot(
    transport: BaseTransport,
    runner_args: RunnerArguments,
    session_pc_id: Optional[str],
    user_id: Optional[str],
):
    """턴 기반 "번역 Agent" 전용 봇.

    별도 Activity(``TranslationWebViewActivity``)가 ``?mode=translation`` 으로 새
    WebRTC 연결을 맺으면 ``run_bot()`` 이 이 함수로 분기한다. 메인 대화 파이프라인과
    완전히 분리된 독립 파이프라인이며 **연결당 파이프라인 1개**라, 예전 번역 모드처럼
    하나의 transport를 두 파이프라인이 공유해 충돌하던 문제가 없다.

    파이프라인: ``STT(한↔영) → 번역 LLM → TTS``. 사용자 발화는 STT 텍스트(사용자
    버블)로, 번역 결과는 LLM 텍스트(봇 버블)로 화면에 표시되며 TTS로 재생된다.

    동시통역 Agent(``GeminiLiveTranslationActivity`` / ``gemini_google_native``)와는
    무관한 별도 경로다.
    """
    logger.info("Starting bot (translation agent mode)")

    # 최신 API 키 사용을 위해 .env 재로드
    env_path = Path(__file__).parent / ".env"
    load_dotenv(env_path, override=True)

    # STT — 한국어/영어 모두 인식 (어느 쪽을 말하든 반대 언어로 번역)
    soniox_api_key = (os.getenv("SONIOX_API_KEY") or "").strip()
    if not soniox_api_key or soniox_api_key == "your_soniox_api_key":
        raise ValueError("SONIOX_API_KEY is required for the translation agent")
    stt = SonioxSTTService(
        api_key=soniox_api_key,
        vad_force_turn_endpoint=False,
        settings=SonioxSTTService.Settings(
            model="stt-rt-preview",
            language_hints=[Language.KO_KR, Language.EN_US],
        ),
    )

    openai_api_key = os.getenv("OPENAI_API_KEY")
    openai_model = os.getenv("OPENAI_MODEL", "gpt-4o")

    # 번역 LLM — 번역 전용 시스템 프롬프트만 사용 (도구/메모리/회의 모드 없음)
    translate_llm = OpenAILLMService(
        api_key=openai_api_key,
        settings=OpenAILLMService.Settings(model=openai_model, temperature=0.2),
    )

    # TTS — 번역문(영어/한국어)을 그대로 읽는다. OpenAI TTS는 텍스트 언어를 자동 인식.
    tts = OpenAITTSService(
        api_key=openai_api_key,
        settings=OpenAITTSService.Settings(voice="nova", model="tts-1"),
        text_aggregation_mode=TextAggregationMode.TOKEN,
    )

    rtvi = RTVIProcessor()

    # 번역 대상 언어를 사용자에게 먼저 물어본 뒤(첫 환영 메시지), 그 언어로 통역한다.
    welcome_message = "번역하려고 하는 상대방 언어를 알려주세요. (예, 영어, 스페인어 등)"
    translation_system_prompt = (
        "You are a turn-based translation assistant for a Korean user.\n"
        "\n"
        "# 첫 메시지 (환영)\n"
        "당신의 가장 첫 번째 발화는 반드시 아래 문장을 **글자 그대로**, 다른 말 없이 "
        "출력하세요:\n"
        f"{welcome_message}\n"
        "\n"
        "# 번역 대상 언어 설정\n"
        "- 사용자의 첫 응답은 번역 대상 언어입니다 (예: \"영어\", \"스페인어\", \"일본어\").\n"
        "- 대상 언어를 인식하면 한 문장으로 짧게 확인하세요. "
        '예: "네, 영어로 번역해 드릴게요. 말씀해 주세요."\n'
        "- 사용자가 언어명이 아니라 일반 문장을 먼저 말하면, 대상 언어를 영어로 간주하고 "
        "그 문장부터 바로 번역하세요.\n"
        "\n"
        "# 번역 (대상 언어 설정 후)\n"
        "- 사용자가 한국어로 말하면 → 대상 언어로 번역.\n"
        "- 사용자가 대상 언어로 말하면 → 한국어로 번역.\n"
        "- 번역문만 출력하세요. 설명·인용부호·언어 라벨 없이.\n"
        "- 원문의 의미·어조·격식을 유지하고, 짧은 발화도 빠짐없이 번역하세요."
    )
    context = LLMContext([{"role": "system", "content": translation_system_prompt}])
    context_aggregator = LLMContextAggregatorPair(
        context,
        user_params=LLMUserAggregatorParams(
            vad_analyzer=SileroVADAnalyzer(params=VADParams(stop_secs=0.5)),
        ),
    )

    # 파이프라인: 연결당 1개. transport.input()/output() 을 다른 파이프라인과 공유하지 않는다.
    pipeline = Pipeline(
        [
            transport.input(),
            rtvi,
            stt,
            context_aggregator.user(),
            translate_llm,
            tts,
            transport.output(),
            context_aggregator.assistant(),
        ]
    )

    task = PipelineTask(
        pipeline,
        params=PipelineParams(enable_metrics=True, enable_usage_metrics=True),
        observers=[RTVIObserver(rtvi)],
    )

    @rtvi.event_handler("on_client_ready")
    async def on_client_ready(rtvi):
        await rtvi.set_bot_ready()
        logger.info("Translation agent: client ready")
        # 연결 직후 LLM을 한 번 돌려 환영 메시지(번역 대상 언어 질문)를 생성한다.
        # 사용자 발화가 없어도 화면/음성으로 첫 안내가 나간다.
        await task.queue_frames([LLMRunFrame()])

    @transport.event_handler("on_client_disconnected")
    async def on_client_disconnected(transport, client):
        logger.info("Translation agent: client disconnected")
        await task.cancel()

    runner = PipelineRunner(handle_sigint=runner_args.handle_sigint)
    if session_pc_id:
        register_session_context(session_pc_id, task)
    try:
        await runner.run(task)
    finally:
        if session_pc_id:
            unregister_session_context(session_pc_id)


async def run_bot(transport: BaseTransport, runner_args: RunnerArguments):
    logger.info(f"Starting bot")

    # 현재 세션의 WebRTC pc_id 추출 (가능한 경우)
    session_pc_id: Optional[str] = None
    oauth_user_id: Optional[str] = (
        None  # OAuth users.id (JWT sub). MCP X-User-Id는 users.gportal_user_id 사용.
    )

    if isinstance(runner_args, SmallWebRTCRunnerArguments):
        # client_pc_id가 있으면 우선 사용 (실제 클라이언트 pc_id)
        client_pc_id = getattr(runner_args.webrtc_connection, "client_pc_id", None)
        if client_pc_id:
            session_pc_id = client_pc_id
            logger.info(f"Using client pc_id: {session_pc_id}")
        else:
            # fallback: 서버에서 생성한 pc_id 사용
            session_pc_id = getattr(runner_args.webrtc_connection, "pc_id", None)
            if session_pc_id:
                logger.info(f"Using server pc_id: {session_pc_id}")

        if hasattr(runner_args, "body") and isinstance(runner_args.body, dict):
            oauth_user_id = runner_args.body.get("user_id")
            if oauth_user_id:
                logger.info(f"Using authenticated user_id (OAuth): {oauth_user_id}")

    # 앱 전반에서 쓰는 user_id: OAuth 로그인 사용자에 대한 users.id (JWT sub)
    # MCP 호출 시 X-User-Id는 users.gportal_user_id 사용 (아래에서 조회 후 gportal_client.set_user_id 설정)
    user_id: Optional[str] = oauth_user_id

    # 번역 Agent: 별도 Activity(TranslationWebViewActivity)가 ?mode=translation 으로
    # 새 연결을 맺으면, 메인 대화 파이프라인 대신 번역 전용 파이프라인으로 분기한다.
    if (
        isinstance(runner_args, SmallWebRTCRunnerArguments)
        and isinstance(getattr(runner_args, "body", None), dict)
        and runner_args.body.get("mode") == "translation"
    ):
        await _run_translation_bot(transport, runner_args, session_pc_id, user_id)
        return

    # .env 파일을 명시적으로 다시 로드하여 최신 API 키 사용
    env_path = Path(__file__).parent / ".env"
    load_dotenv(env_path, override=True)

    # STT 서비스 선택 (환경변수 또는 기본값: soniox)
    stt_provider = os.getenv("STT_PROVIDER", "soniox").lower()

    if stt_provider == "soniox":
        # Soniox STT 설정
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

        stt = SonioxSTTService(
            api_key=soniox_api_key,
            # Soniox 자체 endpoint detection 활용. VAD 짧은 침묵에도 finalize되어
            # 발화가 마침표로 조각나는 문제를 방지. 한국어 자연 대화에 필수.
            vad_force_turn_endpoint=False,
            settings=SonioxSTTService.Settings(
                model="stt-rt-preview",
                language_hints=[Language.KO_KR],  # 한국어 설정
                enable_speaker_diarization=True,  # 회의 모드 speaker_id 트래킹
            ),
        )
    else:
        from pipecat.services.google.stt import GoogleSTTService

        # Google Cloud Speech-to-Text 설정 (기본값)
        logger.info("Using Google Cloud STT service")

        # .env에서 Google Cloud credentials 가져오기
        # GOOGLE_APPLICATION_CREDENTIALS: 서비스 계정 JSON 파일 경로
        # 또는 GOOGLE_CLOUD_CREDENTIALS: 서비스 계정 JSON 문자열
        google_credentials_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        # 상대 경로인 경우 프로젝트 루트 기준으로 변환
        if google_credentials_path and not os.path.isabs(google_credentials_path):
            # 프로젝트 루트 찾기 (mobile 디렉토리에서 1단계 위로)
            project_root = Path(__file__).parent.parent
            google_credentials_path = str(project_root / google_credentials_path)
        google_credentials_json = os.getenv("GOOGLE_CLOUD_CREDENTIALS")

        stt = GoogleSTTService(
            credentials=google_credentials_json,  # JSON 문자열이 있으면 사용
            credentials_path=google_credentials_path,  # 파일 경로가 있으면 사용
            recognizer_id="default-recognizer",  # 생성한 named recognizer 사용
            settings=GoogleSTTService.Settings(
                languages=[Language.KO_KR],  # 한국어 설정
                enable_interim_results=True,  # 중간 결과 표시
                enable_automatic_punctuation=True,  # 자동 구두점
            ),
        )

    # TTS 서비스 선택 (환경변수 또는 기본값: openai)
    tts_provider = os.getenv("TTS_PROVIDER", "openai").lower()

    if tts_provider == "cartesia":
        from pipecat.services.cartesia.tts import CartesiaTTSService

        # Cartesia TTS (한국어 지원, 고품질)
        logger.info("Using Cartesia TTS service")
        tts = CartesiaTTSService(
            api_key=os.getenv("CARTESIA_API_KEY"),
            settings=CartesiaTTSService.Settings(
                voice="71a7ad14-091c-4e8e-a314-022ece01c121",  # British Reading Lady
                language=Language.KO,
            ),
        )
    elif tts_provider == "elevenlabs":
        from pipecat.services.elevenlabs.tts import ElevenLabsTTSService

        # ElevenLabs TTS (한국어 지원, 고품질, 자연스러운 음성)
        logger.info("Using ElevenLabs TTS service")
        elevenlabs_api_key = os.getenv("ELEVENLABS_API_KEY")
        elevenlabs_voice_id = os.getenv("ELEVENLABS_VOICE_ID", "")

        if not elevenlabs_api_key or elevenlabs_api_key == "your_elevenlabs_api_key":
            logger.error("ELEVENLABS_API_KEY가 설정되지 않았습니다.")
            logger.error(
                "ElevenLabs TTS를 사용하려면 .env 파일에 ELEVENLABS_API_KEY를 설정해주세요."
            )
            raise ValueError(
                "ELEVENLABS_API_KEY environment variable is required when using ElevenLabs TTS. "
                "Please set it in your .env file."
            )

        tts = ElevenLabsTTSService(
            api_key=elevenlabs_api_key,
            settings=ElevenLabsTTSService.Settings(
                voice=elevenlabs_voice_id or "21m00Tcm4TlvDq8ikWAM",  # 기본: Rachel voice
                model="eleven_turbo_v2_5",  # 또는 "eleven_multilingual_v2" (다국어 지원)
                language=Language.KO,  # 한국어
                stability=0.5,  # 음성 안정성 (0.0-1.0)
                similarity_boost=0.75,  # 유사도 부스트 (0.0-1.0)
                speed=1.0,  # 음성 속도 (0.7-1.2)
            ),
        )
    else:
        # OpenAI TTS (기본값, 한국어 지원, 안정적)
        logger.info("Using OpenAI TTS service")
        tts = OpenAITTSService(
            api_key=os.getenv("OPENAI_API_KEY"),
            settings=OpenAITTSService.Settings(
                voice="nova",  # 다른 옵션: alloy, echo, fable, onyx, shimmer
                model="tts-1",  # 또는 "tts-1-hd" (고품질, 2배 비용)
            ),
            text_aggregation_mode=TextAggregationMode.TOKEN,
        )

    # ── Supervisor LLM 설정 (Pipecat OpenAILLMService) ──
    openai_model = os.getenv("OPENAI_MODEL", "gpt-4o")
    openai_api_key = os.getenv("OPENAI_API_KEY")
    logger.info(f"Using OpenAI model: {openai_model}")

    # 회의 Agent ON 시에만 사용하는 LLM (버튼으로 ON/OFF)
    meeting_llm = OpenAILLMService(
        api_key=openai_api_key,
        settings=OpenAILLMService.Settings(
            model=openai_model,
            temperature=0.2,
        ),
    )

    # RTVI processor (needed early so tools can send server-messages)
    rtvi = RTVIProcessor()

    # ── GPortal MCP 도구 (앱 시작 시 한 번 조회) ──
    session_store = SessionThreadStore()
    gportal_schemas: list[FunctionSchema] = []
    register_gportal: Optional[Any] = None
    gportal_tool_descriptions = None

    gportal_base_url = os.getenv("GPORTAL_MCP_BASE_URL")
    gportal_api_key = os.getenv("GPORTAL_MCP_API_KEY")
    gportal_password = os.getenv("GPORTAL_MCP_PASSWORD")

    # MCP X-User-Id: users 테이블의 gportal_user_id 사용 (OAuth user_id로 조회)
    gportal_user_id_for_mcp: Optional[str] = None
    if oauth_user_id:
        try:
            user_row = await auth_db.get_user_by_id(oauth_user_id)
            if user_row and user_row.get("gportal_user_id"):
                gportal_user_id_for_mcp = user_row["gportal_user_id"]
                logger.info(f"Using gportal_user_id for MCP X-User-Id: {gportal_user_id_for_mcp}")
        except Exception as e:
            logger.warning(f"Could not resolve gportal_user_id for MCP: {e}")

    gportal_client = None
    if gportal_base_url and gportal_api_key and gportal_password:
        try:
            stream_timeout_env = os.getenv("GPORTAL_MCP_STREAM_TIMEOUT", "").strip()
            stream_timeout: Optional[float] = None
            if stream_timeout_env:
                try:
                    stream_timeout = float(stream_timeout_env)
                except ValueError:
                    logger.warning(
                        f"Invalid GPORTAL_MCP_STREAM_TIMEOUT={stream_timeout_env!r}; using default."
                    )
            gportal_config = GportalMCPConfig(
                base_url=gportal_base_url,
                api_key=gportal_api_key,
                password=gportal_password,
                stream_timeout=stream_timeout if stream_timeout is not None else 50.0,
            )
            gportal_client = GportalMCPClient(gportal_config)
            gportal_client.set_user_id(gportal_user_id_for_mcp)

            raw_schemas = await gportal_client.list_tools()
            logger.info(f"GPortal: fetched {len(raw_schemas)} tool schemas")

            # 세션별 매핑 생성 (session_pc_id가 있으면)
            if session_pc_id:
                session_store.create(session_pc_id)

            gportal_schemas, register_gportal = create_gportal_pipecat_tools(
                gportal_client,
                raw_schemas,
                session_store=session_store,
                session_id=session_pc_id,
            )
            gportal_tool_descriptions = format_gportal_tool_descriptions(raw_schemas)
            logger.info(f"Created {len(gportal_schemas)} GPortal Pipecat tools:")
            for s in raw_schemas:
                name = s.get("name", "")
                desc = (s.get("description") or "")[:40]
                logger.info(f"  {name}, {desc}")
        except Exception as e:
            logger.warning(f"GPortal MCP initialization failed (non-fatal): {e}")
            logger.warning("Continuing without GPortal tools...")

    # ── Context / Aggregator (턴 관리용) ──
    # 시스템 프롬프트는 아래 build_session_system_prompt() 결과로 덮어쓴다.
    # context.set_tools()도 dispatcher 생성 후 호출하므로 여기서는 기본 시드만.
    messages = get_starting_messages()
    context = LLMContext(messages)
    # Soniox best practice (upstream examples/voice/voice-soniox.py 패턴):
    # - Soniox가 자체 endpoint detection 으로 발화 종료를 판단 (vad_force_turn_endpoint=False, 위 STT 설정)
    # - VAD는 turn end 보조 신호로만 사용 (smart turn analyzer 불필요)
    # - 한국어 자연 대화는 단어/조사 사이 짧은 휴지가 빈번하므로 stop_secs를 0.5초로 완화
    context_aggregator = LLMContextAggregatorPair(
        context,
        user_params=LLMUserAggregatorParams(
            vad_analyzer=SileroVADAnalyzer(params=VADParams(stop_secs=0.5)),
        ),
    )

    # Initialize Mem0 memory service (using MEM0_HOST for OSS server)
    # Mem0는 파이프라인에서 제거하고, prompt callable에서 검색/저장 유틸리티로만 사용
    memory = None
    mem0_host = os.getenv("MEM0_HOST", "http://localhost:8888")

    if user_id:
        logger.info(f"Using user_id for Mem0/wake: {user_id}")

    try:
        logger.info(f"Initializing Mem0 memory service (OSS) at {mem0_host}...")
        memory = Mem0MemoryService(
            api_key=None,
            host=mem0_host,
            user_id=user_id,
            agent_id="voice-ai",
            run_id=session_pc_id,
            params=Mem0MemoryService.InputParams(
                search_limit=5,
                search_threshold=0.35,
                api_version="v2",
                system_prompt="이전 대화를 기억해보니, \n\n",
                add_as_system_message=True,
                position=1,
            ),
            is_meeting_mode_active_func=is_meeting_mode_active,
        )
        logger.info(f"Mem0 memory service initialized for user: {user_id or '(unauthenticated)'}")
    except Exception as e:
        logger.warning(f"Failed to initialize Mem0 memory service: {e}")
        logger.warning("Continuing without memory features...")
        memory = None

    # ── 로컬 도구 (Pipecat function calling) ──
    # Mem0는 자동 주입이 아니라, 에이전트가 필요할 때 mem0_search tool을 호출하도록 제공합니다.
    local_schemas, register_locals = create_pipecat_local_tools(session_pc_id, mem0_service=memory)
    logger.info(f"Created {len(local_schemas)} local Pipecat tools")

    # ── 시스템 프롬프트 (정적 부분, 앱 시작 시 한 번) ──
    static_system_prompt = build_static_system_prompt(gportal_tool_descriptions)

    # ── AppConcierge: per-session candidates + prompt section ──
    appconcierge_candidates, appconcierge_section = await _build_appconcierge_for_user(user_id)
    session_system_prompt = build_session_system_prompt(static_system_prompt, appconcierge_section)

    # 메시지 시드의 system 메시지를 session_system_prompt로 덮어써서
    # static_system_prompt + appconcierge_section + GPortal/MEM0/IMPORTANT_RULES
    # 까지 supervisor가 알 수 있게 한다.
    context.set_messages(
        [{"role": "system", "content": session_system_prompt}]
        + [m for m in messages if m.get("role") != "system"]
    )

    # ── AppConcierge: per-session handoff lifecycle store ──
    app_handoff_store = AppHandoffStore()

    # ── Supervisor LLM (Pipecat OpenAILLMService) ──
    supervisor_llm = OpenAILLMService(
        api_key=openai_api_key,
        settings=OpenAILLMService.Settings(
            model=openai_model,
            temperature=0.2,
        ),
    )

    # Mem0 저장: assistant 턴이 끝날 때 직전 user 발화 + 누적 assistant 텍스트를 저장.
    # 저장 트리거를 supervisor_llm 직후로 둬야 TTS의 serialization-queue 버퍼링이나
    # 사용자 interrupt/background 때문에 LLMFullResponseEndFrame이 sink까지 도달하지
    # 못해도 안전하게 저장된다.
    _last_user_text: dict[str, str] = {"value": ""}
    _assistant_buf: list[str] = []
    _assistant_collecting: dict[str, bool] = {"value": False}

    def _extract_latest_user_text(context: LLMContext) -> str:
        try:
            messages = context.get_messages()
        except Exception:
            return ""
        for m in reversed(messages):
            if not isinstance(m, dict) or m.get("role") != "user":
                continue
            content = m.get("content")
            if isinstance(content, str):
                return content.strip()
            if isinstance(content, list):
                parts = []
                for item in content:
                    if isinstance(item, dict) and item.get("type") == "text":
                        parts.append(item.get("text", ""))
                joined = " ".join(p for p in parts if p).strip()
                if joined:
                    return joined
        return ""

    # 회의 모드 ON일 때는 supervisor_llm을 비활성화하고 뒤단의 meeting_llm이
    # context를 처리하도록 한다 (이전 LangGraphReActProcessor의 is_active_fn 역할).
    # 회의 모드 OFF일 때만 OpenAILLMService 본래의 process_frame을 호출하고,
    # ON이면 모든 frame을 그대로 흘려보낸다 (LLMContextFrame 포함).
    _supervisor_process_frame_original = supervisor_llm.process_frame

    async def _supervisor_process_frame_gated(frame, direction: FrameDirection):
        # supervisor에 LLMContextFrame이 들어올 때마다 가장 최근 user 발화를 캡처.
        # (회의 모드 ON/OFF 무관)
        if isinstance(frame, LLMContextFrame):
            user_text = _extract_latest_user_text(frame.context)
            if user_text:
                _last_user_text["value"] = user_text

        # 회의 모드 ON: supervisor는 완전 passthrough.
        if is_meeting_mode_active(session_pc_id):
            await LLMService.process_frame(supervisor_llm, frame, direction)
            await supervisor_llm.push_frame(frame, direction)
            return
        await _supervisor_process_frame_original(frame, direction)

    supervisor_llm.process_frame = _supervisor_process_frame_gated

    # supervisor_llm.push_frame을 wrap해서 LLM 응답 프레임을 TTS 이전 시점에 가로챈다.
    _supervisor_push_frame_original = supervisor_llm.push_frame

    async def _supervisor_push_frame_capturing(
        frame, direction: FrameDirection = FrameDirection.DOWNSTREAM
    ):
        if direction == FrameDirection.DOWNSTREAM:
            if isinstance(frame, LLMFullResponseStartFrame):
                _assistant_buf.clear()
                _assistant_collecting["value"] = True
            elif isinstance(frame, LLMTextFrame) and _assistant_collecting["value"]:
                t = getattr(frame, "text", None)
                if t:
                    _assistant_buf.append(t)
            elif isinstance(frame, LLMFullResponseEndFrame):
                _assistant_collecting["value"] = False
                if memory is not None:
                    user_text = _last_user_text["value"].strip()
                    assistant_text = "".join(_assistant_buf).strip()
                    logger.info(
                        "Supervisor response generated (user_len={}, assistant_len={})",
                        len(user_text),
                        len(assistant_text),
                    )
                    try:
                        if user_text:
                            await memory._store_messages([{"role": "user", "content": user_text}])
                        if assistant_text:
                            await memory._store_messages(
                                [{"role": "assistant", "content": assistant_text}]
                            )
                    except Exception as e:
                        logger.warning(f"Mem0 store failed (non-fatal): {e}")
                    _last_user_text["value"] = ""
        await _supervisor_push_frame_original(frame, direction)

    supervisor_llm.push_frame = _supervisor_push_frame_capturing

    # 회의 Agent 전용 end_meeting_mode (회의 Agent ON일 때만 동작)
    meeting_llm.register_function(
        "end_meeting_mode",
        create_end_meeting_mode(
            context_aggregator,
            memory,
            session_pc_id,
            None,
            rtvi,
        ),
    )

    # Wake word filter - 초기 상태는 AWAKE (클라이언트 접속 시 대화 가능)
    wake_filter = WakeCheckFilter(
        # Wake word: "DuriOn". STT(Soniox 한국어)가 들려주는 음차 변형까지 폭넓게 매칭.
        # 각 phrase는 \b…\b 정규식(단어 사이 \s*)으로 검사되므로 "헤이 두리온" 하나로
        # 붙여 말한 "헤이두리온"도 함께 매칭된다.
        wake_phrases=[
            # 영문 표기 (STT가 영문으로 줄 경우)
            "DuriOn",
            "Durion",
            # 한글 음차 변형 (bare + 헤이/안녕/하이 프리픽스)
            "두리온",
            "헤이 두리온",
            "안녕 두리온",
            "하이 두리온",
            "듀리온",
            "헤이 듀리온",
            "안녕 듀리온",
            "하이 듀리온",
            "듀리언",
            "헤이 듀리언",
            "안녕 듀리언",
            "하이 듀리언",
            "두리언",
            "헤이 두리언",
            "안녕 두리언",
            "하이 두리언",
            "리언",
            "헤이 리언",
            "안녕 리언",
            "하이 리언",
        ],
        keepalive_timeout=5.0,
        initial_state=WakeCheckFilter.WakeState.AWAKE,
    )

    # ── 파이프라인 구성 ──
    # Main: user_agg → supervisor_llm → meeting_llm(passthrough) → TTS
    # 회의 Agent ON: user_agg → supervisor_llm(passthrough) → meeting_llm → TTS
    # Optional: reduce TTS fragmentation by aggregating more aggressively before TTS.
    # When enabled, this tends to reduce the number of TTS requests (and thus TTS spans in Langfuse).
    _single_tts_chunk = os.getenv("PIPECAT_TTS_SINGLE_CHUNK", "").lower() in ("1", "true", "yes")
    if _single_tts_chunk:
        text_aggregator = MaxLengthTextAggregator(
            min_chars_before_yield=100_000, max_chars_before_yield=200_000
        )
    else:
        text_aggregator = MaxLengthTextAggregator()

    pipeline_components = [
        transport.input(),
        rtvi,
        stt,
        wake_filter,
        context_aggregator.user(),
        supervisor_llm,
        meeting_llm,
        LLMTextProcessor(text_aggregator=text_aggregator),
        # LLMTextProcessor가 토큰을 문장/단락 단위 AggregatedTextFrame으로 묶은
        # 직후에 첫 문장을 split하고, 그 뒤 chunk는 skip_tts=True로 마킹한다.
        # 음성으로는 헤드라인만, 화면에는 전체 텍스트가 보인다.
        FirstSentenceTTSFilter(),
        tts,
        transport.output(),
        context_aggregator.assistant(),
    ]

    pipeline = Pipeline(pipeline_components)

    # PIPELINE_IDLE_TIMEOUT_SECS: Idle timeout 시간(초), None이면 비활성화 (기본값: None, 즉 비활성화)
    idle_timeout_secs_str = os.getenv("PIPELINE_IDLE_TIMEOUT_SECS")
    idle_timeout_secs = float(idle_timeout_secs_str) if idle_timeout_secs_str else None

    # ── Langfuse(OpenTelemetry) tracing 메타데이터 구성 ──
    # conversation_id: Langfuse에서 세션 단위로 보기 좋은 식별자
    # additional_span_attributes: user/session/transport 등의 필터링용 태그
    tracing_conversation_id: Optional[str] = None
    additional_span_attributes: dict[str, Any] = {}

    # Common attributes
    if user_id:
        additional_span_attributes["user.id"] = user_id
        # Langfuse OTLP explicit mapping (recommended)
        additional_span_attributes["langfuse.user.id"] = user_id
    additional_span_attributes["pipecat.bot"] = "voice_ai"

    if isinstance(runner_args, SmallWebRTCRunnerArguments):
        tracing_conversation_id = session_pc_id
        additional_span_attributes["transport.type"] = "webrtc"
        if session_pc_id:
            additional_span_attributes["webrtc.pc_id"] = session_pc_id

    elif isinstance(runner_args, WebSocketRunnerArguments):
        additional_span_attributes["transport.type"] = "telephony"
        # Best-effort: extract call/session ids from the serializer configured by create_transport()
        params = getattr(transport, "_params", None)
        serializer = getattr(params, "serializer", None) if params else None
        for attr in ("call_id", "call_sid", "call_control_id", "stream_id", "stream_sid"):
            value = getattr(serializer, attr, None)
            if value:
                tracing_conversation_id = str(value)
                additional_span_attributes[f"telephony.{attr}"] = str(value)
                break

    elif isinstance(runner_args, DailyRunnerArguments):
        additional_span_attributes["transport.type"] = "daily"
        additional_span_attributes["daily.room_url"] = runner_args.room_url
        # Dial-in call_id if present
        body = runner_args.body if isinstance(runner_args.body, dict) else {}
        dialin_settings = body.get("dialin_settings") if isinstance(body, dict) else None
        call_id = dialin_settings.get("call_id") if isinstance(dialin_settings, dict) else None
        tracing_conversation_id = str(call_id) if call_id else runner_args.room_url

    elif isinstance(runner_args, LiveKitRunnerArguments):
        additional_span_attributes["transport.type"] = "livekit"
        additional_span_attributes["livekit.room_name"] = runner_args.room_name
        tracing_conversation_id = runner_args.room_name

    # Langfuse Sessions use sessionId. Prefer explicit mapping, fall back to session.id.
    if tracing_conversation_id:
        additional_span_attributes["session.id"] = tracing_conversation_id
        additional_span_attributes["langfuse.session.id"] = tracing_conversation_id

    # Give the trace a stable, searchable name in Langfuse.
    additional_span_attributes.setdefault("langfuse.trace.name", "voice_ai")

    task = PipelineTask(
        pipeline,
        params=PipelineParams(
            enable_metrics=True,
            enable_usage_metrics=True,
        ),
        enable_turn_tracking=True,
        enable_tracing=True,
        conversation_id=tracing_conversation_id,
        additional_span_attributes=additional_span_attributes,
        observers=[RTVIObserver(rtvi)],
        idle_timeout_secs=idle_timeout_secs,
    )

    # ── 도구 등록: task가 생긴 뒤에야 dispatcher를 만들 수 있다 ──
    # 1) 로컬 + GPortal 핸들러를 supervisor_llm에 부착
    register_locals(supervisor_llm)
    if register_gportal is not None:
        register_gportal(supervisor_llm)

    # 2) AppConcierge 도구 (Phase 3: per-app open_<app_id>)
    appconcierge_schemas: list[FunctionSchema] = []
    if appconcierge_candidates is not None and appconcierge_candidates.by_category:
        app_invoke_dispatcher = make_app_invoke_dispatcher(
            rtvi=rtvi,
            task=task,
            store=app_handoff_store,
            catalog=appconcierge_candidates,
            session_pc_id=session_pc_id,
            user_id=user_id,
            platform="android",
        )
        appconcierge_schemas, register_appconcierge = create_per_app_pipecat_tools(
            dispatcher=app_invoke_dispatcher,
            candidates=appconcierge_candidates,
        )
        register_appconcierge(supervisor_llm)
        logger.info(
            "AppConcierge: {} per-app tools registered ({})",
            len(appconcierge_schemas),
            ", ".join(s.name for s in appconcierge_schemas),
        )

    # 3) 도구 스키마를 context에 붙인다 (Pipecat ToolsSchema).
    standard_tools: list[FunctionSchema] = (
        list(local_schemas) + list(gportal_schemas) + list(appconcierge_schemas)
    )
    context.set_tools(ToolsSchema(standard_tools=standard_tools))
    logger.info(
        f"Total tools on supervisor: {len(standard_tools)} "
        f"(local={len(local_schemas)}, gportal={len(gportal_schemas)}, "
        f"appconcierge={len(appconcierge_schemas)})"
    )

    # 메인 파이프라인 정보를 번역 모드 복원을 위해 저장
    # 번역 모드가 시작되기 전에 메인 파이프라인 정보를 저장해야 함
    if session_pc_id:
        _main_tasks[session_pc_id] = task
        # 메인 파이프라인의 첫 번째 프로세서는 RTVIProcessor
        # pipeline의 첫 번째 프로세서를 찾아서 저장
        # PipelineSource가 아닌 실제 첫 번째 프로세서(RTVIProcessor)를 찾아야 함
        if pipeline and hasattr(pipeline, "_processors") and len(pipeline._processors) > 0:
            # PipelineSource를 건너뛰고 실제 첫 번째 프로세서를 찾음
            first_processor = None
            for processor in pipeline._processors:
                # PipelineSource가 아닌 첫 번째 프로세서를 찾음
                if not isinstance(processor, PipelineSource):
                    first_processor = processor
                    # RTVIProcessor를 우선적으로 찾음
                    if isinstance(processor, RTVIProcessor):
                        break

            if first_processor:
                _main_first_processors[session_pc_id] = first_processor
            else:
                # 찾지 못했으면 rtvi를 사용
                _main_first_processors[session_pc_id] = rtvi
        else:
            # pipeline._processors가 없으면 rtvi를 첫 번째 프로세서로 사용
            _main_first_processors[session_pc_id] = rtvi
        logger.debug(f"Stored main pipeline info for session {session_pc_id} (task: {task.name})")

    # RTVI client ready handler - send greeting immediately, hydrate memories in background.
    @rtvi.event_handler("on_client_ready")
    async def on_client_ready(rtvi):
        await rtvi.set_bot_ready()
        if user_id:
            wake_filter.set_to_awake(user_id)
            logger.info(f"Client ready: Set user {user_id} to AWAKE state (initial connection)")

        # 인사말(welcome message)은 UI(Idle 홈 화면)에서 제거됐으므로 앱 시작
        # 시점에 LLM을 호출하지 않는다. 첫 사용자 발화 때부터 LLM이 동작한다.

        # 백그라운드에서 mem0 메모리를 가져와 context의 system 메시지 뒤에 주입한다.
        #    첫 사용자 발화 시점부터 LLM이 사용자의 과거 맥락을 참조할 수 있다.
        if memory and memory.memory_client and user_id:
            task.create_task(
                _hydrate_context_with_memories(context, memory.memory_client, user_id),
                name=f"hydrate_memories_{user_id}",
            )

    @task.event_handler("on_pipeline_ended")
    async def on_pipeline_ended(task, frame):
        if isinstance(frame, EndFrame) and is_meeting_mode_active(session_pc_id):
            logger.info("Pipeline ending (EndFrame) - meeting Agent was active")

    # 회의 Agent ON일 때만 meeting_llm 토큰 제한 적용
    token_controller = get_token_controller()
    meeting_llm.process_frame = token_controller.create_process_frame_with_limit(
        meeting_llm,
        session_pc_id,
        is_meeting_mode_active,
        get_meeting_mode_start_message_count,
        set_meeting_mode_start_message_count,
    )

    # CancelFrame은 task 차원에서만 감지하면 충분하므로 그대로 유지.
    task.set_reached_downstream_filter((CancelFrame,))

    @task.event_handler("on_frame_reached_downstream")
    async def on_frame_reached_downstream(task, frame):
        if isinstance(frame, CancelFrame) and is_meeting_mode_active(session_pc_id):
            logger.info("Pipeline cancelling (CancelFrame) - meeting Agent was active")

    # RTVI client message handler - 앱 상태 변경 처리
    @rtvi.event_handler("on_client_message")
    async def on_client_message(rtvi, message: Any):
        """Handle client messages (app state, 회의 Agent ON/OFF, 번역 모드).

        Expected message format from client:
        {
            "t": "app_state",
            "d": {
                "state": "background" or "foreground",
                "user_id": "optional_user_id"
            }
        }
        """
        logger.info(f"Received client message: {message}, type: {type(message)}")
        # RTVI client message는 RTVIClientMessage 객체로 전달됨
        # 또는 dict 형태일 수도 있음
        if hasattr(message, "type") and hasattr(message, "data"):
            msg_type = message.type
            msg_data = message.data
            logger.debug(f"Message has type/data attributes: type={msg_type}, data={msg_data}")
        elif isinstance(message, dict):
            msg_type = message.get("type") or message.get("t")
            msg_data = message.get("data") or message.get("d")
            logger.debug(f"Message is dict: type={msg_type}, data={msg_data}")
        else:
            logger.warning(f"Unknown message format: {message}")
            return

        logger.info(f"Processing client message: type={msg_type}, data={msg_data}")

        # Handle meeting mode control
        if msg_type == "webview_handoff_opened":
            # WebView handoff: external link was opened by the app (client-side decision)
            url = ""
            if isinstance(msg_data, dict):
                url = msg_data.get("url", "") or ""
            event_text = (
                "[APP_EVENT: webview_handoff_opened] "
                "앱이 외부 링크를 WebView handoff로 열었습니다. "
                f"url={url}"
            )
            try:
                message_frame = LLMMessagesAppendFrame(
                    messages=[{"role": "user", "content": event_text}],
                    run_llm=False,
                )
                await context_aggregator.user().push_frame(message_frame)
                logger.info("Injected handoff opened event to LLM context")
            except Exception as e:
                logger.error(f"Failed to inject handoff opened event: {e}")
            return

        if msg_type == "webview_handoff_back_confirmed":
            # WebView handoff: user confirmed going back (screenshot delivered via data channel)
            if isinstance(msg_data, dict):
                handoff_id = msg_data.get("handoff_id", "")
                screenshot_b64 = msg_data.get("screenshot_base64", "")
                page_url = msg_data.get("page_url", "")
                page_title = msg_data.get("page_title", "")

                if screenshot_b64 and session_pc_id:
                    try:
                        from tools.handoff_screenshot import _handoff_screenshots

                        _handoff_screenshots[session_pc_id] = screenshot_b64
                        logger.info(
                            "Stored handoff screenshot for session {} (handoff_id={}, size={})",
                            session_pc_id,
                            handoff_id,
                            len(screenshot_b64),
                        )
                    except Exception as e:
                        logger.warning("Failed to store handoff screenshot: {}", e)

                try:
                    # Stop in-progress bot TTS from the prior turn so handoff LLM+TTS can run soon.
                    await task.queue_frames([InterruptionFrame()])
                    # Backend-forced path:
                    # 1) analyze screenshot directly in server code
                    # 2) inject analysis JSON as context
                    # 3) run LLM exactly once for final user-facing answer
                    analysis_json = await analyze_handoff_screenshot_for_session(session_pc_id)
                    event_text = (
                        "[APP_EVENT: webview_handoff_back_confirmed] "
                        "사용자가 외부 사이트에서 돌아왔습니다. "
                        f"handoff_id={handoff_id}, page_url={page_url}, page_title={page_title}. "
                        "아래는 서버에서 직접 실행한 analyze_handoff_screenshot 결과(JSON)입니다:\n"
                        f"{analysis_json}\n\n"
                        "규칙: 위 JSON을 근거로 바로 최종 답변을 완료하세요. "
                        "중간 안내(예: '잠시만 기다려 주세요')만 말하고 멈추지 마세요."
                    )
                    message_frame = LLMMessagesAppendFrame(
                        messages=[{"role": "user", "content": event_text}],
                        run_llm=True,
                    )
                    await context_aggregator.user().push_frame(message_frame)
                    logger.info(
                        "Injected backend-analyzed handoff event and triggered LLM run "
                        "(handoff_id={}, page_title={}, analysis_len={})",
                        handoff_id,
                        page_title,
                        len(analysis_json or ""),
                    )
                except Exception as e:
                    logger.error(f"Failed to inject handoff event: {e}")

            return

        if msg_type == "webview_handoff_back_cancelled":
            handoff_id = msg_data.get("handoff_id", "") if isinstance(msg_data, dict) else ""
            logger.info("User cancelled handoff back (handoff_id={})", handoff_id)
            return

        # ── AppConcierge handoff lifecycle ──
        if msg_type in (
            "app_handoff_opened",
            "app_handoff_back_confirmed",
            "app_handoff_failed",
            "app_handoff_timeout",
        ):
            await _handle_app_handoff_message(
                msg_type=msg_type,
                msg_data=msg_data if isinstance(msg_data, dict) else {},
                store=app_handoff_store,
                session_pc_id=session_pc_id,
                context_aggregator=context_aggregator,
                task=task,
            )
            return

        if msg_type == "start_meeting_mode":
            if is_meeting_mode_active(session_pc_id):
                logger.debug("Meeting mode is already active, skipping start")
            else:
                # Directly call start_meeting_mode function
                try:
                    # Get the start_meeting_mode handler and call it
                    start_handler = create_start_meeting_mode(
                        context_aggregator,
                        memory,
                        session_pc_id,
                        None,
                        rtvi,
                    )

                    class MockFunctionCallParams:
                        def __init__(self):
                            async def result_callback(result, **kwargs):
                                logger.debug(f"Meeting mode start result: {result}")

                            self.result_callback = result_callback

                    await start_handler(MockFunctionCallParams())
                    logger.info("Meeting mode started via client message")
                except Exception as e:
                    logger.error(f"Failed to start meeting mode: {e}")
        elif msg_type == "end_meeting_mode":
            if not is_meeting_mode_active(session_pc_id):
                logger.debug("Meeting mode is not active, skipping end")
            else:
                # 회의 모드를 먼저 비활성화하여 LLM 호출이 차단되지 않도록 함
                # end_meeting_mode 함수는 내부에서 활성화 상태를 확인하지 않고 바로 처리하도록 수정됨
                # 메시지 개수를 먼저 저장 (set_meeting_mode가 메시지 개수를 제거하기 전에)
                start_message_count = get_meeting_mode_start_message_count(session_pc_id)
                set_meeting_mode(session_pc_id, False)
                logger.debug("Temporarily deactivated meeting mode to allow LLM call")

                # LLM이 end_meeting_mode 함수를 호출하도록 메시지 추가
                # 이렇게 하면 LLM이 end_meeting_mode 함수를 호출하고,
                # 함수의 result에 포함된 summary를 사용자에게 전달하게 됨
                try:
                    # LLMMessagesAppendFrame을 context_aggregator.user()로 push
                    # 이렇게 하면 user context aggregator가 메시지를 처리하고 LLM이 응답을 생성함
                    message_frame = LLMMessagesAppendFrame(
                        messages=[{"role": "user", "content": "회의 모드를 종료해줘."}],
                        run_llm=True,
                    )
                    await context_aggregator.user().push_frame(message_frame)
                    logger.info("Requested LLM to end meeting mode via message")
                except Exception as e:
                    logger.error(f"Failed to request end meeting mode: {e}")
                    # 에러 발생 시 회의 모드 상태 복원
                    set_meeting_mode(session_pc_id, True)
                    if start_message_count is not None:
                        set_meeting_mode_start_message_count(session_pc_id, start_message_count)
        elif msg_type == "start_translation_mode":
            if is_translation_mode_active(session_pc_id):
                logger.debug("Translation mode is already active, skipping start")
            else:
                # Directly call start_translation_mode function
                try:
                    # task와 transport를 전달하여 번역 모드 시작 시 기존 task를 일시 중지하고
                    # 별도의 번역 전용 파이프라인을 생성할 수 있도록 함
                    start_handler = create_start_translation_mode(
                        context_aggregator,
                        pipeline,
                        session_pc_id,
                        rtvi,
                        stt,
                        transport,
                        task=task,
                        runner_args=runner_args,
                    )

                    class MockFunctionCallParams:
                        def __init__(self, arguments=None):
                            async def result_callback(result, **kwargs):
                                logger.debug(f"Translation mode start result: {result}")

                            self.result_callback = result_callback
                            self.arguments = arguments or {}

                    # 클라이언트가 보낸 data(provider 등)를 그대로 인자로 전달한다.
                    # 웹 "번역 Agent"는 provider=gemini_google,
                    # 네이티브 "동시통역 Agent"는 provider=gemini_google_native를 보낸다.
                    translation_args = msg_data if isinstance(msg_data, dict) else {}
                    await start_handler(MockFunctionCallParams(translation_args))
                    logger.info("Translation mode started via client message")
                except Exception as e:
                    logger.error(f"Failed to start translation mode: {e}")
        elif msg_type == "end_translation_mode":
            # 번역 모드를 직접 종료 (LLM 함수 호출 없이)
            logger.info(f"Received end_translation_mode message for session {session_pc_id}")
            if not is_translation_mode_active(session_pc_id):
                logger.warning(
                    f"Translation mode is not active for session {session_pc_id}, but received end message"
                )
            try:
                logger.info(f"Calling end_translation_mode_directly for session {session_pc_id}")
                result = await end_translation_mode(
                    session_id=session_pc_id,
                    stt=stt,
                    transport=transport,
                    rtvi=rtvi,
                )
                logger.info(
                    f"Translation mode ended via client message: {result.get('status')}, message: {result.get('message')}"
                )
            except Exception as e:
                logger.error(f"Failed to end translation mode: {e}", exc_info=True)
        elif msg_type == "app_state" and isinstance(msg_data, dict) and user_id:
            app_state = msg_data.get("state")  # "background" or "foreground"
            if app_state == "background":
                wake_filter.reset_to_idle(user_id)
                logger.info(f"App went to background: Set user {user_id} to IDLE state")
            elif app_state == "foreground":
                wake_filter.set_to_awake(user_id)
                logger.info(f"App came to foreground: Set user {user_id} to AWAKE state")

    @transport.event_handler("on_client_connected")
    async def on_client_connected(transport, client):
        logger.info(f"Client connected")
        if user_id:
            wake_filter.set_to_awake(user_id)
            logger.info(f"Client connected: Set user {user_id} to AWAKE state")

        # Video LLM을 위한 카메라 캡처 시작
        try:
            await transport.capture_participant_video(video_source="camera")
            logger.info("📸 Camera capture started for video LLM")
        except Exception as e:
            logger.warning(f"Failed to start camera capture: {e}")

    @transport.event_handler("on_client_disconnected")
    async def on_client_disconnected(transport, client):
        logger.info(f"Client disconnected")
        await task.cancel()

    runner = PipelineRunner(handle_sigint=runner_args.handle_sigint)

    if session_pc_id:
        register_session_context(session_pc_id, task)
    else:
        logger.warning(
            "Session pc_id could not be determined. Text/STT controls will target the latest session."
        )

    try:
        await runner.run(task)
    finally:
        if session_pc_id:
            unregister_session_context(session_pc_id)
            session_store.remove(session_pc_id)
        if gportal_client:
            await gportal_client.close()


async def bot(runner_args: RunnerArguments):
    """Main bot entry point for the bot starter."""

    # NOTE: VAD/turn analyzer는 더 이상 TransportParams에 두지 않고
    # run_bot() 안에서 LLMUserAggregatorParams 로 전달됩니다 (upstream v1.0+).
    transport_params = {
        "daily": lambda: DailyParams(
            audio_in_enabled=True,
            audio_out_enabled=True,
            video_in_enabled=True,  # Video LLM을 위한 비디오 입력 활성화
        ),
        "webrtc": lambda: TransportParams(
            audio_in_enabled=True,
            audio_out_enabled=True,
            video_in_enabled=True,  # Video LLM을 위한 비디오 입력 활성화
            video_in_width=3840,  # 최대 해상도로 캡처 (서버에서 필요시 다운스케일링)
            video_in_height=2160,  # 최대 해상도로 캡처 (서버에서 필요시 다운스케일링)
        ),
    }

    transport = await create_transport(runner_args, transport_params)

    await run_bot(transport, runner_args)


# Export app for uvicorn workers
# This will be created when main() is called, or can be created directly by workers
try:
    app = create_app()
    # Setup OAuth authentication
    try:
        setup_auth_for_app(app)
    except Exception as e:
        logger.warning(f"Failed to setup OAuth authentication: {e}")
        logger.warning("Continuing without OAuth features...")
except Exception:
    # If create_app() fails (e.g., env vars not set), app will be None
    # This is OK for the initial import - main() will set it up
    app = None

if __name__ == "__main__":
    main()
