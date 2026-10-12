import type { Story, StoryDefault } from "@ladle/react";
import { VoiceViz, type VoiceVizStyle } from "./index";

export default {
  title: "Pipecat Voice UI / Visualizers",
  argTypes: {
    style: {
      options: ["bars", "orb", "particles"],
      control: { type: "select" },
      defaultValue: "bars",
    },
    active: {
      control: { type: "boolean" },
      defaultValue: true,
    },
    size: {
      control: { type: "number" },
      defaultValue: 240,
    },
  },
} satisfies StoryDefault;

const Frame: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div
    className="dark"
    style={{
      minHeight: "100vh",
      padding: 40,
      background: "var(--pc-canvas)",
      color: "var(--pc-text)",
      display: "flex",
      flexDirection: "column",
      gap: 24,
    }}
  >
    {children}
  </div>
);

export const Controlled: Story<{
  style: VoiceVizStyle;
  active: boolean;
  size: number;
}> = ({ style, active, size }) => (
  <Frame>
    <div
      style={{
        background: "var(--pc-bg-base)",
        borderRadius: 16,
        padding: 24,
        display: "flex",
        justifyContent: "center",
      }}
    >
      <VoiceViz style={style} active={active} size={size} />
    </div>
  </Frame>
);
Controlled.storyName = "VoiceViz · controlled";

export const AllThree: Story = () => (
  <Frame>
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(3, 1fr)",
        gap: 16,
      }}
    >
      {(["bars", "orb", "particles"] as VoiceVizStyle[]).map((style) => (
        <div
          key={style}
          style={{
            background: "var(--pc-bg-base)",
            borderRadius: 16,
            padding: 24,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 12,
          }}
        >
          <VoiceViz style={style} size={200} />
          <span
            style={{
              fontFamily: "'Geist Mono', ui-monospace, monospace",
              fontSize: 11,
              color: "var(--pc-text-faint)",
              letterSpacing: "0.08em",
              textTransform: "uppercase",
            }}
          >
            {style}
          </span>
        </div>
      ))}
    </div>
  </Frame>
);
AllThree.storyName = "VoiceViz · all three";
