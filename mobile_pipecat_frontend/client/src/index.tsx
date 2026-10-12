import {
  ConsoleTemplate,
  FullScreenContainer,
  ThemeProvider,
  Wordmark,
} from "@pipecat-ai/voice-ui-kit";
import { StrictMode, useEffect } from "react";
import { createRoot } from "react-dom/client";
import { useAuthStore } from "./auth/store";
import { LoginPage } from "./auth/LoginPage";

// voice-ui-kit 스타일은 패키지 exports 맵(./styles.css -> ./dist/voice-ui-kit.css)을
// 통해 제공된다. CSS의 @import는 Vite에서 이 exports 리매핑을 따라가지 못해
// ENOENT가 나므로, exports를 완전히 지원하는 JS import로 불러온다.
import "@pipecat-ai/voice-ui-kit/styles.css";

//@ts-ignore - fontsource-variable/geist is not typed
import "@fontsource-variable/geist";
//@ts-ignore - fontsource-variable/geist is not typed
import "@fontsource-variable/geist-mono";

// 모듈 레벨 상수로 고정 — 매 렌더마다 새 객체를 넘기면 PipecatAppBase가
// client를 재생성(disconnect→reconnect)하며 불안정해진다.
const CONNECT_PARAMS = { connectionUrl: "/api/offer" } as const;

// 마이크 트랙은 "생성"하되(enableMic: true → SmallWebRTC에 오디오 m-line 유지 →
// 미디어가 흘러 유휴 끊김/재연결 루프 방지), 시작 음소거는 ConsoleTemplate의
// startMicMuted가 연결 후 React enableMic(false)로 처리한다(버튼 상태까지 동기화).
// enableMic:false로 시작하면 startAudioOff=true가 돼 트랙 자체가 안 생기고,
// mic+cam 둘 다 트랙이 없으면 SmallWebRTC가 60초 유휴로 연결을 끊는다.
const CLIENT_OPTIONS = { enableCam: false, enableMic: true } as const;

// 좌상단 브랜드 로고 — "pipecat" 대신 "DuriOn". 모듈 상수로 고정해
// memo된 ConsoleTemplate이 매 렌더마다 재생성되지 않게 한다.
const DURION_LOGO = <Wordmark text="DuriOn" />;

// 연결 시점 감지를 위한 컴포넌트
function ConnectionDebugger() {
  useEffect(() => {
    console.debug("[SmallWebRTC] Application initialized and ready to connect");
    
    const originalFetch = window.fetch;
    window.fetch = async function(...args) {
      const url = args[0];
      const options: RequestInit = { ...(args[1] || {}) };

      if (url && typeof url === 'string' && url.startsWith('/api/')) {
        options.credentials = options.credentials || 'include';
        // Inject Bearer token for all API requests so the server middleware
        // can authenticate even when cookies are unavailable (Android WebView).
        const token = localStorage.getItem('auth_token');
        if (token) {
          options.headers = {
            ...(options.headers as Record<string, string> || {}),
            Authorization: `Bearer ${token}`,
          };
        }
      }

      if (url && typeof url === 'string' && url.includes('/api/offer') &&
          (options.method === 'POST' || !options.method)) {
        console.debug("[SmallWebRTC] Connection attempt started:", {
          url,
          timestamp: new Date().toISOString(),
        });

        // 번역 Agent: TranslationWebViewActivity가 ?mode=translation 으로 이 페이지를
        // 로드한다. offer body의 request_data.mode 에 플래그를 실어 보내면 봇(run_bot)이
        // 메인 대화 대신 번역 전용 파이프라인으로 분기한다.
        const isTranslationMode =
          new URLSearchParams(window.location.search).get('mode') === 'translation';
        if (isTranslationMode && typeof options.body === 'string') {
          try {
            const parsedBody = JSON.parse(options.body);
            parsedBody.request_data = {
              ...(parsedBody.request_data || {}),
              mode: 'translation',
            };
            options.body = JSON.stringify(parsedBody);
            console.debug("[SmallWebRTC] translation mode flag injected into offer body");
          } catch (e) {
            console.warn("[SmallWebRTC] failed to inject translation mode flag", e);
          }
        }
      }

      return originalFetch.apply(this, [args[0], options]);
    };
    
    return () => {
      window.fetch = originalFetch;
    };
  }, []);

  return null;
}

