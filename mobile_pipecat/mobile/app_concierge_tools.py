"""Pipecat-native function-calling adapters for AppConcierge.

The main ``OpenAILLMService`` calls one of these functions directly — a
single LLM turn handles both supervisor and per-app dispatch.

Two builders are exposed:

- :func:`create_invoke_app_pipecat_tool` — one ``invoke_app`` function that
  takes ``app_id`` / ``parameters`` / ``spoken_intro``. Used as a drop-in
  bridge while the rest of the pipeline is being migrated.

- :func:`create_per_app_pipecat_tools` — one Pipecat function per candidate
  app (``open_<app_id>``), each with the catalog's typed parameter schema.
  The structured ``tools`` field carries the per-app parameter schemas, so
  the supervisor's prompt does not need them.

Both builders delegate the actual launch to the dispatcher returned by
:func:`app_handoff.make_app_invoke_dispatcher`, which handles TTS intro
queueing + RTVI server-message push.
"""

from __future__ import annotations

from typing import Any, Awaitable, Callable, Dict, List, Optional, Tuple

from loguru import logger
from pipecat.adapters.schemas.function_schema import FunctionSchema
from pipecat.services.llm_service import FunctionCallParams

from app_catalog.candidate_builder import AppCandidate, CandidateSet

AppInvokeDispatcher = Callable[[Dict[str, Any]], Awaitable[str]]
"""Same signature as :data:`app_handoff.dispatcher.AppInvokeDispatcher`."""

INVOKE_APP_TOOL_NAME = "invoke_app"

HandlerRegistrar = Callable[[Any], None]

# Map our catalog ``ParamType`` literal to a JSON-schema type.
_PARAM_TYPE_MAP = {"string": "string", "number": "number", "boolean": "boolean"}


def _normalize_invoke_app_args(args: Dict[str, Any], candidates: CandidateSet) -> Dict[str, Any]:
    """Fold top-level catalog params into ``parameters`` (legacy LLM quirk).

    Providers occasionally return ``{"app_id": "tmap", "destination": "…"}``
    instead of ``{"app_id": "tmap", "parameters": {"destination": "…"}}``.
    The dispatcher and URI renderer only read the nested ``parameters``
    dict, so we merge here.
    """
    out = dict(args)
    app_id = out.get("app_id")
    if not app_id:
        return out
    raw = out.get("parameters")
    params: Dict[str, Any] = dict(raw) if isinstance(raw, dict) else {}
    for app in candidates.all_apps():
        if app.app_id != app_id:
            continue
        for p in app.parameters:
            existing = params.get(p.name)
            if existing not in (None, "") and not (
                isinstance(existing, str) and not str(existing).strip()
            ):
                continue
            top = out.get(p.name)
            if top in (None, "") or (isinstance(top, str) and not top.strip()):
                continue
            params[p.name] = top
        out["parameters"] = params
        break
    return out


def _build_app_properties(app: AppCandidate) -> Tuple[Dict[str, Any], List[str]]:
    """Build the OpenAI tool ``properties`` block for one catalog app.

    Adds a ``spoken_intro`` string that the model fills in to greet the user
    right before the app launches.
    """
    properties: Dict[str, Any] = {}
    required: List[str] = []
    for p in app.parameters:
        properties[p.name] = {
            "type": _PARAM_TYPE_MAP.get(p.type, "string"),
            "description": p.description or "",
        }
        if p.required:
            required.append(p.name)
    properties["spoken_intro"] = {
        "type": "string",
        "description": (
            "앱을 열기 직전에 사용자에게 말할 1문장 한국어 안내. "
            "예: '네이버 지도로 강남역 길찾기 열어드릴게요.'"
        ),
    }
    return properties, required


