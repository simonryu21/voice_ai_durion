#
# Copyright (c) 2024–2025, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#

"""OSS-compatible MemoryClient implementations for Mem0.

This module provides MemoryClient subclasses that handle OSS (Open Source Software)
server-specific behaviors, such as empty /v1/ping/ responses and different API endpoint
requirements.
"""

import hashlib
import os
from typing import Any

import httpx
from loguru import logger
from mem0 import MemoryClient
from mem0.client.project import Project
from mem0.memory.telemetry import capture_client_event


class OSSMemoryClient(MemoryClient):
    """MemoryClient wrapper that handles OSS server's empty /v1/ping/ response."""

    def __init__(self, api_key=None, host=None, **kwargs):
        """Initialize OSSCompatibleMemoryClient, handling OSS server's empty responses."""
        # Use provided api_key or get from env, or use dummy key for OSS
        self.api_key = api_key or os.getenv("MEM0_API_KEY") or "oss-dummy-key"
        self.host = host or "https://api.mem0.ai"
        self.org_id = kwargs.get("org_id")
        self.project_id = kwargs.get("project_id")

        # Create MD5 hash of API key for user_id
        self.user_id = hashlib.md5(self.api_key.encode()).hexdigest()

        # Prepare headers (remove None values before creating client)
        headers = {
            "Mem0-User-ID": self.user_id,
        }
        # Only add Authorization header if api_key is not dummy
        if self.api_key != "oss-dummy-key":
            headers["Authorization"] = f"Token {self.api_key}"

        # Create httpx client
        self.client = httpx.Client(
            base_url=self.host,
            headers=headers,
            timeout=httpx.Timeout(120.0, connect=10.0),
        )

        # Validate API key (may return None for OSS servers)
        self.user_email = self._validate_api_key()

        # Initialize project manager (may fail for OSS, but that's okay)
        try:
            if self.org_id and self.project_id:
                self.project = Project(
                    client=self.client,
                    org_id=self.org_id,
                    project_id=self.project_id,
                    user_email=self.user_email,
                )
            else:
                logger.warning(
                    "org_id and project_id not available for OSS server, skipping Project initialization"
                )
                self.project = None
        except Exception as e:
            logger.warning(f"Could not initialize Project manager for OSS server: {e}")
            self.project = None

        capture_client_event("client.init", self, {"sync_type": "sync"})

    def _validate_api_key(self):
        """Override to handle empty response from OSS server's /v1/ping/ endpoint."""
        try:
            # _prepare_params() may require org_id and project_id, but for OSS we can skip them
            try:
                params = self._prepare_params()
            except Exception:
                # If _prepare_params() fails due to missing org_id/project_id, use empty params
                params = {}
            response = self.client.get("/v1/ping/", params=params)
            # Check if response has content before parsing JSON
            if response.text:
                data = response.json()
                response.raise_for_status()
                if data.get("org_id") and data.get("project_id"):
                    self.org_id = data.get("org_id")
                    self.project_id = data.get("project_id")
                return data.get("user_email")
            else:
                logger.warning(
                    "OSS server returned empty response for /v1/ping/, skipping validation"
                )
                return None
        except Exception as e:
            logger.warning(f"Skipping API key validation for OSS server: {e}")
            return None

    def _prepare_params(self, kwargs=None):
        """Override _prepare_params to ensure user_id, agent_id, run_id are preserved for OSS server."""
        if kwargs is None:
            kwargs = {}

        # Add org_id and project_id if both are available
        if self.org_id and self.project_id:
            kwargs["org_id"] = self.org_id
            kwargs["project_id"] = self.project_id

        # Return kwargs with None values removed
        return {k: v for k, v in kwargs.items() if v is not None}


