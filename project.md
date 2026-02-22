# 项目工程化梳理文档（基于当前仓库）

更新时间：2026-02-19  
适用范围：`agent-app`、`agent-trigger`、`agent-domain`、`agent-infrastructure`、`agent-api`、`agent-types`

证据来源（核心）：
- `docs/01-product-requirements.md`
- `docs/02-system-architecture.md`
- `docs/04-development-backlog.md`
- `docs/dev-ops/postgresql/sql/01_init_database.sql`
- `agent-trigger/src/main/java/com/getoffer/trigger/http/*.java`
- `agent-trigger/src/main/java/com/getoffer/trigger/application/**/*.java`
- `agent-trigger/src/main/java/com/getoffer/trigger/job/*.java`
- `agent-infrastructure/src/main/java/com/getoffer/infrastructure/planning/*.java`
- `agent-app/src/main/java/com/getoffer/config/*.java`
- `agent-app/src/main/resources/application*.yml`

---

## A. 项目总览（高密度版）

### A1. 业务背景 / 用户角色 / 核心价值

一句话：这是一个面向复杂任务分解与可追踪执行的 Agent 系统，外显为对话，内核是可执行工作流引擎，目标是通过 SOP 模板创建与复用稳定提升输出质量，并沉淀可持续优化机制。

要点：
- 用户角色：当前阶段“使用者”和“治理者”为同一账号（`docs/01-product-requirements.md`）。
- 核心价值链：对话发起 -> 自动规划 -> 可观测执行 -> 终态沉淀 -> 治理迭代。
- 产品约束：本轮明确不做多租户组织权限与复杂 RBAC（`docs/01-product-requirements.md`）。

### A2. 系统边界（做什么 / 不做什么）

做什么：
- 登录、鉴权、会话编排、规划与执行闭环：`/api/auth/*`、`/api/v3/chat/*`。
- Workflow 治理：Draft 查询/编辑/发布，SOP Spec 编译与校验：`/api/workflows/**`。
- 任务调度执行、SSE 实时流、终态收敛：`TaskSchedulerDaemon`、`TaskExecutor`、`PlanStatusDaemon`、`ChatStreamV3Controller`。
- 质量回放与观测：`/api/logs/paged`、`/api/quality/evaluations/*`、`/api/observability/alerts/*`。

不做什么：
- 外部 SSO/OAuth、多租户权限分层、复杂 RBAC（PRD 明确 Out of Scope）。
- [假设] 当前无对象存储主链路（仓库未见对象存储适配实现）。
- [假设] 当前无外部 MQ 主链路，事件总线使用 `plan_task_events + pg_notify`。

### A3. 架构图文字版（模块 + 调用链 + 关键依赖）

```text
[Frontend Console]
  -> [ApiAuthFilter + RequestTraceLoggingFilter] (agent-app)
  -> [HTTP/SSE Controllers] (agent-trigger/http)
  -> [Application Services] (agent-trigger/application)
  -> [Domain Services + Entities + State Rules] (agent-domain)
  -> [Repository/Planner/AI/MCP Adapters] (agent-infrastructure)
  -> [PostgreSQL] (核心持久化)

执行事件链路：
TaskExecutor/PlanStatusDaemon
  -> PlanTaskEventPublisher
  -> plan_task_events (持久化)
  -> pg_notify(channel=plan_task_events_channel)
  -> ChatStreamV3Controller 回放 + 实时推送
```

关键依赖：
- DB：PostgreSQL（主链路）。
- 缓存：[假设] 仅进程内缓存（如默认 Agent 缓存），Redis 为预留组件（`README.md`）。
- 消息：PostgreSQL LISTEN/NOTIFY（`PlanTaskEventPublisher`）。
- 第三方：Spring AI ChatModel（OpenAI 兼容）、MCP Server（`McpClientManager`）。

### A4. 关键数据流与状态流（入口到落库）

#### 数据流 1：会话提交到计划创建
1. `POST /api/v3/chat/messages` 进入 `ChatV3Controller`。
2. `ChatConversationCommandService.submitMessage` 创建或复用 `agent_sessions`。
3. 创建回合 `session_turns` 与用户消息 `session_messages`。
4. 异步触发 `PlannerServiceImpl.createPlan`。
5. 路由命中 `workflow_definitions` 或生成/复用 `workflow_drafts`，落库 `routing_decisions`。
6. 创建 `agent_plans` + `agent_tasks`，回写 turn 进入 EXECUTING。

#### 数据流 2：执行到终态
1. `TaskSchedulerDaemon` 推进 `PENDING -> READY/SKIPPED`。
2. `TaskExecutor` claim 任务并调用 `TaskExecutionRunner`。
3. 执行记录写入 `task_executions`，质量事件写入 `quality_evaluation_events`。
4. 执行事件写入 `plan_task_events` 并实时通知。
5. `PlanStatusDaemon` 聚合任务状态推进 `agent_plans`。
6. `TurnFinalizeApplicationService` 幂等落最终消息到 `session_messages`，绑定 `session_turns.final_response_message_id`。

