"use client";

import React, { useEffect, useState } from "react";

export interface VoiceVizOrbProps {
  /** Solid accent color. Defaults to `var(--pc-accent)`. */
  accent?: string;
  /** Whether the visualization is animating. */
  active?: boolean;
  /** Diameter of the visualization in px. @default 220 */
  size?: number;
}

/**
 * Pipecat handoff `VizOrb` — three concentric rings (border-only) breathing
 * at 90° phase offsets, plus a solid 38% accent core with a pulsing glow.
 * No gradient — pure vermilion solid + box-shadow.
 */
export const VoiceVizOrb: React.FC<VoiceVizOrbProps> = ({
  accent = "var(--pc-accent)",
  active = true,
  size = 220,
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

  const pulse1 = 0.5 + 0.5 * Math.sin(t * 2.2);
  const pulse2 = 0.5 + 0.5 * Math.sin(t * 2.2 + Math.PI / 2);
  const pulse3 = 0.5 + 0.5 * Math.sin(t * 2.2 + Math.PI);
  const rings = [pulse3, pulse2, pulse1];

  return (
    <div
      className="relative flex items-center justify-center"
      style={{ width: size, height: size }}
    >
      {rings.map((p, i) => (
        <div
          key={i}
          aria-hidden
          className="absolute rounded-full"
          style={{
            width: `${50 + p * 50}%`,
            height: `${50 + p * 50}%`,
            border: `${2 - i * 0.5}px solid ${accent}`,
            opacity: (1 - p) * 0.7,
          }}
        />
      ))}
      <div
        className="rounded-full"
        style={{
          width: "38%",
          height: "38%",
          background: accent,
          boxShadow: `0 0 ${20 + pulse1 * 60}px ${accent}`,
          transform: `scale(${0.9 + pulse1 * 0.15})`,
        }}
      />
    </div>
  );
};

VoiceVizOrb.displayName = "VoiceVizOrb";
