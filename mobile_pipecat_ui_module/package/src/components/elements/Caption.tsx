"use client";

import { usePipecatConversation } from "@/hooks/usePipecatConversation";
import { cn } from "@/lib/utils";
import { type ConversationMessage, type TextMode } from "@/types/conversation";
import React, { useEffect, useMemo, useState } from "react";

export interface CaptionProps
  extends React.HTMLAttributes<HTMLParagraphElement> {
  /** Whose latest message to show. @default "assistant" */
  role?: "assistant" | "user";
  /** LLM vs TTS text stream for assistant. @default "tts" */
  textMode?: TextMode;
  /** Override placeholder when no message yet. */
  placeholder?: string;
  /**
   * Optional explicit text. When provided, Caption renders this string and
   * skips the conversation subscription entirely — safe to use outside a
   * `PipecatClientProvider`.
   */
  text?: string;
}

function extractText(message: ConversationMessage | undefined): string {
  if (!message) return "";
  return message.parts
    .map((p) => (typeof p.text === "string" ? p.text : ""))
    .join(" ")
    .trim();
}

interface CaptionRenderProps extends Omit<CaptionProps, "role" | "textMode"> {
  resolvedText: string;
}

const CaptionRender: React.FC<CaptionRenderProps> = ({
  resolvedText,
  placeholder = "",
  className,
  // strip alias keys
  text: _text,
  ...rest
}) => {
  void _text;
  const [displayed, setDisplayed] = useState(resolvedText);
  const [visible, setVisible] = useState(Boolean(resolvedText));

  useEffect(() => {
    if (resolvedText === displayed) return;
    if (!resolvedText) {
      setVisible(false);
      const t = setTimeout(() => setDisplayed(""), 200);
      return () => clearTimeout(t);
    }
    setVisible(false);
    const fadeOut = setTimeout(() => {
      setDisplayed(resolvedText);
      setVisible(true);
    }, 120);
    return () => clearTimeout(fadeOut);
  }, [resolvedText, displayed]);

  const shown = displayed || placeholder;
  if (!shown) return null;

  return (
    <p
      className={cn(
        "px-6 text-center text-2xl font-medium tracking-tight",
        "transition-opacity duration-[var(--pc-motion-base)] ease-out",
        visible || displayed === "" ? "opacity-100" : "opacity-0",
        className,
      )}
      style={{ color: "var(--pc-text)" }}
      {...rest}
    >
      {shown}
    </p>
  );
};

const CaptionWithConversation: React.FC<CaptionProps> = ({
  role = "assistant",
  textMode = "tts",
  ...rest
}) => {
  const { messages } = usePipecatConversation({ textMode });
  const latest = useMemo(
    () => [...messages].reverse().find((m) => m.role === role),
    [messages, role],
  );
  const resolvedText = extractText(latest);
  return <CaptionRender {...rest} resolvedText={resolvedText} />;
};

/**
 * Single-line floating caption. When `text` is provided, renders it directly
 * (storybook-safe). Otherwise subscribes to `usePipecatConversation` for the
 * latest message of the given `role`.
 */
export const Caption: React.FC<CaptionProps> = ({ text, ...rest }) => {
  if (text !== undefined) {
    return <CaptionRender {...rest} text={text} resolvedText={text} />;
  }
  return <CaptionWithConversation {...rest} />;
};

Caption.displayName = "Caption";
