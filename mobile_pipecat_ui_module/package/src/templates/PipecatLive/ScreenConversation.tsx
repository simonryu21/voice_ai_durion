import { AppBar } from "@/components/elements/AppBar";
import { AskBar } from "@/components/elements/AskBar";
import { CodeBlock, type CodeBlockLine } from "@/components/elements/CodeBlock";
import { ConversationBubble } from "@/components/elements/ConversationBubble";
import { TypingDots } from "@/components/elements/TypingDots";
import React from "react";

export interface ConversationTurn {
  role: "user" | "assistant";
  text?: React.ReactNode;
  /** Optional code block to render below the body (assistant only). */
  code?: CodeBlockLine[];
  /** Optional follow-up chip labels (assistant only). */
  followUps?: string[];
  /** Meta tag below user bubble / above assistant bubble. */
  meta?: React.ReactNode;
  /** Render this turn as a streaming "typing…" placeholder. */
  streaming?: boolean;
}

export interface ScreenConversationProps {
  /** Turn list (top → bottom). */
  turns?: ConversationTurn[];
  /** AskBar placeholder. @default "Reply or ask follow-up" */
  replyPlaceholder?: string;
  /** Inline mono label for the streaming bubble. @default "typing" */
  typingLabel?: string;
  /**
   * Invoked with the trimmed text when the user submits the AskBar input.
   * When provided, the AskBar becomes an editable text field; otherwise it
   * stays visual-only. Wire this to `usePipecatTextInput().sendTextMessage`.
   */
  onSendMessage?: (text: string) => void | Promise<void>;
}

const DEFAULT_TURNS: ConversationTurn[] = [
  {
    role: "user",
    text: "What's a good way to debounce a websocket reconnect loop in Python?",
    meta: "you · 09:32",
  },
  {
    role: "assistant",
    meta: "pipecat · gemma-3-27b",
    text: (
      <>
        Use exponential backoff with jitter. Start at{" "}
        <span
          style={{
            fontFamily: "'Geist Mono', ui-monospace, monospace",
            fontSize: 13,
            padding: "1px 5px",
            borderRadius: 4,
            background: "var(--pc-chip-bg)",
          }}
        >
          1s
        </span>
        , double on each failure, cap at ~30s, and add random jitter so
        reconnecting clients don't thunder the server in lockstep.
      </>
    ),
    code: [
      {
        segments: [
          { text: "delay", tone: "muted" },
          { text: " = " },
          { text: "min", tone: "accent" },
          { text: "(cap, base * 2**n)" },
        ],
      },
      {
        segments: [
          { text: "delay", tone: "muted" },
          { text: " += " },
          { text: "random", tone: "accent" },
          { text: ".uniform(0, delay)" },
        ],
      },
    ],
    followUps: ["Show in editor", "Explain jitter", "Try aiohttp variant"],
  },
  { role: "assistant", streaming: true },
];

/**
 * Conversation screen — AppBar in `thinking` state, scrollable bubble list
 * with optional inline code blocks and follow-up chips, and AskBar in
 * `idle` state with a "Reply or ask follow-up" placeholder.
 */
export const ScreenConversation: React.FC<ScreenConversationProps> = ({
  turns = DEFAULT_TURNS,
  replyPlaceholder = "Reply or ask follow-up",
  typingLabel = "typing",
  onSendMessage,
}) => {
  const [draft, setDraft] = React.useState("");
  const editable = onSendMessage !== undefined;

  const handleSubmit = (text: string) => {
    const trimmed = text.trim();
    if (!trimmed) return;
    void onSendMessage?.(trimmed);
    setDraft("");
  };

  return (
    <>
      <AppBar state="thinking" latency={218} />
      <div
        className="flex flex-1 flex-col overflow-auto"
        style={{ padding: "8px 18px 20px", gap: 16, minHeight: 0 }}
      >
        {turns.map((turn, i) => {
          if (turn.streaming) {
            return (
              <div
                key={i}
                className="flex flex-col"
                style={{ alignSelf: "flex-start", maxWidth: "88%" }}
              >
                <div className="mb-1.5 flex items-center gap-1.5">
                  <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
                    <path
                      d="M8 1.5l1.3 4.2 4.2 1.3-4.2 1.3L8 12.5 6.7 8.3 2.5 7l4.2-1.3L8 1.5z"
                      fill="var(--pc-accent)"
                    />
                  </svg>
                  <span
                    style={{
                      fontFamily: "'Geist Mono', ui-monospace, monospace",
                      fontSize: 10,
                      color: "var(--pc-text-dim)",
                    }}
                  >
                    {typingLabel}
                    <TypingDots />
                  </span>
                </div>
              </div>
            );
          }

          return (
            <div key={i} className="flex flex-col" style={{ gap: 8 }}>
              <ConversationBubble role={turn.role} meta={turn.meta}>
                {turn.text}
              </ConversationBubble>
              {turn.code && (
                <div
                  style={{
                    alignSelf: turn.role === "user" ? "flex-end" : "flex-start",
                    maxWidth: turn.role === "user" ? "82%" : "88%",
                    width: "100%",
                  }}
                >
                  <CodeBlock lines={turn.code} />
                </div>
              )}
              {turn.followUps && turn.followUps.length > 0 && (
                <div
                  className="flex flex-wrap"
                  style={{ gap: 6, alignSelf: "flex-start" }}
                >
                  {turn.followUps.map((s, j) => (
                    <span
                      key={j}
                      style={{
                        padding: "5px 10px",
                        borderRadius: 999,
                        fontSize: 11.5,
                        background: "var(--pc-chip-bg)",
                        border: "1px solid var(--pc-chip-border)",
                        color: "var(--pc-text-dim)",
                        fontFamily: "'Geist', ui-sans-serif, system-ui",
                      }}
                    >
                      {s}
                    </span>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>
      <AskBar
        state="idle"
        placeholder={replyPlaceholder}
        value={editable ? draft : undefined}
        onValueChange={editable ? setDraft : undefined}
        onSubmit={editable ? handleSubmit : undefined}
      />
    </>
  );
};

ScreenConversation.displayName = "ScreenConversation";
