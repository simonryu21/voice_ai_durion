import { cn } from "@/lib/utils";
import {
  ConversationMessage,
  ConversationMessagePart,
} from "@/types/conversation";
import { Fragment, ReactNode } from "react";
import ReactMarkdown from "react-markdown";
import Thinking from "./Thinking";

/**
 * URL을 감지하고 React 노드 배열로 변환하는 함수 (비마크다운 모드용)
 */
const linkifyText = (text: string): ReactNode[] => {
  const urlRegex =
    /(https?:\/\/[^\s<>"{}|\\^`[\]\]]+[^\s<>"{}|\\^`[\]\].,;:!?])/gi;
  const parts: ReactNode[] = [];
  let lastIndex = 0;
  let match;

  while ((match = urlRegex.exec(text)) !== null) {
    // URL 이전의 텍스트
    if (match.index > lastIndex) {
      parts.push(text.slice(lastIndex, match.index));
    }

    // URL을 링크로 변환
    const url = match[0];
    parts.push(
      <a
        key={match.index}
        href={url}
        className="text-blue-600 dark:text-blue-400 hover:underline break-all"
        target="_blank"
        rel="noopener noreferrer"
      >
        {url}
      </a>,
    );

    lastIndex = urlRegex.lastIndex;
  }

  // 남은 텍스트
  if (lastIndex < text.length) {
    parts.push(text.slice(lastIndex));
  }

  return parts.length > 0 ? parts : [text];
};

/**
 * 텍스트에서 URL을 마크다운 링크 형식으로 변환하는 함수 (마크다운 모드용)
 */
const linkifyTextForMarkdown = (text: string): string => {
  // 이미 마크다운 링크 형식인 부분을 보호하기 위한 정규식
  // [text](url) 또는 [text][ref] 형식은 건너뜀
  const markdownLinkRegex = /\[([^\]]+)\]\(([^)]+)\)/g;

  // 마크다운 링크가 아닌 URL만 찾는 정규식
  // 마크다운 링크 형식 안에 있는 URL은 제외
  const urlRegex =
    /(https?:\/\/[^\s<>"{}|\\^`[\]\]]+[^\s<>"{}|\\^`[\]\].,;:!?])/gi;

  // 먼저 마크다운 링크를 임시로 마킹
  const markdownLinks: Array<{ placeholder: string; original: string }> = [];
  let placeholderIndex = 0;

  let textWithPlaceholders = text.replace(markdownLinkRegex, (match) => {
    const placeholder = `__MARKDOWN_LINK_${placeholderIndex}__`;
    markdownLinks.push({ placeholder, original: match });
    placeholderIndex++;
    return placeholder;
  });

  // 마크다운 링크가 아닌 URL만 변환
  textWithPlaceholders = textWithPlaceholders.replace(urlRegex, (url) => {
    return `[${url}](${url})`;
  });

  // 마크다운 링크를 원래대로 복원
  markdownLinks.forEach(({ placeholder, original }) => {
    textWithPlaceholders = textWithPlaceholders.replace(placeholder, original);
  });

  return textWithPlaceholders;
};

interface Props {
  /**
   * Custom CSS classes for the component
   */
  classNames?: {
    /**
     * Custom CSS classes for the message content
     */
    messageContent?: string;
    /**
     * Custom CSS classes for the thinking
     */
    thinking?: string;
    /**
     * Custom CSS classes for the time
     */
    time?: string;
  };
  /**
   * The message to display
   */
  message: ConversationMessage;
  /**
   * Whether to render text as markdown
   * @default true
   */
  enableMarkdown?: boolean;
}

export const MessageContent = ({
  classNames = {},
  message,
  enableMarkdown = true,
}: Props) => {
  const parts = Array.isArray(message.parts) ? message.parts : [];
  return (
    <div className={cn("flex flex-col gap-2", classNames.messageContent)}>
      {parts.map((part: ConversationMessagePart, idx: number) => {
        const nextPart = parts?.[idx + 1] ?? null;
        const isText = typeof part.text === "string";
        const nextIsText = nextPart && typeof nextPart.text === "string";
        return (
          <Fragment key={idx}>
            {isText ? (
              enableMarkdown ? (
                <div className="prose prose-sm max-w-none dark:prose-invert">
                  <ReactMarkdown
                    components={{
                      p: ({ children }) => (
                        <p className="mb-2 last:mb-0">{children}</p>
                      ),
                      ul: ({ children }) => (
                        <ul className="list-disc pl-5 mb-2 last:mb-0">
                          {children}
                        </ul>
                      ),
                      ol: ({ children }) => (
                        <ol className="list-decimal pl-5 mb-2 last:mb-0">
                          {children}
                        </ol>
                      ),
                      li: ({ children }) => (
                        <li className="mb-1">{children}</li>
                      ),
                      code: ({ children, className }) => {
                        const isInline = !className;
                        return isInline ? (
                          <code className="bg-gray-100 dark:bg-gray-800 px-1 py-0.5 rounded text-sm font-mono">
                            {children}
                          </code>
                        ) : (
                          <code className={className}>{children}</code>
                        );
                      },
                      pre: ({ children }) => (
                        <pre className="bg-gray-100 dark:bg-gray-800 p-3 rounded-lg overflow-x-auto mb-2 last:mb-0">
                          {children}
                        </pre>
                      ),
                      blockquote: ({ children }) => (
                        <blockquote className="border-l-4 border-gray-300 dark:border-gray-600 pl-4 italic mb-2 last:mb-0">
                          {children}
                        </blockquote>
                      ),
                      a: ({ children, href }) => (
                        <a
                          href={href}
                          className="text-blue-600 dark:text-blue-400 hover:underline break-all"
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          {children}
                        </a>
                      ),
                      h1: ({ children }) => (
                        <h1 className="text-2xl font-bold mb-2 mt-4 first:mt-0">
                          {children}
                        </h1>
                      ),
                      h2: ({ children }) => (
                        <h2 className="text-xl font-bold mb-2 mt-4 first:mt-0">
                          {children}
                        </h2>
                      ),
                      h3: ({ children }) => (
                        <h3 className="text-lg font-bold mb-2 mt-4 first:mt-0">
                          {children}
                        </h3>
                      ),
                      strong: ({ children }) => (
                        <strong className="font-bold">{children}</strong>
                      ),
                      em: ({ children }) => (
                        <em className="italic">{children}</em>
                      ),
                    }}
                  >
                    {linkifyTextForMarkdown(part.text as string)}
                  </ReactMarkdown>
                </div>
              ) : (
                <>{linkifyText(part.text as string)}</>
              )
            ) : (
              part.text
            )}
            {isText && nextIsText && !enableMarkdown ? " " : null}
          </Fragment>
        );
      })}
      {parts.length === 0 ||
      parts.every(
        (part) => typeof part.text === "string" && part.text.trim() === "",
      ) ? (
        <Thinking className={classNames.thinking} />
      ) : null}
      <div
        className={cn("self-end text-xs text-gray-500 mb-1", classNames.time)}
      >
        {new Date(message.createdAt).toLocaleTimeString()}
      </div>
    </div>
  );
};
