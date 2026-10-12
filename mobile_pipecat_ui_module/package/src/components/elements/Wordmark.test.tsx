import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Wordmark } from "./Wordmark";

describe("Wordmark", () => {
  it("renders the 'pipecat' text by default", () => {
    render(<Wordmark />);
    expect(screen.getByText("pipecat")).toBeInTheDocument();
  });

  it("hides the text when `iconOnly` is set but keeps the logo tile", () => {
    const { container } = render(<Wordmark iconOnly />);
    expect(screen.queryByText("pipecat")).not.toBeInTheDocument();
    expect(container.querySelector("svg")).not.toBeNull();
  });

  it("keeps the logo tile decorative (aria-hidden)", () => {
    const { container } = render(<Wordmark />);
    expect(container.querySelector("svg")).toHaveAttribute(
      "aria-hidden",
      "true",
    );
  });
});
