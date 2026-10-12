"""OAuth authentication routes for FastAPI."""

import os
import time
from typing import Dict, Optional, Tuple

from fastapi import APIRouter, HTTPException, Request, Response, status
from fastapi.responses import HTMLResponse, RedirectResponse
from loguru import logger

from auth import db
from auth.middleware import get_current_user_id
from auth.oauth import (
    get_google_oauth_url,
    get_kakao_oauth_url,
    get_naver_oauth_url,
    handle_google_callback,
    handle_kakao_callback,
    handle_naver_callback,
)

router = APIRouter(prefix="/api/auth", tags=["auth"])

FRONTEND_URL = os.getenv("FRONTEND_URL", "http://localhost:3000")
# When app is served from same server at /client/ (e.g. ngrok), set to "client"
# so redirect goes to FRONTEND_URL/client/?success=...
# Leave unset when using a separate dev server at FRONTEND_URL.
OAUTH_REDIRECT_PATH = os.getenv("OAUTH_REDIRECT_PATH", "").strip()

# Deduplication cache: WebView delivers the same OAuth callback URL twice.
# Stores {code_prefix: (jwt_token, expires_at)} for 60 s so the second
# request returns the same token-bearing redirect without re-exchanging the code.
_used_codes: Dict[str, Tuple[str, float]] = {}
_kakao_used_codes: Dict[str, Tuple[str, float]] = {}


def _oauth_redirect_url(
    success: bool, error: Optional[str] = None, token: Optional[str] = None
) -> str:
    base = FRONTEND_URL.rstrip("/")
    if OAUTH_REDIRECT_PATH:
        path = OAUTH_REDIRECT_PATH.rstrip("/")
        url = f"{base}/{path}/" if path else f"{base}/"
    else:
        url = f"{base}/auth/callback"
    if success:
        q = "success=true"
        if token:
            q += f"&token={token}"
    else:
        q = f"success=false&error={error or 'unknown'}"
    return f"{url}?{q}"


def _html_redirect_with_token(token: str) -> HTMLResponse:
    """Return an HTML page that redirects to the frontend with the JWT as a URL param.

    Using an HTML page (instead of a plain 307 redirect) ensures the browser
    renders the response in the same origin context before navigating, which
    is required for window.location.replace() to work reliably in WebViews.
    """
    redirect_url = _oauth_redirect_url(success=True, token=token)
    html = f"""<!DOCTYPE html>
<html>
<head>
<script>window.location.replace({redirect_url!r});</script>
<meta http-equiv="refresh" content="0;url={redirect_url}">
</head>
<body>Redirecting...</body>
</html>"""
    return HTMLResponse(content=html)


def setup_auth_routes(app):
    """Setup OAuth authentication routes on the FastAPI app."""
    app.include_router(router)


@router.get("/google/url")
async def google_auth_url(request: Request):
    """Return Google OAuth URL as JSON for the frontend to navigate to."""
    try:
        authorization_url = await get_google_oauth_url(request)
        return {"url": authorization_url}
    except Exception as e:
        logger.error(f"Google OAuth URL error: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to get Google OAuth URL: {str(e)}",
        )


@router.get("/google/login")
async def google_login(request: Request):
    """Initiate Google OAuth via direct redirect (fallback for non-JS clients)."""
    try:
        authorization_url = await get_google_oauth_url(request)
        return HTMLResponse(
            content=f"""<!DOCTYPE html>
<html><head><script>window.location.replace({authorization_url!r});</script>
<meta http-equiv="refresh" content="0;url={authorization_url}"></head>
<body>Redirecting...</body></html>"""
        )
    except Exception as e:
        logger.error(f"Google OAuth login error: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to initiate Google OAuth: {str(e)}",
        )


@router.get("/google/callback")
async def google_callback(request: Request, code: str, state: Optional[str] = None):
    """Handle Google OAuth callback.

    JWT is passed as a URL parameter (?token=...) instead of a cookie because
    Android WebView silently drops httpOnly cookies set during redirects.

    redirect_uri priority: GOOGLE_REDIRECT_URI env var > request.base_url
    """
    now = time.time()
    code_key = code[:32]

    # Clean up expired dedup entries
    for k in [k for k, (_, exp) in list(_used_codes.items()) if exp < now]:
        del _used_codes[k]

    # Return cached token for duplicate callbacks (WebView fires the callback twice)
    if code_key in _used_codes:
        cached_token, _ = _used_codes[code_key]
        logger.warning("Duplicate OAuth callback detected, returning cached token")
        return _html_redirect_with_token(cached_token)

    try:
        env_redirect_uri = os.getenv("GOOGLE_REDIRECT_URI")
        if env_redirect_uri:
            redirect_uri = env_redirect_uri
        else:
            redirect_uri = str(request.base_url).rstrip("/") + "/api/auth/google/callback"

        jwt_token, user = await handle_google_callback(code, redirect_uri=redirect_uri)
        _used_codes[code_key] = (jwt_token, now + 60)

        logger.info(f"Google OAuth successful for user: {user['email']}")
        return _html_redirect_with_token(jwt_token)
    except Exception as e:
        logger.error(f"Google OAuth callback error: {e}")
        return RedirectResponse(url=_oauth_redirect_url(success=False, error=str(e)))


