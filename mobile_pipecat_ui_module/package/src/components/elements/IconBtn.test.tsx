import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { IconBtn } from "./IconBtn";

describe("IconBtn", () => {
  it("exposes a forwarded aria-label and renders its children", () => {
    render(
      <IconBtn aria-label="History">
        <svg data-testid="icon" />
      </IconBtn>,
    );
    const button = screen.getByRole("button", { name: "History" });
    expect(button).toBeInTheDocument();
    expect(screen.getByTestId("icon")).toBeInTheDocument();
  });

  it("fires `onClick` when pressed", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <IconBtn aria-label="History" onClick={onClick}>
        <svg />
      </IconBtn>,
    );
    await user.click(screen.getByRole("button", { name: "History" }));
    expect(onClick).toHaveBeenCalledOnce();
  });

  it("does not fire `onClick` while disabled", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <IconBtn aria-label="History" disabled onClick={onClick}>
        <svg />
      </IconBtn>,
    );
    await user.click(screen.getByRole("button", { name: "History" }));
    expect(onClick).not.toHaveBeenCalled();
  });

  it("forwards a ref to the underlying button", () => {
    const ref = { current: null as HTMLButtonElement | null };
    render(
      <IconBtn aria-label="History" ref={ref}>
        <svg />
      </IconBtn>,
    );
    expect(ref.current).toBeInstanceOf(HTMLButtonElement);
  });
});
