# 开发任务清单（按业务域）

## 1. 文档目标

- 提供可执行的业务域台账：已完成、未完成、计划优化、证据与验收。
- 作为后续迭代唯一基线，避免偏题投入与重复建设。
- 与 `docs/01`、`docs/02`、`docs/03` 保持范围一致。

## 2. 阶段总览（2026-02）

- 核心链路闭环：会话编排（V3）/执行终态/SSE/观测日志。
- 前端主路径收口为 ChatGPT 风格：新聊天直达、历史左置、进度右置。
- V2 编排入口已全量下线并返回迁移提示。
- 当前范围外：登录与 RBAC（明确延后，不在本轮交付）。

## 3. 业务域台账

### 3.1 会话与规划（Session + Planner）

- 状态：`已完成`
- 优先级：`P0`

**已完成功能**
- V3 聚合入口：`POST /api/v3/chat/messages`。
- V3 历史聚合：`GET /api/v3/chat/sessions/{id}/history`。
- V3 路由决策：`GET /api/v3/chat/plans/{id}/routing`。
- V2 编排入口下线：`/api/v2/agents/*`、`/api/v2/sessions*`、`/api/v2/plans/{id}/routing`。
- 会话编排迁移至 `trigger.application.command.ChatConversationCommandService`，`ChatV3Controller` 仅保留协议适配。
- 会话核心策略下沉至 `domain.session.service.SessionConversationDomainService`（默认 Agent、标题、上下文、失败语义）。

**证据**
- 测试：`ConversationOrchestratorServiceTest`、`ChatV3ControllerTest`、`ChatRoutingV3ControllerTest`、`SessionConversationDomainServiceTest`、`AgentV2ControllerTest`、`SessionV2ControllerTest`、`TurnV2ControllerTest`、`PlanRoutingV2ControllerTest`

**未完成 / 计划优化**
- `P1`：V3 请求体高级参数（上下文覆写）前端可视化配置。

---

### 3.2 执行与终态收敛（Executor + Plan Status + Turn Result）

- 状态：`已完成`
- 优先级：`P0`

**已完成功能**
- claim/lease/executionAttempt 并发语义收口。
- `TaskExecutor` 的 claim 配额、超时重试、提示词构造、输出判定、Critic 回滚、Agent 选择与黑板写回规则分别下沉至 `TaskDispatchDomainService`、`TaskExecutionDomainService`、`TaskPromptDomainService`、`TaskEvaluationDomainService`、`TaskRecoveryDomainService`、`TaskAgentSelectionDomainService`、`TaskBlackboardDomainService`。
- 终态收敛幂等：先抢占终态，再写最终 assistant 消息。
- finalize 去重与老代执行者回写拒绝。
- 终态汇总迁移至 `trigger.application.command.TurnFinalizeApplicationService`，`PlanStatusDaemon` 仅调用应用用例。
- 终态与状态推进规则下沉到 `PlanFinalizationDomainService`、`PlanTransitionDomainService`。
- `trigger.service` 兼容包装类已删除（统一 `trigger.application` -> `domain`）。

**证据**
- 测试：`TaskExecutorPlanBoundaryTest`、`TurnResultServiceTest`、`PlanStatusDaemonTest`、`PlanFinalizationDomainServiceTest`、`PlanTransitionDomainServiceTest`、`TaskExecutionDomainServiceTest`、`TaskPromptDomainServiceTest`、`TaskEvaluationDomainServiceTest`、`TaskRecoveryDomainServiceTest`、`TaskAgentSelectionDomainServiceTest`、`TaskBlackboardDomainServiceTest`、`ApplicationDomainBoundaryTest`

**未完成 / 计划优化**
- `P0`：并发压测常态化，校准超时与重试阈值。

---

### 3.3 实时流与回放（SSE）

- 状态：`已完成`
- 优先级：`P0`

**已完成功能**
- V3 聊天语义 SSE：`message.accepted`、`planning.started`、`task.progress`、`task.completed`、`answer.finalizing`、`answer.final`。
- 游标回放补偿（`Last-Event-ID` 优先）。
- 最终结果从终态消息读取，避免中间态误作为最终输出。

**证据**
- 测试：`ChatStreamV3ControllerTest`、`PlanStreamControllerTest`

**未完成 / 计划优化**
- `P1`：流事件分组视图与节点过滤。

---

### 3.4 观测与日志（Observability）

- 状态：`已完成`
- 优先级：`P0`

**已完成功能**
- HTTP 入口统一日志与 `traceId/requestId` 注入。
- 总览指标支持 P95/P99、慢任务、SLA 违约。
- 日志检索 DB 侧过滤 + 计数 + 分页。

**证据**
- 测试：`ConsoleQueryControllerPerformanceTest`、`ObservabilityAlertCatalogControllerTest`

**未完成 / 计划优化**
- `P1`：告警目录 dashboard 链接按环境自动替换与巡检。

---

### 3.5 前端控制台主路径（UI 主链路）

- 状态：`已完成（核心可用）`
- 优先级：`P0`

**已完成功能**
- `/sessions` 一体化：历史侧栏 + 主聊天区 + 执行进度侧栏。
- 新消息仅调用 V3 接口。
- 中间态与最终态分离：仅 `answer.final` 作为最终结果。
- 删除旧入口页面：`SessionListPage`。

**证据**
- 构建：`cd frontend && npm run build`

**未完成 / 计划优化**
- `P1`：移动端会话页体验优化。
- `P1`：执行时间线过滤、折叠与节点分组。

## 4. V2 下线结果（步骤E）

- 结果：`已完成`
- 下线范围：
  - `/api/v2/agents/active`
  - `/api/v2/agents`
  - `/api/v2/sessions`
  - `/api/v2/sessions/{id}/turns`
  - `/api/v2/plans/{id}/routing`
- 行为：以上接口统一返回迁移提示。
- 替代接口：
  - `POST /api/v3/chat/messages`
  - `GET /api/v3/chat/sessions/{id}/history`
  - `GET /api/v3/chat/sessions/{id}/stream?planId=...`
  - `GET /api/v3/chat/plans/{id}/routing`

## 5. 回归基线命令

- 后端回归：
  - `mvn -pl agent-app -am -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentV2ControllerTest,SessionV2ControllerTest,TurnV2ControllerTest,PlanRoutingV2ControllerTest,ConversationOrchestratorServiceTest,ChatV3ControllerTest,ChatRoutingV3ControllerTest,ChatStreamV3ControllerTest,TaskExecutorPlanBoundaryTest,TurnResultServiceTest,PlanStatusDaemonTest,ControllerArchitectureTest,SessionConversationDomainServiceTest,PlanFinalizationDomainServiceTest,ApplicationDomainBoundaryTest,PlanTransitionDomainServiceTest,TaskExecutionDomainServiceTest,TaskPromptDomainServiceTest,TaskEvaluationDomainServiceTest,TaskRecoveryDomainServiceTest,TaskAgentSelectionDomainServiceTest,TaskBlackboardDomainServiceTest test`
- 前端构建：
  - `cd frontend && npm run build`

## 6. 当前范围声明（强约束）

- 当前阶段聚焦核心业务闭环与性能稳定性。
- 登录、RBAC、系统配置治理后台为范围外事项。
- 新增需求需先映射业务域并标注优先级与验收证据。
