import type { Story, StoryDefault } from "@ladle/react";
import { FloatingBubble } from "./FloatingBubble";
import { OverlayBar } from "./OverlayBar";

export default {
  title: "Pipecat Voice UI / Floating",
  argTypes: {
    active: {
      control: { type: "boolean" },
      defaultValue: true,
    },
    label: {
      control: { type: "text" },
      defaultValue: "listening",
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
      display: "flex",
      flexDirection: "column",
      gap: 24,
      alignItems: "center",
    }}
  >
    {children}
  </div>
);

export const OverlayBarStory: Story<{ active: boolean; label: string }> = ({
  active,
  label,
}) => (
  <Frame>
    <div style={{ width: 360 }}>
      <OverlayBar
        active={active}
        label={label}
        onStopTap={() => alert("stop tapped")}
      />
    </div>
  </Frame>
);
OverlayBarStory.storyName = "OverlayBar";

export const FloatingBubbleStory: Story = () => (
  <Frame>
    <div
      style={{
        position: "relative",
        width: 200,
        height: 200,
        background: "var(--pc-bg-base)",
        borderRadius: 16,
      }}
    >
      <FloatingBubble
        onClick={() => alert("bubble tapped")}
        style={{ position: "absolute", left: 16, bottom: 16 }}
      />
    </div>
    <FloatingBubble showBadge={false} onClick={() => alert("idle bubble")} />
  </Frame>
);
FloatingBubbleStory.storyName = "FloatingBubble (with / without badge)";
