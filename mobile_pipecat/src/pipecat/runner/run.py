#
# Copyright (c) 2024-2026, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#

"""Pipecat development runner.

This development runner executes Pipecat bots and provides the supporting
infrastructure they need - creating Daily rooms and tokens, managing WebRTC
connections, and setting up telephony webhook/WebSocket infrastructure. It
supports multiple transport types with a unified interface.

Install with::

    pip install pipecat-ai[runner]

All bots must implement a `bot(runner_args)` async function as the entry point.
The server automatically discovers and executes this function when connections
are established.

Single transport example::

    async def bot(runner_args: RunnerArguments):
        transport = DailyTransport(
            runner_args.room_url,
            runner_args.token,
            "Bot",
            DailyParams(...)
        )
        # Your bot logic here
        await run_pipeline(transport)

    if __name__ == "__main__":
        from pipecat.runner.run import main
        main()

Multiple transport example::

    async def bot(runner_args: RunnerArguments):
        # Type-safe transport detection
        if isinstance(runner_args, DailyRunnerArguments):
            transport = setup_daily_transport(runner_args)  # Your application code
        elif isinstance(runner_args, SmallWebRTCRunnerArguments):
            transport = setup_webrtc_transport(runner_args)  # Your application code
        elif isinstance(runner_args, WebSocketRunnerArguments):
            transport = setup_telephony_transport(runner_args)  # Your application code

        # Your bot implementation
        await run_pipeline(transport)

Supported transports:

- Daily - Creates rooms and tokens, runs bot as participant
- WebRTC - Provides local WebRTC interface with prebuilt UI
- Telephony - Handles webhook and WebSocket connections for Twilio, Telnyx, Plivo, Exotel

To run locally:

- WebRTC: `python bot.py -t webrtc`
- ESP32: `python bot.py -t webrtc --esp32 --host 192.168.1.100`
- Daily (server): `python bot.py -t daily`
- Daily (direct, testing only): `python bot.py -d`
- Telephony: `python bot.py -t twilio -x your_username.ngrok.io`
- Exotel: `python bot.py -t exotel` (no proxy needed, but ngrok connection to HTTP 7860 is required)
"""

import argparse
import asyncio
import mimetypes
import os
import sys
import time
import uuid
import warnings
from contextlib import asynccontextmanager
from dataclasses import dataclass, field
from http import HTTPMethod
from pathlib import Path
from typing import Any, Optional, TypedDict

import aiohttp
import uvicorn
from dotenv import load_dotenv
from fastapi import BackgroundTasks, FastAPI, Header, HTTPException, Request, WebSocket
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import (
    FileResponse,
    HTMLResponse,
    JSONResponse,
    RedirectResponse,
    Response,
)
from loguru import logger

load_dotenv(override=True)

# Suppress websockets deprecation warnings from uvicorn and websockets library
warnings.filterwarnings(
    "ignore",
    category=DeprecationWarning,
    message=".*websockets\\.legacy.*",
)
warnings.filterwarnings(
    "ignore",
    category=DeprecationWarning,
    message=".*websockets\\.server\\.WebSocketServerProtocol.*",
)

from pipecat.frames.frames import (
    LLMConfigureOutputFrame,
    STTMuteFrame,
    TranscriptionFrame,
)
from pipecat.pipeline.task import PipelineTask
from pipecat.runner.types import (
    DailyRunnerArguments,
    RunnerArguments,
    SmallWebRTCRunnerArguments,
    WebSocketRunnerArguments,
)
from pipecat.utils.time import time_now_iso8601

# Global storage for user locations (pc_id -> location info)
_user_locations: dict[str, dict[str, float]] = {}


def get_user_location(pc_id: str) -> dict[str, float] | None:
    """Return stored location for given peer connection ID."""
    return _user_locations.get(pc_id)


def get_all_user_locations() -> dict[str, dict[str, float]]:
    """Return a shallow copy of all stored user locations."""
    return _user_locations.copy()


@dataclass
class SessionContext:
    """Holds runtime information for an active SmallWebRTC session."""

    task: PipelineTask
    stt_muted: bool = False
    skip_tts: bool = False
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)


# Active session contexts keyed by transport pc_id (order preserved).
_session_contexts: dict[str, SessionContext] = {}


def register_session_context(pc_id: str, task: PipelineTask) -> None:
    """Register a pipeline task for the provided session pc_id."""
    if not pc_id:
        return
    _session_contexts[pc_id] = SessionContext(task=task)
    logger.debug(
        f"Registered session context for {pc_id}. Active sessions: {list(_session_contexts)}"
    )


def unregister_session_context(pc_id: str | None) -> None:
    """Remove a session context when the pipeline finishes."""
    if not pc_id:
        return
    if pc_id in _session_contexts:
        _session_contexts.pop(pc_id, None)
        logger.debug(
            f"Unregistered session context for {pc_id}. Active sessions: {list(_session_contexts)}"
        )


def get_session_context(pc_id: str | None = None) -> SessionContext | None:
    """Retrieve a session context by id or fall back to the most recent session."""
    if pc_id and pc_id in _session_contexts:
        return _session_contexts[pc_id]
    if _session_contexts:
        last_key = next(reversed(_session_contexts))
        return _session_contexts[last_key]
    return None


os.environ["ENV"] = "local"

TELEPHONY_TRANSPORTS = ["twilio", "telnyx", "plivo", "exotel"]

# Mirror Pipecat Cloud's 4-hour max session limit so dev rooms get cleaned up.
PIPECAT_ROOM_EXP_HOURS = 4.0

RUNNER_DOWNLOADS_FOLDER: str | None = None
RUNNER_HOST: str = "localhost"
RUNNER_PORT: int = 7860

app: FastAPI = FastAPI()
"""The FastAPI application instance.

Import this to add custom routes from other packages before calling
:func:`main`::

    from pipecat.runner.run import app, main

    @app.get("/my-route")
    async def my_route():
        return {"hello": "world"}

    if __name__ == "__main__":
        main()
"""


