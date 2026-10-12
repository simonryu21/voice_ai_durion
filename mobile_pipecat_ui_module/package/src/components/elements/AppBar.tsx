import { usePipecatLatency } from "@/hooks/usePipecatLatency";
import { cn } from "@/lib/utils";
import React from "react";
import { IconBtn } from "./IconBtn";
import {
  StatusPill,
  type StatusPillProps,
  type StatusPillState,
} from "./StatusPill";
import { Wordmark } from "./Wordmark";

export interface AppBarProps extends React.HTMLAttributes<HTMLDivElement> {
  state: StatusPillState;
  /**
   * Round-trip latency in ms. Pass to render the latency stat in StatusPill.
   * If omitted and `autoLatency` is true (default), subscribes to Pipecat
   * metrics and tracks TTFB automatically.
   */
  latency?: number;
  /**
   * Subscribe to Pipecat `RTVIEvent.Metrics` and render the smoothed TTFB
   * latency. Ignored when `latency` is provided. @default true
   */
  autoLatency?: boolean;
  /** Translated labels forwarded to the StatusPill. */
  statusLabels?: StatusPillProps["labels"];
  /** ARIA label for the trailing history icon. @default "History" */
  historyLabel?: string;
  /** Click handler for the trailing history icon. */
  onHistoryClick?: () => void;
  /** Hide the trailing history icon. */
  noHistory?: boolean;
}

const HistoryIcon = ({ size = 18 }: { size?: number }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M3 12a9 9 0 1 0 3-6.7" />
    <path d="M3 4v5h5M12 7v5l3 2" />
  </svg>
);

/**
 * Top app bar for the Pipecat Voice UI: Pipecat wordmark on the left,
 * StatusPill + trailing history button on the right, with a 1px bottom border.
 */
const AppBarBase: React.FC<
  Omit<AppBarProps, "autoLatency"> & { resolvedLatency?: number }
> = ({
  state,
  resolvedLatency,
  statusLabels,
  historyLabel = "History",
  onHistoryClick,
  noHistory,
  className,
  style,
  // strip unused alias keys before spreading
  latency: _latency,
  ...rest
}) => {
  void _latency;
  return (
    <div
      className={cn(
        "flex items-center justify-between",
        "px-[18px] pt-[14px] pb-[12px]",
        className,
      )}
      style={{
        borderBottom: "1px solid var(--pc-border)",
        ...style,
      }}
      {...rest}
    >
      <Wordmark />
      <div className="flex items-center gap-2.5">
        <StatusPill
          state={state}
          latency={resolvedLatency}
          labels={statusLabels}
        />
        {!noHistory && (
          <IconBtn onClick={onHistoryClick} aria-label={historyLabel}>
            <HistoryIcon />
          </IconBtn>
        )}
      </div>
    </div>
  );
};

const AppBarWithLatency: React.FC<Omit<AppBarProps, "autoLatency">> = (
  props,
) => {
  const auto = usePipecatLatency();
  const resolved = props.latency ?? auto.latencyMs ?? undefined;
  return <AppBarBase {...props} resolvedLatency={resolved} />;
};

/**
 * Top app bar. When `autoLatency` is true (default) and `latency` is not
 * explicitly provided, subscribes to Pipecat `RTVIEvent.Metrics` to render
 * a live TTFB stat. Set `autoLatency={false}` to render outside a
 * `PipecatClientProvider` (e.g. in storybook / static previews).
 */
export const AppBar: React.FC<AppBarProps> = ({
  autoLatency = true,
  ...rest
}) => {
  if (autoLatency) {
    return <AppBarWithLatency {...rest} />;
  }
  return <AppBarBase {...rest} resolvedLatency={rest.latency} />;
};

AppBar.displayName = "AppBar";
