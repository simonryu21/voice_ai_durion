import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@pipecat-ai/voice-ui-kit";

export function Panel({
  children,
  className,
  ...props
}: {
  children: React.ReactNode;
  className?: string;
} & React.HTMLAttributes<HTMLDivElement>) {
  return (
    <Card
      className={`w-full text-primary terminal:bg-scanlines-inverted terminal:shadow-terminal ${className ?? ""}`}
      {...props}
    >
      {children}
    </Card>
  );
}

export function PanelTitle({
  children,
  ...props
}: {
  children: React.ReactNode;
} & React.HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h3
      className="text-primary terminal:uppercase terminal:tracking-widest terminal:font-bold flex flex-row items-center gap-2"
      {...props}
    >
      {children}
    </h3>
  );
}

export function PanelHeader({
  children,
  ...props
}: {
  children: React.ReactNode;
} & React.HTMLAttributes<HTMLDivElement>) {
  return (
    <CardHeader {...props}>
      <CardTitle className="relative border-b border-border pb-4 w-full flex flex-row items-center justify-between">
        {children}
      </CardTitle>
    </CardHeader>
  );
}

export function PanelFooter({
  children,
  ...props
}: {
  children: React.ReactNode;
} & React.HTMLAttributes<HTMLDivElement>) {
  return (
    <CardFooter {...props}>
      <div className="border-t border-border terminal:border-primary/40 pt-3 w-full flex flex-row items-center justify-between">
        {children}
      </div>
    </CardFooter>
  );
}

export function PanelContent({
  children,
  className,
  ...props
}: {
  children: React.ReactNode;
  className?: string;
} & React.HTMLAttributes<HTMLDivElement>) {
  return (
    <CardContent className={className} {...props}>
      {children}
    </CardContent>
  );
}
