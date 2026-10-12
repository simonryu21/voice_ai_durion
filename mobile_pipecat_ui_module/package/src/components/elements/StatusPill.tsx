import { cn } from "@/lib/utils";
import React from "react";

export type StatusPillState = "idle" | "listening" | "thinking" | "speaking";

export interface StatusPillProps extends React.HTMLAttributes<HTMLSpanElement> {
  state: StatusPillState;
  /** Round-trip latency in ms. Hidden if `undefined`. */
  latency?: number;
  /**
   * Override the label for the current state.
   * Takes precedence over `labels[state]`.
   */
  label?: string;
  /**
   * Map of state → label. Use to translate the four state names. Any state
   * you omit falls back to the English default.
   */
  labels?: Partial<Record<StatusPillState, string>>;
}

const DEFAULT_LABELS: Record<StatusPillState, string> = {
  idle: "ready",
  listening: "listening",
  thinking: "thinking",
  speaking: "speaking",
};

/**
 * Compact status indicator: a colored dot (green for idle, accent for the
 * active states with a pulse animation), the state label, and an optional
 * latency stat. Mono font, 10.5px.
 */
export const StatusPill: React.FC<StatusPillProps> = ({
  state,
  latency,
  label,
  labels,
  className,
  style,
  ...rest
}) => {
  const resolvedLabel = label ?? labels?.[state] ?? DEFAULT_LABELS[state];
  const isActive = state !== "idle";
  const dotColor = isActive ? "var(--pc-accent)" : "var(--pc-state-ready)";

  return (
    <span
      className={cn("inline-flex items-center gap-1.5", className)}
      style={{
        padding: "4px 10px 4px 8px",
        borderRadius: 999,
        background: "var(--pc-chip-bg)",
        border: "1px solid var(--pc-chip-border)",
        fontFamily: "'Geist Mono', ui-monospace, monospace",
        fontSize: 10.5,
        color: "var(--pc-text-dim)",
        letterSpacing: "0.02em",
        ...style,
      }}
      {...rest}
    >
      <span
        aria-hidden
        style={{
          width: 6,
          height: 6,
          borderRadius: "50%",
          background: dotColor,
          boxShadow: isActive ? `0 0 8px ${dotColor}` : "none",
          animation: isActive
            ? "pc-pulse-dot 1.2s ease-in-out infinite"
            : "none",
        }}
      />
      {resolvedLabel}
      {latency !== undefined && (
        <span style={{ color: "var(--pc-text-faint)", marginLeft: 4 }}>
          · {latency}ms
        </span>
      )}
    </span>
  );
};

StatusPill.displayName = "StatusPill";
