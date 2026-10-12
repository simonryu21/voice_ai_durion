"""Database connection and utilities for OAuth authentication."""

import os
from typing import Optional

import asyncpg
from loguru import logger


class Database:
    """Database connection manager for OAuth user data.

    Requires DATABASE_URL to be set. OAuth user records are stored in PostgreSQL.
    """

    def __init__(self):
        self._pool: Optional[asyncpg.Pool] = None

    async def connect(self):
        """Initialize database connection pool. Requires DATABASE_URL."""
        if self._pool is not None:
            return

        database_url = os.getenv("DATABASE_URL")
        if not database_url:
            raise ValueError(
                "DATABASE_URL is not set. OAuth user persistence requires a PostgreSQL database."
            )

        self._pool = await asyncpg.create_pool(
            database_url,
            min_size=1,
            max_size=10,
            command_timeout=60,
        )
        logger.info("Database connection pool created")

    async def close(self):
        """Close database connection pool."""
        if self._pool:
            await self._pool.close()
            self._pool = None
            logger.info("Database connection pool closed")

    async def get_user_by_provider(self, provider: str, provider_user_id: str) -> Optional[dict]:
        """Get user by OAuth provider and provider user ID."""
        if self._pool is None:
            await self.connect()

        async with self._pool.acquire() as conn:
            row = await conn.fetchrow(
                """
                SELECT id, provider, provider_user_id, email, name, profile_image_url, gportal_user_id, created_at, updated_at
                FROM users
                WHERE provider = $1 AND provider_user_id = $2
                """,
                provider,
                provider_user_id,
            )

            if row:
                return dict(row)
            return None

    async def create_or_update_user(
        self,
        provider: str,
        provider_user_id: str,
        email: Optional[str] = None,
        name: Optional[str] = None,
        profile_image_url: Optional[str] = None,
        gportal_user_id: Optional[str] = None,
    ) -> dict:
        """Create or update user from OAuth provider."""
        if self._pool is None:
            await self.connect()

        async with self._pool.acquire() as conn:
            row = await conn.fetchrow(
                """
                INSERT INTO users (provider, provider_user_id, email, name, profile_image_url, gportal_user_id, updated_at)
                VALUES ($1, $2, $3, $4, $5, $6, CURRENT_TIMESTAMP)
                ON CONFLICT (provider, provider_user_id)
                DO UPDATE SET
                    email = COALESCE(EXCLUDED.email, users.email),
                    name = COALESCE(EXCLUDED.name, users.name),
                    profile_image_url = COALESCE(EXCLUDED.profile_image_url, users.profile_image_url),
                    gportal_user_id = COALESCE(EXCLUDED.gportal_user_id, users.gportal_user_id),
                    updated_at = CURRENT_TIMESTAMP
                RETURNING id, provider, provider_user_id, email, name, profile_image_url, gportal_user_id, created_at, updated_at
                """,
                provider,
                provider_user_id,
                email,
                name,
                profile_image_url,
                gportal_user_id,
            )

            return dict(row)

    async def update_gportal_user_id(
        self, id: str, gportal_user_id: Optional[str]
    ) -> Optional[dict]:
        """Update gportal_user_id (GPortal X-User-Id) for an existing user by id."""
        if self._pool is None:
            await self.connect()

        async with self._pool.acquire() as conn:
            row = await conn.fetchrow(
                """
                UPDATE users SET gportal_user_id = $2, updated_at = CURRENT_TIMESTAMP
                WHERE id = $1
                RETURNING id, provider, provider_user_id, email, name, profile_image_url, gportal_user_id, created_at, updated_at
                """,
                id,
                gportal_user_id,
            )
            return dict(row) if row else None

    async def get_user_by_id(self, user_id: str) -> Optional[dict]:
        """Get user by UUID (users.id)."""
        if self._pool is None:
            await self.connect()

        async with self._pool.acquire() as conn:
            row = await conn.fetchrow(
                """
                SELECT id, provider, provider_user_id, email, name, profile_image_url, gportal_user_id, created_at, updated_at
                FROM users
                WHERE id = $1
                """,
                user_id,
            )

            if row:
                return dict(row)
            return None


# Global database instance
db = Database()
