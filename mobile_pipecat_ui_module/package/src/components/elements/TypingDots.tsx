import { cn } from "@/lib/utils";
import React from "react";

export interface TypingDotsProps extends React.HTMLAttributes<HTMLSpanElement> {
  /** Dot color. Defaults to `var(--pc-accent)`. */
  color?: string;
}

/**
 * Three-dot typing indicator with the `pc-typing-bounce` keyframe staggered
 * at 0 / 0.15s / 0.3s. Drop next to an inline mono label like "typing".
 */
export const TypingDots: React.FC<TypingDotsProps> = ({
  color = "var(--pc-accent)",
  className,
  style,
  ...rest
}) => {
  return (
    <span
      className={cn("inline-flex", className)}
      style={{
        gap: 3,
        marginLeft: 4,
        verticalAlign: "-1px",
        ...style,
      }}
      {...rest}
    >
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          aria-hidden
          style={{
            width: 4,
            height: 4,
            borderRadius: "50%",
            background: color,
            animation: `pc-typing-bounce 1.1s ease-in-out ${i * 0.15}s infinite`,
          }}
        />
      ))}
    </span>
  );
};

TypingDots.displayName = "TypingDots";
