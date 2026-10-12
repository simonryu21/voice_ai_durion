import type { Story, StoryDefault } from "@ladle/react";
import { AppBar } from "./AppBar";
import { StatusPill, type StatusPillState } from "./StatusPill";

export default {
  title: "Pipecat Voice UI / Status",
  argTypes: {
    state: {
      options: ["idle", "listening", "thinking", "speaking"],
      control: { type: "select" },
      defaultValue: "listening",
    },
    latency: {
      control: { type: "number" },
      defaultValue: 218,
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
    {children}
  </div>
);

export const StatusPillStates: Story<{
  state: StatusPillState;
  latency: number;
}> = ({ state, latency }) => (
  <Frame>
    <div className="flex flex-col gap-4 items-start">
      <StatusPill state="idle" latency={142} />
      <StatusPill state="listening" latency={218} />
      <StatusPill state="thinking" latency={302} />
      <StatusPill state="speaking" latency={195} />
      <div
        style={{ marginTop: 24, fontSize: 11, color: "var(--pc-text-faint)" }}
      >
        Live (from controls): state = {state}, latency = {latency}ms
      </div>
      <StatusPill state={state} latency={latency} />
    </div>
  </Frame>
);
StatusPillStates.storyName = "StatusPill · 4 states";

export const AppBarAllStates: Story<{
  state: StatusPillState;
  latency: number;
}> = ({ state, latency }) => (
  <Frame>
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: 16,
        maxWidth: 392,
      }}
    >
      <div
        style={{
          background: "var(--pc-bg-base)",
          borderRadius: 16,
          overflow: "hidden",
        }}
      >
        <AppBar state="idle" latency={142} autoLatency={false} />
      </div>
      <div
        style={{
          background: "var(--pc-bg-base)",
          borderRadius: 16,
          overflow: "hidden",
        }}
      >
        <AppBar state="listening" latency={218} autoLatency={false} />
      </div>
      <div
        style={{
          background: "var(--pc-bg-base)",
          borderRadius: 16,
          overflow: "hidden",
        }}
      >
        <AppBar state="thinking" latency={302} autoLatency={false} />
      </div>
      <div
        style={{
          background: "var(--pc-bg-base)",
          borderRadius: 16,
          overflow: "hidden",
        }}
      >
        <AppBar state={state} latency={latency} autoLatency={false} />
      </div>
    </div>
  </Frame>
);
AppBarAllStates.storyName = "AppBar · 4 states + control";
