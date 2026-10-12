"""Nearby places tool for searching nearby places using Naver Local API."""

import os
import re
from typing import Any, Dict, List, Optional

import aiohttp
from loguru import logger
from pipecat.adapters.schemas.function_schema import FunctionSchema
from pipecat.runner.run import get_user_location
from pipecat.services.llm_service import FunctionCallParams

from .address import _fetch_address_from_coordinates

# 주변 장소 조회 함수 스키마 정의
nearby_places_function = FunctionSchema(
    name="get_nearby_places",
    description="네이버 로컬 검색 API를 사용해 현재 위치 주변의 맛집/장소 정보를 조회합니다.",
    properties={
        "query": {
            "type": "string",
            "description": "검색할 키워드 (예: 맛집, 카페, 편의점). 기본값은 '맛집'입니다.",
        },
        "limit": {
            "type": "integer",
            "description": "결과 개수 (1~5 사이, 기본값 5).",
        },
        "sort": {
            "type": "string",
            "description": "정렬 방식 ('sim', 'random', 'comment' 중 선택, 기본값 random). sim은 유사도순, random은 랜덤, comment는 리뷰 많은 순입니다.",
        },
        "latitude": {
            "type": "number",
            "description": "사용자 지정 위도 (선택 사항). 제공되면 저장된 위치보다 우선합니다.",
        },
        "longitude": {
            "type": "number",
            "description": "사용자 지정 경도 (선택 사항). 제공되면 저장된 위치보다 우선합니다.",
        },
    },
    required=[],
)


def create_get_nearby_places(session_pc_id: Optional[str]):
    """세션별 주변 장소(맛집 등) 조회 함수를 생성합니다."""

    async def get_nearby_places(params: FunctionCallParams):
        """현재 위치 정보를 기반으로 네이버 로컬 API를 사용해 주변 장소를 조회합니다."""

        query = params.arguments.get("query", "맛집")
        requested_limit = params.arguments.get("limit", 5)
        sort = params.arguments.get("sort", "random")
        latitude = params.arguments.get("latitude")
        longitude = params.arguments.get("longitude")

        if not isinstance(query, str):
            query = str(query)
        query = query.strip() or "맛집"

        # 네이버 로컬 검색 API는 display 최대값이 5이므로 범위를 제한
        try:
            limit = int(requested_limit)
        except (TypeError, ValueError):
            limit = 5
        limit = max(1, min(limit, 5))

        # 네이버 로컬 검색 API의 sort 옵션: "sim" (유사도순, 기본값), "random" (랜덤), "comment" (리뷰 많은 순)
        if sort not in {"sim", "random", "comment"}:
            sort = "random"

        try:
            latitude = float(latitude) if latitude is not None else None
        except (TypeError, ValueError):
            latitude = None

        try:
            longitude = float(longitude) if longitude is not None else None
        except (TypeError, ValueError):
            longitude = None

        client_id = os.getenv("NAVER_CLIENT_ID")
        client_secret = os.getenv("NAVER_CLIENT_SECRET")

        try:
            if (latitude is None or longitude is None) and session_pc_id:
                stored_location = get_user_location(session_pc_id)
                if stored_location:
                    latitude = stored_location.get("latitude")
                    longitude = stored_location.get("longitude")
                    logger.info(f"Using stored location: lat={latitude}, lon={longitude}")
        except Exception as e:
            logger.warning(f"Failed to get stored location for nearby places: {e}")

        if latitude is None or longitude is None:
            await params.result_callback(
                {
                    "error": "위치 정보를 찾을 수 없습니다.",
                    "hint": "브라우저에서 위치 공유를 허용하거나 latitude/longitude 파라미터를 직접 제공해 주세요.",
                }
            )
            return

        # 위경도를 주소로 변환하여 query에 추가
        # get_address_from_coordinates() 함수와 동일한 로직 사용
        result_data = await _fetch_address_from_coordinates(latitude, longitude)
        if result_data:
            region = result_data.get("region", {})
            land = result_data.get("land", {})
            road = result_data.get("road", {})

            # 도로명 주소가 있으면 우선 사용, 없으면 지번 주소 사용
            if road and road.get("name"):
                # 도로명 주소
                region_1 = region.get("area1", {}).get("name", "")  # 시/도
                region_2 = region.get("area2", {}).get("name", "")  # 시/군/구
                region_3 = region.get("area3", {}).get("name", "")  # 동/읍/면
                road_name = road.get("name", "")  # 도로명
                building_number = road.get("number1", "")  # 건물번호
                full_address = (
                    f"{region_1} {region_2} {region_3} {road_name} {building_number}".strip()
                )
            elif land and land.get("number1"):
                # 지번 주소
                region_1 = region.get("area1", {}).get("name", "")
                region_2 = region.get("area2", {}).get("name", "")
                region_3 = region.get("area3", {}).get("name", "")
                region_4 = region.get("area4", {}).get("name", "")
                lot_number = f"{land.get('number1', '')}-{land.get('number2', '')}".strip("-")
                full_address = f"{region_1} {region_2} {region_3} {region_4} {lot_number}".strip()
            else:
                full_address = None

            if full_address:
                query = f"{full_address} {query}".strip()
                logger.info(f"Appended address to query: {query}")

        base_url = "https://openapi.naver.com/v1/search/local.json"
        headers = {
            "X-Naver-Client-Id": client_id,
            "X-Naver-Client-Secret": client_secret,
        }

        # 네이버 로컬 검색 API의 sort 옵션 사용
        effective_sort = sort

        query_params = {
            "query": query,
            "display": limit,
            "start": 1,
            "sort": effective_sort,
        }

        # 네이버 로컬 검색 API는 coordinate 파라미터를 지원하지 않으므로 제거
        # query에 주소 정보가 포함되어 있으므로 coordinate는 필요 없음

        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(base_url, headers=headers, params=query_params) as response:
                    if response.status != 200:
                        error_text = await response.text()
                        await params.result_callback(
                            {
                                "error": "네이버 로컬 검색 API 호출에 실패했습니다.",
                                "status": response.status,
                                "details": error_text,
                            }
                        )
                        return

                    data = await response.json()
                    logger.info(f"Naver Local API response: {data}")
        except Exception as e:
            logger.error(f"Naver Local API error: {e}")
            await params.result_callback(
                {
                    "error": f"네이버 로컬 검색 API 호출 중 오류가 발생했습니다: {str(e)}",
                }
            )
            return

        items = data.get("items", [])
        cleaned: List[Dict[str, Any]] = []

        for item in items:
            title = item.get("title", "")
            title = re.sub(r"<.*?>", "", title).strip()

            cleaned.append(
                {
                    "name": title or None,
                    "category": item.get("category"),
                    "address": item.get("roadAddress") or item.get("address"),
                    "telephone": item.get("telephone") or None,
                    "link": item.get("link") or None,
                    "mapx": item.get("mapx"),
                    "mapy": item.get("mapy"),
                    "distance": item.get("distance"),
                    "description": item.get("description") or None,
                }
            )

        await params.result_callback(
            {
                "query": query,
                "count": len(cleaned),
                "source": "naver-local",
                "sort": sort,
                "places": cleaned,
            }
        )

    return get_nearby_places
