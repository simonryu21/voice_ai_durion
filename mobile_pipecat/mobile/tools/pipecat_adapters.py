"""Pipecat-native function-calling adapters for the bot's local tools.

Each tool is exposed as a :class:`FunctionSchema` (attached to the
``LLMContext`` via :class:`ToolsSchema`) plus an
``async def handler(params: FunctionCallParams)`` callable registered on
the LLM service via :meth:`LLMService.register_function`.

Usage::

    schemas, register = create_pipecat_local_tools(session_pc_id, ..., mem0_service=...)
    register(llm)  # attaches handlers to OpenAILLMService.register_function
    context = LLMContext(messages, tools=ToolsSchema(standard_tools=schemas))
"""

from __future__ import annotations

import asyncio
import json
import os
import re
from datetime import datetime
from typing import Any, Callable, Dict, List, Optional, Tuple

import aiohttp
from loguru import logger
from pipecat.adapters.schemas.function_schema import FunctionSchema
from pipecat.runner.run import get_all_user_locations, get_user_location
from pipecat.services.llm_service import FunctionCallParams

from .address import _fetch_address_from_coordinates
from .handoff_screenshot import analyze_handoff_screenshot_for_session


def _resolve_location(session_pc_id: Optional[str]) -> Optional[Dict[str, Any]]:
    """Resolve current user location from session or latest global record."""
    location_info = None
    try:
        if session_pc_id:
            location_info = get_user_location(session_pc_id)
        if not location_info:
            all_locations = get_all_user_locations()
            if all_locations:
                sorted_locations = sorted(
                    all_locations.items(),
                    key=lambda x: x[1].get("timestamp", ""),
                    reverse=True,
                )
                if sorted_locations:
                    _, location_info = sorted_locations[0]
    except Exception as e:
        logger.warning(f"Failed to get stored location: {e}")
    return location_info


HandlerRegistrar = Callable[[Any], None]
"""Closure that, given a Pipecat ``LLMService`` instance, registers all
handlers produced by :func:`create_pipecat_local_tools`."""


