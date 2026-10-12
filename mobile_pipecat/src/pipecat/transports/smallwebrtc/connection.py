#
# Copyright (c) 2024-2026, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#

"""Small WebRTC connection implementation for Pipecat.

This module provides a WebRTC connection implementation using aiortc,
with support for audio/video tracks, data channels, and signaling
for real-time communication applications.
"""

import asyncio
import json
import os
import time
import uuid
from typing import Any, Literal, Optional

import aiortc.rtcsctptransport as _sctp_transport
from aiortc import (
    RTCConfiguration,
    RTCIceServer,
    RTCPeerConnection,
    RTCSessionDescription,
)
from aiortc.rtcrtpreceiver import RemoteStreamTrack
from av.frame import Frame
from loguru import logger
from pydantic import BaseModel, TypeAdapter

from pipecat.utils.base_object import BaseObject

# Clamp aiortc's SCTP DATA-chunk payload size so the on-wire UDP packet fits
# inside the smallest-MTU path we're likely to see (IPv6 minimum 1280,
# Tailscale overlays default to 1280, some consumer VPNs are lower).
#
# aiortc hardcodes USERDATA_MAX_LENGTH = 1200. After adding SCTP (28) +
# DTLS/GCM (~29) + UDP (8) + IPv6 (40) headers that produces a ~1305-byte
# UDP datagram — over the 1280 MTU. The kernel rejects it with EMSGSIZE,
# SCTP retransmits at the same size, and the data channel silently stalls.
# aiortc has no PMTU discovery (RFC 8831 §6), so there is no auto-recovery.
#
# 1100 brings the worst-case datagram to ~1205 bytes (~75 bytes of slack).
# Throughput cost is negligible: RTVI control frames fragment across one
# extra chunk at most, and audio uses RTP (a separate path).
#
# There is no public API to set this — RTCConfiguration exposes no MTU knob
# and all method references are bare module-global lookups, so patching the
# module attribute before any RTCSctpTransport is instantiated is the only
# option short of forking aiortc.
#
# Remove once aiortc ships DPLPMTUD (RFC 8899) or exposes this as a
# configurable parameter.
_SCTP_MAX_CHUNK_SIZE_DEFAULT = 1100
_sctp_max_chunk_size = int(
    os.environ.get("PIPECAT_SCTP_MAX_CHUNK_SIZE", _SCTP_MAX_CHUNK_SIZE_DEFAULT)
)
_sctp_transport.USERDATA_MAX_LENGTH = _sctp_max_chunk_size
logger.debug(f"[SCTP] USERDATA_MAX_LENGTH set to {_sctp_max_chunk_size}")

SIGNALLING_TYPE = "signalling"
AUDIO_TRANSCEIVER_INDEX = 0
VIDEO_TRANSCEIVER_INDEX = 1
SCREEN_VIDEO_TRANSCEIVER_INDEX = 2

# Maximum number of messages to queue while the data channel is not yet open.
MAX_MESSAGE_QUEUE_SIZE = 50
# Seconds to wait for the data channel to open after the peer connection is established.
DATA_CHANNEL_TIMEOUT_SECS = 10


class TrackStatusMessage(BaseModel):
    """Message for updating track enabled/disabled status.

    Parameters:
        type: Message type identifier.
        receiver_index: Index of the track receiver to update.
        enabled: Whether the track should be enabled or disabled.
    """

    type: Literal["trackStatus"]
    receiver_index: int
    enabled: bool


class RenegotiateMessage(BaseModel):
    """Message requesting WebRTC renegotiation.

    Parameters:
        type: Message type identifier for renegotiation requests.
    """

    type: Literal["renegotiate"] = "renegotiate"


class PeerLeftMessage(BaseModel):
    """Message indicating a peer has left the connection.

    Parameters:
        type: Message type identifier for peer departure.
    """

    type: Literal["peerLeft"] = "peerLeft"


class SignallingMessage:
    """Union types for signaling message handling.

    Parameters:
        Inbound: Types of messages that can be received from peers.
        outbound: Types of messages that can be sent to peers.
    """

    Inbound = TrackStatusMessage  # in case we need to add new messages in the future
    outbound = RenegotiateMessage


