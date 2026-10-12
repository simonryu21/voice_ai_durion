"use client";

import {
  ThemeProvider,
  type ThemeProviderProps,
} from "@/components/ThemeProvider";
import { ConversationProvider } from "@/components/ConversationProvider";
import { createTransport } from "@/lib/transports";
import {
  APIRequest,
  PipecatClient,
  type PipecatClientOptions,
  type TransportConnectionParams,
} from "@pipecat-ai/client-js";
import {
  PipecatClientAudio,
  PipecatClientProvider,
} from "@pipecat-ai/client-react";
import type { DailyTransportConstructorOptions } from "@pipecat-ai/daily-transport";
import type {
  SmallWebRTCTransport,
  SmallWebRTCTransportConstructorOptions,
} from "@pipecat-ai/small-webrtc-transport";
import React, { useCallback, useEffect, useState } from "react";

declare global {
  interface Window {
    /**
     * Debug/bridge handle to the active client — set by PipecatAppBase and
     * read by the Android WebView host when posting app-state messages.
     */
    __pipecatClient?: PipecatClient;
  }
}

/**
 * Props for the PipecatAppBase component.
 */
export interface PipecatBaseProps {
  /** Optional parameters for connect(). Only used when no startBotParams are provided. */
  connectParams?: TransportConnectionParams;
  /** Optional parameters for startBot. */
  startBotParams?: APIRequest;
  /** Callback function to transform the startBot response before connecting. */
  startBotResponseTransformer?: (
    response: TransportConnectionParams,
  ) => TransportConnectionParams | Promise<TransportConnectionParams>;
  /** Type of transport to use for the connection */
  transportType: "smallwebrtc" | "daily";
  /** Options for configuring the transport. */
  transportOptions?:
    | SmallWebRTCTransportConstructorOptions
    | DailyTransportConstructorOptions;
  /** Optional configuration options for the Pipecat client */
  clientOptions?: Partial<PipecatClientOptions>;
  /** Whether to disable the theme provider */
  noThemeProvider?: boolean;
  /** Default theme to use for the app */
  themeProps?: Partial<ThemeProviderProps>;
  /** Whether to automatically connect to the session when the component mounts. Defaults to false. */
  connectOnMount?: boolean;
  /** Whether to automatically initialize devices when the component mounts. Defaults to false. */
  initDevicesOnMount?: boolean;
  /** Disables audio output for the bot. Default: false */
  noAudioOutput?: boolean;

  /**
   * Children can be either:
   * - A render prop function that receives helper props and returns React nodes
   * - Direct React nodes that will be wrapped with the necessary providers
   *
   * @param props - PipecatBaseChildProps including connection handlers, loading, and error state
   * @returns React.ReactNode
   */
  children:
    | ((props: PipecatBaseChildProps) => React.ReactNode)
    | React.ReactNode;
}

/**
 * Props that are passed to child components by the PipecatAppBase.
 */
export interface PipecatBaseChildProps {
  /** Pipecat client instance */
  client: PipecatClient | null;
  /** Function to initiate a connection to the session. Can be sync or async. */
  handleConnect?: () => void | Promise<void>;
  /** Function to disconnect from the current session. Can be sync or async. */
  handleDisconnect?: () => void | Promise<void>;
  /** Error message if connection fails */
  error?: string | null;
  /** Response returned from starting the bot. */
  rawStartBotResponse?: TransportConnectionParams | unknown;
  /** Transformed start bot response. */
  transformedStartBotResponse?: TransportConnectionParams | unknown;
}

const defaultStartBotResponseTransformer = (
  response: TransportConnectionParams,
) => response;

/**
 * PipecatAppBase component that provides a configured Pipecat client with audio capabilities.
 *
 * This component:
 * - Initializes a Pipecat client with the specified transport type
 * - Provides connection and disconnection handlers (sync or async)
 * - Wraps children in the necessary providers (ThemeProvider, PipecatClientProvider)
 * - Handles error states and loading states
 * - Automatically disconnects the client when unmounting
 * - Optionally disables theme provider based on noThemeProvider prop
 * - Optionally auto-connects to the session on mount based on connectOnMount prop
 *
 * @param props - Configuration for the audio client including connection params, transport type, and auto-connect behavior
 * @returns A provider component that wraps children with client context and handlers
 *
 * @example
 * ```tsx
 * // Using as a render prop (function children)
 * <PipecatAppBase
 *   connectParams={...}
 *   transportType="smallwebrtc"
 * >
 *   {({ client, handleConnect, handleDisconnect, error }) => (
 *     <YourComponent
 *       client={client}
 *       handleConnect={handleConnect}
 *       handleDisconnect={handleDisconnect}
 *       error={error}
 *     />
 *   )}
 * </PipecatAppBase>
 *
 * // Using with direct React nodes
 * <PipecatAppBase
 *   startBotParams={...}
 *   transportType="daily"
 * >
 *   <YourComponent />
 * </PipecatAppBase>
 *
 * // Using with noThemeProvider to disable theme wrapping
 * <PipecatAppBase
 *   startBotParams={...}
 *   transportType="daily"
 *   noThemeProvider={true}
 * >
 *   <YourComponent />
 * </PipecatAppBase>
 *
 * // Using with connectOnMount to auto-connect on component mount
 * <PipecatAppBase
 *   connectParams={...}
 *   transportType="smallwebrtc"
 *   connectOnMount={true}
 * >
 *   <YourComponent />
 * </PipecatAppBase>
 * ```
 */
