"use client";

import { AskBar } from "@/components/elements/AskBar";
import { ClientStatus } from "@/components/elements/ClientStatus";
import ConnectButton from "@/components/elements/ConnectButton";
import type { ConversationProps } from "@/components/elements/Conversation";
import { Wordmark } from "@/components/elements/Wordmark";
import { SessionInfo } from "@/components/elements/SessionInfo";
import UserAudioControl from "@/components/elements/UserAudioControl";
import UserAudioOutputControl from "@/components/elements/UserAudioOutputControl";
import UserVideoControl from "@/components/elements/UserVideoControl";
import { BotAudioPanel } from "@/components/panels/BotAudioPanel";
import { BotVideoPanel } from "@/components/panels/BotVideoPanel";
import ConversationPanel from "@/components/panels/ConversationPanel";
import { EventsPanel } from "@/components/panels/EventsPanel";
import { InfoPanel } from "@/components/panels/InfoPanel";
import { PipecatAppBase, PipecatBaseProps } from "@/components/PipecatAppBase";
import ThemeModeToggle from "@/components/ThemeModeToggle";
import {
  Banner,
  BannerClose,
  BannerIcon,
  BannerTitle,
} from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { SpinLoader } from "@/components/ui/loader";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  ResizableHandle,
  ResizablePanel,
  ResizablePanelGroup,
} from "@/components/ui/resizable";
import { usePipecatConversation } from "@/hooks/usePipecatConversation";
import { usePipecatTextInput } from "@/hooks/usePipecatTextInput";
import { cn } from "@/lib/utils";
import { type ConversationMessage } from "@/types/conversation";
import { type PipecatClientOptions, RTVIEvent } from "@pipecat-ai/client-js";
import {
  PipecatClientCamToggle,
  PipecatClientMicToggle,
  PipecatClientVideo,
  usePipecatClient,
  usePipecatClientCamControl,
  usePipecatClientMediaDevices,
  usePipecatClientMicControl,
  useRTVIClientEvent,
} from "@pipecat-ai/client-react";
import {
  CheckCircleIcon,
  ChevronsLeftRightEllipsisIcon,
  CircleAlertIcon,
  InfoIcon,
  MicIcon,
  PanelLeftCloseIcon,
  PanelRightCloseIcon,
  RefreshCwIcon,
  VideoIcon,
} from "lucide-react";
import { type ImperativePanelHandle } from "react-resizable-panels";
import React, { memo, useCallback, useEffect, useRef, useState } from "react";
import { SmallWebRTCCodecSetter } from "./SmallWebRTCCodecSetter";
import UserScreenControl from "../../components/elements/UserScreenControl";

export interface ConsoleTemplateProps
  extends Omit<PipecatBaseProps, "children"> {
  /** Disables RTVI related functionality. Default: false */
  noRTVI?: boolean;
  /** Specifies the RTVI version in use by the server. Default: null */
  serverRTVIVersion?: string | null;
  /** Disables user audio input entirely. Default: false */
  noUserAudio?: boolean;
  /** Disables user video input entirely. Default: false */
  noUserVideo?: boolean;
  /** Disables user screen control entirely. Default: false */
  noScreenControl?: boolean;
  /** Disables audio output for the bot. Default: false */
  noAudioOutput?: boolean;
  /** Disables audio visualization for the bot. Default: false */
  noBotAudio?: boolean;
  /** Disables video visualization for the bot. Default: true */
  noBotVideo?: boolean;
  /** Disables automatic initialization of devices. Default: false */
  noAutoInitDevices?: boolean;
  /**
   * Start with the microphone muted on first connect. The audio track is still
   * created (so the SmallWebRTC media stream stays alive and the connection
   * isn't dropped for being idle), but input is silenced and the mic button
   * shows the off state until the user taps it. Default: false
   */
  startMicMuted?: boolean;

  /** Theme to use for the UI. Default: "system" */
  theme?: string;
  /** Disables the theme switcher in the header. Default: false */
  noThemeSwitch?: boolean;
  /** Disables the logo in the header. Default: false */
  noLogo?: boolean;
  /** Disables the session info panel. Default: false */
  noSessionInfo?: boolean;
  /** Disables the status info panel. Default: false */
  noStatusInfo?: boolean;

  /**
   * Title displayed in the header center.
   * Default: `undefined` — the new Pipecat wordmark on the left is enough.
   * Pass any string to opt back into a center label.
   */
  titleText?: string;
  /**
   * Override the Pipecat accent color (the `--pc-accent` CSS variable).
   * Pass any hex/oklch string, e.g. `"#C73E1D"` to keep the vermilion tone
   * from the Voice UI handoff, or your own brand color. Default: unchanged
   * (whatever value `--pc-accent` resolves to in the surrounding theme).
   */
  accent?: string;
  /** Label for assistant messages. Default: "assistant" */
  assistantLabelText?: string;
  /** Label for user messages. Default: "user" */
  userLabelText?: string;
  /** Label for system messages. Default: "system" */
  systemLabelText?: string;

  /** Whether to collapse the info panel by default. Default: false */
  collapseInfoPanel?: boolean;
  /** Whether to collapse the media panel by default. Default: false */
  collapseMediaPanel?: boolean;

  // Properties from original interface that are still in use but not in the new interface

  /**
   * Sets the audio codec. Only applicable for SmallWebRTC transport.
   * Defaults to "default" which uses the browser's default codec.
   */
  audioCodec?: string;

  /**
   * Sets the video codec. Only applicable for SmallWebRTC transport.
   * Defaults to "default" which uses the browser's default codec.
   */
  videoCodec?: string;

  /**
   * Disables the conversation panel.
   * The bot may still send messages, but they won't be displayed.
   */
  noConversation?: boolean;

  /**
   * Disables the metrics panel.
   * The bot may still send metrics, but they won't be displayed.
   */
  noMetrics?: boolean;

  /**
   * Custom logo component to display in the header.
   * If provided, this will replace the default Pipecat logo.
   */
  logoComponent?: React.ReactNode;

  /**
   * Props to pass to the Conversation component.
   * Allows customization of the conversation display.
   */
  conversationElementProps?: Partial<ConversationProps>;

  /**
   * Callback that receives the injectMessage function.
   * This allows parent components to manually add messages to the conversation.
   */
  onInjectMessage?: (
    injectMessage: (
      message: Pick<ConversationMessage, "role" | "parts">,
    ) => void,
  ) => void;

  /**
   * Callback that receives incoming server messages.
   * This allows parent components to subscribe to server messages from the client.
   */
  onServerMessage?: (data: unknown) => void;

  /**
   * @deprecated Use titleText instead
   * Title displayed in the header. Defaults to "Pipecat Playground".
   */
  title?: string;
}

// 로그인 후 첫 홈 화면(Idle)에 띄우는 4개 에이전트 카드.
// 카드 제목이 곧 액션 키 — 회의/번역/동시통역은 해당 에이전트를 활성화한다.
// PLAN은 "오늘 할일 계획" 기능으로 아직 미설계 — 링크하지 않는다(클릭 무동작).
// 디자인·아이콘·문구는 docs/durion-promo/01-structure-features.html 의 .fcard 를 따른다.
const HOME_AGENT_MEETING = "회의 Agent";
const HOME_AGENT_TRANSLATE = "번역 Agent";
const HOME_AGENT_SIMULTANEOUS = "동시통역 Agent";

interface HomeAgentCard {
  /** 클릭 액션 키이자 카드 제목. */
  title: string;
  /** 아이콘 박스에 들어가는 이모지. */
  icon: string;
  /** 상단 모노 라벨(promo 의 .ft). */
  label: string;
  /** 카드 설명(promo 의 p). */
  description: string;
  /** 우상단 NEW 뱃지 표시 여부. */
  isNew?: boolean;
}