# NOTE:
# 참고용으로만 남겨두고 있습니다.
# add, search, get_all 메서드를 override해서 사용하면 속도가 매우 느려져서 사용하지 않고 있습니다.
class OSSMemoryClientOld(MemoryClient):
    """MemoryClient subclass for OSS servers that may not support /v1/ping/ endpoint.

    NOTE: This class is kept for reference purposes only.
    Currently, MemoryClient is used directly for better performance.
    """

    def __init__(self, api_key=None, host=None, **kwargs):
        """Initialize OSS MemoryClient, skipping API key validation if api_key is None."""
        self.api_key = api_key or os.getenv("MEM0_API_KEY")
        self.host = host or "https://api.mem0.ai"
        self.org_id = kwargs.get("org_id")
        self.project_id = kwargs.get("project_id")

        # For OSS, if no API key, use a dummy value to avoid ValueError
        if not self.api_key:
            logger.warning("No API key provided for OSS server, using dummy key for initialization")
            self.api_key = "oss-dummy-key"

        # Create MD5 hash of API key for user_id
        self.user_id = hashlib.md5(self.api_key.encode()).hexdigest()

        # Create httpx client
        self.client = httpx.Client(
            base_url=self.host,
            headers={
                "Authorization": f"Token {self.api_key}"
                if self.api_key != "oss-dummy-key"
                else None,
                "Mem0-User-ID": self.user_id,
            },
            timeout=300,
        )
        # Remove None headers
        self.client.headers = {k: v for k, v in self.client.headers.items() if v is not None}

        # Skip API key validation for OSS servers
        self.user_email = self._validate_api_key()

    def _validate_api_key(self):
        """Override to skip API key validation for OSS servers."""
        try:
            params = self._prepare_params()
            response = self.client.get("/v1/ping/", params=params)
            # Check if response has content before parsing JSON
            if response.text:
                data = response.json()
                response.raise_for_status()
                if data.get("org_id") and data.get("project_id"):
                    self.org_id = data.get("org_id")
                    self.project_id = data.get("project_id")
                return data.get("user_email")
            else:
                logger.warning(
                    "OSS server returned empty response for /v1/ping/, skipping validation"
                )
                return None
        except Exception as e:
            logger.warning(f"Skipping API key validation for OSS server: {e}")
            return None

        # Initialize project manager (may fail for OSS, but that's okay)
        try:
            self.project = Project(
                client=self.client,
                org_id=self.org_id,
                project_id=self.project_id,
                user_email=self.user_email,
            )
        except Exception as e:
            logger.warning(f"Could not initialize Project manager for OSS server: {e}")
            self.project = None

    def _prepare_payload(self, messages, kwargs):
        """Prepare the payload for the OSS server /v1/memories endpoint."""
        payload = {"messages": messages}

        # Add all relevant parameters from kwargs.
        # NOTE(mem0 1.0): `output_format` is dropped — the OSS server returns
        # the unified {"results": [...]} format natively.
        for key in [
            "user_id",
            "agent_id",
            "run_id",
            "metadata",
            "async_mode",
            "infer",
        ]:
            if key in kwargs:
                payload[key] = kwargs[key]

        return payload

    def add(self, messages, **kwargs) -> dict[str, Any]:
        """Override add to use /v1/ prefix without trailing slash for OSS servers."""
        # Handle different message input formats (align with OSS behavior)
        if isinstance(messages, str):
            messages = [{"role": "user", "content": messages}]
        elif isinstance(messages, dict):
            messages = [messages]
        elif not isinstance(messages, list):
            raise ValueError(
                f"messages must be str, dict, or list[dict], got {type(messages).__name__}"
            )

        # Preserve user_id, agent_id, run_id, infer from kwargs before _prepare_params
        explicit_user_id = kwargs.get("user_id")
        explicit_agent_id = kwargs.get("agent_id")
        explicit_run_id = kwargs.get("run_id")
        explicit_infer = kwargs.get("infer")

        kwargs = self._prepare_params(kwargs)

        # Restore explicit IDs and infer if they were provided
        if explicit_user_id is not None:
            kwargs["user_id"] = explicit_user_id
        if explicit_agent_id is not None:
            kwargs["agent_id"] = explicit_agent_id
        if explicit_run_id is not None:
            kwargs["run_id"] = explicit_run_id
        if explicit_infer is not None:
            kwargs["infer"] = explicit_infer

        # NOTE(mem0 1.0): output_format is no longer needed. The OSS server's
        # /v1/memories endpoint returns {"results": [...]} natively in 1.0+.
        # Set async_mode=True by default for better performance (can be overridden by kwargs)
        if "async_mode" not in kwargs:
            kwargs["async_mode"] = True
        payload = self._prepare_payload(messages, kwargs)
        # Use /v1/memories without trailing slash to avoid 307 redirect
        response = self.client.post("/v1/memories", json=payload)
        response.raise_for_status()
        if "metadata" in kwargs:
            del kwargs["metadata"]
        capture_client_event("client.add", self, {"keys": list(kwargs.keys()), "sync_type": "sync"})
        return response.json()

    def search(self, query: str, **kwargs) -> dict[str, Any]:
        """Override search to use /v1/search endpoint for OSS servers (POST method)."""
        params = self._prepare_params(kwargs)
        # OSS server may not support async_mode, so we remove it
        params.pop("async_mode", None)

        # Build payload according to OSS API: /v1/search POST
        # OSS API supports: query, user_id, agent_id, run_id, filters, limit, threshold
        payload = {"query": query}

        # Add supported parameters
        if "user_id" in params:
            payload["user_id"] = params.pop("user_id")
        if "agent_id" in params:
            payload["agent_id"] = params.pop("agent_id")
        if "run_id" in params:
            payload["run_id"] = params.pop("run_id")
        if "limit" in params:
            payload["limit"] = params.pop("limit")
        if "threshold" in params:
            payload["threshold"] = params.pop("threshold")
        if "filters" in params:
            filters = params.pop("filters")
            # Convert OR filters to AND format if needed (OSS may only support AND)
            if filters and isinstance(filters, dict):
                if "OR" in filters:
                    # Convert OR to AND format - take first clause as primary filter
                    or_clauses = filters["OR"]
                    if or_clauses and len(or_clauses) > 0:
                        # Use first clause as primary filter, add others as additional filters
                        payload["filters"] = {"AND": or_clauses}
                    else:
                        payload["filters"] = None
                else:
                    payload["filters"] = filters
            else:
                payload["filters"] = filters

        # Remove unsupported parameters (version, top_k, threshold, output_format, etc.)
        # These are not part of OSS API schema

        # OSS API: POST /v1/search (not /v1/memories/search)
        response = self.client.post("/v1/search", json=payload)
        response.raise_for_status()
        if "metadata" in kwargs:
            del kwargs["metadata"]
        capture_client_event(
            "client.search",
            self,
            {
                "api_version": "v1",
                "keys": list(kwargs.keys()),
                "sync_type": "sync",
            },
        )
        result = response.json()

        # Ensure v1.1 format (wrap raw list if needed)
        if isinstance(result, list):
            return {"results": result}
        return result

    def get_all(self, **kwargs) -> dict[str, Any]:
        """Override get_all to use /v1/memories GET endpoint for OSS servers."""
        params = self._prepare_params(kwargs)
        params.pop("async_mode", None)

        # Extract filters if present and convert to query params
        filters = kwargs.get("filters", {})
        query_params = {}

        # OSS API: GET /v1/memories with query parameters (user_id, agent_id, run_id)
        if "user_id" in params:
            query_params["user_id"] = params.pop("user_id")
        if "agent_id" in params:
            query_params["agent_id"] = params.pop("agent_id")
        if "run_id" in params:
            query_params["run_id"] = params.pop("run_id")

        # Include any remaining parameters from params (e.g. limit, sort_column, sort_direction)
        query_params.update(params)

        # Handle filters: if filters contain AND clauses, extract user_id/agent_id/run_id
        if filters and isinstance(filters, dict):
            if "AND" in filters:
                for clause in filters["AND"]:
                    if isinstance(clause, dict):
                        for key in ["user_id", "agent_id", "run_id"]:
                            if key in clause:
                                query_params[key] = clause[key]
            else:
                # Direct filters
                for key in ["user_id", "agent_id", "run_id"]:
                    if key in filters:
                        query_params[key] = filters[key]

        # OSS API: GET /v1/memories (not POST)
        response = self.client.get("/v1/memories", params=query_params)
        response.raise_for_status()
        if "metadata" in kwargs:
            del kwargs["metadata"]
        capture_client_event(
            "client.get_all",
            self,
            {
                "api_version": "v1",
                "keys": list(kwargs.keys()),
                "sync_type": "sync",
            },
        )
        result = response.json()

        # Ensure v1.1 format (wrap raw list if needed)
        if isinstance(result, list):
            return {"results": result}
        return result
