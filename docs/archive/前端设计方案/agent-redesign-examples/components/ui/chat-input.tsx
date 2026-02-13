import * as React from "react";
import { cn } from "@/lib/utils";
import { Button } from "./button";

interface ChatInputProps {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  placeholder?: string;
  disabled?: boolean;
  loading?: boolean;
  minRows?: number;
  maxRows?: number;
  className?: string;
}

function ChatInput({
  value,
  onChange,
  onSubmit,
  placeholder = "输入你的问题...",
  disabled = false,
  loading = false,
  minRows = 2,
  maxRows = 6,
  className,
}: ChatInputProps) {
  const textareaRef = React.useRef<HTMLTextAreaElement>(null);

  // 自动调整高度
  const adjustHeight = () => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    textarea.style.height = "auto";
    const newHeight = Math.min(
      Math.max(textarea.scrollHeight, minRows * 24),
      maxRows * 24
    );
    textarea.style.height = `${newHeight}px`;
  };

  React.useEffect(() => {
    adjustHeight();
  }, [value]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      if (value.trim() && !loading && !disabled) {
        onSubmit();
      }
    }
  };

  return (
    <div
      className={cn(
        "relative flex items-end gap-2 rounded-xl border border-slate-200 bg-white p-3 shadow-sm focus-within:border-primary-400 focus-within:ring-2 focus-within:ring-primary-100 transition-all",
        disabled && "opacity-60 cursor-not-allowed",
        className
      )}
    >
      <textarea
        ref={textareaRef}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        disabled={disabled || loading}
        rows={minRows}
        className="flex-1 resize-none bg-transparent text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none disabled:cursor-not-allowed"
        style={{ minHeight: minRows * 24, maxHeight: maxRows * 24 }}
      />

      <div className="flex items-center gap-2">
        {/* 快捷操作 */}
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8 text-slate-400 hover:text-slate-600"
          disabled={disabled || loading}
        >
          <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13"
            />
          </svg>
        </Button>

        {/* 发送按钮 */}
        <Button
          onClick={onSubmit}
          disabled={!value.trim() || loading || disabled}
          size="icon"
          className="h-8 w-8 rounded-lg"
        >
          {loading ? (
            <svg className="animate-spin h-4 w-4" fill="none" viewBox="0 0 24 24">
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
              />
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
              />
            </svg>
          ) : (
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"
              />
            </svg>
          )}
        </Button>
      </div>
    </div>
  );
}

// 快捷命令面板
interface QuickCommand {
  id: string;
  label: string;
  description?: string;
  icon?: React.ReactNode;
}

interface QuickCommandsProps {
  commands: QuickCommand[];
  onSelect: (command: QuickCommand) => void;
  visible: boolean;
  className?: string;
}

function QuickCommands({ commands, onSelect, visible, className }: QuickCommandsProps) {
  if (!visible || commands.length === 0) return null;

  return (
    <div
      className={cn(
        "absolute bottom-full left-0 right-0 mb-2 rounded-lg border border-slate-200 bg-white shadow-lg overflow-hidden",
        className
      )}
    >
      <div className="px-3 py-2 text-xs font-medium text-slate-500 border-b border-slate-100">
        快捷命令
      </div>
      <div className="max-h-48 overflow-y-auto">
        {commands.map((command) => (
          <button
            key={command.id}
            onClick={() => onSelect(command)}
            className="w-full flex items-center gap-3 px-3 py-2.5 text-left hover:bg-slate-50 transition-colors"
          >
            {command.icon && <span className="text-slate-400">{command.icon}</span>}
            <div className="flex-1">
              <div className="text-sm text-slate-900">{command.label}</div>
              {command.description && (
                <div className="text-xs text-slate-500">{command.description}</div>
              )}
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}

export { ChatInput, QuickCommands };
export type { QuickCommand };
