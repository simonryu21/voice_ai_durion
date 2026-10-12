"use client";

import React from "react";
import { VoiceVizBars, type VoiceVizBarsProps } from "./VoiceVizBars";
import { VoiceVizOrb, type VoiceVizOrbProps } from "./VoiceVizOrb";
import {
  VoiceVizParticles,
  type VoiceVizParticlesProps,
} from "./VoiceVizParticles";

export type VoiceVizStyle = "bars" | "orb" | "particles";

export interface VoiceVizProps
  extends VoiceVizBarsProps,
    VoiceVizOrbProps,
    VoiceVizParticlesProps {
  /** Which visualization to render. @default "bars" */
  style?: VoiceVizStyle;
}

/**
 * Style-selectable voice visualizer. Mirrors the handoff Tweaks panel
 * (`Style: bars | orb | particles`). Each underlying viz is exported
 * separately for direct use when the style is known.
 */
export const VoiceViz: React.FC<VoiceVizProps> = ({
  style = "bars",
  ...rest
}) => {
  switch (style) {
    case "orb":
      return <VoiceVizOrb {...rest} />;
    case "particles":
      return <VoiceVizParticles {...rest} />;
    case "bars":
    default:
      return <VoiceVizBars {...rest} />;
  }
};

VoiceViz.displayName = "VoiceViz";

export { VoiceVizBars, VoiceVizOrb, VoiceVizParticles };
export type { VoiceVizBarsProps, VoiceVizOrbProps, VoiceVizParticlesProps };