export const PipecatAppBase: React.FC<PipecatBaseProps> = ({
  clientOptions,
  connectOnMount = false,
  connectParams,
  initDevicesOnMount = false,
  noAudioOutput = false,
  noThemeProvider = false,
  startBotParams,
  startBotResponseTransformer = defaultStartBotResponseTransformer,
  transportOptions,
  transportType,
  themeProps,
  children,
}) => {
  const [client, setClient] = useState<PipecatClient | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [rawStartBotResponse, setRawStartBotResponse] = useState<
    TransportConnectionParams | unknown
  >(null);
  const [transformedStartBotResponse, setTransformedStartBotResponse] =
    useState<TransportConnectionParams | unknown>(null);

  /**
   * Guard against unexpected mic track end: if the outbound audio track ends or is muted,
   * immediately reacquire a new mic track and replace it on the sender to keep the
   * WebRTC session alive. This targets SmallWebRTC transport.
   */
  useEffect(() => {
    if (transportType !== "smallwebrtc" || !client) return;

    let cleanupFns: Array<() => void> = [];
    let retryTimer: ReturnType<typeof setTimeout> | null = null;
    let stopped = false;
    let lastMicDeviceId: string | null = null;
    // 최대 해상도로 캡처하여 서버에서 필요시 다운스케일링
    // 동적 해상도 변경은 복잡하므로 최대 해상도로 고정
    const MAX_VIDEO_WIDTH = 3840;
    const MAX_VIDEO_HEIGHT = 2160;

    const setupTrackGuards = () => {
      if (stopped) return;
      const transport =
        (client.transport as
          | {
              peerConnection?: RTCPeerConnection;
              pc?: RTCPeerConnection;
              _pc?: RTCPeerConnection;
            }
          | null
          | undefined) ?? null;
      const pc: RTCPeerConnection | undefined =
        transport?.peerConnection ?? transport?.pc ?? transport?._pc;
      if (!pc) {
        retryTimer = setTimeout(setupTrackGuards, 500);
        return;
      }

      const replaceAudioTrack = async (
        sender: RTCRtpSender,
        attempt = 1,
      ): Promise<void> => {
        const oldTrack = sender.track || null;
        const preferredId =
          oldTrack?.getSettings().deviceId || lastMicDeviceId || undefined;
        const constraintDevice =
          preferredId !== undefined ? { deviceId: { exact: preferredId } } : {};

        try {
          const stream = await navigator.mediaDevices.getUserMedia({
            audio: { ...constraintDevice },
            video: false,
          });
          const newTrack = stream.getAudioTracks()[0];
          if (!newTrack) throw new Error("No audio track returned");
          lastMicDeviceId =
            newTrack.getSettings().deviceId ?? preferredId ?? null;
          await sender.replaceTrack(newTrack);
          if (oldTrack && oldTrack !== newTrack) {
            oldTrack.stop();
          }
          console.warn(
            `[PipecatAppBase] Replaced audio track after end/mute (attempt ${attempt}) deviceId=${lastMicDeviceId ?? "unknown"}`,
          );
        } catch (err) {
          console.error(
            `[PipecatAppBase] Failed to replace audio track (attempt ${attempt})`,
            err,
          );
          if (attempt < 3) {
            setTimeout(() => replaceAudioTrack(sender, attempt + 1), 500);
          }
        }
      };

      const replaceVideoTrack = async (
        sender: RTCRtpSender,
        width: number,
        height: number,
        attempt = 1,
      ): Promise<void> => {
        const oldTrack = sender.track || null;
        const preferredId = oldTrack?.getSettings().deviceId || undefined;
        const constraintDevice =
          preferredId !== undefined ? { deviceId: { exact: preferredId } } : {};

        try {
          // 최대 해상도로 캡처 (서버에서 필요시 다운스케일링)
          // ideal을 사용하여 가능한 최대 해상도로 시도
          const stream = await navigator.mediaDevices.getUserMedia({
            audio: false,
            video: {
              ...constraintDevice,
              width: { ideal: width, max: 3840 },
              height: { ideal: height, max: 2160 },
            },
          });
          const newTrack = stream.getVideoTracks()[0];
          if (!newTrack) throw new Error("No video track returned");

          // 실제 획득한 해상도 확인
          const settings = newTrack.getSettings();
          const actualWidth = settings.width || width;
          const actualHeight = settings.height || height;

          await sender.replaceTrack(newTrack);
          if (oldTrack && oldTrack !== newTrack) {
            oldTrack.stop();
          }
          console.log(
            `[PipecatAppBase] Replaced video track: requested=${width}x${height}, actual=${actualWidth}x${actualHeight} (attempt ${attempt})`,
          );
        } catch (err) {
          console.error(
            `[PipecatAppBase] Failed to replace video track (attempt ${attempt})`,
            err,
          );
          if (attempt < 3) {
            setTimeout(
              () => replaceVideoTrack(sender, width, height, attempt + 1),
              500,
            );
          }
        }
      };

      // Audio track guards
      pc.getSenders()
        .filter((s) => s.track?.kind === "audio")
        .forEach((sender) => {
          const track = sender.track;
          if (!track) return;

          type TrackWithMuted = MediaStreamTrack & { muted?: boolean };
          const state = track.readyState;
          console.log(
            `[PipecatAppBase] Guarding audio track readyState=${state}, enabled=${track.enabled}, muted=${(track as TrackWithMuted).muted ?? "n/a"}`,
          );

          const onEnded = () => {
            console.warn("[PipecatAppBase] Audio track ended; reacquiring mic");
            replaceAudioTrack(sender);
          };
          const onMute = () => {
            console.warn("[PipecatAppBase] Audio track muted");
          };
          const onUnmute = () => {
            console.log("[PipecatAppBase] Audio track unmuted");
          };

          track.addEventListener("ended", onEnded);
          track.addEventListener("mute", onMute);
          track.addEventListener("unmute", onUnmute);

          cleanupFns.push(() => {
            track.removeEventListener("ended", onEnded);
            track.removeEventListener("mute", onMute);
            track.removeEventListener("unmute", onUnmute);
          });
        });

      // Video track guards and resolution change handler
      // 초기 비디오 트랙이 없거나 낮은 해상도인 경우 최대 해상도로 업그레이드
      const videoSenders = pc
        .getSenders()
        .filter((s) => s.track?.kind === "video");

      if (videoSenders.length > 0) {
        // 기존 비디오 트랙이 있으면 최대 해상도로 업그레이드 시도
        (async () => {
          for (const sender of videoSenders) {
            const track = sender.track;
            if (track) {
              const settings = track.getSettings();
              const currentWidth = settings.width || 0;
              const currentHeight = settings.height || 0;

              // 현재 해상도가 최대 해상도보다 낮으면 업그레이드
              if (
                currentWidth < MAX_VIDEO_WIDTH ||
                currentHeight < MAX_VIDEO_HEIGHT
              ) {
                console.log(
                  `[PipecatAppBase] Upgrading video resolution from ${currentWidth}x${currentHeight} to ${MAX_VIDEO_WIDTH}x${MAX_VIDEO_HEIGHT}`,
                );
                await replaceVideoTrack(
                  sender,
                  MAX_VIDEO_WIDTH,
                  MAX_VIDEO_HEIGHT,
                );
              }
            }
          }
        })();
      }

      videoSenders.forEach((sender) => {
        const track = sender.track;
        if (!track) return;

        const onEnded = () => {
          console.warn(
            "[PipecatAppBase] Video track ended; reacquiring camera at max resolution",
          );
          replaceVideoTrack(sender, MAX_VIDEO_WIDTH, MAX_VIDEO_HEIGHT);
        };

        track.addEventListener("ended", onEnded);

        cleanupFns.push(() => {
          track.removeEventListener("ended", onEnded);
        });
      });

      // Expose function to change video resolution dynamically
      // This can be called when server requests higher resolution
      type WindowWithPipecat = Window & {
        __pipecatChangeVideoResolution?: (
          width: number,
          height: number,
        ) => Promise<void>;
      };
      (window as WindowWithPipecat).__pipecatChangeVideoResolution = async (
        width: number,
        height: number,
      ) => {
        if (stopped || !pc) return;
        const videoSenders = pc
          .getSenders()
          .filter((s) => s.track?.kind === "video");
        for (const sender of videoSenders) {
          await replaceVideoTrack(sender, width, height);
        }
      };
    };

    setupTrackGuards();

    return () => {
      stopped = true;
      cleanupFns.forEach((fn) => fn());
      cleanupFns = [];
      if (retryTimer) clearTimeout(retryTimer);
      type WindowWithPipecat = Window & {
        __pipecatChangeVideoResolution?: (
          width: number,
          height: number,
        ) => Promise<void>;
      };
      delete (window as WindowWithPipecat).__pipecatChangeVideoResolution;
    };
  }, [client, transportType]);

  const startAndConnect = useCallback(
    async (client: PipecatClient) => {
      try {
        if (startBotParams) {
          const response = await client.startBot({
            requestData: {},
            ...startBotParams,
          });
          setRawStartBotResponse(response);
          if (transportType === "smallwebrtc") {
            // Check if response has ICEServers
            if (
              typeof response === "object" &&
              response !== null &&
              "iceConfig" in response
            ) {
              const iceConfig = response.iceConfig as {
                iceServers: RTCIceServer[];
              };
              (client.transport as SmallWebRTCTransport).iceServers =
                iceConfig.iceServers;
            }
          }
          const transformedResponse =
            await startBotResponseTransformer(response);
          await client.connect(transformedResponse);
          setTransformedStartBotResponse(transformedResponse);
        } else {
          await client.connect(connectParams ?? {});
        }
      } catch (err) {
        console.error("Connection error:", err);
        setError(
          `Failed to start session: ${err instanceof Error ? err.message : String(err)}`,
        );
      }
    },
    [connectParams, startBotParams, startBotResponseTransformer, transportType],
  );

  /**
   * Initializes the Pipecat client with the specified transport type.
   * Creates a new client instance when transport type or connection params change.
   */
  useEffect(() => {
    let currentClient: PipecatClient | null = null;

    (async () => {
      try {
        const transport = await createTransport(
          transportType,
          transportOptions,
        );

        const pcClient = new PipecatClient({
          enableCam: true,
          enableMic: true,
          transport: transport,
          // 최대 해상도로 캡처하여 서버에서 필요시 다운스케일링
          // 서버 측 리사이징이 더 효율적이므로 클라이언트는 최대 해상도 유지
          ...clientOptions,
        });
        currentClient = pcClient;
        setClient(pcClient);

        // 전역에 client 저장 (Android에서 앱 상태 메시지를 보낼 때 사용)
        window.__pipecatClient = pcClient;

        if (initDevicesOnMount) {
          await pcClient.initDevices();
        }

        if (connectOnMount) {
          await startAndConnect(pcClient);
        }
      } catch (error) {
        console.error("Failed to initialize transport:", error);
      }
    })();

    return () => {
      currentClient?.disconnect();
      setClient(null);
      setError(null);
      // 전역 client 제거
      delete window.__pipecatClient;
    };
  }, [
    clientOptions,
    connectOnMount,
    initDevicesOnMount,
    startAndConnect,
    transportOptions,
    transportType,
  ]);

  /**
   * Initiates a connection to the session using the configured client.
   * Only allows connection from specific states (initialized, disconnected, error).
   * Clears any previous errors and handles connection failures.
   */
  const handleConnect = async () => {
    if (
      !client ||
      !["initialized", "disconnected", "error"].includes(client.state)
    ) {
      return;
    }
    setError(null);

    await startAndConnect(client);
  };

  /**
   * Disconnects from the current session.
   * Safely handles the case where no client is available.
   */
  const handleDisconnect = async () => {
    if (!client) return;
    await client.disconnect();
  };

  /**
   * Show loading state while client is being initialized.
   * Don't render PipecatClientProvider until client is ready.
   */
  if (!client) {
    return typeof children === "function"
      ? children({ client: null, error: null })
      : children;
  }

  const passedProps: PipecatBaseChildProps = {
    client,
    handleConnect,
    handleDisconnect,
    error,
    rawStartBotResponse,
    transformedStartBotResponse,
  };

  // Only create PipecatClientProvider when client is fully initialized
  const clientProvider = (
    <PipecatClientProvider client={client!}>
      <ConversationProvider>
        {typeof children === "function" ? children(passedProps) : children}
        {!noAudioOutput && <PipecatClientAudio />}
      </ConversationProvider>
    </PipecatClientProvider>
  );

  return noThemeProvider ? (
    clientProvider
  ) : (
    <ThemeProvider {...themeProps}>{clientProvider}</ThemeProvider>
  );
};
