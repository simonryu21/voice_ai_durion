"""Meeting mode tool for starting and ending meeting recording sessions."""

import asyncio
import json
import sys
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional

from loguru import logger

# Status and message keys for tool results and notifications
STATUS_ALREADY_ACTIVE = "already_active"
STATUS_MEETING_STARTED = "meeting_mode_started"
STATUS_MEETING_ENDED = "meeting_mode_ended"
STATUS_ERROR = "error"
KEY_MESSAGE = "message"
KEY_SUMMARY = "summary"
KEY_KEYWORDS = "keywords"
KEY_TITLE = "title"
MEETING_HEADER_DATE_FMT = "%Y-%m-%d"
from pipecat.adapters.schemas.function_schema import FunctionSchema
from pipecat.adapters.schemas.tools_schema import ToolsSchema
from pipecat.frames.frames import FunctionCallResultProperties, LLMMessagesUpdateFrame
from pipecat.processors.aggregators.llm_response_universal import LLMContextAggregatorPair
from pipecat.processors.frameworks.rtvi import RTVIServerMessageFrame
from pipecat.services.llm_service import FunctionCallParams
from pipecat.services.mem0.memory import Mem0MemoryService

# Allow importing from mobile package (processors, starting_prompt)
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from processors.meeting_summarizer import MeetingSummarizer
from starting_prompt import get_meeting_mode_messages, get_starting_messages

start_meeting_mode_function = FunctionSchema(
    name="start_meeting_mode",
    description="회의 모드를 시작합니다. 사용자가 회의를 시작하거나 회의 관련 키워드를 언급할 때 자동으로 호출하세요. "
    "회의 모드가 시작되면 모든 대화가 기록되며, AI는 응답하지 않고 조용히 기록만 합니다.",
    properties={},
    required=[],
)

end_meeting_mode_function = FunctionSchema(
    name="end_meeting_mode",
    description="회의 모드를 종료합니다. 사용자가 회의 종료를 명시하거나, 회의가 끝난 것으로 판단될 때 호출하세요. "
    "회의 종료 후 자동으로 회의 요약이 생성됩니다. "
    "반환값의 message에 요약이 포함되어 있으면, 그 내용을 그대로 말하세요 (회의 모드 종료 인사 후 요약만 말함). "
    "'회의 요약해줘라고 말씀해 주세요' 같은 안내는 하지 말고, 요약이 있으면 바로 말하고 없으면 '회의 모드가 종료되었습니다.'만 말하세요.",
    properties={},
    required=[],
)


# 세션별 회의 모드 상태 저장
_meeting_mode_states: Dict[str, bool] = {}
_meeting_mode_start_times: Dict[str, datetime] = {}
_meeting_mode_start_message_counts: Dict[str, int] = {}


def is_meeting_mode_active(session_id: Optional[str]) -> bool:
    """회의 모드가 활성화되어 있는지 확인합니다."""
    if not session_id:
        return False
    return _meeting_mode_states.get(session_id, False)


def get_meeting_mode_start_time(session_id: Optional[str]) -> Optional[datetime]:
    """회의 모드 시작 시점을 반환합니다.

    Args:
        session_id: 세션 ID

    Returns:
        회의 모드 시작 시점 (datetime) 또는 None
    """
    if not session_id:
        return None
    return _meeting_mode_start_times.get(session_id)


def get_meeting_mode_start_message_count(session_id: Optional[str]) -> Optional[int]:
    """회의 모드 시작 시점의 메시지 개수를 반환합니다.

    Args:
        session_id: 세션 ID

    Returns:
        회의 모드 시작 시점의 메시지 개수 또는 None
    """
    if not session_id:
        return None
    return _meeting_mode_start_message_counts.get(session_id)


def set_meeting_mode_start_message_count(session_id: Optional[str], message_count: int):
    """회의 모드 시작 시점의 메시지 개수를 설정합니다.

    Args:
        session_id: 세션 ID
        message_count: 메시지 개수
    """
    if session_id:
        _meeting_mode_start_message_counts[session_id] = message_count