#### 状态流（核心）
- Turn：`CREATED -> PLANNING -> EXECUTING -> SUMMARIZING -> COMPLETED/FAILED/CANCELLED`（`TurnStatusEnum`）。
- Plan：`PLANNING -> READY -> RUNNING -> PAUSED -> COMPLETED/FAILED/CANCELLED`（`PlanStatusEnum`）。
- Task：`PENDING -> READY -> RUNNING -> VALIDATING -> REFINING -> COMPLETED/FAILED/SKIPPED`（`TaskStatusEnum`）。

### A5. 非功能目标（NFR）与当前实现

| NFR 目标 | 目标口径 | 当前实现方式 | 证据 |
| --- | --- | --- | --- |
| 性能 | 首进度事件快、闭环稳定 | 会话提交快速 ACK + 异步规划；调度/执行线程池隔离；分页查询下推 | `ChatConversationCommandService`、`TaskExecutor`、`application.yml` |
| 可用性 | 执行链路抗抖 | claim lease + 心跳续租 + 超时重试 + Root fallback | `TaskExecutionRuntimeSupport`、`PlannerFallbackPolicyDomainService` |
| 一致性 | 终态幂等、防重复 | 乐观锁 version、claimed update guard、assistant 终态唯一索引 | `TaskPersistenceApplicationService`、`01_init_database.sql` |
| 安全性 | 登录态最小防护 | JWT HS256 + `auth_session_blacklist` 吊销；`/api/**` 统一鉴权 | `ApiAuthFilter`、`AuthSessionCommandService` |
| 可演进性 | Graph 版本可迁移 | Graph DSL v2 单写单读 + 候选兼容升级 + 迁移模板 | `docs/design/10-workflow-version-migration.md` |

### A6. 可观测性现状与排障入口

现状：
- 入口日志：`RequestTraceLoggingFilter` 输出 `HTTP_IN/HTTP_OUT/HTTP_ERROR`，注入 `X-Trace-Id/X-Request-Id`。
- 执行指标：任务 claim、超时、重试、执行耗时、Plan finalize 等 Micrometer 指标。
- 事件可追溯：`plan_task_events` 支撑回放与日志检索。
- 告警治理：`/api/observability/alerts/catalog` 与 `/probe-status`，并有巡检作业 `ObservabilityAlertCatalogProbeJob`。

排障入口建议顺序：
1. 先查入口日志：按 `traceId` 定位请求与错误。
2. 再查执行事件：`/api/plans/{id}/events`、`/api/logs/paged`。
3. 查任务与质量：`/api/tasks/paged`、`/api/quality/evaluations/paged`。
4. 查巡检/规则：`/api/observability/alerts/probe-status` + `docs/dev-ops/observability/*`。

### A7. 风险地图（Top 9）

| 风险 | 发生条件 | 影响 | 检测手段 | 应对 |
| --- | --- | --- | --- | --- |
| 1. 异步规划丢单 | ACK 后进程异常 | turn 长时间停留 PLANNING | `CHAT_V3_ACCEPTED` 后无计划事件 | 引入 outbox/补偿扫描 |
| 2. 路由误命中或频繁 fallback | Definition 匹配弱、Root 输出不稳 | 输出质量波动、时延上升 | `routing_decisions` + fallback 指标 | 强化路由策略与 Root 约束 |
| 3. SSE 连接抖动 | 网络/代理中断 | 用户进度感知中断 | SSE 重连率、`stream.error` 事件 | 回放补偿 + 心跳 + 静默恢复 |
| 4. 执行超时集中爆发 | 上游模型慢、网络抖动 | 失败率上升 | `agent.task.execution.timeout.total` | 调整 timeout/retry，限流降载 |
| 5. 乐观锁冲突高 | 并发写 plan/task | 终态延迟或写失败 | claimed guard reject/冲突日志 | 收敛写路径、有限重试 |
| 6. 工具策略误配 | allowlist/blocklist 错误 | 工具不可用或越界调用 | `tool_policy` 审计日志 | 增加策略预检与灰度 |
| 7. 鉴权配置不安全 | JWT secret 使用默认值 | 令牌风险 | 启动配置检查、安全扫描 | 强制生产密钥与轮转 |
| 8. 分享链接泄露 | token 出现在 URL/日志 | 结果外泄 | 访问日志异常、分享访问峰值 | 缩短 TTL、加审计、后续改签名票据 |
| 9. 查询接口压力过高 | 大页/宽过滤/高并发 | DB 抖动 | 慢查询日志、P95 上升 | 限制 page size、加索引、离线聚合 |

### A8. 迭代优先级建议

P0（立即）：
- 补齐“提交 ACK 后规划补偿”机制，避免 PLANNING 悬挂。
- 生产环境强制 JWT 密钥基线检查与轮转流程。
- 为 SSE/执行超时建立 SLO 看板与告警阈值统一基线。

P1（近期）：
- 为会话历史游标分页补齐容量压测与慢查询基线（1k/10k 消息会话）。
- 完善分享链接审计与访问频控。
- 引入路由命中效果评估（命中率、fallback 率、首事件时延关联）。

P2（中期）：
- 发布灰度治理体系升级（配置中心 + 动态开关 + 回滚编排）。
- 多租户/权限演进设计基线（保持当前单账号不破坏）。
- 质量评估策略平台化（可配置 rubric 与更完整实验分析）。

---

## B. 功能清单（按模块分组）

