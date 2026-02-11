# Frontend Workspace

## 目标

前端以“查询快照 + SSE 增量”模式实现对话工作台，覆盖：
- 会话创建与进入
- 回合触发（chat -> plan）
- 三栏执行可视化（消息、任务、执行记录）

## 启动

```bash
cd frontend
npm install
npm run dev
```

默认后端地址：`http://127.0.0.1:8091`。
可通过环境变量覆盖：

```bash
VITE_API_BASE_URL=http://127.0.0.1:8091
VITE_HTTP_TIMEOUT_MS=15000
VITE_CHAT_TIMEOUT_MS=90000
```

说明：
- `VITE_HTTP_TIMEOUT_MS`：普通接口超时，默认 `15000ms`。
- `VITE_CHAT_TIMEOUT_MS`：`POST /api/sessions/{id}/chat` 专用超时，默认 `90000ms`（用于覆盖 Root 规划重试导致的长耗时场景）。

## 页面

- `/login`：开发态 userId 设置
- `/sessions`：会话创建与最近会话入口
- `/sessions/:sessionId`：对话工作台（三栏）

## 演示级能力

- 中栏消息按 Turn 分组，支持回合历史回放
- 过程事件结构化展示（TaskStarted/TaskLog/TaskCompleted/PlanFinished）
- 右栏任务表支持状态筛选、attempt/更新时间排序
- 错误面板聚合展示 Plan errorSummary 与失败任务摘要
- Task 执行记录抽屉（attempt、模型、耗时、错误类型、响应内容）

## 数据流

1. 首屏：`GET /api/sessions/{id}/overview`
2. 回合列表：`GET /api/sessions/{id}/turns`
3. 消息流：`GET /api/sessions/{id}/messages`
4. 发送消息：`POST /api/sessions/{id}/chat`
5. 实时流：`GET /api/plans/{id}/stream?lastEventId=...`

## 说明

- 当前后端未提供“按 userId 列出所有 session”的查询接口。
- `/sessions` 页面使用本地书签（创建后写入 localStorage）管理最近会话入口。
