import { cn } from "@/lib/utils";
import { useMemo } from "react";

interface Props {
  /**
   * Custom label for assistant messages
   * @default "assistant"
   */
  assistantLabel?: string;
  /**
   * Custom label for user/client messages
   * @default "user"
   */
  clientLabel?: string;
  /**
   * Custom label for system messages
   * @default "system"
   */
  systemLabel?: string;
  /**
   * Custom CSS classes for the role label
   */
  className?: string;
  /**
   * The role of the message
   */
  role: "user" | "assistant" | "system";
  /**
   * Optional override label (already resolved upstream).
   * When provided, this is rendered verbatim.
   */
  labelOverride?: string;
  /**
   * Optional user ID for speaker diarization (e.g., "1", "2")
   * When provided for user messages, the label will be "user_1", "user_2", etc.
   */
  userId?: string;
}

/**
 * MessageRole component that displays the role of a message
 *
 * @example
 * ```tsx
 * import { MessageRole } from "@pipecat-ai/voice-ui-kit";
 *
 * <MessageRole role="assistant" />
 * ```
 */
export const MessageRole = ({
  assistantLabel = "assistant",
  clientLabel = "user",
  systemLabel = "system",
  className,
  role,
  labelOverride,
  userId,
}: Props) => {
  /**
   * Maps message roles to their display labels
   * @returns Object mapping role keys to display labels
   */
  const roleLabelMap = useMemo(
    () => ({
      user: clientLabel,
      assistant: assistantLabel,
      system: systemLabel,
    }),
    [assistantLabel, clientLabel, systemLabel],
  );

  // For user messages with userId, append the speaker ID (e.g., "user_1", "user_2")
  const displayLabel = useMemo(() => {
    if (labelOverride) return labelOverride;
    const baseLabel = roleLabelMap[role] || role;
    if (role === "user" && userId && userId !== "__webview_handoff__") {
      return `${baseLabel}_${userId}`;
    }
    return baseLabel;
  }, [labelOverride, role, userId, roleLabelMap]);

  return (
    <div
      className={cn(
        "font-semibold font-mono text-xs leading-6 w-max",
        {
          "text-client": role === "user",
          "text-agent": role === "assistant",
          "text-subtle": role === "system",
        },
        className,
      )}
    >
      {displayLabel}
    </div>
  );
};
