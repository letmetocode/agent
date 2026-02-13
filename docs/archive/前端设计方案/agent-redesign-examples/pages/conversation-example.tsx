/**
 * 对话页示例 - 展示新设计的组件使用方式
 */
import * as React from "react";
import { cn } from "@/lib/utils";

// 布局组件
import { CompactSidebar } from "@/components/layout/sidebar";

// UI 组件
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ChatInput } from "@/components/ui/chat-input";
import { MessageBubble, SystemMessage, ThinkingIndicator } from "@/components/ui/message-bubble";
import { TaskListItem, ExecutionLogItem } from "@/components/ui/task-card";
import { ProgressIndicator, StepIndicator } from "@/components/ui/status-indicator";
import { EmptySessions } from "@/components/ui/empty-state";
import { MessageSkeleton } from "@/components/ui/skeleton";

// 模拟数据
const mockMessages = [
  {
    id: 1,
    role: "user" as const,
    content: "帮我分析一下这个月的销售数据",
    timestamp: "10:30",
  },
  {
    id: 2,
    role: "agent" as const,
    content: "我来帮您分析销售数据。首先让我获取相关数据...",
    timestamp: "10:30",
    citations: [
      { id: "c1", title: "销售报表.xlsx" },
      { id: "c2", title: "客户数据.csv" },
    ],
  },
];

const mockTasks = [
  { id: 1, name: "获取销售数据", status: "completed" as const, nodeName: "data_fetch", duration: "2s" },
  { id: 2, name: "数据清洗", status: "completed" as const, nodeName: "data_clean", duration: "3s" },
  { id: 3, name: "趋势分析", status: "running" as const, nodeName: "analysis", duration: "5s" },
  { id: 4, name: "生成报告", status: "pending" as const, nodeName: "report" },
];

const mockLogs = [
  { timestamp: "10:30:15", level: "info" as const, message: "开始获取销售数据" },
  { timestamp: "10:30:17", level: "info" as const, message: "成功获取 1,234 条记录" },
  { timestamp: "10:30:20", level: "debug" as const, message: "数据清洗完成", details: "移除了 23 条无效记录" },
];

const planSteps = [
  { label: "数据获取", status: "completed" as const },
  { label: "数据清洗", status: "completed" as const },
  { label: "趋势分析", status: "current" as const },
  { label: "生成报告", status: "pending" as const },
];

