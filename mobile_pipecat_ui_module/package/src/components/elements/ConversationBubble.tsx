import { cn } from "@/lib/utils";
import React from "react";

export interface ConversationBubbleProps
  extends React.HTMLAttributes<HTMLDivElement> {
  role: "user" | "assistant";
  /** Sub-meta (e.g. "you · 09:32" or "pipecat · gemma-3-27b"). Optional. */
  meta?: React.ReactNode;
  /** Max bubble width as a percentage of the row. Defaults vary by role. */
  maxWidth?: string;
  /** Children render inside the bubble body. */
  children: React.ReactNode;
}

const Sparkle: React.FC<{ size?: number; color?: string }> = ({
  size = 12,
  color = "var(--pc-accent)",
}) => (
  <svg width={size} height={size} viewBox="0 0 16 16" fill="none">
    <path
      d="M8 1.5l1.3 4.2 4.2 1.3-4.2 1.3L8 12.5 6.7 8.3 2.5 7l4.2-1.3L8 1.5z"
      fill={color}
    />
  </svg>
);

/**
 * Conversation bubble matching the handoff Conversation screen.
 * - `role="user"`: right-aligned, vermilion fill, radius `18 18 4 18`.
 * - `role="assistant"`: left-aligned, surface fill + border, radius
 *   `18 18 18 4`, with an accent sparkle + meta line above.
 *
 * `meta` is rendered below user bubbles and above assistant bubbles. Pass
 * the body content as children.
 */
export const ConversationBubble: React.FC<ConversationBubbleProps> = ({
  role,
  meta,
  maxWidth,
  className,
  style,
  children,
  ...rest
}) => {
  const isUser = role === "user";
  const computedMax = maxWidth ?? (isUser ? "82%" : "88%");

  return (
    <div
      className={cn(
        "flex flex-col",
        isUser ? "items-end" : "items-start",
        className,
      )}
      style={{
        maxWidth: computedMax,
        alignSelf: isUser ? "flex-end" : "flex-start",
        ...style,
      }}
      {...rest}
    >
      {!isUser && meta && (
        <div className="mb-1.5 flex items-center gap-1.5">
          <Sparkle />
          <span
            style={{
              fontFamily: "'Geist Mono', ui-monospace, monospace",
              fontSize: 10,
              color: "var(--pc-text-dim)",
              letterSpacing: "0.04em",
            }}
          >
            {meta}
          </span>
        </div>
      )}

      <div
        style={{
          padding: isUser ? "10px 14px" : "12px 14px",
          borderRadius: isUser ? "18px 18px 4px 18px" : "18px 18px 18px 4px",
          background: isUser ? "var(--pc-accent)" : "var(--pc-surface)",
          border: isUser ? "none" : "1px solid var(--pc-border)",
          color: isUser ? "#fff" : "var(--pc-text)",
          fontSize: 14.5,
          lineHeight: isUser ? 1.45 : 1.5,
        }}
      >
        {children}
      </div>

      {isUser && meta && (
        <div
          style={{
            textAlign: "right",
            marginTop: 4,
            fontFamily: "'Geist Mono', ui-monospace, monospace",
            fontSize: 10,
            color: "var(--pc-text-faint)",
          }}
        >
          {meta}
        </div>
      )}
    </div>
  );
};

ConversationBubble.displayName = "ConversationBubble";
