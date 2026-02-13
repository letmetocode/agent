# Frontend Workspace

## 目标

前端围绕 **Agent 控制台主路径** 重构：

- 新建 Agent / 新建任务
- 输入目标并执行
- 执行中途控制与观测
- 查看结果与引用
- 导出 / 分享 / 历史回溯

风格基线：极简、现代、克制；优先可读性与效率（Desktop First）。

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

## 信息架构（一级导航）

- `/workspace`：工作台（快捷启动、系统健康、最近会话与产出）
- `/sessions`：对话与执行入口（统一启动器）
- `/sessions/:sessionId`：会话执行页（2.5 栏布局）
- `/tasks`：任务中心（筛选、分页、列表/看板）
- `/tasks/:taskId`：任务详情（时间轴、中途控制、结果与引用）
- `/assets/tools`：工具与插件
- `/assets/knowledge`：知识库列表
- `/assets/knowledge/:kbId`：知识库详情与检索测试
- `/observability/overview`：监控总览
- `/observability/logs`：日志检索
- `/settings/profile`：个人设置
- `/workflows/drafts`：Workflow Draft 治理
- `/share/tasks/:taskId`：匿名分享结果页（携带 code + token）

## 当前可用能力

- 全局控制台壳布局（侧边导航 + 顶栏搜索 + 面包屑）
- 页面级统一状态组件（空/加载/错误/不可用）
- 会话页执行上下文聚合（回合、事件流、Plan/Task 进度）
- 会话启动器已接入 V2 会话与回合接口（支持先选 Agent 或快速创建 Agent）
- 会话主路径默认走 `/api/v2/*`，旧 `/api/sessions/{id}/chat` 已下线
- 任务详情中途控制（暂停/继续/取消/失败重试）
- 任务结果导出（Markdown/JSON）与分享链接生成、管理、批量失效
- 日志分页检索（level/taskId/traceId/keyword）
- 知识库文档详情与检索测试
- 监控总览 P95/P99/慢任务/SLA 指标展示
- 告警阈值与触发逻辑在后端监控系统固化（Prometheus 规则），前端仅负责展示与跳转排障入口。

## 已接入后端接口（2026-02）

- 会话与执行：
  - `GET /api/v2/agents/active`
  - `POST /api/v2/agents`
  - `POST /api/v2/sessions`
  - `POST /api/v2/sessions/{id}/turns`
  - `GET /api/v2/plans/{id}/routing`
  - `GET /api/sessions/list`
  - `GET /api/sessions/{id}/overview`
  - `GET /api/sessions/{id}/turns`
  - `GET /api/sessions/{id}/messages`
  - `POST /api/sessions/{id}/chat`（已下线，返回迁移提示）
  - `GET /api/plans/{id}`
  - `GET /api/plans/{id}/tasks`
  - `GET /api/plans/{id}/events`
  - `GET /api/plans/{id}/stream?lastEventId=...`
- 任务中心：
  - `GET /api/tasks/paged`
  - `GET /api/tasks/{id}`
  - `GET /api/tasks/{id}/executions`
  - `POST /api/tasks/{id}/pause`
  - `POST /api/tasks/{id}/resume`
  - `POST /api/tasks/{id}/cancel`
  - `POST /api/tasks/{id}/retry-from-failed`
  - `GET /api/tasks/{id}/export`
  - `POST /api/tasks/{id}/share-links`
  - `GET /api/tasks/{id}/share-links`
  - `POST /api/tasks/{id}/share-links/{shareId}/revoke`
  - `POST /api/tasks/{id}/share-links/revoke-all`
  - `GET /api/share/tasks/{id}`
- 观测与日志：
  - `GET /api/dashboard/overview`
  - `GET /api/logs/paged`
- 资产中心：
  - `GET /api/agents/tools`
  - `GET /api/agents/vector-stores`
  - `GET /api/knowledge-bases/{id}`
  - `GET /api/knowledge-bases/{id}/documents`
  - `POST /api/knowledge-bases/{id}/retrieval-tests`
