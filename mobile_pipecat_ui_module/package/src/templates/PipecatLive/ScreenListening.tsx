import { AppBar } from "@/components/elements/AppBar";
import { AskBar } from "@/components/elements/AskBar";
import { VoiceViz, type VoiceVizStyle } from "@/visualizers/VoiceViz";
import React from "react";

export interface ScreenListeningProps {
  /** Quote (what the user is/was saying). `\n` becomes a line break. */
  quote?: string;
  /** System detail chip text (e.g. STT model name). */
  systemLabel?: string;
  /** Visualizer style. @default "bars" */
  vizStyle?: VoiceVizStyle;
  /** Fired when the AskBar stop button is tapped. */
  onStopTap?: () => void;
}

/**
 * Listening screen — radial accent glow behind a centered VoiceViz, a quoted
 * caption, and a mic-active chip; AskBar is in `listening` mode below.
 */
export const ScreenListening: React.FC<ScreenListeningProps> = ({
  quote = "How do I deploy this\nto a Raspberry Pi?",
  systemLabel = "microphone active · whisper-large-v3",
  vizStyle = "bars",
  onStopTap,
}) => {
  return (
    <>
      <AppBar state="listening" latency={218} />
      <div
        className="relative flex flex-1 flex-col items-center justify-center"
        style={{ padding: 20 }}
      >
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              "radial-gradient(circle at 50% 55%, color-mix(in srgb, var(--pc-accent) 15%, transparent) 0%, transparent 55%)",
          }}
        />
        <VoiceViz style={vizStyle} size={240} />
        <div
          style={{
            marginTop: 36,
            fontFamily: "'Geist', ui-sans-serif, system-ui",
            fontSize: 22,
            fontWeight: 500,
            color: "var(--pc-text)",
            textAlign: "center",
            lineHeight: 1.3,
            letterSpacing: "-0.01em",
            maxWidth: 280,
            whiteSpace: "pre-line",
          }}
        >
          {`"${quote}"`}
        </div>
        <div
          className="inline-flex items-center"
          style={{
            marginTop: 16,
            gap: 8,
            padding: "6px 12px",
            borderRadius: 999,
            background: "var(--pc-chip-bg)",
            border: "1px solid var(--pc-chip-border)",
            fontFamily: "'Geist Mono', ui-monospace, monospace",
            fontSize: 10.5,
            color: "var(--pc-text-dim)",
          }}
        >
          <span
            aria-hidden
            style={{
              width: 6,
              height: 6,
              borderRadius: "50%",
              background: "var(--pc-accent)",
              animation: "pc-pulse-dot 1.2s ease-in-out infinite",
            }}
          />
          {systemLabel}
        </div>
      </div>
      <AskBar state="listening" onMicTap={onStopTap} />
    </>
  );
};

ScreenListening.displayName = "ScreenListening";
