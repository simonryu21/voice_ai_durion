"""Location tool for getting current location information."""

from typing import Any, Dict, Optional

from loguru import logger
from pipecat.adapters.schemas.function_schema import FunctionSchema
from pipecat.runner.run import get_all_user_locations, get_user_location
from pipecat.services.llm_service import FunctionCallParams

# 위치 정보 함수 스키마 정의
location_function = FunctionSchema(
    name="get_current_location",
    description="현재 세션 또는 최근 사용자 위치 정보를 반환합니다. 위치 정보가 없으면 None을 반환합니다.",
    properties={},
    required=[],
)


def create_get_current_location(session_pc_id: Optional[str]):
    """세션별 위치 조회 함수를 생성합니다."""

    async def get_current_location(params: FunctionCallParams):
        """저장된 위치 정보를 반환합니다."""
        location_info: Optional[Dict[str, Any]] = None

        try:
            # 먼저 session_pc_id로 시도
            if session_pc_id:
                location_info = get_user_location(session_pc_id)
                if location_info:
                    logger.info(
                        f"Retrieved stored location for session {session_pc_id}: "
                        f"lat={location_info.get('latitude')}, "
                        f"lon={location_info.get('longitude')}"
                    )

            # session_pc_id로 찾지 못했으면 최근 저장된 위치 찾기
            if not location_info:
                all_locations = get_all_user_locations()
                if all_locations:
                    # timestamp 기준으로 정렬하여 가장 최근 위치 선택
                    sorted_locations = sorted(
                        all_locations.items(),
                        key=lambda x: x[1].get("timestamp", ""),
                        reverse=True,
                    )
                    if sorted_locations:
                        recent_pc_id, location_info = sorted_locations[0]
                        logger.info(
                            f"Retrieved most recent location from {recent_pc_id}: "
                            f"lat={location_info.get('latitude')}, "
                            f"lon={location_info.get('longitude')}"
                        )
        except Exception as e:
            logger.warning(f"Failed to get stored location: {e}")

        if not location_info:
            result = {
                "latitude": None,
                "longitude": None,
                "timestamp": None,
                "source": "none",
                "message": "저장된 위치 정보가 없습니다. 브라우저에서 위치 공유를 허용해주세요.",
            }
        else:
            result = {
                "latitude": location_info.get("latitude"),
                "longitude": location_info.get("longitude"),
                "timestamp": location_info.get("timestamp"),
                "source": "session" if session_pc_id else "global",
            }

        await params.result_callback(result)

    return get_current_location
