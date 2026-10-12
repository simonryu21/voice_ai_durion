import { cn } from "@/lib/utils";
import React from "react";

export interface PcChipProps extends React.HTMLAttributes<HTMLSpanElement> {
  /** Visual tone. `accent` fills with vermilion + white text. */
  tone?: "default" | "accent";
  /** Use Geist Mono with uppercase tracking (default for tag chips). */
  mono?: boolean;
}

/**
 * Small label chip used for suggestion tags, code inline tokens, and the
 * "microphone active · whisper-large-v3" style status chips.
 */
export const PcChip = React.forwardRef<HTMLSpanElement, PcChipProps>(
  (
    { tone = "default", mono = true, className, style, children, ...rest },
    ref,
  ) => {
    const isAccent = tone === "accent";
    return (
      <span
        ref={ref}
        className={cn("inline-flex items-center", className)}
        style={{
          padding: "3px 7px",
          borderRadius: 4,
          background: isAccent ? "var(--pc-accent)" : "var(--pc-chip-bg)",
          color: isAccent ? "#fff" : "var(--pc-text-dim)",
          border: isAccent ? "none" : "1px solid var(--pc-chip-border)",
          fontFamily: mono
            ? "'Geist Mono', ui-monospace, monospace"
            : "'Geist', ui-sans-serif, system-ui, -apple-system, sans-serif",
          fontSize: 9.5,
          letterSpacing: "0.08em",
          textTransform: mono ? "uppercase" : "none",
          ...style,
        }}
        {...rest}
      >
        {children}
      </span>
    );
  },
);

PcChip.displayName = "PcChip";