class SmallWebRTCTrack:
    """Wrapper for WebRTC media tracks with enabled/disabled state management.

    Provides additional functionality on top of aiortc tracks including
    enable/disable control and frame discarding for audio and video streams.
    """

    def __init__(self, receiver):
        """Initialize the WebRTC track wrapper.

        Args:
            receiver: The RemoteStreamTrack receiver instance.
        """
        self._receiver = receiver
        # Configuring the receiver for not consuming the track by default to prevent memory grow
        self._receiver._enabled = False
        self._track = receiver.track
        self._enabled = True
        self._last_recv_time: float = 0.0
        self._idle_task: asyncio.Task | None = None
        # Idle timeout (seconds) before discarding old frames; large default to avoid STT dropouts (env: WEBRTC_IDLE_TIMEOUT_SECS)
        self._idle_timeout: float = float(os.getenv("WEBRTC_IDLE_TIMEOUT_SECS", "10.0"))
        # Only discard old frames when queue size exceeds this threshold to avoid STT dropouts (env: WEBRTC_QUEUE_SIZE_THRESHOLD)
        self._queue_size_threshold: int = int(os.getenv("WEBRTC_QUEUE_SIZE_THRESHOLD", "100"))

    def set_enabled(self, enabled: bool) -> None:
        """Enable or disable the track.

        Args:
            enabled: Whether the track should be enabled for receiving frames.
        """
        self._enabled = enabled

    def is_enabled(self) -> bool:
        """Check if the track is currently enabled.

        Returns:
            True if the track is enabled for receiving frames.
        """
        return self._enabled

    async def discard_old_frames(self):
        """Discard old frames from the track queue when above threshold to reduce latency without dropping STT words."""
        remote_track = self._track
        if isinstance(remote_track, RemoteStreamTrack):
            if not hasattr(remote_track, "_queue") or not isinstance(
                remote_track._queue, asyncio.Queue
            ):
                logger.warning("_queue does not exist or has changed in aiortc.")
                return

            queue_size = remote_track._queue.qsize()

            # 큐 크기가 임계값 미만이면 버리지 않음 (STT 단어 누락 방지)
            if queue_size < self._queue_size_threshold:
                logger.trace(
                    f"Skipping frame discard for {self._track.kind} track: "
                    f"queue size ({queue_size}) below threshold ({self._queue_size_threshold})"
                )
                return

            # 큐 크기가 임계값 이상일 때만 일부 프레임 버리기
            # 전체를 버리지 않고 일부만 버려서 메모리와 성능의 균형 유지
            frames_to_discard = max(1, queue_size - self._queue_size_threshold // 2)
            frames_discarded = 0
            for _ in range(frames_to_discard):
                if remote_track._queue.empty():
                    break
                remote_track._queue.get_nowait()
                remote_track._queue.task_done()
                frames_discarded += 1

            # 프레임을 실제로 버렸을 때만 로그 출력
            if frames_discarded > 0:
                logger.debug(
                    f"Discarded {frames_discarded} old frame(s) from {self._track.kind} track "
                    f"(queue size: {queue_size}, threshold: {self._queue_size_threshold})"
                )

    async def recv(self) -> Frame | None:
        """Receive the next frame from the track.

        Enables the internal receiving state and starts idle watcher.

        Returns:
            The next frame, except for video tracks, where it returns the frame only if the track is enabled, otherwise, returns None.
        """
        self._receiver._enabled = True
        self._last_recv_time = time.time()

        # start idle watcher if not already running
        if not self._idle_task or self._idle_task.done():
            self._idle_task = asyncio.create_task(self._idle_watcher())

        if not self._enabled and self._track.kind == "video":
            return None
        return await self._track.recv()

    async def _idle_watcher(self):
        """Disable receiving if idle for more than _idle_timeout and monitor queue size."""
        while self._receiver._enabled:
            await asyncio.sleep(self._idle_timeout)
            idle_duration = time.time() - self._last_recv_time
            if idle_duration >= self._idle_timeout:
                # discard old frames to prevent memory growth
                logger.debug(
                    f"Disabling receiver for {self._track.kind} track after {idle_duration:.2f}s idle"
                )
                await self.discard_old_frames()
                self._receiver._enabled = False

    def stop(self):
        """Stop receiving frames from the track."""
        self._receiver._enabled = False
        if self._idle_task:
            self._idle_task.cancel()
            self._idle_task = None
        if self._track:
            self._track.stop()

    def __getattr__(self, name):
        """Forward attribute access to the underlying track.

        Args:
            name: The attribute name to access.

        Returns:
            The attribute value from the underlying track.
        """
        # Forward other attribute/method calls to the underlying track
        return getattr(self._track, name)


# Alias so we don't need to expose RTCIceServer
IceServer = RTCIceServer


class SmallWebRTCConnection(BaseObject):
    """WebRTC connection implementation using aiortc.

    Provides WebRTC peer connection functionality including ICE server configuration,
    track management, data channel communication, and connection state handling
    for real-time audio/video communication.
    """

    def __init__(
        self,
        ice_servers: list[str] | list[IceServer] | None = None,
        connection_timeout_secs: int = 60,
    ):
        """Initialize the WebRTC connection.

        Args:
            ice_servers: List of ICE servers as URLs or IceServer objects.
            connection_timeout_secs: Timeout in seconds for connecting to the peer.

        Raises:
            TypeError: If ice_servers contains mixed types or unsupported types.
        """
        super().__init__()
        if not ice_servers:
            self.ice_servers: list[IceServer] = []
        elif all(isinstance(s, IceServer) for s in ice_servers):
            self.ice_servers = ice_servers
        elif all(isinstance(s, str) for s in ice_servers):
            self.ice_servers = [IceServer(urls=s) for s in ice_servers]
        else:
            raise TypeError("ice_servers must be either List[str] or List[RTCIceServer]")
        self._connect_invoked = False
        self._track_map = {}
        self._track_getters = {
            AUDIO_TRANSCEIVER_INDEX: self.audio_input_track,
            VIDEO_TRANSCEIVER_INDEX: self.video_input_track,
            SCREEN_VIDEO_TRANSCEIVER_INDEX: self.screen_video_input_track,
        }
        self.connection_timeout_secs = connection_timeout_secs

        self._client_pc_id: str | None = None  # pc_id from client request, if provided
        # recovery attempt counter for transient failures
        self._recovery_attempts: int = 0
        self._initialize()

        # Register supported handlers. The user will only be able to register
        # these handlers.
        self._register_event_handler("app-message")
        self._register_event_handler("track-started")
        self._register_event_handler("track-ended")
        # connection states
        self._register_event_handler("connecting")
        self._register_event_handler("connected")
        self._register_event_handler("disconnected")
        self._register_event_handler("closed")
        self._register_event_handler("failed")
        self._register_event_handler("new")

    @property
    def pc(self) -> RTCPeerConnection:
        """Get the underlying RTCPeerConnection.

        Returns:
            The aiortc RTCPeerConnection instance.
        """
        return self._pc

    @property
    def pc_id(self) -> str:
        """Get the peer connection identifier.

        Returns:
            The unique identifier for this peer connection.
        """
        return self._pc_id

    @property
    def client_pc_id(self) -> str | None:
        """Get the actual client pc_id from the request.

        Returns:
            The actual client pc_id if available, otherwise None.
        """
        return self._client_pc_id

    def _initialize(self):
        """Initialize the peer connection and associated components."""
        logger.debug("Initializing new peer connection")
        rtc_config = RTCConfiguration(iceServers=self.ice_servers)

        self._answer: RTCSessionDescription | None = None
        self._pc = RTCPeerConnection(rtc_config)
        self._pc_id = f"{self.name}-{uuid.uuid4().hex}"
        self._setup_listeners()
        self._data_channel = None
        self._renegotiation_in_progress = False
        self._last_received_time = None
        self._outgoing_messages_queue = []
        self._data_channel_enabled = True
        self._pending_app_messages = []
        self._connecting_timeout_task = None
        self._data_channel_timeout_task = None

    def _setup_listeners(self):
        """Set up event listeners for the peer connection."""

        @self._pc.on("datachannel")
        def on_datachannel(channel):
            self._data_channel = channel

            # Flush queued messages once the data channel is open
            @channel.on("open")
            async def on_open():
                logger.debug("Data channel is open!")
                self._flush_message_queue()

            @channel.on("message")
            async def on_message(message):
                try:
                    # aiortc does not provide any way so we can be aware when we are disconnected,
                    # so we are using this keep alive message as a way to implement that
                    if isinstance(message, str) and message.startswith("ping"):
                        self._last_received_time = time.time()
                    else:
                        json_message = json.loads(message)
                        # Normalize message format: support both {"type": ...} and {"t": ...} formats
                        # Front-end may send compact format with "t" (type) and "d" (data) keys
                        if "t" in json_message and "type" not in json_message:
                            json_message["type"] = json_message["t"]
                        if "d" in json_message and "data" not in json_message:
                            json_message["data"] = json_message["d"]

                        message_type = json_message.get("type")
                        if message_type == SIGNALLING_TYPE and json_message.get("message"):
                            self._handle_signalling_message(json_message["message"])
                        else:
                            if self.is_connected():
                                await self._call_event_handler("app-message", json_message)
                            else:
                                logger.debug("Client not connected. Queuing app-message.")
                                self._pending_app_messages.append(json_message)
                except Exception as e:
                    logger.error(f"Error parsing JSON message {message}, {e}")

        # Despite the fact that aiortc provides this listener, they don't have a status for "disconnected"
        # So, in case we loose connection, this event will not be triggered
        @self._pc.on("connectionstatechange")
        async def on_connectionstatechange():
            await self._handle_new_connection_state()

        # Despite the fact that aiortc provides this listener, they don't have a status for "disconnected"
        # So, in case we loose connection, this event will not be triggered
        @self._pc.on("iceconnectionstatechange")
        async def on_iceconnectionstatechange():
            logger.debug(
                f"ICE connection state is {self._pc.iceConnectionState}, connection is {self._pc.connectionState}"
            )

        @self._pc.on("icegatheringstatechange")
        async def on_icegatheringstatechange():
            logger.debug(f"ICE gathering state is {self._pc.iceGatheringState}")

        @self._pc.on("track")
        async def on_track(track):
            logger.debug(f"Track {track.kind} received")
            await self._call_event_handler("track-started", track)

            @track.on("ended")
            async def on_ended():
                logger.debug(f"Track {track.kind} ended")
                await self._call_event_handler("track-ended", track)
                # If the peer connection is still up, try to renegotiate to allow the client
                # to reattach a new track instead of letting the session fully close.
                if self._pc.connectionState not in {"closed", "failed"}:
                    self.ask_to_renegotiate()

    async def _create_answer(self, sdp: str, type: str):
        """Create an SDP answer for the given offer."""
        offer = RTCSessionDescription(sdp=sdp, type=type)
        await self._pc.setRemoteDescription(offer)

        # For some reason, aiortc is not respecting the SDP for the transceivers to be sendrcv
        # so we are basically forcing it to act this way
        self.force_transceivers_to_send_recv()

        # this answer does not contain the ice candidates, which will be gathered later, after the setLocalDescription
        logger.debug(f"Creating answer")
        local_answer = await self._pc.createAnswer()
        await self._pc.setLocalDescription(local_answer)
        logger.debug(f"Setting the answer after the local description is created")
        self._answer = self._pc.localDescription

    async def initialize(self, sdp: str, type: str, client_pc_id: str | None = None):
        """Initialize the connection with an SDP offer.

        Args:
            sdp: The SDP offer string.
            type: The SDP type (usually "offer").
            client_pc_id: Optional actual client pc_id from the request.
        """
        if client_pc_id:
            self._client_pc_id = client_pc_id
        await self._create_answer(sdp, type)

    async def connect(self):
        """Connect the WebRTC peer connection and handle initial setup."""
        self._connect_invoked = True
        # If we already connected, trigger again the connected event
        if self.is_connected():
            await self._call_event_handler("connected")
            logger.debug("Flushing pending app-messages")
            for message in self._pending_app_messages:
                await self._call_event_handler("app-message", message)
            # We are renegotiating here, because likely we have loose the first video frames
            # and aiortc does not handle that pretty well.
            video_input_track = self.video_input_track()
            if video_input_track:
                await self.video_input_track().discard_old_frames()
            screen_video_input_track = self.screen_video_input_track()
            if screen_video_input_track:
                await self.screen_video_input_track().discard_old_frames()
            if video_input_track or screen_video_input_track:
                # This prevents an issue where sometimes the WebRTC connection can be established
                # before the bot is ready to receive video. When that happens, we can lose a couple
                # of seconds of video before we received a key frame to finally start displaying it.
                self.ask_to_renegotiate()

    async def renegotiate(self, sdp: str, type: str, restart_pc: bool = False):
        """Renegotiate the WebRTC connection with new parameters.

        Args:
            sdp: The new SDP offer string.
            type: The SDP type (usually "offer").
            restart_pc: Whether to restart the peer connection entirely.
        """
        logger.debug(f"Renegotiating {self._pc_id}")

        if restart_pc:
            await self._call_event_handler("disconnected")
            logger.debug("Closing old peer connection")
            # removing the listeners to prevent the bot from closing
            self._pc.remove_all_listeners()
            await self._close()
            # we are initializing a new peer connection in this case.
            self._initialize()

        await self._create_answer(sdp, type)

        # Maybe we should refactor to receive a message from the client side when the renegotiation is completed.
        # or look at the peer connection listeners
        # but this is good enough for now for testing.
        async def delayed_task():
            await asyncio.sleep(2)
            self._renegotiation_in_progress = False

        asyncio.create_task(delayed_task())

    def force_transceivers_to_send_recv(self):
        """Force all transceivers to bidirectional send/receive mode."""
        transceivers = self._pc.getTransceivers()
        # For now, we only support sendrecv for camera audio and video (the first two transceivers)
        for i, transceiver in enumerate(transceivers):
            if i < 2:  # First two transceivers (camera audio and video)
                transceiver.direction = "sendrecv"
            else:
                transceiver.direction = "recvonly"

    def replace_audio_track(self, track):
        """Replace the audio track in the first transceiver.

        Args:
            track: The new audio track to use for sending.
        """
        logger.debug(f"Replacing audio track {track.kind}")
        # Transceivers always appear in creation-order for both peers
        # For now we are only considering that we are going to have 02 transceivers,
        # one for audio and one for video
        transceivers = self._pc.getTransceivers()
        if len(transceivers) > 0 and transceivers[0].sender:
            transceivers[0].sender.replaceTrack(track)
        else:
            logger.warning("Audio transceiver not found. Cannot replace audio track.")

    def replace_video_track(self, track):
        """Replace the video track in the second transceiver.

        Args:
            track: The new video track to use for sending.
        """
        logger.debug(f"Replacing video track {track.kind}")
        # Transceivers always appear in creation-order for both peers
        # For now we are only considering that we are going to have 02 transceivers,
        # one for audio and one for video
        transceivers = self._pc.getTransceivers()
        if len(transceivers) > 1 and transceivers[1].sender:
            transceivers[1].sender.replaceTrack(track)
        else:
            logger.warning("Video transceiver not found. Cannot replace video track.")

    def replace_screen_video_track(self, track):
        """Replace the screen video track in the second transceiver.

        Args:
            track: The new screen video track to use for sending.
        """
        logger.debug(f"Replacing screen video track {track.kind}")
        # Transceivers always appear in creation-order for both peers
        # For now we are only considering that we are going to have 02 transceivers,
        # one for audio and one for video
        transceivers = self._pc.getTransceivers()
        if len(transceivers) > 2 and transceivers[2].sender:
            transceivers[2].sender.replaceTrack(track)
        else:
            logger.warning("Screen video transceiver not found. Cannot replace screen video track.")

    async def disconnect(self):
        """Disconnect from the WebRTC peer connection."""
        self.send_app_message({"type": SIGNALLING_TYPE, "message": PeerLeftMessage().model_dump()})
        await self._close()

    async def _close(self):
        """Close the peer connection and cleanup resources."""
        # Stop message sender task (if it was ever started)
        _message_task = getattr(self, "_message_task", None)
        if _message_task:
            _message_task.cancel()
            try:
                await _message_task
            except asyncio.CancelledError:
                pass
            self._message_task = None
        if hasattr(self, "_message_queue"):
            self._message_queue = None

        for track in self._track_map.values():
            if track:
                track.stop()
        self._track_map.clear()
        if self._pc:
            await self._pc.close()
        self._outgoing_messages_queue.clear()
        self._data_channel_enabled = True
        self._pending_app_messages.clear()
        self._track_map = {}
        self._cancel_monitoring_connecting_state()
        self._cancel_data_channel_timeout()

    def get_answer(self):
        """Get the SDP answer for the current connection.

        Returns:
            Dictionary containing SDP answer, type, and peer connection ID,
            or None if no answer is available.
        """
        if not self._answer:
            return None

        return {
            "sdp": self._answer.sdp,
            "type": self._answer.type,
            "pc_id": self._pc_id,
        }

    def _monitoring_connecting_state(self) -> None:
        """Start monitoring the peer connection while it is in the *connecting* state.

        This method schedules a timeout task that will automatically close the
        connection if it remains in the connecting state for more than the specified
        timeout, default to 60 seconds.
        """
        logger.debug("Monitoring connecting state")

        async def timeout_handler():
            # We will close the connection in case we have remained in the connecting state for over 1 minute
            await asyncio.sleep(self.connection_timeout_secs)
            logger.warning("Timeout establishing the connection to the remote peer. Closing.")

            await self._close()

        # Create and store the timeout task
        self._connecting_timeout_task = asyncio.create_task(timeout_handler())

    def _cancel_monitoring_connecting_state(self) -> None:
        """Cancel the ongoing connecting-state timeout task, if any.

        This method should be called once the connection has either succeeded or
        transitioned out of the connecting state. If the timeout task is still
        pending, it will be canceled and the reference cleared.
        """
        if self._connecting_timeout_task and not self._connecting_timeout_task.done():
            logger.debug("Cancelling the connecting timeout task")
            self._connecting_timeout_task.cancel()
        self._connecting_timeout_task = None

    def _start_data_channel_timeout(self) -> None:
        """Start a timeout to detect if the data channel fails to open after connection.

        Schedules a background task that fires ``DATA_CHANNEL_TIMEOUT_SECS`` seconds after
        the peer connection reaches the *connected* state.  If the data channel has not
        opened by then, the queued messages are discarded, a warning is logged, and future
        calls to :meth:`send_app_message` will silently drop messages instead of queuing
        them (fall-back to "discard" mode).

        The task is automatically cancelled when the data channel opens successfully (see
        :meth:`_flush_message_queue`) or when the connection is closed (see
        :meth:`_close`).
        """

        async def timeout_handler():
            await asyncio.sleep(DATA_CHANNEL_TIMEOUT_SECS)
            if not self._data_channel or self._data_channel.readyState != "open":
                logger.warning(
                    f"Data channel not established within {DATA_CHANNEL_TIMEOUT_SECS}s after "
                    "connection. Clearing message queue and disabling future queueing."
                )
                self._outgoing_messages_queue.clear()
                self._data_channel_enabled = False

        self._data_channel_timeout_task = asyncio.create_task(timeout_handler())

    def _cancel_data_channel_timeout(self) -> None:
        """Cancel the data-channel open timeout task, if any.

        Should be called when the data channel opens successfully (the timeout is no longer
        needed) or when the connection is being torn down.  If the task is still pending it
        will be cancelled and the reference cleared.
        """
        if self._data_channel_timeout_task and not self._data_channel_timeout_task.done():
            logger.debug("Cancelling the data channel timeout task")
            self._data_channel_timeout_task.cancel()
        self._data_channel_timeout_task = None

    async def _handle_new_connection_state(self):
        """Handle changes in the peer connection state."""
        state = self._pc.connectionState
        if state == "connecting":
            self._monitoring_connecting_state()
        else:
            self._cancel_monitoring_connecting_state()

        if state == "connected" and not self._data_channel_timeout_task:
            self._start_data_channel_timeout()

        if state == "connected" and not self._connect_invoked:
            # We are going to wait until the pipeline is ready before triggering the event
            return
        logger.info(f"WebRTC connection state changed to: {state} (pc_id={self._pc_id})")
        if state == "connected":
            self._recovery_attempts = 0
        await self._call_event_handler(state)
        if state == "failed":
            if self._recovery_attempts < 1:
                self._recovery_attempts += 1
                logger.warning(
                    f"Connection state {state}, requesting renegotiation before closing (attempt {self._recovery_attempts}, pc_id={self._pc_id})"
                )
                self.ask_to_renegotiate()
                return
            logger.warning(
                f"Connection failed after retry, closing peer connection (pc_id={self._pc_id})."
            )
            await self._close()
        elif state == "closed":
            logger.info(f"Connection closed, cleaning up (pc_id={self._pc_id}).")
            await self._close()

    # Despite the fact that aiortc provides this listener, they don't have a status for "disconnected"
    # So, there is no advantage in looking at self._pc.connectionState
    # That is why we are trying to keep our own state
    def is_connected(self) -> bool:
        """Check if the WebRTC connection is currently active.

        Returns:
            True if the connection is active and receiving data.
        """
        # If the small webrtc transport has never invoked to connect
        # we are acting like if we are not connected
        if not self._connect_invoked:
            return False

        # First check the actual WebRTC connection state
        pc_state = self._pc.connectionState
        if pc_state in {"closed", "failed"}:
            return False

        # If connection state is "connected", trust it even if ping is stale
        # This handles cases where ping messages are delayed or lost but WebRTC is still active
        if pc_state == "connected":
            if self._last_received_time is None:
                # No ping received yet, but connection is established - trust the connection state
                return True
            # If we have received pings before, check if it's recent (within 3 seconds)
            # But if connection state is "connected", allow a longer grace period (30 seconds)
            # to handle temporary network hiccups or when frontend stops sending pings
            time_since_last_ping = time.time() - self._last_received_time
            if time_since_last_ping < 3:
                return True
            # If ping is stale but connection state is still "connected",
            # still consider it connected (ping might be delayed or frontend stopped sending pings)
            # Only log if it's been a very long time (>30s) to avoid spam
            if time_since_last_ping > 30:
                logger.warning(
                    f"Ping is very stale ({time_since_last_ping:.1f}s) but connection state is 'connected', "
                    f"assuming connection is still active (pc_id={self._pc_id})"
                )
            return True

        # For other states (connecting, disconnected), check ping if available
        if self._last_received_time is None:
            # No ping received and connection not fully established
            return False
        # Checks if the last received ping was within the last 3 seconds.
        return (time.time() - self._last_received_time) < 3

    def audio_input_track(self):
        """Get the audio input track wrapper.

        Returns:
            SmallWebRTCTrack wrapper for the audio track, or None if unavailable.
        """
        if self._track_map.get(AUDIO_TRANSCEIVER_INDEX):
            return self._track_map[AUDIO_TRANSCEIVER_INDEX]

        # Transceivers always appear in creation-order for both peers
        # For support 3 receivers in the following order:
        #   audio, video, screenVideo
        transceivers = self._pc.getTransceivers()
        if len(transceivers) == 0 or not transceivers[AUDIO_TRANSCEIVER_INDEX].receiver:
            logger.warning("No audio transceiver is available")
            return None

        receiver = transceivers[AUDIO_TRANSCEIVER_INDEX].receiver
        audio_track = SmallWebRTCTrack(receiver) if receiver else None
        self._track_map[AUDIO_TRANSCEIVER_INDEX] = audio_track
        return audio_track

    def video_input_track(self):
        """Get the video input track wrapper.

        Returns:
            SmallWebRTCTrack wrapper for the video track, or None if unavailable.
        """
        if self._track_map.get(VIDEO_TRANSCEIVER_INDEX):
            return self._track_map[VIDEO_TRANSCEIVER_INDEX]

        # Transceivers always appear in creation-order for both peers
        # For support 3 receivers in the following order:
        #   audio, video, screenVideo
        transceivers = self._pc.getTransceivers()
        if len(transceivers) <= 1 or not transceivers[VIDEO_TRANSCEIVER_INDEX].receiver:
            logger.warning("No video transceiver is available")
            return None

        receiver = transceivers[VIDEO_TRANSCEIVER_INDEX].receiver
        video_track = SmallWebRTCTrack(receiver) if receiver else None
        self._track_map[VIDEO_TRANSCEIVER_INDEX] = video_track
        return video_track

    def screen_video_input_track(self):
        """Get the screen video input track wrapper.

        Returns:
            SmallWebRTCTrack wrapper for the screen video track, or None if unavailable.
        """
        if self._track_map.get(SCREEN_VIDEO_TRANSCEIVER_INDEX):
            return self._track_map[SCREEN_VIDEO_TRANSCEIVER_INDEX]

        # Transceivers always appear in creation-order for both peers
        # For support 3 receivers in the following order:
        #   audio, video, screenVideo
        transceivers = self._pc.getTransceivers()
        if len(transceivers) <= 2 or not transceivers[SCREEN_VIDEO_TRANSCEIVER_INDEX].receiver:
            logger.warning("No screen video transceiver is available")
            return None

        receiver = transceivers[SCREEN_VIDEO_TRANSCEIVER_INDEX].receiver
        video_track = SmallWebRTCTrack(receiver) if receiver else None
        self._track_map[SCREEN_VIDEO_TRANSCEIVER_INDEX] = video_track
        return video_track

    def send_app_message(self, message: Any):
        """Send an application message through the data channel.

        If the data channel is open the message is sent immediately.  Otherwise,
        the message is placed in an in-memory queue so it can be flushed once the
        channel opens, subject to the following constraints:

        * Queueing is only attempted when ``_data_channel_enabled`` is ``True``.  It is
          set to ``False`` when the data-channel open timeout fires (see
          :meth:`_start_data_channel_timeout`), after which messages are silently
          discarded.
        * The queue will not grow beyond ``MAX_MESSAGE_QUEUE_SIZE`` entries.
          Messages that arrive when the queue is full are discarded with a warning.

        Args:
            message: The message to send (will be JSON serialized).
        """
        json_message = json.dumps(message)
        if self._data_channel and self._data_channel.readyState == "open":
            self._data_channel.send(json_message)
        elif self._data_channel_enabled:
            if len(self._outgoing_messages_queue) < MAX_MESSAGE_QUEUE_SIZE:
                logger.debug("Data channel not ready, queuing message")
                self._outgoing_messages_queue.append(json_message)
            else:
                logger.warning(
                    f"Message queue is full ({MAX_MESSAGE_QUEUE_SIZE} messages). Discarding message."
                )
        else:
            # The client might choose never to create a data channel.
            logger.trace("Data channel unavailable and queueing disabled. Discarding message.")

    def _flush_message_queue(self):
        """Flush all queued messages through the now-open data channel.

        Called when the data channel transitions to the *open* state.  Cancels
        the data-channel open timeout (it is no longer needed) and sends every
        message that was buffered while the channel was unavailable.
        """
        self._cancel_data_channel_timeout()
        logger.debug("Data channel is open, flushing queued messages")
        while self._outgoing_messages_queue:
            message = self._outgoing_messages_queue.pop(0)
            self._data_channel.send(message)

    def ask_to_renegotiate(self):
        """Request renegotiation of the WebRTC connection."""
        if self._renegotiation_in_progress:
            return

        if self._pc.connectionState in {"closed", "failed"}:
            logger.debug(
                f"Skipping renegotiation request, connection state is {self._pc.connectionState}"
            )
            return

        self._renegotiation_in_progress = True
        self.send_app_message(
            {"type": SIGNALLING_TYPE, "message": RenegotiateMessage().model_dump()}
        )

    def _handle_signalling_message(self, message):
        """Handle incoming signaling messages."""
        logger.debug(f"Signalling message received: {message}")
        inbound_adapter = TypeAdapter(SignallingMessage.Inbound)
        signalling_message = inbound_adapter.validate_python(message)
        match signalling_message:
            case TrackStatusMessage():
                track = (
                    self._track_getters.get(signalling_message.receiver_index) or (lambda: None)
                )()
                if track:
                    track.set_enabled(signalling_message.enabled)

    async def add_ice_candidate(self, candidate):
        """Handle incoming ICE candidates."""
        logger.debug(f"Adding remote candidate: {candidate}")
        await self.pc.addIceCandidate(candidate)
