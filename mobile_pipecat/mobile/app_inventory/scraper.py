"""Background Google Play Store category scraper.

Queues package names that have no cached category and asynchronously
fetches their Play Store genre via google-play-scraper, then stores
the result in store_app_category_cache.
"""

import asyncio
from typing import Optional

from loguru import logger

from app_inventory.db import AppInventoryDB

try:
    from google_play_scraper import app as gp_app

    _SCRAPER_AVAILABLE = True
except ImportError:
    _SCRAPER_AVAILABLE = False
    gp_app = None  # type: ignore[assignment]
    logger.warning("google-play-scraper not installed; category scraping disabled")

try:
    from google_play_scraper.exceptions import NotFoundError as _NotFoundError
except Exception:
    _NotFoundError = type("NotFoundError", (Exception,), {})


class PlayStoreScraper:
    """Async background worker that scrapes Play Store categories.

    Uses an asyncio.Queue internally. Call ``enqueue()`` to add packages
    and ``start()`` / ``stop()`` to manage the background worker task.
    """

    STORE = "google_play"
    BATCH_DELAY_SECONDS = 0.5

    def __init__(self, db: AppInventoryDB, max_queue_size: int = 5000):
        self._db = db
        self._queue: asyncio.Queue[str] = asyncio.Queue(maxsize=max_queue_size)
        self._worker_task: Optional[asyncio.Task] = None
        self._seen: set[str] = set()

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------

    def start(self):
        """Spawn the background worker (idempotent)."""
        if not _SCRAPER_AVAILABLE:
            logger.info("Scraper worker not started (library unavailable)")
            return
        if self._worker_task is None or self._worker_task.done():
            self._worker_task = asyncio.create_task(self._worker(), name="play-store-scraper")
            logger.info("Play Store scraper worker started")

    async def stop(self):
        """Cancel the worker and wait for it to finish."""
        if self._worker_task and not self._worker_task.done():
            self._worker_task.cancel()
            try:
                await self._worker_task
            except asyncio.CancelledError:
                pass
            logger.info("Play Store scraper worker stopped")

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def enqueue(self, package_names: list[str]):
        """Add package names to the scrape queue (non-blocking, deduped)."""
        if not _SCRAPER_AVAILABLE:
            return
        for pkg in package_names:
            if pkg in self._seen:
                continue
            self._seen.add(pkg)
            try:
                self._queue.put_nowait(pkg)
            except asyncio.QueueFull:
                logger.warning(f"Scrape queue full, dropping {pkg}")

    # ------------------------------------------------------------------
    # Worker
    # ------------------------------------------------------------------

    async def _worker(self):
        """Continuously pull from the queue and scrape."""
        while True:
            pkg = await self._queue.get()
            try:
                await self._scrape_one(pkg)
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.opt(exception=True).warning(f"Failed to scrape {pkg}")
            finally:
                self._queue.task_done()
            await asyncio.sleep(self.BATCH_DELAY_SECONDS)

    async def _scrape_one(self, package_name: str):
        """Fetch a single app's genre from Play Store and cache it."""
        try:
            result = await asyncio.to_thread(gp_app, package_name, lang="en", country="us")
        except _NotFoundError:
            logger.debug(f"Package {package_name} not found on Play Store")
            await self._db.upsert_category_cache(package_name, self.STORE, None, None)
            return

        store_category = result.get("genreId")
        mapped = None
        if store_category:
            mapped = await self._db.get_mapped_category(self.STORE, store_category)

        await self._db.upsert_category_cache(package_name, self.STORE, store_category, mapped)
        logger.debug(f"Scraped {package_name}: store_category={store_category}, mapped={mapped}")