@router.get("/naver/login")
async def naver_login():
    """Initiate Naver OAuth login flow."""
    try:
        authorization_url = await get_naver_oauth_url()
        return RedirectResponse(url=authorization_url)
    except Exception as e:
        logger.error(f"Naver OAuth login error: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to initiate Naver OAuth: {str(e)}",
        )


@router.get("/naver/callback")
async def naver_callback(request: Request, code: str, state: str = None):
    """Handle Naver OAuth callback."""
    try:
        jwt_token, user = await handle_naver_callback(code, state)
        logger.info(f"Naver OAuth successful for user: {user['email']}")
        return _html_redirect_with_token(jwt_token)
    except Exception as e:
        logger.error(f"Naver OAuth callback error: {e}")
        return RedirectResponse(url=_oauth_redirect_url(success=False, error=str(e)))


@router.get("/kakao/url")
async def kakao_auth_url(request: Request):
    """Return Kakao OAuth URL as JSON for the frontend to navigate to."""
    try:
        authorization_url = await get_kakao_oauth_url(request)
        return {"url": authorization_url}
    except Exception as e:
        logger.error(f"Kakao OAuth URL error: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to get Kakao OAuth URL: {str(e)}",
        )


@router.get("/kakao/login")
async def kakao_login(request: Request):
    """Initiate Kakao OAuth via direct redirect (fallback for non-JS clients)."""
    try:
        authorization_url = await get_kakao_oauth_url(request)
        return HTMLResponse(
            content=f"""<!DOCTYPE html>
<html><head><script>window.location.replace({authorization_url!r});</script>
<meta http-equiv="refresh" content="0;url={authorization_url}"></head>
<body>Redirecting...</body></html>"""
        )
    except Exception as e:
        logger.error(f"Kakao OAuth login error: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to initiate Kakao OAuth: {str(e)}",
        )


@router.get("/kakao/callback")
async def kakao_callback(request: Request, code: str, state: Optional[str] = None):
    """Handle Kakao OAuth callback.

    JWT is passed as a URL parameter (?token=...) instead of a cookie because
    Android WebView silently drops httpOnly cookies set during redirects.

    redirect_uri priority: KAKAO_REDIRECT_URI env var > request.base_url
    """
    now = time.time()
    code_key = code[:32]

    for k in [k for k, (_, exp) in list(_kakao_used_codes.items()) if exp < now]:
        del _kakao_used_codes[k]

    if code_key in _kakao_used_codes:
        cached_token, _ = _kakao_used_codes[code_key]
        logger.warning("Duplicate Kakao OAuth callback detected, returning cached token")
        return _html_redirect_with_token(cached_token)

    try:
        env_redirect_uri = os.getenv("KAKAO_REDIRECT_URI")
        if env_redirect_uri:
            redirect_uri = env_redirect_uri
        else:
            redirect_uri = str(request.base_url).rstrip("/") + "/api/auth/kakao/callback"

        jwt_token, user = await handle_kakao_callback(code, redirect_uri=redirect_uri, state=state)
        _kakao_used_codes[code_key] = (jwt_token, now + 60)

        logger.info(f"Kakao OAuth successful for user: {user.get('email', 'N/A')}")
        return _html_redirect_with_token(jwt_token)
    except Exception as e:
        logger.error(f"Kakao OAuth callback error: {e}")
        return RedirectResponse(url=_oauth_redirect_url(success=False, error=str(e)))


@router.get("/verify")
async def verify_token_endpoint(request: Request):
    """Verify JWT token and return user info."""
    user_id = await get_current_user_id(request)
    if not user_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing token",
        )

    user = await db.get_user_by_id(user_id)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="User not found",
        )

    return {
        "authenticated": True,
        "user": {
            "id": str(user["id"]),
            "email": user.get("email"),
            "name": user.get("name"),
            "profile_image_url": user.get("profile_image_url"),
            "provider": user.get("provider"),
        },
    }


@router.post("/logout")
async def logout():
    """Logout user."""
    response = Response(status_code=status.HTTP_200_OK)
    response.delete_cookie(key="access_token", httponly=True, samesite="lax")
    return {"message": "Logged out successfully"}
