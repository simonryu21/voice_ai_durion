import { FloatingBubble } from "@/components/elements/FloatingBubble";
import React from "react";
import { FakeHostApp } from "./FakeHostApp";

export interface ScreenBubbleProps {
  /** Fired when the floating bubble is tapped. */
  onBubbleTap?: () => void;
}

/**
 * Bubble screen — a FloatingBubble pinned bottom-left over a generic host
 * app (Notes mock). Use this to demo the minimized floating entry point.
 */
export const ScreenBubble: React.FC<ScreenBubbleProps> = ({ onBubbleTap }) => {
  return (
    <div className="relative flex flex-1 flex-col">
      <FakeHostApp />
      <FloatingBubble
        onClick={onBubbleTap}
        style={{
          position: "absolute",
          left: 16,
          bottom: 16,
        }}
      />
    </div>
  );
};

ScreenBubble.displayName = "ScreenBubble";
