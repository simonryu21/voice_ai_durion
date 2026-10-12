"""Address tool for converting coordinates to address (reverse geocoding)."""

import os
from typing import Any, Dict, Optional

import aiohttp
from loguru import logger
from pipecat.adapters.schemas.function_schema import FunctionSchema
from pipecat.runner.run import get_user_location
from pipecat.services.llm_service import FunctionCallParams

# 좌표-주소 변환 함수 스키마 정의
address_from_coordinates_function = FunctionSchema(
    name="get_address_from_coordinates",
    description="위도/경도 좌표를 정확한 주소로 변환합니다 (역지오코딩). 사용자가 현재 위치의 정확한 주소를 알고 싶어할 때 이 함수를 사용하세요. '서울'처럼 광범위한 정보가 아닌 구체적인 주소(시/도, 시/군/구, 동/읍/면, 건물명 등)를 제공합니다.",
    properties={
        "latitude": {
            "type": "number",
            "description": "위도 (예: 37.5665). 제공되지 않으면 저장된 위치를 사용합니다.",
        },
        "longitude": {
            "type": "number",
            "description": "경도 (예: 126.9780). 제공되지 않으면 저장된 위치를 사용합니다.",
        },
    },
    required=[],
)


async def _fetch_address_from_coordinates(
    latitude: float, longitude: float
) -> Optional[Dict[str, Any]]:
    """위경도를 주소로 변환하는 네이버 Maps API를 호출합니다.

    Args:
        latitude: 위도
        longitude: 경도

    Returns:
        API 응답의 첫 번째 결과 데이터 또는 None (에러 시)
    """
    try:
        # 네이버 클라우드 플랫폼 Maps API - 좌표-주소 변환 (역지오코딩)
        naver_maps_api_key_id = os.getenv("NAVER_MAPS_API_KEY_ID")
        naver_maps_api_key = os.getenv("NAVER_MAPS_API_KEY")

        if (
            not naver_maps_api_key_id
            or naver_maps_api_key_id == "your_naver_maps_api_key_id"
            or not naver_maps_api_key
            or naver_maps_api_key == "your_naver_maps_api_key"
        ):
            return None

        base_url = "https://maps.apigw.ntruss.com/map-reversegeocode/v2/gc"
        headers = {
            "x-ncp-apigw-api-key-id": naver_maps_api_key_id.strip(),
            "x-ncp-apigw-api-key": naver_maps_api_key.strip(),
        }
        query_params = {
            "coords": f"{longitude},{latitude}",
            "output": "json",
            "orders": "roadaddr,addr",
        }

        async with aiohttp.ClientSession() as session:
            async with session.get(base_url, headers=headers, params=query_params) as response:
                if response.status != 200:
                    logger.debug(f"Naver Maps API error: status={response.status}")
                    return None

                data = await response.json()
                results = data.get("results", [])
                if not results:
                    return None

                return results[0]
    except Exception as e:
        logger.debug(f"Error converting coordinates to address: {e}")

    return None


