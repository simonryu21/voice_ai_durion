import {
  type ConversationMessage,
  type ConversationMessagePart,
} from "@/types/conversation";
import { create } from "zustand";

interface ConversationState {
  messages: ConversationMessage[];
  messageCallbacks: Map<string, (message: ConversationMessage) => void>;
  // Store separate text streams for LLM and TTS
  llmTextStreams: Map<string, string>; // messageId -> accumulated LLM text
  ttsTextStreams: Map<string, string>; // messageId -> accumulated TTS text

  // Actions
  registerMessageCallback: (
    id: string,
    callback?: (message: ConversationMessage) => void,
  ) => void;
  unregisterMessageCallback: (id: string) => void;
  clearMessages: () => void;
  addMessage: (
    message: Omit<ConversationMessage, "createdAt" | "updatedAt">,
  ) => void;
  updateLastMessage: (
    role: "user" | "assistant",
    updates: Partial<ConversationMessage>,
  ) => void;
  finalizeLastMessage: (role: "user" | "assistant") => void;
  removeEmptyLastMessage: (role: "user" | "assistant") => void;
  injectMessage: (message: {
    role: "user" | "assistant" | "system";
    parts: ConversationMessagePart[];
    /** When set, prevents merge with adjacent user messages (e.g. handoff injection). */
    user_id?: string;
  }) => void;
  upsertUserTranscript: (
    text: string | React.ReactNode,
    final: boolean,
    user_id?: string,
  ) => void;
  updateAssistantText: (
    text: string,
    final: boolean,
    source: "llm" | "tts",
  ) => void;
  startAssistantLlmStream: () => void;
}

export const sortByCreatedAt = (
  a: ConversationMessage,
  b: ConversationMessage,
): number => {
  return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
};

export const isMessageEmpty = (message: ConversationMessage): boolean => {
  const parts = message.parts || [];
  if (parts.length === 0) return true;
  return parts.every((p) =>
    typeof p.text === "string" ? p.text.trim().length === 0 : false,
  );
};

export const filterEmptyMessages = (
  messages: ConversationMessage[],
): ConversationMessage[] => {
  return messages.filter((message, index, array) => {
    if (!isMessageEmpty(message)) return true;

    // For empty messages, keep only if no following non-empty message with same role
    const nextMessageWithSameRole = array
      .slice(index + 1)
      .find((m) => m.role === message.role && !isMessageEmpty(m));

    return !nextMessageWithSameRole;
  });
};

export const mergeMessages = (
  messages: ConversationMessage[],
): ConversationMessage[] => {
  const mergedMessages: ConversationMessage[] = [];

  for (let i = 0; i < messages.length; i++) {
    const currentMessage = messages[i];
    const lastMerged = mergedMessages[mergedMessages.length - 1];

    const timeDiff = lastMerged
      ? Math.abs(
          new Date(currentMessage.createdAt).getTime() -
            new Date(lastMerged.createdAt).getTime(),
        )
      : Infinity;

    // For user messages, also check user_id to prevent merging messages from different speakers
    const sameUserId =
      lastMerged?.role === "user" && currentMessage.role === "user"
        ? lastMerged.user_id === currentMessage.user_id
        : true; // For non-user messages or when user_id is not set, allow merging

    const shouldMerge =
      lastMerged &&
      lastMerged.role === currentMessage.role &&
      currentMessage.role !== "system" &&
      timeDiff < 30000 &&
      sameUserId; // Add user_id check

    if (shouldMerge) {
      mergedMessages[mergedMessages.length - 1] = {
        ...lastMerged,
        parts: [...(lastMerged.parts || []), ...(currentMessage.parts || [])],
        updatedAt: currentMessage.updatedAt || currentMessage.createdAt,
        final: currentMessage.final !== false,
      };
    } else {
      mergedMessages.push({ ...currentMessage });
    }
  }

  return mergedMessages;
};

// Helper function to call all registered callbacks
const callAllMessageCallbacks = (
  callbacks: Map<string, (message: ConversationMessage) => void>,
  message: ConversationMessage,
) => {
  callbacks.forEach((callback) => {
    try {
      callback(message);
    } catch (error) {
      console.error("Error in message callback:", error);
    }
  });
};