def set_meeting_mode(session_id: Optional[str], active: bool):
    """회의 모드 상태를 설정합니다."""
    if session_id:
        _meeting_mode_states[session_id] = active
        if active:
            # 회의 모드 시작 시점 기록
            _meeting_mode_start_times[session_id] = datetime.now()
            logger.info(
                f"Meeting mode activated for session: {session_id} at {_meeting_mode_start_times[session_id]}"
            )
        else:
            # 회의 모드 종료 시 시작 시점만 제거. start_message_count는 end_meeting_mode 툴이
            # 요약에 사용한 뒤에 제거함 (클라이언트에서 먼저 set_meeting_mode(False) 호출해도
            # 툴이 나중에 get_meeting_mode_start_message_count를 쓸 수 있도록).
            _meeting_mode_start_times.pop(session_id, None)
            logger.info(f"Meeting mode deactivated for session: {session_id}")
    else:
        logger.warning("Cannot set meeting mode: session_id is None")


def create_start_meeting_mode(
    context_aggregator: LLMContextAggregatorPair,
    memory_service: Optional[Mem0MemoryService] = None,
    session_pc_id: Optional[str] = None,
    meeting_detector=None,
    rtvi=None,
):
    """회의 모드 시작 함수를 생성합니다.

    Args:
        context_aggregator: LLM context aggregator pair
        memory_service: Optional Mem0 memory service
        session_pc_id: Session ID for tracking
        meeting_detector: Optional MeetingModeDetector instance for message tracking
        rtvi: Optional RTVIProcessor instance for sending notifications
    """

    async def start_meeting_mode(params: FunctionCallParams):
        """회의 모드를 시작합니다."""
        try:
            if is_meeting_mode_active(session_pc_id):
                logger.debug("Meeting mode is already active, skipping start")
                await params.result_callback(
                    {
                        "status": STATUS_ALREADY_ACTIVE,
                        KEY_MESSAGE: "회의 모드가 이미 활성화되어 있습니다.",
                    }
                )
                return

            # 회의 모드 상태 저장
            set_meeting_mode(session_pc_id, True)

            # 회의 Agent 전용 도구만 컨텍스트에 설정 (main에는 회의 도구 없음)
            context_aggregator.user().context.set_tools(
                ToolsSchema(standard_tools=[end_meeting_mode_function])
            )

            # 회의 모드 시작 시점의 메시지 개수 기록
            try:
                # context_aggregator는 LLMContextAggregatorPair이므로 user() 또는 assistant()를 통해 aggregator에 접근
                # user aggregator의 context를 통해 메시지 가져오기
                user_aggregator = context_aggregator.user()
                context_messages = user_aggregator.context.get_messages()
                non_system_messages = [
                    msg for msg in context_messages if msg.get("role") != "system"
                ]
                message_count = len(non_system_messages)
                set_meeting_mode_start_message_count(session_pc_id, message_count)
                logger.debug(f"Recorded meeting mode start message count: {message_count}")
            except Exception as e:
                logger.warning(f"Failed to record meeting mode start message count: {e}")

            # MeetingModeDetector에 회의 모드 활성화 알림
            if meeting_detector:
                meeting_detector.set_meeting_mode_active(True)
                logger.debug("MeetingModeDetector: Started tracking meeting messages")

            # 프롬프트 변경 (회의 모드 전용 프롬프트로 업데이트)
            meeting_messages = get_meeting_mode_messages()
            await context_aggregator.assistant().push_frame(
                LLMMessagesUpdateFrame(
                    messages=meeting_messages,
                    run_llm=False,
                )
            )
            logger.info("Meeting mode started")

            # 사용자에게 알림 메시지 전송
            if rtvi:
                try:
                    notification_message = RTVIServerMessageFrame(
                        data={
                            "type": "meeting_mode_started",
                            "message": "회의 모드가 시작되었습니다. 모든 대화가 기록됩니다.",
                            "title": "회의 모드 시작",
                        }
                    )
                    await rtvi.push_transport_message(notification_message)
                    logger.info(
                        "📤 Sent meeting mode start notification to client via RTVIServerMessageFrame"
                    )
                except Exception as e:
                    logger.error(f"❌ Failed to send meeting mode start notification: {e}")
            else:
                logger.warning(
                    "⚠️ RTVI instance is None, cannot send meeting mode start notification"
                )

            result = {
                "status": STATUS_MEETING_STARTED,
                KEY_MESSAGE: "회의 모드가 시작되었습니다. 모든 대화가 기록됩니다.",
            }
            await params.result_callback(result)

        except Exception as e:
            logger.error(f"Error starting meeting mode: {e}")
            await params.result_callback(
                {
                    "status": STATUS_ERROR,
                    KEY_MESSAGE: f"회의 모드 시작 중 오류가 발생했습니다: {str(e)}",
                }
            )

    return start_meeting_mode