// Android에서 전송하는 앱 상태 변경 이벤트를 리스닝하는 전역 리스너
function setupAppStateListener() {
  const handleAppStateChange = async (event: Event) => {
    const customEvent = event as CustomEvent;
    const message = customEvent.detail;
    console.log("[AppStateListener] Received app state change:", message);

    try {
      const client = (window as any).__pipecatClient;
      if (!client) {
        console.warn("[AppStateListener] PipecatClient not available yet");
        return;
      }

      // PipecatClient의 transport를 통해 메시지 전송
      // SmallWebRTC transport는 RTVI 프로토콜을 사용
      const transport = client.transport;
      if (!transport) {
        console.warn("[AppStateListener] Transport not available");
        return;
      }

      // RTVI 프로토콜 형식으로 메시지 구성
      const messageId = `app_state_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
      const rtviMessage = {
        label: "rtvi-ai",  // RTVI 메시지 식별자
        type: "client-message",  // RTVI client-message 타입
        id: messageId,
        data: {
          t: "app_state",  // 실제 메시지 타입
          d: {
            state: message.state, // "background" or "foreground"
            // user_id는 필요시 추가 가능
          }
        }
      };

      console.log("[AppStateListener] Sending RTVI message:", rtviMessage);

      // transport.sendMessage를 통해 RTVI 프로토콜로 전송
      if (typeof transport.sendMessage === 'function') {
        await transport.sendMessage(rtviMessage);
        console.log("[AppStateListener] App state message sent:", rtviMessage);
      } else {
        console.warn("[AppStateListener] transport.sendMessage is not available");
      }
    } catch (error) {
      console.error("[AppStateListener] Failed to send app state message:", error);
    }
  };

  // CustomEvent 리스너 등록
  window.addEventListener('appStateChange', handleAppStateChange);

  // 전역 함수로 등록 (Android에서 직접 호출 가능)
  (window as any).sendAppStateMessage = (message: any) => {
    const event = new CustomEvent('appStateChange', { detail: message });
    window.dispatchEvent(event);
  };

  console.log("[AppStateListener] App state listener initialized");
}

// Android(BaseWebViewActivity)에서 사용하는 전역 훅 제공
function setupAndroidBridgeHooks() {
  const w = window as any;

  const getClient = () => w.__pipecatClient;

  const updateConnectionState = () => {
    const client = getClient();
    // Android 쪽에서 pipecatConnectionState 값을 읽어 connected/connecting 여부를 체크함
    // ConsoleTemplate 내부 client.state가 "ready"면 connected 로 매핑
    const state = client?.state;
    w.pipecatConnectionState =
      state === "ready" ? "connected" : state === "connecting" ? "connecting" : state || "unknown";
  };

  const tryDisconnect = async (client: any) => {
    const fn =
      (typeof client?.disconnect === "function" && client.disconnect.bind(client)) ||
      (typeof client?.transport?.disconnect === "function" && client.transport.disconnect.bind(client.transport));
    if (!fn) return;
    const r = fn();
    await (r instanceof Promise ? r : Promise.resolve());
  };

  const tryConnect = async (client: any) => {
    const fn =
      (typeof client?.connect === "function" && client.connect.bind(client)) ||
      (typeof client?.transport?.connect === "function" && client.transport.connect.bind(client.transport));
    if (!fn) return;
    // Do not pass an empty object here.
    // For SmallWebRTC, connect() without args uses the configured connectParams
    // (e.g. connectionUrl from ConsoleTemplate). Passing {} can wipe required details.
    const r = fn();
    await (r instanceof Promise ? r : Promise.resolve());
  };

  const reconnect = async () => {
    const client = getClient();
    updateConnectionState();
    console.log("[AndroidBridgeHooks] reconnect() called. current state=", w.pipecatConnectionState);
    if (!client) return;
    if (client.state === "ready" || client.state === "connecting") return;
    try {
      await tryDisconnect(client);
      await tryConnect(client);
    } catch (e) {
      console.warn("[AndroidBridgeHooks] reconnect failed", e);
    } finally {
      updateConnectionState();
      console.log("[AndroidBridgeHooks] reconnect() finished. state=", w.pipecatConnectionState);
    }
  };

  // BaseWebViewActivity.checkAndReconnectIfNeeded()에서 찾는 함수들
  w.reconnect = reconnect;
  w.connect = reconnect;

  // BaseWebViewActivity.callbackAppEvent()에서 호출되는 전역 함수
  w.callbackAppEvent = (evt: any) => {
    console.log("[AndroidBridgeHooks] callbackAppEvent received:", evt);
    const eventName = evt?.eventName ?? "";
    const eventType = evt?.eventType ?? "";
    if (eventName === "Screen" && eventType === "resume") {
      void reconnect();
    }
  };

  updateConnectionState();
  window.setInterval(updateConnectionState, 500);
  console.log("[AndroidBridgeHooks] initialized");
}

// 앱 상태 리스너 초기화
setupAppStateListener();
setupAndroidBridgeHooks();

// Main App Component with Authentication
function App() {
  const { isAuthenticated, isLoading, checkAuth } = useAuthStore();

  useEffect(() => {
    // Handle OAuth callback token BEFORE calling checkAuth.
    // The server passes ?success=true&token=JWT to avoid WebView cookie issues.
    // We must store the token synchronously so checkAuth() can read it.
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get("token");
    const error = urlParams.get("error");

    if (token) {
      useAuthStore.getState().setToken(token);
      // Remove token from URL immediately so it doesn't stay in browser history
      window.history.replaceState({}, "", window.location.pathname);
    } else if (error) {
      console.error("OAuth error:", decodeURIComponent(error));
      window.history.replaceState({}, "", window.location.pathname);
    } else if (urlParams.has("success")) {
      window.history.replaceState({}, "", window.location.pathname);
    }

    checkAuth();
  }, [checkAuth]);

  if (isLoading) {
    return (
      <div style={{ 
        display: "flex", 
        justifyContent: "center", 
        alignItems: "center", 
        minHeight: "100vh" 
      }}>
        <div>Loading...</div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <LoginPage />;
  }

  return (
    <ThemeProvider>
      <FullScreenContainer>
        <ConnectionDebugger />
        <ConsoleTemplate
          connectOnMount
          startMicMuted
          logoComponent={DURION_LOGO}
          connectParams={CONNECT_PARAMS}
          clientOptions={CLIENT_OPTIONS}
          transportType="smallwebrtc"
        />
      </FullScreenContainer>
    </ThemeProvider>
  );
}

createRoot(document.getElementById("root")!).render(
  // @ts-ignore
  <StrictMode>
    <App />
  </StrictMode>
);