def _get_bot_module():
    """Get the bot module from the calling script."""
    import importlib.util

    # Get the main module (the file that was executed)
    main_module = sys.modules["__main__"]

    # Check if it has a bot function
    if hasattr(main_module, "bot"):
        return main_module

    # Try to import 'bot' module from current directory
    try:
        import bot  # type: ignore[import-untyped]

        return bot
    except ImportError:
        pass

    # Look for any .py file in current directory that has a bot function
    # (excluding server.py).
    cwd = os.getcwd()
    for filename in os.listdir(cwd):
        if filename.endswith(".py") and filename != "server.py":
            try:
                module_name = filename[:-3]  # Remove .py extension
                spec = importlib.util.spec_from_file_location(
                    module_name, os.path.join(cwd, filename)
                )
                if spec is None or spec.loader is None:
                    continue
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)

                if hasattr(module, "bot"):
                    return module
            except Exception:
                continue

    raise ImportError(
        "Could not find 'bot' function. Make sure your bot file has a 'bot' function."
    )


async def _run_telephony_bot(websocket: WebSocket, args: argparse.Namespace):
    """Run a bot for telephony transports."""
    bot_module = _get_bot_module()

    # Just pass the WebSocket - let the bot handle parsing
    runner_args = WebSocketRunnerArguments(websocket=websocket, session_id=str(uuid.uuid4()))
    runner_args.cli_args = args

    await bot_module.bot(runner_args)


def create_app() -> FastAPI:
    """Create FastAPI app with transport-specific routes.

    This function can be called from bot scripts to create an app instance
    that can be imported by uvicorn workers. It reads settings from environment
    variables that are set by the main() function.

    Returns:
        FastAPI app instance
    """
    # Read settings from environment variables (set by main())
    args = argparse.Namespace(
        transport=os.getenv("RUNNER_TRANSPORT", "webrtc"),
        host=os.getenv("RUNNER_HOST", RUNNER_HOST),
        proxy=os.getenv("RUNNER_PROXY"),
        esp32=os.getenv("RUNNER_ESP32") == "1",
        whatsapp=os.getenv("RUNNER_WHATSAPP") == "1",
        folder=os.getenv("RUNNER_FOLDER", RUNNER_DOWNLOADS_FOLDER),
        dialin=False,
    )
    return _create_server_app(args)


def _create_server_app(args: argparse.Namespace):
    """Create FastAPI app with transport-specific routes."""
    app = FastAPI()

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Set up transport-specific routes
    if args.transport == "webrtc":
        _setup_webrtc_routes(app, args)
        if args.whatsapp:
            _setup_whatsapp_routes(app, args)
    elif args.transport == "daily":
        _setup_daily_routes(app, args)
    elif args.transport in TELEPHONY_TRANSPORTS:
        _setup_telephony_routes(app, args)
    else:
        logger.warning(f"Unknown transport type: {args.transport}")

    return app


def _resolve_download_path(folder: str, filename: str) -> Path:
    """Resolve a download path and ensure it stays within the downloads folder."""
    allowed_base = Path(folder).resolve()
    file_path = (allowed_base / filename).resolve()

    if not file_path.is_relative_to(allowed_base):
        raise HTTPException(status_code=403, detail="Access denied")

    return file_path


