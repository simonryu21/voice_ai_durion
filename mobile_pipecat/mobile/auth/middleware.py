"""Authentication middleware for FastAPI."""

from typing import Optional

from fastapi import HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from loguru import logger

from auth.jwt_utils import get_user_id_from_token, verify_token

security = HTTPBearer(auto_error=False)


async def get_current_user_id(request: Request) -> Optional[str]:
    """Extract user ID from JWT token in request.

    Checks both Authorization header and httpOnly cookie.

    Args:
        request: FastAPI Request object

    Returns:
        User ID if token is valid, None otherwise
    """
    # Try to get token from Authorization header first
    credentials: Optional[HTTPAuthorizationCredentials] = await security(request)
    if credentials:
        token = credentials.credentials
        user_id = get_user_id_from_token(token)
        if user_id:
            return user_id

    # Try to get token from httpOnly cookie
    token = request.cookies.get("access_token")
    if token:
        user_id = get_user_id_from_token(token)
        if user_id:
            return user_id

    return None


async def require_auth(request: Request) -> str:
    """Require authentication and return user ID.

    Raises HTTPException if user is not authenticated.

    Args:
        request: FastAPI Request object

    Returns:
        User ID

    Raises:
        HTTPException: If user is not authenticated
    """
    user_id = await get_current_user_id(request)
    if not user_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return user_id
