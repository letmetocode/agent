# Frontend 控制台信息架构与页面布局（2026-02）

## 1. 目标与原则

- 产品目标：围绕 Agent 主路径（创建→执行→观测→沉淀）构建高可读、高效率控制台。
- 风格基线：极简、现代、克制，减少视觉噪音，优先信息层级和任务完成率。
- 交互原则：主路径前置、低频能力收敛；状态可见、可恢复、可追责。
- 落地原则：优先可实现与可维护，避免仅视觉层“临时补丁”。

## 2. 信息架构（IA）与导航

一级导航（左侧 Sidebar）：

1. 工作台（`/workspace`）
2. 对话与执行（`/sessions`、`/sessions/:sessionId`）
3. 任务中心（`/tasks`、`/tasks/:taskId`）
4. 资产中心（`/assets/tools`、`/assets/knowledge`、`/assets/knowledge/:kbId`）
5. 观测与日志（`/observability/overview`、`/observability/logs`）
6. 设置（`/settings/profile`、`/settings/system`、`/settings/access`）

补充治理入口：

- Workflow Draft 治理（`/workflows/drafts`）

导航层级策略：

- 一级导航承载“工作域”（Workspace / Task / Asset / Observability）。
- 二级页面承载“实体详情”（Session / Task / KnowledgeBase 详情）。
- 面包屑仅用于当前位置确认，不承载主要操作，避免路径分裂。

## 3. 关键页面布局（文字线框）

### 3.1 工作台 `/workspace`

线框（12 栅格）：

- 顶部（12）：页面标题 + 主动作“新建执行” + 次动作“任务中心”。
- 第一行（8 + 4）：`快捷启动卡` + `系统健康卡`。
- 第二行（6 + 6）：`最近会话` + `运行中任务`。
- 第三行（12）：`最近产出与可复用结果`。

设计理由：

- 首屏先给可执行动作，再给系统状态，符合“先行动后分析”。
- 宽卡展示最近产出，减少跨页跳转。

### 3.2 对话与执行 `/sessions/:sessionId`

线框（2.5 栏）：

- 左栏（280）：会话信息、Plan 列表、历史回合入口。
- 中栏（自适应）：消息流（主阅读区）+ 底部固定输入框。
- 右栏（360）：Tab 面板（进度 / 引用与异常 / 任务）。

设计理由：

- 中栏保证阅读宽度，减少“执行中”信息分散。
- 右栏按需打开上下文，降低新用户认知负担。

### 3.3 任务中心 `/tasks`

线框：

- 顶部筛选带：关键词 + 状态 + 查询按钮 + 视图切换（列表/看板）。
- 主区：默认列表（分页表格）；看板作为态势补充。

设计理由：

- 默认列表更利于定位具体任务与下钻详情。
- 看板保留给快速态势浏览，不挤占主路径。

### 3.4 任务详情 `/tasks/:taskId`

线框（主副双栏）：

- 头部动作条：暂停 / 继续 / 取消 / 从失败节点重试。
- 左主区（8）：执行时间轴 + 阶段步骤 + 结果与引用 + 执行记录。
- 右侧栏（4）：任务元信息、最近耗时、执行图入口。
- 底部动作：导出 Markdown / JSON、生成分享链接。

设计理由：

- 将“中途控制”前置到头部，缩短故障干预路径。
- 结果、引用、执行记录同屏，支持快速复盘与审计。

### 3.5 资产中心

#### 工具与插件 `/assets/tools`

- 列表卡片：工具状态、授权、调用量、错误摘要。
- 右侧动作：启停、配置、查看调用历史。

#### 知识库 `/assets/knowledge`、`/assets/knowledge/:kbId`

- 列表页：库状态、文档量、索引进度。
- 详情页：文档表格 + 检索测试面板（Query -> TopK 结果）。

设计理由：

- 工具和知识作为“执行资产”同域管理，便于运维协同。
- 知识库详情把“索引健康 + 召回效果”放在一页闭环。

### 3.6 观测与日志

#### 监控总览 `/observability/overview`

- KPI 卡：总量、成功率、运行中、失败、P95/P99、慢任务、SLA 超阈值。
- 趋势区：成功/运行/失败占比。
- 失败任务榜：支持快速回溯。

#### 日志检索 `/observability/logs`

- 过滤器：level、traceId、taskId、keyword。
- 主表：分页日志 + 抽屉原始事件。