def _setup_webrtc_routes(app: FastAPI, args: argparse.Namespace):
    """Set up WebRTC-specific routes."""
    try:
        from pipecat_ai_small_webrtc_prebuilt.frontend import SmallWebRTCPrebuiltUI

        from pipecat.transports.smallwebrtc.connection import SmallWebRTCConnection
        from pipecat.transports.smallwebrtc.request_handler import (
            IceCandidate,
            SmallWebRTCPatchRequest,
            SmallWebRTCRequest,
            SmallWebRTCRequestHandler,
        )
    except ImportError as e:
        logger.error(f"WebRTC transport dependencies not installed: {e}")
        return

    class IceServer(TypedDict, total=False):
        urls: str | list[str]

    class IceConfig(TypedDict):
        iceServers: list[IceServer]

    class StartBotResult(TypedDict, total=False):
        sessionId: str
        iceConfig: IceConfig | None

    # In-memory store of active sessions: session_id -> session info
    active_sessions: dict[str, dict[str, Any]] = {}

    # Mount the frontend (serves index.html, geolocation.js, and other assets)
    # Use local frontend if specified, otherwise use prebuilt package
    frontend_dist_path = os.getenv("FRONTEND_DIST_PATH")
    if frontend_dist_path:
        from fastapi.staticfiles import StaticFiles

        local_frontend_path = Path(frontend_dist_path)
        if local_frontend_path.exists():
            logger.info(f"Mounting local frontend from: {local_frontend_path}")
            app.mount(
                "/client", StaticFiles(directory=str(local_frontend_path), html=True), name="client"
            )
        else:
            logger.warning(
                f"Local frontend path does not exist: {local_frontend_path}, falling back to prebuilt"
            )
            app.mount("/client", SmallWebRTCPrebuiltUI)
    else:
        app.mount("/client", SmallWebRTCPrebuiltUI)

    @app.get("/", include_in_schema=False)
    async def root_index():
        """Serve custom index page."""
        # Try to find index.html in the bot's directory
        bot_module = _get_bot_module()
        bot_file_path = Path(bot_module.__file__).parent
        index_html_path = bot_file_path / "index.html"

        if index_html_path.exists():
            return FileResponse(
                path=str(index_html_path),
                media_type="text/html",
                headers={"Cache-Control": "no-cache"},
            )
        else:
            # Fallback to redirect if custom index.html not found
            logger.info(f"index.html not found at {index_html_path}, redirecting to /client/")
            return RedirectResponse(url="/client/")

    @app.get("/files/{filename:path}")
    async def download_file(filename: str):
        """Handle file downloads."""
        if not args.folder:
            logger.warning(f"Attempting to download {filename}, but downloads folder not setup.")
            raise HTTPException(404)

        file_path = _resolve_download_path(args.folder, filename)
        if not file_path.exists():
            raise HTTPException(404)

        media_type, _ = mimetypes.guess_type(file_path)

        return FileResponse(path=file_path, media_type=media_type, filename=file_path.name)

    # Initialize the SmallWebRTC request handler
    small_webrtc_handler: SmallWebRTCRequestHandler = SmallWebRTCRequestHandler(
        esp32_mode=args.esp32, host=args.host
    )

    @app.post("/api/offer")
    async def offer(
        request: Request,
        background_tasks: BackgroundTasks,
        session_id: str | None = None,
    ):
        """Handle WebRTC offer requests via SmallWebRTCRequestHandler.

        Accepts both snake_case and camelCase (e.g. requestData, pcId, restartPc).
        """
        try:
            data = await request.json()
        except Exception as e:
            raise HTTPException(status_code=422, detail=f"Invalid JSON body: {e!s}") from e
        if not isinstance(data, dict):
            raise HTTPException(status_code=422, detail="Request body must be a JSON object")
        try:
            webrtc_request = SmallWebRTCRequest.from_dict(data)
        except (TypeError, KeyError) as e:
            raise HTTPException(
                status_code=422,
                detail=f"Missing or invalid field (sdp and type required): {e!s}",
            ) from e

        # When called via the /sessions/{session_id}/api/offer proxy the
        # session_id is threaded through; for direct /api/offer calls we mint
        # one so bots see a stable identifier in either path.
        resolved_session_id = session_id or str(uuid.uuid4())

        # Prepare runner arguments with the callback to run your bot
        async def webrtc_connection_callback(connection: SmallWebRTCConnection):
            bot_module = _get_bot_module()

            runner_args = SmallWebRTCRunnerArguments(
                webrtc_connection=connection,
                body=webrtc_request.request_data,
                session_id=resolved_session_id,
            )
            runner_args.cli_args = args
            background_tasks.add_task(bot_module.bot, runner_args)

        # Delegate handling to SmallWebRTCRequestHandler
        answer = await small_webrtc_handler.handle_web_request(
            request=webrtc_request,
            webrtc_connection_callback=webrtc_connection_callback,
        )
        return answer

    @app.patch("/api/offer")
    async def ice_candidate(request: Request):
        """Handle WebRTC new ice candidate requests. Accepts both snake_case and camelCase (pc_id/pcId, sdp_mid/sdpMid, sdp_mline_index/sdpMLineIndex)."""
        try:
            data = await request.json()
            pc_id = data.get("pc_id") or data.get("pcId")
            raw_candidates = data.get("candidates", [])
            candidates = []
            for c in raw_candidates:
                if isinstance(c, dict):
                    candidates.append(
                        IceCandidate(
                            candidate=c.get("candidate", ""),
                            sdp_mid=c.get("sdp_mid") or c.get("sdpMid", ""),
                            sdp_mline_index=int(
                                c.get("sdp_mline_index", c.get("sdpMLineIndex", 0))
                            ),
                        )
                    )
            patch_request = SmallWebRTCPatchRequest(pc_id=pc_id or "", candidates=candidates)
            logger.debug(f"Received patch request: {patch_request}")
            await small_webrtc_handler.handle_patch_request(patch_request)
            return {"status": "success"}
        except Exception as e:
            logger.error(f"Error parsing PATCH /api/offer request: {e}")
            raise HTTPException(status_code=400, detail=f"Invalid request: {str(e)}")

    @app.post("/api/location")
    async def receive_location(request: Request):
        """Receive user's geolocation information."""
        global _user_locations
        try:
            data = await request.json()
            pc_id = data.get("pc_id")
            latitude = data.get("latitude")
            longitude = data.get("longitude")

            if pc_id and latitude is not None and longitude is not None:
                _user_locations[pc_id] = {
                    "latitude": latitude,
                    "longitude": longitude,
                    "timestamp": data.get("timestamp"),
                }
                logger.info(f"Stored location for {pc_id}: lat={latitude}, lon={longitude}")
                return {"status": "success", "message": "Location stored"}
            else:
                return {"status": "error", "message": "Missing required fields"}
        except Exception as e:
            logger.error(f"Error receiving location: {e}")
            return {"status": "error", "message": str(e)}

    @app.post("/api/text")
    async def receive_text_input(request: Request):
        """Receive a text message from the frontend and forward it to the active pipeline."""
        try:
            data = await request.json()
        except Exception as e:
            logger.error(f"Error parsing text input payload: {e}")
            raise HTTPException(status_code=400, detail="Invalid JSON payload") from e

        text = (data.get("text") or "").strip()
        pc_id = data.get("pc_id")

        if not text:
            raise HTTPException(status_code=400, detail="Text input cannot be empty")

        session_context = get_session_context(pc_id)
        if not session_context:
            raise HTTPException(status_code=404, detail="No active session available")

        message_id = str(uuid.uuid4())
        # Prefer authenticated user_id from OAuth middleware (if present).
        # Fallback to explicit payload field, and finally to "anonymous".
        state_user_id = getattr(request.state, "user_id", None)
        user_id = state_user_id or data.get("user_id") or data.get("userId") or "anonymous"

        async with session_context.lock:
            # Synthesize a transcription frame so the pipeline records this input like STT.
            transcription_frame = TranscriptionFrame(
                text=text,
                user_id=user_id,
                timestamp=time_now_iso8601(),
            )
            await session_context.task.queue_frame(transcription_frame)

        logger.info(
            f"Queued text input (as transcription) for user {user_id} (message_id={message_id}): {text}"
        )
        return {"status": "success", "message_id": message_id}

    @app.post("/api/stt")
    async def toggle_stt(request: Request):
        """Enable or disable STT processing for an active session."""
        try:
            data = await request.json()
        except Exception as e:
            logger.error(f"Error parsing STT toggle payload: {e}")
            raise HTTPException(status_code=400, detail="Invalid JSON payload") from e

        if "enabled" not in data:
            raise HTTPException(status_code=400, detail="'enabled' field is required")

        enabled = bool(data.get("enabled"))
        pc_id = data.get("pc_id")

        session_context = get_session_context(pc_id)
        if not session_context:
            raise HTTPException(status_code=404, detail="No active session available")

        async with session_context.lock:
            mute = not enabled
            if session_context.stt_muted == mute:
                logger.debug(
                    f"STT already {'disabled' if mute else 'enabled'} for session {pc_id or 'latest'}"
                )
                return {"status": "success", "enabled": enabled}

            await session_context.task.queue_frame(STTMuteFrame(mute=mute))
            session_context.stt_muted = mute

        logger.info(f"STT {'enabled' if enabled else 'disabled'} for session {pc_id or 'latest'}")
        return {"status": "success", "enabled": enabled}

    @app.post("/api/tts")
    async def toggle_tts(request: Request):
        """Enable or disable TTS responses for an active session."""
        try:
            data = await request.json()
        except Exception as e:
            logger.error(f"Error parsing TTS toggle payload: {e}")
            raise HTTPException(status_code=400, detail="Invalid JSON payload") from e

        if "enabled" not in data:
            raise HTTPException(status_code=400, detail="'enabled' field is required")

        enabled = bool(data.get("enabled"))
        pc_id = data.get("pc_id")

        session_context = get_session_context(pc_id)
        if not session_context:
            raise HTTPException(status_code=404, detail="No active session available")

        async with session_context.lock:
            skip_tts = not enabled
            if session_context.skip_tts == skip_tts:
                logger.debug(
                    f"TTS already {'disabled' if skip_tts else 'enabled'} for session {pc_id or 'latest'}"
                )
                return {"status": "success", "enabled": enabled}

            await session_context.task.queue_frame(LLMConfigureOutputFrame(skip_tts=skip_tts))
            session_context.skip_tts = skip_tts

        logger.info(f"TTS {'enabled' if enabled else 'disabled'} for session {pc_id or 'latest'}")
        return {"status": "success", "enabled": enabled}

    @app.post("/start")
    async def rtvi_start(request: Request):
        """Mimic Pipecat Cloud's /start endpoint."""
        # Parse the request body
        try:
            request_data = await request.json()
            logger.debug(f"Received request: {request_data}")
        except Exception as e:
            logger.error(f"Failed to parse request body: {e}")
            request_data = {}

        # Store session info immediately in memory, replicate the behavior expected on Pipecat Cloud
        session_id = str(uuid.uuid4())
        active_sessions[session_id] = request_data.get("body", {})

        result: StartBotResult = {"sessionId": session_id}
        if request_data.get("enableDefaultIceServers"):
            result["iceConfig"] = IceConfig(
                iceServers=[IceServer(urls=["stun:stun.l.google.com:19302"])]
            )

        return result

    @app.api_route(
        "/sessions/{session_id}/{path:path}",
        methods=["GET", "POST", "PUT", "PATCH", "DELETE"],
    )
    async def proxy_request(
        session_id: str, path: str, request: Request, background_tasks: BackgroundTasks
    ):
        """Mimic Pipecat Cloud's proxy."""
        active_session = active_sessions.get(session_id)
        if active_session is None:
            return JSONResponse(
                content={"error": "Invalid or not-yet-ready session_id"}, status_code=404
            )

        if path.endswith("api/offer"):
            # For POST requests, delegate to the offer endpoint handler
            if request.method == HTTPMethod.POST.value:
                try:
                    return await offer(request, background_tasks)
                except Exception as e:
                    logger.error(f"Failed to handle WebRTC offer request: {e}")
                    return JSONResponse(
                        content={"error": f"Invalid WebRTC request: {str(e)}"}, status_code=400
                    )
            elif request.method == HTTPMethod.PATCH.value:
                try:
                    return await ice_candidate(request)
                except HTTPException:
                    raise
                except Exception as e:
                    logger.error(f"Failed to parse WebRTC patch request: {e}")
                    return JSONResponse(
                        content={"error": f"Invalid WebRTC request: {str(e)}"}, status_code=400
                    )

        logger.info(f"Received request for path: {path}")
        return Response(status_code=200)

    @asynccontextmanager
    async def smallwebrtc_lifespan(app: FastAPI):
        """Manage FastAPI application lifecycle and cleanup connections."""
        yield
        await small_webrtc_handler.close()

    # Add the SmallWebRTC lifespan to the app
    _add_lifespan_to_app(app, smallwebrtc_lifespan)