| 功能ID | 功能名称 | 入口（API/任务） | 主要用户 | 重要依赖 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| F01 | 认证与统一鉴权 | `/api/auth/login` `/logout` `/me` + `ApiAuthFilter` | 使用者/治理者 | `AuthSessionCommandService` `auth_session_blacklist` | P0 |
| F02 | 会话消息提交与编排 | `POST /api/v3/chat/messages` | 使用者 | `ChatConversationCommandService` `PlannerServiceImpl` | P0 |
| F03 | 会话历史与路由查询 | `GET /api/v3/chat/sessions/{id}/history` + `GET /api/v3/chat/plans/{id}/routing` | 使用者/治理者 | `ChatHistoryQueryService` `ChatRoutingQueryService` | P0 |
| F04 | SSE 流式与回放 | `GET /api/v3/chat/sessions/{id}/stream` | 使用者 | `ChatStreamV3Controller` `PlanTaskEventPublisher` | P0 |
| F05 | Workflow Draft 治理发布 | `/api/workflows/drafts*` `/api/workflows/definitions*` | 治理者 | `WorkflowGovernanceApplicationService` `workflow_*` | P0 |
| F06 | SOP Spec 编译与校验 | `/api/workflows/sop-spec/drafts/{id}/compile|validate` | 治理者 | `SopSpecCompileService` `WorkflowGraphPolicyKernel` | P0 |
| F07 | 任务调度推进 | `TaskSchedulerDaemon` 定时任务 | 系统 | `TaskScheduleApplicationService` `agent_tasks` | P0 |
| F08 | 任务执行与质量评估 | `TaskExecutor` 定时任务 | 系统 | `TaskExecutionRunner` `AgentFactoryImpl` `task_executions` | P0 |
| F09 | Plan 状态同步与终态收敛 | `PlanStatusDaemon` 定时任务 | 系统/使用者 | `PlanStatusSyncApplicationService` `TurnFinalizeApplicationService` | P0 |
| F10 | 任务控制与导出 | `/api/tasks/{id}` + `/api/tasks/{id}/pause|resume|cancel|retry-from-failed|export` + `/api/plans/{id}/events` | 使用者/治理者 | `TaskActionCommandService` | P1 |
| F11 | 分享链接管理与匿名访问 | `/api/tasks/{id}/share-links*` + `/api/share/tasks/{id}` | 使用者/外部查看者 | `task_share_links` `ShareAccessController` | P1 |
| F12 | 控制台分页查询与质量回放 | `/api/sessions/list` `/api/tasks/paged` `/api/tasks/{id}` `/api/plans/{id}/events` `/api/logs/paged` `/api/logs/tool-policy/paged` `/api/quality/*` | 治理者 | `ConsoleQueryController` `QueryController` | P1 |
| F13 | 观测告警目录与巡检 | `/api/observability/alerts/catalog|probe-status` + `ObservabilityAlertCatalogProbeJob` | 治理者/值班人员 | `alert-catalog.json` `ObservabilityAlertCatalogLinkProbeService` | P1 |

---

## C. 功能卡片（固定模板）

### F01 功能卡片：认证与统一鉴权

1) 功能名称：认证与统一鉴权。  
2) 功能目标（一句话）：提供最小可用登录体系，并对 `/api/**` 实现统一登录态校验。  
3) 用户/触发入口：`POST /api/auth/login`、`POST /api/auth/logout`、`GET /api/auth/me`、`ApiAuthFilter`。  
4) 业务范围与边界：支持本地账号密码 + JWT；支持黑名单吊销；不支持 RBAC/SSO/多租户权限矩阵。  
5) 输入/输出契约：  
输入：`username/password`；鉴权请求支持 `Authorization: Bearer <token>`，SSE 兼容 `accessToken` query。  
输出：统一 `Response<T>`；成功码 `0000`；参数错误 `0002`；`ApiAuthFilter` 拦截失败返回 HTTP 401；`/api/auth/login` 凭证错误返回 HTTP 200 + `0002`。  
6) 核心流程：登录签发 JWT -> 请求进入 `ApiAuthFilter` -> `AuthSessionCommandService.requireValidSession` 验签并查黑名单 -> 注入 `auth.userId`。  
7) 模块与依赖：  
内部：`agent-trigger/http/AuthController.java`、`agent-app/config/ApiAuthFilter.java`、`agent-trigger/application/command/AuthSessionCommandService.java`。  
外部：表 `auth_session_blacklist`。  
8) 数据与状态设计：JWT 含 `jti/exp/iss/sub`；登出写黑名单；一致性为最终一致（立即可校验吊销）。  
9) 技术方案与选型理由：采用 JWT + 黑名单，兼顾无状态校验与可撤销；备选为纯服务端 Session，未选因跨实例扩展与接入成本较高。  
10) 非功能性设计：TTL 可配置，签名 HS256；过滤器白名单最小化；日志脱敏由 `RequestTraceLoggingFilter` 控制。  
11) 风险清单与应对：默认密钥风险 -> 启动配置巡检 -> 强制生产密钥；token 泄露 -> 缩短 TTL + 强制登出吊销。  
12) 可观测与排障指南：关键日志 `Auth rejected`、`HTTP_ERROR`；字段 `traceId/requestId/path/status`。  
13) 测试与验收：`AuthControllerTest`、`AuthSessionCommandServiceTest`、`ApiAuthFilterTest`；验收口径为登录闭环可用、401 处理正确。  
14) 配置与部署：`app.auth.local.*`、`app.auth.jwt.*`、`app.auth.token.ttl-hours`。  
15) 改进建议：P0 强制密钥轮转；P1 引入 refresh token；P2 预留 RBAC 扩展接口。