- Workflow 治理：
  - `GET /api/workflows/drafts`
  - `GET /api/workflows/drafts/{id}`
  - `PUT /api/workflows/drafts/{id}`
  - `POST /api/workflows/drafts/{id}/publish`

## 数据流（核心页面）

1. 首屏：`GET /api/dashboard/overview`
2. 会话：`GET /api/sessions/{id}/overview|turns|messages`
3. 任务：`GET /api/tasks/paged` + `GET /api/tasks/{id}` + `GET /api/tasks/{id}/executions`
4. 日志：`GET /api/logs/paged`
5. 资产：`GET /api/agents/vector-stores` + `/api/knowledge-bases/*`
6. 实时：`GET /api/plans/{id}/stream?lastEventId=...`

## 工程说明

- 路由已启用 `lazy + Suspense` 页面级拆包。
- UI 组件基于 `Ant Design`，样式遵循统一间距/字号体系。
- 任务、会话、日志页面优先采用服务端分页，降低前端全量加载压力。

## UI 重构基线（2026-02）

- 重构范围：覆盖全部一级导航页面（工作台/对话与执行/任务中心/资产中心/观测与日志/设置/Workflow 治理）。
- 实施策略：渐进式替换，保持任意时点可运行与可回滚。
- 布局骨架：统一 `page-container` + `page-section`，主标题统一由 `PageHeader` 承载。
- 状态组件：统一 `StateView` 渲染空态、加载态、错误态、权限态、不可用态。
- 视觉规范：统一字号阶梯（12/14/16/20/24）、间距体系（4/8/12/16/24/32）、圆角（8/12）与卡片阴影层级。
- 交互优先级：优先提升任务完成效率（主操作可见、路径短、状态明确），视觉统一次之。

## 分享闭环手工回归清单

- 任务详情页创建分享链接（默认 TTL）：
  - 进入 `/tasks/:taskId`，点击“分享结果”，直接创建。
  - 预期：出现 `token` 与可复制 `shareUrl`，状态为 `ACTIVE`。
- TTL 边界校验：
  - 创建时输入 `expiresHours < 1` 与 `expiresHours > app.share.max-ttl-hours`。
  - 预期：后端钳制到 `[1, maxTtlHours]`，前端状态与过期时间正确展示。
- 单条撤销与批量撤销：
  - 对单条执行“撤销”，再执行“全部撤销”。
  - 预期：状态变为 `REVOKED`，撤销原因/时间可见，已撤销链接不可恢复。
- 匿名分享访问（有效链接）：
  - 使用 `/share/tasks/:taskId?code=...&token=...` 打开。
  - 预期：可查看任务输出与引用，不暴露内部控制操作。
- 匿名分享失败分支：
  - 分别验证 `token 错误`、`code 错误`、`链接已撤销`、`链接已过期`。
  - 预期：页面提示“链接不可用/已失效”，不泄露内部细节。


## 会话与规划 V2 手工回归清单

- Agent 选择路径：
  - 在 `/sessions` 选择已有 Agent，输入目标启动执行。
  - 预期：成功创建 Session 与 Turn，并跳转会话详情页。
- Agent 快速创建路径：
  - 在 `/sessions` 选择“快速创建 Agent”，填写最小字段后启动。
  - 预期：先创建 Agent，再创建 Session + Turn。
- 路由决策展示：
  - 执行后在会话页“引用与异常”区域查看路由摘要。
  - 预期：可看到 `sourceType/fallbackFlag/plannerAttempts`，fallback 时显示原因。
- 候补草案提示：
  - 触发未命中生产 Definition 场景。
  - 预期：展示候补 Draft 提示，可跳转治理页或查看 Draft。

- 旧接口下线验证：
  - 直接调用 `/api/sessions/{id}/chat`。
  - 预期：返回“旧接口已下线，请使用 /api/v2/sessions/{id}/turns”。