def create_invoke_app_pipecat_tool(
    *,
    dispatcher: AppInvokeDispatcher,
    candidates: CandidateSet,
) -> Tuple[FunctionSchema, HandlerRegistrar]:
    """Build a single ``invoke_app`` Pipecat function (Phase-2 bridge).

    The handler validates the chosen ``app_id`` is in the candidate set,
    normalises the args, and delegates to ``dispatcher``. The dispatcher
    queues the TTS intro and pushes the RTVI envelope.

    Args:
        dispatcher: Async callable that performs the actual launch.
        candidates: Per-user candidate set used for arg normalisation /
            validation.

    Returns:
        Tuple ``(schema, register)``. ``register(llm)`` attaches the
        handler.
    """
    schema = FunctionSchema(
        name=INVOKE_APP_TOOL_NAME,
        description=(
            "선택한 앱과 파라미터로 사용자 폰에서 앱을 실행합니다. "
            "spoken_intro 문장이 음성으로 안내된 직후 실제 실행이 일어납니다. "
            "발화에서 적합한 앱이 명확하고 필수 파라미터가 모두 추출 가능할 때만 호출하세요."
        ),
        properties={
            "app_id": {
                "type": "string",
                "description": "카탈로그 app_id (예: tmap, naver_map, kakaotalk).",
            },
            "parameters": {
                "type": "object",
                "description": "앱별 카탈로그 스키마에 맞춘 파라미터 (예: {'destination': '강남역'}).",
            },
            "spoken_intro": {
                "type": "string",
                "description": (
                    "앱을 열기 직전에 사용자에게 말할 1문장 한국어 안내. "
                    "예: '네이버 지도로 강남역 길찾기 열어드릴게요.'"
                ),
            },
        },
        required=["app_id", "spoken_intro"],
    )

    async def _invoke_app(params: FunctionCallParams) -> None:
        args = params.arguments or {}
        normalised = _normalize_invoke_app_args(dict(args), candidates)
        app_id = normalised.get("app_id")
        if not app_id:
            await params.result_callback("[ERROR] invoke_app requires app_id")
            return

        # Validate against the per-user candidate set so the model can't
        # hallucinate apps outside the supervisor's allowed pool.
        valid_ids = {a.app_id for a in candidates.all_apps()}
        if app_id not in valid_ids:
            logger.warning("[invoke_app] unknown app_id={!r} (valid={})", app_id, sorted(valid_ids))
            await params.result_callback(
                f"[ERROR] unknown app_id `{app_id}` — choose from {sorted(valid_ids)}"
            )
            return

        try:
            result = await dispatcher(
                {
                    "app_id": app_id,
                    "parameters": normalised.get("parameters") or {},
                    "spoken_intro": normalised.get("spoken_intro") or "",
                }
            )
        except Exception as e:
            logger.error("[invoke_app] dispatcher failed: {}", e)
            await params.result_callback(f"[ERROR] dispatcher failed: {e}")
            return

        await params.result_callback(result)

    def register(llm: Any) -> None:
        llm.register_function(INVOKE_APP_TOOL_NAME, _invoke_app)

    return schema, register


def create_per_app_pipecat_tools(
    *,
    dispatcher: AppInvokeDispatcher,
    candidates: CandidateSet,
) -> Tuple[List[FunctionSchema], HandlerRegistrar]:
    """Build one ``open_<app_id>`` Pipecat function per candidate app.

    Each function carries the catalog's typed parameter schema so OpenAI
    structurally enforces required parameters (the model will ask the user
    in plain Korean instead of calling the function with a missing field).
    The handler delegates to ``dispatcher``.

    Args:
        dispatcher: Async callable that performs the actual launch.
        candidates: Per-user candidate set; one function is generated per
            ``app.app_id`` in :meth:`CandidateSet.all_apps`.

    Returns:
        Tuple ``(schemas, register)``. ``register(llm)`` attaches every
        per-app handler.
    """
    schemas: List[FunctionSchema] = []
    handlers: Dict[str, Callable[[FunctionCallParams], Any]] = {}
    seen: set[str] = set()

    for app in candidates.all_apps():
        if app.app_id in seen:
            # Same app might appear in multiple categories — register once.
            continue
        seen.add(app.app_id)

        properties, required = _build_app_properties(app)
        name = f"open_{app.app_id}"
        description = (
            f"{app.display_name}을(를) 엽니다. 언제 쓰나: {' '.join(app.use_when.split())}"
        )
        if app.examples:
            description += " 예시: " + ", ".join(f'"{ex}"' for ex in app.examples)
        # Always require spoken_intro so we never launch silently.
        required = list(dict.fromkeys([*required, "spoken_intro"]))

        schemas.append(
            FunctionSchema(
                name=name,
                description=description,
                properties=properties,
                required=required,
            )
        )

        def _make_handler(app_id: str):
            async def _handler(params: FunctionCallParams) -> None:
                args = dict(params.arguments or {})
                spoken_intro = (args.pop("spoken_intro", "") or "").strip()
                try:
                    result = await dispatcher(
                        {
                            "app_id": app_id,
                            "parameters": args,
                            "spoken_intro": spoken_intro,
                        }
                    )
                except Exception as e:
                    logger.error("[open_{}] dispatcher failed: {}", app_id, e)
                    await params.result_callback(f"[ERROR] dispatcher failed: {e}")
                    return
                await params.result_callback(result)

            return _handler

        handlers[name] = _make_handler(app.app_id)

    def register(llm: Any) -> None:
        for fn_name, handler in handlers.items():
            llm.register_function(fn_name, handler)

    return schemas, register


__all__ = [
    "AppInvokeDispatcher",
    "INVOKE_APP_TOOL_NAME",
    "create_invoke_app_pipecat_tool",
    "create_per_app_pipecat_tools",
]