export const useConversationStore = create<ConversationState>()((set) => ({
  messages: [],
  messageCallbacks: new Map(),
  llmTextStreams: new Map(),
  ttsTextStreams: new Map(),

  registerMessageCallback: (id, callback) =>
    set((state) => {
      const newState = { ...state };
      newState.messageCallbacks.set(id, callback || (() => {}));
      return newState;
    }),

  unregisterMessageCallback: (id) =>
    set((state) => {
      const newState = { ...state };
      newState.messageCallbacks.delete(id);
      return newState;
    }),

  clearMessages: () =>
    set({
      messages: [],
      llmTextStreams: new Map(),
      ttsTextStreams: new Map(),
    }),

  addMessage: (messageData) => {
    const now = new Date();
    const message: ConversationMessage = {
      ...messageData,
      createdAt: now.toISOString(),
      updatedAt: now.toISOString(),
    };

    set((state) => {
      const updatedMessages = [...state.messages, message];
      const processedMessages = mergeMessages(
        filterEmptyMessages(updatedMessages.sort(sortByCreatedAt)),
      );

      callAllMessageCallbacks(state.messageCallbacks, message);
      return { messages: processedMessages };
    });
  },

  updateLastMessage: (role, updates) => {
    set((state) => {
      const messages = [...state.messages];
      const lastMessageIndex = messages.findLastIndex(
        (msg) => msg.role === role,
      );

      if (lastMessageIndex === -1) return state;

      const updatedMessage = {
        ...messages[lastMessageIndex],
        ...updates,
        updatedAt: new Date().toISOString(),
      } as ConversationMessage;

      messages[lastMessageIndex] = updatedMessage;
      const processedMessages = mergeMessages(
        filterEmptyMessages(messages.sort(sortByCreatedAt)),
      );

      callAllMessageCallbacks(state.messageCallbacks, updatedMessage);
      return { messages: processedMessages };
    });
  },

  finalizeLastMessage: (role) => {
    set((state) => {
      const messages = [...state.messages];
      const lastMessageIndex = messages.findLastIndex(
        (msg) => msg.role === role,
      );

      if (lastMessageIndex === -1) return state;

      const lastMessage = messages[lastMessageIndex];

      // Check if message is empty in both parts and text streams
      const hasTextInStreams =
        state.llmTextStreams.get(lastMessage.createdAt) ||
        state.ttsTextStreams.get(lastMessage.createdAt);

      if (isMessageEmpty(lastMessage) && !hasTextInStreams) {
        // Remove empty message only if it has no text in streams either
        messages.splice(lastMessageIndex, 1);
      } else {
        // Finalize message and its last part
        const parts = [...(lastMessage.parts || [])];
        if (parts.length > 0) {
          parts[parts.length - 1] = {
            ...parts[parts.length - 1],
            final: true,
          };
        }
        messages[lastMessageIndex] = {
          ...lastMessage,
          parts,
          final: true,
          updatedAt: new Date().toISOString(),
        };
        callAllMessageCallbacks(
          state.messageCallbacks,
          messages[lastMessageIndex],
        );
      }

      const processedMessages = mergeMessages(
        filterEmptyMessages(messages.sort(sortByCreatedAt)),
      );

      return { messages: processedMessages };
    });
  },

  removeEmptyLastMessage: (role) => {
    set((state) => {
      const messages = [...state.messages];
      const lastMessageIndex = messages.findLastIndex(
        (msg) => msg.role === role,
      );

      if (lastMessageIndex === -1) return state;

      const lastMessage = messages[lastMessageIndex];
      if (isMessageEmpty(lastMessage)) {
        messages.splice(lastMessageIndex, 1);
        const processedMessages = mergeMessages(
          filterEmptyMessages(messages.sort(sortByCreatedAt)),
        );
        return { messages: processedMessages };
      }

      return state;
    });
  },

  injectMessage: (messageData) => {
    const now = new Date();
    const message: ConversationMessage = {
      role: messageData.role,
      final: true,
      parts: [...messageData.parts],
      createdAt: now.toISOString(),
      updatedAt: now.toISOString(),
      ...(messageData.user_id !== undefined
        ? { user_id: messageData.user_id }
        : {}),
    };

    set((state) => {
      const updatedMessages = [...state.messages, message];
      const processedMessages = mergeMessages(
        filterEmptyMessages(updatedMessages.sort(sortByCreatedAt)),
      );

      callAllMessageCallbacks(state.messageCallbacks, message);
      return { messages: processedMessages };
    });
  },

  upsertUserTranscript: (text, final, user_id) => {
    //console.log(
    //  `[conversationStore] upsertUserTranscript called: user_id=${user_id ?? "undefined"}, final=${final}, text="${text.substring(0, 30)}..."`,
    //);
    const now = new Date();
    set((state) => {
      // First, merge messages to ensure consistent state
      const processedMessages = mergeMessages(
        filterEmptyMessages([...state.messages].sort(sortByCreatedAt)),
      );

      // Find last user message with the same user_id (if user_id is provided)
      // Prefer non-final messages for interim updates
      let lastUserIndex = -1;
      if (user_id !== undefined && user_id !== "") {
        // user_id가 제공된 경우: 같은 user_id를 가진 메시지 중 final이 아닌 것을 우선 찾음
        // 먼저 final이 아닌 메시지를 찾고, 없으면 final인 메시지를 찾음
        lastUserIndex = processedMessages.findLastIndex(
          (m) => m.role === "user" && m.user_id === user_id && !m.final,
        );
        if (lastUserIndex === -1) {
          lastUserIndex = processedMessages.findLastIndex(
            (m) => m.role === "user" && m.user_id === user_id,
          );
        }
      } else {
        // user_id가 없는 경우: 마지막 user 메시지 중 final이 아닌 것을 우선 찾음
        lastUserIndex = processedMessages.findLastIndex(
          (m) => m.role === "user" && !m.final,
        );
        if (lastUserIndex === -1) {
          lastUserIndex = processedMessages.findLastIndex(
            (m) => m.role === "user",
          );
        }
      }

      // Update existing user message if:
      // 1. There's an existing user message with the same user_id AND
      // 2. The message is not final yet (only update non-final messages)
      // 3. user_id가 일치하는 경우에만 업데이트 (다른 화자의 메시지는 업데이트하지 않음)
      // This prevents overwriting finalized messages with new interim updates
      // AND prevents overwriting messages from different speakers
      let shouldUpdate = false;
      if (lastUserIndex !== -1 && !processedMessages[lastUserIndex].final) {
        const foundMessage = processedMessages[lastUserIndex];
        if (user_id === undefined || user_id === "") {
          // user_id가 제공되지 않은 경우: 기존 동작 유지
          shouldUpdate = true;
        } else {
          // user_id가 제공된 경우: 반드시 일치해야 함
          // findLastIndex가 이미 같은 user_id를 가진 메시지만 찾도록 되어 있으므로,
          // lastUserIndex !== -1이면 반드시 일치함
          shouldUpdate = foundMessage.user_id === user_id;
        }
      }

      // Debug logging (always log, not just when user_id is provided)
      //const textPreview =
      //  typeof text === "string" && text.length > 0
      //    ? `${text.substring(0, 30)}...`
      //    : String(text);
      //console.log(
      //  `[upsertUserTranscript] user_id=${user_id ?? "undefined"}, lastUserIndex=${lastUserIndex}, ` +
      //    `foundMessageUserId=${lastUserIndex !== -1 ? (processedMessages[lastUserIndex]?.user_id ?? "undefined") : "none"}, ` +
      //    `foundMessageFinal=${lastUserIndex !== -1 ? processedMessages[lastUserIndex]?.final : "none"}, ` +
      //    `shouldUpdate=${shouldUpdate}, final=${final}, text="${textPreview}"`,
      //);

      if (shouldUpdate) {
        // Update existing user message
        const messages = [...processedMessages];
        const target = { ...messages[lastUserIndex] };
        const parts: ConversationMessagePart[] = Array.isArray(target.parts)
          ? [...target.parts]
          : [];

        const lastPart = parts[parts.length - 1];

        // 중복 체크: 같은 텍스트의 interim transcription이면 업데이트하지 않음
        if (
          final === false &&
          lastPart &&
          !lastPart.final &&
          lastPart.text === text
        ) {
          // 같은 텍스트의 interim transcription이면 업데이트하지 않음
          return { messages: processedMessages };
        }

        if (final === false) {
          // InterimTranscriptionFrame: 백엔드에서 이미 전체 누적 텍스트를 보냄
          // 이전 interim part를 완전히 교체 (덮어쓰기)
          if (lastPart && !lastPart.final) {
            // Update existing interim part
            parts[parts.length - 1] = {
              ...lastPart,
              text,
              final: false,
            };
          } else {
            // Start a new interim part (or replace final part if it exists)
            // If there's a final part, replace it with interim
            if (lastPart && lastPart.final) {
              parts[parts.length - 1] = {
                ...lastPart,
                text,
                final: false,
              };
            } else {
              parts.push({ text, final: false, createdAt: now.toISOString() });
            }
          }
        } else {
          // TranscriptionFrame (final=true): 최종 텍스트
          // 기존 interim part를 제거하고 최종 텍스트로 교체
          if (lastPart && !lastPart.final) {
            // Replace interim part with final part
            parts[parts.length - 1] = {
              ...lastPart,
              text,
              final: true,
            };
          } else {
            // Add new final part
            parts.push({ text, final: true, createdAt: now.toISOString() });
          }
        }

        const updatedMessage: ConversationMessage = {
          ...target,
          final: final ? true : target.final,
          parts,
          updatedAt: now.toISOString(),
          user_id: user_id !== undefined ? user_id : target.user_id, // user_id 유지 또는 업데이트
        };

        messages[lastUserIndex] = updatedMessage;

        // Process messages through mergeMessages and filterEmptyMessages
        const finalMessages = mergeMessages(
          filterEmptyMessages(messages.sort(sortByCreatedAt)),
        );

        callAllMessageCallbacks(state.messageCallbacks, updatedMessage);
        return { messages: finalMessages };
      }

      // Create a new user message initialized with this transcript
      // (새로운 화자이거나, 기존 메시지가 final인 경우)
      const newMessage: ConversationMessage = {
        role: "user",
        final,
        parts: [
          {
            text,
            final,
            createdAt: now.toISOString(),
          },
        ],
        createdAt: now.toISOString(),
        updatedAt: now.toISOString(),
        user_id: user_id, // 새로운 메시지에 user_id 저장
      };

      const updatedMessages = [...processedMessages, newMessage];
      const finalMessages = mergeMessages(
        filterEmptyMessages(updatedMessages.sort(sortByCreatedAt)),
      );
      callAllMessageCallbacks(state.messageCallbacks, newMessage);
      return { messages: finalMessages };
    });
  },

  updateAssistantText: (text, final, source) => {
    const now = new Date();
    set((state) => {
      const messages = [...state.messages];
      const llmTextStreams = new Map(state.llmTextStreams);
      const ttsTextStreams = new Map(state.ttsTextStreams);

      const lastInThread = messages[messages.length - 1];
      // If the latest message is not assistant (e.g. injected user before BotLlmStarted),
      // append text to the previous assistant — findLastIndex would pick the wrong bubble.
      if (!lastInThread || lastInThread.role !== "assistant") {
        const newAssistantId = now.toISOString();
        const newMessage: ConversationMessage = {
          role: "assistant",
          final,
          parts: [],
          createdAt: newAssistantId,
          updatedAt: now.toISOString(),
        };
        messages.push(newMessage);
      }

      const lastAssistantIndex = messages.findLastIndex(
        (msg) => msg.role === "assistant",
      );

      let messageId: string;

      if (lastAssistantIndex === -1) {
        // Create new assistant message
        messageId = now.toISOString();
        const newMessage: ConversationMessage = {
          role: "assistant",
          final,
          parts: [],
          createdAt: messageId,
          updatedAt: messageId,
        };
        messages.push(newMessage);
      } else {
        // Update existing assistant message (always the trailing assistant we write to)
        const lastMessage = messages[lastAssistantIndex];
        messageId = lastMessage.createdAt;

        messages[lastAssistantIndex] = {
          ...lastMessage,
          final: final ? true : lastMessage.final,
          updatedAt: now.toISOString(),
        };
      }

      // Update the appropriate text stream
      if (source === "llm") {
        const currentText = llmTextStreams.get(messageId) || "";
        llmTextStreams.set(messageId, currentText + text);
      } else {
        const currentText = ttsTextStreams.get(messageId) || "";
        // Add space between TTS chunks for proper word separation
        const separator =
          currentText && !currentText.endsWith(" ") && !text.startsWith(" ")
            ? " "
            : "";
        ttsTextStreams.set(messageId, currentText + separator + text);
      }

      // Don't filter out messages that have text in the text streams
      const processedMessages = mergeMessages(messages.sort(sortByCreatedAt));

      return {
        messages: processedMessages,
        llmTextStreams,
        ttsTextStreams,
      };
    });
  },

  startAssistantLlmStream: () => {
    set((state) => {
      const messages = [...state.messages];
      const llmTextStreams = new Map(state.llmTextStreams);
      const now = new Date();

      const lastIndex = messages.length - 1;
      // Get the last assistant message
      const lastAssistantIndex = messages.findLastIndex(
        (msg) => msg.role === "assistant",
      );
      const lastAssistant =
        lastAssistantIndex !== -1 ? messages[lastAssistantIndex] : undefined;

      // Check if the last assistant message is final
      if (!lastAssistant || lastIndex !== lastAssistantIndex) {
        // Create a new assistant message
        const newMessage: ConversationMessage = {
          role: "assistant",
          final: false,
          parts: [],
          createdAt: now.toISOString(),
          updatedAt: now.toISOString(),
        };
        messages.push(newMessage);
      } else if (lastIndex === lastAssistantIndex) {
        // The last assistant message is not final, so we need to add a space
        // to the LLM stream to separate it from the previous content
        const messageId = lastAssistant.createdAt;
        const currentText = llmTextStreams.get(messageId) || "";

        // Add a space if the current text doesn't end with one
        if (currentText && !currentText.endsWith(" ")) {
          llmTextStreams.set(messageId, currentText + " ");
        }

        messages[lastAssistantIndex] = {
          ...lastAssistant,
          final: false,
          updatedAt: now.toISOString(),
        };
      }

      const processedMessages = mergeMessages(
        filterEmptyMessages(messages.sort(sortByCreatedAt)),
      );

      return {
        messages: processedMessages,
        llmTextStreams,
      };
    });
  },
}));
