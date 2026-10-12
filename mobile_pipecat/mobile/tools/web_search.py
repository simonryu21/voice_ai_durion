"""Web search tool using multiple search engines (DuckDuckGo, Tavily, etc.)."""

import asyncio
import os
from typing import Any, Dict, List, Optional

# Search engine providers
from duckduckgo_search import DDGS  # type: ignore
from loguru import logger
from pipecat.adapters.schemas.function_schema import FunctionSchema
from pipecat.services.llm_service import FunctionCallParams
from tavily import TavilyClient  # type: ignore

# 웹 검색 함수 스키마 정의
web_search_function = FunctionSchema(
    name="web_search",
    description="""검색 엔진을 사용하여 웹에서 정보를 검색합니다. (Tavily, DuckDuckGo 등)

**반드시 이 함수를 사용해야 하는 경우:**
- 사용자가 명시적으로 "검색해줘", "웹 검색해줘", "인터넷에서 찾아줘", "구글에서 검색해줘", "검색해봐", "찾아봐줘" 등의 표현을 사용할 때
- 사용자가 "최신 정보", "최근 뉴스", "요즘 트렌드" 같은 시간에 민감한 정보를 요청할 때
- 사용자가 특정 주제에 대한 사실 확인이나 검증을 요청할 때
- 사용자가 "웹에서", "인터넷에서", "온라인에서" 같은 표현과 함께 정보를 요청할 때
- 일반적인 지식이나 정보가 필요하지만 다른 전용 도구(get_current_weather, get_nearby_places 등)로는 해결할 수 없을 때

**이 함수를 사용하지 말아야 하는 경우:**
- 날씨 정보: get_current_weather() 사용
- 현재 위치 주변 맛집/장소: get_nearby_places() 사용
- 현재 날짜/시간: get_current_datetime() 사용
- 사용자 위치 정보: get_current_location() 사용
- 소셜 미디어 이미지: search_social_images() 사용

**검색 쿼리 작성 가이드:**
- 사용자의 질문이나 키워드를 그대로 사용하되, 불필요한 조사나 문맥 단어는 제거
- 예: "파이썬 튜토리얼 추천해줘" → query: "파이썬 튜토리얼"
- 예: "최신 AI 뉴스 검색해줘" → query: "최신 AI 뉴스"
- 예: "웹에서 오늘 날씨 찾아봐" → query: "오늘 날씨" (단, get_current_weather가 더 정확하므로 웹 검색 대신 날씨 도구 사용 권장)""",
    properties={
        "query": {
            "type": "string",
            "description": "검색할 키워드나 질문. 사용자의 요청에서 핵심 검색어를 추출하여 사용하세요. (예: '파이썬 튜토리얼', '최신 AI 뉴스', '삼성전자 주가')",
        },
        "max_results": {
            "type": "integer",
            "description": "반환할 검색 결과의 최대 개수 (1~10 사이, 기본값 5). 일반적인 검색은 5개면 충분하며, 더 많은 정보가 필요할 때만 증가시키세요.",
        },
        "region": {
            "type": "string",
            "description": "검색 지역 설정 (예: 'kr-ko' 한국어, 'us-en' 영어, 기본값 'kr-ko'). 한국어 질문은 'kr-ko', 영어 질문은 'us-en'을 사용하세요.",
        },
        "search_provider": {
            "type": "string",
            "description": "검색 엔진 선택 ('tavily', 'duckduckgo', 'auto' 중 선택, 기본값 'auto'). 'auto'는 환경변수 설정에 따라 자동 선택됩니다.",
        },
    },
    required=["query"],
)


def create_web_search():
    """웹 검색 함수를 생성합니다."""

    async def web_search(params: FunctionCallParams):
        """다양한 검색 엔진을 사용하여 웹 검색을 수행합니다."""

        query = params.arguments.get("query")
        max_results = params.arguments.get("max_results", 5)
        region = params.arguments.get("region", "kr-ko")
        search_provider = params.arguments.get("search_provider", "auto")

        # 검색 엔진 자동 선택
        if search_provider == "auto":
            # 환경변수로 우선순위 설정
            preferred_provider = os.getenv("WEB_SEARCH_PROVIDER", "tavily").lower()
            if preferred_provider == "tavily":
                search_provider = "tavily"
            elif preferred_provider == "duckduckgo":
                search_provider = "duckduckgo"
            else:
                search_provider = "tavily"

        # 입력 검증
        if not query or not isinstance(query, str):
            await params.result_callback({"error": "검색 쿼리가 필요합니다.", "query": query})
            return

        query = query.strip()
        if not query:
            await params.result_callback({"error": "검색 쿼리가 비어있습니다.", "query": query})
            return

        # max_results 범위 제한
        try:
            max_results = int(max_results)
        except (TypeError, ValueError):
            max_results = 5
        max_results = max(1, min(max_results, 10))

        try:
            formatted_results: List[Dict[str, Any]] = []
            source = "unknown"

            if search_provider == "tavily":
                # Tavily 검색 (AI 검색 엔진, 더 나은 결과)
                api_key = os.getenv("TAVILY_API_KEY")
                if not api_key:
                    logger.warning("TAVILY_API_KEY가 설정되지 않았습니다. DuckDuckGo로 대체합니다.")
                    search_provider = "duckduckgo"
                else:
                    client = TavilyClient(api_key=api_key)
                    response = client.search(
                        query=query,
                        max_results=max_results,
                        search_depth="advanced",  # 더 깊은 검색
                    )

                    for result in response.get("results", []):
                        formatted_results.append(
                            {
                                "title": result.get("title", ""),
                                "url": result.get("url", ""),
                                "snippet": result.get("content", ""),
                            }
                        )
                    source = "tavily"
                    logger.info(
                        f"Tavily search completed: query='{query}', results={len(formatted_results)}"
                    )

            if search_provider == "duckduckgo" or (
                search_provider == "tavily" and not formatted_results
            ):
                # DuckDuckGo 검색 (fallback)
                def search_sync():
                    results = []
                    try:
                        with DDGS() as ddgs:
                            search_results = ddgs.text(
                                keywords=query,
                                max_results=max_results,
                                region=region,
                            )
                            results = list(search_results)
                    except Exception as e:
                        logger.error(f"DuckDuckGo search error: {e}")
                        raise
                    return results

                search_results = await asyncio.to_thread(search_sync)

                for result in search_results:
                    formatted_results.append(
                        {
                            "title": result.get("title", ""),
                            "url": result.get("href", ""),
                            "snippet": result.get("body", ""),
                        }
                    )
                source = "duckduckgo"
                logger.info(
                    f"DuckDuckGo search completed: query='{query}', results={len(formatted_results)}"
                )

            if not formatted_results:
                await params.result_callback(
                    {
                        "error": "검색 결과를 찾을 수 없습니다.",
                        "query": query,
                        "source": source,
                    }
                )
                return

            await params.result_callback(
                {
                    "query": query,
                    "count": len(formatted_results),
                    "source": source,
                    "region": region if source == "duckduckgo" else None,
                    "results": formatted_results,
                }
            )

        except Exception as e:
            logger.error(f"Web search error: {e}")
            await params.result_callback(
                {
                    "error": f"웹 검색 중 오류가 발생했습니다: {str(e)}",
                    "query": query,
                }
            )

    return web_search
