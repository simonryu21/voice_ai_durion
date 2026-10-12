import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { AskBar } from "./AskBar";

describe("AskBar", () => {
  it("exposes default aria-labels for the attach, mic, and wave buttons", () => {
    render(<AskBar state="idle" />);
    expect(screen.getByRole("button", { name: "Attach" })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Start listening" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Voice mode" }),
    ).toBeInTheDocument();
  });

  it("swaps the mic button to the stop label while listening", () => {
    render(<AskBar state="listening" />);
    expect(
      screen.getByRole("button", { name: "Stop listening" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Start listening" }),
    ).not.toBeInTheDocument();
  });

  it("uses translated labels when provided", () => {
    render(
      <AskBar
        state="idle"
        attachLabel="첨부"
        micLabel="듣기 시작"
        waveLabel="음성 모드"
      />,
    );
    expect(screen.getByRole("button", { name: "첨부" })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "듣기 시작" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "음성 모드" }),
    ).toBeInTheDocument();
  });

  it("fires the mic, wave, and attach tap handlers", async () => {
    const user = userEvent.setup();
    const onMicTap = vi.fn();
    const onWaveTap = vi.fn();
    const onAttachTap = vi.fn();
    render(
      <AskBar
        state="idle"
        onMicTap={onMicTap}
        onWaveTap={onWaveTap}
        onAttachTap={onAttachTap}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Start listening" }));
    await user.click(screen.getByRole("button", { name: "Voice mode" }));
    await user.click(screen.getByRole("button", { name: "Attach" }));
    expect(onMicTap).toHaveBeenCalledOnce();
    expect(onWaveTap).toHaveBeenCalledOnce();
    expect(onAttachTap).toHaveBeenCalledOnce();
  });

  it("disables the mic button in the disabled state", async () => {
    const user = userEvent.setup();
    const onMicTap = vi.fn();
    render(<AskBar state="disabled" onMicTap={onMicTap} />);
    const mic = screen.getByRole("button", { name: "Start listening" });
    expect(mic).toBeDisabled();
    await user.click(mic);
    expect(onMicTap).not.toHaveBeenCalled();
  });

  it("stays visual-only (no text input) when `value` is undefined", () => {
    render(<AskBar state="idle" placeholder="Ask anything" />);
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(screen.getByText("Ask anything")).toBeInTheDocument();
  });

  it("renders a labelled text input when controlled", () => {
    render(<AskBar state="idle" value="" placeholder="Ask anything" />);
    expect(
      screen.getByRole("textbox", { name: "Ask anything" }),
    ).toBeInTheDocument();
  });

  it("fires `onValueChange` on keystrokes when controlled", async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    render(<AskBar state="idle" value="" onValueChange={onValueChange} />);
    await user.type(screen.getByRole("textbox"), "a");
    expect(onValueChange).toHaveBeenCalledWith("a");
  });

  it("fires `onSubmit` with the current value on Enter", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<AskBar state="idle" value="hello" onSubmit={onSubmit} />);
    await user.type(screen.getByRole("textbox"), "{Enter}");
    expect(onSubmit).toHaveBeenCalledWith("hello");
  });
});
