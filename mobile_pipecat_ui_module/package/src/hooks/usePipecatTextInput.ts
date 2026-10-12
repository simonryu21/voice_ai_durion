import { usePipecatClient } from "@pipecat-ai/client-react";
import { useCallback } from "react";

import { usePipecatConnectionState } from "@/hooks/usePipecatConnectionState";
import { usePipecatConversation } from "@/hooks/usePipecatConversation";

/**
 * Options for `usePipecatTextInput`.
 */
export interface UsePipecatTextInputOptions {
  /**
   * Whether the bot should respond immediately after the typed message is
   * appended to the LLM context.
   * @default true
   */
  runImmediately?: boolean;
}

/**
 * Result of `usePipecatTextInput`.
 */
export interface UsePipecatTextInputResult {
  /**
   * Send a typed message to the bot. The text is surfaced locally as a user
   * message and appended to the LLM context to trigger a bot response.
   * Empty input or a disconnected client is a no-op.
   */
  sendTextMessage: (text: string) => Promise<void>;
  /** True when a connected client is available to receive text. */
  isReady: boolean;
}

/**
 * Bridges an AskBar (or any text field) to the active Pipecat session.
 *
 * `sendText` does not echo a user transcript back, so the typed
 * message is also injected into the conversation stream via
 * `usePipecatConversation` so it shows up in the UI alongside spoken turns.
 *
 * Must be used within a `PipecatClientProvider` + `ConversationProvider`
 * (both supplied by `PipecatAppBase`).
 *
 * Example::
 *
 *     const { sendTextMessage } = usePipecatTextInput();
 *     <AskBar value={text} onValueChange={setText} onSubmit={sendTextMessage} />
 */
export const usePipecatTextInput = ({
  runImmediately = true,
}: UsePipecatTextInputOptions = {}): UsePipecatTextInputResult => {
  const client = usePipecatClient();
  const { isConnected } = usePipecatConnectionState();
  const { injectMessage } = usePipecatConversation();

  const isReady = isConnected && !!client;

  const sendTextMessage = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      if (!trimmed || !client || !isConnected) return;

      injectMessage({
        role: "user",
        parts: [
          { text: trimmed, final: true, createdAt: new Date().toISOString() },
        ],
      });

      // NOTE: client.appendToContext()는 deprecated이며 "append-to-context" 메시지를
      // 보내는데, 현재 서버(pipecat RTVIProcessor)는 "send-text"만 처리한다. 그래서
      // appendToContext로는 텍스트가 LLM을 트리거하지 못해 무응답이었다.
      // sendText()는 "send-text"를 보내 _handle_send_text → LLMMessagesAppendFrame(run_llm)
      // 으로 이어져 LLM 실행 + TTS 응답까지 정상 동작한다.
      await client.sendText(trimmed, {
        run_immediately: runImmediately,
      });
    },
    [client, isConnected, injectMessage, runImmediately],
  );

  return { sendTextMessage, isReady };
};

export default usePipecatTextInput;
