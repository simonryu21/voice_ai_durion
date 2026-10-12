import { RTVIEvent } from "@pipecat-ai/client-js";
import {
  usePipecatClientTransportState,
  useRTVIClientEvent,
} from "@pipecat-ai/client-react";
import {
  Button,
  DeviceSelect,
  Divider,
  Input,
  UserAudioControl,
} from "@pipecat-ai/voice-ui-kit";
import { useCallback, useState } from "react";
import {
  Panel,
  PanelContent,
  PanelFooter,
  PanelHeader,
  PanelTitle,
} from "./Panel";
import { StatusLED } from "./StatusLED";

export function Controls({ children }: { children?: React.ReactNode }) {
  const [speaking, setSpeaking] = useState(false);
  const transportState = usePipecatClientTransportState();
  useRTVIClientEvent(
    RTVIEvent.UserStartedSpeaking,
    useCallback(() => {
      setSpeaking(true);
    }, []),
  );
  useRTVIClientEvent(
    RTVIEvent.UserStoppedSpeaking,
    useCallback(() => {
      setSpeaking(false);
    }, []),
  );

  return (
    <Panel
      id="controls"
      className="w-full text-primary terminal:bg-scanlines terminal:shadow-terminal"
    >
      <PanelHeader>
        <PanelTitle>Control Interface</PanelTitle>
        <span className="terminal-text hidden terminal:static">{`[audio/text input]`}</span>
      </PanelHeader>
      <PanelContent className="flex-1 flex flex-col gap-4">
        <div className="flex flex-row gap-4">
          <div className="flex flex-col gap-2 flex-1">
            <label className="terminal-label">Audio device</label>
            <DeviceSelect
              variant="outline"
              size="lg"
              className="w-full terminal:text-base"
              classNames={{
                selectContent: "terminal:shadow-terminal",
              }}
            />
          </div>
          <div className="flex flex-col gap-2 flex-1">
            <label className="terminal-label">Audio device</label>
            <UserAudioControl
              variant="outline"
              noDevicePicker
              noIcon
              noVisualizer
              activeText="Mic on"
              inactiveText="Muted"
              size="lg"
              classNames={{
                button:
                  "justify-center transition-none terminal:bg-transparent terminal:uppercase terminal:hover:shadow-terminal",
                inactiveText: "flex-0 order-1",
                activeText: "flex-0 order-1",
              }}
            >
              ◉
            </UserAudioControl>
          </div>
        </div>
        <div className="flex flex-row gap-4">
          <div className="flex flex-col gap-2 flex-1">
            <label className="terminal-label">Text Input</label>
            <div className="flex flex-row gap-2">
              <Input
                variant="secondary"
                size="lg"
                disabled={transportState !== "ready"}
                className="w-full"
                placeholder="Type your message here..."
              />
              <Button
                variant="outline"
                size="lg"
                disabled={transportState !== "ready"}
                className="terminal:uppercase"
              >
                Transmit
              </Button>
            </div>
          </div>
        </div>
        <Divider className="terminal:bg-primary/20" />
        {children}
      </PanelContent>
      <PanelFooter>
        <div className="flex flex-row gap-6">
          <StatusLED
            label="Link"
            on={["ready", "connected"].includes(transportState)}
            classNames={{
              on: "bg-active",
            }}
          />
          <StatusLED label="Audio" on={speaking} />
          <StatusLED label="Text" />
        </div>
        <span className="terminal-text hidden terminal:static">
          Protocol: RTVI
        </span>
      </PanelFooter>
    </Panel>
  );
}
