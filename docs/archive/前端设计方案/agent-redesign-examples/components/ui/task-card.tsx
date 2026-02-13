import * as React from "react";
import { cn } from "@/lib/utils";
import { Badge } from "./badge";
import { ProgressIndicator, StatusIndicator } from "./status-indicator";

interface TaskCardProps {
  id: string | number;
  name: string;
  status: "pending" | "running" | "completed" | "failed" | "cancelled";
  progress?: number;
  duration?: string;
  agentName?: string;
  createdAt?: string;
  onClick?: () => void;
  className?: string;
}

function TaskCard({
  id,
  name,
  status,
  progress,
  duration,
  agentName,
  createdAt,
  onClick,
  className,
}: TaskCardProps) {
  const statusConfig = {
    pending: { variant: "default" as const, label: "等待中" },
    running: { variant: "primary" as const, label: "运行中" },
    completed: { variant: "success" as const, label: "已完成" },
    failed: { variant: "error" as const, label: "失败" },
    cancelled: { variant: "warning" as const, label: "已取消" },
  };

  return (
    <div
      onClick={onClick}
      className={cn(
        "p-4 rounded-lg border border-slate-200 bg-white transition-all",
        onClick && "cursor-pointer hover:border-primary-300 hover:shadow-md",
        className
      )}
    >
      <div className="flex items-start justify-between mb-3">
        <div className="flex-1 min-w-0">
          <h4 className="font-medium text-slate-900 text-truncate">{name}</h4>
          <div className="flex items-center gap-2 mt-1 text-xs text-slate-500">
            <span>#{id}</span>
            {agentName && (
              <>
                <span>·</span>
                <span>{agentName}</span>
              </>
            )}
            {createdAt && (
              <>
                <span>·</span>
                <span>{createdAt}</span>
              </>
            )}
          </div>
        </div>
        <Badge variant={statusConfig[status].variant}>{statusConfig[status].label}</Badge>
      </div>

      {status === "running" && progress !== undefined && (
        <ProgressIndicator progress={progress} size="sm" />
      )}

      {duration && status !== "running" && (
        <div className="flex items-center gap-2 text-xs text-slate-500">
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
          <span>耗时 {duration}</span>
        </div>
      )}
    </div>
  );
}

// 任务列表项（紧凑版）
interface TaskListItemProps {
  id: string | number;
  name: string;
  status: "pending" | "running" | "completed" | "failed" | "cancelled";
  nodeName?: string;
  duration?: string;
  onClick?: () => void;
  className?: string;
}

function TaskListItem({
  id,
  name,
  status,
  nodeName,
  duration,
  onClick,
  className,
}: TaskListItemProps) {
  const statusColors = {
    pending: "bg-slate-400",
    running: "bg-green-500 animate-pulse",
    completed: "bg-green-500",
    failed: "bg-red-500",
    cancelled: "bg-amber-500",
  };

  return (
    <div
      onClick={onClick}
      className={cn(
        "flex items-center gap-3 px-3 py-2.5 rounded-md transition-colors",
        onClick && "cursor-pointer hover:bg-slate-50",
        className
      )}
    >
      <span className={cn("w-2 h-2 rounded-full shrink-0", statusColors[status])} />
      <div className="flex-1 min-w-0">
        <div className="text-sm text-slate-900 text-truncate">{name}</div>
        <div className="flex items-center gap-2 text-xs text-slate-500">
          <span>#{id}</span>
          {nodeName && (
            <>
              <span>·</span>
              <span>{nodeName}</span>
            </>
          )}
        </div>
      </div>
      {duration && <span className="text-xs text-slate-400 shrink-0">{duration}</span>}
    </div>
  );
}

// 执行日志项
interface ExecutionLogItemProps {
  timestamp: string;
  level: "info" | "warning" | "error" | "debug";
  message: string;
  details?: string;
  className?: string;
}

function ExecutionLogItem({
  timestamp,
  level,
  message,
  details,
  className,
}: ExecutionLogItemProps) {
  const levelConfig = {
    info: { color: "text-blue-600 bg-blue-50", label: "INFO" },
    warning: { color: "text-amber-600 bg-amber-50", label: "WARN" },
    error: { color: "text-red-600 bg-red-50", label: "ERROR" },
    debug: { color: "text-slate-600 bg-slate-100", label: "DEBUG" },
  };

  const [expanded, setExpanded] = React.useState(false);

  return (
    <div className={cn("py-2 border-b border-slate-100 last:border-0", className)}>
      <div className="flex items-start gap-2">
        <span className="text-xs text-slate-400 shrink-0 font-mono">{timestamp}</span>
        <span
          className={cn(
            "px-1.5 py-0.5 rounded text-[10px] font-medium shrink-0",
            levelConfig[level].color
          )}
        >
          {levelConfig[level].label}
        </span>
        <span className="text-sm text-slate-700 flex-1">{message}</span>
        {details && (
          <button
            onClick={() => setExpanded(!expanded)}
            className="text-xs text-slate-400 hover:text-slate-600 shrink-0"
          >
            {expanded ? "收起" : "详情"}
          </button>
        )}
      </div>
      {expanded && details && (
        <div className="mt-2 ml-16 p-2 rounded bg-slate-50 text-xs text-slate-600 font-mono overflow-x-auto">
          {details}
        </div>
      )}
    </div>
  );
}

export { TaskCard, TaskListItem, ExecutionLogItem };