def create_end_meeting_mode(
    context_aggregator: LLMContextAggregatorPair,
    memory_service: Optional[Mem0MemoryService] = None,
    session_pc_id: Optional[str] = None,
    meeting_detector=None,
    rtvi=None,
):
    """회의 모드 종료 함수를 생성합니다.

    Args:
        context_aggregator: LLM context aggregator pair
        memory_service: Optional Mem0 memory service
        session_pc_id: Session ID for tracking
        meeting_detector: Optional MeetingModeDetector instance for retrieving tracked messages
        rtvi: Optional RTVIProcessor instance for sending notifications
    """

    async def end_meeting_mode(params: FunctionCallParams):
        """회의 모드를 종료하고 요약을 생성합니다."""
        try:
            # 회의 모드가 이미 비활성화되었을 수 있으므로 (LLM 호출을 위해)
            # 활성화 상태 확인을 건너뛰고 바로 처리
            # MeetingModeDetector에서 저장된 회의 메시지 가져오기 (상태 해제 전에 수행)
            meeting_messages = []
            if meeting_detector:
                meeting_messages = meeting_detector.get_meeting_messages()
                meeting_detector.set_meeting_mode_active(False)
                logger.info(f"Retrieved {len(meeting_messages)} messages from meeting mode")
            else:
                # MeetingModeDetector가 없는 경우 (버튼으로 제어하는 경우)
                # context에서 회의 모드 시작 시점 이후의 메시지 가져오기 (상태 해제 전에 수행)
                try:
                    start_message_count = get_meeting_mode_start_message_count(session_pc_id)
                    if start_message_count is not None:
                        user_aggregator = context_aggregator.user()
                        all_messages = user_aggregator.context.get_messages()
                        meeting_messages = all_messages[start_message_count:]
                        logger.info(
                            f"Retrieved {len(meeting_messages)} messages from context "
                            f"(start_message_count={start_message_count}, total={len(all_messages)})"
                        )
                    else:
                        logger.warning(
                            f"No start_message_count for session {session_pc_id}; "
                            f"cannot retrieve meeting messages for summary"
                        )
                except Exception as e:
                    logger.warning(f"Failed to get meeting messages from context: {e}")

            # 회의 모드 상태 해제 (메시지 가져온 후에 수행)
            set_meeting_mode(session_pc_id, False)
            # 요약에 사용했으므로 세션의 start_message_count 제거 (다음 회의 모드용)
            _meeting_mode_start_message_counts.pop(session_pc_id, None)

            logger.info(
                "Meeting mode ended. Generating summary first so tool returns within timeout..."
            )

            # 요약만 먼저 생성 (Mem0 저장은 나중에 백그라운드로). 이렇게 해야 result_callback이
            # 10초 타임아웃 전에 호출되어 요약이 TTS로 전달됨.
            summary_message = ""
            summary_result = None
            if meeting_messages:
                logger.info(f"Found {len(meeting_messages)} meeting messages for summary")
                try:
                    summarizer = MeetingSummarizer()
                    summary_result = await summarizer.generate_summary(meeting_messages)
                    if summary_result.get("status") == "success":
                        logger.info("Meeting summary generated successfully")
                    else:
                        summary_message = summary_result.get(
                            "message", "회의 요약 생성에 실패했습니다."
                        )
                        logger.warning(f"Failed to generate summary: {summary_message}")
                except Exception as e:
                    logger.error(f"Error generating meeting summary: {e}")
                    summary_message = f"회의 내용 {len(meeting_messages)}개 메시지가 기록되었습니다. 요약 생성 중 오류가 발생했습니다: {str(e)}"
            elif memory_service:
                summary_message = ""
            else:
                summary_message = "회의 모드가 종료되었습니다. (기록된 메시지 없음)"

            # 알림 먼저 전송(팝업 빠르게 표시), Mem0 저장은 그 다음 백그라운드에서 수행
            async def _store_and_notify_in_background():
                try:
                    # Send notification first so the client popup appears quickly
                    if rtvi:
                        notification_data = _build_meeting_end_notification(
                            meeting_messages, summary_result
                        )
                        await rtvi.push_transport_message(
                            RTVIServerMessageFrame(data=notification_data)
                        )
                        logger.debug("Sent meeting mode end notification to client")
                    # 2) 그 다음 Mem0 저장 (알림과 무관하게 백그라운드로)
                    if meeting_messages and memory_service and memory_service.memory_client:
                        await _store_meeting_messages_to_memory(
                            memory_service,
                            meeting_messages,
                            session_pc_id,
                        )
                        logger.info(
                            f"Stored {len(meeting_messages)} meeting messages to memory as batch"
                        )
                    if summary_result and summary_result.get("status") == "success":
                        if memory_service and memory_service.memory_client:
                            try:
                                await _store_meeting_summary_to_memory(
                                    memory_service,
                                    summary_result,
                                    session_pc_id,
                                )
                                logger.info("Meeting summary stored to memory with metadata")
                            except Exception as e:
                                logger.error(f"Error storing meeting summary to memory: {e}")
                except Exception as e:
                    logger.warning(f"Meeting mode background store/notify failed: {e}")

            asyncio.create_task(_store_and_notify_in_background())

            result = {
                "status": STATUS_MEETING_ENDED,
                KEY_MESSAGE: "회의 모드가 종료되었습니다.",
                "summary_result": summary_result if summary_result else None,
                "message_count": len(meeting_messages) if meeting_messages else 0,
            }
            if summary_result and summary_result.get("status") == "success":
                result[KEY_SUMMARY] = summary_result.get(KEY_SUMMARY, "")
                result[KEY_TITLE] = summary_result.get(KEY_TITLE, "회의 요약")
                result[KEY_KEYWORDS] = summary_result.get(KEY_KEYWORDS, [])
                result[KEY_MESSAGE] = (
                    f"회의 모드가 종료되었습니다.\n\n{result[KEY_TITLE]}\n\n{result[KEY_SUMMARY]}"
                )
            elif summary_message:
                result[KEY_MESSAGE] += f" {summary_message}"

            # 프롬프트 복원 (일반 모드로) - 함수 호출 결과 처리 전에 복원
            normal_messages = get_starting_messages()
            await context_aggregator.assistant().push_frame(
                LLMMessagesUpdateFrame(
                    messages=normal_messages,
                    run_llm=False,  # result_callback에서 run_llm=True로 설정할 예정
                )
            )
            # main 컨텍스트에는 회의 도구 없음 (회의 Agent OFF 시)
            context_aggregator.user().context.set_tools(ToolsSchema(standard_tools=[]))

            # 함수 호출 결과 처리 후 LLM이 응답을 생성하도록 설정
            properties = FunctionCallResultProperties(run_llm=True)
            await params.result_callback(result, properties=properties)

        except Exception as e:
            logger.error(f"Error ending meeting mode: {e}")
            await params.result_callback(
                {
                    "status": STATUS_ERROR,
                    KEY_MESSAGE: f"회의 모드 종료 중 오류가 발생했습니다: {str(e)}",
                }
            )

    return end_meeting_mode