def create_get_address_from_coordinates(session_pc_id: Optional[str]):
    """세션별 좌표-주소 변환 함수를 생성합니다."""

    async def get_address_from_coordinates(params: FunctionCallParams):
        """위도/경도를 정확한 주소로 변환합니다 (reverse geocoding)."""
        latitude = params.arguments.get("latitude")
        longitude = params.arguments.get("longitude")

        # 세션 저장 위치 사용
        if latitude is None or longitude is None:
            try:
                if session_pc_id:
                    stored_location = get_user_location(session_pc_id)
                    if stored_location:
                        latitude = latitude or stored_location.get("latitude")
                        longitude = longitude or stored_location.get("longitude")
                        logger.info(f"Using stored location: lat={latitude}, lon={longitude}")
            except Exception as e:
                logger.warning(f"Failed to get stored location: {e}")

        if latitude is None or longitude is None:
            await params.result_callback(
                {
                    "error": "위도/경도 정보가 없습니다.",
                    "hint": "브라우저에서 위치 공유를 허용하거나 latitude/longitude 파라미터를 직접 제공해 주세요.",
                }
            )
            return

        try:
            latitude = float(latitude)
            longitude = float(longitude)
        except (TypeError, ValueError):
            await params.result_callback(
                {
                    "error": "유효하지 않은 위도/경도 값입니다.",
                    "latitude": latitude,
                    "longitude": longitude,
                }
            )
            return

        # 네이버 클라우드 플랫폼 Maps API - 좌표-주소 변환 (역지오코딩)
        # 공통 helper 함수 사용
        result_data = await _fetch_address_from_coordinates(latitude, longitude)

        if not result_data:
            # API 키 확인
            naver_maps_api_key_id = os.getenv("NAVER_MAPS_API_KEY_ID")
            naver_maps_api_key = os.getenv("NAVER_MAPS_API_KEY")

            if (
                not naver_maps_api_key_id
                or naver_maps_api_key_id == "your_naver_maps_api_key_id"
                or not naver_maps_api_key
                or naver_maps_api_key == "your_naver_maps_api_key"
            ):
                logger.warning("Naver Maps API keys not configured")
                await params.result_callback(
                    {
                        "error": "NAVER_MAPS_API_KEY_ID 또는 NAVER_MAPS_API_KEY가 설정되지 않았습니다.",
                        "hint": "https://www.ncloud.com/product/applicationService/maps 에서 Maps API 서비스를 신청하고 Access Key ID와 Secret Key를 발급받아 .env 파일에 설정해 주세요.",
                        "latitude": latitude,
                        "longitude": longitude,
                    }
                )
            else:
                await params.result_callback(
                    {
                        "error": "주소 정보를 찾을 수 없습니다.",
                        "latitude": latitude,
                        "longitude": longitude,
                    }
                )
            return
        region = result_data.get("region", {})
        land = result_data.get("land", {})
        road = result_data.get("road", {})

        # 도로명 주소가 있으면 우선 사용, 없으면 지번 주소 사용
        if road and road.get("name"):
            # 도로명 주소
            region_1 = region.get("area1", {}).get("name", "")  # 시/도
            region_2 = region.get("area2", {}).get("name", "")  # 시/군/구
            region_3 = region.get("area3", {}).get("name", "")  # 동/읍/면
            region_4 = region.get("area4", {}).get("name", "")  # 상세 지역
            road_name = road.get("name", "")  # 도로명
            building_number = road.get("number1", "")  # 건물번호
            building_name = land.get("name", "") or road.get("name", "")  # 건물명
            full_address = f"{region_1} {region_2} {region_3} {road_name} {building_number}".strip()
            address_type = "road"
        elif land and land.get("number1"):
            # 지번 주소
            region_1 = region.get("area1", {}).get("name", "")
            region_2 = region.get("area2", {}).get("name", "")
            region_3 = region.get("area3", {}).get("name", "")
            region_4 = region.get("area4", {}).get("name", "")
            lot_number = f"{land.get('number1', '')}-{land.get('number2', '')}".strip("-")
            building_name = land.get("name", "")
            full_address = f"{region_1} {region_2} {region_3} {region_4} {lot_number}".strip()
            address_type = "lot"
        else:
            await params.result_callback(
                {
                    "error": "주소 정보를 파싱할 수 없습니다.",
                    "latitude": latitude,
                    "longitude": longitude,
                }
            )
            return

        # 포맷된 주소 생성
        formatted_parts = [region_1, region_2, region_3]
        if address_type == "road":
            if road_name:
                formatted_parts.append(road_name)
            if building_number:
                formatted_parts.append(building_number)
        elif address_type == "lot":
            if region_4:
                formatted_parts.append(region_4)
            if lot_number:
                formatted_parts.append(lot_number)

        formatted_address = " ".join(filter(None, formatted_parts))
        if building_name and building_name not in formatted_address:
            formatted_address += f" {building_name}"

        result = {
            "latitude": latitude,
            "longitude": longitude,
            "full_address": full_address,
            "region_1": region_1,  # 시/도
            "region_2": region_2,  # 시/군/구
            "region_3": region_3,  # 동/읍/면
            "region_4": region_4 if address_type == "lot" else "",  # 상세 지역 (지번 주소일 때)
            "building_name": building_name,
            "address_type": address_type,  # "road" (도로명) 또는 "lot" (지번)
            "formatted_address": formatted_address.strip(),
        }

        await params.result_callback(result)

    return get_address_from_coordinates
