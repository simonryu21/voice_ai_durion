"""Candidate builder.

Merges the static catalog with a user's app inventory and produces a
`CandidateSet` — for each catalog category, up to 2 apps that the
Supervisor LLM should consider when routing utterances to AppConcierge.

See `app-concierge-agent-design.md` §4.2 / decision #3.
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Iterable, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field

from app_catalog.schema import (
    CatalogApp,
    CatalogCategory,
    CatalogParam,
    ExecutionPath,
    Platform,
)


class InventoryItem(BaseModel):
    """Minimal shape required from the user's app inventory.

    The full inventory row has more fields (label, category, sort_order, ...);
    only the ones used for matching/scoring are declared here so that callers
    can pass dicts straight from `app_inventory.db.get_snapshot_items()`.
    """

    model_config = ConfigDict(extra="ignore")

    package_name: str
    usage_score: Optional[float] = None
    label: Optional[str] = None


class AppCandidate(BaseModel):
    """One app eligible to be invoked for a given category."""

    model_config = ConfigDict(extra="forbid")

    app_id: str
    display_name: str
    package_name: str
    category: str
    use_when: str
    examples: list[str]
    parameters: list[CatalogParam]
    execution: dict[Platform, list[ExecutionPath]]
    popularity_rank: int
    source: Literal["user_inventory", "popular_default"]
    user_usage_score: Optional[float] = None


class CandidateSet(BaseModel):
    """The result of merging catalog + inventory for one user."""

    model_config = ConfigDict(extra="forbid")

    user_id: Optional[str] = None
    platform: Platform
    generated_at: datetime
    by_category: dict[str, list[AppCandidate]] = Field(default_factory=dict)

    def all_apps(self) -> list[AppCandidate]:
        return [a for apps in self.by_category.values() for a in apps]


class CandidateBuilder:
    """Build a per-user candidate set from catalog + inventory.

    Algorithm (decision #3 — *user inventory first, popular fills the rest*):

    1. For each catalog category, find apps whose `package_name` is in the
       user's inventory; sort by inventory `usage_score` descending.
    2. Take the top N (default 2) as user_inventory candidates.
    3. If fewer than N, fill the gap with the catalog's remaining apps
       sorted by `popularity_rank` ascending; mark `source=popular_default`.
    4. Skip apps that have no execution paths for the target platform.
    """

    DEFAULT_PER_CATEGORY = 2

    def __init__(
        self, catalog: dict[str, CatalogCategory], per_category: int = DEFAULT_PER_CATEGORY
    ):
        self._catalog = catalog
        self._per_category = per_category

    def build(
        self,
        user_id: Optional[str],
        platform: Platform,
        inventory: Iterable[InventoryItem | dict],
    ) -> CandidateSet:
        normalized = self._normalize_inventory(inventory)
        score_by_pkg = {i.package_name: (i.usage_score or 0.0) for i in normalized}

        by_category: dict[str, list[AppCandidate]] = {}
        for cat_id, category in self._catalog.items():
            installed = [a for a in category.apps if a.package_name in score_by_pkg]
            installed.sort(key=lambda a: -score_by_pkg.get(a.package_name, 0.0))

            chosen: list[tuple[CatalogApp, str]] = [(a, "user_inventory") for a in installed]

            if len(chosen) < self._per_category:
                remaining_ids = {a.app_id for a, _ in chosen}
                popular = sorted(
                    (a for a in category.apps if a.app_id not in remaining_ids),
                    key=lambda a: a.popularity_rank,
                )
                fill = self._per_category - len(chosen)
                chosen.extend((a, "popular_default") for a in popular[:fill])

            chosen = [(a, src) for a, src in chosen if platform in a.execution]
            chosen = chosen[: self._per_category]

            by_category[cat_id] = [
                AppCandidate(
                    app_id=a.app_id,
                    display_name=a.display_name,
                    package_name=a.package_name,
                    category=cat_id,
                    use_when=a.use_when,
                    examples=a.examples,
                    parameters=a.parameters,
                    execution=a.execution,
                    popularity_rank=a.popularity_rank,
                    source=src,
                    user_usage_score=(
                        score_by_pkg.get(a.package_name) if src == "user_inventory" else None
                    ),
                )
                for a, src in chosen
            ]

        return CandidateSet(
            user_id=user_id,
            platform=platform,
            generated_at=datetime.now(timezone.utc),
            by_category=by_category,
        )

    @staticmethod
    def _normalize_inventory(items: Iterable[InventoryItem | dict]) -> list[InventoryItem]:
        out: list[InventoryItem] = []
        for it in items:
            if isinstance(it, InventoryItem):
                out.append(it)
            else:
                out.append(InventoryItem.model_validate(it))
        return out