def _build_meeting_end_notification(
    meeting_messages: List[Dict[str, Any]],
    summary_result: Optional[Dict[str, Any]],
) -> Dict[str, Any]:
    """Build notification payload for meeting mode ended (title, message, optional summary)."""
    data = {
        "type": "meeting_mode_ended",
        "title": "회의 모드 종료",
        KEY_MESSAGE: "회의 모드가 종료되었습니다.",
    }
    if summary_result and summary_result.get("status") == "success":
        data["summary"] = {
            "message_count": len(meeting_messages),
            KEY_KEYWORDS: summary_result.get(KEY_KEYWORDS, []),
            "summary_text": summary_result.get(KEY_SUMMARY, ""),
            KEY_TITLE: summary_result.get(KEY_TITLE, "회의 요약"),
        }
        data[KEY_MESSAGE] = (
            f"회의 모드가 종료되었습니다. {len(meeting_messages)}개의 메시지를 요약했습니다."
        )
    elif meeting_messages:
        data[KEY_MESSAGE] = (
            f"회의 모드가 종료되었습니다. {len(meeting_messages)}개의 메시지가 기록되었습니다."
        )
    return data


async def _store_meeting_messages_to_memory(
    memory_service: Mem0MemoryService,
    meeting_messages: List[Dict[str, Any]],
    session_pc_id: Optional[str] = None,
):
    """Store all meeting messages to Mem0 memory as a batch.

    Args:
        memory_service: Mem0MemoryService instance
        meeting_messages: List of meeting messages to store
        session_pc_id: Session ID for tracking
    """
    try:
        if not meeting_messages:
            logger.warning("No meeting messages to store")
            return

        # Filter out system messages and empty messages
        messages_to_store = [
            msg
            for msg in meeting_messages
            if msg.get("role") in ["user", "assistant"]
            and msg.get("content")
            and isinstance(msg.get("content"), str)
            and len(msg.get("content", "").strip()) > 0
        ]

        if not messages_to_store:
            logger.warning("No valid messages to store after filtering")
            return

        meeting_date = datetime.now().strftime(MEETING_HEADER_DATE_FMT)
        meeting_header = f"[{meeting_date} 회의 전체 데이터]"

        # Prepend header to the first message's content
        if messages_to_store:
            first_message = messages_to_store[0].copy()
            original_content = first_message.get("content", "")
            first_message["content"] = f"{meeting_header}\n\n{original_content}"
            messages_to_store[0] = first_message

        logger.info(f"Storing {len(messages_to_store)} meeting messages to memory as batch")

        # Prepare metadata
        metadata = {
            "platform": "pipecat",
            "type": "meeting_transcript",
            "message_count": len(messages_to_store),
        }

        # ID 파라미터 준비
        id_params = {}
        if memory_service.user_id:
            id_params["user_id"] = memory_service.user_id
        if memory_service.agent_id:
            id_params["agent_id"] = memory_service.agent_id
        if session_pc_id:
            id_params["run_id"] = session_pc_id
            metadata["conversation_id"] = session_pc_id
            metadata["session_id"] = session_pc_id

        # 최소 하나의 ID가 필요
        if not id_params:
            logger.warning("Cannot store meeting messages: no user_id, agent_id, or run_id")
            return

        # Mem0에 배치로 저장
        params = {
            "messages": messages_to_store,
            "infer": True,
            "metadata": metadata,
        }
        params.update(id_params)

        # mem0 add() is sync (httpx), run in thread to avoid blocking event loop
        result = await asyncio.to_thread(memory_service.memory_client.add, **params)
        logger.debug(f"Memory add() response: {result}")
        logger.info(
            f"Stored {len(messages_to_store)} meeting messages to memory as batch (session={session_pc_id})"
        )

    except Exception as e:
        logger.error(f"Error storing meeting messages to memory: {e}")
        raise