### F02 功能卡片：会话消息提交与编排

1) 功能名称：会话消息提交与编排。  
2) 功能目标（一句话）：把用户消息转为可执行回合并异步触发计划创建。  
3) 用户/触发入口：`POST /api/v3/chat/messages`。  
4) 业务范围与边界：支持新建/复用 session、去重提交、灰度放量；不保证强一致“提交即一定建计划”。  
5) 输入/输出契约：  
输入：`ChatMessageSubmitRequestV3DTO`（`clientMessageId/userId/sessionId/message/title/agentKey/scenario/metaInfo/contextOverrides`）。  
输出：`ChatMessageSubmitResponseV3DTO`，含 `sessionId/turnId/planId/submissionState/streamPath/historyPath`；幂等键为 `clientMessageId`。  
6) 核心流程：校验参数 -> `prepareSession` -> 去重查 turn -> 创建 turn+user message -> 异步 `createPlan` -> turn 进入 EXECUTING。  
7) 模块与依赖：  
内部：`ChatV3Controller`、`ChatConversationCommandService`、`SessionConversationDomainService`、`PlannerServiceImpl`。  
外部：`agent_sessions/session_turns/session_messages/agent_plans/routing_decisions`。  
8) 数据与状态设计：turn 初始 `PLANNING`；异步建 plan 后转 `EXECUTING`；`clientMessageId` 主存于 `session_turns.client_message_id`（保留 metadata 兼容）。  
9) 技术方案与选型理由：采用快速 ACK + 异步规划以提升交互时延；备选同步规划未选，因首响应慢且前端体验差。  
10) 非功能性设计：灰度开关 `release-control.chat-planning.*`；失败兜底 `markTurnAsFailed`。  
11) 风险清单与应对：ACK 后异步失败导致挂起 -> 已上线 `PlanningTurnRecoveryJob` 补偿扫描；并发重复提交窗口 -> 已通过 DB 唯一约束 + 冲突复用收敛。  
12) 可观测与排障指南：日志关键字 `CHAT_V3_ACCEPTED`、`CHAT_V3_PLAN_BOUND`、`CHAT_V3_PLAN_ASYNC_FAILED`。  
13) 测试与验收：`ChatV3ControllerTest`、`ConversationOrchestratorServiceTest`、`SessionConversationDomainServiceTest`。  
14) 配置与部署：`release-control.chat-planning.enabled/traffic-percent/kill-switch`。  
15) 改进建议：P0 增加规划 outbox；P1 客户端幂等冲突码；P2 编排审计报表。

### F03 功能卡片：会话历史与路由查询

1) 功能名称：会话历史与路由查询。  
2) 功能目标（一句话）：为前端恢复上下文与解释路由来源提供读模型。  
3) 用户/触发入口：`GET /api/v3/chat/sessions/{id}/history`、`GET /api/v3/chat/plans/{id}/routing`。  
4) 业务范围与边界：返回会话历史分页（支持游标拉取）与计划路由；超长会话由前端“加载更多历史”增量拉取。  
5) 输入/输出契约：  
输入：`sessionId` 或 `planId`；历史查询支持 `cursor/limit/order`。  
输出：`ChatHistoryResponseV3DTO`（含 `hasMore/nextCursor/limit/order`）与 `RoutingDecisionDTO`；失败返回 `0002`。  
6) 核心流程：校验实体存在 -> 按 turn 游标分页查询 -> 批量聚合消息 -> latestPlan 解析 / 路由查询 -> DTO 组装。  
7) 模块与依赖：  
内部：`ChatHistoryQueryService`、`ChatRoutingQueryService`。  
外部：`session_turns/session_messages/agent_plans/routing_decisions`。  
8) 数据与状态设计：历史以 turn-id 游标分页，消息按 turnIds 批量聚合并在前端归并去重；latestPlan 按 `updatedAt` 选取。  
9) 技术方案与选型理由：聚合查询接口减少前端拼装复杂度；备选拆分多接口未选，因请求数和时序复杂度更高。  
10) 非功能性设计：读路径无写事务；分页参数限幅（默认 50，上限 200），避免单次大包。  
11) 风险清单与应对：长会话容量未知 -> 增加分页压测与索引巡检；路由记录缺失 -> 前端允许空 `routingDecision`。  
12) 可观测与排障指南：通过 `traceId` 关联入口日志，再看 `routing_decisions` 行是否存在。  
13) 测试与验收：`ChatRoutingV3ControllerTest`、`ChatV3ControllerTest`。  
14) 配置与部署：无强配置；受全局鉴权与日志配置影响。  
15) 改进建议：P1 会话历史分页容量基线（1k/10k 消息）；P2 路由解释字段标准化（可读原因分类）。

### F04 功能卡片：SSE 流式与回放

