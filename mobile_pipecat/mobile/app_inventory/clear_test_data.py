"""Clear app inventory PostgreSQL data for local / QA testing.

``POST /api/v1/users/me/app-inventory`` (upload) touches:

- **user_app_inventory_snapshot** + **user_app_inventory_item** — saved snapshot (items CASCADE).
- **app_popularity_device_log** — one row per device after first ``client_sorted=true`` upload.
- **app_popularity_counter** — per-package crowdsourced scores (updated on that first upload).
- **store_app_category_cache** — Play Store genre cache (category step + background scraper).

Reference / seed data (do **not** delete for normal tests):

- **store_category_mapping**, **app_category_override**, **navigation_app_list**

Run from ``mobile/``::

    uv run python app_inventory/clear_test_data.py --full
    uv run python app_inventory/clear_test_data.py --user-id <uuid>
    uv run python app_inventory/clear_test_data.py --device-id <device_id>
"""

from __future__ import annotations

import argparse
import asyncio
import os
import sys
from pathlib import Path

import asyncpg
from dotenv import load_dotenv


def _load_env() -> None:
    env_path = Path(__file__).resolve().parent.parent / ".env"
    load_dotenv(env_path, override=True)


async def _run(args: argparse.Namespace) -> None:
    _load_env()
    dsn = os.getenv("DATABASE_URL")
    if not dsn:
        print("DATABASE_URL is not set.", file=sys.stderr)
        sys.exit(1)

    conn = await asyncpg.connect(dsn)
    try:
        if args.full:
            await conn.execute("DELETE FROM user_app_inventory_snapshot;")
            await conn.execute("DELETE FROM app_popularity_device_log;")
            await conn.execute("DELETE FROM app_popularity_counter;")
            if not args.keep_scrape_cache:
                await conn.execute("DELETE FROM store_app_category_cache;")
            print(
                "Cleared: snapshots (+items), popularity logs/counters"
                + ("" if args.keep_scrape_cache else ", store_app_category_cache")
            )
        else:
            if args.user_id:
                n = await conn.execute(
                    "DELETE FROM user_app_inventory_snapshot WHERE user_id = $1;",
                    args.user_id,
                )
                print(f"Deleted snapshots for user_id={args.user_id} ({n})")
            if args.device_id:
                n = await conn.execute(
                    "DELETE FROM app_popularity_device_log WHERE device_id = $1;",
                    args.device_id,
                )
                print(f"Deleted device log for device_id={args.device_id} ({n})")
            if not args.user_id and not args.device_id and not args.reset_popularity:
                print(
                    "Specify --full, or --user-id, or --device-id, or --reset-popularity.",
                    file=sys.stderr,
                )
                sys.exit(2)

        if args.reset_popularity and not args.full:
            await conn.execute("DELETE FROM app_popularity_device_log;")
            await conn.execute("DELETE FROM app_popularity_counter;")
            print("Cleared: app_popularity_device_log, app_popularity_counter")
    finally:
        await conn.close()


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument(
        "--full",
        action="store_true",
        help="Delete all snapshots, popularity data, and scrape cache (full 'never uploaded' reset).",
    )
    p.add_argument("--user-id", metavar="UUID", help="Delete only snapshots for this user.")
    p.add_argument(
        "--device-id",
        metavar="ID",
        help="Delete only app_popularity_device_log row (device can be counted again).",
    )
    p.add_argument(
        "--keep-scrape-cache",
        action="store_true",
        help="With --full, keep store_app_category_cache (faster re-tests).",
    )
    p.add_argument(
        "--reset-popularity",
        action="store_true",
        help="With partial mode: also truncate all popularity counters and device logs.",
    )
    args = p.parse_args()
    asyncio.run(_run(args))


if __name__ == "__main__":
    main()
