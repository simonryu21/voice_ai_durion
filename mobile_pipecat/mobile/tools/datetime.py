"""DateTime tool for getting current date and time."""

from datetime import datetime

from pipecat.adapters.schemas.function_schema import FunctionSchema
from pipecat.services.llm_service import FunctionCallParams

# 날짜/시간 함수 스키마 정의
datetime_function = FunctionSchema(
    name="get_current_datetime",
    description="현재 날짜와 시간 정보를 가져옵니다. 날짜나 시간 관련 질문에 답변할 때 이 함수를 사용하세요.",
    properties={},
    required=[],
)


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