1) 功能名称：SSE 流式与回放。  
2) 功能目标（一句话）：把执行进度实时映射为聊天语义事件，并支持断线补偿。  
3) 用户/触发入口：`GET /api/v3/chat/sessions/{id}/stream`。  
4) 业务范围与边界：支持 `planId/lastEventId`、`Last-Event-ID`；不保证跨天长连接稳定。  
5) 输入/输出契约：  
输入：`sessionId`，可选 `planId/lastEventId`。  
输出：事件类型 `message.accepted/planning.started/task.progress/task.completed/answer.finalizing/answer.final/stream.completed`。  
6) 核心流程：校验 session/plan -> 建立 `SseEmitter` -> 初始事件 -> 回放 `plan_task_events` -> 订阅实时事件 -> 心跳与补偿扫描。  
7) 模块与依赖：  
内部：`ChatStreamV3Controller`、`ChatSseEventMapper`、`PlanTaskEventPublisher`。  
外部：`plan_task_events`、`session_messages`。  
8) 数据与状态设计：游标以 `eventId` 前进；`PLAN_FINISHED` 触发 final answer 并主动关闭连接。  
9) 技术方案与选型理由：SSE 简化浏览器消费并支持重连；备选 WebSocket 未选，因服务端状态管理与协议复杂度更高。  
10) 非功能性设计：心跳与回放参数可配置；响应头禁缓存与代理缓冲。  
11) 风险清单与应对：订阅内存增长 -> 连接清理；漏事件 -> replay 补偿；代理断流 -> 心跳 + 重连。  
12) 可观测与排障指南：日志 `CHAT_V3_STREAM_SUBSCRIBED/UNSUBSCRIBED`；查 `plan_task_events` 是否持续增长。  
13) 测试与验收：`ChatStreamV3ControllerTest`、`ChatSseEventMapperTest`、`SessionChatPlanSseIntegrationTest`。  
14) 配置与部署：`sse.heartbeat-interval-ms`、`sse.replay.batch-size`、`sse.replay.max-batches-per-sweep`。  
15) 改进建议：P1 订阅限流；P1 SSE 连接指标面板；P2 长连接分片与隔离。

### F05 功能卡片：Workflow Draft 治理发布

1) 功能名称：Workflow Draft 治理发布。  
2) 功能目标（一句话）：让治理者可管理 Draft 并发布为可路由的 Definition 版本。  
3) 用户/触发入口：`/api/workflows/drafts*`、`/api/workflows/definitions*`、`/api/workflows/drafts/{id}/publish`。  
4) 业务范围与边界：支持列表/详情/更新/发布；不支持导入导出分享。  
5) 输入/输出契约：  
输入：JSON Map（含 `draftKey/category/name/graphDefinition/inputSchema/defaultConfig/toolPolicy/constraints`）。  
输出：发布成功返回 `definitionId/definitionKey/definitionVersion`。  
6) 核心流程：读取 Draft -> 校验状态可编辑可发布 -> 校验 graph DSL v2 与 SOP 编译一致性 -> 克隆为 Definition 并版本递增 -> Draft 标记 PUBLISHED。  
7) 模块与依赖：  
内部：`WorkflowGovernanceController`、`WorkflowGovernanceApplicationService`。  
外部：`workflow_drafts`、`workflow_definitions`。  
8) 数据与状态设计：Draft 状态 `DRAFT/REVIEWING/PUBLISHED/ARCHIVED`；Definition 状态 `ACTIVE/DISABLED/ARCHIVED`。  
9) 技术方案与选型理由：治理与运行数据分离（Draft/Definition）保证可审计；备选“单表覆盖更新”未选，因版本追溯弱。  
10) 非功能性设计：更新与发布使用事务；发布前严格一致性校验。  
11) 风险清单与应对：Map 契约弱类型 -> 逐步 DTO 化；错误图发布 -> 保持 DSL v2 校验。  
12) 可观测与排障指南：排查顺序为 Draft 状态 -> compileHash -> Definition 版本号。  
13) 测试与验收：`WorkflowGovernanceApplicationServiceTest`、`WorkflowDraftLifecycleServiceTest`。  
14) 配置与部署：依赖 Graph DSL 与 Root 规划配置；无独立运行开关。  
15) 改进建议：P1 Draft 差异比较与审批日志；P2 多人协作冲突检测。

### F06 功能卡片：SOP Spec 编译与校验

1) 功能名称：SOP Spec 编译与校验。  
2) 功能目标（一句话）：把治理层 SOP Spec 编译成可执行 Runtime Graph，并在发布前确保结构合法。  
3) 用户/触发入口：`POST /api/workflows/sop-spec/drafts/{id}/compile|validate`。  
4) 业务范围与边界：支持 steps/groups、依赖校验、循环检测、策略归一；不支持跨 Draft 组合编译。  
5) 输入/输出契约：  
输入：`sopSpec`（`steps` 必填，`groups` 可选）。  
输出：`sopRuntimeGraph`、`compileHash`、`nodeSignature`、`warnings`，校验返回 `pass/issues`。  
6) 核心流程：解析 steps/groups -> 校验依赖与环 -> 生成 nodes/edges/groups -> `WorkflowGraphPolicyKernel` 归一化 -> 计算 hash/signature。  
7) 模块与依赖：  
内部：`SopSpecCompileService`、`WorkflowGraphPolicyKernel`。  
外部：发布路径写入 `workflow_drafts.constraints_json`。  
8) 数据与状态设计：编译产物固定 `graphDefinition.version=2`；`compileHash` 用于发布一致性门禁。  
9) 技术方案与选型理由：采用“治理源 + 编译产物”双层模型；备选直接编辑 runtime graph 未选，因治理可读性差。  
10) 非功能性设计：编译纯内存执行；异常以明确错误消息回传。  
11) 风险清单与应对：Spec 复杂度上升 -> 需要 schema 与 lint；隐式组自动补齐可能引入认知偏差 -> 回显 warning。  
12) 可观测与排障指南：先看 `issues`，再对比 `compileHash` 与 Draft 当前 graph hash。  
13) 测试与验收：`SopSpecCompileServiceTest`、`WorkflowGraphPolicyKernelTest`、`GraphDslPolicyServiceTest`。  
14) 配置与部署：跟随治理模块发布；无独立环境变量。  
15) 改进建议：P1 引入 JSON Schema；P2 支持编译性能统计与缓存。

