import { type ReactNode } from "react";

export interface ConversationMessagePart {
  text: string | ReactNode;
  final: boolean;
  createdAt: string;
}

export interface ConversationMessage {
  role: "user" | "assistant" | "system";
  final?: boolean;
  parts: ConversationMessagePart[];
  createdAt: string;
  updatedAt?: string;
  user_id?: string; // 화자 ID (회의 모드에서 여러 화자 구분용)
}

/**
 * Text mode for conversation display
 */
export type TextMode = "llm" | "tts";
