import type { Story, StoryDefault } from "@ladle/react";
import { IconBtn } from "./IconBtn";
import { PcChip } from "./PcChip";
import { SuggestionGrid } from "./SuggestionGrid";
import { Wordmark } from "./Wordmark";

export default {
  title: "Pipecat Voice UI / Identity",
} satisfies StoryDefault;

const Frame: React.FC<{ children: React.ReactNode; dark?: boolean }> = ({
  children,
  dark = true,
}) => (
  <div
    className={dark ? "dark" : ""}
    style={{
      minHeight: "100vh",
      padding: 32,
      background: "var(--pc-canvas)",
      color: "var(--pc-text)",
      display: "flex",
      flexDirection: "column",
      gap: 24,
    }}
  >
    {children}
  </div>
);

export const WordmarkStory: Story = () => (
  <Frame>
    <div className="flex flex-col gap-6 items-start">
      <Wordmark />
      <Wordmark iconOnly />
      <div
        style={{
          background: "var(--pc-surface)",
          padding: 16,
          borderRadius: 12,
        }}
      >
        <Wordmark accent="#1976D2" />
      </div>
    </div>
  </Frame>
);
WordmarkStory.storyName = "Wordmark";

export const WordmarkLight: Story = () => (
  <Frame dark={false}>
    <Wordmark />
  </Frame>
);
WordmarkLight.storyName = "Wordmark (light)";

export const ChipStory: Story = () => (
  <Frame>
    <div className="flex flex-col gap-3 items-start">
      <PcChip>code</PcChip>
      <PcChip tone="accent">plan</PcChip>
      <PcChip mono={false}>follow-up · sans</PcChip>
    </div>
  </Frame>
);
ChipStory.storyName = "PcChip";

export const IconBtnStory: Story = () => (
  <Frame>
    <div className="flex gap-3 items-center">
      <IconBtn aria-label="default">●</IconBtn>
      <IconBtn aria-label="accent" tone="accent">
        ●
      </IconBtn>
      <IconBtn aria-label="ghost" tone="ghost">
        ●
      </IconBtn>
      <IconBtn aria-label="size 38" size={38} tone="accent">
        ●
      </IconBtn>
    </div>
  </Frame>
);
IconBtnStory.storyName = "IconBtn";

export const SuggestionGridStory: Story = () => (
  <Frame>
    <SuggestionGrid
      cards={[
        { tag: "code", title: "Explain this Python\ntraceback" },
        { tag: "plan", title: "Draft a 5-step\nlaunch checklist" },
        { tag: "voice", title: "Practice my\nstandup intro" },
        { tag: "data", title: "Summarize the\nattached CSV" },
      ]}
    />
  </Frame>
);
SuggestionGridStory.storyName = "SuggestionGrid";
