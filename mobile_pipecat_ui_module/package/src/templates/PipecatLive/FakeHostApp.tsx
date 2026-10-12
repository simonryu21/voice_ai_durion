import React from "react";

/**
 * Generic "Notes"-style host app used as a placeholder backdrop for the
 * Overlay and Bubble screens. Not a recreation of any branded app — only
 * exists so the floating overlays have a believable surface to sit on.
 */
export const FakeHostApp: React.FC = () => {
  return (
    <div
      className="flex flex-1 flex-col"
      style={{
        background: "var(--pc-bg-base)",
        color: "var(--pc-text)",
        fontFamily: "'Geist', ui-sans-serif, system-ui",
      }}
    >
      <div
        className="flex items-center justify-between"
        style={{
          padding: "14px 18px 12px",
          borderBottom: "1px solid var(--pc-border)",
        }}
      >
        <div className="flex items-center gap-2.5">
          <div
            className="flex items-center justify-center"
            style={{
              width: 26,
              height: 26,
              borderRadius: 7,
              background: "#2E7D6B",
              color: "#fff",
              fontSize: 13,
              fontWeight: 700,
              fontFamily: "'Geist', ui-sans-serif",
            }}
          >
            N
          </div>
          <span style={{ fontSize: 15, fontWeight: 600 }}>Notes</span>
        </div>
        <span style={{ fontSize: 22, color: "var(--pc-text-dim)" }}>+</span>
      </div>

      <div
        className="flex-1 overflow-hidden"
        style={{ padding: "18px 18px 0" }}
      >
        <div
          style={{
            fontFamily: "'Geist Mono', ui-monospace, monospace",
            fontSize: 10,
            letterSpacing: "0.08em",
            color: "var(--pc-text-faint)",
            textTransform: "uppercase",
          }}
        >
          FRI · 09:31
        </div>
        <div
          style={{
            marginTop: 6,
            fontSize: 22,
            fontWeight: 600,
            letterSpacing: "-0.01em",
            lineHeight: 1.2,
          }}
        >
          Pi deployment checklist
        </div>
        <div
          className="flex flex-col"
          style={{
            marginTop: 18,
            gap: 14,
            fontSize: 14,
            lineHeight: 1.55,
            color: "var(--pc-text-dim)",
          }}
        >
          {[
            ["1.", "Flash latest 64-bit Pi OS to SD card", true],
            ["2.", "Enable SSH + I²S audio in raspi-config", true],
            ["3.", "Install pipecat-ai + uvloop", true],
            ["4.", "Mount mic array, run device probe", false],
            ["5.", "Set systemd unit, point at WebSocket URL", false],
            ["6.", "Test wake-word in noisy room", false],
          ].map(([n, txt, done], i) => (
            <div key={i} className="flex" style={{ gap: 12 }}>
              <span
                style={{
                  fontFamily: "'Geist Mono', ui-monospace, monospace",
                  color: "var(--pc-text-faint)",
                  width: 18,
                }}
              >
                {n as string}
              </span>
              <span
                className="flex-1"
                style={{
                  color: done ? "var(--pc-text-faint)" : "var(--pc-text)",
                  textDecoration: done ? "line-through" : "none",
                }}
              >
                {txt as string}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

FakeHostApp.displayName = "FakeHostApp";
