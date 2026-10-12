"use client";

import React, { useEffect, useState } from "react";

export interface VoiceVizBarsProps {
  /** Solid accent color. Defaults to `var(--pc-accent)`. */
  accent?: string;
  /** Whether the visualization is animating. */
  active?: boolean;
  /** Diameter of the visualization in px. @default 220 */
  size?: number;
  /** Number of bars. @default 32 */
  bars?: number;
}

/**
 * Pipecat handoff `VizBars` — 32 vertical accent-colored bars with a
 * sin-based envelope (taller in the middle, shorter at the edges). Synthetic
 * waveform when no audio track is wired in.
 */
export const VoiceVizBars: React.FC<VoiceVizBarsProps> = ({
  accent = "var(--pc-accent)",
  active = true,
  size = 220,
  bars: barCount = 32,
}) => {
  const [t, setT] = useState(0);
  useEffect(() => {
    if (!active) return;
    let raf = 0;
    const loop = () => {
      setT(performance.now() / 1000);
      raf = requestAnimationFrame(loop);
    };
    loop();
    return () => cancelAnimationFrame(raf);
  }, [active]);

  const heights = Array.from({ length: barCount }, (_, i) => {
    const phase = i / barCount;
    const base = 0.18 + 0.5 * Math.abs(Math.sin(phase * 5 + t * 3.5));
    const env = Math.sin(phase * Math.PI) ** 1.4;
    return active ? base * env : 0.05;
  });

  return (
    <div
      className="flex w-full items-center justify-center"
      style={{ gap: 4, height: size }}
    >
      {heights.map((h, i) => (
        <div
          key={i}
          style={{
            width: 5,
            height: `${Math.max(4, h * size)}px`,
            borderRadius: 2,
            background: accent,
            opacity: 0.6 + h * 0.4,
            transition: "height 80ms linear",
          }}
        />
      ))}
    </div>
  );
};

VoiceVizBars.displayName = "VoiceVizBars";
