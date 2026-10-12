import { cn } from "@/lib/utils";
import React from "react";

export interface WordmarkProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Logo tile color. Defaults to `--pc-accent`. */
  accent?: string;
  /** "pipecat" text color. Defaults to `--pc-text`. */
  textColor?: string;
  /** Hide the text and only render the logo tile. */
  iconOnly?: boolean;
  /** Logo tile size in px. @default 22 */
  size?: number;
  /** Brand text shown next to the tile. @default "pipecat" */
  text?: string;
}

/**
 * Pipecat wordmark — a vermilion rounded tile with two white bars (long + short)
 * followed by the "pipecat" text in Geist 600.
 */
export const Wordmark = React.forwardRef<HTMLDivElement, WordmarkProps>(
  (
    {
      accent = "var(--pc-accent)",
      textColor = "var(--pc-text)",
      iconOnly = false,
      size = 22,
      text = "pipecat",
      className,
      ...rest
    },
    ref,
  ) => {
    return (
      <div
        ref={ref}
        className={cn("inline-flex items-center gap-2", className)}
        {...rest}
      >
        <svg width={size} height={size} viewBox="0 0 22 22" aria-hidden>
          <rect x="0" y="0" width="22" height="22" rx="5" fill={accent} />
          <rect x="5" y="5" width="4" height="12" fill="#fff" />
          <rect x="13" y="5" width="4" height="6" fill="#fff" />
        </svg>
        {!iconOnly && (
          <span
            style={{
              fontFamily:
                "'Geist', ui-sans-serif, system-ui, -apple-system, sans-serif",
              fontWeight: 600,
              fontSize: 17,
              letterSpacing: "-0.01em",
              color: textColor,
            }}
          >
            {text}
          </span>
        )}
      </div>
    );
  },
);

Wordmark.displayName = "Wordmark";
