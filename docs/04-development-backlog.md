# 开发任务清单（按业务域）

## 1. 文档目标

- 提供可执行的业务域台账：已完成、未完成、计划优化、证据与验收。
- 作为后续迭代唯一基线，避免偏题投入与重复建设。
- 与 `docs/01`、`docs/02`、`docs/03` 保持范围一致。

## 2. 阶段总览（2026-02）

- 核心链路闭环：会话编排（V3）/执行终态/SSE/观测日志。
- 前端主路径收口为 ChatGPT 风格：新聊天直达、历史左置、进度右置。
- V1/V2 旧入口代码已清理，仅保留 V3 主链路。
- 当前范围外：登录与 RBAC（明确延后，不在本轮交付）。

## 3. 业务域台账

### 3.1 会话与规划（Session + Planner）

- 状态：`已完成`
- 优先级：`P0`

**已完成功能**
- V3 聚合入口：`POST /api/v3/chat/messages`。
- V3 历史聚合：`GET /api/v3/chat/sessions/{id}/history`。
- V3 路由决策：`GET /api/v3/chat/plans/{id}/routing`。
- 旧入口代码清理：`/api/v2/agents/*`、`/api/v2/sessions*`、`/api/v2/plans/{id}/routing`、`/api/sessions/{id}/chat`、`/api/plans/{id}/stream`。
- 旧只读查询入口收敛：移除 `/api/sessions/{id}`、`/api/sessions/{id}/plans`、`/api/sessions/{id}/overview`、`/api/tasks`、`/api/logs`，统一走 `/api/v3/chat/sessions/{id}/history` + 分页查询接口。
- 会话编排迁移至 `trigger.application.command.ChatConversationCommandService`，`ChatV3Controller` 仅保留协议适配。
- 会话核心策略下沉至 `domain.session.service.SessionConversationDomainService`（默认 Agent、标题、上下文、失败语义）。
- Planner 输入校验增强：`inputSchema.required` 缺失系统字段（如 `sessionId`）时，先从运行时上下文自动补全再校验，避免创建会话后首轮报错。
- Graph DSL v2 上线：Planner 仅接收 `version=2` 图定义，支持 `nodes/edges/groups` 归一化与边界边过滤。
- Root 候选 Draft 版本兼容升级：候选草案仅在 `version` 不兼容但节点可执行时自动补齐为 v2，并补齐缺省 `groups/edges`，减少无效重试。
- Root 候选 Draft 结构非法快速降级：候选草案被判定为确定性结构错误时（如边引用不存在节点）不再走满重试次数，直接降级单节点 Draft。
- Root 规划软超时快速降级：单次 Root 规划超出 `planner.root.timeout.soft-ms` 后视为不可重试，首次超时即降级并记录真实尝试次数。
- Graph 依赖策略注入：节点/分组 `joinPolicy/failurePolicy/quorum` 写入 `task.configSnapshot.graphPolicy`，供调度统一判定。
- Workflow 治理接口增强：Draft 更新/发布阶段增加 Graph DSL v2 结构校验。
- SOP 治理分层落地：`sopSpec` 作为治理源数据，保存时自动编译为 Runtime Graph（`graphDefinition`）。
- SOP 编译/校验接口：`POST /api/workflows/sop-spec/drafts/{id}/compile`、`POST /api/workflows/sop-spec/drafts/{id}/validate`。
- Workflow Draft 页新增图形化 SOP 编排：节点拖拽、依赖连线、策略编辑、编译预览与保存闭环。
- 发布一致性保护：Draft 含 `sopSpec` 时，发布阶段强制校验 `compileHash` 与 Runtime Graph 一致。

**证据**
- 测试：`ConversationOrchestratorServiceTest`、`ChatV3ControllerTest`、`ChatRoutingV3ControllerTest`、`SessionConversationDomainServiceTest`、`PlannerServiceRootDraftTest`

