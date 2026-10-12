import { useConversationStore } from "@/stores/conversationStore";
import {
  type ConversationMessage,
  type ConversationMessagePart,
} from "@/types/conversation";
import { RTVIEvent } from "@pipecat-ai/client-js";
import { usePipecatClient, useRTVIClientEvent } from "@pipecat-ai/client-react";
import { createContext, useContext, useEffect, useRef } from "react";

const USER_0 = "user_0";

interface ConversationContextValue {
  messages: ConversationMessage[];
  injectMessage: (message: {
    role: "user" | "assistant" | "system";
    parts: ConversationMessagePart[];
    user_id?: string;
  }) => void;
}

const ConversationContext = createContext<ConversationContextValue | null>(
  null,
);

export const ConversationProvider = ({ children }: React.PropsWithChildren) => {
  const {
    messages,
    clearMessages,
    addMessage,
    finalizeLastMessage,
    removeEmptyLastMessage,
    injectMessage,
    upsertUserTranscript,
    updateAssistantText,
    startAssistantLlmStream,
  } = useConversationStore();

  const userStoppedTimeout = useRef<ReturnType<typeof setTimeout>>(undefined);
  const assistantStreamResetRef = useRef<number>(0);
  const client = usePipecatClient();

  // Direct subscription to user-transcription messages via client.on()
  // RTVIClient may emit user-transcription as a raw message type, not as RTVIEvent.UserTranscript
  useEffect(() => {
    if (!client) {
      console.log(
        "[ConversationProvider] client not available, skipping user-transcription subscription",
      );
      return;
    }

    const handleUserTranscription = (data: unknown) => {
      console.log(
        "[ConversationProvider] user-transcription message received via client.on()",
        data,
      );
      const messageData = data as {
        text?: string;
        final?: boolean;
        user_id?: string;
      };
      const text = messageData.text ?? "";
      const final = Boolean(messageData.final);
      const raw = (messageData.user_id ?? "").trim();
      const user_id =
        !raw || raw === "anonymous" || raw === "user_anonymous" ? USER_0 : raw;
      console.log(
        `[ConversationProvider] Processing user-transcription: user_id=${user_id}, final=${final}, text="${text.substring(0, 30)}..."`,
      );
      upsertUserTranscript(text, final, user_id);
      clearTimeout(userStoppedTimeout.current);
    };

    // Try to subscribe to user-transcription as a direct event name
    // Note: This may not work if RTVIClient doesn't expose it as an event
    try {
      // @ts-expect-error - user-transcription may not be in the type definitions
      client.on("user-transcription", handleUserTranscription);
      console.log(
        "[ConversationProvider] Successfully subscribed to user-transcription",
      );
    } catch (e) {
      console.warn(
        "[ConversationProvider] Could not subscribe to user-transcription:",
        e,
      );
    }

    return () => {
      try {
        // @ts-expect-error - user-transcription may not be in the type definitions
        client.off("user-transcription", handleUserTranscription);
        console.log(
          "[ConversationProvider] Unsubscribed from user-transcription",
        );
      } catch {
        // ignore cleanup errors
      }
    };
  }, [client, upsertUserTranscript]);

  useRTVIClientEvent(RTVIEvent.Connected, () => {
    clearMessages();
  });

  useRTVIClientEvent(RTVIEvent.BotLlmStarted, () => {
    startAssistantLlmStream();
    // Nudge a reset counter so any consumer logic can infer fresh turn if needed
    assistantStreamResetRef.current += 1;
  });

  useRTVIClientEvent(RTVIEvent.BotLlmText, (data) => {
    updateAssistantText(data.text, false, "llm");
  });

  useRTVIClientEvent(RTVIEvent.BotLlmStopped, () => {
    finalizeLastMessage("assistant");
  });

  useRTVIClientEvent(RTVIEvent.BotTtsStarted, () => {
    // Start a new assistant message for TTS if there isn't one already in progress
    const store = useConversationStore.getState();
    const lastAssistantIndex = store.messages.findLastIndex(
      (msg: ConversationMessage) => msg.role === "assistant",
    );
    const lastAssistant =
      lastAssistantIndex !== -1
        ? store.messages[lastAssistantIndex]
        : undefined;

    if (!lastAssistant || lastAssistant.final) {
      addMessage({
        role: "assistant",
        final: false,
        parts: [],
      });
    }
  });

  useRTVIClientEvent(RTVIEvent.BotTtsText, (data) => {
    updateAssistantText(data.text, false, "tts");
  });

  useRTVIClientEvent(RTVIEvent.BotTtsStopped, () => {
    // Finalize the TTS text stream
    const store = useConversationStore.getState();
    const lastAssistant = store.messages.findLast(
      (m: ConversationMessage) => m.role === "assistant",
    );

    if (lastAssistant && !lastAssistant.final) {
      finalizeLastMessage("assistant");
    }
  });

  useRTVIClientEvent(RTVIEvent.UserStartedSpeaking, () => {
    // Clear any pending cleanup timers
    clearTimeout(userStoppedTimeout.current);
  });

  useRTVIClientEvent(RTVIEvent.UserTranscript, (data) => {
    //console.log("[ConversationProvider] UserTranscript event triggered", data);
    const text = data.text ?? "";
    const final = Boolean(data.final);
    const raw = (data.user_id ?? "").trim();
    const user_id =
      !raw || raw === "anonymous" || raw === "user_anonymous" ? USER_0 : raw;
    //console.log(
    //  `[ConversationProvider] UserTranscript received: user_id=${user_id}, final=${final}, text="${text.substring(0, 30)}..."`,
    //);
    upsertUserTranscript(text, final, user_id);

    // If we got any transcript, cancel pending cleanup
    clearTimeout(userStoppedTimeout.current);
  });

  useRTVIClientEvent(RTVIEvent.UserStoppedSpeaking, () => {
    clearTimeout(userStoppedTimeout.current);
    // If no transcript ends up arriving, ensure any accidental empty placeholder is removed.
    userStoppedTimeout.current = setTimeout(() => {
      const lastUser = useConversationStore
        .getState()
        .messages.findLast((m: ConversationMessage) => m.role === "user");
      const hasParts =
        Array.isArray(lastUser?.parts) && lastUser!.parts.length > 0;
      if (!lastUser || !hasParts) {
        removeEmptyLastMessage("user");
      } else if (!lastUser.final) {
        finalizeLastMessage("user");
      }
    }, 3000);
  });

  const contextValue: ConversationContextValue = {
    messages,
    injectMessage,
  };

  return (
    <ConversationContext.Provider value={contextValue}>
      {children}
    </ConversationContext.Provider>
  );
};

export const useConversationContext = (): ConversationContextValue => {
  const context = useContext(ConversationContext);
  if (!context) {
    throw new Error(
      "useConversation must be used within a ConversationProvider",
    );
  }
  return context;
};