async def _store_meeting_summary_to_memory(
    memory_service: Mem0MemoryService,
    summary_result: Dict[str, Any],
    session_pc_id: Optional[str] = None,
):
    """Store meeting summary to Mem0 memory with metadata.

    Args:
        memory_service: Mem0MemoryService instance
        summary_result: Result from MeetingSummarizer.generate_summary()
        session_pc_id: Session ID for tracking
    """
    try:
        summary_text = summary_result.get("summary", "")
        participants = summary_result.get("participants", [])
        keywords = summary_result.get("keywords", [])
        meeting_datetime = summary_result.get("meeting_datetime")
        mode = summary_result.get("mode", "conversation")
        title = summary_result.get("title", "회의 요약")

        # mode가 "expert meeting" 또는 "meeting"인 경우에만 "회의" 키워드 추가
        if mode in ("expert meeting", "meeting") and "회의" not in keywords:
            keywords.insert(0, "회의")

        # 일시 설정
        if not meeting_datetime:
            meeting_datetime = datetime.now().isoformat()

        # 메모리에 저장할 메시지 생성
        memory_message = {
            "role": "assistant",
            "content": f"회의 요약:\n\n{summary_text}",
        }

        # Metadata 준비
        metadata = {
            "platform": "pipecat",
            "type": "meeting_summary",
            "title": title,
            "participants": json.dumps(participants, ensure_ascii=False) if participants else "[]",
            "meeting_datetime": meeting_datetime,
            "keywords": ",".join(keywords) if keywords else "회의",
            "message_count": summary_result.get("message_count", 0),
            "mode": summary_result.get("mode"),
        }

        # ID 파라미터 준비
        id_params = {}
        if memory_service.user_id:
            id_params["user_id"] = memory_service.user_id
        if memory_service.agent_id:
            id_params["agent_id"] = memory_service.agent_id
        if session_pc_id:
            id_params["run_id"] = session_pc_id
            metadata["conversation_id"] = session_pc_id
            metadata["session_id"] = session_pc_id

        # 최소 하나의 ID가 필요
        if not id_params:
            logger.warning("Cannot store meeting summary: no user_id, agent_id, or run_id")
            return

        # Mem0에 저장
        params = {
            "messages": [memory_message],
            "metadata": metadata,
        }
        params.update(id_params)

        # mem0 add() is sync (httpx), run in thread to avoid blocking event loop
        result = await asyncio.to_thread(memory_service.memory_client.add, **params)
        logger.debug(f"Memory add() response: {result}")
        logger.info(
            f"Stored meeting summary to memory: mode={metadata.get('mode')}, title={title}, keywords={keywords}"
        )

    except Exception as e:
        logger.error(f"Error storing meeting summary to memory: {e}")
        raise
