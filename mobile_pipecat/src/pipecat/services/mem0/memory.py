#
# Copyright (c) 2024-2026, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#

"""Mem0 memory service integration for Pipecat.

This module provides a memory service that integrates with Mem0 to store
and retrieve conversational memories, enhancing LLM context with relevant
historical information.
"""

import asyncio
import traceback
from collections.abc import Callable
from typing import Any, Optional

from loguru import logger
from mem0 import Memory, MemoryClient
from pydantic import BaseModel, Field

from pipecat.frames.frames import Frame, LLMContextFrame
from pipecat.processors.frame_processor import FrameDirection, FrameProcessor
from pipecat.services.mem0.oss_memory_client import OSSMemoryClient


class Mem0MemoryService(FrameProcessor):
    """A standalone memory service that integrates with Mem0.

    This service intercepts message frames in the pipeline, stores them in Mem0,
    and enhances context with relevant memories before passing them downstream.
    Supports both local and cloud-based Mem0 configurations.
    """

    class InputParams(BaseModel):
        """Configuration parameters for Mem0 memory service.

        Parameters:
            search_limit: Maximum number of memories to retrieve per query.
            search_threshold: Minimum similarity threshold for memory retrieval.
            system_prompt: Prefix text for memory context messages.
            add_as_system_message: Whether to add memories as system messages.
            position: Position to insert memory messages in context.
        """

        search_limit: int = Field(default=10, ge=1)
        search_threshold: float = Field(default=0.1, ge=0.0, le=1.0)
        system_prompt: str = Field(default="Based on previous conversations, I recall: \n\n")
        add_as_system_message: bool = Field(default=True)
        position: int = Field(default=1)
        request_timeout: float = Field(
            default=120.0, gt=0, description="Timeout (seconds) for Mem0 client requests"
        )

    def __init__(
        self,
        *,
        api_key: str | None = None,
        local_config: dict[str, Any] | None = None,
        user_id: str | None = None,
        agent_id: str | None = None,
        run_id: str | None = None,
        params: InputParams | None = None,
        host: str | None = None,
        is_meeting_mode_active_func: Callable[[str | None], bool] | None = None,
    ):
        """Initialize the Mem0 memory service.

        Args:
            api_key: The API key for accessing Mem0's cloud API.
            local_config: Local configuration for Mem0 client (alternative to cloud API).
            user_id: The user ID to associate with memories in Mem0.
            agent_id: The agent ID to associate with memories in Mem0.
            run_id: The run ID to associate with memories in Mem0.
            params: Configuration parameters for memory retrieval and storage.
            host: The host of the Mem0 server.
            is_meeting_mode_active_func: Optional function to check if meeting mode is active.
                If provided and returns True, memory search will be skipped during meeting mode.

        Raises:
            ValueError: If none of user_id, agent_id, or run_id are provided.
        """
        # Important: Call the parent class __init__ first
        super().__init__()

        params = params or Mem0MemoryService.InputParams()

        # local_config가 제공된 경우 Memory 클래스를 직접 사용 (서버 연결 없이 로컬에서 작동)
        # local_config가 None이 아니고 빈 딕셔너리가 아닌 경우에만 사용
        if local_config and isinstance(local_config, dict) and len(local_config) > 0:
            logger.info(
                f"Using local_config with Memory class (local_config keys: {list(local_config.keys())})"
            )
            self.memory_client = Memory.from_config(local_config)
        else:
            logger.info(
                f"Using MemoryClient with api_key={'provided' if api_key else 'None'}, host={host}"
            )
            if local_config is not None:
                logger.warning(f"local_config was provided but is empty or invalid: {local_config}")
            # OSS 서버의 경우: host가 기본값이 아니거나 api_key가 None이면 OSSCompatibleMemoryClient 사용
            # OSS 서버는 /v1/ping/에서 빈 응답을 반환할 수 있고, _prepare_params() 오버라이드가 필요함
            default_host = "https://api.mem0.ai"
            is_oss_server = (host and host != default_host) or api_key is None
            if is_oss_server:
                logger.info(f"Detected OSS server (host={host}), using OSSCompatibleMemoryClient")
                # OSS 서버는 API 키 검증을 하지 않으므로, api_key가 None이면 더미 키 사용
                oss_api_key = api_key or "oss-dummy-key"
                self.memory_client = OSSMemoryClient(api_key=oss_api_key, host=host)
            else:
                logger.info(f"Using standard MemoryClient for host={host}")
                self.memory_client = MemoryClient(api_key=api_key, host=host)
        # At least one of user_id, agent_id, or run_id must be provided
        if not any([user_id, agent_id, run_id]):
            raise ValueError("At least one of user_id, agent_id, or run_id must be provided")

        self.user_id = user_id
        self.agent_id = agent_id
        self.run_id = run_id
        self.search_limit = params.search_limit
        self.search_threshold = params.search_threshold
        self.system_prompt = params.system_prompt
        self.add_as_system_message = params.add_as_system_message
        self.position = params.position
        self.request_timeout = params.request_timeout
        self.last_query = None
        self._is_meeting_mode_active_func = is_meeting_mode_active_func
        self._background_storage_tasks = set()  # Track background storage tasks
        logger.info(f"Initialized Mem0MemoryService with {user_id=}, {agent_id=}, {run_id=}")

    async def _store_messages(self, messages: list[dict[str, Any]]):
        """Store messages in Mem0 as raw data.

        Args:
            messages: List of message dictionaries to store in memory.
        """
        # Always store asynchronously in background to avoid blocking the main pipeline
        # This prevents timeouts from affecting other operations and keeps the system responsive
        logger.trace(
            f"Storing {len(messages)} messages asynchronously in background (session_id={self.run_id})"
        )

        async def _store_in_background():
            """Store messages in background."""
            try:
                if not messages:
                    return

                # Get the first message (should be only one)
                msg = messages[0] if messages else None
                if not isinstance(msg, dict):
                    return

                role = msg.get("role", "")
                content = msg.get("content", "")

                # Only store user or assistant messages with string content
                if role not in ["user", "assistant"] or not content or not isinstance(content, str):
                    return

                # Skip context messages marked with our marker or containing the system prompt
                context_marker = "<!-- mem0_context -->"
                if context_marker in content or (
                    self.system_prompt and self.system_prompt in content
                ):
                    logger.debug(
                        f"Skipping context/prompt message to avoid recursion: {content[:50]}..."
                    )
                    return

                # If the message was merged, extract only the query part
                if "Current query: " in content:
                    content = content.split("Current query: ")[-1].strip()

                # Prepare message for storage (preserve speaker_id for meeting mode)
                message_to_store = {"role": role, "content": content}
                if "speaker_id" in msg:
                    message_to_store["speaker_id"] = msg.get("speaker_id")

                # Prepare metadata
                metadata = {"platform": "pipecat"}
                if self.run_id:
                    metadata["conversation_id"] = self.run_id
                    metadata["session_id"] = self.run_id

                # NOTE(mem0 1.0): output_format is set internally by the client
                # ({"results": [...]} is the only response format now), so we
                # don't pass it here. See docs/MIGRATION_GUIDE_v1.0.md.
                params = {
                    "messages": [message_to_store],
                    "infer": True,
                    "metadata": metadata,
                    "user_id": self.user_id,
                    "agent_id": self.agent_id,
                    "timeout": self.request_timeout,
                }

                # Call memory_client.add() in thread to avoid blocking
                result = await asyncio.to_thread(self.memory_client.add, **params)
                logger.debug(f"Mem0 add() response: {result}")
                logger.info(f"Successfully stored message in Mem0 (role: {role})")
            except Exception as e:
                error_str = str(e).lower()
                is_timeout = "timeout" in error_str or "timed out" in error_str
                if is_timeout:
                    # Use % formatting instead of f-string to avoid format spec errors
                    # when exception message contains curly braces
                    logger.error("Timeout storing message in Mem0: %s", e)
                else:
                    # Use % formatting instead of f-string to avoid format spec errors
                    # when exception message contains curly braces
                    logger.error("Error in background memory storage: %s", e, exc_info=True)

        # Create background task for async storage
        task = asyncio.create_task(_store_in_background())
        self._background_storage_tasks.add(task)

        # Clean up completed tasks
        task.add_done_callback(self._background_storage_tasks.discard)

    async def _retrieve_memories(self, query: str) -> list[dict[str, Any]]:
        """Retrieve relevant memories from Mem0 using a dual-strategy.

        Uses two strategies:
        1. Semantic search for relevance (Similarity)
        2. Recency fetch for latest context (Time)

        Args:
            query: The query to search for relevant memories.

        Returns:
            List of unique, relevant memory dictionaries matching the query and recent context.
        """
        try:
            logger.debug(f"Retrieving memories for query: {query}")

            # Strategy 1: Semantic search
            search_params = {
                "query": query,
                "user_id": self.user_id,
                "agent_id": self.agent_id,
                "limit": self.search_limit,
                "threshold": self.search_threshold,
                "timeout": self.request_timeout,
            }
            search_params = {k: v for k, v in search_params.items() if v is not None}

            # Strategy 2: Recency fetch using get_all() to avoid empty query warning
            # get_all() retrieves all memories filtered by user_id/agent_id, sorted by recency
            recent_params = {}
            if self.user_id:
                recent_params["user_id"] = self.user_id
            if self.agent_id:
                recent_params["agent_id"] = self.agent_id
            # Use get_all with limit to get recent memories without empty query warning
            recent_params["limit"] = (
                20  # Fetch last 20 items to ensure important memories are included
            )
            recent_params["timeout"] = self.request_timeout

            # NOTE(mem0 1.0): api_version is no longer a public parameter; the
            # client always uses v2 internally. See docs/MIGRATION_GUIDE_v1.0.md.

            # Execute both in parallel to save time
            vector_task = asyncio.to_thread(self.memory_client.search, **search_params)
            recent_task = asyncio.to_thread(self.memory_client.get_all, **recent_params)

            results_list = await asyncio.gather(vector_task, recent_task, return_exceptions=True)

            all_memories = []
            for i, res in enumerate(results_list):
                if isinstance(res, Exception):
                    logger.warning(f"Memory strategy {i + 1} failed: {res}")
                    continue

                strategy_name = "Similarity (search)" if i == 0 else "Recency (get_all)"

                # Extract list from dict if needed (common in OSS response)
                m_list = []
                if isinstance(res, list):
                    m_list = res
                    logger.debug(f"{strategy_name}: Got list response with {len(m_list)} items")
                elif isinstance(res, dict):
                    # Log response structure for debugging
                    logger.debug(f"{strategy_name}: Response keys: {list(res.keys())}")
                    m_list = res.get("results") or res.get("data") or res.get("items") or []
                    logger.debug(f"{strategy_name}: Extracted {len(m_list)} memories from response")
                    if not m_list:
                        # Log what we got instead
                        logger.warning(
                            f"{strategy_name}: No memories found in response. Response structure: {res}"
                        )
                    if not m_list and any(k in res for k in ["memory", "content", "text"]):
                        m_list = [res]
                        logger.debug(f"{strategy_name}: Treated single memory object as list")

                all_memories.extend(m_list)

            # Deduplicate and filter garbage
            unique_memories = []
            seen_hashes = set()
            seen_contents = set()

            # Lowercase query for comparison
            q_clean = query.strip().lower()

            for m in all_memories:
                if not isinstance(m, dict):
                    logger.debug(f"Skipping non-dict memory item: {type(m)}")
                    continue

                m_id = m.get("id")
                m_hash = m.get("hash") or m_id
                content = (m.get("memory") or m.get("content") or "").strip()
                content_lower = content.lower()

                # Log first few memory objects to debug structure
                if len(unique_memories) < 3:
                    logger.debug(
                        f"Memory object {len(unique_memories)}: keys={list(m.keys())}, content_preview={content[:50] if content else 'EMPTY'}"
                    )

                # Filter out garbage
                # 1. Skip if same as query (this is the "question pollution")
                if content_lower == q_clean or content_lower in q_clean or q_clean in content_lower:
                    if content.endswith("?"):
                        logger.trace(f"Skipping question-like memory pollution: {content}")
                        continue

                # 2. Skip duplicates
                if m_hash in seen_hashes or content_lower in seen_contents:
                    continue

                # 3. Skip very short meaningless items
                if len(content) < 3:
                    continue

                unique_memories.append(m)
                seen_hashes.add(m_hash)
                seen_contents.add(content_lower)

            # Limit final results
            final_results = unique_memories[: self.search_limit]
            logger.info(
                f"Retrieved {len(final_results)} total unique memories (Similarity + Recency)"
            )
            return final_results

        except Exception as e:
            logger.error(f"Error in dual-strategy memory retrieval: {e}")
            logger.debug(f"Traceback: {traceback.format_exc()}")
            return []

    async def _enhance_context_with_memories(
        self,
        context: Any,
        query: str,
        original_user_message: str = None,
        session_id: str | None = None,
    ):
        """Enhance the LLM context with relevant memories.

        Args:
            context: The LLM context to enhance.
            query: The current user query to search memories for.
            original_user_message: Original user message content.
            session_id: Optional session ID.
        """
        # Skip if this is the same query we just processed
        if self.last_query == query:
            return

        # Skip memory search during meeting mode
        if self._is_meeting_mode_active_func:
            try:
                is_active = self._is_meeting_mode_active_func(session_id)
                if is_active:
                    logger.debug(
                        f"Skipping memory search during meeting mode (session_id={session_id})"
                    )
                    return
            except Exception as e:
                logger.warning(f"Error checking meeting mode status: {e}")

        self.last_query = query

        memories = await self._retrieve_memories(query)
        if not memories:
            return

        # Format memories as context text
        memory_context = "Previous conversation context:\n\n"
        MAX_TOTAL_MEMORY_LENGTH = 4000

        for i, memory in enumerate(memories, 1):
            if len(memory_context) > MAX_TOTAL_MEMORY_LENGTH:
                break

            content = memory.get("memory", memory.get("content", ""))
            if content:
                memory_context += f"{i}. {content}\n\n"

        if memory_context == "Previous conversation context:\n\n":
            return

        # Clean up any previous context messages to avoid accumulation
        # Use a marker to identify our context messages
        context_marker = "<!-- mem0_context -->"
        messages = context.get_messages()
        # Remove messages with the marker in-place since context.messages may not have a setter
        for i in range(len(messages) - 1, -1, -1):
            content = messages[i].get("content", "")
            if isinstance(content, str) and context_marker in content:
                messages.pop(i)

        # Handle context enhancement based on params
        if self.add_as_system_message:
            # Insert at specified position
            messages.insert(
                self.position,
                {
                    "role": "system",
                    "content": f"{context_marker}{self.system_prompt}{memory_context}",
                },
            )
            logger.info(f"Added memories as a system message at position {self.position}")
        else:
            # Merge into the last user message
            for msg in reversed(messages):
                if msg["role"] == "user":
                    msg["content"] = (
                        f"{context_marker}{self.system_prompt}\n{memory_context}\n\nCurrent query: {msg['content']}"
                    )
                    logger.info("Merged memories into the last user message")
                    break

    async def process_frame(self, frame: Frame, direction: FrameDirection):
        """Process incoming frames, intercept context frames for memory integration.

        Args:
            frame: The incoming frame to process.
            direction: The direction of frame flow in the pipeline.
        """
        await super().process_frame(frame, direction)

        context = None

        if isinstance(frame, LLMContextFrame):
            context = frame.context

        if context:
            try:
                # Get the latest user message to use as a query for memory retrieval
                context_messages = context.get_messages()
                latest_user_message = None
                latest_assistant_message = None

                for message in reversed(context_messages):
                    if message.get("role") == "user" and isinstance(message.get("content"), str):
                        latest_user_message = message.get("content")
                        break

                # Check if the latest message is an assistant message (AI response was just added)
                if context_messages:
                    last_message = context_messages[-1]
                    if last_message.get("role") == "assistant" and isinstance(
                        last_message.get("content"), str
                    ):
                        latest_assistant_message = last_message.get("content")

                # Helper function to conditionally store messages (skip during meeting mode)
                async def _conditionally_store_latest_message():
                    """Store latest message if not in meeting mode."""
                    session_id = self.run_id or self.user_id

                    # Check if meeting mode is active
                    if self._is_meeting_mode_active_func:
                        try:
                            is_active = self._is_meeting_mode_active_func(session_id)
                            if is_active:
                                logger.debug(
                                    f"Skipping memory storage during meeting mode (session_id={session_id})"
                                )
                                return
                        except Exception as e:
                            logger.warning(f"Error checking meeting mode status for storage: {e}")
                            # If check fails, store normally

                    # Store the latest message
                    latest_message = [context_messages[-1]] if context_messages else []
                    if latest_message:
                        asyncio.create_task(self._store_messages(latest_message))

                if latest_user_message:
                    # Enhance context with memories before passing it downstream
                    # Pass the original user message so it can be merged with memories
                    session_id = self.run_id or self.user_id
                    await self._enhance_context_with_memories(
                        context,
                        latest_user_message,
                        original_user_message=latest_user_message,
                        session_id=session_id,
                    )
                    # Store only the latest message in Mem0 when user message is detected
                    # Skip storage during meeting mode (will be stored as batch when meeting ends)
                    await _conditionally_store_latest_message()
                elif latest_assistant_message:
                    # Store only the latest message when assistant message is added (to capture AI responses)
                    # Skip storage during meeting mode (will be stored as batch when meeting ends)
                    await _conditionally_store_latest_message()

                # Pass the enhanced context frame downstream
                await self.push_frame(frame, direction)
            except Exception as e:
                await self.push_error(
                    error_msg=f"Error processing with Mem0: {str(e)}", exception=e
                )
                await self.push_frame(frame, direction)  # Still pass the original frame through
        else:
            # For non-context frames, just pass them through
            await self.push_frame(frame, direction)
