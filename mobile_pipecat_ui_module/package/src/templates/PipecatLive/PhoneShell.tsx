import { cn } from "@/lib/utils";
import React from "react";

export interface PhoneShellProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Phone width in px. @default 392 */
  width?: number;
  /** Phone height in px. @default 820 */
  height?: number;
  /** Show the status bar (time / signal / battery). @default true */
  withStatusBar?: boolean;
  /** Show the gesture nav home pill at the bottom. @default true */
  withNavBar?: boolean;
  /** Children render inside the inner safe area (between status bar and nav). */
  children: React.ReactNode;
}

const StatusBar: React.FC<{ color: string }> = ({ color }) => (
  <div
    className="relative flex items-center justify-between"
    style={{
      padding: "14px 24px 6px",
      fontFamily: "'Geist', system-ui",
      fontSize: 13,
      fontWeight: 600,
      color,
    }}
  >
    <span
      style={{
        fontFamily: "'Geist Mono', monospace",
        fontVariantNumeric: "tabular-nums",
      }}
    >
      9:32
    </span>
    {/* Camera punch-hole */}
    <div
      aria-hidden
      className="absolute rounded-full"
      style={{
        left: "50%",
        top: 8,
        transform: "translateX(-50%)",
        width: 26,
        height: 26,
        background: "#000",
      }}
    />
    <span className="inline-flex items-center" style={{ gap: 5 }}>
      <svg width="14" height="10" viewBox="0 0 14 10" fill={color}>
        <path d="M7 9.3L0 2.3a9.9 9.9 0 0 1 14 0L7 9.3z" />
      </svg>
      <svg width="14" height="10" viewBox="0 0 14 10" fill={color}>
        <rect x="1" y="6" width="2" height="3" rx="0.4" />
        <rect x="4.5" y="4" width="2" height="5" rx="0.4" />
        <rect x="8" y="2" width="2" height="7" rx="0.4" />
        <rect x="11.5" y="0" width="2" height="9" rx="0.4" />
      </svg>
      <svg width="22" height="11" viewBox="0 0 22 11">
        <rect
          x="0.5"
          y="0.5"
          width="18"
          height="10"
          rx="2.5"
          fill="none"
          stroke={color}
          strokeOpacity="0.5"
        />
        <rect
          x="20"
          y="3.5"
          width="1.5"
          height="4"
          rx="0.5"
          fill={color}
          opacity="0.5"
        />
        <rect x="2" y="2" width="13" height="7" rx="1.2" fill={color} />
      </svg>
    </span>
  </div>
);

const NavBar: React.FC<{ color: string }> = ({ color }) => (
  <div
    className="flex items-center justify-center"
    style={{ height: 22, paddingBottom: 6 }}
  >
    <div
      style={{
        width: 124,
        height: 4,
        borderRadius: 2,
        background: color,
        opacity: 0.5,
      }}
    />
  </div>
);

/**
 * Phone device shell that exactly matches the handoff `Phone` mock — outer
 * radius 44, inner radius 34, warm-black bezel, and a chrome status bar +
 * gesture nav pill that follows the current `--pc-text` for inversion.
 */
export const PhoneShell: React.FC<PhoneShellProps> = ({
  width = 392,
  height = 820,
  withStatusBar = true,
  withNavBar = true,
  className,
  style,
  children,
  ...rest
}) => {
  return (
    <div
      className={cn("relative overflow-hidden", className)}
      style={{
        width,
        height,
        borderRadius: 44,
        background: "var(--pc-bg-base)",
        border: "10px solid var(--pc-device-bezel)",
        boxShadow:
          "0 1px 0 rgba(255,255,255,0.05) inset, 0 30px 80px rgba(0,0,0,0.28), 0 6px 20px rgba(0,0,0,0.18)",
        ...style,
      }}
      {...rest}
    >
      <div
        className="flex h-full w-full flex-col overflow-hidden"
        style={{ borderRadius: 34, background: "var(--pc-bg-base)" }}
      >
        {withStatusBar && <StatusBar color="var(--pc-text)" />}
        <div className="flex min-h-0 flex-1 flex-col">{children}</div>
        {withNavBar && <NavBar color="var(--pc-text)" />}
      </div>
    </div>
  );
};

PhoneShell.displayName = "PhoneShell";
