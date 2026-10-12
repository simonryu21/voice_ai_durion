"""Render catalog `ExecutionPath` templates with parameter values.

The catalog stores templates like::

    "https://map.naver.com/v5/search/{query}"

Given ``parameters = {"query": "강남역"}`` this module produces::

    "https://map.naver.com/v5/search/%EA%B0%95%EB%82%A8%EC%97%AD"

Parameter values are URL-encoded (RFC 3986) so the client gets a
ready-to-launch URI. Missing optional parameters are replaced with empty
strings; missing required parameters raise.
"""

from __future__ import annotations

import re
from typing import Any, Optional
from urllib.parse import quote

from pydantic import BaseModel, ConfigDict

from app_catalog.schema import CatalogApp, ExecutionPath, Platform

_PLACEHOLDER_RE = re.compile(r"\{([a-zA-Z_][a-zA-Z0-9_]*)\}")


class RenderedExecutionPath(BaseModel):
    """One execution path with placeholders filled in.

    Mirrors the Phase-2 protocol spec wire shape so this object can be
    dropped straight into the RTVI envelope payload.
    """

    model_config = ConfigDict(extra="forbid")

    type: str
    priority: int
    uri: Optional[str] = None
    action: Optional[str] = None
    extras: Optional[dict[str, str]] = None


def _format_template(template: str, parameters: dict[str, Any]) -> str:
    """Replace `{name}` placeholders with URL-encoded parameter values.

    Unknown placeholders are replaced with the empty string. This matches
    the design's tolerance for partially-extracted parameters (the
    AppConcierge LLM may omit optional ones).
    """

    def _replace(match: re.Match) -> str:
        key = match.group(1)
        value = parameters.get(key, "")
        if value is None:
            value = ""
        return quote(str(value), safe="")

    return _PLACEHOLDER_RE.sub(_replace, template)


def _validate_required_parameters(app: CatalogApp, parameters: dict[str, Any]) -> None:
    missing = [
        p.name
        for p in app.parameters
        if p.required
        and (
            p.name not in parameters
            or parameters[p.name] is None
            or (isinstance(parameters[p.name], str) and not parameters[p.name].strip())
        )
    ]
    if missing:
        raise ValueError(f"app `{app.app_id}` missing required parameters: {missing}")


def render_execution_paths(
    app: CatalogApp,
    parameters: dict[str, Any],
    *,
    platform: Platform = "android",
) -> list[RenderedExecutionPath]:
    """Render every execution path for the given platform.

    Returns the list sorted by ``priority`` ascending so the first entry
    is the client's preferred way to launch.

    Raises:
        ValueError: if a required parameter is missing or the catalog has
            no execution paths for the requested platform.
    """
    paths = app.execution.get(platform)
    if not paths:
        raise ValueError(f"app `{app.app_id}` has no execution paths for platform `{platform}`")

    _validate_required_parameters(app, parameters)

    rendered: list[RenderedExecutionPath] = []
    for path in sorted(paths, key=lambda p: p.priority):
        rendered.append(_render_one(path, parameters))
    return rendered


def _render_one(path: ExecutionPath, parameters: dict[str, Any]) -> RenderedExecutionPath:
    if path.type == "intent":
        uri = _format_template(path.uri_template, parameters) if path.uri_template else None
        return RenderedExecutionPath(
            type=path.type,
            priority=path.priority,
            uri=uri,
            action=path.action,
            extras={
                key: _PLACEHOLDER_RE.sub(
                    lambda match: str(parameters.get(match.group(1)) or ""), value
                )
                for key, value in path.extras.items()
            }
            if path.extras
            else None,
        )
    # applink / deeplink / scheme / universal_link all use `template`.
    template = path.template or ""
    return RenderedExecutionPath(
        type=path.type,
        priority=path.priority,
        uri=_format_template(template, parameters),
    )
