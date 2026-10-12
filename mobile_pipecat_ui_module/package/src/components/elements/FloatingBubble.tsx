"use client";

import { cn } from "@/lib/utils";
import React, { useEffect, useState } from "react";

export interface FloatingBubbleProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /** Bubble side length in px. @default 56 */
  size?: number;
  /** Show the white "active mic" badge in the top-right. @default true */
  showBadge?: boolean;
}

/**
 * Floating Pipecat bubble (FAB) — a vermilion circle with the Pipecat
 * wordmark (icon-only), a breathing halo, and an optional active mic
 * badge. Pin it with `position: absolute; left/bottom`.
 */
export const FloatingBubble: React.FC<FloatingBubbleProps> = ({
  size = 56,
  showBadge = true,
  className,
  style,
  "aria-label": ariaLabel = "Open Pipecat",
  ...rest
}) => {
  const [t, setT] = useState(0);
  useEffect(() => {
    let raf = 0;
    const loop = () => {
      setT(performance.now() / 1000);
      raf = requestAnimationFrame(loop);
    };
    loop();
    return () => cancelAnimationFrame(raf);
  }, []);

  const pulse = 0.5 + 0.5 * Math.sin(t * 2.2);
  const badgeSize = 14;
  const badgeDot = 8;

  return (
    <button
      type="button"
      aria-label={ariaLabel}
      className={cn(
        "relative inline-flex cursor-pointer border-0 bg-transparent p-0",
        className,
      )}
      style={{ width: size, height: size, ...style }}
      {...rest}
    >
      {/* Halo — blurred accent disc, scales with pulse */}
      <span
        aria-hidden
        className="pointer-events-none absolute inset-0 rounded-full"
        style={{
          background: "var(--pc-accent)",
          opacity: 0.2 + pulse * 0.15,
          transform: `scale(${1 + pulse * 0.35})`,
          filter: "blur(1px)",
        }}
      />
      {/* Outer pulse ring */}
      <span
        aria-hidden
        className="absolute inset-0 rounded-full"
        style={{
          boxShadow: `0 0 0 ${2 + pulse * 6}px color-mix(in srgb, var(--pc-accent) 20%, transparent)`,
        }}
      />
      {/* Core */}
      <span
        className="absolute inset-0 inline-flex items-center justify-center rounded-full"
        style={{
          background: "var(--pc-accent)",
          boxShadow:
            "0 8px 22px color-mix(in srgb, var(--pc-accent) 40%, transparent), 0 2px 6px rgba(0,0,0,0.3)",
        }}
      >
        <svg
          width={size * 0.46}
          height={size * 0.46}
          viewBox="0 0 22 22"
          aria-hidden
        >
          <rect x="5" y="5" width="4" height="12" fill="#fff" />
          <rect x="13" y="5" width="4" height="6" fill="#fff" />
        </svg>
      </span>
      {showBadge && (
        <span
          aria-hidden
          className="absolute inline-flex items-center justify-center rounded-full"
          style={{
            top: -2,
            right: -2,
            width: badgeSize,
            height: badgeSize,
            background: "#fff",
            boxShadow: "0 2px 4px rgba(0,0,0,0.25)",
          }}
        >
          <span
            className="rounded-full"
            style={{
              width: badgeDot,
              height: badgeDot,
              background: "var(--pc-accent)",
              animation: "pc-pulse-dot 1.2s ease-in-out infinite",
            }}
          />
        </span>
      )}
    </button>
  );
};

FloatingBubble.displayName = "FloatingBubble";
