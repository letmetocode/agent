import * as React from "react";
import { cn } from "@/lib/utils";
import { Badge } from "./badge";

interface MessageBubbleProps {
  role: "user" | "agent" | "system";
  content: string;
  timestamp?: string;
  citations?: { id: string; title: string; snippet?: string }[];
  isStreaming?: boolean;
  className?: string;
}

function MessageBubble({
  role,
  content,
  timestamp,
  citations,
  isStreaming = false,
  className,
}: MessageBubbleProps) {
  const isUser = role === "user";

  return (
    <div
      className={cn(
        "flex w-full",
        isUser ? "justify-end" : "justify-start",
        className
      )}
    >
      <div
        className={cn(
          "max-w-[85%] lg:max-w-[75%]",
          isUser ? "flex-row-reverse" : "flex-row"
        )}
      >
        {/* 头像 */}
        <div
          className={cn(
            "flex items-start gap-3",
            isUser ? "flex-row-reverse" : "flex-row"
          )}
        >
          <div
            className={cn(
              "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-medium",
              isUser
                ? "bg-primary-600 text-white"
                : "bg-slate-200 text-slate-700"
            )}
          >
            {isUser ? "U" : "AI"}
          </div>

          <div className="flex flex-col gap-1">
            {/* 消息内容 */}
            <div
              className={cn(
                "relative rounded-2xl px-4 py-3 text-sm leading-relaxed",
                isUser
                  ? "bg-primary-600 text-white rounded-br-md"
                  : "bg-white text-slate-900 rounded-bl-md border border-slate-200 shadow-sm"
              )}
            >
              {content}
              {isStreaming && (
                <span className="ml-1 inline-block animate-pulse">▋</span>
              )}
            </div>

            {/* 元信息 */}
            <div
              className={cn(
                "flex items-center gap-2 text-xs text-slate-400",
                isUser ? "justify-end" : "justify-start"
              )}
            >
              {timestamp && <span>{timestamp}</span>}
              {isUser && <span>已发送</span>}
            </div>

            {/* 引用来源 */}
            {citations && citations.length > 0 && (
              <div className="mt-2 space-y-2">
                <div className="text-xs font-medium text-slate-500">引用来源</div>
                <div className="flex flex-wrap gap-2">
                  {citations.map((citation) => (
                    <Badge
                      key={citation.id}
                      variant="outline"
                      className="cursor-pointer hover:bg-slate-50"
                    >
                      <svg
                        className="mr-1 h-3 w-3"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1"
                        />
                      </svg>
                      {citation.title}
                    </Badge>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

// 系统消息
interface SystemMessageProps {
  type: "info" | "warning" | "error" | "success";
  title?: string;
  content: string;
  className?: string;
}

function SystemMessage({ type, title, content, className }: SystemMessageProps) {
  const typeStyles = {
    info: "bg-blue-50 border-blue-200 text-blue-800",
    warning: "bg-amber-50 border-amber-200 text-amber-800",
    error: "bg-red-50 border-red-200 text-red-800",
    success: "bg-green-50 border-green-200 text-green-800",
  };

  const iconStyles = {
    info: "text-blue-500",
    warning: "text-amber-500",
    error: "text-red-500",
    success: "text-green-500",
  };

  const icons = {
    info: (
      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
        />
      </svg>
    ),
    warning: (
      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
        />
      </svg>
    ),
    error: (
      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
        />
      </svg>
    ),
    success: (
      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
        />
      </svg>
    ),
  };

  return (
    <div
      className={cn(
        "flex items-start gap-3 rounded-lg border p-3",
        typeStyles[type],
        className
      )}
    >
      <div className={cn("shrink-0", iconStyles[type])}>{icons[type]}</div>
      <div className="flex-1">
        {title && <div className="font-medium">{title}</div>}
        <div className="text-sm opacity-90">{content}</div>
      </div>
    </div>
  );
}

// 思考中指示器
function ThinkingIndicator({ className }: { className?: string }) {
  return (
    <div className={cn("flex items-center gap-2 text-sm text-slate-500", className)}>
      <div className="flex gap-1">
        <span className="h-2 w-2 animate-bounce rounded-full bg-slate-400 [animation-delay:-0.3s]" />
        <span className="h-2 w-2 animate-bounce rounded-full bg-slate-400 [animation-delay:-0.15s]" />
        <span className="h-2 w-2 animate-bounce rounded-full bg-slate-400" />
      </div>
      <span>Agent 正在思考...</span>
    </div>
  );
}

export { MessageBubble, SystemMessage, ThinkingIndicator };
