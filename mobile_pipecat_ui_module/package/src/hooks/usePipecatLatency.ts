import { RTVIEvent } from "@pipecat-ai/client-js";
import { useRTVIClientEvent } from "@pipecat-ai/client-react";
import { useCallback, useRef, useState } from "react";

interface ProcessingMetric {
  processor: string;
  value: number;
}

interface MetricsPayload {
  ttfb?: ProcessingMetric[];
}

export interface UsePipecatLatencyOptions {
  /**
   * Smoothing factor for the EMA (exponential moving average).
   * 0 = no smoothing (always last sample), 1 = never updates.
   * @default 0.6
   */
  smoothing?: number;
  /** Restrict tracking to a single processor name. */
  processor?: string;
}

export interface UsePipecatLatencyResult {
  /** Smoothed latency in milliseconds, or null if no metrics have arrived. */
  latencyMs: number | null;
  /** Reset the running average back to null. */
  reset: () => void;
}

/**
 * Tracks Pipecat TTFB metrics and exposes a smoothed round-trip latency
 * suitable for the StatusPill / AppBar. Subscribes to `RTVIEvent.Metrics`
 * and folds in the max TTFB across processors (or a specific one) with an
 * EMA so the pill doesn't flicker on every event.
 */
export function usePipecatLatency({
  smoothing = 0.6,
  processor,
}: UsePipecatLatencyOptions = {}): UsePipecatLatencyResult {
  const [latencyMs, setLatencyMs] = useState<number | null>(null);
  const lastRef = useRef<number | null>(null);

  useRTVIClientEvent(
    RTVIEvent.Metrics,
    useCallback(
      (data: MetricsPayload) => {
        if (
          !data?.ttfb ||
          !Array.isArray(data.ttfb) ||
          data.ttfb.length === 0
        ) {
          return;
        }
        const sample = processor
          ? data.ttfb.find((m) => m.processor === processor)?.value
          : data.ttfb.reduce((max, m) => Math.max(max, m.value), 0);
        if (sample === undefined || sample <= 0) return;

        const ms = sample * 1000;
        const prev = lastRef.current;
        const next =
          prev == null ? ms : prev * smoothing + ms * (1 - smoothing);
        lastRef.current = next;
        setLatencyMs(Math.round(next));
      },
      [smoothing, processor],
    ),
  );

  const reset = useCallback(() => {
    lastRef.current = null;
    setLatencyMs(null);
  }, []);

  return { latencyMs, reset };
}