def _add_lifespan_to_app(app: FastAPI, new_lifespan):
    """Add a new lifespan context manager to the app, combining with existing if present.

    Args:
        app: The FastAPI application instance
        new_lifespan: The new lifespan context manager to add
    """
    if hasattr(app.router, "lifespan_context") and app.router.lifespan_context is not None:
        # If there's already a lifespan context, combine them
        existing_lifespan = app.router.lifespan_context

        @asynccontextmanager
        async def combined_lifespan(app: FastAPI):
            async with existing_lifespan(app):
                async with new_lifespan(app):
                    yield

        app.router.lifespan_context = combined_lifespan
    else:
        # No existing lifespan, use the new one
        app.router.lifespan_context = new_lifespan


def _setup_whatsapp_routes(app: FastAPI, args: argparse.Namespace):
    """Set up WhatsApp-specific routes."""
    required_vars = [
        "WHATSAPP_APP_SECRET",
        "WHATSAPP_PHONE_NUMBER_ID",
        "WHATSAPP_TOKEN",
        "WHATSAPP_WEBHOOK_VERIFICATION_TOKEN",
    ]
    missing = [v for v in required_vars if not os.getenv(v)]
    if missing:
        missing_list = "\n    ".join(missing)
        logger.error(
            f"""Missing required environment variables for WhatsApp transport:
    {missing_list}
            """
        )
        return

    WHATSAPP_APP_SECRET = os.environ["WHATSAPP_APP_SECRET"]
    WHATSAPP_PHONE_NUMBER_ID = os.environ["WHATSAPP_PHONE_NUMBER_ID"]
    WHATSAPP_TOKEN = os.environ["WHATSAPP_TOKEN"]
    WHATSAPP_WEBHOOK_VERIFICATION_TOKEN = os.environ["WHATSAPP_WEBHOOK_VERIFICATION_TOKEN"]

    try:
        from pipecat.transports.smallwebrtc.connection import SmallWebRTCConnection
        from pipecat.transports.whatsapp.api import WhatsAppWebhookRequest
        from pipecat.transports.whatsapp.client import WhatsAppClient
    except ImportError as e:
        logger.error(f"WhatsApp transport dependencies not installed: {e}")
        return

    # Global WhatsApp client instance
    whatsapp_client: WhatsAppClient | None = None

    @app.get(
        "/whatsapp",
        summary="Verify WhatsApp webhook",
        description="Handles WhatsApp webhook verification requests from Meta",
    )
    async def verify_webhook(request: Request):
        """Verify WhatsApp webhook endpoint.

        This endpoint is called by Meta's WhatsApp Business API to verify
        the webhook URL during setup. It validates the verification token
        and returns the challenge parameter if successful.
        """
        if whatsapp_client is None:
            logger.error("WhatsApp client is not initialized")
            raise HTTPException(status_code=503, detail="Service unavailable")

        params = dict(request.query_params)
        logger.debug(f"Webhook verification request received with params: {list(params.keys())}")

        try:
            result = await whatsapp_client.handle_verify_webhook_request(
                params=params, expected_verification_token=WHATSAPP_WEBHOOK_VERIFICATION_TOKEN
            )
            logger.info("Webhook verification successful")
            return result
        except ValueError as e:
            logger.warning(f"Webhook verification failed: {e}")
            raise HTTPException(status_code=403, detail="Verification failed")

    @app.post(
        "/whatsapp",
        summary="Handle WhatsApp webhook events",
        description="Processes incoming WhatsApp messages and call events",
    )
    async def whatsapp_webhook(
        body: WhatsAppWebhookRequest,
        background_tasks: BackgroundTasks,
        request: Request,
        x_hub_signature_256: str = Header(None),
    ):
        """Handle incoming WhatsApp webhook events.

        For call events, establishes WebRTC connections and spawns bot instances
        in the background to handle real-time communication.
        """
        if whatsapp_client is None:
            logger.error("WhatsApp client is not initialized")
            raise HTTPException(status_code=503, detail="Service unavailable")

        # Validate webhook object type
        if body.object != "whatsapp_business_account":
            logger.warning(f"Invalid webhook object type: {body.object}")
            raise HTTPException(status_code=400, detail="Invalid object type")

        logger.debug(f"Processing WhatsApp webhook: {body.model_dump()}")

        async def connection_callback(connection: SmallWebRTCConnection):
            """Handle new WebRTC connections from WhatsApp calls.

            Called when a WebRTC connection is established for a WhatsApp call.
            Spawns a bot instance to handle the conversation.

            Args:
                connection: The established WebRTC connection
            """
            bot_module = _get_bot_module()
            runner_args = SmallWebRTCRunnerArguments(
                webrtc_connection=connection, session_id=str(uuid.uuid4())
            )
            runner_args.cli_args = args
            background_tasks.add_task(bot_module.bot, runner_args)

        try:
            # Process the webhook request
            raw_body = await request.body()
            result = await whatsapp_client.handle_webhook_request(
                body, connection_callback, sha256_signature=x_hub_signature_256, raw_body=raw_body
            )
            logger.debug(f"Webhook processed successfully: {result}")
            return {"status": "success", "message": "Webhook processed successfully"}
        except ValueError as ve:
            logger.warning(f"Invalid webhook request format: {ve}")
            raise HTTPException(status_code=400, detail=f"Invalid request: {str(ve)}")
        except Exception as e:
            logger.error(f"Internal error processing webhook: {e}")
            raise HTTPException(status_code=500, detail="Internal server error processing webhook")

    @asynccontextmanager
    async def whatsapp_lifespan(app: FastAPI):
        """Manage WhatsApp client lifecycle and cleanup connections."""
        nonlocal whatsapp_client

        # Initialize WhatsApp client with persistent HTTP session
        async with aiohttp.ClientSession() as session:
            whatsapp_client = WhatsAppClient(
                whatsapp_token=WHATSAPP_TOKEN,
                whatsapp_secret=WHATSAPP_APP_SECRET,
                phone_number_id=WHATSAPP_PHONE_NUMBER_ID,
                session=session,
            )
            logger.info("WhatsApp client initialized successfully")

            try:
                yield  # Run the application
            finally:
                # Cleanup all active calls on shutdown
                logger.info("Cleaning up WhatsApp client resources...")
                if whatsapp_client:
                    await whatsapp_client.terminate_all_calls()
                logger.info("WhatsApp cleanup completed")

    # Add the WhatsApp lifespan to the app
    _add_lifespan_to_app(app, whatsapp_lifespan)


