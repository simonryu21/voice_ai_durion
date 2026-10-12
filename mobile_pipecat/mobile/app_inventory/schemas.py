"""Pydantic models for the app inventory API."""

from datetime import datetime
from enum import Enum
from typing import Literal, Optional

from pydantic import BaseModel, Field


class AppCategory(str, Enum):
    """App category enum matching the server-side classification."""

    NAVIGATION = "navigation"
    MAPS = "maps"
    SOCIAL_CHAT = "social_chat"
    SOCIAL_OPEN = "social_open"
    GAME = "game"
    ENTERTAINMENT = "entertainment"
    PRODUCTIVITY = "productivity"
    LIFESTYLE = "lifestyle"
    EDUCATION = "education"
    FINANCE = "finance"
    SHOPPING = "shopping"
    HEALTH_FITNESS = "health_fitness"
    TRAVEL_LOCAL = "travel_local"
    FOOD_DRINK = "food_drink"
    PHOTO_VIDEO = "photo_video"
    MUSIC_AUDIO = "music_audio"
    NEWS = "news"
    BOOKS_REFERENCE = "books_reference"
    BUSINESS = "business"
    UTILITIES = "utilities"
    WEATHER = "weather"
    OTHER = "other"
    UNCATEGORIZED = "uncategorized"


CATEGORY_LIMITS: dict[str, int] = {cat.value: 5 for cat in AppCategory}
CATEGORY_LIMITS["other"] = 50
CATEGORY_LIMITS["uncategorized"] = 50

MAPS_AND_NAVIGATION_STORE_CATEGORIES = frozenset({"MAPS_AND_NAVIGATION"})


class AppItemRequest(BaseModel):
    """Single app entry in the inventory upload."""

    package_name: str = Field(..., min_length=1, max_length=255)
    label: Optional[str] = Field(None, max_length=255)
    usage_score: Optional[float] = None
    is_system: Optional[bool] = None


class AppInventoryRequest(BaseModel):
    """Request body for POST /api/v1/users/me/app-inventory."""

    client_ts: datetime
    device_id: str = Field(..., min_length=1, max_length=64)
    platform: Literal["android", "ios"]
    client_usage_stats_collected: bool
    client_sorted: bool
    apps: list[AppItemRequest] = Field(..., min_length=1)


# ---- Response models ----


class AppItemResponse(BaseModel):
    """Single app entry in a snapshot response."""

    package_name: str
    label: Optional[str] = None
    category: str
    usage_score: Optional[float] = None
    sort_order: int
    is_system: Optional[bool] = None


class AppInventorySnapshotResponse(BaseModel):
    """Response for GET /api/v1/users/me/app-inventory."""

    snapshot_id: str
    device_id: str
    platform: str
    client_sorted: bool
    created_at: datetime
    client_ts: datetime
    apps: list[AppItemResponse]
