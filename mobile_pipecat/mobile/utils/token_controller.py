"""Token controller for managing LLM context token limits.

This module provides a singleton TokenController class for estimating token counts
and limiting context messages to stay within API token limits.
"""

from typing import Any, Callable, Optional

import httpx
from loguru import logger
from pipecat.frames.frames import (
    LLMContextFrame,
    LLMFullResponseEndFrame,
    LLMFullResponseStartFrame,
    LLMUpdateSettingsFrame,
)
from pipecat.processors.frame_processor import FrameDirection
from pipecat.services.llm_service import LLMService


class TokenController:
    """Singleton class for managing LLM context token limits.

    This class provides methods for estimating token counts and limiting
    context messages to stay within API token limits.
    """

    _instance: Optional["TokenController"] = None
    _initialized: bool = False

    FINAL_MAX_TOKENS: int = 30000  # OpenAI API 제한

    MEETING_END_KEYWORDS: list[str] = [
        "회의 종료",
        "회의 끝",
        "회의 모드 종료",
        "이상으로 회의를 마칩니다",
        "회의를 마무리합니다",
        "오늘 회의 여기까지",
        "회의 마무리",
        "회의를 끝내겠습니다",
        "회의를 종료합니다",
        "회의 요약해줘",
        "회의 내용 요약해줘",
    ]

    def __new__(cls):
        """싱글톤 패턴 구현."""
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    def __init__(self):
        """초기화 (싱글톤이므로 한 번만 실행됨)."""
        if not self._initialized:
            self._initialized = True

    def estimate_tokens(self, text: str) -> int:
        """텍스트의 토큰 수를 추정합니다.

        Args:
            text: 추정할 텍스트

        Returns:
            추정된 토큰 수
        """
        if not text:
            return 0

        text_str = str(text)
        korean_chars = sum(1 for c in text_str if "\uac00" <= c <= "\ud7a3")
        other_chars = len(text_str) - korean_chars

        estimated = int(korean_chars / 2.0 + other_chars / 3.5)
        estimated = int(estimated * 1.2)  # 20% 안전 마진
        return max(estimated, 1)

    def estimate_message_tokens(self, message: dict) -> int:
        """메시지의 토큰 수를 추정합니다.

        Args:
            message: 메시지 딕셔너리

        Returns:
            추정된 토큰 수
        """
        content = message.get("content", "")
        if isinstance(content, str):
            return self.estimate_tokens(content)
        elif isinstance(content, list):
            total = 0
            for item in content:
                if isinstance(item, dict):
                    if "text" in item:
                        total += self.estimate_tokens(item["text"])
                    elif "input_audio" in item or "image_url" in item:
                        total += 100  # 오디오/이미지 대략적 추정
            return total
        return 0

    def limit_context_messages(
        self,
        context_obj: Any,
        session_pc_id: str,
        is_meeting_mode_active_func: Callable[[str], bool],
        get_meeting_mode_start_message_count_func: Callable[[str], Optional[int]],
        set_meeting_mode_start_message_count_func: Callable[[str, int], None],
    ) -> None:
        """LLM context의 메시지 수와 토큰 수를 제한하여 토큰 제한을 방지합니다.

        1. 미팅 모드가 active인 경우: memory의 이전 내용 제거, 미팅 모드 시작 이후의 대화만 사용
        2. max 토큰수(default 30000) 초과 시: 오래된 메시지부터 제거 (system 메시지는 항상 유지)

        Args:
            context_obj: LLMContext 인스턴스
            session_pc_id: 세션 ID
            is_meeting_mode_active_func: 회의 모드 활성화 여부 확인 함수
            get_meeting_mode_start_message_count_func: 회의 모드 시작 메시지 개수 조회 함수
            set_meeting_mode_start_message_count_func: 회의 모드 시작 메시지 개수 설정 함수
        """
        try:
            messages = (
                context_obj.messages
                if hasattr(context_obj, "messages")
                else context_obj.get_messages()
            )

            if not messages:
                return

            if is_meeting_mode_active_func(session_pc_id):
                messages = [
                    msg
                    for msg in messages
                    if not (
                        isinstance(msg.get("content"), str)
                        and msg.get("content", "").startswith("Previous conversation context:")
                    )
                ]

                meeting_start_message_count = get_meeting_mode_start_message_count_func(
                    session_pc_id
                )
                if meeting_start_message_count is not None:
                    system_messages = [msg for msg in messages if msg.get("role") == "system"]
                    non_system_messages = [msg for msg in messages if msg.get("role") != "system"]

                    if len(non_system_messages) > meeting_start_message_count:
                        messages_after_meeting = (
                            len(non_system_messages) - meeting_start_message_count
                        )
                        non_system_messages = (
                            non_system_messages[-messages_after_meeting:]
                            if messages_after_meeting > 0
                            else []
                        )
                        messages = system_messages + non_system_messages
                        logger.debug(
                            f"Meeting mode active: Kept {len(non_system_messages)} messages after meeting start"
                        )
                else:
                    non_system_count = len([msg for msg in messages if msg.get("role") != "system"])
                    set_meeting_mode_start_message_count_func(session_pc_id, non_system_count)
                    logger.debug(
                        f"Meeting mode active: Recorded start message count {non_system_count}"
                    )

            system_messages = [msg for msg in messages if msg.get("role") == "system"]
            non_system_messages = [msg for msg in messages if msg.get("role") != "system"]

            system_tokens = sum(self.estimate_message_tokens(msg) for msg in system_messages)
            non_system_tokens = sum(
                self.estimate_message_tokens(msg) for msg in non_system_messages
            )
            total_tokens = system_tokens + non_system_tokens

            # Group messages: assistant messages with tool_calls and their following tool messages
            message_groups = []
            i = 0
            while i < len(non_system_messages):
                msg = non_system_messages[i]
                if msg.get("role") == "assistant" and msg.get("tool_calls"):
                    # This is an assistant message with tool_calls
                    # Collect all following tool messages that belong to this assistant
                    group = [i]
                    tool_call_ids = {
                        tc.get("id") for tc in msg.get("tool_calls", []) if tc.get("id")
                    }
                    i += 1
                    while i < len(non_system_messages):
                        next_msg = non_system_messages[i]
                        if next_msg.get("role") == "tool":
                            tool_call_id = next_msg.get("tool_call_id")
                            if tool_call_id and tool_call_id in tool_call_ids:
                                group.append(i)
                                i += 1
                            else:
                                break
                        else:
                            break
                    message_groups.append(group)
                else:
                    # Regular message (user, assistant without tool_calls, etc.)
                    message_groups.append([i])
                    i += 1

            logger.debug(
                f"Total tokens: {total_tokens}, Total messages: {len(system_messages) + len(non_system_messages)}"
            )
            if total_tokens > self.FINAL_MAX_TOKENS:
                available_tokens = self.FINAL_MAX_TOKENS - system_tokens

                if available_tokens <= 0:
                    logger.warning(
                        f"⚠️ System messages alone exceed token limit ({system_tokens} tokens). "
                        f"Keeping all system messages and removing all non-system messages."
                    )
                    kept_non_system = []
                else:
                    kept_indices = set()
                    current_tokens = 0

                    # Process message groups in reverse order (newest first)
                    for group in reversed(message_groups):
                        # Calculate total tokens for this group
                        group_tokens = sum(
                            self.estimate_message_tokens(non_system_messages[idx]) for idx in group
                        )

                        if current_tokens + group_tokens <= available_tokens:
                            # Can fit this entire group
                            kept_indices.update(group)
                            current_tokens += group_tokens
                        else:
                            # Can't fit this group, stop processing older messages
                            break

                    kept_non_system = [non_system_messages[i] for i in sorted(kept_indices)]

                messages = system_messages + kept_non_system
                removed_count = len(non_system_messages) - len(kept_non_system)
                total_tokens = system_tokens + sum(
                    self.estimate_message_tokens(msg) for msg in kept_non_system
                )

                logger.warning(
                    f"⚠️ Context token limit applied. "
                    f"Removed {removed_count} old non-system messages to stay under {self.FINAL_MAX_TOKENS} tokens. "
                    f"Kept {len(system_messages)} system messages and {len(kept_non_system)} non-system messages. "
                    f"Estimated tokens: ~{total_tokens}"
                )
            else:
                logger.debug(
                    f"Context token check: {len(messages)} messages ({len(system_messages)} system, {len(non_system_messages)} non-system), ~{total_tokens} tokens"
                )

            if hasattr(context_obj, "set_messages"):
                context_obj.set_messages(messages)
            else:
                logger.warning(
                    f"Context object {type(context_obj)} does not have set_messages method"
                )

        except Exception as e:
            logger.error(f"Error limiting context messages: {e}")

    def create_process_frame_with_limit(
        self,
        llm_service: Any,
        session_pc_id: str,
        is_meeting_mode_active_func: Callable[[str], bool],
        get_meeting_mode_start_message_count_func: Callable[[str], Optional[int]],
        set_meeting_mode_start_message_count_func: Callable[[str, int], None],
    ):
        """LLM 서비스의 process_frame을 오버라이드하기 위한 함수를 생성합니다.

        Args:
            llm_service: LLM 서비스 인스턴스
            session_pc_id: 세션 ID
            is_meeting_mode_active_func: 회의 모드 활성화 여부 확인 함수
            get_meeting_mode_start_message_count_func: 회의 모드 시작 메시지 개수 조회 함수
            set_meeting_mode_start_message_count_func: 회의 모드 시작 메시지 개수 설정 함수

        Returns:
            오버라이드된 process_frame 메서드
        """
        # token_controller 인스턴스를 클로저로 캡처
        token_controller = self

        async def process_frame_with_limit(self, frame, direction: FrameDirection):
            """meeting_llm의 process_frame을 오버라이드:

            - 회의 모드 OFF: context frame을 완전히 passthrough. meeting_llm은
              이 경우 supervisor_llm 뒤에 앉아 있는 비활성 LLM이므로 절대
              completion을 만들면 안 된다.
            - 회의 모드 ON: end_meeting_mode 키워드만 통과시키고 그 외엔
              context를 차단. 통과하는 경우엔 limit_context_messages 적용 후
              _process_context 호출.
            """
            # 부모 클래스(LLMService)의 process_frame을 호출 (기본 프레임 처리)
            await LLMService.process_frame(llm_service, frame, direction)

            context = None
            if isinstance(frame, LLMContextFrame):
                context = frame.context
            elif isinstance(frame, LLMUpdateSettingsFrame):
                await llm_service._update_settings(frame.settings)
                return
            else:
                await llm_service.push_frame(frame, direction)
                return

            if not context:
                return

            # 회의 모드 OFF: meeting_llm은 비활성 — context를 그대로 흘려보낸다.
            if not is_meeting_mode_active_func(session_pc_id):
                await llm_service.push_frame(frame, direction)
                return

            # 회의 모드 ON: end_meeting_mode 키워드만 통과.
            should_allow_llm_call = False
            messages_to_check = []
            if isinstance(frame, LLMContextFrame):
                messages_to_check = context.get_messages()

            for message in messages_to_check:
                if message.get("role") == "user":
                    content = message.get("content", "")
                    if isinstance(content, list):
                        text_content = " ".join(
                            item.get("text", "")
                            if isinstance(item, dict) and item.get("type") == "text"
                            else str(item)
                            for item in content
                        )
                        content = text_content
                    elif not isinstance(content, str):
                        content = str(content)

                    if any(keyword in content for keyword in token_controller.MEETING_END_KEYWORDS):
                        logger.info(
                            f"Meeting end keyword detected in {type(frame).__name__}: Allowing LLM call to process end_meeting_mode()"
                        )
                        should_allow_llm_call = True
                        break

            if not should_allow_llm_call:
                logger.debug("Meeting mode active: Blocking LLM call")
                await llm_service.push_frame(frame, direction)
                return

            token_controller.limit_context_messages(
                context,
                session_pc_id,
                is_meeting_mode_active_func,
                get_meeting_mode_start_message_count_func,
                set_meeting_mode_start_message_count_func,
            )
            try:
                await llm_service.push_frame(LLMFullResponseStartFrame())
                await llm_service.start_processing_metrics()
                await llm_service._process_context(context)
            except httpx.TimeoutException:
                await llm_service._call_event_handler("on_completion_timeout")
            finally:
                await llm_service.stop_processing_metrics()
                await llm_service.push_frame(LLMFullResponseEndFrame())

        return process_frame_with_limit.__get__(llm_service, type(llm_service))


def get_token_controller() -> TokenController:
    """TokenController 싱글톤 인스턴스를 반환합니다.

    Returns:
        TokenController 싱글톤 인스턴스
    """
    return TokenController()