def _setup_daily_routes(app: FastAPI, args: argparse.Namespace):
    """Set up Daily-specific routes."""

    @app.get("/")
    async def create_room_and_start_agent():
        """Launch a Daily bot and redirect to room."""
        print("Starting bot with Daily transport and redirecting to Daily room")

        import aiohttp

        from pipecat.runner.daily import configure

        async with aiohttp.ClientSession() as session:
            room_url, token = await configure(session, room_exp_duration=PIPECAT_ROOM_EXP_HOURS)

            # Start the bot in the background with empty body for GET requests
            bot_module = _get_bot_module()
            runner_args = DailyRunnerArguments(
                room_url=room_url, token=token, session_id=str(uuid.uuid4())
            )
            runner_args.cli_args = args
            asyncio.create_task(bot_module.bot(runner_args))
            return RedirectResponse(room_url)

    @app.post("/start")
    async def start_agent(request: Request):
        """Handler for /start endpoints.

        Expects POST body like::
            {
                "createDailyRoom": true,
                "dailyRoomProperties": { "start_video_off": true },
                "dailyMeetingTokenProperties": { "is_owner": true, "user_name": "Bot" },
                "body": { "custom_data": "value" }
            }
        """
        print("Starting bot with Daily transport")

        # Parse the request body
        try:
            request_data = await request.json()
            logger.debug(f"Received request: {request_data}")
        except Exception as e:
            logger.error(f"Failed to parse request body: {e}")
            request_data = {}

        create_daily_room = request_data.get("createDailyRoom", False)
        body = request_data.get("body", {})
        daily_room_properties_dict = request_data.get("dailyRoomProperties", None)
        daily_token_properties_dict = request_data.get("dailyMeetingTokenProperties", None)

        bot_module = _get_bot_module()

        existing_room_url = os.getenv("DAILY_ROOM_URL")

        session_id = str(uuid.uuid4())
        result = None

        # Configure room if:
        # 1. Explicitly requested via createDailyRoom in payload
        # 2. Using pre-configured room from DAILY_ROOM_URL env var
        if create_daily_room or existing_room_url:
            import aiohttp

            from pipecat.runner.daily import configure
            from pipecat.transports.daily.utils import (
                DailyMeetingTokenProperties,
                DailyRoomProperties,
            )

            async with aiohttp.ClientSession() as session:
                # Parse dailyRoomProperties if provided
                room_properties = None
                if daily_room_properties_dict:
                    # Apply Pipecat Cloud's session policy if caller didn't override.
                    daily_room_properties_dict.setdefault(
                        "exp", time.time() + PIPECAT_ROOM_EXP_HOURS * 3600
                    )
                    daily_room_properties_dict.setdefault("eject_at_room_exp", True)
                    try:
                        room_properties = DailyRoomProperties(**daily_room_properties_dict)
                        logger.debug(f"Using custom room properties: {room_properties}")
                    except Exception as e:
                        logger.error(f"Failed to parse dailyRoomProperties: {e}")
                        # Continue without custom properties

                # Parse dailyMeetingTokenProperties if provided
                token_properties = None
                if daily_token_properties_dict:
                    try:
                        token_properties = DailyMeetingTokenProperties(
                            **daily_token_properties_dict
                        )
                        logger.debug(f"Using custom token properties: {token_properties}")
                    except Exception as e:
                        logger.error(f"Failed to parse dailyMeetingTokenProperties: {e}")
                        # Continue without custom properties

                room_url, token = await configure(
                    session,
                    room_exp_duration=PIPECAT_ROOM_EXP_HOURS,
                    room_properties=room_properties,
                    token_properties=token_properties,
                )
                runner_args = DailyRunnerArguments(
                    room_url=room_url, token=token, body=body, session_id=session_id
                )
                result = {
                    "dailyRoom": room_url,
                    "dailyToken": token,
                    "sessionId": session_id,
                }
        else:
            runner_args = RunnerArguments(body=body, session_id=session_id)

        # Update CLI args.
        runner_args.cli_args = args

        # Start the bot in the background
        asyncio.create_task(bot_module.bot(runner_args))

        return result

    if args.dialin:

        @app.post("/daily-dialin-webhook")
        async def handle_dialin_webhook(request: Request):
            """Handle incoming Daily PSTN dial-in webhook.

            This endpoint mimics Pipecat Cloud's dial-in webhook handler.
            It receives Daily webhook data, creates a SIP-enabled room, and starts the bot.

            Expected webhook payload::

                {
                    "From": "+15551234567",
                    "To": "+15559876543",
                    "callId": "uuid-call-id",
                    "callDomain": "uuid-call-domain",
                    "sipHeaders": {...}  // optional
                }

            Returns::

                {
                    "dailyRoom": "https://...",
                    "dailyToken": "...",
                    "sessionId": "uuid"
                }
            """
            logger.debug("Received Daily dial-in webhook")

            try:
                data = await request.json()
                logger.debug(f"Webhook data: {data}")
            except Exception as e:
                logger.error(f"Failed to parse webhook data: {e}")
                raise HTTPException(status_code=400, detail="Invalid JSON payload")

            # Handle webhook verification test (sent by Daily when configuring webhook)
            if data.get("test") or data.get("Test"):
                logger.debug("Webhook verification test received")
                return {"status": "OK"}

            # Validate required fields
            if not all(key in data for key in ["From", "To", "callId", "callDomain"]):
                raise HTTPException(
                    status_code=400,
                    detail="Missing required fields: From, To, callId, callDomain",
                )

            import aiohttp

            from pipecat.runner.daily import configure
            from pipecat.runner.types import DailyDialinRequest, DialinSettings

            # Create Daily room with SIP capabilities
            async with aiohttp.ClientSession() as session:
                try:
                    room_config = await configure(
                        session,
                        sip_caller_phone=data.get("From"),
                        room_exp_duration=PIPECAT_ROOM_EXP_HOURS,
                    )
                except Exception as e:
                    logger.error(f"Failed to create Daily room: {e}")
                    raise HTTPException(
                        status_code=500, detail=f"Failed to create Daily room: {str(e)}"
                    )

            # Get Daily API URL from environment, fallback to production
            daily_api_url = os.getenv("DAILY_API_URL", "https://api.daily.co/v1")

            # Get Daily API key from environment
            daily_api_key = os.getenv("DAILY_API_KEY")
            if not daily_api_key:
                logger.error("DAILY_API_KEY not found in environment")
                raise HTTPException(
                    status_code=500, detail="DAILY_API_KEY not configured on server"
                )

            # Prepare dial-in settings matching Pipecat Cloud structure
            dialin_settings = DialinSettings(
                call_id=data.get("callId"),
                call_domain=data.get("callDomain"),
                To=data.get("To"),
                From=data.get("From"),
                sip_headers=data.get("sipHeaders"),
            )

            # Create request body matching Pipecat Cloud payload
            request_body = DailyDialinRequest(
                dialin_settings=dialin_settings,
                daily_api_key=daily_api_key,
                daily_api_url=daily_api_url,
            )

            # Generate session ID for both the runner args and the response
            session_id = str(uuid.uuid4())

            # Start bot with dial-in context
            bot_module = _get_bot_module()
            runner_args = DailyRunnerArguments(
                room_url=room_config.room_url,
                token=room_config.token,
                body=request_body.model_dump(),
                session_id=session_id,
            )
            runner_args.cli_args = args

            asyncio.create_task(bot_module.bot(runner_args))

            # Return response matching Pipecat Cloud format
            return {
                "dailyRoom": room_config.room_url,
                "dailyToken": room_config.token,
                "sessionId": session_id,
            }