**未完成 / 计划优化**
- `P1`：V3 请求体高级参数（上下文覆写）前端可视化配置。
- `P1`：SOP 图形化编排补齐分组批量操作与高级校验提示（如循环依赖路径可视化）。

---

### 3.2 执行与终态收敛（Executor + Plan Status + Turn Result）

- 状态：`已完成`
- 优先级：`P0`

**已完成功能**
- claim/lease/executionAttempt 并发语义收口。
- `TaskExecutor` 的 claim 配额、超时重试、提示词构造、输出判定、Critic 回滚、Agent 选择、黑板写回与 JSON 解析规则分别下沉至 `TaskDispatchDomainService`、`TaskExecutionDomainService`、`TaskPromptDomainService`、`TaskEvaluationDomainService`、`TaskRecoveryDomainService`、`TaskAgentSelectionDomainService`、`TaskBlackboardDomainService`、`TaskJsonDomainService`。
- 持久化失败处理与重试收口：`TaskPersistenceApplicationService` 统一承接 `safeUpdateTask/safeUpdateClaimedTask/safeSaveExecution` 与 Plan 黑板写回重试分支。
- 持久化策略语义下沉：`TaskPersistencePolicyDomainService` 统一识别乐观锁冲突、重试判定与错误归一化。
- 终态收敛幂等：先抢占终态，再写最终 assistant 消息。
- finalize 去重与老代执行者回写拒绝。
- 终态汇总迁移至 `trigger.application.command.TurnFinalizeApplicationService`，`PlanStatusDaemon` 仅调用应用用例。
- job 壳层化：`PlanStatusDaemon` 编排收口到 `PlanStatusSyncApplicationService`，`TaskSchedulerDaemon` 编排收口到 `TaskScheduleApplicationService`。
- 终态与状态推进规则下沉到 `PlanFinalizationDomainService`、`PlanTransitionDomainService`，依赖判定规则下沉到 `TaskDependencyPolicyDomainService`。
- 计划终态收敛支持 Fail-Safe：`PlanStatusSyncApplicationService` 在 FAILED 聚合时按 `TaskFailurePolicyDomainService` 过滤可容忍失败，避免误判计划失败。
- `trigger.service` 兼容包装类已删除（统一 `trigger.application` -> `domain`）。

**证据**
- 测试：`TaskExecutorPlanBoundaryTest`、`TurnResultServiceTest`、`PlanStatusDaemonTest`、`PlanFinalizationDomainServiceTest`、`PlanTransitionDomainServiceTest`、`TaskExecutionDomainServiceTest`、`TaskPromptDomainServiceTest`、`TaskEvaluationDomainServiceTest`、`TaskRecoveryDomainServiceTest`、`TaskAgentSelectionDomainServiceTest`、`TaskBlackboardDomainServiceTest`、`TaskJsonDomainServiceTest`、`TaskPersistencePolicyDomainServiceTest`、`TaskPersistenceApplicationServiceTest`、`TaskDependencyPolicyDomainServiceTest`、`TaskScheduleApplicationServiceTest`、`PlanStatusSyncApplicationServiceTest`、`ApplicationDomainBoundaryTest`

**未完成 / 计划优化**
- `P0`：并发压测常态化，校准超时与重试阈值。
- `P1`：图治理后台补齐可视化编辑（groups/joinPolicy/quorum/failurePolicy）。

---

### 3.3 实时流与回放（SSE）

- 状态：`已完成`
- 优先级：`P0`

**已完成功能**
- V3 聊天语义 SSE：`message.accepted`、`planning.started`、`task.progress`、`task.completed`、`answer.finalizing`、`answer.final`、`stream.completed`。
- 游标回放补偿（`Last-Event-ID` 优先）。
- 最终结果从终态消息读取，避免中间态误作为最终输出。
- 前端消费层自动恢复：断链指数退避重连（最多 3 次）+ 历史轮询兜底（1.5s 间隔，30s 超时）。
- 重连提示降噪：断链先静默同步历史；仅在确认执行未结束时进入重连提示；同类 `stream.error` 10 秒去重。
- SSE 抖动容错：`onerror` 触发时若 22 秒内有心跳/事件，则视为短暂链路波动并静默等待浏览器自动恢复，不立即重建连接。
- SSE 服务端抗抖：响应头关闭代理缓冲 + 重连订阅仅回放漏事件（不重复引导事件），减少重连噪音与重复状态提示。