export default function ConversationPage() {
  const [inputValue, setInputValue] = React.useState("");
  const [isLoading, setIsLoading] = React.useState(false);
  const [rightPanelTab, setRightPanelTab] = React.useState<"tasks" | "logs" | "plan">("tasks");

  const handleSend = () => {
    if (!inputValue.trim()) return;
    setIsLoading(true);
    // 模拟发送
    setTimeout(() => {
      setIsLoading(false);
      setInputValue("");
    }, 2000);
  };

  return (
    <div className="flex h-screen bg-slate-50">
      {/* 左侧边栏 - 回合/Plan 历史 */}
      <CompactSidebar
        title="Session #12345"
        subtitle="销售数据分析"
        headerAction={
          <Button variant="ghost" size="sm">
            返回
          </Button>
        }
        className="w-64 shrink-0"
      >
        <div className="space-y-4">
          {/* 回合列表 */}
          <div>
            <h4 className="text-xs font-medium text-slate-500 mb-2">回合历史</h4>
            <div className="space-y-1">
              <button className="w-full text-left px-3 py-2 rounded-md bg-primary-50 text-primary-700 text-sm font-medium">
                Turn #3 (当前)
              </button>
              <button className="w-full text-left px-3 py-2 rounded-md text-slate-600 hover:bg-slate-50 text-sm">
                Turn #2
              </button>
              <button className="w-full text-left px-3 py-2 rounded-md text-slate-600 hover:bg-slate-50 text-sm">
                Turn #1
              </button>
            </div>
          </div>

          {/* Plan 历史 */}
          <div>
            <h4 className="text-xs font-medium text-slate-500 mb-2">Plan 历史</h4>
            <div className="space-y-1">
              <div className="flex items-center gap-2 px-3 py-2 rounded-md bg-primary-50">
                <Badge variant="primary" className="text-[10px]">运行中</Badge>
                <span className="text-sm text-primary-700">#456</span>
              </div>
              <div className="flex items-center gap-2 px-3 py-2 rounded-md hover:bg-slate-50 cursor-pointer">
                <Badge variant="success" className="text-[10px]">已完成</Badge>
                <span className="text-sm text-slate-600">#455</span>
              </div>
              <div className="flex items-center gap-2 px-3 py-2 rounded-md hover:bg-slate-50 cursor-pointer">
                <Badge variant="error" className="text-[10px]">失败</Badge>
                <span className="text-sm text-slate-600">#454</span>
              </div>
            </div>
          </div>
        </div>
      </CompactSidebar>

      {/* 主对话区 */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* 头部 */}
        <div className="h-14 flex items-center justify-between px-6 border-b border-slate-200 bg-white">
          <div className="flex items-center gap-3">
            <h2 className="font-semibold text-slate-900">销售数据分析</h2>
            <Badge variant="primary" dot dotColor="success">
              运行中
            </Badge>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="ghost" size="sm">
              <svg className="h-4 w-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
              </svg>
              分享
            </Button>
            <Button variant="ghost" size="sm">
              <svg className="h-4 w-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
              导出
            </Button>
          </div>
        </div>

        {/* 消息列表 */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {mockMessages.map((msg) => (
            <MessageBubble
              key={msg.id}
              role={msg.role}
              content={msg.content}
              timestamp={msg.timestamp}
              citations={msg.citations}
            />
          ))}

          {/* 系统消息 */}
          <SystemMessage
            type="info"
            title="Plan #456 已启动"
            content="预计执行时间 30-45 秒，您可以实时查看执行进度"
          />

          {/* 思考中 */}
          {isLoading && <ThinkingIndicator />}

          {/* 骨架屏 */}
          {isLoading && <MessageSkeleton />}
        </div>

        {/* 输入区 */}
        <div className="p-4 border-t border-slate-200 bg-white">
          <ChatInput
            value={inputValue}
            onChange={setInputValue}
            onSubmit={handleSend}
            placeholder="输入你的问题，按 Enter 发送..."
            loading={isLoading}
          />
          <div className="mt-2 text-xs text-slate-400 text-center">
            Agent 可能会产生不准确的信息，请验证重要信息。
          </div>
        </div>
      </div>

      {/* 右侧面板 */}
      <div className="w-80 shrink-0 border-l border-slate-200 bg-white flex flex-col">
        {/* 标签切换 */}
        <div className="flex border-b border-slate-200">
          {[
            { key: "tasks", label: "任务" },
            { key: "logs", label: "日志" },
            { key: "plan", label: "流程" },
          ].map((tab) => (
            <button
              key={tab.key}
              onClick={() => setRightPanelTab(tab.key as typeof rightPanelTab)}
              className={cn(
                "flex-1 py-3 text-sm font-medium transition-colors",
                rightPanelTab === tab.key
                  ? "text-primary-600 border-b-2 border-primary-600"
                  : "text-slate-500 hover:text-slate-700"
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* 面板内容 */}
        <div className="flex-1 overflow-y-auto p-4">
          {rightPanelTab === "tasks" && (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-slate-700">任务列表</span>
                <Badge variant="primary">3/4</Badge>
              </div>
              <ProgressIndicator progress={75} size="sm" />
              <div className="space-y-1">
                {mockTasks.map((task) => (
                  <TaskListItem
                    key={task.id}
                    id={task.id}
                    name={task.name}
                    status={task.status}
                    nodeName={task.nodeName}
                    duration={task.duration}
                  />
                ))}
              </div>
            </div>
          )}

          {rightPanelTab === "logs" && (
            <div className="space-y-1">
              {mockLogs.map((log, index) => (
                <ExecutionLogItem
                  key={index}
                  timestamp={log.timestamp}
                  level={log.level}
                  message={log.message}
                  details={log.details}
                />
              ))}
            </div>
          )}

          {rightPanelTab === "plan" && (
            <div className="space-y-4">
              <StepIndicator steps={planSteps} currentStep={2} />
              <div className="p-3 rounded-lg bg-slate-50 text-sm text-slate-600">
                <div className="font-medium mb-1">当前步骤: 趋势分析</div>
                <div className="text-xs">正在分析销售数据的趋势和模式...</div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
