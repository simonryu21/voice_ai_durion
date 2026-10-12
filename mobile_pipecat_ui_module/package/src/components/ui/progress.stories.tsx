import type { Story, StoryDefault } from "@ladle/react";
import { Badge } from "./badge";
import { Progress, type ProgressProps } from "./progress";

export default {
  title: "Primitives",
} satisfies StoryDefault;

export const Default: Story<ProgressProps> = ({ color, size, ...props }) => {
  const filteredProps = {
    color: color === null ? undefined : color,
    size: size === null ? undefined : size,
    ...props,
  };
  return (
    <div className="ladle-section-container">
      <h3 className="ladle-section-header">Progress</h3>
      <div className="ladle-section">
        <Progress {...filteredProps} className="h-4" />
        <Progress {...filteredProps} percent={50} className="h-4" />
        <Progress {...filteredProps} percent={100} className="h-4" />
      </div>
      <h3 className="ladle-section-header">Size</h3>
      <p>
        Convenience sizes. Can also be set with <code>className="w-*"</code>
      </p>
      <div className="ladle-section">
        <Progress {...filteredProps} percent={50} size="sm" className="h-4" />
        <Progress {...filteredProps} percent={50} size="lg" className="h-4" />
        <Progress {...filteredProps} percent={50} size="xl" className="h-4" />
        <Progress {...filteredProps} percent={50} size="half" className="h-4" />
      </div>
      <h3 className="ladle-section-header">Colors</h3>
      <div className="ladle-section">
        <Progress {...filteredProps} percent={50} color="secondary" />
        <Progress {...filteredProps} percent={50} color="destructive" />
        <Progress {...filteredProps} percent={50} color="warning" />
        <Progress {...filteredProps} percent={50} color="active" />
        <Progress {...filteredProps} percent={50} color="inactive" />
        <Progress {...filteredProps} percent={50} color="agent" />
        <Progress {...filteredProps} percent={50} color="client" />
      </div>
      <h3 className="ladle-section-header">Rounded</h3>
      <div className="ladle-section">
        <Progress
          {...filteredProps}
          percent={50}
          rounded
          className="h-4 w-full"
        />
      </div>
      <h3 className="ladle-section-header">Badge</h3>
      <div className="ladle-section">
        <Badge buttonSizing>
          <Progress
            {...filteredProps}
            percent={50}
            size="lg"
            className="h-[2px]"
          />
          Some Badge
        </Badge>
      </div>
    </div>
  );
};

Default.argTypes = {
  size: {
    options: ["default", "half", "xs", "sm", "lg", "xl"],
    control: { type: "select" },
    defaultValue: "default",
  },
};

Default.storyName = "Progress";