const HOME_AGENT_CARDS: HomeAgentCard[] = [
  {
    title: HOME_AGENT_MEETING,
    icon: "🧑‍💼",
    label: "Meeting Agent",
    description:
      "회의를 실시간으로 받아쓰고 요약. 안건·결정·액션아이템을 정리.",
    isNew: true,
  },
  {
    title: HOME_AGENT_TRANSLATE,
    icon: "🌐",
    label: "Translation",
    description: "10여개 언어의 대화·문서를 실시간 번역.",
    isNew: true,
  },
  {
    title: HOME_AGENT_SIMULTANEOUS,
    icon: "🎧",
    label: "Live Interpret",
    description:
      "Live 오디오로 양방향 저지연 동시통역. 해외 미팅에서 진가.",
    isNew: true,
  },
  {
    title: "PLAN",
    icon: "📅",
    label: "Daily Brief",
    description:
      "메일·일정을 읽어 오늘 할 일을 타임라인 순서로 브리핑하는 아침 비서.",
    isNew: true,
  },
];

// 홈 카드 아래에 노출하는 예시 발화(말풍선). 클릭하면 AskBar 입력창에 문장이 채워진다
// (자동 전송하지 않음 — 사용자가 확인 후 보낸다). 출처: docs/durion-promo/02-use-cases.html
// "사용자 질의 시나리오 10선" 중 1·6·7·8·9·10번.
const HOME_EXAMPLE_PROMPTS: string[] = [
  "강남역까지 차로 가는 길 띄워줘",
  "어제 안 읽은 메일 중 중요한 것만 요약해줘",
  "방금 받은 회의 초대 메일, 일정에 넣어줘",
  "저번에 말한 그 추천 카페 다시 알려줘",
  "나 우유 알레르기 있는 거 기억하지? 근처 디저트 카페 찾아 길안내해줘",
  "운전 중인데, 집 도착하면 보일러 켜지게 하고 가는 길에 주유소 띄워줘",
];

const defaultClientOptions: Partial<PipecatClientOptions> = {
  enableCam: false,
  enableMic: true,
};
const defaultTransportOptions: React.ComponentProps<
  typeof PipecatAppBase
>["transportOptions"] = {};

export const ConsoleTemplate: React.FC<ConsoleTemplateProps> = memo((props) => {
  const {
    clientOptions = defaultClientOptions,
    connectOnMount = false,
    connectParams,
    noAutoInitDevices = false,
    startBotParams,
    startBotResponseTransformer,
    theme,
    transportOptions = defaultTransportOptions,
    transportType = "smallwebrtc",
  } = props;

  return (
    <PipecatAppBase
      connectOnMount={connectOnMount}
      connectParams={connectParams}
      initDevicesOnMount={!noAutoInitDevices}
      startBotParams={startBotParams}
      startBotResponseTransformer={startBotResponseTransformer}
      transportType={transportType}
      clientOptions={clientOptions}
      transportOptions={transportOptions}
      themeProps={{
        defaultTheme: theme,
      }}
    >
      {({ client, error, handleConnect, handleDisconnect }) =>
        !client ? (
          <div className="flex items-center justify-center h-full w-full">
            <SpinLoader />
          </div>
        ) : (
          <ConsoleUI
            {...props}
            error={error}
            handleConnect={handleConnect}
            handleDisconnect={handleDisconnect}
          />
        )
      }
    </PipecatAppBase>
  );
});

interface ConsoleUIProps extends ConsoleTemplateProps {
  error?: string | null;
  handleConnect?: () => void | Promise<void>;
  handleDisconnect?: () => void | Promise<void>;
  participantId?: string;
}