### F07 功能卡片：任务调度推进（PENDING -> READY/SKIPPED）

1) 功能名称：任务调度推进。  
2) 功能目标（一句话）：按依赖状态把待执行任务推进到可执行状态。  
3) 用户/触发入口：`TaskSchedulerDaemon`（`@Scheduled`）。  
4) 业务范围与边界：只处理 `PENDING`；不执行 LLM 调用。  
5) 输入/输出契约：  
输入：仓储中的 `PENDING` 任务与同 plan 状态映射。  
输出：状态变更结果 `promoted/skipped/waiting/error`。  
6) 核心流程：拉取 PENDING -> 按 plan 分组 -> 计算 `DependencyDecision` -> `markReady` 或 `skip` -> 持久化。  
7) 模块与依赖：  
内部：`TaskSchedulerDaemon`、`TaskScheduleApplicationService`、`TaskDependencyPolicy`。  
外部：`agent_tasks`。  
8) 数据与状态设计：依赖节点来自 `dependency_node_ids`；状态机严格受 `AgentTaskEntity` 约束。  
9) 技术方案与选型理由：采用周期轮询调度，简单稳定；备选事件驱动调度未选，因当前单机阶段复杂度收益比低。  
10) 非功能性设计：默认 1s 轮询，可调；失败单任务隔离不阻断全批次。  
11) 风险清单与应对：全量扫描成本上升 -> 后续分批分页；依赖图错误 -> 编译阶段提前校验。  
12) 可观测与排障指南：日志 `Task schedule round finished`，关注 promoted/skipped 比例。  
13) 测试与验收：`TaskScheduleApplicationServiceTest`、`TaskDependencyPolicyDomainServiceTest`。  
14) 配置与部署：`scheduler.poll-interval-ms`。  
15) 改进建议：P1 引入 backlog-aware 动态批次；P2 多队列优先级调度。

### F08 功能卡片：任务执行与质量评估

1) 功能名称：任务执行与质量评估。  
2) 功能目标（一句话）：执行 READY/REFINING 任务，完成调用、评估、回滚、持久化与事件发布。  
3) 用户/触发入口：`TaskExecutor.executeReadyTasks()` 定时触发。  
4) 业务范围与边界：支持 worker/critic、超时重试、工具策略约束；不覆盖跨服务分布式调度。  
5) 输入/输出契约：  
输入：`AgentTaskEntity` + `AgentPlanEntity` + `configSnapshot`（含 `toolPolicy/validationSchema`）。  
输出：`task_executions`、`quality_evaluation_events`、`plan_task_events`、任务状态更新。  
6) 核心流程：claim -> 开心跳 -> 构造 prompt -> 选 Agent/工具 -> 调用 LLM（带 timeout）-> 评估/critic 分支 -> 保存 execution -> 更新 task -> 发布事件。  
7) 模块与依赖：  
内部：`TaskExecutor`、`TaskExecutionRunner`、`TaskExecutionClientResolver`、`TaskExecutionRuntimeSupport`、`AgentFactoryImpl`。  
外部：`agent_tasks/task_executions/quality_evaluation_events/agent_registry/agent_tools/agent_tool_catalog`。  
8) 数据与状态设计：claim 代际 `claim_owner + execution_attempt` 防止旧执行者回写；超时与评估结果结构化落库。  
9) 技术方案与选型理由：采用“调度器 + 运行器 + 支持适配器”分层；备选单类一体化未选，因可维护性差。  
10) 非功能性设计：`timeout-ms`、`timeout-retry-max`、lease 心跳、并发限制、丰富指标。  
11) 风险清单与应对：模型抖动超时 -> 有限重试 + 指标告警；工具策略误配 -> 审计日志 + strict 模式保护。  
12) 可观测与排障指南：指标如 `agent.task.execution.timeout.total`、`agent.task.claim.*`；日志含 `taskId/nodeId/errorType`。  
13) 测试与验收：`TaskExecutionRunnerTest`、`TaskExecutorPlanBoundaryTest`、`TaskPersistenceApplicationServiceTest`、`ExecutorTerminalConvergenceIntegrationTest`。  
14) 配置与部署：`executor.claim.*`、`executor.execution.*`、`executor.agent.*`、`executor.observability.*`。  
15) 改进建议：P0 建立执行 SLO；P1 引入模型路由策略；P2 任务级熔断与隔离仓。

### F09 功能卡片：Plan 状态同步与回合终态收敛

