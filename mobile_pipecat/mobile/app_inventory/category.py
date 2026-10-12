"""Server-side app category determination pipeline.

Priority order:
  1. app_category_override (manual)
  2. navigation_app_list  (MAPS_AND_NAVIGATION → navigation vs maps)
  3. store_app_category_cache + store_category_mapping
  4. Fallback → 'uncategorized'
"""

from app_inventory.db import AppInventoryDB
from app_inventory.schemas import MAPS_AND_NAVIGATION_STORE_CATEGORIES


class CategoryService:
    """Resolves package names to our internal category enum values."""

    def __init__(self, db: AppInventoryDB):
        self._db = db

    async def determine_categories(
        self, package_names: list[str]
    ) -> tuple[dict[str, str], list[str]]:
        """Batch-determine categories for a list of package names.

        Returns:
            (categories, uncached)
            - categories: {package_name: category_string} for ALL input packages.
            - uncached: package_names that had no cache entry and should be scraped.
        """
        if not package_names:
            return {}, []

        categories: dict[str, str] = {}

        # --- 1. Manual overrides (highest priority) ---
        overrides = await self._db.get_category_overrides(package_names)
        categories.update(overrides)

        remaining = [p for p in package_names if p not in categories]
        if not remaining:
            return categories, []

        # --- 2. Store cache + mapping ---
        cached = await self._db.get_cached_categories(remaining)

        needs_nav_check: list[str] = []
        for pkg, (store_cat, mapped_cat) in cached.items():
            if store_cat in MAPS_AND_NAVIGATION_STORE_CATEGORIES:
                needs_nav_check.append(pkg)
            elif mapped_cat:
                categories[pkg] = mapped_cat
            else:
                categories[pkg] = "other"

        # --- 3. Navigation vs maps disambiguation ---
        if needs_nav_check:
            nav_set = await self._db.get_navigation_apps(needs_nav_check)
            for pkg in needs_nav_check:
                categories[pkg] = "navigation" if pkg in nav_set else "maps"

        # --- 4. Fallback: uncategorized (queue for scraping) ---
        uncached: list[str] = []
        for pkg in remaining:
            if pkg not in categories:
                categories[pkg] = "uncategorized"
                uncached.append(pkg)

        return categories, uncached
