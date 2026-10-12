import { AppBar } from "@/components/elements/AppBar";
import { AskBar, type AskBarState } from "@/components/elements/AskBar";
import {
  type SuggestionCard,
  SuggestionGrid,
} from "@/components/elements/SuggestionGrid";
import React from "react";

export interface ScreenIdleProps {
  /**
   * Suggestions shown in the 2×2 grid.
   * @default context-based set (morning / afternoon / evening / weekend)
   */
  cards?: SuggestionCard[];
  /** Greeting line (left part). @default time-based "Good morning," etc. */
  greeting?: string;
  /**
   * The full headline (overrides the default `${greeting}\nhow can I help today?`).
   * Use this when translating the entire phrase. The word to highlight with
   * an accent underline is controlled by `highlight`.
   */
  headline?: React.ReactNode;
  /** The word/phrase highlighted with the accent underline. @default "today" */
  highlight?: string;
  /** Mono pre-suggestion label. @default "SUGGESTIONS" */
  suggestionsLabel?: string;
  /** Trailing CTA. @default "see all →" */
  seeAllLabel?: string;
  /** Session label prefix (mono). @default "SESSION" */
  sessionLabel?: string;
  /** AskBar placeholder. @default "Ask anything" */
  askPlaceholder?: string;
  /** Fired when the bottom mic button is tapped. */
  onMicTap?: () => void;
  /** AskBar state. @default "idle" */
  askBarState?: AskBarState;
}

const MORNING_CARDS: SuggestionCard[] = [
  { tag: "plan", title: "Draft today's\npriorities" },
  { tag: "voice", title: "Practice my\nstandup intro" },
  { tag: "code", title: "Review last night's\nPR comments" },
  { tag: "data", title: "Summarize my\nunread inbox" },
];

const AFTERNOON_CARDS: SuggestionCard[] = [
  { tag: "code", title: "Explain this Python\ntraceback" },
  { tag: "plan", title: "Draft a 5-step\nlaunch checklist" },
  { tag: "data", title: "Summarize the\nattached CSV" },
  { tag: "idea", title: "Brainstorm names\nfor this feature" },
];

const EVENING_CARDS: SuggestionCard[] = [
  { tag: "recap", title: "Summarize what\nI shipped today" },
  { tag: "plan", title: "Draft tomorrow's\nstandup notes" },
  { tag: "voice", title: "Rehearse my\ndemo script" },
  { tag: "idea", title: "Suggest a short\nevening read" },
];

const WEEKEND_CARDS: SuggestionCard[] = [
  { tag: "idea", title: "Plan a weekend\nside project" },
  { tag: "voice", title: "Practice a\nlanguage lesson" },
  { tag: "plan", title: "Outline next\nweek's goals" },
  { tag: "data", title: "Summarize a\nlong article" },
];

/** Suggestion set chosen by weekday/weekend and time of day. */
const defaultCards = (now: Date = new Date()): SuggestionCard[] => {
  const day = now.getDay();
  if (day === 0 || day === 6) return WEEKEND_CARDS;
  const h = now.getHours();
  if (h < 12) return MORNING_CARDS;
  if (h < 18) return AFTERNOON_CARDS;
  return EVENING_CARDS;
};

const defaultGreeting = () => {
  const h = new Date().getHours();
  if (h < 12) return "Good morning,";
  if (h < 18) return "Good afternoon,";
  return "Good evening,";
};

const defaultSessionLabel = (prefix: string) =>
  `${prefix} · ${new Date().toISOString().slice(0, 10).replace(/-/g, "·")}`;

/**
 * Idle screen — AppBar, time-based greeting with an accent underline on the
 * last word, SESSION label, suggestion grid, and AskBar. Matches the
 * `app.jsx#ScreenIdle` layout 1:1.
 */
export const ScreenIdle: React.FC<ScreenIdleProps> = ({
  cards = defaultCards(),
  greeting = defaultGreeting(),
  headline,
  highlight = "today",
  suggestionsLabel = "Suggestions",
  seeAllLabel = "see all →",
  sessionLabel = "SESSION",
  askPlaceholder,
  onMicTap,
  askBarState = "idle",
}) => {
  const headlineNode = headline ?? (
    <>
      <span style={{ color: "var(--pc-text-dim)" }}>{greeting}</span>
      <br />
      how can I help{" "}
      <span
        style={{
          background:
            "linear-gradient(180deg, transparent 60%, color-mix(in srgb, var(--pc-accent) 20%, transparent) 60%)",
        }}
      >
        {highlight}
      </span>
      ?
    </>
  );

  return (
    <>
      <AppBar state="idle" latency={142} />
      <div
        className="flex-1 overflow-auto"
        style={{ padding: "24px 18px 18px", minHeight: 0 }}
      >
        <div
          style={{
            fontFamily: "'Geist', ui-sans-serif, system-ui, -apple-system",
            fontSize: 28,
            lineHeight: 1.15,
            letterSpacing: "-0.02em",
            color: "var(--pc-text)",
            fontWeight: 500,
          }}
        >
          {headlineNode}
        </div>
        <div
          style={{
            marginTop: 8,
            fontFamily: "'Geist Mono', ui-monospace, monospace",
            fontSize: 10.5,
            color: "var(--pc-text-faint)",
            letterSpacing: "0.04em",
          }}
        >
          {defaultSessionLabel(sessionLabel)}
        </div>
        <div
          className="flex items-center justify-between"
          style={{ marginTop: 22, padding: "0 0 12px" }}
        >
          <span
            style={{
              fontFamily: "'Geist Mono', ui-monospace, monospace",
              fontSize: 10.5,
              letterSpacing: "0.08em",
              textTransform: "uppercase",
              color: "var(--pc-text-faint)",
            }}
          >
            {suggestionsLabel}
          </span>
          <span
            style={{
              fontSize: 11.5,
              color: "var(--pc-accent)",
              fontFamily: "'Geist', ui-sans-serif, system-ui",
              fontWeight: 500,
            }}
          >
            {seeAllLabel}
          </span>
        </div>
        <div style={{ marginLeft: -18, marginRight: -18 }}>
          <SuggestionGrid cards={cards} />
        </div>
      </div>
      <AskBar
        state={askBarState}
        placeholder={askPlaceholder}
        onMicTap={onMicTap}
      />
    </>
  );
};

ScreenIdle.displayName = "ScreenIdle";
