import type { Story, StoryDefault } from "@ladle/react";
import { Caption } from "./Caption";
import { CodeBlock } from "./CodeBlock";
import { ConversationBubble } from "./ConversationBubble";
import { TypingDots } from "./TypingDots";

export default {
  title: "Pipecat Voice UI / Conversation",
} satisfies StoryDefault;

const Frame: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div
    className="dark"
    style={{
      minHeight: "100vh",
      padding: 32,
      background: "var(--pc-canvas)",
      color: "var(--pc-text)",
    }}
  >
    <div
      style={{
        maxWidth: 392,
        background: "var(--pc-bg-base)",
        borderRadius: 16,
        padding: "16px 18px",
        display: "flex",
        flexDirection: "column",
        gap: 16,
      }}
    >
      {children}
    </div>
  </div>
);

export const BubbleStory: Story = () => (
  <Frame>
    <ConversationBubble role="user" meta="you · 09:32">
      What's a good way to debounce a websocket reconnect loop in Python?
    </ConversationBubble>

    <ConversationBubble role="assistant" meta="pipecat · gemma-3-27b">
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
      , double on each failure, cap at ~30s, and add random jitter.
    </ConversationBubble>
  </Frame>
);
BubbleStory.storyName = "ConversationBubble · user + assistant";

export const CodeBlockStory: Story = () => (
  <Frame>
    <CodeBlock
      lines={[
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
      ]}
    />
    <CodeBlock
      lines={[
        { text: "// plain text without segments" },
        { text: "console.log('Hi')" },
      ]}
    />
  </Frame>
);
CodeBlockStory.storyName = "CodeBlock";

export const TypingDotsStory: Story = () => (
  <Frame>
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 6,
        fontFamily: "'Geist Mono', ui-monospace, monospace",
        fontSize: 10,
        color: "var(--pc-text-dim)",
      }}
    >
      typing
      <TypingDots />
    </div>
  </Frame>
);
TypingDotsStory.storyName = "TypingDots";

export const CaptionStory: Story = () => (
  <Frame>
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      <Caption text="" placeholder="탭하여 시작" />
      <Caption text="듣고 있어요" />
      <Caption text="주문하신 김치찌개는 12,000원입니다." />
    </div>
  </Frame>
);
CaptionStory.storyName = "Caption · static text override";
