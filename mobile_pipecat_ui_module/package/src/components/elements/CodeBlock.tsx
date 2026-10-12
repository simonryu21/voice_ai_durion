import { cn } from "@/lib/utils";
import React from "react";

export interface CodeBlockLine {
  /** Raw code; render plain. */
  text?: string;
  /** Or render as syntax-highlighted segments (left → right). */
  segments?: Array<{ text: string; tone?: "accent" | "muted" | "default" }>;
}

export interface CodeBlockProps extends React.HTMLAttributes<HTMLDivElement> {
  lines: CodeBlockLine[];
}

const SEGMENT_COLOR: Record<
  NonNullable<CodeBlockLine["segments"]>[number]["tone"] & string,
  string
> = {
  accent: "var(--pc-accent)",
  muted: "#9AA",
  default: "#E5DDD8",
};

/**
 * Inline code block matching the handoff Conversation screen — black bg,
 * Geist Mono 11.5px, 12px radius, simple keyword tinting via segment tones.
 */
export const CodeBlock: React.FC<CodeBlockProps> = ({
  lines,
  className,
  style,
  ...rest
}) => {
  return (
    <div
      className={cn(className)}
      style={{
        padding: "10px 12px",
        borderRadius: 12,
        background: "#0E0B0A",
        fontFamily: "'Geist Mono', ui-monospace, monospace",
        fontSize: 11.5,
        lineHeight: 1.55,
        color: "#E5DDD8",
        overflowX: "auto",
        ...style,
      }}
      {...rest}
    >
      {lines.map((line, i) => (
        <div key={i}>
          {line.segments
            ? line.segments.map((seg, j) => (
                <span
                  key={j}
                  style={{ color: SEGMENT_COLOR[seg.tone ?? "default"] }}
                >
                  {seg.text}
                </span>
              ))
            : line.text}
        </div>
      ))}
    </div>
  );
};

CodeBlock.displayName = "CodeBlock";
