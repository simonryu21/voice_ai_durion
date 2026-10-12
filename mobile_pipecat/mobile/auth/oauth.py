"""OAuth 2.0 authentication handlers for Google, Naver, and Kakao."""

import os
import secrets
import time
from typing import Dict, Optional
from urllib.parse import urlencode

from authlib.integrations.httpx_client import AsyncOAuth2Client
from httpx import AsyncClient
from loguru import logger

from auth.db import db
from auth.jwt_utils import create_access_token


def _get_google_oauth_settings(redirect_uri: Optional[str] = None):
    """Read Google OAuth env vars. redirect_uri overrides env when provided (for IP/localhost 접속 구분)."""
    return {
        "client_id": os.getenv("GOOGLE_CLIENT_ID"),
        "client_secret": os.getenv("GOOGLE_CLIENT_SECRET"),
        "redirect_uri": redirect_uri
        or os.getenv("GOOGLE_REDIRECT_URI", "https://localhost:7860/api/auth/google/callback"),
    }


def _get_naver_oauth_settings():
    return {
        "client_id": os.getenv("NAVER_CLIENT_ID"),
        "client_secret": os.getenv("NAVER_CLIENT_SECRET"),
        "redirect_uri": os.getenv(
            "NAVER_REDIRECT_URI", "http://localhost:7860/api/auth/naver/callback"
        ),
    }


def _get_kakao_oauth_settings(redirect_uri: Optional[str] = None):
    """Read Kakao OAuth env vars. redirect_uri must match the authorize request."""
    return {
        "client_id": os.getenv("KAKAO_CLIENT_ID"),
        "client_secret": os.getenv("KAKAO_CLIENT_SECRET"),
        "redirect_uri": redirect_uri
        or os.getenv("KAKAO_REDIRECT_URI", "http://localhost:7860/api/auth/kakao/callback"),
    }


def _get_kakao_oauth_scope() -> str:
    """Return Kakao OAuth scope string.

    Kakao is strict about requested scopes vs enabled consent items in the Kakao Developers console.
    If a scope is not enabled/approved, the authorize request fails with an error like KOE205.

    Configure via `KAKAO_SCOPE` (space-separated, e.g. "profile_nickname profile_image").
    """
    scope = (os.getenv("KAKAO_SCOPE") or "").strip()
    # Safe default: do not request account_email by default (often requires extra consent/verification).
    return scope or "profile_nickname profile_image"


# CSRF protection: state issued at authorize time, single-use, short TTL.
_kakao_oauth_states: Dict[str, float] = {}


def _get_base_url():
    return os.getenv("BASE_URL", "http://localhost:7860")


async def get_google_oauth_url(request=None) -> str:
    """Generate Google OAuth URL.

    redirect_uri priority:
    1. GOOGLE_REDIRECT_URI env var (explicit override, e.g. ngrok URL)
    2. request.base_url (dynamic, works for direct IP access)
    3. Default localhost fallback
    """
    env_redirect_uri = os.getenv("GOOGLE_REDIRECT_URI")
    if env_redirect_uri:
        redirect_uri = env_redirect_uri
    elif request is not None:
        redirect_uri = str(request.base_url).rstrip("/") + "/api/auth/google/callback"
    else:
        redirect_uri = None
    settings = _get_google_oauth_settings(redirect_uri=redirect_uri)
    if not settings["client_id"]:
        raise ValueError("GOOGLE_CLIENT_ID is not set")

    oauth_client = AsyncOAuth2Client(
        client_id=settings["client_id"],
        client_secret=settings["client_secret"],
        redirect_uri=settings["redirect_uri"],
    )

    # create_authorization_url is sync (returns tuple), do not await
    authorization_url, _ = oauth_client.create_authorization_url(
        "https://accounts.google.com/o/oauth2/v2/auth",
        access_type="offline",
        prompt="consent",
        scope="openid email profile",
    )

    return authorization_url


