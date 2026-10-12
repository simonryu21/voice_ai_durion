"""Meeting summarizer for generating summaries of meeting conversations."""

import json
import os
from datetime import datetime
from typing import Any, Dict, List, Literal, Optional

from loguru import logger
from openai import AsyncOpenAI
from pydantic import BaseModel, ValidationError


class ConversationSummary(BaseModel):
    mode: Literal["expert meeting", "meeting", "conversation"] = "meeting"
    summary: str
    title: str = "대화 요약"
    keywords: List[str] = []


class MeetingSummarizer:
    """Class for generating summaries of meeting conversations using OpenAI API."""

    # Expert meeting mode threshold
    EXPERT_MEETING_MODE_THRESHOLD: int = 12

    def __init__(
        self,
        model: str = "gpt-4o-mini",
        max_tokens: Optional[int] = None,
        api_key: Optional[str] = None,
    ):
        """Initialize the meeting summarizer.

        Args:
            model: OpenAI model to use for summarization (default: gpt-4o-mini)
            max_tokens: Maximum tokens for the summary (default: None, uses model's maximum limit)
            api_key: OpenAI API key (default: from OPENAI_API_KEY environment variable)
        """
        self.model = model
        self.max_tokens = max_tokens  # None means use model's maximum output token limit
        self.api_key = api_key or os.getenv("OPENAI_API_KEY")

    async def generate_summary(
        self,
        messages: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        """Generate a summary of meeting messages using OpenAI API.

        Args:
            messages: List of meeting messages with 'role', 'content', 'timestamp', and optionally 'speaker_id'

        Returns:
            Dictionary with 'summary', 'key_points', 'participants', 'message_count', and 'status'
        """
        if not messages:
            return {
                "status": "error",
                "message": "회의 메시지가 없습니다.",
            }

        if not self.api_key:
            return {
                "status": "error",
                "message": "OPENAI_API_KEY 환경 변수가 설정되지 않았습니다.",
            }

        try:
            # 메시지 포맷팅
            formatted_messages = []
            for msg in messages:
                role = msg.get("role", "user")
                content = msg.get("content", "")
                speaker_id = msg.get("speaker_id")
                formatted_content = f"[화자 {speaker_id}] {content}" if speaker_id else content
                formatted_messages.append({"role": role, "content": formatted_content})

            meeting_text = "\n".join(
                [f"{msg['role']}: {msg['content']}" for msg in formatted_messages]
            )
            message_count = len(messages)

            # 전체 텍스트 길이 계산 (한글 기준 대략적인 문자 수)
            total_text_length = len(meeting_text)

            # 메시지 길이에 따른 conversation 모드 요약 길이 결정
            if total_text_length <= 500:
                conversation_summary_guidance = "1-2문단으로 작성"
            elif total_text_length <= 2000:
                conversation_summary_guidance = "2-4문단으로 작성"
            else:
                conversation_summary_guidance = "4-6문단 이상으로 주요 내용 빼먹지 않게 작성"

            system_prompt = f"""당신은 회의/대화 요약 전문가입니다. 한 번의 응답으로 요약과 메타데이터를 JSON으로 반환하세요. 응답은 ConversationSummary 스키마를 따라야 합니다.

모드 판정 순서:
1) 이 대화가 일반 대화인지 회의인지 먼저 판단합니다.
   - 일반 대화이면 mode="conversation"
2) 회의라고 판단되면 메시지 개수를 보고
   - {self.EXPERT_MEETING_MODE_THRESHOLD}개 이상이면 mode="expert meeting"
   - 미만이면 mode="meeting"

모드별 작성 지침:

[conversation 모드]
- summary: {conversation_summary_guidance} (대화 내용의 길이에 따라 가변적으로 작성)
- 내용: 주요 화제, 배경, 결론을 포함한 완결된 대화 요약
- 형식: 자연스러운 문단 형태로 작성
- 목적: summary 필드 하나만으로 바로 보여줄 수 있는 완결된 요약
- 중요: 대화가 길수록 더 상세하고 포괄적으로 작성하세요. 짧은 대화는 간결하게, 긴 대화는 주요 내용을 빠짐없이 포함하여 작성하세요.

[meeting 모드]
- summary: 2-3문단으로 작성
- 필수 포함 항목:
  * 회의 제목/주제
  * 개요 (회의 목적과 핵심 내용)
  * 주요 논의 사항 (핵심 토론 내용)
  * 결정 사항 (확정된 내용)
  * 액션 아이템 (있을 경우)
  * 리스크/이슈 (있을 경우)
- 형식: 자연스러운 문단 형태로 구성
- 목적: 추가 후처리 없이 바로 사용 가능한 완결형 회의록

[expert meeting 모드]
- summary: 전문가가 작성한 상세 회의록 수준으로 5-7문단 작성
- 반드시 아래 구조와 형식을 정확히 따르세요:

=== 전문 회의록 작성 템플릿 ===

**1. 제목**
- 명확하고 구체적인 회의 제목 작성
- 예: "2024년 Q1 제품 출시 전략 회의", "신규 기능 개발 우선순위 논의"

**2. 개요/요약**
- 1-2문단으로 작성
- 회의 목적, 배경, 핵심 흐름을 명확히 기술
- 언제, 왜, 누가 참석했는지 간략히 언급

**3. 참석자 및 역할**
- 주요 참석자와 각자의 역할/책임 명시
- 발언자 식별이 가능한 경우 발언 내용과 연결
- 알 수 없는 경우 생략 가능

**4. 주요 논의 사항**
- 불릿 포인트로 상세 기록
- 각 논의 항목별로:
  * 논의된 내용의 핵심 사실
  * 언급된 구체적 수치, 데이터, 통계
  * 제시된 의견과 근거
  * 논의 과정에서 나온 중요한 질문과 답변
- 시간 순서나 논리적 흐름을 고려하여 구성

**5. 결정 사항**
- 불릿 포인트로 명확히 기록
- 확정된 결정 내용을 구체적으로 기술
- 결정 근거나 배경이 있으면 간략히 포함
- 결정 시점이나 조건이 있으면 명시

**6. 액션 아이템**
- 불릿 포인트로 상세 기록
- 각 액션 아이템마다 반드시 포함:
  * 구체적인 작업 내용
  * 담당자 (알 수 있는 경우)
  * 기한/마감일 (언급된 경우)
  * 예상 결과물 또는 목표
- 우선순위가 있으면 표시

**7. 리스크 및 이슈**
- 식별된 리스크나 잠재적 문제점 기록
- 각 리스크별로:
  * 리스크 내용과 영향도
  * 대응 계획 또는 완화 방안
  * 모니터링 방법 (언급된 경우)
- 리스크가 없으면 "식별된 리스크 없음" 또는 생략

**8. 추가 논의 및 메모**
- 위 항목에 포함되지 않은 중요 정보
- 향후 논의 예정 사항
- 참고 자료나 관련 문서 언급
- 특이 사항이나 주의할 점

**9. 다음 단계**
- 후속 조치나 다음 회의 계획
- 확인이 필요한 사항
- 공유가 필요한 정보

=== 작성 원칙 ===
- 객관적이고 사실 중심으로 작성
- 추측이나 해석은 최소화하고 실제 언급된 내용 중심
- 전문적이지만 이해하기 쉬운 문체 사용
- 중요한 수치, 날짜, 이름은 정확히 기록
- 불명확한 내용은 "추가 확인 필요" 등으로 표시
- 각 섹션은 명확히 구분되도록 작성

필수 필드:
- mode, summary, title(최대 50자), keywords(5-10개 한국어)

반드시 JSON만 반환하고, 추가 설명은 넣지 마세요."""

            user_prompt = f"""다음은 대화 내용입니다 (총 {message_count}개 메시지):

{meeting_text}

위 지침에 따라 JSON으로만 응답하세요."""

            client = AsyncOpenAI(api_key=self.api_key)

            schema = ConversationSummary.model_json_schema()
            # OpenAI JSON schema requires explicit required/allOf fields; enforce no extra props
            if isinstance(schema, dict):
                props = (
                    schema.get("properties", {})
                    if isinstance(schema.get("properties"), dict)
                    else {}
                )
                schema["additionalProperties"] = False
                schema["required"] = list(props.keys())

            create_params = {
                "model": self.model,
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt},
                ],
                "temperature": 0.7,
            }

            if self.max_tokens is not None:
                create_params["max_tokens"] = self.max_tokens

            # Prefer structured JSON schema response if supported
            if "gpt-4o" in self.model or "gpt-4-turbo" in self.model:
                try:
                    create_params["response_format"] = {
                        "type": "json_schema",
                        "json_schema": {
                            "name": "ConversationSummary",
                            "schema": schema,
                            "strict": True,
                        },
                    }
                except Exception:
                    # fallback to plain JSON mode
                    create_params["response_format"] = {"type": "json_object"}

            response = await client.chat.completions.create(**create_params)
            summary_text = response.choices[0].message.content

            parsed = None
            try:
                parsed = json.loads(summary_text)
            except Exception:
                if summary_text.startswith("```"):
                    json_start = summary_text.find("{")
                    json_end = summary_text.rfind("}")
                    if json_start != -1 and json_end != -1:
                        try:
                            parsed = json.loads(summary_text[json_start : json_end + 1])
                        except Exception:
                            parsed = None

            if not parsed or not isinstance(parsed, dict):
                return {
                    "status": "error",
                    "message": "LLM 응답을 JSON으로 파싱하지 못했습니다.",
                }

            try:
                coerced_model = ConversationSummary.model_validate(parsed)
            except ValidationError as e:
                logger.warning(f"ConversationSummary validation failed: {e}")
                return {
                    "status": "error",
                    "message": "LLM 응답이 스키마와 맞지 않습니다.",
                }

            participants = set()
            for msg in messages:
                speaker_id = msg.get("speaker_id")
                if speaker_id:
                    participants.add(f"화자 {speaker_id}")

            first_timestamp = messages[0].get("timestamp") if messages else None
            meeting_datetime = first_timestamp or datetime.now().isoformat()

            return {
                "status": "success",
                "summary": coerced_model.summary,
                "participants": list(participants) if participants else ["알 수 없음"],
                "message_count": len(messages),
                "model": self.model,
                "meeting_datetime": meeting_datetime,
                "mode": coerced_model.mode,
                "title": coerced_model.title,
                "keywords": coerced_model.keywords,
            }

        except Exception as e:
            logger.error(f"Error generating meeting summary: {e}")
            return {
                "status": "error",
                "message": f"회의 요약 생성 중 오류가 발생했습니다: {str(e)}",
            }
