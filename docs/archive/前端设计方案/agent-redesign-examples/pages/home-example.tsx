//**
 * 工作台页面示例 - 展示新设计的组件使用方式
 */
import * as React from "react";
import { cn } from "@/lib/utils";

// 布局组件
import { Sidebar, NavItem } from "@/components/layout/sidebar";

// UI 组件
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { TaskCard } from "@/components/ui/task-card";
import { StatusIndicator } from "@/components/ui/status-indicator";
import { EmptySessions } from "@/components/ui/empty-state";
import { CardSkeleton } from "@/components/ui/skeleton";

// 导航图标
const navItems: NavItem[] = [
  {
    label: "工作台",
    href: "/",
    icon: (
      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
      </svg>
    ),
    active: true,
  },
  {
    label: "Agent 工厂",
    href: "/agents",
    icon: (
      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
      </svg>
    ),
  },
  {
    label: "任务中心",
    href: "/tasks",
    icon: (
      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
      </svg>
    ),
    badge: 3,
  },
  {
    label: "知识库",
    href: "/knowledge",
    icon: (
      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
      </svg>
    ),
  },
  {
    label: "监控大盘",
    href: "/monitoring",
    icon: (
      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
      </svg>
    ),
  },
  {
    label: "设置",
    href: "/settings",
    icon: (
      <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
      </svg>
    ),
  },
];

// 模拟数据
const recentSessions = [
  { id: 1, title: "销售数据分析", agent: "数据分析师", updatedAt: "10分钟前", status: "running" as const },
  { id: 2, title: "代码审查", agent: "代码助手", updatedAt: "1小时前", status: "completed" as const },
  { id: 3, title: "文档生成", agent: "文档生成器", updatedAt: "2小时前", status: "completed" as const },
  { id: 4, title: "需求分析", agent: "产品经理", updatedAt: "昨天", status: "completed" as const },
];

const runningTasks = [
  { id: 123, name: "销售数据分析", status: "running" as const, progress: 65, agentName: "数据分析师" },
  { id: 124, name: "代码审查", status: "running" as const, progress: 30, agentName: "代码助手" },
  { id: 125, name: "文档生成", status: "pending" as const, agentName: "文档生成器" },
];