def _setup_telephony_routes(app: FastAPI, args: argparse.Namespace):
    """Set up telephony-specific routes."""
    # XML response templates (Exotel doesn't use XML webhooks)
    XML_TEMPLATES = {
        "twilio": f"""<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Connect>
    <Stream url="wss://{args.proxy}/ws"></Stream>
  </Connect>
  <Pause length="40"/>
</Response>""",
        "telnyx": f"""<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Connect>
    <Stream url="wss://{args.proxy}/ws" bidirectionalMode="rtp"></Stream>
  </Connect>
  <Pause length="40"/>
</Response>""",
        "plivo": f"""<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Stream bidirectional="true" keepCallAlive="true" contentType="audio/x-mulaw;rate=8000">wss://{args.proxy}/ws</Stream>
</Response>""",
    }

    @app.post("/")
    async def start_call():
        """Handle telephony webhook and return XML response."""
        if args.transport == "exotel":
            # Exotel doesn't use POST webhooks - redirect to proper documentation
            logger.debug("POST Exotel endpoint - not used")
            return {
                "error": "Exotel doesn't use POST webhooks",
                "websocket_url": f"wss://{args.proxy}/ws",
                "note": "Configure the WebSocket URL above in your Exotel App Bazaar Voicebot Applet",
            }
        else:
            logger.debug(f"POST {args.transport.upper()} XML")
            xml_content = XML_TEMPLATES.get(args.transport, "<Response></Response>")
            return HTMLResponse(content=xml_content, media_type="application/xml")

    @app.websocket("/ws")
    async def websocket_endpoint(websocket: WebSocket):
        """Handle WebSocket connections for telephony."""
        await websocket.accept()
        logger.debug("WebSocket connection accepted")
        await _run_telephony_bot(websocket, args)

    @app.get("/")
    async def start_agent():
        """Simple status endpoint for telephony transports."""
        return {"status": f"Bot started with {args.transport}"}


