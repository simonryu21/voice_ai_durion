"use client";

import { cn } from "@/lib/utils";
import React, { useEffect, useState } from "react";
import { Wordmark } from "./Wordmark";

export interface OverlayBarProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Show "listening" label + animated mini bars. */
  active?: boolean;
  /** Label shown on the right. @default "listening" */
  label?: string;
  /** ARIA label for the stop button. @default "Stop" */
  stopLabel?: string;
  /** Fired when the stop button is tapped. */
  onStopTap?: () => void;
  /** Number of mini bars. @default 22 */
  bars?: number;
  /** Theme variant for the inner container background. @default "auto" */
  theme?: "dark" | "light" | "auto";
}

const Stop: React.FC = () => (
  <svg
    width="16"
    height="16"
    viewBox="0 0 16 16"
    fill="currentColor"
    aria-hidden
  >
    <rect x="4" y="4" width="8" height="8" rx="1.5" />
  </svg>
);

/**
 * Floating system-overlay bar — pill container with backdrop blur, a soft
 * accent glow ring, Pipecat wordmark, animated mini bars, a "listening"
 * label, and a vermilion Stop button.
 *
 * Pin it with `position: absolute; left/right; bottom` over a host app.
 */
export const OverlayBar: React.FC<OverlayBarProps> = ({
  active = true,
  label = "listening",
  stopLabel = "Stop",
  onStopTap,
  bars: barCount = 22,
  theme = "auto",
  className,
  style,
  ...rest
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
    const base = 0.2 + 0.55 * Math.abs(Math.sin(phase * 5.5 + t * 4));
    const env = Math.sin(phase * Math.PI) ** 1.2;
    return active ? base * env : 0.05;
  });

  const innerBg =
    theme === "dark"
      ? "rgba(20,16,15,0.92)"
      : theme === "light"
        ? "rgba(255,255,255,0.92)"
        : "var(--pc-bg-base)";

  return (
    <div
      className={cn(className)}
      style={{
        padding: 4,
        borderRadius: 26,
        background: "color-mix(in srgb, var(--pc-accent) 13%, transparent)",
        boxShadow:
          "0 0 0 1.5px var(--pc-accent), 0 20px 50px rgba(0,0,0,0.4), 0 6px 16px color-mix(in srgb, var(--pc-accent) 33%, transparent)",
        backdropFilter: "blur(20px)",
        WebkitBackdropFilter: "blur(20px)",
        ...style,
      }}
      {...rest}
    >
      <div
        className="flex items-center gap-2.5"
        style={{
          padding: "8px 8px 8px 14px",
          borderRadius: 22,
          background: innerBg,
        }}
      >
        <Wordmark iconOnly />

        <div
          className="flex flex-1 items-center"
          style={{ gap: 2.5, height: 28, minWidth: 0 }}
        >
          {heights.map((h, i) => (
            <div
              key={i}
              style={{
                width: 3,
                height: `${Math.max(4, h * 28)}px`,
                borderRadius: 1.5,
                background: "var(--pc-accent)",
                opacity: 0.55 + h * 0.45,
                transition: "height 70ms linear",
                flexShrink: 0,
              }}
            />
          ))}
        </div>

        <span
          style={{
            fontFamily: "'Geist Mono', ui-monospace, monospace",
            fontSize: 10.5,
            color: "var(--pc-text-dim)",
            letterSpacing: "0.04em",
            whiteSpace: "nowrap",
          }}
        >
          {label}
        </span>

        <button
          type="button"
          onClick={onStopTap}
          aria-label={stopLabel}
          className="inline-flex items-center justify-center border-0 cursor-pointer"
          style={{
            width: 36,
            height: 36,
            borderRadius: "50%",
            background: "var(--pc-accent)",
            color: "#fff",
            boxShadow:
              "0 4px 12px color-mix(in srgb, var(--pc-accent) 53%, transparent)",
            flexShrink: 0,
          }}
        >
          <Stop />
        </button>
      </div>
    </div>
  );
};

OverlayBar.displayName = "OverlayBar";