**证据**
- 测试：`ChatStreamV3ControllerTest`
- 压测脚本：`scripts/chat_e2e_perf.py`（会话发送 -> SSE 终态收敛）

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
- 修复会话页“需手动刷新才更新”：`answer.final` 与断链场景均自动刷新历史并收敛终态。
- 发送超时收敛：提交请求 15 秒超时后转入后台自动恢复，不再长期占用发送按钮 loading。
- 乐观发送：点击发送后立即清空输入框并将用户消息落入聊天区（`PENDING`），降低“未发送出去”的体感。
- 失败可恢复：自动恢复未命中时将本地消息标记 `FAILED` 并提供重试入口，直接复用同一 `clientMessageId` 幂等提交。
- 执行期主动同步：执行中每 2.5 秒静默同步历史快照，降低 SSE 波动下的前端停滞感。

**证据**
- 构建：`cd frontend && npm run build`

**未完成 / 计划优化**
- `P1`：移动端会话页体验优化。
- `P1`：执行时间线过滤、折叠与节点分组。

## 4. 旧入口清理结果

- 结果：`已完成`
- 清理范围：
  - `/api/v2/agents/active`
  - `/api/v2/agents`
  - `/api/v2/sessions`
  - `/api/v2/sessions/{id}/turns`
  - `/api/v2/plans/{id}/routing`
  - `/api/sessions/{id}/chat`
  - `/api/plans/{id}/stream`
  - `/api/sessions/{id}`
  - `/api/sessions/{id}/plans`
  - `/api/sessions/{id}/overview`
  - `/api/tasks`
  - `/api/logs`
- 行为：以上入口代码已移除，不再保留迁移提示分支。
- 替代接口：
  - `POST /api/v3/chat/messages`
  - `GET /api/v3/chat/sessions/{id}/history`
  - `GET /api/v3/chat/sessions/{id}/stream?planId=...`
  - `GET /api/v3/chat/plans/{id}/routing`
  - `GET /api/sessions/list`
  - `GET /api/tasks/paged`
  - `GET /api/logs/paged`

## 5. 回归基线命令

- Docker 集成测试前置（Docker Desktop on macOS）：
  - `export DOCKER_HOST=unix:///Users/${USER}/.docker/run/docker.sock`
  - `export DOCKER_API_VERSION=1.44`
- 后端回归：
  - `mvn -pl agent-app -am -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ConversationOrchestratorServiceTest,ChatV3ControllerTest,ChatRoutingV3ControllerTest,ChatStreamV3ControllerTest,TaskExecutorPlanBoundaryTest,TurnResultServiceTest,PlanStatusDaemonTest,ControllerArchitectureTest,SessionConversationDomainServiceTest,PlanFinalizationDomainServiceTest,ApplicationDomainBoundaryTest,PlanTransitionDomainServiceTest,TaskExecutionDomainServiceTest,TaskPromptDomainServiceTest,TaskEvaluationDomainServiceTest,TaskRecoveryDomainServiceTest,TaskAgentSelectionDomainServiceTest,TaskBlackboardDomainServiceTest,TaskJsonDomainServiceTest,TaskPersistencePolicyDomainServiceTest,TaskPersistenceApplicationServiceTest,TaskDependencyPolicyDomainServiceTest,TaskScheduleApplicationServiceTest,PlanStatusSyncApplicationServiceTest test`
- 前端构建：
  - `cd frontend && npm run build`

## 6. 当前范围声明（强约束）

- 当前阶段聚焦核心业务闭环与性能稳定性。
- 登录、RBAC、系统配置治理后台为范围外事项。
- 新增需求需先映射业务域并标注优先级与验收证据。
