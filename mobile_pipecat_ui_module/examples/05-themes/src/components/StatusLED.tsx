import { LED, type LEDProps } from "@pipecat-ai/voice-ui-kit";

export function StatusLED({
  label,
  ...ledProps
}: {
  label: string;
} & LEDProps) {
  return (
    <div className="flex items-center gap-3">
      <LED
        {...ledProps}
        className="terminal:rounded-none size-2"
        classNames={{
          off: ledProps.classNames?.off ?? "bg-primary/30",
          on: `${ledProps.classNames?.on ?? "bg-primary"} terminal:shadow-led`,
        }}
      />
      <span className="text-sm text-muted-foreground terminal-text">
        {label}
      </span>
    </div>
  );
}
