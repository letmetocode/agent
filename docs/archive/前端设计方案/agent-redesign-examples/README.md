# Agent 前端重构示例代码

本目录包含 Agent 应用前端重构的设计实现示例代码。

## 目录结构

```
agent-redesign-examples/
├── tailwind.config.js          # Tailwind CSS 配置
├── globals.css                 # 全局样式
├── lib/
│   └── utils.ts               # 工具函数
├── components/
│   ├── ui/                    # 基础 UI 组件
│   │   ├── button.tsx         # 按钮组件
│   │   ├── card.tsx           # 卡片组件
│   │   ├── badge.tsx          # 标签组件
│   │   ├── input.tsx          # 输入框组件
│   │   ├── status-indicator.tsx  # 状态指示器
│   │   ├── message-bubble.tsx    # 消息气泡
│   │   ├── chat-input.tsx        # 聊天输入框
│   │   ├── task-card.tsx         # 任务卡片
│   │   ├── empty-state.tsx       # 空状态
│   │   └── skeleton.tsx          # 骨架屏
│   └── layout/                # 布局组件
│       └── sidebar.tsx        # 侧边栏
└── pages/                     # 页面示例
    ├── home-example.tsx       # 工作台页面
    └── conversation-example.tsx  # 对话页面
```

## 安装依赖

```bash
# 核心依赖
npm install react react-dom
npm install -D typescript @types/react @types/react-dom

# Tailwind CSS
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p

# 工具库
npm install clsx tailwind-merge class-variance-authority

# Radix UI (无样式组件)
npm install @radix-ui/react-slot

# 图标
npm install lucide-react

# 动画
npm install framer-motion
```

## 快速开始

### 1. 配置 Tailwind CSS

将 `tailwind.config.js` 复制到你的项目根目录。

### 2. 添加全局样式

将 `globals.css` 复制到 `src/styles/globals.css`，并在入口文件导入：

```tsx
import "./styles/globals.css";
```

### 3. 配置路径别名

在 `tsconfig.json` 中添加：

```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  }
}
```

### 4. 使用组件

```tsx
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";

function App() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>示例卡片</CardTitle>
      </CardHeader>
      <CardContent>
        <Button>点击我</Button>
      </CardContent>
    </Card>
  );
}
```

## 组件使用指南

### 按钮 Button

```tsx
import { Button } from "@/components/ui/button";

// 变体
<Button variant="default">默认</Button>
<Button variant="outline">描边</Button>
<Button variant="ghost">幽灵</Button>
<Button variant="destructive">危险</Button>

// 尺寸
<Button size="sm">小</Button>
<Button size="default">默认</Button>
<Button size="lg">大</Button>
<Button size="icon">图标</Button>

// 加载状态
<Button loading>加载中</Button>
```

### 卡片 Card

```tsx
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card";

<Card interactive hover>
  <CardHeader>
    <CardTitle>卡片标题</CardTitle>
    <CardDescription>卡片描述</CardDescription>
  </CardHeader>
  <CardContent>内容</CardContent>
  <CardFooter>底部</CardFooter>
</Card>
```

### 状态指示器 StatusIndicator

```tsx
import { StatusIndicator, ProgressIndicator, StepIndicator } from "@/components/ui/status-indicator";

// 状态点
<StatusIndicator status="running" text="运行中" />

// 进度条
<ProgressIndicator progress={75} status="running" />

// 步骤指示器
<StepIndicator
  steps={[
    { label: "步骤1", status: "completed" },
    { label: "步骤2", status: "current" },
    { label: "步骤3", status: "pending" },
  ]}
  currentStep={1}
/>
```

### 消息气泡 MessageBubble

```tsx
import { MessageBubble, SystemMessage, ThinkingIndicator } from "@/components/ui/message-bubble";

// 用户消息
<MessageBubble
  role="user"
  content="你好"
  timestamp="10:30"
/>

// Agent 消息
<MessageBubble
  role="agent"
  content="你好！有什么可以帮你的？"
  timestamp="10:30"
  citations={[{ id: "1", title: "参考文档" }]}
/>

// 系统消息
<SystemMessage type="info" title="提示" content="系统消息内容" />

// 思考中
<ThinkingIndicator />
```

