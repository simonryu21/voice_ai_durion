"""Pydantic models for GPortal MCP client configuration."""

from pydantic import BaseModel, Field


class GportalMCPConfig(BaseModel):
    """GPortal MCP server connection configuration.

    Args:
        base_url: GPortal backend base URL (e.g. "https://gportal.example.com").
        api_key: Server-to-server API key.
        password: Client secret for authentication.
        stream_timeout: Streaming request timeout in seconds.
        token_refresh_margin: Seconds before expiry to refresh the token.
    """

    base_url: str
    api_key: str
    password: str
    stream_timeout: float = 120.0
    token_refresh_margin: int = 300
