#
# Copyright (c) 2024-2026, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#

"""Langfuse exporter helpers for Pipecat OpenTelemetry tracing.

Pipecat tracing is OpenTelemetry-based. Langfuse supports OTLP ingestion on
`/api/public/otel` using Basic Auth (public/secret key). The exporter sends
to ``{endpoint}/v1/traces``; Langfuse expects that path.

This module keeps the integration optional:
- If OpenTelemetry isn't installed, it becomes a no-op.
- If the OTLP exporter package isn't installed, it becomes a no-op.

Environment variables (mirrors common Langfuse SDK conventions):
    LANGFUSE_ENABLED: Enable/disable Langfuse exporting (default: "false").
    LANGFUSE_PUBLIC_KEY: Langfuse public key (pk-lf-...).
    LANGFUSE_SECRET_KEY: Langfuse secret key (sk-lf-...).
    LANGFUSE_BASE_URL: Langfuse base URL, e.g. "http://localhost:3001".

Optional variables:
    LANGFUSE_OTEL_ENDPOINT: Override OTLP endpoint base (without /v1/traces).
        Default: "{LANGFUSE_BASE_URL}/api/public/otel"
    PIPECAT_OTEL_SERVICE_NAME: Overrides the OpenTelemetry service.name.
        Default: "pipecat"
    PIPECAT_LANGFUSE_CONSOLE_EXPORT: If "true", also export spans to console.

Troubleshooting:
    - 404: Self-hosted Langfuse must be v3.22.0 or newer. Set LANGFUSE_OTEL_ENDPOINT
      to the exact base URL (e.g. "http://localhost:3000/api/public/otel/v1/traces").
    - 401 Unauthorized: API keys are region-specific. Use LANGFUSE_BASE_URL
      https://cloud.langfuse.com for EU or https://us.cloud.langfuse.com for US,
      and ensure LANGFUSE_PUBLIC_KEY / LANGFUSE_SECRET_KEY are from the same
      project and region. Regenerate keys in the Langfuse dashboard if needed.
"""

from __future__ import annotations

import base64
import logging
import os
from typing import Optional

from loguru import logger as loguru_logger

from pipecat.utils.tracing.setup import is_tracing_available, setup_tracing

_log = logging.getLogger(__name__)


def _is_truthy(value: str | None) -> bool:
    if value is None:
        return False
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def _strip_key(value: str | None) -> str | None:
    """Strip whitespace, newlines, and optional surrounding quotes from an env key value."""
    if value is None:
        return None
    # Remove any newlines/carriage returns that can slip in from .env or shell
    value = value.replace("\r", "").replace("\n", "").strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in ('"', "'"):
        value = value[1:-1].strip()
    return value if value else None


def _langfuse_otlp_endpoint_from_env() -> str | None:
    override = os.getenv("LANGFUSE_OTEL_ENDPOINT")
    if override:
        return override.rstrip("/")

    base_url = os.getenv("LANGFUSE_BASE_URL")
    if not base_url:
        return None

    # Prefer the signal-specific traces endpoint since some OTLP exporters do not
    # automatically append "/v1/traces" when a custom endpoint is provided.
    return f"{base_url.rstrip('/')}/api/public/otel/v1/traces"


def get_langfuse_otlp_http_exporter():
    """Create an OTLP/HTTP span exporter configured for Langfuse.

    Returns:
        An OTLPSpanExporter instance, or None if disabled/misconfigured.
    """
    if not is_tracing_available():
        _log.warning("Langfuse: OpenTelemetry not available. Install with: uv sync --extra tracing")
        return None

    if not _is_truthy(os.getenv("LANGFUSE_ENABLED", "false")):
        _log.info("Langfuse: disabled (LANGFUSE_ENABLED is not true)")
        return None

    public_key = _strip_key(os.getenv("LANGFUSE_PUBLIC_KEY"))
    secret_key = _strip_key(os.getenv("LANGFUSE_SECRET_KEY"))
    endpoint = _langfuse_otlp_endpoint_from_env()

    if not public_key or not secret_key:
        _log.warning("Langfuse: missing LANGFUSE_PUBLIC_KEY or LANGFUSE_SECRET_KEY in env")
        return None
    if not endpoint:
        _log.warning("Langfuse: missing LANGFUSE_BASE_URL (or LANGFUSE_OTEL_ENDPOINT) in env")
        return None

    try:
        from opentelemetry.exporter.otlp.proto.http.trace_exporter import (  # type: ignore
            OTLPSpanExporter,
        )
        from opentelemetry.sdk.trace.export import SpanExportResult
    except Exception as e:
        _log.warning(
            "Langfuse: OTLP HTTP exporter not installed (%s). "
            "Install with: uv sync --extra tracing",
            e,
        )
        return None

    # Langfuse OTEL endpoint uses Basic Auth: username=public_key, password=secret_key.
    # Use only the headers dict; clear any env headers so a stale/wrong value does not override.
    for env_key in ("OTEL_EXPORTER_OTLP_HEADERS", "OTEL_EXPORTER_OTLP_TRACES_HEADERS"):
        os.environ.pop(env_key, None)
    token_raw = f"{public_key}:{secret_key}".encode("utf-8")
    token_b64 = base64.b64encode(token_raw).decode("ascii")
    headers = {"Authorization": f"Basic {token_b64}"}

    real = OTLPSpanExporter(endpoint=endpoint, headers=headers)

    class _LoggingOtlpExporter:
        """Wraps OTLPSpanExporter to log export success/failure for debugging."""

        def export(self, spans):
            try:
                result = real.export(spans)
                if result is SpanExportResult.FAILURE:
                    loguru_logger.warning(
                        "Langfuse OTLP export returned FAILURE for {} span(s)", len(spans)
                    )
                elif spans:
                    loguru_logger.info(
                        "Langfuse OTLP export sent {} span(s) to {}", len(spans), endpoint
                    )
                return result
            except Exception as e:
                loguru_logger.exception("Langfuse OTLP export failed: {}", e)
                return SpanExportResult.FAILURE

        def force_flush(self, timeout_millis=None):
            return getattr(real, "force_flush", lambda **kw: True)(timeout_millis=timeout_millis)

        def shutdown(self):
            return getattr(real, "shutdown", lambda: None)()

    return _LoggingOtlpExporter()


def setup_langfuse_tracing_from_env(
    *,
    default_service_name: str = "pipecat",
) -> tuple[bool, bool]:
    """Set up Pipecat OpenTelemetry tracing to export spans to Langfuse.

    This is a convenience wrapper around `setup_tracing(...)` that reads common
    Langfuse environment variables.

    Args:
        default_service_name: service.name to use if PIPECAT_OTEL_SERVICE_NAME is not set.

    Returns:
        (success, exporter_configured): success is True if tracing was set up;
        exporter_configured is True if spans will be sent to Langfuse (OTLP).
    """
    service_name = os.getenv("PIPECAT_OTEL_SERVICE_NAME", default_service_name)
    console_export = _is_truthy(os.getenv("PIPECAT_LANGFUSE_CONSOLE_EXPORT", "false"))
    exporter = get_langfuse_otlp_http_exporter()
    ok = setup_tracing(service_name=service_name, exporter=exporter, console_export=console_export)
    if ok and exporter is not None:
        _log.info(
            "Langfuse tracing enabled (OTLP -> %s)",
            _langfuse_otlp_endpoint_from_env() or "",
        )
    return (ok, exporter is not None)