1) 功能名称：Plan 状态同步与回合终态收敛。  
2) 功能目标（一句话）：把任务聚合状态收敛为计划终态，并幂等生成最终用户可见结果。  
3) 用户/触发入口：`PlanStatusDaemon.syncPlanStatuses()`。  
4) 业务范围与边界：处理 READY/RUNNING plan；终态触发 finalize；不处理历史归档。  
5) 输入/输出契约：  
输入：`agent_plans` + `agent_tasks` 汇总统计。  
输出：`agent_plans.status` 更新、`session_turns` 终态、最终 assistant 消息、`PLAN_FINISHED` 事件。  
6) 核心流程：加载计划 -> 统计任务状态 -> 依据 `PlanTransitionDomainService` 迁移 -> 终态调用 `TurnFinalizeApplicationService` -> 发布完成事件。  
7) 模块与依赖：  
内部：`PlanStatusDaemon`、`PlanStatusSyncApplicationService`、`TurnFinalizeApplicationService`、`PlanFinalizationDomainService`。  
外部：`agent_plans/agent_tasks/session_turns/session_messages/plan_task_events`。  
8) 数据与状态设计：`markTerminalIfNotTerminal` + assistant 最终消息唯一约束防重；支持 optimistic lock 冲突容忍。  
9) 技术方案与选型理由：采用后台聚合守护保证主执行链简单；备选同步收敛未选，因执行链路耦合更重。  
10) 非功能性设计：批量处理与上限控制，冲突识别后跳过重试。  
11) 风险清单与应对：计划卡在 RUNNING -> 关注未终态任务与 lease；重复 finalize -> 由幂等写保护。  
12) 可观测与排障指南：指标 `agent.plan.finalize.*`；日志 `TURN_FINALIZED` 与 reconcile warn。  
13) 测试与验收：`PlanStatusDaemonTest`、`PlanStatusSyncApplicationServiceTest`、`PlanFinalizationDomainServiceTest`。  
14) 配置与部署：`plan-status.poll-interval-ms`、`plan-status.batch-size`、`plan-status.max-plans-per-round`。  
15) 改进建议：P1 终态延迟分布监控；P2 失败自动诊断报告。

### F10 功能卡片：任务控制与产物导出

1) 功能名称：任务控制与产物导出。  
2) 功能目标（一句话）：提供任务生命周期人工干预与结果导出能力。  
3) 用户/触发入口：`/api/tasks/{id}`、`/api/tasks/{id}/pause|resume|cancel|retry-from-failed|export`、`/api/plans/{id}/events`。  
4) 业务范围与边界：可控制 plan 状态与 failed 任务重试；不支持跨计划批量操作。  
5) 输入/输出契约：  
输入：`taskId`，导出 `format=markdown|json`。  
输出：`TaskDetailDTO` 或导出内容 `fileName/contentType/content`。  
6) 核心流程：校验 task/plan -> 状态转换合法性检查 -> 更新 plan/task -> 组装导出内容。  
7) 模块与依赖：  
内部：`TaskActionController`、`TaskActionCommandService`。  
外部：`agent_tasks/agent_plans/task_executions`。  
8) 数据与状态设计：状态机使用领域实体约束；重试会清理 claim 并把 task 回滚 READY。  
9) 技术方案与选型理由：操作命令集中在应用服务便于审计；备选各接口直连仓储未选，因规则易分散。  
10) 非功能性设计：导出内容生成在内存，适合当前体量。  
11) 风险清单与应对：并发操作冲突 -> 依赖 optimistic lock；误操作取消 -> 增加审计记录与确认机制。  
12) 可观测与排障指南：查 `TaskActionCommandService` 异常消息与 plan/task 当前状态。  
13) 测试与验收：[假设] 目前以集成链路覆盖为主，建议补充任务动作专门测试。  
14) 配置与部署：无独立运行开关。  
15) 改进建议：P1 增加操作审计事件；P2 增加批量操作与审批。

### F11 功能卡片：分享链接管理与匿名访问

1) 功能名称：分享链接管理与匿名访问。  
2) 功能目标（一句话）：安全地分享任务结果给未登录访问者，并支持随时吊销。  
3) 用户/触发入口：`/api/tasks/{id}/share-links*`、`GET /api/share/tasks/{id}`。  
4) 业务范围与边界：支持创建/查询/撤销/全撤销；匿名访问需要 `code + token`；不支持细粒度内容脱敏配置。  
5) 输入/输出契约：  
输入：创建可传 `expiresHours`，匿名访问需 `code/token`。  
输出：分享链接元数据与 `shareUrl`，匿名访问返回任务结果与参考信息。  
6) 核心流程：生成随机 token 与 shareCode -> token 加盐哈希落库 -> 访问时校验存在性/吊销/过期/哈希匹配 -> 返回结果。  
7) 模块与依赖：  
内部：`TaskActionCommandService`、`ShareAccessController`。  
外部：`task_share_links`、`agent_tasks`、`plan_task_events`。  
8) 数据与状态设计：`task_share_links` 记录 `revoked/expires_at/token_hash`；状态 `ACTIVE/EXPIRED/REVOKED`。  
9) 技术方案与选型理由：一次性 token + 哈希存储降低泄露风险；备选明文 token 存库未选，安全性差。  
10) 非功能性设计：TTL 上限可配置；哈希对比使用常量时间比较。  
11) 风险清单与应对：URL token 外泄 -> 缩短 TTL + 审计访问；暴力猜测 -> 增加访问频控与失败告警。  
12) 可观测与排障指南：核查 `task_share_links` 中 revoked/expires/tokenHash；查看匿名访问错误返回。  
13) 测试与验收：`ShareAccessControllerIntegrationTest`、`TaskShareLinkControllerIntegrationTest`。  
14) 配置与部署：`app.share.base-url`、`app.share.token-salt`、`app.share.max-ttl-hours`。  
15) 改进建议：P1 增加访问审计表；P2 把 token 从 URL 迁移到短期签名票据。

