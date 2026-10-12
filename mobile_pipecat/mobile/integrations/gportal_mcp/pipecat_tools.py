"""Pipecat-native function-calling adapter for GPortal MCP tools.

Builds Pipecat ``FunctionSchema`` + handler pairs from a GPortal
``tools/list`` response. The wire protocol (SSE stream from the MCP
server, per-tool ``thread_id`` persistence via :class:`SessionThreadStore`)
is unchanged from earlier versions.

``thread_id`` is intentionally **not** exposed to the LLM — it is looked
up from the session store inside the handler.
"""

from __future__ import annotations

from typing import Any, Callable, Dict, List, Optional, Tuple

from loguru import logger
from pipecat.adapters.schemas.function_schema import FunctionSchema
from pipecat.services.llm_service import FunctionCallParams

from .client import GportalMCPClient
from .session import SessionThreadStore

HandlerRegistrar = Callable[[Any], None]


def create_gportal_pipecat_tools(
    client: GportalMCPClient,
    tool_schemas: List[Dict],
    session_store: Optional[SessionThreadStore] = None,
    session_id: Optional[str] = None,
) -> Tuple[List[FunctionSchema], HandlerRegistrar]:
    """Build Pipecat ``FunctionSchema`` list and handler registrar from GPortal ``tools/list``.

    Args:
        client: An initialised :class:`GportalMCPClient`.
        tool_schemas: List returned by :meth:`GportalMCPClient.list_tools`.
        session_store: Store for looking up / persisting per-agent thread IDs.
        session_id: Pipecat session identifier used as key into *session_store*.

    Returns:
        Tuple ``(schemas, register)``. ``register(llm)`` attaches every tool
        handler via ``llm.register_function(name, handler)`` on the supplied
        Pipecat LLM service.
    """
    schemas: List[FunctionSchema] = []
    handlers: Dict[str, Callable[[FunctionCallParams], Any]] = {}

    for schema in tool_schemas:
        tool_name = schema["name"]
        description = schema.get("description", f"Invoke {tool_name}")

        schemas.append(
            FunctionSchema(
                name=tool_name,
                description=description,
                properties={
                    "subagent_request": {
                        "type": "string",
                        "description": "Request text to pass to the sub-agent",
                    },
                },
                required=["subagent_request"],
            )
        )

        def _make_handler(name: str):
            async def _handler(params: FunctionCallParams) -> None:
                args = params.arguments or {}
                subagent_request = args.get("subagent_request", "")

                thread_id: Optional[str] = None
                mapping = session_store.get(session_id) if session_store and session_id else None
                if mapping:
                    thread_id = mapping.gportal_thread_ids.get(name)

                chunks: List[str] = []
                try:
                    async for event_type, data in client.call_tool_stream(
                        name, subagent_request, thread_id
                    ):
                        if event_type == "message" and data.get("content"):
                            chunks.append(data["content"])
                        elif event_type == "error":
                            raise RuntimeError(f"gportal error: {data.get('error')}")
                        elif event_type == "end":
                            new_tid = data.get("thread_id")
                            if new_tid and mapping:
                                mapping.gportal_thread_ids[name] = new_tid
                                logger.debug(f"Stored gportal thread_id for {name}: {new_tid}")
                            break
                except Exception as e:
                    partial = "".join(chunks).strip()
                    if partial:
                        logger.warning(
                            f"GPortal tool {name} stream error after partial output: {e}"
                        )
                        await params.result_callback(
                            partial
                            + "\n\n(참고: 도구 응답이 전송 중 끊겨 일부만 반영되었을 수 있어요.)"
                        )
                        return
                    logger.warning(f"GPortal tool {name} failed: {e}")
                    await params.result_callback(f"도구 실행 중 오류가 발생했습니다: {e}")
                    return

                await params.result_callback("".join(chunks))

            return _handler

        handlers[tool_name] = _make_handler(tool_name)

    def register(llm: Any) -> None:
        for name, handler in handlers.items():
            llm.register_function(name, handler)

    return schemas, register


def format_gportal_tool_descriptions(tool_schemas: List[Dict]) -> str:
    """Format tool schemas into a human-readable block for the system prompt.

    Args:
        tool_schemas: List returned by :meth:`GportalMCPClient.list_tools`.

    Returns:
        Formatted string listing each tool name and description.
    """
    lines = []
    for schema in tool_schemas:
        name = schema["name"]
        desc = schema.get("description", "")
        lines.append(f"  - {name}: {desc}")
    return "\n".join(lines)


__all__ = [
    "HandlerRegistrar",
    "create_gportal_pipecat_tools",
    "format_gportal_tool_descriptions",
]
