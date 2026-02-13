import * as React from "react";
import { cn } from "@/lib/utils";

type StatusType = "default" | "success" | "warning" | "error" | "info" | "running" | "pending";

interface StatusIndicatorProps {
  status: StatusType;
  text?: string;
  size?: "sm" | "md" | "lg";
  animated?: boolean;
  className?: string;
}

const statusConfig: Record<StatusType, { color: string; label: string; animate?: boolean }> = {
  default: { color: "bg-slate-400", label: "默认" },
  success: { color: "bg-green-500", label: "成功" },
  warning: { color: "bg-amber-500", label: "警告" },
  error: { color: "bg-red-500", label: "错误" },
  info: { color: "bg-blue-500", label: "信息" },
  running: { color: "bg-green-500", label: "运行中", animate: true },
  pending: { color: "bg-slate-400", label: "等待中" },
};

const sizeConfig = {
  sm: "h-1.5 w-1.5",
  md: "h-2 w-2",
  lg: "h-2.5 w-2.5",
};

function StatusIndicator({
  status,
  text,
  size = "md",
  animated = true,
  className,
}: StatusIndicatorProps) {
  const config = statusConfig[status];
  const displayText = text || config.label;

  return (
    <div className={cn("inline-flex items-center gap-2", className)}>
      <span
        className={cn(
          "rounded-full",
          sizeConfig[size],
          config.color,
          config.animate && animated && "animate-pulse"
        )}
      />
      <span className="text-sm text-slate-600">{displayText}</span>
    </div>
  );
}

// 进度指示器
interface ProgressIndicatorProps {
  progress: number; // 0-100
  status?: "idle" | "running" | "paused" | "completed" | "error";
  showPercentage?: boolean;
  size?: "sm" | "md" | "lg";
  className?: string;
}

function ProgressIndicator({
  progress,
  status = "running",
  showPercentage = true,
  size = "md",
  className,
}: ProgressIndicatorProps) {
  const sizeClasses = {
    sm: "h-1",
    md: "h-2",
    lg: "h-3",
  };

  const statusColors = {
    idle: "bg-slate-200",
    running: "bg-primary-500",
    paused: "bg-amber-500",
    completed: "bg-green-500",
    error: "bg-red-500",
  };

  return (
    <div className={cn("w-full", className)}>
      <div className={cn("w-full rounded-full bg-slate-200", sizeClasses[size])}>
        <div
          className={cn(
            "rounded-full transition-all duration-300",
            sizeClasses[size],
            statusColors[status]
          )}
          style={{ width: `${Math.min(100, Math.max(0, progress))}%` }}
        />
      </div>
      {showPercentage && (
        <div className="mt-1 flex justify-between text-xs text-slate-500">
          <span>
            {status === "running" && "执行中..."}
            {status === "paused" && "已暂停"}
            {status === "completed" && "已完成"}
            {status === "error" && "执行失败"}
            {status === "idle" && "等待中"}
          </span>
          <span>{Math.round(progress)}%</span>
        </div>
      )}
    </div>
  );
}

// 步骤指示器
interface StepIndicatorProps {
  steps: { label: string; status: "pending" | "current" | "completed" | "error" }[];
  currentStep: number;
  className?: string;
}

function StepIndicator({ steps, currentStep, className }: StepIndicatorProps) {
  return (
    <div className={cn("flex items-center", className)}>
      {steps.map((step, index) => {
        const isLast = index === steps.length - 1;
        const status = step.status;

        return (
          <React.Fragment key={index}>
            <div className="flex items-center">
              <div
                className={cn(
                  "flex h-6 w-6 items-center justify-center rounded-full text-xs font-medium",
                  status === "completed" && "bg-green-500 text-white",
                  status === "current" && "bg-primary-600 text-white ring-2 ring-primary-200",
                  status === "pending" && "bg-slate-200 text-slate-500",
                  status === "error" && "bg-red-500 text-white"
                )}
              >
                {status === "completed" ? (
                  <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                  </svg>
                ) : status === "error" ? (
                  <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                ) : (
                  index + 1
                )}
              </div>
              <span
                className={cn(
                  "ml-2 text-sm",
                  status === "current" && "font-medium text-slate-900",
                  status === "completed" && "text-slate-600",
                  status === "pending" && "text-slate-400",
                  status === "error" && "text-red-600"
                )}
              >
                {step.label}
              </span>
            </div>
            {!isLast && (
              <div
                className={cn(
                  "mx-3 h-px w-8",
                  status === "completed" && steps[index + 1]?.status !== "pending"
                    ? "bg-green-500"
                    : "bg-slate-200"
                )}
              />
            )}
          </React.Fragment>
        );
      })}
    </div>
  );
}

export { StatusIndicator, ProgressIndicator, StepIndicator };
export type { StatusType };
