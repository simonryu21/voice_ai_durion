"""Database operations for app inventory."""

import os
from typing import Optional

import asyncpg
from loguru import logger


class AppInventoryDB:
    """Database access layer for app inventory tables.

    Uses its own asyncpg connection pool (shares DATABASE_URL with auth module).
    """

    def __init__(self):
        self._pool: Optional[asyncpg.Pool] = None

    async def connect(self):
        """Initialize connection pool from DATABASE_URL."""
        if self._pool is not None:
            return

        database_url = os.getenv("DATABASE_URL")
        if not database_url:
            raise ValueError("DATABASE_URL is not set.")

        self._pool = await asyncpg.create_pool(
            database_url,
            min_size=1,
            max_size=10,
            command_timeout=60,
        )
        logger.info("AppInventoryDB connection pool created")

    async def close(self):
        """Close connection pool."""
        if self._pool:
            await self._pool.close()
            self._pool = None
            logger.info("AppInventoryDB connection pool closed")

    # ------------------------------------------------------------------
    # Category determination queries
    # ------------------------------------------------------------------

    async def get_category_overrides(self, package_names: list[str]) -> dict[str, str]:
        """Batch lookup in app_category_override. Returns {package_name: mapped_category}."""
        async with self._pool.acquire() as conn:
            rows = await conn.fetch(
                """
                SELECT package_name, mapped_category
                FROM app_category_override
                WHERE package_name = ANY($1::text[])
                """,
                package_names,
            )
        return {r["package_name"]: r["mapped_category"] for r in rows}

    async def get_cached_categories(
        self, package_names: list[str], store: str = "google_play"
    ) -> dict[str, tuple[Optional[str], Optional[str]]]:
        """Batch lookup in store_app_category_cache joined with store_category_mapping.

        Returns {package_name: (store_category, mapped_category)}.
        mapped_category may be None if no mapping rule exists.
        """
        async with self._pool.acquire() as conn:
            rows = await conn.fetch(
                """
                SELECT c.package_name,
                       c.store_category,
                       COALESCE(m.mapped_category, c.mapped_category) AS mapped_category
                FROM store_app_category_cache c
                LEFT JOIN store_category_mapping m
                       ON m.store = c.store AND m.store_category = c.store_category
                WHERE c.package_name = ANY($1::text[])
                  AND c.store = $2
                """,
                package_names,
                store,
            )
        return {r["package_name"]: (r["store_category"], r["mapped_category"]) for r in rows}

    async def get_navigation_apps(self, package_names: list[str]) -> set[str]:
        """Return subset of package_names that are in navigation_app_list."""
        async with self._pool.acquire() as conn:
            rows = await conn.fetch(
                """
                SELECT package_name
                FROM navigation_app_list
                WHERE package_name = ANY($1::text[])
                """,
                package_names,
            )
        return {r["package_name"] for r in rows}

    async def upsert_category_cache(
        self,
        package_name: str,
        store: str,
        store_category: Optional[str],
        mapped_category: Optional[str],
    ):
        """Insert or update a row in store_app_category_cache."""
        async with self._pool.acquire() as conn:
            await conn.execute(
                """
                INSERT INTO store_app_category_cache
                    (package_name, store, store_category, mapped_category, fetched_at)
                VALUES ($1, $2, $3, $4, now())
                ON CONFLICT (package_name, store)
                DO UPDATE SET store_category  = EXCLUDED.store_category,
                              mapped_category = EXCLUDED.mapped_category,
                              fetched_at      = now()
                """,
                package_name,
                store,
                store_category,
                mapped_category,
            )

    # ------------------------------------------------------------------
    # Popularity counting
    # ------------------------------------------------------------------

    async def is_device_counted(self, device_id: str) -> bool:
        """Check if device_id has already been counted."""
        async with self._pool.acquire() as conn:
            row = await conn.fetchrow(
                "SELECT 1 FROM app_popularity_device_log WHERE device_id = $1",
                device_id,
            )
        return row is not None

    async def update_popularity_counters(self, apps: list[dict], device_id: str):
        """Atomically increment popularity counters for each app and log device_id.

        Args:
            apps: List of dicts with 'package_name' and optional 'usage_score'.
            device_id: Device identifier for dedup.
        """
        async with self._pool.acquire() as conn:
            async with conn.transaction():
                rechecked = await conn.fetchrow(
                    "SELECT 1 FROM app_popularity_device_log WHERE device_id = $1",
                    device_id,
                )
                if rechecked:
                    return

                await conn.execute(
                    "INSERT INTO app_popularity_device_log (device_id) VALUES ($1)",
                    device_id,
                )

                for app in apps:
                    score = app.get("usage_score") or 0.0
                    await conn.execute(
                        """
                        INSERT INTO app_popularity_counter (package_name, device_count, total_score)
                        VALUES ($1, 1, $2)
                        ON CONFLICT (package_name)
                        DO UPDATE SET device_count = app_popularity_counter.device_count + 1,
                                      total_score  = app_popularity_counter.total_score + $2
                        """,
                        app["package_name"],
                        float(score),
                    )

    async def get_popularity_scores(self, package_names: list[str]) -> dict[str, float]:
        """Return {package_name: total_score} from app_popularity_counter."""
        if not package_names:
            return {}
        async with self._pool.acquire() as conn:
            rows = await conn.fetch(
                """
                SELECT package_name, total_score
                FROM app_popularity_counter
                WHERE package_name = ANY($1::text[])
                """,
                package_names,
            )
        return {r["package_name"]: float(r["total_score"]) for r in rows}

    # ------------------------------------------------------------------
    # Snapshot storage
    # ------------------------------------------------------------------

    async def save_snapshot(
        self,
        user_id: str,
        device_id: str,
        platform: str,
        client_sorted: bool,
        client_ts,
        items: list[dict],
    ) -> str:
        """Save a snapshot header and its items. Returns snapshot UUID."""
        async with self._pool.acquire() as conn:
            async with conn.transaction():
                row = await conn.fetchrow(
                    """
                    INSERT INTO user_app_inventory_snapshot
                        (user_id, device_id, platform, client_sorted, client_ts)
                    VALUES ($1, $2, $3, $4, $5)
                    RETURNING id
                    """,
                    user_id,
                    device_id,
                    platform,
                    client_sorted,
                    client_ts,
                )
                snapshot_id = row["id"]

                if items:
                    records = [
                        (
                            snapshot_id,
                            it["package_name"],
                            it.get("label"),
                            it["category"],
                            it.get("usage_score"),
                            it["sort_order"],
                            it.get("is_system", False),
                        )
                        for it in items
                    ]
                    await conn.executemany(
                        """
                        INSERT INTO user_app_inventory_item
                            (snapshot_id, package_name, label, category,
                             usage_score, sort_order, is_system)
                        VALUES ($1, $2, $3, $4, $5, $6, $7)
                        """,
                        records,
                    )

        return str(snapshot_id)

    # ------------------------------------------------------------------
    # Mapping lookup (for scraper to resolve store_category → mapped_category)
    # ------------------------------------------------------------------

    # ------------------------------------------------------------------
    # Query (read) operations
    # ------------------------------------------------------------------

    async def get_latest_snapshot(self, user_id: str) -> Optional[dict]:
        """Return the most recent snapshot header for a user."""
        async with self._pool.acquire() as conn:
            row = await conn.fetchrow(
                """
                SELECT id, user_id, device_id, platform, client_sorted,
                       created_at, client_ts
                FROM user_app_inventory_snapshot
                WHERE user_id = $1
                ORDER BY created_at DESC
                LIMIT 1
                """,
                user_id,
            )
        return dict(row) if row else None

    async def get_snapshot_items(self, snapshot_id, category: Optional[str] = None) -> list[dict]:
        """Return items for a snapshot, optionally filtered by category."""
        async with self._pool.acquire() as conn:
            if category:
                rows = await conn.fetch(
                    """
                    SELECT package_name, label, category, usage_score,
                           sort_order, is_system
                    FROM user_app_inventory_item
                    WHERE snapshot_id = $1 AND category = $2
                    ORDER BY sort_order
                    """,
                    snapshot_id,
                    category,
                )
            else:
                rows = await conn.fetch(
                    """
                    SELECT package_name, label, category, usage_score,
                           sort_order, is_system
                    FROM user_app_inventory_item
                    WHERE snapshot_id = $1
                    ORDER BY sort_order
                    """,
                    snapshot_id,
                )
        return [dict(r) for r in rows]

    # ------------------------------------------------------------------
    # Mapping lookup (for scraper to resolve store_category → mapped_category)
    # ------------------------------------------------------------------

    async def get_mapped_category(self, store: str, store_category: str) -> Optional[str]:
        """Look up mapped_category from store_category_mapping."""
        async with self._pool.acquire() as conn:
            row = await conn.fetchrow(
                """
                SELECT mapped_category
                FROM store_category_mapping
                WHERE store = $1 AND store_category = $2
                """,
                store,
                store_category,
            )
        return row["mapped_category"] if row else None


app_inventory_db = AppInventoryDB()
