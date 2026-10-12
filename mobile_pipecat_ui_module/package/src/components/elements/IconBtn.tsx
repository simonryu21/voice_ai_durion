import { cn } from "@/lib/utils";
import React from "react";

export interface IconBtnProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /** Square button side length in px. @default 32 */
  size?: number;
  /** Background style. @default "chip" (subtle chipBg) */
  tone?: "chip" | "accent" | "ghost";
}

/**
 * Square rounded icon button matching the handoff `iconBtn` style:
 * 32×32 by default, radius 10, chipBg fill, no border. Use `tone="accent"`
 * for vermilion-filled actions (Stop / submit) and `tone="ghost"` to inherit
 * the parent background entirely.
 */
export const IconBtn = React.forwardRef<HTMLButtonElement, IconBtnProps>(
  ({ size = 32, tone = "chip", className, style, children, ...rest }, ref) => {
    const palette = {
      chip: {
        background: "var(--pc-chip-bg)",
        color: "var(--pc-text)",
        boxShadow: "none",
      },
      accent: {
        background: "var(--pc-accent)",
        color: "#fff",
        boxShadow:
          "0 6px 18px color-mix(in srgb, var(--pc-accent) 40%, transparent)",
      },
      ghost: {
        background: "transparent",
        color: "var(--pc-text)",
        boxShadow: "none",
      },
    }[tone];

    return (
      <button
        ref={ref}
        type="button"
        className={cn(
          "inline-flex items-center justify-center border-0 cursor-pointer",
          "transition-colors duration-[var(--pc-motion-fast)]",
          "disabled:cursor-not-allowed disabled:opacity-50",
          className,
        )}
        style={{
          width: size,
          height: size,
          borderRadius: tone === "accent" ? "50%" : 10,
          ...palette,
          ...style,
        }}
        {...rest}
      >
        {children}
      </button>
    );
  },
);

IconBtn.displayName = "IconBtn";
