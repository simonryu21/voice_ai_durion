"""Social media image search tool using SerpAPI."""

import os
from typing import Any, Dict, List, Optional

import aiohttp
from loguru import logger
from pipecat.adapters.schemas.function_schema import FunctionSchema
from pipecat.services.llm_service import FunctionCallParams

# 소셜 미디어 이미지 검색 함수 스키마 정의
social_image_search_function = FunctionSchema(
    name="search_social_images",
    description="소셜 미디어 플랫폼(Instagram, Pinterest, Twitter, Facebook, LinkedIn, YouTube, TikTok, Reddit 등)에서 이미지를 검색합니다. 특정 주제나 키워드에 대한 이미지를 찾을 때 사용하세요.",
    properties={
        "query": {
            "type": "string",
            "description": "검색할 키워드 (예: '맛집', '여행', '패션').",
        },
        "platform": {
            "type": "string",
            "description": "검색할 플랫폼 ('instagram', 'pinterest', 'twitter', 'facebook', 'linkedin', 'youtube', 'tiktok', 'reddit', 'all' 중 선택, 기본값 'all').",
            "enum": [
                "instagram",
                "pinterest",
                "twitter",
                "facebook",
                "linkedin",
                "youtube",
                "tiktok",
                "reddit",
                "all",
            ],
        },
        "limit": {
            "type": "integer",
            "description": "결과 개수 (1~10 사이, 기본값 5).",
        },
    },
    required=["query"],
)


def create_search_social_images(session_pc_id: Optional[str]):
    """세션별 소셜 미디어 이미지 검색 함수를 생성합니다."""

    async def search_social_images(params: FunctionCallParams):
        """소셜 미디어 플랫폼에서 이미지를 검색합니다."""
        query = params.arguments.get("query")
        platform = params.arguments.get("platform", "all")
        requested_limit = params.arguments.get("limit", 5)

        if not query or not isinstance(query, str):
            await params.result_callback(
                {
                    "error": "검색 키워드를 입력해주세요.",
                }
            )
            return

        query = query.strip()

        # 결과 개수 제한
        try:
            limit = int(requested_limit)
        except (TypeError, ValueError):
            limit = 5
        limit = max(1, min(limit, 10))

        # 플랫폼 유효성 검사
        valid_platforms = [
            "instagram",
            "pinterest",
            "twitter",
            "facebook",
            "linkedin",
            "youtube",
            "tiktok",
            "reddit",
            "all",
        ]
        if platform not in valid_platforms:
            platform = "all"

        # SerpAPI 키 확인
        serp_api_key = os.getenv("SERP_API_KEY")

        if not serp_api_key or serp_api_key == "your_serp_api_key":
            logger.warning("SERP_API_KEY가 설정되지 않았습니다.")
            await params.result_callback(
                {
                    "error": "SERP_API_KEY가 설정되지 않았습니다.",
                    "hint": "SerpAPI에서 API 키를 발급받아 .env 파일에 설정해주세요. https://serpapi.com",
                }
            )
            return

        # SerpAPI 엔드포인트 설정
        base_url = "https://serpapi.com/search.json"

        # 플랫폼별 검색 엔진 설정
        engine_map = {
            "instagram": "google_images",
            "pinterest": "pinterest",
            "twitter": "google_images",
            "facebook": "google_images",
            "linkedin": "google_images",
            "youtube": "google_images",
            "tiktok": "google_images",
            "reddit": "google_images",
            "all": "google_images",
        }

        engine = engine_map.get(platform, "google_images")

        # 검색 쿼리 구성
        if platform == "instagram":
            search_query = f"site:instagram.com {query}"
        elif platform == "pinterest":
            search_query = query  # Pinterest는 자체 엔진 사용
        elif platform == "twitter":
            search_query = f"site:twitter.com {query}"
        elif platform == "facebook":
            search_query = f"site:facebook.com {query}"
        elif platform == "linkedin":
            search_query = f"site:linkedin.com {query}"
        elif platform == "youtube":
            search_query = f"site:youtube.com {query}"
        elif platform == "tiktok":
            search_query = f"site:tiktok.com {query}"
        elif platform == "reddit":
            search_query = f"site:reddit.com {query}"
        else:
            # all: 모든 플랫폼에서 검색
            search_query = f"{query} (instagram OR pinterest OR twitter OR facebook OR linkedin OR youtube OR tiktok OR reddit)"

        query_params = {
            "engine": engine,
            "q": search_query,
            "api_key": serp_api_key,
            "num": limit,
            "hl": "ko",  # 한국어
            "gl": "kr",  # 한국
        }

        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(base_url, params=query_params) as response:
                    if response.status != 200:
                        error_text = await response.text()
                        await params.result_callback(
                            {
                                "error": "소셜 미디어 이미지 검색 API 호출에 실패했습니다.",
                                "status": response.status,
                                "details": error_text,
                            }
                        )
                        return

                    data = await response.json()
                    logger.info(f"SerpAPI response: {data}")

                    # 이미지 결과 추출
                    images = data.get("images_results", [])
                    if not images:
                        # Pinterest의 경우 다른 구조
                        if platform == "pinterest":
                            pins = data.get("pins", [])
                            images = []
                            for pin in pins[:limit]:
                                images.append(
                                    {
                                        "title": pin.get("title", ""),
                                        "link": pin.get("link", ""),
                                        "thumbnail": pin.get("image", ""),
                                        "source": "pinterest",
                                    }
                                )
                        else:
                            await params.result_callback(
                                {
                                    "query": query,
                                    "platform": platform,
                                    "count": 0,
                                    "images": [],
                                    "message": "검색 결과가 없습니다.",
                                }
                            )
                            return

                    # 결과 정리
                    cleaned: List[Dict[str, Any]] = []
                    for img in images[:limit]:
                        cleaned.append(
                            {
                                "title": img.get("title", ""),
                                "url": img.get("link") or img.get("original", ""),
                                "thumbnail": img.get("thumbnail", ""),
                                "source": img.get("source", platform),
                            }
                        )

                    await params.result_callback(
                        {
                            "query": query,
                            "platform": platform,
                            "count": len(cleaned),
                            "images": cleaned,
                        }
                    )

        except Exception as e:
            logger.error(f"Social image search API error: {e}")
            await params.result_callback(
                {
                    "error": f"소셜 미디어 이미지 검색 중 오류가 발생했습니다: {str(e)}",
                }
            )

    return search_social_images
