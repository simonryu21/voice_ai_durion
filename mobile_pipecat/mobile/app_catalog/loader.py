"""YAML catalog loader.

Reads every `*.yaml` file under a directory, validates it against
`CatalogCategory`, and returns a dict keyed by `category` id. Used by
`CandidateBuilder` at session start.
"""

from __future__ import annotations

from pathlib import Path
from typing import Optional

import yaml
from loguru import logger

from app_catalog.schema import CatalogCategory

_DEFAULT_CATALOG_DIR = Path(__file__).resolve().parent


class CatalogLoader:
    """Load and cache the static YAML catalog.

    The loader is intentionally simple: it eagerly reads all YAML files at
    construction time and keeps the parsed objects in memory. The catalog is
    expected to be small (5-7 categories × 2-3 apps) and changes rarely
    (git-managed), so we don't bother with file watchers or lazy loading.
    """

    def __init__(self, catalog_dir: Optional[Path] = None):
        self._dir = catalog_dir or _DEFAULT_CATALOG_DIR
        self._categories: dict[str, CatalogCategory] = {}

    def load(self) -> dict[str, CatalogCategory]:
        """Load every `*.yaml` under the catalog directory.

        Raises:
            ValueError: if a file fails schema validation or two files declare
                the same `category` id.
        """
        self._categories.clear()
        if not self._dir.exists():
            logger.warning(f"AppConcierge catalog directory not found: {self._dir}")
            return {}

        files = sorted(self._dir.glob("*.yaml"))
        for path in files:
            with path.open("r", encoding="utf-8") as f:
                raw = yaml.safe_load(f)
            if raw is None:
                logger.warning(f"AppConcierge catalog file is empty: {path.name}")
                continue
            try:
                category = CatalogCategory.model_validate(raw)
            except Exception as e:
                raise ValueError(f"Invalid catalog file `{path.name}`: {e}") from e

            if category.category in self._categories:
                raise ValueError(
                    f"Duplicate category `{category.category}` declared in `{path.name}`"
                )
            self._categories[category.category] = category
            logger.debug(
                f"AppConcierge catalog loaded: {category.category} "
                f"({len(category.apps)} apps from {path.name})"
            )

        return dict(self._categories)

    @property
    def categories(self) -> dict[str, CatalogCategory]:
        return dict(self._categories)
