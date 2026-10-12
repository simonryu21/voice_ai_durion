import { OverlayBar } from "@/components/elements/OverlayBar";
import React from "react";
import { FakeHostApp } from "./FakeHostApp";

export interface ScreenOverlayProps {
  /** Fired when the stop button is tapped. */
  onStopTap?: () => void;
}

/**
 * Overlay screen — the OverlayBar pinned to the bottom over a generic host
 * app (Notes mock). Use this to demo what the system overlay looks like.
 */
export const ScreenOverlay: React.FC<ScreenOverlayProps> = ({ onStopTap }) => {
  return (
    <div className="relative flex flex-1 flex-col">
      <FakeHostApp />
      <OverlayBar
        onStopTap={onStopTap}
        style={{
          position: "absolute",
          left: 12,
          right: 12,
          bottom: 12,
        }}
      />
    </div>
  );
};

ScreenOverlay.displayName = "ScreenOverlay";