export default function HomePage() {
  const [sidebarCollapsed, setSidebarCollapsed] = React.useState(false);
  const [quickInput, setQuickInput] = React.useState("");

  return (
    <div className="flex h-screen bg-slate-50">
      {/* 侧边栏 */}
      <Sidebar
        items={navItems}
        collapsed={sidebarCollapsed}
        onToggle={() => setSidebarCollapsed(!sidebarCollapsed)}
      />

      {/* 主内容区 */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* 头部 */}
        <header className="h-14 flex items-center justify-between px-6 border-b border-slate-200 bg-white">
          <h1 className="text-lg font-semibold text-slate-900">工作台</h1>
          <div className="flex items-center gap-4">
            <span className="text-sm text-slate-500">user@example.com</span>
            <div className="h-8 w-8 rounded-full bg-primary-100 flex items-center justify-center text-primary-700 font-medium">
              U
            </div>
          </div>
        </header>

        {/* 页面内容 */}
        <main className="flex-1 overflow-y-auto p-6">
          <div className="max-w-6xl mx-auto space-y-6">
            {/* 欢迎区域 + 快捷创建 */}
            <Card className="bg-gradient-to-r from-primary-600 to-primary-700 text-white border-0">
              <CardContent className="p-6">
                <h2 className="text-xl font-semibold mb-2">欢迎回来 👋</h2>
                <p className="text-primary-100 mb-4">今天想要完成什么任务？</p>
                <div className="flex gap-3">
                  <div className="flex-1 relative">
                    <Input
                      value={quickInput}
                      onChange={(e) => setQuickInput(e.target.value)}
                      placeholder="描述你的任务，例如：分析销售数据..."
                      className="bg-white/10 border-white/20 text-white placeholder:text-white/60 pr-32"
                    />
                    <Button
                      className="absolute right-1 top-1 bottom-1 bg-white text-primary-600 hover:bg-white/90"
                      size="sm"
                    >
                      创建会话
                    </Button>
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* 统计卡片 */}
            <div className="grid grid-cols-4 gap-4">
              {[
                { label: "Agent 数量", value: "12", change: "+2" },
                { label: "今日任务", value: "48", change: "+15%" },
                { label: "成功率", value: "98.5%", change: "+1.2%" },
                { label: "平均耗时", value: "12s", change: "-3s" },
              ].map((stat, index) => (
                <Card key={index} hover>
                  <CardContent className="p-4">
                    <div className="text-sm text-slate-500 mb-1">{stat.label}</div>
                    <div className="flex items-baseline gap-2">
                      <span className="text-2xl font-semibold text-slate-900">{stat.value}</span>
                      <span className={cn(
                        "text-xs",
                        stat.change.startsWith("+") ? "text-green-600" : "text-red-600"
                      )}>
                        {stat.change}
                      </span>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>

            {/* 内容网格 */}
            <div className="grid grid-cols-3 gap-6">
              {/* 最近会话 */}
              <div className="col-span-2">
                <Card>
                  <CardHeader className="flex flex-row items-center justify-between">
                    <div>
                      <CardTitle>最近会话</CardTitle>
                      <CardDescription>你最近的 4 个会话</CardDescription>
                    </div>
                    <Button variant="ghost" size="sm">查看全部</Button>
                  </CardHeader>
                  <CardContent>
                    <div className="grid grid-cols-2 gap-4">
                      {recentSessions.map((session) => (
                        <Card
                          key={session.id}
                          interactive
                          className="p-4 cursor-pointer"
                        >
                          <div className="flex items-start justify-between mb-2">
                            <div className="h-10 w-10 rounded-lg bg-primary-100 flex items-center justify-center text-primary-600">
                              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                              </svg>
                            </div>
                            <Badge
                              variant={session.status === "running" ? "primary" : "success"}
                              className="text-[10px]"
                            >
                              {session.status === "running" ? "运行中" : "已完成"}
                            </Badge>
                          </div>
                          <h4 className="font-medium text-slate-900 mb-1">{session.title}</h4>
                          <div className="flex items-center gap-2 text-xs text-slate-500">
                            <span>{session.agent}</span>
                            <span>·</span>
                            <span>{session.updatedAt}</span>
                          </div>
                        </Card>
                      ))}
                    </div>
                  </CardContent>
                </Card>
              </div>

              {/* 运行中任务 */}
              <div>
                <Card>
                  <CardHeader className="flex flex-row items-center justify-between">
                    <div>
                      <CardTitle>运行中任务</CardTitle>
                      <CardDescription>实时任务状态</CardDescription>
                    </div>
                    <StatusIndicator status="running" text="3 个任务" />
                  </CardHeader>
                  <CardContent className="space-y-3">
                    {runningTasks.map((task) => (
                      <TaskCard
                        key={task.id}
                        id={task.id}
                        name={task.name}
                        status={task.status}
                        progress={task.progress}
                        agentName={task.agentName}
                      />
                    ))}
                  </CardContent>
                </Card>
              </div>
            </div>

            {/* 快捷入口 */}
            <div>
              <h3 className="text-sm font-medium text-slate-700 mb-3">快捷入口</h3>
              <div className="flex gap-3">
                {[
                  { label: "新建 Agent", icon: "🤖", color: "bg-purple-100 text-purple-700" },
                  { label: "上传文档", icon: "📄", color: "bg-blue-100 text-blue-700" },
                  { label: "查看日志", icon: "📊", color: "bg-green-100 text-green-700" },
                  { label: "API 文档", icon: "🔌", color: "bg-orange-100 text-orange-700" },
                ].map((item, index) => (
                  <Button
                    key={index}
                    variant="outline"
                    className="flex-1 h-auto py-4 flex flex-col items-center gap-2"
                  >
                    <span className={cn("h-10 w-10 rounded-lg flex items-center justify-center text-lg", item.color)}>
                      {item.icon}
                    </span>
                    <span className="text-sm">{item.label}</span>
                  </Button>
                ))}
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