async def handle_google_callback(code: str, redirect_uri: Optional[str] = None) -> tuple[str, dict]:
    """Handle Google OAuth callback. redirect_uri는 로그인 시 사용한 값과 동일해야 함 (보통 request.base_url 기준)."""
    settings = _get_google_oauth_settings(redirect_uri=redirect_uri)
    if not settings["client_id"] or not settings["client_secret"]:
        raise ValueError("Google OAuth credentials are not set")

    oauth_client = AsyncOAuth2Client(
        client_id=settings["client_id"],
        client_secret=settings["client_secret"],
        redirect_uri=settings["redirect_uri"],
    )

    # Exchange authorization code for access token
    token_response = await oauth_client.fetch_token(
        "https://oauth2.googleapis.com/token",
        code=code,
    )

    access_token = token_response.get("access_token")
    if not access_token:
        raise ValueError("Failed to get access token from Google")

    # Get user info from Google
    async with AsyncClient() as client:
        user_info_response = await client.get(
            "https://www.googleapis.com/oauth2/v2/userinfo",
            headers={"Authorization": f"Bearer {access_token}"},
        )
        user_info_response.raise_for_status()
        user_info = user_info_response.json()

    # Create or update user in database
    user = await db.create_or_update_user(
        provider="google",
        provider_user_id=user_info["id"],
        email=user_info.get("email"),
        name=user_info.get("name"),
        profile_image_url=user_info.get("picture"),
    )

    # Create JWT token
    jwt_token = create_access_token(data={"sub": str(user["id"])})

    return jwt_token, user


async def get_naver_oauth_url() -> str:
    """Generate Naver OAuth authorization URL."""
    settings = _get_naver_oauth_settings()
    if not settings["client_id"]:
        raise ValueError("NAVER_CLIENT_ID is not set")

    # Naver uses state parameter for CSRF protection
    import secrets

    state = secrets.token_urlsafe(32)

    params = {
        "response_type": "code",
        "client_id": settings["client_id"],
        "redirect_uri": settings["redirect_uri"],
        "state": state,
    }

    query_string = "&".join([f"{k}={v}" for k, v in params.items()])
    authorization_url = f"https://nid.naver.com/oauth2.0/authorize?{query_string}"

    return authorization_url


async def handle_naver_callback(code: str, state: Optional[str] = None) -> tuple[str, dict]:
    """Handle Naver OAuth callback and return JWT token and user info.

    Args:
        code: Authorization code from Naver
        state: State parameter for CSRF protection

    Returns:
        Tuple of (JWT token, user info dict)
    """
    settings = _get_naver_oauth_settings()
    if not settings["client_id"] or not settings["client_secret"]:
        raise ValueError("Naver OAuth credentials are not set")

    # Exchange authorization code for access token
    async with AsyncClient() as client:
        token_response = await client.post(
            "https://nid.naver.com/oauth2.0/token",
            params={
                "grant_type": "authorization_code",
                "client_id": settings["client_id"],
                "client_secret": settings["client_secret"],
                "code": code,
                "state": state,
            },
        )
        token_response.raise_for_status()
        token_data = token_response.json()

    access_token = token_data.get("access_token")
    if not access_token:
        raise ValueError("Failed to get access token from Naver")

    # Get user info from Naver
    async with AsyncClient() as client:
        user_info_response = await client.get(
            "https://openapi.naver.com/v1/nid/me",
            headers={"Authorization": f"Bearer {access_token}"},
        )
        user_info_response.raise_for_status()
        response_data = user_info_response.json()

    if response_data.get("resultcode") != "00":
        raise ValueError(f"Naver API error: {response_data.get('message')}")

    user_info = response_data.get("response", {})

    # Create or update user in database
    user = await db.create_or_update_user(
        provider="naver",
        provider_user_id=user_info["id"],
        email=user_info.get("email"),
        name=user_info.get("name"),
        profile_image_url=user_info.get("profile_image"),
    )

    # Create JWT token
    jwt_token = create_access_token(data={"sub": str(user["id"])})

    return jwt_token, user


