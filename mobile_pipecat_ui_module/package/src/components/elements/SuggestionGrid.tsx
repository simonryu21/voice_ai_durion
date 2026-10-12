import { cn } from "@/lib/utils";
import React from "react";

export interface SuggestionCard {
  /** Short tag (uppercase, mono). The first card defaults to accent fill. */
  tag: string;
  /** Card title. `\n` becomes a line break. */
  title: string;
  /** Highlight this card with accent-filled tag chip. Auto on first card. */
  accent?: boolean;
}

export interface SuggestionGridProps
  extends Omit<React.HTMLAttributes<HTMLDivElement>, "onSelect"> {
  cards: SuggestionCard[];
  /** Fired when a card is selected. Receives the title. */
  onSelect?: (title: string) => void;
}

/**
 * 2-column grid of suggestion cards used on the Idle screen. Each card has
 * an uppercase mono tag chip and a 14sp title with `pre-line` whitespace.
 * The first card (or any card with `accent: true`) renders its tag chip in
 * the accent color.
 */
export const SuggestionGrid: React.FC<SuggestionGridProps> = ({
  cards,
  onSelect,
  className,
  ...rest
}) => {
  return (
    <div
      className={cn("grid gap-2.5 px-[18px]", className)}
      style={{ gridTemplateColumns: "1fr 1fr" }}
      {...rest}
    >
      {cards.map((card, i) => {
        const isAccent = card.accent ?? i === 0;
        return (
          <button
            key={`${card.tag}-${i}`}
            type="button"
            onClick={() => onSelect?.(card.title)}
            className="flex cursor-pointer flex-col items-stretch text-left"
            style={{
              padding: "14px 14px 16px",
              borderRadius: 16,
              background: "var(--pc-surface)",
              border: "1px solid var(--pc-border)",
              gap: 16,
              minHeight: 110,
            }}
          >
            <span
              className="self-start"
              style={{
                fontFamily: "'Geist Mono', ui-monospace, monospace",
                fontSize: 9.5,
                letterSpacing: "0.08em",
                textTransform: "uppercase",
                padding: "3px 7px",
                borderRadius: 4,
                background: isAccent ? "var(--pc-accent)" : "var(--pc-chip-bg)",
                color: isAccent ? "#fff" : "var(--pc-text-dim)",
              }}
            >
              {card.tag}
            </span>
            <span
              style={{
                fontSize: 14,
                lineHeight: 1.35,
                color: "var(--pc-text)",
                fontWeight: 500,
                whiteSpace: "pre-line",
              }}
            >
              {card.title}
            </span>
          </button>
        );
      })}
    </div>
  );
};

SuggestionGrid.displayName = "SuggestionGrid";
