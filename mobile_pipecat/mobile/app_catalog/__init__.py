"""App catalog package.

Static curated list of apps with their deeplink/applink/intent execution paths,
loaded at startup and merged with each user's app inventory at session start.
"""

from app_catalog.candidate_builder import (
    AppCandidate,
    CandidateBuilder,
    CandidateSet,
    InventoryItem,
)
from app_catalog.loader import CatalogLoader
from app_catalog.prompt import render_candidates_section
from app_catalog.schema import (
    CatalogApp,
    CatalogCategory,
    CatalogParam,
    ExecutionPath,
)

__all__ = [
    "AppCandidate",
    "CandidateBuilder",
    "CandidateSet",
    "CatalogApp",
    "CatalogCategory",
    "CatalogLoader",
    "CatalogParam",
    "ExecutionPath",
    "InventoryItem",
    "render_candidates_section",
]