### F12 功能卡片：控制台分页查询与质量回放

1) 功能名称：控制台分页查询与质量回放。  
2) 功能目标（一句话）：为治理端提供可分页、可过滤、可回溯的运营视图。  
3) 用户/触发入口：`/api/sessions/list`、`/api/tasks/paged`、`/api/tasks/{id}`、`/api/plans/{id}/events`、`/api/logs/paged`、`/api/logs/tool-policy/paged`、`/api/quality/evaluations/*`、`/api/dashboard/overview`。  
4) 业务范围与边界：覆盖会话、任务、日志、工具策略日志、质量事件、仪表盘；知识库 retrieval-test 占位接口已下线。  
5) 输入/输出契约：  
输入：分页参数 `page/size` 与多维过滤条件。  
输出：统一分页结构 `page/size/total/items` 或聚合摘要。  
6) 核心流程：参数归一 -> DB 侧计数/分页 -> DTO 组装 -> 返回。  
7) 模块与依赖：  
内部：`ConsoleQueryController`、`QueryController`、`TaskDetailViewAssembler`。  
外部：`agent_sessions/agent_plans/agent_tasks/plan_task_events/quality_evaluation_events/vector_store_registry`。  
8) 数据与状态设计：日志来自 `plan_task_events`；质量回放来自 `quality_evaluation_events`，支持实验维度汇总。  
9) 技术方案与选型理由：读接口收敛并强调 DB 下推分页；备选内存聚合未选，因大数据量不稳定。  
10) 非功能性设计：分页上限控制（通常 <=100）；常用索引在初始化 SQL 中已提供。  
11) 风险清单与应对：复杂过滤慢查询 -> 加索引与查询计划巡检；跨用户查询风险 -> 后续补充 owner 校验。  
12) 可观测与排障指南：先看慢请求日志，再看 SQL 执行计划与索引命中。  
13) 测试与验收：`QueryControllerPerformanceTest`、`ConsoleQueryControllerPerformanceTest`。  
14) 配置与部署：无独立配置，受全局日志与 DB 连接池配置影响。  
15) 改进建议：P1 增加按时间分区与归档策略；P2 增加离线报表任务。

### F13 功能卡片：观测告警目录与巡检

1) 功能名称：观测告警目录与巡检。  
2) 功能目标（一句话）：把告警规则、看板、runbook 与健康状态统一为可查询可巡检资产。  
3) 用户/触发入口：`GET /api/observability/alerts/catalog`、`GET /api/observability/alerts/probe-status`、`ObservabilityAlertCatalogProbeJob`。  
4) 业务范围与边界：支持目录加载、dashboard 占位符替换、链接探测、趋势快照；不直接管理 Prometheus 规则生命周期。  
5) 输入/输出契约：  
输入：probe-status 支持 `window` 参数。  
输出：目录内容与巡检状态（`status/failureRate/trend/issues/envStats/moduleStats`）。  
6) 核心流程：启动加载 `alert-catalog.json` -> 定时探测 dashboard/runbook -> 写入状态存储 -> 对外查询。  
7) 模块与依赖：  
内部：`ObservabilityAlertCatalogController`、`ObservabilityAlertCatalogLinkProbeService`、`ObservabilityAlertCatalogProbeStateStore`、`ObservabilityAlertCatalogProbeJob`。  
外部：`agent-app/src/main/resources/observability/alert-catalog.json`。  
8) 数据与状态设计：状态保存在进程内快照，保留最近 N 次历史用于趋势计算。  
9) 技术方案与选型理由：轻量巡检实现成本低；备选外部健康检查平台未选，因当前阶段先保证自检闭环。  
10) 非功能性设计：HTTP 超时、巡检间隔、历史窗口、趋势阈值均可配置。  
11) 风险清单与应对：误报或网络抖动 -> 趋势窗口 + 阈值降噪；目录占位符未替换 -> 启动告警提示。  
12) 可观测与排障指南：查 probe-status，再看巡检日志 issue 列表。  
13) 测试与验收：`ObservabilityAlertCatalogControllerTest`、`ObservabilityAlertCatalogLinkProbeServiceTest`、`ObservabilityAlertCatalogProbeStateStoreTest`。  
14) 配置与部署：`observability.alert-catalog.dashboard.*`、`observability.alert-catalog.link-check.*`。  
15) 改进建议：P1 增加探测结果持久化；P2 增加与告警平台自动对账。

---

## D. 假设

### D1. 基于现有材料的【假设】

- 【假设】当前生产部署仍以单机或小规模多实例为主，未引入独立消息队列。
- 【假设】对象存储不在主链路（仓库未见对象存储适配层）。
- 【假设】当前权限模型仍是“单账号治理 + 会话级 userId”，尚未启用组织级隔离。
