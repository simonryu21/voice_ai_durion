"""OAuth authentication module."""

from auth.db import db
from auth.jwt_utils import create_access_token, get_user_id_from_token, verify_token
from auth.oauth import (
    get_google_oauth_url,
    get_kakao_oauth_url,
    get_naver_oauth_url,
    handle_google_callback,
    handle_kakao_callback,
    handle_naver_callback,
)

__all__ = [
    "db",
    "create_access_token",
    "get_user_id_from_token",
    "verify_token",
    "get_google_oauth_url",
    "get_naver_oauth_url",
    "get_kakao_oauth_url",
    "handle_google_callback",
    "handle_naver_callback",
    "handle_kakao_callback",
]
