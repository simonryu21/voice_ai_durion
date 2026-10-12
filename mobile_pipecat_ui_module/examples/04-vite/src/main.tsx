import { StrictMode, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";

import type {
  PipecatBaseChildProps,
  VoiceVizStyle,
} from "@pipecat-ai/voice-ui-kit";
import {
  ErrorCard,
  PhoneShell,
  PipecatAppBase,
  ScreenBubble,
  ScreenConversation,
  ScreenIdle,
  ScreenListening,
  ScreenOverlay,
  SpinLoader,
  type AskBarState,
  type ConversationTurn,
  usePipecatConnectionState,
  usePipecatConversation,
  usePipecatTextInput,
} from "@pipecat-ai/voice-ui-kit";

import "./index.css";

type ScreenId = "idle" | "listening" | "conversation" | "overlay" | "bubble";

const SCREEN_TABS: Array<{ id: ScreenId; label: string }> = [
  { id: "idle", label: "01 · Idle" },
  { id: "listening", label: "02 · Listening" },
  { id: "conversation", label: "03 · Conversation" },
  { id: "overlay", label: "04 · Overlay bar" },
  { id: "bubble", label: "05 · Bubble" },
];

const VIZ_OPTIONS: VoiceVizStyle[] = ["bars", "orb", "particles"];

const ACCENT_OPTIONS = [
  { value: "#C73E1D", label: "deep" },
  { value: "#D94A2B", label: "alt 1" },
  { value: "#E55934", label: "alt 2" },
  { value: "#B33A1A", label: "alt 3" },
];

const Tabs: React.FC<{
  active: ScreenId;
  onPick: (id: ScreenId) => void;
}> = ({ active, onPick }) => (
  <div
    role="tablist"
    aria-label="Screens"
    style={{
      display: "flex",
      gap: 6,
      padding: 6,
      borderRadius: 999,
      background: "var(--pc-chip-bg)",
      border: "1px solid var(--pc-chip-border)",
    }}
  >
    {SCREEN_TABS.map((t) => {
      const isActive = active === t.id;
      return (
        <button
          key={t.id}
          type="button"
          role="tab"
          aria-selected={isActive}
          onClick={() => onPick(t.id)}
          style={{
            padding: "8px 14px",
            borderRadius: 999,
            border: "none",
            cursor: "pointer",
            fontFamily: "'Geist Mono', ui-monospace, monospace",
            fontSize: 11,
            letterSpacing: "0.04em",
            background: isActive ? "var(--pc-accent)" : "transparent",
            color: isActive ? "#fff" : "var(--pc-text-dim)",
            transition: "background var(--pc-motion-fast)",
          }}
        >
          {t.label}
        </button>
      );
    })}
  </div>
);

const TweakRow: React.FC<{ label: string; children: React.ReactNode }> = ({
  label,
  children,
}) => (
  <div
    className="flex items-center justify-between"
    style={{ gap: 12, padding: "6px 4px" }}
  >
    <span
      style={{
        fontFamily: "'Geist Mono', ui-monospace, monospace",
        fontSize: 10.5,
        letterSpacing: "0.04em",
        color: "var(--pc-text-faint)",
        textTransform: "uppercase",
      }}
    >
      {label}
    </span>
    <div className="flex" style={{ gap: 4 }}>
      {children}
    </div>
  </div>
);

const TweakChoice: React.FC<{
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
  swatch?: string;
}> = ({ active, onClick, children, swatch }) => (
  <button
    type="button"
    onClick={onClick}
    aria-pressed={active}
    style={{
      display: "inline-flex",
      alignItems: "center",
      gap: 6,
      padding: "6px 10px",
      borderRadius: 999,
      border: `1px solid ${active ? "var(--pc-accent)" : "var(--pc-chip-border)"}`,
      background: active ? "color-mix(in srgb, var(--pc-accent) 12%, transparent)" : "transparent",
      color: active ? "var(--pc-text)" : "var(--pc-text-dim)",
      fontFamily: "'Geist Mono', ui-monospace, monospace",
      fontSize: 10.5,
      letterSpacing: "0.04em",
      cursor: "pointer",
    }}
  >
    {swatch && (
      <span
        aria-hidden
        style={{
          width: 10,
          height: 10,
          borderRadius: "50%",
          background: swatch,
          border: "1px solid rgba(0,0,0,0.15)",
        }}
      />
    )}
    {children}
  </button>
);

interface TweaksState {
  screen: ScreenId;
  dark: boolean;
  accent: string;
  viz: VoiceVizStyle;
}

const TweaksPanel: React.FC<{
  tweaks: TweaksState;
  onChange: <K extends keyof TweaksState>(key: K, value: TweaksState[K]) => void;
}> = ({ tweaks, onChange }) => (
  <aside
    aria-label="Tweaks"
    style={{
      position: "fixed",
      top: 20,
      right: 20,
      width: 260,
      padding: 16,
      borderRadius: 16,
      background: "var(--pc-surface)",
      border: "1px solid var(--pc-border)",
      color: "var(--pc-text)",
      boxShadow: "0 8px 24px rgba(0,0,0,0.18)",
      display: "flex",
      flexDirection: "column",
      gap: 6,
    }}
  >
    <div
      style={{
        fontFamily: "'Geist Mono', ui-monospace, monospace",
        fontSize: 11,
        letterSpacing: "0.08em",
        textTransform: "uppercase",
        color: "var(--pc-text-faint)",
        paddingBottom: 8,
        borderBottom: "1px solid var(--pc-border)",
      }}
    >
      Tweaks
    </div>
    <TweakRow label="Theme">
      <TweakChoice active={!tweaks.dark} onClick={() => onChange("dark", false)}>
        light
      </TweakChoice>
      <TweakChoice active={tweaks.dark} onClick={() => onChange("dark", true)}>
        dark
      </TweakChoice>
    </TweakRow>
    <TweakRow label="Accent">
      {ACCENT_OPTIONS.map((a) => (
        <TweakChoice
          key={a.value}
          active={tweaks.accent === a.value}
          onClick={() => onChange("accent", a.value)}
          swatch={a.value}
        >
          {a.label}
        </TweakChoice>
      ))}
    </TweakRow>
    <TweakRow label="Viz">
      {VIZ_OPTIONS.map((v) => (
        <TweakChoice
          key={v}
          active={tweaks.viz === v}
          onClick={() => onChange("viz", v)}
        >
          {v}
        </TweakChoice>
      ))}
    </TweakRow>
  </aside>
);

export const App = ({
  handleConnect,
  handleDisconnect,
}: PipecatBaseChildProps) => {
  const { state } = usePipecatConnectionState();
  const { sendTextMessage } = usePipecatTextInput();
  const { messages } = usePipecatConversation();

  // When the session has produced messages, render them as live turns;
  // otherwise fall back to ScreenConversation's built-in demo turns.
  const liveTurns: ConversationTurn[] | undefined = messages.length
    ? messages
        .filter((m) => m.role === "user" || m.role === "assistant")
        .map((m) => ({
          role: m.role as "user" | "assistant",
          text: m.parts
            .map((p) => (typeof p.text === "string" ? p.text : ""))
            .join(""),
          meta: m.role === "assistant" ? "pipecat" : "you",
        }))
    : undefined;

  const askBarState: AskBarState =
    state === "connected"
      ? "listening"
      : state === "connecting"
        ? "disabled"
        : "idle";

  const [tweaks, setTweaks] = useState<TweaksState>({
    screen: "idle",
    dark: true,
    accent: "#C73E1D",
    viz: "bars",
  });

  // Drive the .dark class on <html> + accent override via inline custom prop
  useEffect(() => {
    document.documentElement.classList.toggle("dark", tweaks.dark);
    document.documentElement.style.setProperty("--pc-accent", tweaks.accent);
    return () => {
      document.documentElement.style.removeProperty("--pc-accent");
    };
  }, [tweaks.dark, tweaks.accent]);

  const setTweak = <K extends keyof TweaksState>(
    key: K,
    value: TweaksState[K],
  ) => setTweaks((prev) => ({ ...prev, [key]: value }));

  const onMicTap = () => {
    if (state === "disconnected") void handleConnect?.();
    else void handleDisconnect?.();
  };

  const screenNode = (() => {
    switch (tweaks.screen) {
      case "idle":
        return <ScreenIdle onMicTap={onMicTap} askBarState={askBarState} />;
      case "listening":
        return <ScreenListening vizStyle={tweaks.viz} onStopTap={onMicTap} />;
      case "conversation":
        return (
          <ScreenConversation
            turns={liveTurns}
            onSendMessage={sendTextMessage}
          />
        );
      case "overlay":
        return <ScreenOverlay onStopTap={onMicTap} />;
      case "bubble":
        return <ScreenBubble onBubbleTap={onMicTap} />;
    }
  })();

  return (
    <div
      style={{
        minHeight: "100vh",
        background: "var(--pc-canvas)",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: 20,
        padding: 40,
      }}
    >
      <Tabs
        active={tweaks.screen}
        onPick={(id) => setTweak("screen", id)}
      />
      <PhoneShell>{screenNode}</PhoneShell>
      <TweaksPanel tweaks={tweaks} onChange={setTweak} />
    </div>
  );
};

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <PipecatAppBase
      connectParams={{
        webrtcUrl: "/api/offer",
      }}
      initDevicesOnMount
      transportType="smallwebrtc"
      noThemeProvider
    >
      {({
        client,
        handleConnect,
        handleDisconnect,
        error,
      }: PipecatBaseChildProps) =>
        !client ? (
          <SpinLoader />
        ) : error ? (
          <ErrorCard>{error}</ErrorCard>
        ) : (
          <App
            client={client}
            handleConnect={handleConnect}
            handleDisconnect={handleDisconnect}
          />
        )
      }
    </PipecatAppBase>
  </StrictMode>,
);
