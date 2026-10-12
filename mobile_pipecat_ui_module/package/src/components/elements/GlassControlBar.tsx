import { cn } from "@/lib/utils";
import React from "react";

export interface GlassControlBarProps
  extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
}

/**
 * Pill-shaped translucent container with backdrop blur, intended to host
 * icon-only controls in a Gemini-style live UI.
 */
export const GlassControlBar = React.forwardRef<
  HTMLDivElement,
  GlassControlBarProps
>(({ className, children, ...rest }, ref) => {
  return (
    <div
      ref={ref}
      className={cn(
        "inline-flex items-center gap-3 px-3 py-2",
        "rounded-full border backdrop-blur-xl",
        "shadow-[0_8px_40px_-12px_rgba(0,0,0,0.6)]",
        "transition-colors duration-[var(--pc-motion-base)]",
        className,
      )}
      style={{
        background: "var(--pc-chip-bg)",
        borderColor: "var(--pc-chip-border)",
      }}
      {...rest}
    >
      {children}
    </div>
  );
});

GlassControlBar.displayName = "GlassControlBar";