const ConsoleUI = ({
  // Core functionality
  // noRTVI,
  // serverRTVIVersion,
  noUserAudio = false,
  noUserVideo = false,
  noScreenControl = false,
  noAudioOutput = false,
  noBotAudio = false,
  noBotVideo = false,

  // Transport and client options
  transportType = "smallwebrtc",

  // UI configuration
  noThemeSwitch = false,
  noLogo = false,
  noSessionInfo = false,
  noStatusInfo = false,

  // Text labels
  titleText,
  assistantLabelText,
  userLabelText,
  systemLabelText,

  // Tone
  accent,

  // Panel collapse settings
  collapseInfoPanel = false,
  collapseMediaPanel = false,

  // Legacy properties
  audioCodec = "default",
  videoCodec = "default",
  noConversation = false,
  noMetrics = false,
  logoComponent,
  conversationElementProps,
  onInjectMessage,
  onServerMessage,

  // Passed props
  error,
  handleConnect,
  handleDisconnect,
  startMicMuted = false,
}: ConsoleUIProps) => {
  const handoffWaitingTitleLine1 = "외부 사이트에서 돌아오셨군요!";
  const handoffWaitingTitleLine2 = "잠시만 기다려주세요";
  const handoffWaitingMessage = `${handoffWaitingTitleLine1} ${handoffWaitingTitleLine2}`;
  const [isBotAreaCollapsed, setIsBotAreaCollapsed] = useState(false);
  const [isInfoPanelCollapsed, setIsInfoPanelCollapsed] = useState(false);
  const [isEventsPanelCollapsed, setIsEventsPanelCollapsed] = useState(false);
  const [participantId, setParticipantId] = useState("");
  const [participantDisplayName, setParticipantDisplayName] = useState("");
  const [sessionId, setSessionId] = useState("");
  const [isMeetingModeActive, setIsMeetingModeActive] = useState(false);
  const [isTranslationModeActive, setIsTranslationModeActive] = useState(false);
  // AskBar 텍스트 입력값. 음성(마이크)과 별개로 항상 사용 가능.
  const [askText, setAskText] = useState("");
  // 미연결 상태에서 입력·전송한 텍스트를 보관했다가, 연결이 ready 되면 전송하기 위한 버퍼.
  const pendingTextRef = useRef<string>("");
  // Which translation agent is active: "gemini_google" (web 번역 Agent) or
  // "gemini_google_native" (native 동시통역 Agent). null when inactive.
  const [translationProvider, setTranslationProvider] = useState<string | null>(
    null,
  );
  const client = usePipecatClient();
  const [showPipVideo, setShowPipVideo] = useState(false);
  const [pipPosition, setPipPosition] = useState({ x: 16, y: 80 }); // Initial position in pixels from right/top
  const dragRef = useRef<{
    isDragging: boolean;
    startX: number;
    startY: number;
    initialX: number;
    initialY: number;
  } | null>(null);
  const internalCamIndexRef = useRef(0);

  const { availableCams, updateCam } = usePipecatClientMediaDevices();

  // startMicMuted: 첫 연결(ready) 시 마이크를 한 번 음소거한다. React enableMic()을
  // 거치므로 isMicEnabled(버튼 상태)와 트랜스포트 음소거가 함께 갱신된다. 트랙 자체는
  // (enableMic:true로) 생성돼 있어 SmallWebRTC 미디어 스트림이 유지되고 유휴 끊김이 없다.
  const { enableMic } = usePipecatClientMicControl();
  const startMicMutedRef = useRef(false);
  // 사용자가 마이크 버튼을 직접 눌러 음성을 켰는지. startMicMuted일 때, 누르기
  // 전까지는 (연결 초기 isMicEnabled가 잠깐 true로 잡혀도) 버튼을 항상 OFF로
  // 표시해 "빨강→회색" 깜빡임 없이 처음부터 회색으로 시작하게 한다.
  const [micUserActivated, setMicUserActivated] = useState(false);
  useEffect(() => {
    if (!startMicMuted || startMicMutedRef.current) return;
    if (client?.state === "ready") {
      startMicMutedRef.current = true;
      enableMic(false);
    }
  }, [startMicMuted, client?.state, enableMic]);

  // 대표 전면/후면 카메라 하나씩만 추출하는 헬퍼 함수
  const getRepresentativeCams = (cams: MediaDeviceInfo[]) => {
    const front = cams.find((c) => /front|user|facing|전면/i.test(c.label));
    const back = cams.find((c) => /back|rear|environment|후면/i.test(c.label));
    return { front, back };
  };

  const switchCamera = () => {
    const { front, back } = getRepresentativeCams(availableCams);

    // 둘 중 하나라도 없으면 기존의 단순 인덱스 토글로 폴백
    if (!front || !back) {
      if (availableCams.length < 2) return;
      internalCamIndexRef.current =
        (internalCamIndexRef.current + 1) % availableCams.length;
      updateCam(availableCams[internalCamIndexRef.current].deviceId);
      return;
    }

    // 대표 카메라 2개 사이를 무조건 0(전면) <-> 1(후면) 토글
    internalCamIndexRef.current = internalCamIndexRef.current === 0 ? 1 : 0;
    const nextDevice = internalCamIndexRef.current === 0 ? front : back;
    updateCam(nextDevice.deviceId);
  };

  const handlePointerDown = (e: React.PointerEvent) => {
    const el = e.currentTarget as HTMLElement;
    el.setPointerCapture(e.pointerId);
    dragRef.current = {
      isDragging: true,
      startX: e.clientX,
      startY: e.clientY,
      initialX: pipPosition.x,
      initialY: pipPosition.y,
    };
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (!dragRef.current?.isDragging) return;

    const deltaX = dragRef.current.startX - e.clientX;
    const deltaY = e.clientY - dragRef.current.startY;

    setPipPosition({
      x: dragRef.current.initialX + deltaX,
      y: dragRef.current.initialY + deltaY,
    });
  };

  const handlePointerUp = () => {
    if (dragRef.current) {
      dragRef.current.isDragging = false;
    }
  };

  const toggleMeetingMode = async () => {
    if (!client) {
      console.warn("[MeetingMode] Client not available");
      return;
    }

    try {
      // 버튼 클릭 시 즉시 상태 업데이트 (optimistic update)
      const newState = !isMeetingModeActive;
      setIsMeetingModeActive(newState);

      const messageType = newState ? "start_meeting_mode" : "end_meeting_mode";
      console.log(`[MeetingMode] Sending ${messageType} message`);
      await client.sendClientMessage(messageType, {});
      console.log(`[MeetingMode] ${messageType} message sent successfully`);
    } catch (error) {
      console.error(
        "[MeetingMode] Failed to send meeting mode message:",
        error,
      );
      // 에러 발생 시 상태 복원
      setIsMeetingModeActive(!isMeetingModeActive);
    }
  };

  // LiveTranslationPlugin 호출 함수 (Android WebView 환경)
  // CodeEngineBridge는 WebView의 JavaScriptInterface로 주입됨
  const callLiveTranslationPlugin = useCallback(
    (
      action: "startTranslation" | "stopTranslation" | "openTranslationAgent",
    ) => {
      try {
        // window 객체에서 CodeEngineBridge 직접 접근 시도
        const windowWithBridge = window as typeof window & {
          CodeEngineBridge?: {
            postMessage: (message: string) => void;
          };
        };
        const bridge = windowWithBridge.CodeEngineBridge;

        console.log(
          "[TranslationMode] Checking CodeEngineBridge availability...",
        );
        console.log("[TranslationMode] window.CodeEngineBridge:", bridge);
        console.log("[TranslationMode] typeof bridge:", typeof bridge);

        if (bridge && typeof bridge.postMessage === "function") {
          const command = {
            serviceName: "LiveTranslation",
            action: action,
            params: {},
            requestId: `LiveTranslation.${action}.${Date.now()}`,
          };

          const commandJson = JSON.stringify(command);
          console.log(
            `[TranslationMode] Calling LiveTranslationPlugin: ${action}`,
          );
          console.log(`[TranslationMode] Command JSON: ${commandJson}`);

          // CodeEngineBridge.postMessage 호출
          bridge.postMessage(commandJson);

          console.log(
            `[TranslationMode] LiveTranslationPlugin ${action} called successfully`,
          );
        } else {
          console.warn(
            "[TranslationMode] CodeEngineBridge not available (not in Android WebView)",
          );
        }
      } catch (pluginError) {
        console.error(
          "[TranslationMode] Failed to call LiveTranslationPlugin:",
          pluginError,
        );
        // 플러그인 호출 실패는 치명적이지 않으므로 계속 진행
      }
    },
    [],
  );

  const toggleTranslationMode = useCallback(
    async (provider: string = "gemini_google") => {
      if (!client) {
        console.warn("[TranslationMode] Client not available");
        return;
      }

      try {
        // 버튼 클릭 시 즉시 상태 업데이트 (optimistic update)
        const newState = !isTranslationModeActive;
        setIsTranslationModeActive(newState);
        setTranslationProvider(newState ? provider : null);

        const messageType = newState
          ? "start_translation_mode"
          : "end_translation_mode";
        console.log(
          `[TranslationMode] Sending ${messageType} message (provider=${provider})`,
        );
        // start 시에만 provider를 실어 보낸다. 봇은 이 값을 env 기본값보다
        // 우선 적용한다 (웹 번역 = gemini_google, 동시통역 = gemini_google_native).
        if (newState) {
          await client.sendClientMessage("start_translation_mode", {
            provider,
          });
        } else {
          // 종료: 네이티브 동시통역이 WebRTC를 끊었을 수 있다. end 메시지
          // 전송 실패가 아래 fast-reconnect를 건너뛰게 해서는 안 되므로
          // 여기서만 따로 잡아준다.
          try {
            await client.sendClientMessage("end_translation_mode", {});
          } catch (e) {
            console.warn(
              "[TranslationMode] end message skipped (client offline):",
              e,
            );
          }
        }
        console.log(
          `[TranslationMode] ${messageType} message sent successfully`,
        );

        // end_translation_mode인 경우 빠르게 재연결 수행
        // 서버에서 다른 pipeline으로 전환되었거나 native 앱의 audio recorder를 통해
        // raw data를 사용한 후 원래 bot 기능으로 돌아가기 위해 재연결이 필요함
        // 최소한의 대기 시간으로 빠르게 재연결하여 UI 상태 변경을 최소화
        if (!newState && messageType === "end_translation_mode") {
          console.log(
            "[TranslationMode] Fast reconnecting after end_translation_mode...",
          );
          const disconnectResult = client.disconnect();
          const disconnectPromise =
            disconnectResult instanceof Promise
              ? disconnectResult
              : Promise.resolve();

          disconnectPromise
            .then(() => {
              // 최소한의 대기 시간(100ms)으로 빠르게 재연결
              // 이렇게 하면 transport state 변경이 거의 보이지 않음
              setTimeout(() => {
                try {
                  const connectResult = client.connect({});
                  const connectPromise =
                    connectResult instanceof Promise
                      ? connectResult
                      : Promise.resolve();
                  connectPromise
                    .then(() => {
                      console.log(
                        "[TranslationMode] Fast reconnect successful",
                      );
                    })
                    .catch((error) => {
                      console.error(
                        "[TranslationMode] Error during fast reconnect:",
                        error,
                      );
                    });
                } catch (error) {
                  console.error(
                    "[TranslationMode] Error calling client.connect():",
                    error,
                  );
                }
              }, 100); // 100ms로 빠르게 처리하여 UI 상태 변경 최소화
            })
            .catch((error) => {
              console.error(
                "[TranslationMode] Error during disconnect:",
                error,
              );
            });
        }
      } catch (error) {
        console.error(
          "[TranslationMode] Failed to send translation mode message:",
          error,
        );
        // 에러 발생 시 상태 복원
        setIsTranslationModeActive(!isTranslationModeActive);
        setTranslationProvider(translationProvider);
      }
    },
    [client, isTranslationModeActive, translationProvider],
  );
  const [meetingModeNotification, setMeetingModeNotification] = useState<{
    visible: boolean;
    title: string;
    message: string;
    variant: "primary" | "destructive";
  } | null>(null);
  const [handoffWaiting, setHandoffWaiting] = useState(false);
  const [handoffWaitingDots, setHandoffWaitingDots] = useState(".");
  const handoffWaitingRef = useRef(false);
  const handoffWaitingStartedAtRef = useRef<string | null>(null);
  const handoffWaitingStartedMsRef = useRef<number>(0);

  const infoPanelRef = useRef<ImperativePanelHandle>(null);

  const handleConversationMessageAdded = useCallback(
    (message: ConversationMessage) => {
      if (!handoffWaitingRef.current) return;
      if (message.role !== "assistant") return;
      if (!handoffWaitingStartedAtRef.current) return;
      if (message.createdAt <= handoffWaitingStartedAtRef.current) return;

      const firstPart = message.parts?.[0];
      const firstText =
        firstPart && typeof firstPart.text === "string" ? firstPart.text : "";
      if (!firstText.trim()) return;

      const isHandoffWaitingMessage = firstText.includes(handoffWaitingMessage);
      const minVisibleMs = 1200;
      const elapsed = Date.now() - handoffWaitingStartedMsRef.current;
      if (!isHandoffWaitingMessage && elapsed < minVisibleMs) return;

      if (!isHandoffWaitingMessage) {
        setHandoffWaiting(false);
        handoffWaitingStartedAtRef.current = null;
        handoffWaitingStartedMsRef.current = 0;
      }
    },
    [handoffWaitingMessage],
  );

  const { injectMessage } = usePipecatConversation({
    onMessageAdded: handleConversationMessageAdded,
  });
  const { isCamEnabled } = usePipecatClientCamControl();
  // AskBar 텍스트 입력 → LLM 컨텍스트. 연결되면 STT→LLM→TTS는 봇에서 자동.
  const { sendTextMessage, isReady: isTextInputReady } = usePipecatTextInput();

  const handleAskBarSubmit = useCallback(
    (value: string) => {
      if (!value.trim()) return;
      void (async () => {
        try {
          if (isTextInputReady) {
            await sendTextMessage(value);
            setAskText("");
          } else {
            // 아직 봇에 미연결: 입력 텍스트를 보관하고 연결을 트리거한다.
            // 연결이 ready 되면 아래 useEffect가 보관된 텍스트를 전송한다.
            pendingTextRef.current = value;
            setAskText("");
            await handleConnect?.();
          }
        } catch (e) {
          console.error("[AskBar] Failed to send text message:", e);
        }
      })();
    },
    [isTextInputReady, sendTextMessage, handleConnect],
  );

  // 미연결 상태에서 입력한 텍스트를, 연결이 ready 되는 즉시 전송한다.
  useEffect(() => {
    if (isTextInputReady && pendingTextRef.current) {
      const pending = pendingTextRef.current;
      pendingTextRef.current = "";
      void sendTextMessage(pending);
    }
  }, [isTextInputReady, sendTextMessage]);

  useEffect(() => {
    handoffWaitingRef.current = handoffWaiting;
  }, [handoffWaiting]);

  useEffect(() => {
    if (!handoffWaiting) {
      setHandoffWaitingDots(".");
      handoffWaitingStartedMsRef.current = 0;
      return;
    }
    const frames = [".", "..", "..."];
    let idx = 0;
    const timer = window.setInterval(() => {
      idx = (idx + 1) % frames.length;
      setHandoffWaitingDots(frames[idx]);
    }, 500);
    return () => window.clearInterval(timer);
  }, [handoffWaiting]);

  // Expose injectMessage to parent if requested
  useEffect(() => {
    if (onInjectMessage) onInjectMessage(injectMessage);
  }, [onInjectMessage, injectMessage]);

  // Android 앱에서 번역 모드 종료를 호출할 수 있도록 전역 함수 노출
  useEffect(() => {
    if (!client) return;

    // window 객체에 번역 모드 종료 함수 추가
    const sendTranslationModeEnd = async () => {
      try {
        console.log("[Console] sendTranslationModeEnd called from native app.");
        //if (client) {
        //  await client.sendClientMessage("end_translation_mode", {});
        //  console.log(
        //    "[Console] end_translation_mode message sent successfully",
        //  );
        //}
        await toggleTranslationMode();
      } catch (error) {
        console.error("[Console] Failed to send end_translation_mode:", error);
      }
    };

    // 번역 모드 상태 확인 함수 추가
    const getTranslationModeState = () => {
      return isTranslationModeActive;
    };

    (
      window as typeof window & {
        sendTranslationModeEnd?: () => Promise<void>;
        isTranslationModeActive?: boolean | (() => boolean);
      }
    ).sendTranslationModeEnd = sendTranslationModeEnd;

    (
      window as typeof window & {
        sendTranslationModeEnd?: () => Promise<void>;
        isTranslationModeActive?: boolean | (() => boolean);
      }
    ).isTranslationModeActive = getTranslationModeState;

    // Cleanup
    return () => {
      const win = window as typeof window & {
        sendTranslationModeEnd?: () => Promise<void>;
        isTranslationModeActive?: boolean | (() => boolean);
      };
      delete win.sendTranslationModeEnd;
      delete win.isTranslationModeActive;
    };
  }, [client, isTranslationModeActive, toggleTranslationMode]);

  // Android Native(WebView/HandoffWebViewActivity)에서 스크린샷/취소 이벤트를 전달할 수 있도록 전역 함수 노출
  useEffect(() => {
    const pendingRefKey = "__pendingNativeMessages";
    const windowAnyForQueue = window as unknown as {
      [pendingRefKey]?: Array<{ type: string; data: unknown }>;
    };
    if (!windowAnyForQueue[pendingRefKey]) {
      windowAnyForQueue[pendingRefKey] = [];
    }

    const enqueue = (type: string, data: unknown) => {
      windowAnyForQueue[pendingRefKey]?.push({ type, data });
    };

    const flushIfReady = async () => {
      if (!client || client.state !== "ready") return;
      const q = windowAnyForQueue[pendingRefKey];
      if (!q || q.length === 0) return;
      const items = q.splice(0, q.length);
      for (const item of items) {
        try {
          await client.sendClientMessage(item.type, item.data ?? {});
        } catch (e) {
          console.warn(
            "[Console] Failed to flush queued native message",
            item,
            e,
          );
        }
      }
    };

    const windowAny = window as unknown as {
      onHandoffOpened?: (data: { url: string }) => void;
      sendHandoffScreenshot?: (data: {
        handoff_id: string;
        screenshot_base64: string;
        page_url?: string;
        page_title?: string;
      }) => void;
      cancelHandoffBack?: (data: { handoff_id: string }) => void;
    };

    windowAny.onHandoffOpened = (data) => {
      console.log("[Console] Handoff opened from native WebView", data);
      const payload = { url: data?.url || "" };
      if (client && client.state === "ready") {
        client.sendClientMessage("webview_handoff_opened", payload);
      } else {
        console.warn("[Console] Client not ready, queueing handoff opened");
        enqueue("webview_handoff_opened", payload);
      }
    };

    windowAny.sendHandoffScreenshot = (data) => {
      console.log("[Console] Received handoff screenshot from Android", {
        handoff_id: data?.handoff_id,
        size: data?.screenshot_base64?.length,
      });

      const returnedAt = new Date().toISOString();
      injectMessage({
        role: "user",
        // Distinct user_id so mergeMessages does not fold this into the prior user bubble.
        user_id: "__webview_handoff__",
        parts: [
          {
            text: "외부 사이트에서 돌아왔어요.",
            final: true,
            createdAt: returnedAt,
          },
        ],
      });

      handoffWaitingStartedAtRef.current = returnedAt;
      handoffWaitingStartedMsRef.current = Date.now();
      setHandoffWaiting(true);

      const payload = {
        handoff_id: data.handoff_id,
        screenshot_base64: data.screenshot_base64,
        page_url: data.page_url || "",
        page_title: data.page_title || "",
      };

      if (client && client.state === "ready") {
        client.sendClientMessage("webview_handoff_back_confirmed", payload);
        console.log("[Console] Sent handoff screenshot to server via RTVI");
      } else {
        console.warn("[Console] Client not ready, queueing screenshot");
        enqueue("webview_handoff_back_confirmed", payload);
      }
    };

    windowAny.cancelHandoffBack = (data) => {
      console.log("[Console] Handoff back cancelled from Android", {
        handoff_id: data?.handoff_id,
      });

      const payload = { handoff_id: data.handoff_id };
      if (client && client.state === "ready") {
        client.sendClientMessage("webview_handoff_back_cancelled", payload);
      } else {
        console.warn("[Console] Client not ready, queueing cancel");
        enqueue("webview_handoff_back_cancelled", payload);
      }
    };

    // Best-effort flush on mount and whenever client becomes ready.
    flushIfReady();
    const interval = window.setInterval(flushIfReady, 500);

    return () => {
      delete windowAny.onHandoffOpened;
      delete windowAny.sendHandoffScreenshot;
      delete windowAny.cancelHandoffBack;
      window.clearInterval(interval);
    };
  }, [client, injectMessage]);

  // Android(WebViewActivity/BaseWebViewActivity)에서 호출하는 연결상태/앱이벤트/재연결 훅 제공
  useEffect(() => {
    if (!client) return;

    const windowAny = window as unknown as {
      pipecatConnectionState?: string;
      reconnect?: () => Promise<void> | void;
      connect?: () => Promise<void> | void;
      sendAppStateMessage?: (msg: { t: string; d: { state: string } }) => void;
      callbackAppEvent?: (evt: {
        eventName?: string;
        eventType?: string;
        params?: string;
      }) => void;
    };

    const updateConnState = () => {
      // Android 쪽에서 'connected'/'connecting'만 예외 처리하므로,
      // 여기서는 "ready"를 "connected"로 매핑한다.
      windowAny.pipecatConnectionState =
        client.state === "ready"
          ? "connected"
          : client.state === "connecting"
            ? "connecting"
            : client.state || "unknown";
    };

    const doReconnect = async () => {
      try {
        updateConnState();
        if (client.state === "ready" || client.state === "connecting") return;

        const disconnectResult = client.disconnect();
        await (disconnectResult instanceof Promise
          ? disconnectResult
          : Promise.resolve());

        const connectResult = client.connect({});
        await (connectResult instanceof Promise
          ? connectResult
          : Promise.resolve());

        updateConnState();
      } catch (e) {
        console.warn("[Console] reconnect failed", e);
        updateConnState();
      }
    };

    windowAny.reconnect = doReconnect;
    windowAny.connect = doReconnect;

    // Android BaseWebViewActivity.sendAppStateToServer()가 찾는 커스텀 훅
    windowAny.sendAppStateMessage = (msg) => {
      try {
        if (!msg || msg.t !== "app_state") return;
        const payload = { state: msg?.d?.state ?? "" };
        if (client.state === "ready") {
          client.sendClientMessage("app_state", payload);
        } else {
          const pendingRefKey = "__pendingNativeMessages";
          const q = (
            window as unknown as {
              [pendingRefKey]?: Array<{ type: string; data: unknown }>;
            }
          )[pendingRefKey];
          if (q) q.push({ type: "app_state", data: payload });
        }
      } catch (e) {
        console.warn("[Console] sendAppStateMessage failed", e);
      }
    };

    // Android BaseWebViewActivity.callbackAppEvent()에서 호출되는 전역 함수
    windowAny.callbackAppEvent = (evt) => {
      try {
        const eventName = evt?.eventName ?? "";
        const eventType = evt?.eventType ?? "";
        if (eventName === "Screen" && eventType === "resume") {
          void doReconnect();
        }
      } catch (e) {
        console.warn("[Console] callbackAppEvent handler failed", e);
      }
    };

    // Keep the global state fresh while this template is mounted.
    updateConnState();
    const interval = window.setInterval(updateConnState, 500);

    return () => {
      window.clearInterval(interval);
      delete windowAny.pipecatConnectionState;
      delete windowAny.reconnect;
      delete windowAny.connect;
      delete windowAny.sendAppStateMessage;
      delete windowAny.callbackAppEvent;
    };
  }, [client]);

  // Fetch authenticated user info (users.name) and use as primary display name.
  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      try {
        const res = await fetch("/api/auth/verify", { method: "GET" });
        if (!res.ok) return;
        const data = (await res.json()) as {
          authenticated?: boolean;
          user?: { name?: string };
        };
        const name = data?.user?.name?.trim();
        if (!cancelled && name) setParticipantDisplayName(name);
      } catch {
        // ignore
      }
    };
    void run();
    return () => {
      cancelled = true;
    };
  }, []);

  const noBotArea = noBotAudio && noBotVideo;
  const noConversationPanel = noConversation && noMetrics;
  const noDevices =
    noAudioOutput && noUserAudio && noUserVideo && noScreenControl;
  const noInfoPanel = noStatusInfo && noDevices && noSessionInfo;

  // 번역 Agent 전용 화면(TranslationWebViewActivity가 ?mode=translation 으로 로드)
  // 에서는 홈 에이전트 카드를 숨긴다 — 번역 전용 화면이라 에이전트 런처가 불필요.
  const isTranslationMode =
    typeof window !== "undefined" &&
    new URLSearchParams(window.location.search).get("mode") === "translation";

  // 홈 화면 에이전트 카드. 오버레이가 아니라 대화 스크롤 최상단(headerSlot)에
  // 놓여 메시지가 쌓이면 위로 스크롤된다. 카드를 고르면 해당 에이전트를 켠다.
  const handleHomeCardSelect = (title: string) => {
    if (title === HOME_AGENT_MEETING) {
      toggleMeetingMode();
    } else if (title === HOME_AGENT_TRANSLATE) {
      // 번역 Agent — 별도 네이티브 Activity(TranslationWebViewActivity)에서 구동.
      // 해당 Activity가 ?mode=translation 으로 봇에 새 연결을 맺고, 봇은 번역 전용
      // 파이프라인으로 분기한다. 메인 챗봇 연결은 끊어 메인 파이프라인을 정리한다
      // (Activity back/종료 시 메인 화면 resume → 자동 재연결).
      callLiveTranslationPlugin("openTranslationAgent");
      try {
        const result = client?.disconnect();
        if (result instanceof Promise) result.catch(() => {});
      } catch (e) {
        console.warn("[TranslationAgent] disconnect failed:", e);
      }
    } else if (title === HOME_AGENT_SIMULTANEOUS) {
      toggleTranslationMode("gemini_google_native");
    }
    // PLAN 카드는 아직 미설계(오늘 할일 계획) — 클릭 무동작.
  };

  const homeCards = isTranslationMode ? undefined : (
    <div className="mb-2 flex flex-col gap-5 pt-2">
      <div className="text-center">
        <h2 className="text-xl font-semibold">무엇을 도와드릴까요?</h2>
        <p className="text-muted-foreground mt-1 text-sm">
          에이전트를 선택 또는 아래와 같이 말해보세요.
        </p>
      </div>
      <div className="grid gap-3.5" style={{ gridTemplateColumns: "1fr 1fr" }}>
        {HOME_AGENT_CARDS.map((card) => (
          <button
            key={card.title}
            type="button"
            onClick={() => handleHomeCardSelect(card.title)}
            className="flex cursor-pointer flex-col items-stretch overflow-hidden text-left"
            style={{
              position: "relative",
              padding: "14px 16px",
              borderRadius: 18,
              background: "var(--pc-surface)",
              border: "1px solid var(--pc-border)",
              gap: 8,
              minHeight: 124,
            }}
          >
            {card.isNew && (
              <span
                style={{
                  position: "absolute",
                  top: 15,
                  right: 15,
                  fontFamily: "'Geist Mono', ui-monospace, monospace",
                  fontSize: 9,
                  color: "#fff",
                  background: "var(--pc-accent)",
                  padding: "2px 6px",
                  borderRadius: 5,
                }}
              >
                NEW
              </span>
            )}
            <div className="flex items-end" style={{ gap: 9 }}>
              <span
                className="flex items-center justify-center"
                style={{
                  width: 42,
                  height: 42,
                  borderRadius: 12,
                  fontSize: 20,
                  flexShrink: 0,
                  background:
                    "color-mix(in srgb, var(--pc-accent) 14%, transparent)",
                  border:
                    "1px solid color-mix(in srgb, var(--pc-accent) 30%, transparent)",
                }}
              >
                {card.icon}
              </span>
              <span
                style={{
                  paddingBottom: 3,
                  fontFamily: "'Geist Mono', ui-monospace, monospace",
                  fontSize: 9.5,
                  letterSpacing: "0.1em",
                  textTransform: "uppercase",
                  color: "var(--pc-text-faint)",
                }}
              >
                {card.label}
              </span>
            </div>
            <span
              style={{
                margin: 0,
                fontSize: 16,
                fontWeight: 600,
                color: "var(--pc-text)",
              }}
            >
              {card.title}
            </span>
            <span
              style={{
                margin: 0,
                fontSize: 13,
                lineHeight: 1.55,
                color: "var(--pc-text-dim)",
              }}
            >
              {card.description}
            </span>
          </button>
        ))}
      </div>
      <div className="flex flex-col gap-2.5">
        <div className="flex flex-col items-start gap-2">
          {HOME_EXAMPLE_PROMPTS.map((prompt) => (
            <button
              key={prompt}
              type="button"
              onClick={() => setAskText(prompt)}
              className="cursor-pointer text-left"
              style={{
                maxWidth: "100%",
                padding: "10px 14px",
                background: "var(--pc-surface)",
                border: "1px solid var(--pc-border)",
                borderRadius: 16,
                borderTopLeftRadius: 4,
                fontSize: 13.5,
                lineHeight: 1.5,
                color: "var(--pc-text)",
              }}
            >
              <span aria-hidden style={{ marginRight: 6, opacity: 0.7 }}>
                💬
              </span>
              {prompt}
            </button>
          ))}
        </div>
      </div>
    </div>
  );

  useRTVIClientEvent(RTVIEvent.ParticipantConnected, (p) => {
    if (p.local) setParticipantId(p.id || "");
    if (p.local) {
      const name = (p as unknown as { name?: string }).name;
      if (name && name.trim()) setParticipantDisplayName(name.trim());
    }
  });
  useRTVIClientEvent(RTVIEvent.TrackStarted, (_track, p) => {
    if (p?.id && p?.local) setParticipantId(p.id);
    if (p?.local) {
      const name = (p as unknown as { name?: string }).name;
      if (name && name.trim()) setParticipantDisplayName(name.trim());
    }
  });
  useRTVIClientEvent(RTVIEvent.ServerMessage, (data) => {
    console.log("[Console] ServerMessage received:", data);
    onServerMessage?.(data);

    // Handle meeting mode notifications
    // RTVI ServerMessage can be either:
    // 1. Direct data object: { type: "meeting_mode_started", ... }
    // 2. Wrapped in data field: { data: { type: "meeting_mode_started", ... } }
    let messageData: unknown = data;
    if (data && typeof data === "object" && "data" in data) {
      messageData = (data as { data?: unknown }).data;
    }

    if (
      messageData &&
      typeof messageData === "object" &&
      "type" in messageData
    ) {
      const type = (messageData as { type: string }).type;

      // Handle reconnect_required message (for translation mode end)
      if (type === "reconnect_required") {
        const reconnectData = messageData as {
          type: string;
          message?: string;
          reason?: string;
        };
        console.log("[Console] Reconnect required:", reconnectData);
        console.log("[Console] Client state:", client?.state);
        console.log("[Console] Client available:", !!client);

        // Silently reconnect without changing UI state (Disconnect button stays as is)
        // Use client.connect/disconnect directly instead of handleConnect/handleDisconnect
        // to avoid UI state changes
        // 최소한의 대기 시간으로 빠르게 재연결하여 UI 상태 변경을 최소화
        if (client) {
          console.log(
            "[Console] Fast reconnecting after translation mode end...",
          );
          const disconnectResult = client.disconnect();
          const disconnectPromise =
            disconnectResult instanceof Promise
              ? disconnectResult
              : Promise.resolve();

          disconnectPromise
            .then(() => {
              // 최소한의 대기 시간(100ms)으로 빠르게 재연결
              // 이렇게 하면 transport state 변경이 거의 보이지 않음
              setTimeout(() => {
                try {
                  const connectResult = client.connect({});
                  const connectPromise =
                    connectResult instanceof Promise
                      ? connectResult
                      : Promise.resolve();
                  connectPromise
                    .then(() => {
                      console.log("[Console] Fast reconnect successful");
                    })
                    .catch((error) => {
                      console.error(
                        "[Console] Error during fast reconnect:",
                        error,
                      );
                    });
                } catch (error) {
                  console.error(
                    "[Console] Error calling client.connect():",
                    error,
                  );
                }
              }, 100); // 500ms -> 100ms로 단축하여 더 빠르게 처리
            })
            .catch((error) => {
              console.error("[Console] Error during disconnect:", error);
            });
        } else {
          console.warn("[Console] Client not available for reconnect");
        }
        return;
      }

      if (type === "meeting_mode_started") {
        setIsMeetingModeActive(true);
        const meetingData = messageData as {
          type: string;
          title?: string;
          message?: string;
        };
        setMeetingModeNotification({
          visible: true,
          title: meetingData.title || "회의 모드 시작",
          message: meetingData.message || "회의 모드가 시작되었습니다.",
          variant: "primary",
        });
        // Auto-hide after 5 seconds
        setTimeout(() => {
          setMeetingModeNotification((prev) =>
            prev ? { ...prev, visible: false } : null,
          );
        }, 5000);
      } else if (type === "meeting_mode_ended") {
        setIsMeetingModeActive(false);
        const meetingData = messageData as {
          type: string;
          title?: string;
          message?: string;
          summary?: {
            message_count?: number;
            keywords?: string[];
          };
        };
        let message = meetingData.message || "회의 모드가 종료되었습니다.";
        if (meetingData.summary) {
          const { message_count, keywords } = meetingData.summary;
          if (message_count) {
            message += ` ${message_count}개의 메시지가 기록되었습니다.`;
          }
          if (keywords && keywords.length > 0) {
            message += ` 주요 키워드: ${keywords.slice(0, 5).join(", ")}`;
          }
        }
        setMeetingModeNotification({
          visible: true,
          title: meetingData.title || "회의 모드 종료",
          message,
          variant: "primary",
        });
        // Auto-hide after 7 seconds (longer for summary info)
        setTimeout(() => {
          setMeetingModeNotification((prev) =>
            prev ? { ...prev, visible: false } : null,
          );
        }, 7000);
      } else if (type === "translation_mode_started") {
        setIsTranslationModeActive(true);
        const translationData = messageData as {
          type: string;
          status?: string;
          title?: string;
          message?: string;
          source_language?: string;
          target_language?: string;
          llm_provider?: string;
        };

        // llm_provider 저장
        if (translationData.llm_provider) {
          console.log(
            `[TranslationMode] LLM Provider saved: ${translationData.llm_provider}`,
          );
          // gemini_google_native일 때만 LiveTranslationPlugin 호출
          if (translationData.llm_provider === "gemini_google_native") {
            callLiveTranslationPlugin("startTranslation");
            // 네이티브 동시통역이 OS 마이크를 단독 사용하도록 웹 WebRTC
            // 세션을 끊는다. 안 그러면 WebView가 마이크를 계속 점유해
            // 네이티브 AudioRecord가 캡처를 못 하거나 크래시한다. 네이티브
            // 종료 시 sendTranslationModeEnd → toggleTranslationMode가 재연결.
            try {
              const result = client?.disconnect();
              if (result instanceof Promise) result.catch(() => {});
            } catch (e) {
              console.warn(
                "[TranslationMode] disconnect for native handoff failed:",
                e,
              );
            }
          }
        }
        setMeetingModeNotification({
          visible: true,
          title: translationData.title || "번역 모드 시작",
          message: translationData.message || "번역 모드가 시작되었습니다.",
          variant: "primary",
        });
        // Auto-hide after 5 seconds
        setTimeout(() => {
          setMeetingModeNotification((prev) =>
            prev ? { ...prev, visible: false } : null,
          );
        }, 5000);
      } else if (type === "translation_mode_ended") {
        setIsTranslationModeActive(false);
        const translationData = messageData as {
          type: string;
          title?: string;
          message?: string;
        };
        setMeetingModeNotification({
          visible: true,
          title: translationData.title || "번역 모드 종료",
          message: translationData.message || "번역 모드가 종료되었습니다.",
          variant: "primary",
        });
        // Auto-hide after 5 seconds
        setTimeout(() => {
          setMeetingModeNotification((prev) =>
            prev ? { ...prev, visible: false } : null,
          );
        }, 5000);
      } else if (type === "app_invoke") {
        console.log("[AppHandoff] app_invoke received from server", {
          rawData: data,
          messageData,
        });

        const envelope = messageData as {
          type: string;
          handoff_id?: string;
          payload?: {
            app_id?: string;
            display_name?: string;
            package_name?: string;
            execution_paths?: Array<{
              type: string;
              priority?: number;
              uri?: string;
              action?: string;
              extras?: Record<string, string>;
            }>;
            delay_ms?: number;
            timeout_ms?: number;
          };
        };
        const handoffId = envelope.handoff_id || "";
        const payload = envelope.payload || {};
        const delayMs =
          typeof payload.delay_ms === "number" ? payload.delay_ms : 1000;

        console.log("[AppHandoff] envelope parsed", {
          handoffId,
          app_id: payload.app_id,
          package_name: payload.package_name,
          execution_paths: payload.execution_paths,
          delayMs,
        });

        const windowWithBridgeProbe = window as typeof window & {
          CodeEngineBridge?: { postMessage?: unknown };
        };
        console.log("[AppHandoff] bridge probe at receive time", {
          hasWindow: typeof window !== "undefined",
          hasBridge: !!windowWithBridgeProbe.CodeEngineBridge,
          bridgePostMessageType:
            typeof windowWithBridgeProbe.CodeEngineBridge?.postMessage,
          userAgent:
            typeof navigator !== "undefined" ? navigator.userAgent : "n/a",
        });

        const dispatchToBridge = () => {
          console.log("[AppHandoff] dispatchToBridge fired", {
            handoffId,
            elapsedSinceReceive: `~${delayMs}ms`,
          });

          const windowWithBridge = window as typeof window & {
            CodeEngineBridge?: { postMessage: (message: string) => void };
          };
          const bridge = windowWithBridge.CodeEngineBridge;
          if (!bridge || typeof bridge.postMessage !== "function") {
            console.warn(
              "[AppHandoff] CodeEngineBridge not available; cannot launch app",
              {
                handoffId,
                app_id: payload.app_id,
                hasBridge: !!bridge,
                bridgeKeys: bridge ? Object.keys(bridge) : null,
              },
            );
            if (client && client.state === "ready") {
              client.sendClientMessage("app_invoke_failed", {
                handoff_id: handoffId,
                reason: "bridge_unavailable",
              });
            }
            return;
          }

          const command = {
            serviceName: "AppHandoff",
            action: "launch",
            params: {
              handoff_id: handoffId,
              app_id: payload.app_id || "",
              package_name: payload.package_name || "",
              execution_paths: payload.execution_paths || [],
              timeout_ms: payload.timeout_ms || 0,
            },
            requestId: `AppHandoff.launch.${handoffId || Date.now()}`,
          };

          const commandJson = JSON.stringify(command);
          console.log("[AppHandoff] about to call bridge.postMessage", {
            requestId: command.requestId,
            jsonLength: commandJson.length,
            jsonPreview: commandJson.slice(0, 500),
          });

          try {
            bridge.postMessage(commandJson);
            console.log("[AppHandoff] bridge.postMessage returned (sync)", {
              handoffId,
              app_id: payload.app_id,
              paths: command.params.execution_paths.length,
            });
          } catch (e) {
            console.error("[AppHandoff] bridge.postMessage threw", e);
            if (client && client.state === "ready") {
              client.sendClientMessage("app_invoke_failed", {
                handoff_id: handoffId,
                reason: "bridge_post_failed",
              });
            }
          }
        };

        if (delayMs > 0) {
          console.log("[AppHandoff] scheduling dispatch", { delayMs });
          setTimeout(dispatchToBridge, delayMs);
        } else {
          dispatchToBridge();
        }
      }
    }
  });
  useRTVIClientEvent(RTVIEvent.BotStarted, (data) => {
    const sessionData = data as { sessionId?: string };
    if (sessionData?.sessionId) {
      setSessionId(sessionData.sessionId);
    }
  });
  useRTVIClientEvent(RTVIEvent.BotLlmStarted, () => {
    // Close the "handoff waiting" banner as soon as the bot starts generating an answer.
    if (!handoffWaitingRef.current) return;
    setHandoffWaiting(false);
    handoffWaitingStartedAtRef.current = null;
    handoffWaitingStartedMsRef.current = 0;
  });

  return (
    <>
      {transportType === "smallwebrtc" && (
        <SmallWebRTCCodecSetter
          audioCodec={audioCodec}
          transportType={transportType}
          videoCodec={videoCodec}
        />
      )}
      <div
        className="relative flex flex-col h-full w-full overflow-auto"
        style={
          accent
            ? ({ ["--pc-accent" as string]: accent } as React.CSSProperties)
            : undefined
        }
      >
        <div className="h-min grid grid-cols-2 sm:grid-cols-[150px_1fr_150px] gap-2 items-center justify-center p-2 bg-background sm:relative top-0 w-full z-10">
          {noLogo ? <span className="h-6" /> : (logoComponent ?? <Wordmark />)}
          {titleText ? (
            <strong className="hidden sm:block text-center">{titleText}</strong>
          ) : (
            <span className="hidden sm:block" />
          )}
          <div className="flex items-center justify-end gap-2 sm:gap-3 xl:gap-6">
            <div className="flex items-center gap-1">
              {!noThemeSwitch && (
                <span className="hidden sm:inline-flex">
                  <ThemeModeToggle />
                </span>
              )}
              <Button
                className="hidden sm:flex"
                variant={"ghost"}
                isIcon
                onClick={() => {
                  if (isInfoPanelCollapsed) {
                    infoPanelRef.current?.expand();
                  } else {
                    infoPanelRef.current?.collapse();
                  }
                }}
              >
                {isInfoPanelCollapsed ? (
                  <PanelLeftCloseIcon />
                ) : (
                  <PanelRightCloseIcon />
                )}
              </Button>
            </div>
            {/* Idle 홈 화면 도입으로 명시적 Connect 버튼은 숨긴다. 단
                ConnectButton은 마운트된 채로 두어 자동 연결은 계속 동작한다. */}
            <div className="hidden">
              <ConnectButton
                onConnect={handleConnect}
                onDisconnect={handleDisconnect}
              />
            </div>
          </div>
        </div>

        {error && (
          <Banner
            variant="destructive"
            className="h-min animate-in fade-in duration-300"
          >
            <BannerIcon icon={CircleAlertIcon} />
            <BannerTitle>
              Unable to connect. Please check web console for errors.
            </BannerTitle>
            <BannerClose variant="destructive" />
          </Banner>
        )}
        {meetingModeNotification?.visible && (
          <Banner
            variant={meetingModeNotification.variant}
            className="h-min animate-in fade-in duration-300"
            defaultVisible={true}
            onClose={() =>
              setMeetingModeNotification((prev) =>
                prev ? { ...prev, visible: false } : null,
              )
            }
          >
            <BannerIcon icon={CheckCircleIcon} />
            <BannerTitle>
              <strong>{meetingModeNotification.title}</strong>
              <br />
              {meetingModeNotification.message}
            </BannerTitle>
            <BannerClose variant={meetingModeNotification.variant} />
          </Banner>
        )}
        {handoffWaiting && (
          <Banner
            variant="primary"
            className="h-min animate-in fade-in duration-300"
            defaultVisible={true}
          >
            <BannerIcon icon={InfoIcon} />
            <BannerTitle>
              <span className="block">{handoffWaitingTitleLine1}</span>
              <span className="block">
                {handoffWaitingTitleLine2}
                <span className="inline-block w-[3ch] whitespace-nowrap">
                  {handoffWaitingDots}
                </span>
              </span>
            </BannerTitle>
          </Banner>
        )}
        <div className="hidden sm:block h-full">
          <ResizablePanelGroup direction="vertical" className="h-full">
            <ResizablePanel defaultSize={70} minSize={50}>
              <ResizablePanelGroup direction="horizontal">
                {!noBotArea && (
                  <>
                    <ResizablePanel
                      className="flex flex-col gap-2 p-2 xl:gap-4"
                      defaultSize={collapseMediaPanel ? 8 : 26}
                      maxSize={30}
                      minSize={10}
                      collapsible
                      collapsedSize={8}
                      onCollapse={() => setIsBotAreaCollapsed(true)}
                      onExpand={() => setIsBotAreaCollapsed(false)}
                    >
                      {!noBotAudio && (
                        <BotAudioPanel
                          className={cn({
                            "mb-auto": noBotVideo,
                          })}
                          collapsed={isBotAreaCollapsed}
                        />
                      )}
                      {!noBotVideo && (
                        <BotVideoPanel
                          className={cn({
                            "mt-auto": noBotAudio,
                          })}
                          collapsed={isBotAreaCollapsed}
                        />
                      )}
                    </ResizablePanel>
                    {(!noConversationPanel || !noInfoPanel) && (
                      <ResizableHandle withHandle />
                    )}
                  </>
                )}
                {!noConversationPanel && (
                  <>
                    <ResizablePanel
                      className="h-full p-2"
                      defaultSize={collapseInfoPanel ? 70 : 47}
                      minSize={30}
                    >
                      <ConversationPanel
                        noConversation={noConversation}
                        noMetrics={noMetrics}
                        conversationElementProps={{
                          ...conversationElementProps,
                          assistantLabel: assistantLabelText,
                          clientLabel: userLabelText,
                          clientDisplayName: participantDisplayName,
                          systemLabel: systemLabelText,
                          headerSlot: homeCards,
                        }}
                      />
                    </ResizablePanel>
                    {!noInfoPanel && <ResizableHandle withHandle />}
                  </>
                )}
                {!noInfoPanel && (
                  <ResizablePanel
                    id="info-panel"
                    ref={infoPanelRef}
                    collapsible
                    collapsedSize={4}
                    defaultSize={collapseInfoPanel ? 4 : 27}
                    minSize={15}
                    onCollapse={() => setIsInfoPanelCollapsed(true)}
                    onExpand={() => setIsInfoPanelCollapsed(false)}
                    className="p-2"
                  >
                    {isInfoPanelCollapsed ? (
                      <div className="flex flex-col items-center justify-center gap-4 h-full">
                        {!noStatusInfo && (
                          <Popover>
                            <PopoverTrigger asChild>
                              <Button variant="ghost" isIcon>
                                <ChevronsLeftRightEllipsisIcon size={16} />
                              </Button>
                            </PopoverTrigger>
                            <PopoverContent side="left">
                              <ClientStatus />
                            </PopoverContent>
                          </Popover>
                        )}
                        {!noDevices && (
                          <Popover>
                            <PopoverTrigger asChild>
                              <Button variant="ghost" isIcon>
                                <MicIcon size={16} />
                              </Button>
                            </PopoverTrigger>
                            <PopoverContent
                              className="flex flex-col gap-2"
                              side="left"
                            >
                              {!noUserAudio && <UserAudioControl />}
                              {!noUserVideo && (
                                <UserVideoControl noVideo={!isCamEnabled} />
                              )}
                              {!noScreenControl && <UserScreenControl />}
                              {!noAudioOutput && <UserAudioOutputControl />}
                            </PopoverContent>
                          </Popover>
                        )}
                        {!noSessionInfo && (
                          <Popover>
                            <PopoverTrigger asChild>
                              <Button variant="ghost" isIcon>
                                <InfoIcon size={16} />
                              </Button>
                            </PopoverTrigger>
                            <PopoverContent side="left">
                              <SessionInfo
                                participantId={participantId}
                                sessionId={sessionId}
                              />
                            </PopoverContent>
                          </Popover>
                        )}
                      </div>
                    ) : (
                      <InfoPanel
                        noAudioOutput={noAudioOutput}
                        noSessionInfo={noSessionInfo}
                        noStatusInfo={noStatusInfo}
                        noUserAudio={noUserAudio}
                        noUserVideo={noUserVideo}
                        noScreenControl={noScreenControl}
                        participantId={participantId}
                        sessionId={sessionId}
                      />
                    )}
                  </ResizablePanel>
                )}
              </ResizablePanelGroup>
            </ResizablePanel>
            <ResizableHandle withHandle />
            <ResizablePanel
              collapsible
              collapsedSize={4}
              minSize={7}
              onCollapse={() => setIsEventsPanelCollapsed(true)}
              onExpand={() => setIsEventsPanelCollapsed(false)}
            >
              <EventsPanel collapsed={isEventsPanelCollapsed} />
            </ResizablePanel>
          </ResizablePanelGroup>
        </div>
        <div className="flex flex-col gap-0 h-full sm:hidden overflow-hidden relative">
          <div className="flex flex-col overflow-hidden flex-1">
            <ConversationPanel
              noConversation={noConversation}
              noMetrics={noMetrics}
              conversationElementProps={{
                ...conversationElementProps,
                assistantLabel: assistantLabelText,
                clientLabel: userLabelText,
                clientDisplayName: participantDisplayName,
                systemLabel: systemLabelText,
                headerSlot: homeCards,
              }}
            />
          </div>

          {/* 마이그레이션 "03 Conversation"의 AskBar — 하단 고정.
              텍스트=입력→usePipecatTextInput, 음성=마이크 토글. */}
          <PipecatClientMicToggle>
            {({ isMicEnabled, onClick }) => (
              <AskBar
                className="shrink-0"
                state="idle"
                micActive={
                  startMicMuted && !micUserActivated ? false : isMicEnabled
                }
                noAttach
                noWave
                value={askText}
                onValueChange={setAskText}
                onSubmit={handleAskBarSubmit}
                onMicTap={() => {
                  setMicUserActivated(true);
                  onClick();
                }}
                placeholder="메시지를 입력하세요"
                micLabel="마이크 켜기"
                stopLabel="마이크 끄기"
              />
            )}
          </PipecatClientMicToggle>

          <PipecatClientCamToggle>
            {({ isCamEnabled, onClick }) => (
              <>
                {!showPipVideo && (
                  <div className="fixed top-2 right-2 z-[60] flex items-center gap-1">
                    <ThemeModeToggle />
                    <Button
                      className="shadow-lg"
                      variant={isCamEnabled ? "primary" : "secondary"}
                      onClick={() => {
                        if (!isCamEnabled) {
                          // 처음 켤 때 전면 카메라를 명시적으로 선택
                          const { front } =
                            getRepresentativeCams(availableCams);
                          if (front) {
                            internalCamIndexRef.current = 0; // 0번을 전면으로 고정
                            updateCam(front.deviceId);
                          }
                          onClick();
                        }
                        setShowPipVideo(true);
                      }}
                    >
                      <VideoIcon className="mr-2 h-4 w-4" />
                      Video
                    </Button>
                  </div>
                )}
              </>
            )}
          </PipecatClientCamToggle>

          {showPipVideo && (
            <div
              className="absolute w-32 aspect-[9/16] bg-black rounded-lg overflow-hidden shadow-2xl z-40 border-2 border-primary touch-none"
              style={{
                top: `${pipPosition.y}px`,
                right: `${pipPosition.x}px`,
              }}
              onPointerDown={handlePointerDown}
              onPointerMove={handlePointerMove}
              onPointerUp={handlePointerUp}
              onPointerCancel={handlePointerUp}
            >
              <PipecatClientVideo
                className="w-full h-full object-cover"
                participant="local"
              />
              {/* Close Button (X) */}
              <Button
                variant="ghost"
                size="sm"
                isIcon
                className="absolute top-1 right-1 h-6 w-6 bg-black/50 hover:bg-black/70 text-white rounded-full p-0"
                onClick={(e) => {
                  e.stopPropagation();
                  setShowPipVideo(false);
                }}
              >
                <span className="text-xs font-bold">✕</span>
              </Button>
              {/* Camera Switch Button */}
              <Button
                variant="ghost"
                size="sm"
                isIcon
                className="absolute bottom-2 left-2 h-8 w-8 bg-black/50 hover:bg-black/70 text-white rounded-full p-0 flex items-center justify-center"
                onClick={(e) => {
                  e.stopPropagation();
                  switchCamera();
                }}
              >
                <RefreshCwIcon size={16} className="text-white" />
              </Button>
            </div>
          )}
        </div>
      </div>
    </>
  );
};
