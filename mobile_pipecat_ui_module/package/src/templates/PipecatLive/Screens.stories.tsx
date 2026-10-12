import { PipecatAppBase } from "@/components/PipecatAppBase";
import type { Story, StoryDefault } from "@ladle/react";
import { useState } from "react";
import { PhoneShell } from "./PhoneShell";
import { ScreenBubble } from "./ScreenBubble";
import { ScreenConversation } from "./ScreenConversation";
import { ScreenIdle } from "./ScreenIdle";
import { ScreenListening } from "./ScreenListening";
import { ScreenOverlay } from "./ScreenOverlay";

type ScreenId = "idle" | "listening" | "conversation" | "overlay" | "bubble";

const TABS: Array<{ id: ScreenId; label: string }> = [
  { id: "idle", label: "01 · Idle" },
  { id: "listening", label: "02 · Listening" },
  { id: "conversation", label: "03 · Conversation" },
  { id: "overlay", label: "04 · Overlay" },
  { id: "bubble", label: "05 · Bubble" },
];

export default {
  title: "Pipecat Voice UI / Screens",
  argTypes: {
    screen: {
      options: TABS.map((t) => t.id),
      control: { type: "select" },
      defaultValue: "idle",
    },
    dark: {
      control: { type: "boolean" },
      defaultValue: true,
    },
  },
} satisfies StoryDefault;

const Canvas: React.FC<{
  children: React.ReactNode;
  dark: boolean;
  active: ScreenId;
  onPick: (id: ScreenId) => void;
}> = ({ children, dark, active, onPick }) => (
  <div
    className={dark ? "dark" : ""}
    style={{
      minHeight: "100vh",
      background: "var(--pc-canvas)",
      color: "var(--pc-text)",
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      gap: 20,
      padding: 40,
    }}
  >
    <div
      role="tablist"
      aria-label="Pipecat screens"
      style={{
        display: "flex",
        gap: 6,
        padding: 6,
        borderRadius: 999,
        background: "var(--pc-chip-bg)",
        border: "1px solid var(--pc-chip-border)",
      }}
    >
      {TABS.map((t) => {
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
    {children}
  </div>
);

const renderScreen = (id: ScreenId) => {
  switch (id) {
    case "idle":
      return <ScreenIdle onMicTap={() => {}} />;
    case "listening":
      return <ScreenListening onStopTap={() => {}} />;
    case "conversation":
      return <ScreenConversation />;
    case "overlay":
      return <ScreenOverlay onStopTap={() => {}} />;
    case "bubble":
      return <ScreenBubble onBubbleTap={() => {}} />;
  }
};

export const AllScreens: Story<{ screen: ScreenId; dark: boolean }> = ({
  screen,
  dark,
}) => {
  const [active, setActive] = useState<ScreenId>(screen);
  return (
    <PipecatAppBase transportType="smallwebrtc" noThemeProvider>
      {() => (
        <Canvas dark={dark} active={active} onPick={setActive}>
          <PhoneShell>{renderScreen(active)}</PhoneShell>
        </Canvas>
      )}
    </PipecatAppBase>
  );
};
AllScreens.storyName = "All five (canvas)";

const FocusStory =
  (id: ScreenId): Story<{ dark: boolean }> =>
  ({ dark }) => (
    <PipecatAppBase transportType="smallwebrtc" noThemeProvider>
      {() => (
        <div
          className={dark ? "dark" : ""}
          style={{
            minHeight: "100vh",
            background: "var(--pc-canvas)",
            color: "var(--pc-text)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: 40,
          }}
        >
          <PhoneShell>{renderScreen(id)}</PhoneShell>
        </div>
      )}
    </PipecatAppBase>
  );

export const IdleFocus = FocusStory("idle");
IdleFocus.storyName = "Focus · Idle";

export const ListeningFocus = FocusStory("listening");
ListeningFocus.storyName = "Focus · Listening";

export const ConversationFocus = FocusStory("conversation");
ConversationFocus.storyName = "Focus · Conversation";

export const OverlayFocus = FocusStory("overlay");
OverlayFocus.storyName = "Focus · Overlay";

export const BubbleFocus = FocusStory("bubble");
BubbleFocus.storyName = "Focus · Bubble";
