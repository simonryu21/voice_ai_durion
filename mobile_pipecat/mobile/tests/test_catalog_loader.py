"""Tests for app_catalog.loader."""

import textwrap
from pathlib import Path

import pytest

from app_catalog.loader import CatalogLoader


def _write(tmp_path: Path, name: str, contents: str) -> None:
    (tmp_path / name).write_text(textwrap.dedent(contents).lstrip(), encoding="utf-8")


def test_load_empty_dir(tmp_path: Path):
    loader = CatalogLoader(catalog_dir=tmp_path)
    assert loader.load() == {}


def test_load_missing_dir(tmp_path: Path):
    loader = CatalogLoader(catalog_dir=tmp_path / "does_not_exist")
    assert loader.load() == {}


def test_load_valid_file(tmp_path: Path):
    _write(
        tmp_path,
        "navigation.yaml",
        """
        category: navigation
        display_name: 지도/길찾기
        apps:
          - app_id: naver_map
            display_name: 네이버 지도
            package_name: com.nhn.android.nmap
            popularity_rank: 1
            use_when: 길찾기, 대중교통 경로 검색.
            examples:
              - 강남역 가는 길
            parameters:
              - name: query
                type: string
                required: true
            execution:
              android:
                - type: applink
                  priority: 1
                  template: "https://map.naver.com/v5/search/{query}"
        """,
    )
    cats = CatalogLoader(catalog_dir=tmp_path).load()
    assert "navigation" in cats
    assert cats["navigation"].apps[0].app_id == "naver_map"


def test_load_invalid_file_raises(tmp_path: Path):
    _write(
        tmp_path,
        "broken.yaml",
        """
        category: broken
        display_name: x
        apps:
          - app_id: x
            display_name: x
            package_name: x
            popularity_rank: 1
            use_when: x
            execution:
              android:
                - type: applink
                  priority: 1
                  # missing template
        """,
    )
    with pytest.raises(ValueError, match="Invalid catalog file"):
        CatalogLoader(catalog_dir=tmp_path).load()


def test_load_rejects_duplicate_categories(tmp_path: Path):
    body = """
        category: shared
        display_name: x
        apps:
          - app_id: x
            display_name: x
            package_name: com.x
            popularity_rank: 1
            use_when: x
            execution:
              android:
                - type: applink
                  priority: 1
                  template: "https://x"
    """
    _write(tmp_path, "a.yaml", body)
    _write(tmp_path, "b.yaml", body)
    with pytest.raises(ValueError, match="Duplicate category"):
        CatalogLoader(catalog_dir=tmp_path).load()


def test_load_skips_empty_files(tmp_path: Path):
    (tmp_path / "empty.yaml").write_text("", encoding="utf-8")
    assert CatalogLoader(catalog_dir=tmp_path).load() == {}


def test_default_dir_loads_real_catalog():
    """When pointed at the actual mobile/app_catalog/ dir, loader must succeed.

    This is a structural sanity check — it doesn't assert content, just that
    every shipped YAML parses cleanly.
    """
    loader = CatalogLoader()
    cats = loader.load()
    # may be empty until step 4 lands; just ensure no exception
    assert isinstance(cats, dict)