async def _run_daily_direct(args: argparse.Namespace):
    """Run Daily bot with direct connection (no FastAPI server)."""
    try:
        from pipecat.runner.daily import configure
    except ImportError as e:
        logger.error("Daily transport dependencies not installed.")
        return

    logger.info("Running with direct Daily connection...")

    async with aiohttp.ClientSession() as session:
        room_url, token = await configure(session, room_exp_duration=PIPECAT_ROOM_EXP_HOURS)

        # Direct connections have no request body, so use empty dict
        runner_args = DailyRunnerArguments(
            room_url=room_url, token=token, session_id=str(uuid.uuid4())
        )
        runner_args.handle_sigint = True
        runner_args.cli_args = args

        # Get the bot module and run it directly
        bot_module = _get_bot_module()

        print(f"📞 Joining Daily room: {room_url}")
        print("   (Direct connection - no web server needed)")
        print()

        await bot_module.bot(runner_args)


def _validate_and_clean_proxy(proxy: str) -> str:
    """Validate and clean proxy hostname, removing protocol if present."""
    if not proxy:
        return proxy

    original_proxy = proxy

    # Strip common protocols
    if proxy.startswith(("http://", "https://")):
        proxy = proxy.split("://", 1)[1]
        logger.warning(
            f"Removed protocol from proxy URL. Using '{proxy}' instead of '{original_proxy}'. "
            f"The --proxy argument expects only the hostname (e.g., 'mybot.ngrok.io')."
        )

    # Remove trailing slashes
    proxy = proxy.rstrip("/")

    return proxy


def runner_downloads_folder() -> str | None:
    """Returns the folder where files are stored for later download."""
    return RUNNER_DOWNLOADS_FOLDER


def runner_host() -> str:
    """Returns the host name of this runner."""
    return RUNNER_HOST


def runner_port() -> int:
    """Returns the port of this runner."""
    return RUNNER_PORT


