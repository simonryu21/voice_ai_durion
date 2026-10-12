"""GPortal MCP REST/SSE client.

Handles authentication, tool listing, and streaming tool invocation against
the GPortal MCP backend.  The ``X-User-Id`` header is set at runtime via
:meth:`set_user_id` using the value from the Postgres ``users.gportal_user_id`` column.
"""

import asyncio
import json
import time
from typing import AsyncIterator, Dict, List, Optional, Tuple

import aiohttp
from loguru import logger

from .types import GportalMCPConfig


class GportalMCPClient:
    """GPortal MCP REST/SSE client.

    Provides authentication, tool listing, and streaming tool invocation.
    ``X-User-Id`` is populated from ``users.gportal_user_id`` via :meth:`set_user_id`.
    """

    def __init__(self, config: GportalMCPConfig):
        """Initialize the client.

        Args:
            config: Connection configuration for the GPortal MCP server.
        """
        self._config = config
        self._user_id: Optional[str] = None
        self._access_token: Optional[str] = None
        self._token_expires_at: float = 0.0
        self._session: Optional[aiohttp.ClientSession] = None
        self._thread_ids: Dict[str, str] = {}
        self._login_lock = asyncio.Lock()

    def set_user_id(self, user_id: Optional[str]) -> None:
        """Set the ``X-User-Id`` header value.

        Args:
            user_id: Value from ``users.gportal_user_id`` column (GPortal X-User-Id), or ``None`` to clear.
        """
        self._user_id = user_id

    async def _ensure_session(self) -> aiohttp.ClientSession:
        if self._session is None or self._session.closed:
            self._session = aiohttp.ClientSession()
        return self._session

    async def close(self):
        """Close the underlying HTTP session."""
        if self._session and not self._session.closed:
            await self._session.close()

    # ── Authentication ──

    async def _ensure_token(self):
        """Refresh the access token if it is missing or about to expire."""
        margin = self._config.token_refresh_margin
        if self._access_token and time.time() < (self._token_expires_at - margin):
            return
        async with self._login_lock:
            if self._access_token and time.time() < (self._token_expires_at - margin):
                return
            await self._login()

    async def _login(self):
        """POST /api/mcp/login to obtain an access token."""
        session = await self._ensure_session()
        url = f"{self._config.base_url}/api/mcp/login"
        payload = {
            "api_key": self._config.api_key,
            "password": self._config.password,
        }
        async with session.post(url, json=payload) as resp:
            if resp.status != 200:
                text = await resp.text()
                raise RuntimeError(f"gportal login failed ({resp.status}): {text}")
            data = await resp.json()
            self._access_token = data["access_token"]
            self._token_expires_at = time.time() + data.get("expires_in", 3600)
            logger.info("gportal MCP login succeeded")

    def _headers(self) -> Dict[str, str]:
        headers = {
            "Authorization": f"Bearer {self._access_token}",
            "Content-Type": "application/json",
        }
        if self._user_id:
            headers["X-User-Id"] = self._user_id
        return headers

    # ── Tool listing ──

    async def list_tools(self) -> List[Dict]:
        """GET /api/mcp/tools/list and return tool schema list.

        Returns:
            A list of tool schema dictionaries.
        """
        await self._ensure_token()
        session = await self._ensure_session()
        url = f"{self._config.base_url}/api/mcp/tools/list"
        async with session.get(url, headers=self._headers()) as resp:
            if resp.status == 401:
                await self._login()
                async with session.get(url, headers=self._headers()) as retry_resp:
                    retry_resp.raise_for_status()
                    data = await retry_resp.json()
                    return data.get("tools", [])
            resp.raise_for_status()
            data = await resp.json()
            return data.get("tools", [])

    # ── Streaming tool invocation ──

    async def call_tool_stream(
        self, name: str, subagent_request: str, thread_id: Optional[str] = None
    ) -> AsyncIterator[Tuple[str, Dict]]:
        """POST /api/mcp/tools/call_stream and yield SSE events.

        Args:
            name: Tool name to invoke.
            subagent_request: Request text to pass to the sub-agent.
            thread_id: Optional thread ID for conversation continuity.

        Yields:
            Tuples of ``(event_type, data_dict)`` for each SSE event.
        """
        await self._ensure_token()
        session = await self._ensure_session()
        url = f"{self._config.base_url}/api/mcp/tools/call_stream"
        payload = {
            "name": name,
            "arguments": {
                "subagent_request": subagent_request,
                "thread_id": thread_id,
            },
        }

        retries = 0
        max_retries = 2
        backoff = 1.0

        while True:
            try:
                async with session.post(
                    url,
                    json=payload,
                    headers=self._headers(),
                    timeout=aiohttp.ClientTimeout(total=self._config.stream_timeout),
                ) as resp:
                    if resp.status == 401 and retries == 0:
                        await self._login()
                        retries += 1
                        continue

                    resp.raise_for_status()
                    event_type = ""
                    saw_end = False
                    async for line_bytes in resp.content:
                        line = line_bytes.decode("utf-8").rstrip("\n\r")
                        if line.startswith("event:"):
                            event_type = line[len("event:") :].strip()
                        elif line.startswith("data:"):
                            raw = line[len("data:") :].strip()
                            try:
                                data = json.loads(raw)
                            except json.JSONDecodeError:
                                data = {"raw": raw}
                            if (
                                event_type == "message"
                                and "content" in data
                                and data["content"] is not None
                            ):
                                data["content"] = data["content"].replace("\\n", "\n")
                            yield event_type, data
                            if event_type == "end":
                                saw_end = True
                                return
                    # If the server closes the connection (or a proxy truncates it) without an explicit
                    # "end" event, treat it as an error so callers don't silently proceed with empty
                    # tool output and the agent can surface a retry/failure.
                    if not saw_end:
                        raise RuntimeError("gportal tool stream ended without 'end' event")
                    return

            except (aiohttp.ClientError, asyncio.TimeoutError) as exc:
                retries += 1
                if retries > max_retries:
                    raise
                logger.warning(f"gportal call_tool_stream retry {retries}/{max_retries}: {exc}")
                await asyncio.sleep(backoff)
                backoff *= 3

    # ── Thread ID management ──

    def get_thread_id(self, agent_name: str) -> Optional[str]:
        """Get the stored thread ID for an agent.

        Args:
            agent_name: The agent name key.
        """
        return self._thread_ids.get(agent_name)

    def set_thread_id(self, agent_name: str, thread_id: str):
        """Store the thread ID for an agent.

        Args:
            agent_name: The agent name key.
            thread_id: The thread ID to store.
        """
        self._thread_ids[agent_name] = thread_id
