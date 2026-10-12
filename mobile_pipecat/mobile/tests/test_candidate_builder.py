"""Tests for app_catalog.candidate_builder."""

from app_catalog.candidate_builder import CandidateBuilder, InventoryItem
from app_catalog.schema import CatalogApp, CatalogCategory, ExecutionPath


def _make_app(
    app_id: str,
    package_name: str,
    popularity_rank: int = 1,
    *,
    android: bool = True,
    ios: bool = True,
) -> CatalogApp:
    execution = {}
    if android:
        execution["android"] = [
            ExecutionPath(type="applink", priority=1, template=f"https://x/{app_id}")
        ]
    if ios:
        execution["ios"] = [
            ExecutionPath(type="universal_link", priority=1, template=f"https://x/{app_id}")
        ]
    return CatalogApp(
        app_id=app_id,
        display_name=app_id.title(),
        package_name=package_name,
        popularity_rank=popularity_rank,
        use_when=f"Use {app_id} when ...",
        examples=[f"열어 {app_id}"],
        execution=execution,
    )


def _catalog(*apps_per_cat: tuple[str, list[CatalogApp]]) -> dict[str, CatalogCategory]:
    return {
        cat: CatalogCategory(category=cat, display_name=cat, apps=apps)
        for cat, apps in apps_per_cat
    }


def test_empty_inventory_picks_top_2_by_popularity():
    catalog = _catalog(
        (
            "navigation",
            [
                _make_app("a", "com.a", popularity_rank=2),
                _make_app("b", "com.b", popularity_rank=1),
                _make_app("c", "com.c", popularity_rank=3),
            ],
        )
    )
    result = CandidateBuilder(catalog).build(user_id="u", platform="android", inventory=[])
    nav = result.by_category["navigation"]
    assert [c.app_id for c in nav] == ["b", "a"]
    assert all(c.source == "popular_default" for c in nav)
    assert all(c.user_usage_score is None for c in nav)


def test_one_app_installed_then_popular_fills():
    catalog = _catalog(
        (
            "navigation",
            [
                _make_app("a", "com.a", popularity_rank=1),
                _make_app("b", "com.b", popularity_rank=2),
                _make_app("c", "com.c", popularity_rank=3),
            ],
        )
    )
    inventory = [InventoryItem(package_name="com.b", usage_score=10.0)]
    result = CandidateBuilder(catalog).build("u", "android", inventory)
    nav = result.by_category["navigation"]
    assert [c.app_id for c in nav] == ["b", "a"]
    assert nav[0].source == "user_inventory"
    assert nav[0].user_usage_score == 10.0
    assert nav[1].source == "popular_default"
    assert nav[1].user_usage_score is None


def test_two_apps_installed_skip_popular():
    catalog = _catalog(
        (
            "navigation",
            [
                _make_app("a", "com.a", popularity_rank=1),
                _make_app("b", "com.b", popularity_rank=2),
                _make_app("c", "com.c", popularity_rank=3),
            ],
        )
    )
    inventory = [
        InventoryItem(package_name="com.a", usage_score=5.0),
        InventoryItem(package_name="com.c", usage_score=20.0),
    ]
    result = CandidateBuilder(catalog).build("u", "android", inventory)
    nav = result.by_category["navigation"]
    assert [c.app_id for c in nav] == ["c", "a"]
    assert all(c.source == "user_inventory" for c in nav)


def test_three_installed_takes_top_two_by_usage():
    catalog = _catalog(
        (
            "navigation",
            [
                _make_app("a", "com.a"),
                _make_app("b", "com.b"),
                _make_app("c", "com.c"),
            ],
        )
    )
    inventory = [
        InventoryItem(package_name="com.a", usage_score=1.0),
        InventoryItem(package_name="com.b", usage_score=3.0),
        InventoryItem(package_name="com.c", usage_score=2.0),
    ]
    nav = CandidateBuilder(catalog).build("u", "android", inventory).by_category["navigation"]
    assert [c.app_id for c in nav] == ["b", "c"]


def test_inventory_with_unknown_packages_is_ignored():
    catalog = _catalog(
        ("navigation", [_make_app("a", "com.a"), _make_app("b", "com.b", popularity_rank=2)]),
    )
    inventory = [
        InventoryItem(package_name="com.unrelated", usage_score=999.0),
    ]
    nav = CandidateBuilder(catalog).build("u", "android", inventory).by_category["navigation"]
    assert [c.app_id for c in nav] == ["a", "b"]
    assert all(c.source == "popular_default" for c in nav)


def test_dict_inventory_accepted():
    catalog = _catalog(("navigation", [_make_app("a", "com.a")]))
    nav = (
        CandidateBuilder(catalog)
        .build("u", "android", [{"package_name": "com.a", "usage_score": 1.0}])
        .by_category["navigation"]
    )
    assert nav[0].source == "user_inventory"


def test_platform_filters_apps_without_paths():
    catalog = _catalog(
        (
            "navigation",
            [
                _make_app("only_android", "com.x", android=True, ios=False, popularity_rank=1),
                _make_app("only_ios", "com.y", android=False, ios=True, popularity_rank=2),
            ],
        )
    )
    nav = CandidateBuilder(catalog).build("u", "android", []).by_category["navigation"]
    assert [c.app_id for c in nav] == ["only_android"]


def test_per_category_limit_overridable():
    catalog = _catalog(
        (
            "navigation",
            [_make_app(f"a{i}", f"com.a{i}", popularity_rank=i + 1) for i in range(5)],
        )
    )
    nav = (
        CandidateBuilder(catalog, per_category=3)
        .build("u", "android", [])
        .by_category["navigation"]
    )
    assert len(nav) == 3
    assert [c.app_id for c in nav] == ["a0", "a1", "a2"]


def test_category_with_only_one_app_returns_one():
    catalog = _catalog(("navigation", [_make_app("solo", "com.solo")]))
    nav = CandidateBuilder(catalog).build("u", "android", []).by_category["navigation"]
    assert len(nav) == 1


def test_real_catalog_smoke():
    """Build candidates against the shipped catalog with no inventory."""
    from app_catalog.loader import CatalogLoader

    catalog = CatalogLoader().load()
    result = CandidateBuilder(catalog).build("u", "android", [])
    # All shipped categories must appear with at least one candidate
    assert set(result.by_category.keys()) == set(catalog.keys())
    for cat, cands in result.by_category.items():
        assert 1 <= len(cands) <= 2, f"{cat}: {cands}"
        assert all(c.source == "popular_default" for c in cands)