def _cleanup_kakao_oauth_states(now: float) -> None:
    for k in [k for k, exp in list(_kakao_oauth_states.items()) if exp < now]:
        del _kakao_oauth_states[k]


async def get_kakao_oauth_url(request=None) -> str:
    """Generate Kakao OAuth authorization URL.

    redirect_uri priority:
    1. KAKAO_REDIRECT_URI env var (explicit override, e.g. ngrok URL)
    2. request.base_url (dynamic, works for direct IP access)
    3. Default localhost fallback
    """
    env_redirect_uri = os.getenv("KAKAO_REDIRECT_URI")
    if env_redirect_uri:
        redirect_uri = env_redirect_uri
    elif request is not None:
        redirect_uri = str(request.base_url).rstrip("/") + "/api/auth/kakao/callback"
    else:
        redirect_uri = None

    settings = _get_kakao_oauth_settings(redirect_uri=redirect_uri)
    if not settings["client_id"]:
        raise ValueError("KAKAO_CLIENT_ID is not set")

    now = time.time()
    _cleanup_kakao_oauth_states(now)
    state = secrets.token_urlsafe(32)
    _kakao_oauth_states[state] = now + 600.0

    # Kakao: scope values are space-separated (Kakao REST API docs).
    params = {
        "client_id": settings["client_id"],
        "redirect_uri": settings["redirect_uri"],
        "response_type": "code",
        "scope": _get_kakao_oauth_scope(),
        "state": state,
    }
    query_string = urlencode(params)
    return f"https://kauth.kakao.com/oauth/authorize?{query_string}"


async def handle_kakao_callback(
    code: str,
    redirect_uri: Optional[str] = None,
    state: Optional[str] = None,
) -> tuple[str, dict]:
    """Handle Kakao OAuth callback and return JWT token and user info.

    Args:
        code: Authorization code from Kakao
        redirect_uri: Must match the value used in the authorize request (token exchange).
        state: CSRF token from the authorize response (must match server-issued state).

    Returns:
        Tuple of (JWT token, user info dict)
    """
    now = time.time()
    _cleanup_kakao_oauth_states(now)

    if not state or state not in _kakao_oauth_states:
        raise ValueError("Invalid or expired Kakao OAuth state")
    del _kakao_oauth_states[state]

    settings = _get_kakao_oauth_settings(redirect_uri=redirect_uri)
    if not settings["client_id"] or not settings["client_secret"]:
        raise ValueError("Kakao OAuth credentials are not set")

    # Exchange authorization code for access token
    async with AsyncClient() as client:
        token_response = await client.post(
            "https://kauth.kakao.com/oauth/token",
            headers={"Content-Type": "application/x-www-form-urlencoded"},
            data={
                "grant_type": "authorization_code",
                "client_id": settings["client_id"],
                "client_secret": settings["client_secret"],
                "code": code,
                "redirect_uri": settings["redirect_uri"],
            },
        )
        token_response.raise_for_status()
        token_data = token_response.json()

    access_token = token_data.get("access_token")
    if not access_token:
        raise ValueError("Failed to get access token from Kakao")

    # Get user info from Kakao
    async with AsyncClient() as client:
        user_info_response = await client.get(
            "https://kapi.kakao.com/v2/user/me",
            headers={"Authorization": f"Bearer {access_token}"},
        )
        user_info_response.raise_for_status()
        user_info = user_info_response.json()

    kakao_account = user_info.get("kakao_account", {})
    profile = kakao_account.get("profile", {})

    # Create or update user in database
    user = await db.create_or_update_user(
        provider="kakao",
        provider_user_id=str(user_info["id"]),
        email=kakao_account.get("email"),
        name=profile.get("nickname"),
        profile_image_url=profile.get("profile_image_url"),
    )

    # Create JWT token
    jwt_token = create_access_token(data={"sub": str(user["id"])})

    return jwt_token, user