### 聊天输入框 ChatInput

```tsx
import { ChatInput } from "@/components/ui/chat-input";

const [value, setValue] = useState("");

<ChatInput
  value={value}
  onChange={setValue}
  onSubmit={() => console.log("发送:", value)}
  placeholder="输入消息..."
  loading={false}
/>
```

### 任务卡片 TaskCard

```tsx
import { TaskCard, TaskListItem } from "@/components/ui/task-card";

// 卡片形式
<TaskCard
  id={1}
  name="数据分析"
  status="running"
  progress={65}
  agentName="数据分析师"
/>

// 列表项形式
<TaskListItem
  id={1}
  name="数据分析"
  status="running"
  nodeName="analysis"
  duration="12s"
/>
```

### 空状态 EmptyState

```tsx
import { EmptyState, EmptySessions, EmptyTasks } from "@/components/ui/empty-state";

// 通用空状态
<EmptyState
  icon={<Icon />}
  title="暂无数据"
  description="描述文字"
  action={{ label: "创建", onClick: () => {} }}
/>

// 预设空状态
<EmptySessions onCreate={() => {}} />
<EmptyTasks onRefresh={() => {}} />
```

### 骨架屏 Skeleton

```tsx
import { Skeleton, CardSkeleton, ListSkeleton, MessageSkeleton, PageSkeleton } from "@/components/ui/skeleton";

// 基础骨架
<Skeleton className="h-4 w-3/4" />

// 预设骨架
<CardSkeleton />
<ListSkeleton count={5} />
<MessageSkeleton />
<PageSkeleton />
```

### 侧边栏 Sidebar

```tsx
import { Sidebar, CompactSidebar } from "@/components/layout/sidebar";

// 主导航侧边栏
<Sidebar
  items={[
    { label: "工作台", href: "/", icon: <Icon />, active: true },
    { label: "Agent", href: "/agents", icon: <Icon /> },
  ]}
  collapsed={false}
  onToggle={() => {}}
/>

// 紧凑侧边栏（用于对话页）
<CompactSidebar
  title="Session #123"
  subtitle="会话标题"
  headerAction={<Button>操作</Button>}
>
  {/* 内容 */}
</CompactSidebar>
```

## 页面示例

### 工作台页面

参考 `pages/home-example.tsx`

### 对话页面

参考 `pages/conversation-example.tsx`

## 设计原则

1. **极简主义**：每个元素都有其存在的理由
2. **一致性**：颜色、间距、交互模式全站统一
3. **反馈及时**：用户操作后立即给予反馈
4. **渐进披露**：按优先级展示信息，避免过载
5. **效率优先**：支持快捷键，减少操作步骤

## 色彩系统

| 名称 | 用途 |
|------|------|
| primary-600 | 主按钮、链接、强调 |
| slate-900 | 主要文字 |
| slate-600 | 次要文字 |
| slate-400 | 辅助文字 |
| slate-200 | 边框、分割线 |
| slate-100 | 背景、hover |
| success | 成功状态 |
| warning | 警告状态 |
| error | 错误状态 |

## 间距体系

基础单位：4px

| 名称 | 值 | 用途 |
|------|-----|------|
| space-1 | 4px | 紧凑间距 |
| space-2 | 8px | 小间距 |
| space-3 | 12px | 中间距 |
| space-4 | 16px | 标准间距 |
| space-6 | 24px | 大间距 |
| space-8 | 32px | 区块间距 |

## 响应式断点

| 断点 | 宽度 | 用途 |
|------|------|------|
| sm | 640px | 手机横屏 |
| md | 768px | 平板 |
| lg | 1024px | 小桌面 |
| xl | 1280px | 大桌面 |
| 2xl | 1536px | 超大屏 |

## 许可证

MIT