def create_pipecat_local_tools(
    session_pc_id: Optional[str],
    *,
    mem0_service: Optional[Any] = None,
) -> Tuple[List[FunctionSchema], HandlerRegistrar]:
    """Build Pipecat ``FunctionSchema`` list and matching handler registrar.

    Args:
        session_pc_id: WebRTC pc_id used to resolve session-scoped data
            (e.g., stored user location, cached handoff screenshot).
        mem0_service: Optional ``Mem0MemoryService``. When provided, a
            ``mem0_search`` function is added so the supervisor can fetch
            past memories on demand.

    Returns:
        Tuple ``(schemas, register)``. ``schemas`` is the list of
        :class:`FunctionSchema` to wrap in :class:`ToolsSchema`.
        ``register(llm)`` attaches each handler via
        ``llm.register_function(name, handler)`` on the supplied service.
    """
    schemas: List[FunctionSchema] = []
    handlers: Dict[str, Callable[[FunctionCallParams], Any]] = {}

    # 1. get_current_datetime ────────────────────────────────────────────
    async def _get_current_datetime(params: FunctionCallParams) -> None:
        now = datetime.now()
        day_kr = {
            "Monday": "월요일",
            "Tuesday": "화요일",
            "Wednesday": "수요일",
            "Thursday": "목요일",
            "Friday": "금요일",
            "Saturday": "토요일",
            "Sunday": "일요일",
        }
        await params.result_callback(
            json.dumps(
                {
                    "date": now.strftime("%Y년 %m월 %d일"),
                    "time": now.strftime("%H시 %M분"),
                    "day_of_week": day_kr.get(now.strftime("%A"), now.strftime("%A")),
                    "iso_datetime": now.isoformat(),
                },
                ensure_ascii=False,
            )
        )

    schemas.append(
        FunctionSchema(
            name="get_current_datetime",
            description="현재 날짜와 시간 정보를 가져옵니다.",
            properties={},
            required=[],
        )
    )
    handlers["get_current_datetime"] = _get_current_datetime

    # 2. get_current_location ────────────────────────────────────────────
    async def _get_current_location(params: FunctionCallParams) -> None:
        location_info = _resolve_location(session_pc_id)
        if not location_info:
            await params.result_callback(
                json.dumps(
                    {
                        "latitude": None,
                        "longitude": None,
                        "message": "저장된 위치 정보가 없습니다. 브라우저에서 위치 공유를 허용해주세요.",
                    },
                    ensure_ascii=False,
                )
            )
            return
        await params.result_callback(
            json.dumps(
                {
                    "latitude": location_info.get("latitude"),
                    "longitude": location_info.get("longitude"),
                    "timestamp": location_info.get("timestamp"),
                },
                ensure_ascii=False,
            )
        )

    schemas.append(
        FunctionSchema(
            name="get_current_location",
            description="현재 세션 또는 최근 사용자 위치 정보를 반환합니다.",
            properties={},
            required=[],
        )
    )
    handlers["get_current_location"] = _get_current_location

    # 3. get_current_weather ─────────────────────────────────────────────
    async def _get_current_weather(params: FunctionCallParams) -> None:
        args = params.arguments or {}
        location = args.get("location")
        latitude = args.get("latitude")
        longitude = args.get("longitude")

        if session_pc_id:
            stored = get_user_location(session_pc_id)
            if stored:
                latitude = latitude or stored.get("latitude")
                longitude = longitude or stored.get("longitude")

        api_key = os.getenv("OPENWEATHER_API_KEY")
        if not api_key or api_key == "your_openweather_api_key":
            await params.result_callback("날씨 API가 설정되지 않아 날씨 정보를 가져올 수 없습니다.")
            return

        url = "https://api.openweathermap.org/data/2.5/weather"
        if location:
            api_params = {"q": location, "appid": api_key, "units": "metric", "lang": "kr"}
        elif latitude is not None and longitude is not None:
            api_params = {
                "lat": latitude,
                "lon": longitude,
                "appid": api_key,
                "units": "metric",
                "lang": "kr",
            }
        else:
            api_params = {"q": "Seoul", "appid": api_key, "units": "metric", "lang": "kr"}

        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(url, params=api_params) as resp:
                    if resp.status == 200:
                        data = await resp.json()
                        await params.result_callback(
                            json.dumps(
                                {
                                    "location": f"{data['name']}, {data['sys']['country']}",
                                    "temperature": str(round(data["main"]["temp"])),
                                    "feels_like": str(round(data["main"]["feels_like"])),
                                    "description": data["weather"][0]["description"],
                                    "humidity": str(data["main"]["humidity"]),
                                    "wind_speed": str(round(data["wind"]["speed"], 1)),
                                },
                                ensure_ascii=False,
                            )
                        )
                        return
                    error_data = await resp.json()
                    logger.debug("OpenWeather API error: %s", error_data)
                    await params.result_callback(
                        "날씨 정보를 가져오지 못했습니다. 잠시 후 다시 시도해 주세요."
                    )
        except Exception as e:
            logger.debug("Weather request failed: %s", e)
            await params.result_callback(
                "날씨 정보를 일시적으로 가져오지 못했습니다. 잠시 후 다시 시도해 주세요."
            )

    schemas.append(
        FunctionSchema(
            name="get_current_weather",
            description=(
                "현재 날씨 정보를 가져옵니다. 자동으로 세션 저장 위치를 사용하며, "
                "특정 도시 조회 시에만 location 파라미터를 사용하세요."
            ),
            properties={
                "location": {
                    "type": "string",
                    "description": "도시명 (예: Seoul, Busan). 현재 위치가 아닌 특정 도시 날씨 조회 시에만 사용.",
                },
                "latitude": {"type": "number", "description": "위도"},
                "longitude": {"type": "number", "description": "경도"},
            },
            required=[],
        )
    )
    handlers["get_current_weather"] = _get_current_weather

    # 4. get_nearby_places ───────────────────────────────────────────────
    async def _get_nearby_places(params: FunctionCallParams) -> None:
        args = params.arguments or {}
        query = args.get("query", "맛집")
        limit = args.get("limit", 5)
        sort = args.get("sort", "random")
        latitude = args.get("latitude")
        longitude = args.get("longitude")

        if (latitude is None or longitude is None) and session_pc_id:
            stored = get_user_location(session_pc_id)
            if stored:
                latitude = latitude or stored.get("latitude")
                longitude = longitude or stored.get("longitude")

        if latitude is None or longitude is None:
            await params.result_callback(
                "위치 정보를 찾을 수 없습니다. 브라우저에서 위치 공유를 허용해 주세요."
            )
            return

        limit = max(1, min(int(limit), 5))
        if sort not in {"sim", "random", "comment"}:
            sort = "random"

        result_data = await _fetch_address_from_coordinates(latitude, longitude)
        search_query = query
        if result_data:
            region = result_data.get("region", {})
            road = result_data.get("road", {})
            land = result_data.get("land", {})
            if road and road.get("name"):
                r1 = region.get("area1", {}).get("name", "")
                r2 = region.get("area2", {}).get("name", "")
                r3 = region.get("area3", {}).get("name", "")
                rn = road.get("name", "")
                bn = road.get("number1", "")
                full_addr = f"{r1} {r2} {r3} {rn} {bn}".strip()
            elif land and land.get("number1"):
                r1 = region.get("area1", {}).get("name", "")
                r2 = region.get("area2", {}).get("name", "")
                r3 = region.get("area3", {}).get("name", "")
                r4 = region.get("area4", {}).get("name", "")
                ln = f"{land.get('number1', '')}-{land.get('number2', '')}".strip("-")
                full_addr = f"{r1} {r2} {r3} {r4} {ln}".strip()
            else:
                full_addr = None
            if full_addr:
                search_query = f"{full_addr} {query}".strip()

        client_id = os.getenv("NAVER_CLIENT_ID")
        client_secret = os.getenv("NAVER_CLIENT_SECRET")
        base_url = "https://openapi.naver.com/v1/search/local.json"
        headers = {"X-Naver-Client-Id": client_id, "X-Naver-Client-Secret": client_secret}
        qp = {"query": search_query, "display": limit, "start": 1, "sort": sort}

        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(base_url, headers=headers, params=qp) as resp:
                    if resp.status != 200:
                        await params.result_callback(
                            "주변 장소 검색에 실패했습니다. 잠시 후 다시 시도해 주세요."
                        )
                        return
                    data = await resp.json()
        except Exception as e:
            logger.debug("Nearby places search failed: %s", e)
            await params.result_callback(
                "주변 장소 검색에 실패했습니다. 잠시 후 다시 시도해 주세요."
            )
            return

        items = data.get("items", [])
        cleaned = []
        for item in items:
            title = re.sub(r"<.*?>", "", item.get("title", "")).strip()
            cleaned.append(
                {
                    "name": title or None,
                    "category": item.get("category"),
                    "address": item.get("roadAddress") or item.get("address"),
                    "telephone": item.get("telephone") or None,
                    "link": item.get("link") or None,
                }
            )
        await params.result_callback(
            json.dumps(
                {"query": search_query, "count": len(cleaned), "places": cleaned},
                ensure_ascii=False,
            )
        )

    schemas.append(
        FunctionSchema(
            name="get_nearby_places",
            description="현재 위치 주변의 맛집/장소 정보를 조회합니다.",
            properties={
                "query": {
                    "type": "string",
                    "description": "검색할 키워드 (예: 맛집, 카페, 편의점)",
                },
                "limit": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 5,
                    "description": "결과 개수 (1~5)",
                },
                "sort": {
                    "type": "string",
                    "enum": ["sim", "random", "comment"],
                    "description": "정렬 방식",
                },
                "latitude": {"type": "number", "description": "사용자 지정 위도"},
                "longitude": {"type": "number", "description": "사용자 지정 경도"},
            },
            required=[],
        )
    )
    handlers["get_nearby_places"] = _get_nearby_places

    # 5. get_address_from_coordinates ────────────────────────────────────
    async def _get_address_from_coordinates(params: FunctionCallParams) -> None:
        args = params.arguments or {}
        latitude = args.get("latitude")
        longitude = args.get("longitude")

        if latitude is None or longitude is None:
            if session_pc_id:
                stored = get_user_location(session_pc_id)
                if stored:
                    latitude = latitude or stored.get("latitude")
                    longitude = longitude or stored.get("longitude")

        if latitude is None or longitude is None:
            await params.result_callback("위도/경도 정보가 없습니다. 위치 공유를 허용해 주세요.")
            return

        result_data = await _fetch_address_from_coordinates(latitude, longitude)
        if not result_data:
            await params.result_callback("해당 좌표의 주소 정보를 찾을 수 없습니다.")
            return

        region = result_data.get("region", {})
        land = result_data.get("land", {})
        road = result_data.get("road", {})

        if road and road.get("name"):
            r1 = region.get("area1", {}).get("name", "")
            r2 = region.get("area2", {}).get("name", "")
            r3 = region.get("area3", {}).get("name", "")
            rn = road.get("name", "")
            bn = road.get("number1", "")
            building_name = land.get("name", "") or road.get("name", "")
            full_address = f"{r1} {r2} {r3} {rn} {bn}".strip()
            address_type = "road"
        elif land and land.get("number1"):
            r1 = region.get("area1", {}).get("name", "")
            r2 = region.get("area2", {}).get("name", "")
            r3 = region.get("area3", {}).get("name", "")
            r4 = region.get("area4", {}).get("name", "")
            ln = f"{land.get('number1', '')}-{land.get('number2', '')}".strip("-")
            building_name = land.get("name", "")
            full_address = f"{r1} {r2} {r3} {r4} {ln}".strip()
            address_type = "lot"
        else:
            await params.result_callback("주소 정보를 파싱할 수 없습니다.")
            return

        await params.result_callback(
            json.dumps(
                {
                    "latitude": latitude,
                    "longitude": longitude,
                    "full_address": full_address,
                    "building_name": building_name,
                    "address_type": address_type,
                },
                ensure_ascii=False,
            )
        )

    schemas.append(
        FunctionSchema(
            name="get_address_from_coordinates",
            description="위도/경도 좌표를 정확한 주소로 변환합니다 (역지오코딩).",
            properties={
                "latitude": {
                    "type": "number",
                    "description": "위도. 미제공 시 저장된 위치 사용.",
                },
                "longitude": {
                    "type": "number",
                    "description": "경도. 미제공 시 저장된 위치 사용.",
                },
            },
            required=[],
        )
    )
    handlers["get_address_from_coordinates"] = _get_address_from_coordinates

    # 6. search_social_images ────────────────────────────────────────────
    async def _search_social_images(params: FunctionCallParams) -> None:
        args = params.arguments or {}
        query = args.get("query", "")
        platform = args.get("platform", "all")
        limit = max(1, min(int(args.get("limit", 5)), 10))

        serp_api_key = os.getenv("SERP_API_KEY")
        if not serp_api_key or serp_api_key == "your_serp_api_key":
            await params.result_callback("이미지 검색 API가 설정되지 않아 검색할 수 없습니다.")
            return

        engine_map = {
            "instagram": "google_images",
            "pinterest": "pinterest",
            "twitter": "google_images",
            "all": "google_images",
        }
        engine = engine_map.get(platform, "google_images")

        if platform == "instagram":
            sq = f"site:instagram.com {query}"
        elif platform == "pinterest":
            sq = query
        elif platform == "twitter":
            sq = f"site:twitter.com {query}"
        else:
            sq = f"{query} (instagram OR pinterest OR twitter)"

        qp = {
            "engine": engine,
            "q": sq,
            "api_key": serp_api_key,
            "num": limit,
            "hl": "ko",
            "gl": "kr",
        }

        try:
            async with aiohttp.ClientSession() as session:
                async with session.get("https://serpapi.com/search.json", params=qp) as resp:
                    if resp.status != 200:
                        await params.result_callback(
                            "소셜 이미지 검색에 실패했습니다. 잠시 후 다시 시도해 주세요."
                        )
                        return
                    data = await resp.json()
                    images = data.get("images_results", [])
                    if not images and platform == "pinterest":
                        images = [
                            {
                                "title": p.get("title", ""),
                                "link": p.get("link", ""),
                                "thumbnail": p.get("image", ""),
                            }
                            for p in data.get("pins", [])[:limit]
                        ]
                    cleaned = [
                        {
                            "title": img.get("title", ""),
                            "url": img.get("link") or img.get("original", ""),
                            "thumbnail": img.get("thumbnail", ""),
                        }
                        for img in images[:limit]
                    ]
                    await params.result_callback(
                        json.dumps(
                            {
                                "query": query,
                                "platform": platform,
                                "count": len(cleaned),
                                "images": cleaned,
                            },
                            ensure_ascii=False,
                        )
                    )
        except Exception as e:
            logger.debug("Social image search failed: %s", e)
            await params.result_callback(
                "소셜 이미지 검색에 실패했습니다. 잠시 후 다시 시도해 주세요."
            )

    schemas.append(
        FunctionSchema(
            name="search_social_images",
            description="소셜 미디어에서 이미지를 검색합니다.",
            properties={
                "query": {
                    "type": "string",
                    "description": "검색할 키워드 (예: 맛집, 여행, 패션)",
                },
                "platform": {
                    "type": "string",
                    "enum": ["instagram", "pinterest", "twitter", "all"],
                    "description": "검색할 플랫폼",
                },
                "limit": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 10,
                    "description": "결과 개수 (1~10)",
                },
            },
            required=["query"],
        )
    )
    handlers["search_social_images"] = _search_social_images

    # 7. web_search ──────────────────────────────────────────────────────
    async def _web_search(params: FunctionCallParams) -> None:
        args = params.arguments or {}
        query = args.get("query", "")
        max_results = max(1, min(int(args.get("max_results", 5)), 10))
        region = args.get("region", "kr-ko")
        search_provider = args.get("search_provider", "auto")

        if search_provider == "auto":
            search_provider = os.getenv("WEB_SEARCH_PROVIDER", "tavily").lower()
            if search_provider not in {"tavily", "duckduckgo"}:
                search_provider = "tavily"

        formatted_results: List[Dict[str, Any]] = []
        source = "unknown"

        try:
            if search_provider == "tavily":
                api_key = os.getenv("TAVILY_API_KEY")
                if not api_key:
                    search_provider = "duckduckgo"
                else:
                    try:
                        from tavily import TavilyClient  # type: ignore[import-not-found]
                    except ModuleNotFoundError:
                        logger.warning(
                            "Tavily selected but dependency missing; falling back to DuckDuckGo "
                            "(install: tavily-python)"
                        )
                        search_provider = "duckduckgo"
                    else:
                        client = TavilyClient(api_key=api_key)
                        response = client.search(
                            query=query, max_results=max_results, search_depth="advanced"
                        )
                        for r in response.get("results", []):
                            formatted_results.append(
                                {
                                    "title": r.get("title", ""),
                                    "url": r.get("url", ""),
                                    "snippet": r.get("content", ""),
                                }
                            )
                        source = "tavily"

            if search_provider == "duckduckgo" or (
                search_provider == "tavily" and not formatted_results
            ):
                from duckduckgo_search import DDGS  # type: ignore[import-not-found]

                def _sync():
                    with DDGS() as ddgs:
                        return list(
                            ddgs.text(keywords=query, max_results=max_results, region=region)
                        )

                results = await asyncio.to_thread(_sync)
                for r in results:
                    formatted_results.append(
                        {
                            "title": r.get("title", ""),
                            "url": r.get("href", ""),
                            "snippet": r.get("body", ""),
                        }
                    )
                source = "duckduckgo"

            await params.result_callback(
                json.dumps(
                    {
                        "query": query,
                        "count": len(formatted_results),
                        "source": source,
                        "results": formatted_results,
                    },
                    ensure_ascii=False,
                )
            )
        except Exception:
            logger.exception(
                "Web search failed (provider={}, query={!r}, max_results={}, region={})",
                search_provider,
                query,
                max_results,
                region,
            )
            await params.result_callback("웹 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.")

    schemas.append(
        FunctionSchema(
            name="web_search",
            description=(
                "웹 검색을 수행합니다. 사용자가 '검색해줘', '최신 정보' 등을 요청할 때 사용. "
                "날씨/위치/맛집/이미지 검색에는 전용 도구를 사용하세요."
            ),
            properties={
                "query": {"type": "string", "description": "검색할 키워드나 질문"},
                "max_results": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 10,
                    "description": "최대 결과 개수",
                },
                "region": {
                    "type": "string",
                    "description": "검색 지역 ('kr-ko', 'us-en' 등)",
                },
                "search_provider": {
                    "type": "string",
                    "enum": ["tavily", "duckduckgo", "auto"],
                    "description": "검색 엔진",
                },
            },
            required=["query"],
        )
    )
    handlers["web_search"] = _web_search

    # 8. analyze_handoff_screenshot ──────────────────────────────────────
    async def _analyze_handoff_screenshot(params: FunctionCallParams) -> None:
        result = await analyze_handoff_screenshot_for_session(session_pc_id)
        await params.result_callback(result)

    schemas.append(
        FunctionSchema(
            name="analyze_handoff_screenshot",
            description=(
                "세션에 저장된 WebView 스크린샷을 비전 모델로 분석해 구조화된 JSON을 반환합니다."
            ),
            properties={},
            required=[],
        )
    )
    handlers["analyze_handoff_screenshot"] = _analyze_handoff_screenshot

    # 9. mem0_search (optional) ──────────────────────────────────────────
    if mem0_service:

        async def _mem0_search(params: FunctionCallParams) -> None:
            args = params.arguments or {}
            query = args.get("query", "")
            try:
                if not query or not query.strip():
                    await params.result_callback(
                        json.dumps(
                            {
                                "query": query,
                                "count": 0,
                                "memories": [],
                                "message": "빈 질의입니다.",
                            },
                            ensure_ascii=False,
                        )
                    )
                    return
                memories = await mem0_service._retrieve_memories(query.strip())
                cleaned: List[str] = []
                for m in memories or []:
                    if isinstance(m, dict):
                        content = (m.get("memory") or m.get("content") or "").strip()
                    else:
                        content = str(m).strip()
                    if content:
                        cleaned.append(content)
                await params.result_callback(
                    json.dumps(
                        {"query": query, "count": len(cleaned), "memories": cleaned},
                        ensure_ascii=False,
                    )
                )
            except Exception as e:
                logger.warning(f"Mem0 search failed (non-fatal): {e}")
                await params.result_callback(
                    json.dumps(
                        {
                            "query": query,
                            "count": 0,
                            "memories": [],
                            "error": "mem0_search_failed",
                        },
                        ensure_ascii=False,
                    )
                )

        schemas.append(
            FunctionSchema(
                name="mem0_search",
                description=(
                    "이전 대화 메모리를 검색합니다. 사용자의 요청이 과거 맥락(이름/선호/이전 결정/이전 작업)을 "
                    "참조하는 것 같을 때만 호출하세요. 날씨/시간/웹검색 같은 단발성 정보에는 사용하지 마세요."
                ),
                properties={
                    "query": {
                        "type": "string",
                        "description": (
                            "메모리에서 찾고 싶은 질의. 사용자의 마지막 요청을 그대로 넣거나, "
                            "찾고 싶은 정보(선호/이름/이전 결정 등)를 간단히 요약해서 넣으세요."
                        ),
                    },
                },
                required=["query"],
            )
        )
        handlers["mem0_search"] = _mem0_search

    def register(llm: Any) -> None:
        """Attach all handlers to the supplied Pipecat LLM service."""
        for name, handler in handlers.items():
            llm.register_function(name, handler)

    return schemas, register


__all__ = ["create_pipecat_local_tools", "HandlerRegistrar"]
