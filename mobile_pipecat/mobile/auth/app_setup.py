"""Setup OAuth authentication and app inventory for FastAPI app."""

import asyncio
import os

from fastapi import Request, status
from fastapi.responses import JSONResponse
from loguru import logger

from app_inventory.db import app_inventory_db
from app_inventory.routes import get_scraper, setup_app_inventory_routes
from auth import db as auth_db
from auth.middleware import require_auth
from auth.routes import setup_auth_routes


async def auth_middleware(request: Request, call_next):
    """Middleware to require authentication for sensitive endpoints."""
    # Require auth for endpoints that should always be tied to a logged-in user.
    protected = request.method == "POST" and request.url.path in {"/api/offer", "/api/text"}
    if protected:
        try:
            user_id = await require_auth(request)
            # Store user_id in request state for later use
            request.state.user_id = user_id
            logger.debug(f"Authenticated request from user: {user_id}")

            # Read request body and add user_id to request_data
            import json

            body = await request.body()
            if body:
                try:
                    request_data = json.loads(body)
                    # Add user_id to request_data
                    if "request_data" not in request_data:
                        request_data["request_data"] = {}
                    if not isinstance(request_data["request_data"], dict):
                        request_data["request_data"] = {"user_id": user_id}
                    else:
                        request_data["request_data"]["user_id"] = user_id

                    # Replace request body with modified data
                    request._body = json.dumps(request_data).encode()
                except json.JSONDecodeError:
                    logger.warning("Failed to parse request body as JSON")
        except Exception as e:
            logger.warning(f"Authentication failed for {request.url.path}: {e}")
            return JSONResponse(
                status_code=status.HTTP_401_UNAUTHORIZED,
                content={"detail": "Authentication required"},
            )

    response = await call_next(request)
    return response


async def _connect_with_retries(name: str, connect_fn) -> bool:
    """Try connect_fn until it succeeds or attempts are exhausted.

    Handles PostgreSQL starting after the API (e.g. Docker Compose), which
    otherwise leaves pools unset while auth later recovers via per-request lazy
    ``connect()`` in ``auth.db``.
    """
    max_attempts = int(os.getenv("DATABASE_CONNECT_MAX_ATTEMPTS", "15"))
    delay_sec = float(os.getenv("DATABASE_CONNECT_RETRY_DELAY_SEC", "2"))

    for attempt in range(1, max_attempts + 1):
        try:
            await connect_fn()
            return True
        except Exception as e:
            if attempt >= max_attempts:
                logger.error(f"Failed to initialize {name} after {max_attempts} attempts: {e}")
                return False
            logger.warning(
                f"{name} not ready ({e!s}); retry {attempt}/{max_attempts} in {delay_sec}s..."
            )
            await asyncio.sleep(delay_sec)
    return False


def setup_auth_for_app(app):
    """Setup OAuth authentication for FastAPI app.

    Args:
        app: FastAPI app instance
    """

    # Add startup/shutdown events for database connections
    @app.on_event("startup")
    async def startup_event():
        if await _connect_with_retries("auth database", auth_db.connect):
            logger.info("Auth database connection pool initialized")
        else:
            logger.warning("Continuing without auth database connection...")

        if await _connect_with_retries("app inventory database", app_inventory_db.connect):
            get_scraper().start()
            logger.info("App inventory database + scraper initialized")
        else:
            logger.warning("Continuing without app inventory features...")

    @app.on_event("shutdown")
    async def shutdown_event():
        try:
            scraper = get_scraper()
            await scraper.stop()
        except Exception as e:
            logger.error(f"Error stopping scraper: {e}")

        try:
            await app_inventory_db.close()
        except Exception as e:
            logger.error(f"Error closing app inventory database: {e}")

        try:
            await auth_db.close()
            logger.info("Database connection pools closed")
        except Exception as e:
            logger.error(f"Error closing auth database: {e}")

    # Add authentication middleware
    app.middleware("http")(auth_middleware)

    # Setup OAuth routes
    setup_auth_routes(app)

    # Setup app inventory routes
    setup_app_inventory_routes(app)

    logger.info("OAuth authentication + app inventory setup complete")