设计理由：

- 先看总体健康，再下钻具体日志，符合 SRE 排障路径。

## 4. 核心用户路径（端到端）

1. 新建 Agent/任务：在 `/sessions` 启动器创建会话并进入执行页。
2. 输入目标：提交目标与约束，触发 Plan/Task 生成。
3. 执行与中途控制：在任务详情中执行暂停/继续/取消/失败重试。
4. 结果与引用：查看任务输出、引用来源、执行记录。
5. 导出/分享：导出 Markdown/JSON，生成时效分享链接。
6. 历史回溯：回到工作台、任务中心、日志检索进行复盘。

## 5. 组件与状态设计

统一基础组件：

- `PageHeader`：标题、描述、主次动作、扩展状态。
- `StateView`：空状态 / 加载态 / 错误态 / 不可用态。
- `StatusTag`：统一状态语义映射（颜色 + 文案）。

状态规范：

- 空状态：给“下一步动作”按钮（如“前往创建”）。
- 加载态：页面局部骨架，不阻塞全局导航。
- 错误态：明确错误原因 + 重试动作。
- 权限/不可用态：说明原因（无权限/配置缺失）+ 替代路径。
- 长任务进度：时间轴 + 步骤条双通道展示。
- 流式输出：会话页消息流 + 事件流并行展示，支持 lastEventId 回放。

## 6. 视觉规范（实现基线）

- 字体：`Inter`, `Noto Sans SC`, `system-ui`。
- 字号梯度：`12 / 14 / 16 / 20 / 24`。
- 间距体系：`4px` 基础单位，常用 `8 / 12 / 16 / 24 / 32`。
- 圆角：容器 `10px`，输入控件 `8px`。
- 阴影：卡片轻阴影，悬浮态加强一级。
- 按钮层级：Primary（主路径）、Default（次路径）、Text/Link（低干扰动作）。
- 色彩与对比：中性色为主，状态色用于反馈；正文对比度遵循可读性优先。

## 7. 可实现建议（工程策略）

- 组件库：`Ant Design`（表格、表单、抽屉、状态反馈完整，开发效率高）。
- 布局方式：`Layout + Grid + Space`，保证复杂页面结构一致性。
- 路由策略：`React Router + lazy + Suspense`，按页面拆包。
- 数据策略：
  - 列表类接口优先服务端分页（会话/任务/日志）。
  - 详情页并发拉取（主数据 + 明细），缩短首屏等待。
- 响应式策略：
  - Desktop（>=1280）：完整 2/3 栏布局。
  - Tablet（768-1279）：右栏折叠为抽屉或下沉。
  - Mobile（<768）：首期以浏览与检索为主，操作收敛。

## 8. 接口接入状态（当前实现）

已落地：

- 会话：`GET /api/sessions/list`、`GET /api/sessions/{id}/overview|turns|messages`、`POST /api/sessions/{id}/chat`
- 任务：`GET /api/tasks/paged`、`GET /api/tasks/{id}`、`GET /api/tasks/{id}/executions`
- 任务控制：
  - `POST /api/tasks/{id}/pause`
  - `POST /api/tasks/{id}/resume`
  - `POST /api/tasks/{id}/cancel`
  - `POST /api/tasks/{id}/retry-from-failed`
- 任务产物：`GET /api/tasks/{id}/export`、`POST /api/tasks/{id}/share-links`、`GET /api/tasks/{id}/share-links`、`POST /api/tasks/{id}/share-links/{shareId}/revoke`、`POST /api/tasks/{id}/share-links/revoke-all`
- 匿名分享读取：`GET /api/share/tasks/{id}`
- 日志：`GET /api/logs/paged`（含 `traceId`）
- 监控：`GET /api/dashboard/overview`（含 `latencyStats`、`slowTaskCount`、`slaBreachCount`）
- 资产：
  - `GET /api/agents/tools`
  - `GET /api/agents/vector-stores`
  - `GET /api/knowledge-bases/{id}`
  - `GET /api/knowledge-bases/{id}/documents`
  - `POST /api/knowledge-bases/{id}/retrieval-tests`

## 9. 下一阶段建议

- 看板视图升级为真实分组拖拽与批量操作。
- 监控总览补充时间范围切换（1h/24h/7d）。
- 分享管理补充访问审计看板（按创建、访问失败、撤销维度统计）。
