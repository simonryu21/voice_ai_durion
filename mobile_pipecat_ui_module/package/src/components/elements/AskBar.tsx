import { cn } from "@/lib/utils";
import React from "react";

export type AskBarState = "idle" | "listening" | "disabled";

export interface AskBarProps
  extends Omit<React.HTMLAttributes<HTMLDivElement>, "onChange" | "onSubmit"> {
  state: AskBarState;
  /** Placeholder shown when not listening. @default "Ask anything" */
  placeholder?: string;
  /** Text shown next to the mic caret while listening. @default "listening" */
  listeningLabel?: string;
  /** ARIA label for the leading plus icon. @default "Attach" */
  attachLabel?: string;
  /** ARIA label for the mic button in idle state. @default "Start listening" */
  micLabel?: string;
  /** ARIA label for the mic button in listening state. @default "Stop listening" */
  stopLabel?: string;
  /** ARIA label for the trailing wave button. @default "Voice mode" */
  waveLabel?: string;
  /**
   * Controlled text value. When defined, the placeholder slot becomes a
   * real text input. Leave undefined for visual-only AskBar.
   */
  value?: string;
  /** Fired on every keystroke when controlled. */
  onValueChange?: (value: string) => void;
  /**
   * Fired when the user presses Enter (no Shift) in the input. Receives the
   * current value before it's cleared. Defaults to no-op.
   */
  onSubmit?: (value: string) => void;
  /** Auto-focus the text input on mount when controlled. */
  autoFocusInput?: boolean;
  /**
   * Render the mic button in its active (accent) style regardless of
   * `state`. Use to reflect a live microphone while keeping the text input
   * usable (`state="idle"`). Ignored — implied true — while listening.
   */
  micActive?: boolean;
  /** Hide the leading attach (plus) button. @default false */
  noAttach?: boolean;
  /** Hide the trailing wave (voice mode) button. @default false */
  noWave?: boolean;
  /** Fired when the user clicks the mic/stop button. */
  onMicTap?: () => void;
  /** Fired when the user clicks the trailing wave (voice mode) button. */
  onWaveTap?: () => void;
  /** Optional click handler on the leading plus icon. */
  onAttachTap?: () => void;
}

const Icon = {
  Plus: ({ size = 20 }: { size?: number }) => (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  ),
  Mic: ({ size = 18 }: { size?: number }) => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <rect x="9" y="3" width="6" height="12" rx="3" fill="currentColor" />
      <path
        d="M5 11a7 7 0 0 0 14 0M12 18v3"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
      />
    </svg>
  ),
  Wave: ({ size = 18 }: { size?: number }) => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path
        d="M4 12v0M8 8v8M12 4v16M16 8v8M20 12v0"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
      />
    </svg>
  ),
  Stop: ({ size = 16 }: { size?: number }) => (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="currentColor">
      <rect x="4" y="4" width="8" height="8" rx="1.5" />
    </svg>
  ),
};

const MicCaret: React.FC = () => (
  <span
    aria-hidden
    style={{
      display: "inline-block",
      width: 2,
      height: 14,
      marginLeft: 2,
      background: "var(--pc-accent)",
      verticalAlign: "-2px",
      animation: "pc-blink-caret 1s steps(2) infinite",
    }}
  />
);

/**
 * Bottom input pill matching the handoff `AskBar`. Hosts a leading attach
 * icon, a single-line placeholder/listening label, a Mic/Stop toggle, and
 * a trailing Wave (voice mode) button. When `state === "listening"` the
 * outer border picks up the accent color and a soft ring is rendered.
 */
export const AskBar: React.FC<AskBarProps> = ({
  state,
  placeholder = "Ask anything",
  listeningLabel = "listening",
  attachLabel = "Attach",
  micLabel = "Start listening",
  stopLabel = "Stop listening",
  waveLabel = "Voice mode",
  value,
  onValueChange,
  onSubmit,
  autoFocusInput,
  micActive = false,
  noAttach = false,
  noWave = false,
  onMicTap,
  onWaveTap,
  onAttachTap,
  className,
  style,
  ...rest
}) => {
  const listening = state === "listening";
  const disabled = state === "disabled";
  const controlled = value !== undefined;
  // The mic button picks up its accent style while listening or whenever the
  // microphone is reported active by the host.
  const micOn = listening || micActive;

  return (
    <div className={cn("px-3.5 pb-3.5", className)} style={style} {...rest}>
      <div
        className="flex items-center gap-2.5"
        style={{
          padding: "8px 8px 8px 14px",
          borderRadius: 28,
          background: "var(--pc-input-bg)",
          border: `1.5px solid ${
            listening ? "var(--pc-accent)" : "var(--pc-border)"
          }`,
          boxShadow: listening
            ? "0 0 0 4px color-mix(in srgb, var(--pc-accent) 13%, transparent), 0 8px 24px rgba(0,0,0,0.18)"
            : "0 4px 16px rgba(0,0,0,0.12)",
          transition:
            "border-color var(--pc-motion-base), box-shadow var(--pc-motion-base)",
          opacity: disabled ? 0.5 : 1,
        }}
      >
        {!noAttach && (
          <button
            type="button"
            onClick={onAttachTap}
            aria-label={attachLabel}
            className="cursor-pointer border-0 bg-transparent p-0"
            style={{ color: "var(--pc-text-dim)" }}
          >
            <Icon.Plus />
          </button>
        )}

        {listening || !controlled ? (
          <span
            className="flex-1 text-[15px]"
            style={{
              color: listening ? "var(--pc-text)" : "var(--pc-text-faint)",
              fontFamily:
                "'Geist', ui-sans-serif, system-ui, -apple-system, sans-serif",
            }}
          >
            {listening ? (
              <>
                {listeningLabel}
                <MicCaret />
              </>
            ) : (
              placeholder
            )}
          </span>
        ) : (
          <input
            type="text"
            value={value}
            onChange={(e) => onValueChange?.(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                onSubmit?.(value ?? "");
              }
            }}
            placeholder={placeholder}
            disabled={disabled}
            autoFocus={autoFocusInput}
            aria-label={placeholder}
            className="flex-1 border-0 bg-transparent outline-none text-[15px]"
            style={{
              color: "var(--pc-text)",
              fontFamily:
                "'Geist', ui-sans-serif, system-ui, -apple-system, sans-serif",
            }}
          />
        )}

        <button
          type="button"
          onClick={onMicTap}
          disabled={disabled}
          aria-label={micOn ? stopLabel : micLabel}
          className="inline-flex items-center justify-center border-0 cursor-pointer disabled:cursor-not-allowed"
          style={{
            width: 38,
            height: 38,
            borderRadius: "50%",
            background: micOn ? "var(--pc-accent)" : "var(--pc-chip-bg)",
            color: micOn ? "#fff" : "var(--pc-text)",
            boxShadow: micOn
              ? "0 6px 18px color-mix(in srgb, var(--pc-accent) 40%, transparent)"
              : "none",
            transition: "background var(--pc-motion-base)",
          }}
        >
          {listening ? <Icon.Stop /> : <Icon.Mic />}
        </button>

        {!noWave && (
          <button
            type="button"
            onClick={onWaveTap}
            aria-label={waveLabel}
            className="inline-flex items-center justify-center border-0 cursor-pointer"
            style={{
              width: 38,
              height: 38,
              borderRadius: "50%",
              background: "var(--pc-chip-bg)",
              color: "var(--pc-text)",
            }}
          >
            <Icon.Wave />
          </button>
        )}
      </div>
    </div>
  );
};

AskBar.displayName = "AskBar";
