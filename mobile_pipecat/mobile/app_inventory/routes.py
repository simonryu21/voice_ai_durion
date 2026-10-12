"""FastAPI routes for app inventory."""

from collections import defaultdict
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from loguru import logger

from app_inventory.category import CategoryService
from app_inventory.db import app_inventory_db
from app_inventory.schemas import (
    CATEGORY_LIMITS,
    AppInventoryRequest,
    AppInventorySnapshotResponse,
    AppItemResponse,
)
from app_inventory.scraper import PlayStoreScraper
from auth.middleware import require_auth

_category_service = CategoryService(app_inventory_db)
_scraper = PlayStoreScraper(app_inventory_db)


def get_scraper() -> PlayStoreScraper:
    return _scraper


async def _ensure_app_inventory_backend() -> None:
    """If startup ``connect()`` failed (e.g. Postgres was down), establish pool here.

    Also starts the Play Store scraper worker when it was never started at startup.
    Router-level dependency avoids adding this to every new DB method.
    """
    try:
        await app_inventory_db.connect()
        get_scraper().start()
    except ValueError as e:
        logger.error(f"App inventory configuration error: {e}")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Server is not configured for app inventory (DATABASE_URL).",
        ) from e
    except Exception as e:
        logger.warning(f"App inventory database unavailable: {e}")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Database temporarily unavailable. Retry later.",
        ) from e


router = APIRouter(
    prefix="/api/v1/users/me",
    tags=["app-inventory"],
    dependencies=[Depends(_ensure_app_inventory_backend)],
)


def setup_app_inventory_routes(app):
    """Register the app-inventory router on the FastAPI app."""
    app.include_router(router)


@router.post(
    "/app-inventory",
    status_code=status.HTTP_204_NO_CONTENT,
    response_class=Response,
)
async def upload_app_inventory(
    body: AppInventoryRequest,
    user_id: str = Depends(require_auth),
):
    """Receive an app inventory snapshot from a client device.

    Flow:
      1. Determine category for every app (override → cache → fallback).
      2. If client_sorted=true and device not yet counted → update popularity.
      3. If client_sorted=false → sort within each category by popularity.
      4. Trim to top-N per category.
      5. Save snapshot + items.
      6. Enqueue uncached packages for background scraping.
    """
    package_names = [a.package_name for a in body.apps]

    # --- 1. Category determination ---
    categories, uncached = await _category_service.determine_categories(package_names)

    # --- 2. Popularity counting (client_sorted=true, first time per device) ---
    if body.client_sorted:
        already_counted = await app_inventory_db.is_device_counted(body.device_id)
        if not already_counted:
            counter_apps = [
                {"package_name": a.package_name, "usage_score": a.usage_score} for a in body.apps
            ]
            await app_inventory_db.update_popularity_counters(counter_apps, body.device_id)
            logger.info(
                f"Popularity counters updated for device={body.device_id} "
                f"({len(counter_apps)} apps)"
            )

    # Build per-app enriched dicts
    app_dicts = []
    for idx, a in enumerate(body.apps):
        app_dicts.append(
            {
                "package_name": a.package_name,
                "label": a.label,
                "category": categories.get(a.package_name, "uncategorized"),
                "usage_score": a.usage_score,
                "is_system": a.is_system,
                "original_order": idx,
            }
        )

    # --- 3. Server-side sorting for client_sorted=false ---
    if not body.client_sorted:
        pop_scores = await app_inventory_db.get_popularity_scores(package_names)
        for d in app_dicts:
            d["_pop"] = pop_scores.get(d["package_name"], 0.0)
        # Stable sort by popularity descending within each category
        app_dicts.sort(key=lambda d: (-d["_pop"], d["original_order"]))

    # --- 4. Group by category & trim ---
    by_category: dict[str, list[dict]] = defaultdict(list)
    for d in app_dicts:
        by_category[d["category"]].append(d)

    items_to_save: list[dict] = []
    global_order = 0
    for cat, cat_apps in by_category.items():
        limit = CATEGORY_LIMITS.get(cat, 5)
        for d in cat_apps[:limit]:
            items_to_save.append(
                {
                    "package_name": d["package_name"],
                    "label": d["label"],
                    "category": cat,
                    "usage_score": d["usage_score"],
                    "sort_order": global_order,
                    "is_system": d.get("is_system", False),
                }
            )
            global_order += 1

    # --- 5. Persist snapshot ---
    snapshot_id = await app_inventory_db.save_snapshot(
        user_id=user_id,
        device_id=body.device_id,
        platform=body.platform,
        client_sorted=body.client_sorted,
        client_ts=body.client_ts,
        items=items_to_save,
    )
    logger.info(
        f"Saved app inventory snapshot={snapshot_id} user={user_id} "
        f"device={body.device_id} items={len(items_to_save)}"
    )

    # --- 6. Enqueue uncached packages for scraping ---
    if uncached:
        _scraper.enqueue(uncached)
        logger.debug(f"Enqueued {len(uncached)} packages for scraping")

    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get(
    "/app-inventory",
    response_model=AppInventorySnapshotResponse,
)
async def get_app_inventory(
    category: Optional[str] = Query(None, description="Filter by category"),
    user_id: str = Depends(require_auth),
):
    """Return the latest app inventory snapshot for the authenticated user."""
    snapshot = await app_inventory_db.get_latest_snapshot(user_id)
    if not snapshot:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No app inventory found",
        )

    items = await app_inventory_db.get_snapshot_items(snapshot["id"], category)

    return AppInventorySnapshotResponse(
        snapshot_id=str(snapshot["id"]),
        device_id=snapshot["device_id"],
        platform=snapshot["platform"],
        client_sorted=snapshot["client_sorted"],
        created_at=snapshot["created_at"],
        client_ts=snapshot["client_ts"],
        apps=[
            AppItemResponse(
                package_name=it["package_name"],
                label=it.get("label"),
                category=it["category"],
                usage_score=it.get("usage_score"),
                sort_order=it["sort_order"],
                is_system=it.get("is_system"),
            )
            for it in items
        ],
    )
