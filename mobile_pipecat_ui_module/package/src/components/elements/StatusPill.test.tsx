import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { StatusPill, type StatusPillState } from "./StatusPill";

describe("StatusPill", () => {
  it("renders the default English label for every state", () => {
    const cases: Array<[StatusPillState, string]> = [
      ["idle", "ready"],
      ["listening", "listening"],
      ["thinking", "thinking"],
      ["speaking", "speaking"],
    ];
    for (const [state, label] of cases) {
      const { unmount } = render(<StatusPill state={state} />);
      expect(screen.getByText(label)).toBeInTheDocument();
      unmount();
    }
  });

  it("prefers an explicit `label` over the `labels` map and the default", () => {
    render(
      <StatusPill
        state="listening"
        label="듣는 중"
        labels={{ listening: "수신 중" }}
      />,
    );
    expect(screen.getByText("듣는 중")).toBeInTheDocument();
    expect(screen.queryByText("수신 중")).not.toBeInTheDocument();
  });

  it("translates via the `labels` map and falls back per-state", () => {
    const { rerender } = render(
      <StatusPill state="listening" labels={{ listening: "수신 중" }} />,
    );
    expect(screen.getByText("수신 중")).toBeInTheDocument();

    // `thinking` is omitted from the map → English default.
    rerender(<StatusPill state="thinking" labels={{ listening: "수신 중" }} />);
    expect(screen.getByText("thinking")).toBeInTheDocument();
  });

  it("shows the latency stat only when `latency` is provided", () => {
    const { rerender } = render(<StatusPill state="idle" />);
    expect(screen.queryByText(/ms$/)).not.toBeInTheDocument();

    rerender(<StatusPill state="idle" latency={142} />);
    expect(screen.getByText(/142ms/)).toBeInTheDocument();
  });

  it("marks the status dot as decorative (aria-hidden)", () => {
    const { container } = render(<StatusPill state="listening" />);
    const dot = container.querySelector('[aria-hidden="true"]');
    expect(dot).not.toBeNull();
  });
});
