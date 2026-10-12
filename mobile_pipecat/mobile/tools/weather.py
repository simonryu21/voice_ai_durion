"""Weather tool for getting current weather information."""

import os
from typing import Optional

import aiohttp
from loguru import logger
from pipecat.adapters.schemas.function_schema import FunctionSchema
from pipecat.runner.run import get_user_location
from pipecat.services.llm_service import FunctionCallParams

# 날씨 함수 스키마 정의
weather_function = FunctionSchema(
    name="get_current_weather",
    description="현재 날씨 정보를 가져옵니다. 날씨 관련 질문에 답변할 때 이 함수를 사용하세요. 이 함수는 자동으로 사용자의 현재 위치(세션에 저장된 위치)를 확인하여 해당 위치의 날씨를 조회합니다. 특정 도시의 날씨를 조회하고 싶은 경우에만 location 파라미터를 사용하세요.",
    properties={
        "location": {
            "type": "string",
            "description": "날씨를 조회할 도시명 (예: Seoul, Busan, Tokyo). 현재 위치가 아닌 특정 도시의 날씨를 조회할 때만 사용합니다.",
        },
        "latitude": {
            "type": "number",
            "description": "위도 (예: 37.5665). longitude와 함께 사용됩니다. 일반적으로는 세션에 저장된 현재 위치가 자동으로 사용되므로 이 파라미터는 필요하지 않습니다.",
        },
        "longitude": {
            "type": "number",
            "description": "경도 (예: 126.9780). latitude와 함께 사용됩니다. 일반적으로는 세션에 저장된 현재 위치가 자동으로 사용되므로 이 파라미터는 필요하지 않습니다.",
        },
    },
    required=[],
)


def create_get_current_weather(session_pc_id: Optional[str]):
    """세션별 날씨 조회 함수를 생성합니다."""

    async def get_current_weather(params: FunctionCallParams):
        """현재 날씨 정보를 가져오는 함수"""
        # location, latitude, longitude 파라미터 가져오기
        location = params.arguments.get("location")
        latitude = params.arguments.get("latitude")
        longitude = params.arguments.get("longitude")

        # 항상 먼저 세션 저장 위치 확인 (현재 위치 우선 사용)
        try:
            if session_pc_id:
                stored_location = get_user_location(session_pc_id)
                if stored_location:
                    stored_lat = stored_location.get("latitude")
                    stored_lon = stored_location.get("longitude")
                    if stored_lat is not None and stored_lon is not None:
                        # 세션 저장 위치가 있으면 우선 사용
                        latitude = stored_lat
                        longitude = stored_lon
                        logger.info(
                            f"Using stored current location: lat={latitude}, lon={longitude}"
                        )
        except Exception as e:
            logger.warning(f"Failed to get stored location: {e}")

        # 파라미터로 제공된 위경도가 있고 세션 위치가 없었던 경우에만 사용
        if latitude is None or longitude is None:
            param_lat = params.arguments.get("latitude")
            param_lon = params.arguments.get("longitude")
            if param_lat is not None and param_lon is not None:
                latitude = param_lat
                longitude = param_lon
                logger.info(f"Using provided location: lat={latitude}, lon={longitude}")

        # OpenWeatherMap API 키 확인
        api_key = os.getenv("OPENWEATHER_API_KEY")

        if not api_key or api_key == "your_openweather_api_key":
            logger.warning(
                "OPENWEATHER_API_KEY가 설정되지 않았습니다. 기본 날씨 정보를 반환합니다."
            )
            result = {
                "location": location or "Unknown",
                "temperature": "22",
                "description": "날씨 정보를 가져올 수 없습니다. OPENWEATHER_API_KEY를 설정해주세요.",
                "humidity": None,
                "wind_speed": None,
                "error": "API key not configured",
            }
            await params.result_callback(result)
            return

        try:
            # OpenWeatherMap API 호출
            url = "https://api.openweathermap.org/data/2.5/weather"
            async with aiohttp.ClientSession() as session:
                # location 파라미터가 명시적으로 제공된 경우 우선 사용
                if location:
                    api_params = {
                        "q": location,
                        "appid": api_key,
                        "units": "metric",  # 섭씨 온도
                        "lang": "kr",  # 한국어 응답
                    }
                elif latitude is not None and longitude is not None:
                    # location이 없고 위경도가 있는 경우 위경도 사용
                    api_params = {
                        "lat": latitude,
                        "lon": longitude,
                        "appid": api_key,
                        "units": "metric",  # 섭씨 온도
                        "lang": "kr",  # 한국어 응답
                    }
                else:
                    # 기본값: Seoul
                    api_params = {
                        "q": "Seoul",
                        "appid": api_key,
                        "units": "metric",
                        "lang": "kr",
                    }

                async with session.get(url, params=api_params) as response:
                    if response.status == 200:
                        data = await response.json()
                        weather_info = {
                            "location": f"{data['name']}, {data['sys']['country']}",
                            "coordinates": f"({data['coord']['lat']}, {data['coord']['lon']})",
                            "temperature": str(round(data["main"]["temp"])),
                            "feels_like": str(round(data["main"]["feels_like"])),
                            "description": data["weather"][0]["description"],
                            "humidity": str(data["main"]["humidity"]),
                            "wind_speed": str(round(data["wind"]["speed"], 1)),
                            "pressure": str(data["main"]["pressure"]),
                        }
                        await params.result_callback(weather_info)
                    else:
                        error_data = await response.json()
                        result = {
                            "location": location or "Unknown",
                            "error": f"API error: {error_data.get('message', 'Unknown error')}",
                        }
                        await params.result_callback(result)
        except Exception as e:
            logger.error(f"Weather API error: {e}")
            result = {
                "location": location or "Unknown",
                "error": f"날씨 정보를 가져오는 중 오류가 발생했습니다: {str(e)}",
            }
            await params.result_callback(result)

    return get_current_weather
