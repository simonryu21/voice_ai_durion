import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { AppBar } from "./AppBar";

// `autoLatency={false}` keeps AppBar out of the Pipecat client hooks so it
// can render standalone (the storybook-safe path, see migration guide §9).
describe("AppBar", () => {
  it("renders the wordmark and the status pill for the current state", () => {
    render(<AppBar state="listening" autoLatency={false} />);
    expect(screen.getByText("pipecat")).toBeInTheDocument();
    expect(screen.getByText("listening")).toBeInTheDocument();
  });

  it("exposes the default 'History' aria-label on the trailing icon", () => {
    render(<AppBar state="idle" autoLatency={false} />);
    expect(screen.getByRole("button", { name: "History" })).toBeInTheDocument();
  });

  it("uses a translated `historyLabel` when provided", () => {
    render(<AppBar state="idle" autoLatency={false} historyLabel="기록" />);
    expect(screen.getByRole("button", { name: "기록" })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "History" }),
    ).not.toBeInTheDocument();
  });

  it("fires `onHistoryClick` when the trailing icon is pressed", async () => {
    const user = userEvent.setup();
    const onHistoryClick = vi.fn();
    render(
      <AppBar
        state="idle"
        autoLatency={false}
        onHistoryClick={onHistoryClick}
      />,
    );
    await user.click(screen.getByRole("button", { name: "History" }));
    expect(onHistoryClick).toHaveBeenCalledOnce();
  });

  it("hides the history icon when `noHistory` is set", () => {
    render(<AppBar state="idle" autoLatency={false} noHistory />);
    expect(
      screen.queryByRole("button", { name: "History" }),
    ).not.toBeInTheDocument();
  });

  it("forwards an explicit `latency` to the status pill", () => {
    render(<AppBar state="thinking" autoLatency={false} latency={218} />);
    expect(screen.getByText(/218ms/)).toBeInTheDocument();
  });

  it("forwards translated `statusLabels` to the status pill", () => {
    render(
      <AppBar
        state="thinking"
        autoLatency={false}
        statusLabels={{ thinking: "생각 중" }}
      />,
    );
    expect(screen.getByText("생각 중")).toBeInTheDocument();
  });
});