def main(parser: argparse.ArgumentParser | None = None):
    """Start the Pipecat development runner.

    Parses command-line arguments and starts a FastAPI server configured
    for the specified transport type.

    The runner discovers and runs any ``bot(runner_args)`` function found in the
    calling module.

    Command-line arguments:
       - --host: Server host address (default: localhost) 879
       - --port: Server port (default: 7860)
       - -t/--transport: Transport type (daily, webrtc, twilio, telnyx, plivo, exotel)
       - -x/--proxy: Public proxy hostname for telephony webhooks
       - -d/--direct: Connect directly to Daily room (automatically sets transport to daily)
       - -f/--folder: Path to downloads folder
       - --dialin: Enable Daily PSTN dial-in webhook handling (requires Daily transport)
       - --esp32: Enable SDP munging for ESP32 compatibility (requires --host with IP address)
       - --whatsapp: Ensure requried WhatsApp environment variables are present
       - -v/--verbose: Increase logging verbosity

    Args:
        parser: Optional custom argument parser. If provided, default runner
            arguments are added to it so bots can define their own CLI
            arguments. Custom arguments should not conflict with the default
            ones. Custom args are accessible via `runner_args.cli_args`.

    """
    global RUNNER_DOWNLOADS_FOLDER, RUNNER_HOST, RUNNER_PORT

    if not parser:
        parser = argparse.ArgumentParser(description="Pipecat Development Runner")
    parser.add_argument("--host", type=str, default=RUNNER_HOST, help="Host address")
    parser.add_argument("--port", type=int, default=RUNNER_PORT, help="Port number")
    parser.add_argument(
        "-t",
        "--transport",
        type=str,
        choices=["daily", "webrtc", *TELEPHONY_TRANSPORTS],
        default="webrtc",
        help="Transport type",
    )
    parser.add_argument("-x", "--proxy", help="Public proxy host name")
    parser.add_argument(
        "-d",
        "--direct",
        action="store_true",
        default=False,
        help="Connect directly to Daily room (automatically sets transport to daily)",
    )
    parser.add_argument("-f", "--folder", type=str, help="Path to downloads folder")
    parser.add_argument(
        "-v", "--verbose", action="count", default=0, help="Increase logging verbosity"
    )
    parser.add_argument(
        "--ssl-keyfile",
        type=str,
        default=os.getenv("SSL_KEYFILE"),
        help="Path to SSL key file for HTTPS",
    )
    parser.add_argument(
        "--ssl-certfile",
        type=str,
        default=os.getenv("SSL_CERTFILE"),
        help="Path to SSL certificate file for HTTPS",
    )
    parser.add_argument(
        "--workers",
        type=int,
        default=None,
        help="Number of worker processes (for production, recommended: CPU cores)",
    )
    parser.add_argument(
        "--log-level",
        type=str,
        default=None,
        choices=["critical", "error", "warning", "info", "debug", "trace"],
        help="Log level for uvicorn (default: info for production, debug for development)",
    )
    parser.add_argument(
        "--no-access-log",
        action="store_true",
        default=False,
        help="Disable access log for better performance in production",
    )
    parser.add_argument(
        "--dialin",
        action="store_true",
        default=False,
        help="Enable Daily PSTN dial-in webhook handling (requires Daily transport)",
    )
    parser.add_argument(
        "--esp32",
        action="store_true",
        default=False,
        help="Enable SDP munging for ESP32 compatibility (requires --host with IP address)",
    )
    parser.add_argument(
        "--whatsapp",
        action="store_true",
        default=False,
        help="Ensure requried WhatsApp environment variables are present",
    )

    args = parser.parse_args()

    # Validate and clean proxy hostname
    if args.proxy:
        args.proxy = _validate_and_clean_proxy(args.proxy)

    # Auto-set transport to daily if --direct is used without explicit transport
    if args.direct and args.transport == "webrtc":  # webrtc is the default
        args.transport = "daily"
    elif args.direct and args.transport != "daily":
        logger.error("--direct flag only works with Daily transport (-t daily)")
        return

    # Validate ESP32 requirements
    if args.esp32 and args.host == "localhost":
        logger.error("For ESP32, you need to specify `--host IP` so we can do SDP munging.")
        return

    # Validate dial-in requirements
    if args.dialin and args.transport != "daily":
        logger.error("--dialin flag only works with Daily transport (-t daily)")
        return

    # Log level
    logger.remove()
    logger.add(sys.stderr, level="TRACE" if args.verbose else "DEBUG")

    # Handle direct Daily connection (no FastAPI server)
    if args.direct:
        print()
        print("🚀 Connecting directly to Daily room...")
        print()

        # Run direct Daily connection
        asyncio.run(_run_daily_direct(args))
        return

    # Run the server
    ssl_kwargs = {}
    protocol = "http"
    if args.ssl_keyfile and args.ssl_certfile:
        # Debug: Log SSL arguments
        logger.debug(
            f"SSL arguments received: keyfile={args.ssl_keyfile}, certfile={args.ssl_certfile}"
        )
        # Check if SSL files exist
        if os.path.exists(args.ssl_keyfile) and os.path.exists(args.ssl_certfile):
            ssl_kwargs["ssl_keyfile"] = args.ssl_keyfile
            ssl_kwargs["ssl_certfile"] = args.ssl_certfile
            protocol = "https"
            logger.info(f"SSL enabled: keyfile={args.ssl_keyfile}, certfile={args.ssl_certfile}")
        else:
            logger.warning(
                f"SSL files not found: keyfile={args.ssl_keyfile}, certfile={args.ssl_certfile}"
            )
    else:
        logger.debug(
            f"SSL arguments not provided: ssl_keyfile={args.ssl_keyfile}, ssl_certfile={args.ssl_certfile}"
        )

    # Print startup message for server-based transports
    if args.transport == "webrtc":
        print()
        if args.esp32:
            print(f"🚀 Bot ready! (ESP32 mode)")
        elif args.whatsapp:
            print(f"🚀 Bot ready! (WhatsApp)")
        else:
            print(f"🚀 Bot ready!")
        print(f"   → Open {protocol}://{args.host}:{args.port}/client in your browser")
        print()
    elif args.transport == "daily":
        print()
        print(f"🚀 Bot ready!")
        print(f"   → Open http://{args.host}:{args.port} in your browser to start a session")
        print()

    RUNNER_DOWNLOADS_FOLDER = args.folder
    RUNNER_HOST = args.host
    RUNNER_PORT = args.port

    # Store settings in environment variables for worker processes (e.g. uvicorn workers)
    os.environ["RUNNER_TRANSPORT"] = args.transport
    os.environ["RUNNER_HOST"] = args.host
    if args.proxy:
        os.environ["RUNNER_PROXY"] = args.proxy
    if args.esp32:
        os.environ["RUNNER_ESP32"] = "1"
    if args.whatsapp:
        os.environ["RUNNER_WHATSAPP"] = "1"
    if args.folder:
        os.environ["RUNNER_FOLDER"] = args.folder

    # Use caller's app if it exists (e.g. bot script already called create_app() + setup_auth_for_app).
    # Otherwise create the app with transport-specific setup.
    caller_module = sys.modules.get("__main__")
    caller_app = getattr(caller_module, "app", None) if caller_module else None
    if caller_app is not None and hasattr(caller_app, "router"):
        app = caller_app
        logger.debug("Using FastAPI app from caller module (e.g. with OAuth)")
    else:
        app = _create_server_app(args)
        if args.workers and caller_module:
            caller_module.app = app

    # Uvicorn configuration for production optimization
    uvicorn_kwargs = {
        "host": args.host,
        "port": args.port,
        **ssl_kwargs,
    }

    # Production optimizations
    if args.workers:
        uvicorn_kwargs["workers"] = args.workers
        logger.info(f"Running with {args.workers} worker processes")
        # When using workers, we need to use import string instead of app object
        # Store app in the caller module's globals so it can be imported by workers
        caller_module = sys.modules.get("__main__")
        if caller_module:
            caller_module.app = app
            # Get the script name (e.g., "voice_ai" from "voice_ai.py")
            script_name = os.path.splitext(os.path.basename(sys.argv[0]))[0]
            import_string = f"{script_name}:app"
            logger.info(f"Using import string: {import_string}")
        else:
            # Fallback: use app object directly (workers won't work, but won't crash)
            logger.warning("Could not determine caller module, using app object directly")
            import_string = app
    else:
        import_string = app

    if args.log_level:
        uvicorn_kwargs["log_level"] = args.log_level
    elif args.verbose == 0:
        # Default to info level for production (when not verbose)
        uvicorn_kwargs["log_level"] = "info"

    if args.no_access_log:
        uvicorn_kwargs["access_log"] = False
        logger.info("Access log disabled for better performance")

    uvicorn.run(import_string, **uvicorn_kwargs)


if __name__ == "__main__":
    main()
