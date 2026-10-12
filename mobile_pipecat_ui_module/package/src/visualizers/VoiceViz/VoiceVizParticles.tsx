"use client";

import React, { useEffect, useState } from "react";

export interface VoiceVizParticlesProps {
  /** Solid accent color. Defaults to `var(--pc-accent)`. */
  accent?: string;
  /** Whether the visualization is animating. */
  active?: boolean;
  /** Diameter of the visualization in px. @default 220 */
  size?: number;
  /** Grid side count. @default 11 (→ 121 dots total) */
  grid?: number;
}

/**
 * Pipecat handoff `VizParticles` — an N×N dot grid where each dot's scale
 * and opacity follow a radial sine wave breathing outwards.
 */
export const VoiceVizParticles: React.FC<VoiceVizParticlesProps> = ({
  accent = "var(--pc-accent)",
  active = true,
  size = 220,
  grid: N = 11,
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

  const cell = size / N;
  const dots: Array<{ x: number; y: number; scale: number; op: number }> = [];
  for (let y = 0; y < N; y++) {
    for (let x = 0; x < N; x++) {
      const dx = x - (N - 1) / 2;
      const dy = y - (N - 1) / 2;
      const dist = Math.sqrt(dx * dx + dy * dy);
      const wave = Math.sin(dist * 0.9 - t * 3);
      const scale = active ? 0.3 + Math.max(0, wave) * 1.1 : 0.3;
      const op = active ? 0.2 + Math.max(0, wave) * 0.8 : 0.2;
      dots.push({ x, y, scale, op });
    }
  }

  return (
    <div className="relative" style={{ width: size, height: size }}>
      {dots.map((d, i) => (
        <div
          key={i}
          aria-hidden
          className="absolute rounded-full"
          style={{
            left: d.x * cell + cell / 2 - 4,
            top: d.y * cell + cell / 2 - 4,
            width: 8,
            height: 8,
            background: accent,
            opacity: d.op,
            transform: `scale(${d.scale})`,
            transition: "transform 80ms linear, opacity 80ms linear",
          }}
        />
      ))}
    </div>
  );
};

VoiceVizParticles.displayName = "VoiceVizParticles";
