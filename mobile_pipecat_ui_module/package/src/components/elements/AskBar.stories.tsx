import type { Story, StoryDefault } from "@ladle/react";
import { useState } from "react";
import { AskBar, type AskBarState } from "./AskBar";

export default {
  title: "Pipecat Voice UI / Controls",
  argTypes: {
    state: {
      options: ["idle", "listening", "disabled"],
      control: { type: "select" },
      defaultValue: "idle",
    },
  },
} satisfies StoryDefault;

const Frame: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div
    className="dark"
    style={{
      minHeight: "100vh",
      padding: 32,
      background: "var(--pc-canvas)",
      color: "var(--pc-text)",
    }}
  >
    <div
      style={{
        maxWidth: 392,
        background: "var(--pc-bg-base)",
        borderRadius: 16,
        padding: 8,
      }}
    >
      {children}
    </div>
  </div>
);

export const AskBarStates: Story = () => (
  <Frame>
    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      <AskBar state="idle" onMicTap={() => {}} />
      <AskBar state="listening" onMicTap={() => {}} />
      <AskBar state="disabled" onMicTap={() => {}} />
      <AskBar
        state="idle"
        placeholder="Reply or ask follow-up"
        onMicTap={() => {}}
      />
    </div>
  </Frame>
);
AskBarStates.storyName = "AskBar · 3 states + reply variant";

export const AskBarControlled: Story<{ state: AskBarState }> = ({ state }) => (
  <Frame>
    <AskBar state={state} onMicTap={() => {}} />
  </Frame>
);
AskBarControlled.storyName = "AskBar · controlled state";

export const AskBarTextInput: Story = () => {
  const [value, setValue] = useState("");
  const [submitted, setSubmitted] = useState<string[]>([]);
  return (
    <Frame>
      <AskBar
        state="idle"
        value={value}
        onValueChange={setValue}
        onSubmit={(v) => {
          if (!v.trim()) return;
          setSubmitted((prev) => [...prev, v]);
          setValue("");
        }}
        onMicTap={() => {}}
        autoFocusInput
      />
      <div
        style={{
          marginTop: 16,
          padding: 12,
          background: "var(--pc-bg-base)",
          borderRadius: 12,
          fontFamily: "'Geist Mono', ui-monospace, monospace",
          fontSize: 11,
          color: "var(--pc-text-dim)",
          lineHeight: 1.6,
        }}
      >
        <div>value: "{value}"</div>
        <div>
          submitted: {submitted.length === 0 ? "—" : submitted.join(" · ")}
        </div>
        <div style={{ marginTop: 4, color: "var(--pc-text-faint)" }}>
          Type and press Enter
        </div>
      </div>
    </Frame>
  );
};
AskBarTextInput.storyName = "AskBar · text input (controlled)";
