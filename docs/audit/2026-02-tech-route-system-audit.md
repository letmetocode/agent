# 2026-02 技术路线/实现路径系统审计报告

## 审计范围与方法
- 审计目标：基于当前 PRD 与实际代码实现，评估“技术路线/实现路径”是否符合主流工程实践、产品定位（SOP 模板创建/复用 -> 输出质量提升 -> 用户生态）、以及是否存在冗余/过期/无效抽象。
- 证据来源：`docs/01-product-requirements.md`、`docs/02-system-architecture.md`、`README.md`、`docs/dev-ops/postgresql/sql/01_init_database.sql` 以及 `agent-*` 模块源码。
- 结论口径：仅依据“当前实际实现”，不按设想；所有判断均附路径级证据。

## A. 技术路线清单（按实际实现）

| 路线名称 | 入口/触发点 | 关键模块/文件路径 | 主要数据结构/表 | 依赖组件 |
| --- | --- | --- | --- | --- |
| 1. 会话编排与异步规划（V3） | `POST /api/v3/chat/messages` | `agent-trigger/src/main/java/com/getoffer/trigger/http/ChatV3Controller.java`<br>`agent-trigger/src/main/java/com/getoffer/trigger/application/command/ChatConversationCommandService.java`<br>`agent-domain/src/main/java/com/getoffer/domain/session/service/SessionConversationDomainService.java` | `agent_sessions` `session_turns` `session_messages` `agent_plans` `routing_decisions` | Spring MVC、线程池 `commonThreadPoolExecutor`、`PlannerService` |
| 2. 路由与编排引擎（Definition 命中 + Root Draft 兜底） | `PlannerService.createPlan(...)`，由会话编排异步触发 | `agent-infrastructure/src/main/java/com/getoffer/infrastructure/planning/PlannerServiceImpl.java`<br>`agent-infrastructure/src/main/java/com/getoffer/infrastructure/planning/RootWorkflowDraftPlannerImpl.java`<br>`agent-domain/src/main/java/com/getoffer/domain/planning/service/PlannerFallbackPolicyDomainService.java`<br>`agent-app/src/main/java/com/getoffer/config/RootAgentHealthCheckRunner.java` | `workflow_definitions` `workflow_drafts` `routing_decisions` `agent_plans` `agent_tasks` | Spring AI `ChatClient`、`IAgentRegistryRepository`、Micrometer |
| 3. 模板治理（SOP Spec -> Runtime Graph -> 发布版本） | `/api/workflows/**`、`/api/workflows/sop-spec/**` | `agent-trigger/src/main/java/com/getoffer/trigger/http/WorkflowGovernanceController.java`<br>`agent-trigger/src/main/java/com/getoffer/trigger/application/command/SopSpecCompileService.java`<br>`agent-domain/src/main/java/com/getoffer/domain/planning/service/GraphDslPolicyService.java` | `workflow_drafts` `workflow_definitions`（`version`、`node_signature`、`constraints.compileHash`） | ObjectMapper、MyBatis 仓储实现 |
| 4. 任务调度执行状态机与事件流 | `@Scheduled`（调度/执行/聚合）+ `GET /api/v3/chat/sessions/{id}/stream` | `agent-trigger/src/main/java/com/getoffer/trigger/job/TaskSchedulerDaemon.java`<br>`agent-trigger/src/main/java/com/getoffer/trigger/job/TaskExecutor.java`<br>`agent-trigger/src/main/java/com/getoffer/trigger/job/TaskExecutionRunner.java`<br>`agent-trigger/src/main/java/com/getoffer/trigger/job/PlanStatusDaemon.java`<br>`agent-trigger/src/main/java/com/getoffer/trigger/http/ChatStreamV3Controller.java`<br>`agent-trigger/src/main/java/com/getoffer/trigger/event/PlanTaskEventPublisher.java` | `agent_tasks` `task_executions` `agent_plans` `plan_task_events` | Micrometer、PostgreSQL LISTEN/NOTIFY、SSE |
| 5. LLM/工具调用与 Agent 选路 | 任务执行阶段 `resolveTaskClient` 与 Root 草案生成 | `agent-trigger/src/main/java/com/getoffer/trigger/job/TaskExecutionClientResolver.java`<br>`agent-domain/src/main/java/com/getoffer/domain/task/service/TaskAgentSelectionDomainService.java`<br>`agent-infrastructure/src/main/java/com/getoffer/infrastructure/ai/AgentFactoryImpl.java`<br>`agent-infrastructure/src/main/java/com/getoffer/infrastructure/mcp/McpClientManager.java` | `agent_registry` `agent_tool_catalog` `agent_tools` `vector_store_registry` | Spring AI、MCP 协议客户端、ToolCallback |
| 6. 质量提升闭环（校验/批评/回滚/黑板/终态） | `TaskExecutionRunner` 的 validation/critic 分支 + 终态收敛 | `agent-domain/src/main/java/com/getoffer/domain/task/service/TaskEvaluationDomainService.java`<br>`agent-domain/src/main/java/com/getoffer/domain/task/service/TaskRecoveryDomainService.java`<br>`agent-domain/src/main/java/com/getoffer/domain/task/service/TaskBlackboardDomainService.java`<br>`agent-trigger/src/main/java/com/getoffer/trigger/application/command/TaskPersistenceApplicationService.java`<br>`agent-domain/src/main/java/com/getoffer/domain/planning/service/PlanFinalizationDomainService.java`<br>`agent-trigger/src/main/java/com/getoffer/trigger/application/command/TurnFinalizeApplicationService.java` | `task_executions` `agent_tasks` `agent_plans.global_context` `session_turns` `session_messages` | JSON 解析、乐观锁重试、计划终态事件 |
| 7. 数据与存储 | 应用启动、仓储读写、本地编排脚本 | `docs/dev-ops/postgresql/sql/01_init_database.sql`<br>`agent-app/src/test/resources/sql/integration-schema.sql`<br>`agent-infrastructure/src/main/java/com/getoffer/infrastructure/repository/**`<br>`README.md`（Redis 现状） | 会话、规划、执行、事件、Agent 配置与工具关联全表族 | PostgreSQL（主）、Redis（预留未上主链路） |
| 8. 权限、多租户与分享访问 | `/api/**` 鉴权、`/api/auth/*`、`/api/share/tasks/*` | `agent-app/src/main/java/com/getoffer/config/ApiAuthFilter.java`<br>`agent-trigger/src/main/java/com/getoffer/trigger/http/AuthController.java`<br>`agent-trigger/src/main/java/com/getoffer/trigger/application/command/AuthSessionCommandService.java`<br>`agent-domain/src/main/java/com/getoffer/domain/session/service/SessionConversationDomainService.java`<br>`agent-trigger/src/main/java/com/getoffer/trigger/http/ShareAccessController.java` | `agent_sessions.user_id` `task_share_links` `workflow_* .tenant_id` | 本地账号密码、内存 token 会话、SHA-256 令牌哈希 |
| 9. 可运维与治理 | 指标上报、告警目录 API、巡检定时任务、运维脚本 | `agent-trigger/src/main/java/com/getoffer/trigger/job/TaskExecutionRuntimeSupport.java`<br>`agent-trigger/src/main/java/com/getoffer/trigger/http/ObservabilityAlertCatalogController.java`<br>`agent-trigger/src/main/java/com/getoffer/trigger/job/ObservabilityAlertCatalogProbeJob.java`<br>`docs/dev-ops/observability/**`<br>`scripts/devops/observability-gate.sh`<br>`agent-app/src/main/resources/application*.yml` | `plan_task_events`（审计/回放）、告警目录 JSON | Micrometer、Prometheus 规则、HTTP 入口日志、Profile 配置 |

## B. 三维评估（逐路线）

### B1. 会话编排与异步规划（V3）

#### 1) Mainstream Check
- 当前实现：`ChatV3Controller.submitMessage` 快速 ACK，`ChatConversationCommandService.dispatchAsyncPlanning` 通过本地线程池异步触发 `plannerService.createPlan`。
- 与主流差异：未采用持久化队列/Outbox 的异步投递；幂等主要依赖 `clientMessageId` 查询复用（`SessionTurnRepositoryImpl.findBySessionIdAndClientMessageId` + `SessionTurnMapper.xml` 的 metadata 条件），缺少数据库硬约束。
- 风险：在“ACK 成功 -> 异步规划执行前”进程异常时，回合可能停留在 `PLANNING`；并发重复请求存在竞争窗口，幂等语义偏“最终一致”而非“强一致”。
- 结论：短期可保留（PRD 当前是单机闭环，见 `docs/01-product-requirements.md:24-31`），中期应跟随主流补齐可恢复异步投递。
- 建议改法：
  - 引入 `turn_planning_outbox`（或等价表）+ 后台 Worker，替代纯内存异步派发。
  - 为 `(session_id, metadata->>'clientMessageId')` 建唯一索引或等价幂等键表。
  - 增加“长时间 PLANNING 回合”巡检与自动补偿任务。

#### 2) Product Fit
- 用户价值：快速 ACK 与异步规划显著改善“发起即反馈”体验，符合“对话式外显”定位（`docs/01-product-requirements.md:12`，`docs/02-system-architecture.md:68-88`）。
- 为技术而技术：无明显。
- 决策：保留主链路；补强可靠性，不改变交互协议。

#### 3) Redundancy & Stale（本路线）
- 问题：幂等判定只有应用层查询，无数据库强约束。
- 证据：`agent-trigger/src/main/java/com/getoffer/trigger/application/command/ChatConversationCommandService.java`、`agent-app/src/main/resources/mybatis/mapper/SessionTurnMapper.xml:83-84`。
- 处理：新增幂等唯一索引或幂等键表；冲突走“已受理”回包。
- 优先级：P1。

### B2. 路由与编排引擎（Definition 命中 + Root Draft 兜底）

#### 1) Mainstream Check
- 当前实现：优先 `findProductionActive` 命中 Definition；未命中时走 Root 草案规划，失败后按 `PlannerFallbackPolicyDomainService` 降级单节点候选 Draft。
- 与主流差异：
  - 路由匹配仍是 `routeDescription` 词元打分（`PlannerServiceImpl.matchDefinition`），未引入向量召回/语义路由。
  - Root 草案生成采用“自由文本 prompt + 解析 JSON”，契约约束主要在后置校验。
- 风险：路由召回精度与稳定性受描述文本质量影响；Root 输出漂移时重试/降级频次上升。
- 结论：
  - `PRODUCTION + ACTIVE` 优先命中与 Root 兜底机制应保留（符合模板复用导向）。
  - 路由与 Root 输出约束建议逐步跟随主流：语义路由 + 强结构化输出契约。
- 建议改法：
  - 路由层增加“规则召回 + 语义召回”双通道，保留当前词元策略作为 fallback。
  - Root 输出接入 JSON Schema 校验与版本化契约（失败分类更细，减少无效重试）。

#### 2) Product Fit
- 用户价值：是“模板复用优先、未命中自动生成候选草案”的核心路径，直接服务于产品定位（`docs/01-product-requirements.md:11`）。
- 冲突点：若路由误命中/误降级，会直接影响输出质量与用户信任。
- 决策：保留并强化，不建议下线。

#### 3) Redundancy & Stale（本路线）
- 问题：编排内核过大、规则散落（后续维护高风险）。
- 证据：`agent-infrastructure/src/main/java/com/getoffer/infrastructure/planning/PlannerServiceImpl.java`（约 2070 行）。
- 处理：拆分为 `RoutingPolicyService`、`DraftLifecycleService`、`GraphNormalizationService`、`InputBindingService`。
- 优先级：P1。

### B3. 模板治理（SOP Spec -> Runtime Graph -> 发布版本）

#### 1) Mainstream Check
- 当前实现：治理层以 `sopSpec` 为源，`SopSpecCompileService` 编译 Runtime Graph，发布前校验 `compileHash` 一致性，并写入 Definition 版本。
- 与主流差异：
  - 治理流程主要集中在 Controller（`WorkflowGovernanceController`）内完成，应用服务/领域服务边界偏薄。
  - `Map<String, Object>` 为主的数据结构，缺少显式 Schema DTO 与迁移器。
- 风险：接口变更容易牵连编译/发布规则；字段漂移、数据兼容与错误定位成本上升。
- 结论：技术方向正确（治理源与运行时分离符合主流），实现形态应收敛到“强契约 + 分层编排”。
- 建议改法：
  - 新增 `WorkflowGovernanceApplicationService`，Controller 仅做协议映射。
  - 将 `sopSpec` 与 Runtime Graph 建立版本化 DTO + JSON Schema。
  - 引入 Draft/Definition 变更审计事件（谁在何时改了什么）。

#### 2) Product Fit
- 用户价值：模板创建、编辑、发布是产品核心价值路径（`docs/01-product-requirements.md:76-80`）。
- 为技术而技术：无。
- 决策：保留并强化治理一致性。

#### 3) Redundancy & Stale（本路线）
- 问题：治理逻辑在单 Controller 聚集，抽象层次不稳定。
- 证据：`agent-trigger/src/main/java/com/getoffer/trigger/http/WorkflowGovernanceController.java`（约 567 行，含编译/校验/发布/数据映射）。
- 处理：拆为 `DraftCommandService`、`SopCompileFacade`、`DefinitionPublishService`，Controller 只保留请求/响应。
- 优先级：P1。

### B4. 任务调度执行状态机与事件流

#### 1) Mainstream Check
- 当前实现：`TaskSchedulerDaemon` 推进 PENDING -> READY/SKIPPED，`TaskExecutor` claim + dispatch，`TaskExecutionRunner` 执行，`PlanStatusDaemon` 聚合终态并触发 finalize；`PlanTaskEventPublisher` 负责持久化与跨实例通知。
- 与主流差异：
  - 自研状态机与调度，而非 Temporal/Cadence 等外部工作流引擎。
  - 定时轮询节奏固定，缺少基于 backlog 的自适应调度。
- 风险：规模增长时调优成本高；高并发下需要更多守护策略（公平性、反压、热分片）。
- 结论：当前阶段可保留差异化（避免过早引入重引擎），但需加强运行参数治理与反压能力。
- 建议改法：
  - 引入 backlog-aware 调度参数（动态 batch、动态 poll interval）。
  - 为 claim 失败/心跳异常/超时终态建立 SLO 面板与自动阈值告警。

#### 2) Product Fit
- 用户价值：直接决定可追踪执行与实时反馈体验（`docs/02-system-architecture.md:96-143`）。
- 冲突点：若执行队列抖动，会直接影响“首个进度事件 P95 < 3s”的验收目标（`docs/01-product-requirements.md:108`）。
- 决策：保留当前范式，逐步增强调度治理。

#### 3) Redundancy & Stale（本路线）
- 问题：执行链条组件多、责任边界存在重复传递成本（Runner/Adapter/FlowSupport/RuntimeSupport）。
- 证据：`agent-trigger/src/main/java/com/getoffer/trigger/job/TaskExecutionRunner.java`、`TaskExecutionCallSupportAdapter.java`、`TaskExecutionEvaluationSupportAdapter.java`、`TaskExecutionPersistenceSupportAdapter.java`、`TaskExecutionFlowSupport.java`、`TaskExecutionRuntimeSupport.java`。
- 处理：保持接口解耦，已收敛为“调用域/评估域/持久化域”三组最小接口，后续继续观察接口稳定性与依赖收敛效果。
- 优先级：P2。

### B5. LLM/工具调用与 Agent 选路

#### 1) Mainstream Check
- 当前实现：任务执行时通过 `TaskExecutionClientResolver` 执行 `configured -> fallback keys -> default active agent` 选路；`AgentFactoryImpl` 解析工具并可接入 MCP；`McpClientManager` 管理 stdio/sse/streamable_http。
- 与主流差异：
  - 缺少统一“模型路由策略层”（成本/时延/质量的多目标路由）。
  - Workflow 的 `toolPolicy` 目前仅存储与哈希参与，未进入执行期强约束。
- 风险：
  - 回退到“首个激活 Agent”会带来不可预期行为。
  - `toolPolicy` 抽象形同虚设，治理配置与运行行为可能偏离。
- 结论：需部分跟随主流（策略化路由 + 工具权限收敛），保留现有 MCP 插件能力。
- 建议改法：
  - 增加 `ModelRoutingPolicy`（可按任务类型、失败率、成本动态选模）。
  - 在执行前把 `toolPolicy` 映射为“允许工具列表/阻断策略”，并落审计。

#### 2) Product Fit
- 用户价值：高，直接影响输出质量、成本与可扩展生态（工具生态）。
- 为技术而技术：`toolPolicy` 当前存在“治理可配、执行不生效”的落差。
- 决策：保留并补齐执行约束。

#### 3) Redundancy & Stale（本路线）
- 问题：`toolPolicy` 数据链路未闭环。
- 证据：`toolPolicy` 主要出现在 Draft/Definition 存储与哈希（`WorkflowGovernanceController`、`Workflow*RepositoryImpl`、`PlannerServiceImpl:1194`），未见执行期消费。
- 处理：
  - 要么上线执行期强约束（推荐）。
  - 要么标记废弃并删除字段，避免误导治理端。
- 优先级：P1。

### B6. 质量提升闭环（校验/批评/回滚/黑板/终态）

#### 1) Mainstream Check
- 当前实现：`TaskEvaluationDomainService` 以关键词和简单 JSON 语义判定 pass/fail，`TaskRecoveryDomainService` 做 critic 回滚，`TaskBlackboardDomainService` 回写上下文，`TaskPersistenceApplicationService` 处理乐观锁冲突重试。
- 与主流差异：
  - 评估契约弱（关键词 + 宽松 JSON），缺少稳定评分体系与结构化 rubric。
  - 未形成 A/B 实验与反馈闭环主链路（代码/SQL 未见实验分桶与评估事件模型）。
- 风险：输出质量提升难以量化，难以支持“可持续优化机制”的产品目标。
- 结论：必须跟随主流补齐“可测量评估 + 试验平台 + 回归基线”。
- 建议改法：
  - 引入 `EvaluationSchema`（必填字段、评分维度、阈值）。
  - 把 critic 输出从“解析 JSON 文本”升级为“强约束结构化响应”。
  - 新增实验分桶与质量事件落库，支持离线分析与线上灰度。

#### 2) Product Fit
- 用户价值：这是“输出质量提升”主目标的核心路线（`docs/01-product-requirements.md:11`）。
- 冲突点：当前评估策略偏启发式，和“稳定、一致、可控”的目标有差距。
- 决策：保留当前闭环骨架，但优先升级评估契约与实验能力。

#### 3) Redundancy & Stale（本路线）
- 问题：质量评估策略弱契约。
- 证据：`agent-domain/src/main/java/com/getoffer/domain/task/service/TaskEvaluationDomainService.java:34-72`；`agent-trigger/src/main/java/com/getoffer/trigger/job/TaskExecutionFlowSupport.java:88-94`。
- 处理：引入 schema 化评估与打分器插件；为关键任务保留“人工可解释反馈字段”。
- 优先级：P0。

### B7. 数据与存储

#### 1) Mainstream Check
- 当前实现：PostgreSQL 承担主链路持久化，Schema 覆盖会话/规划/执行/事件/Agent 配置；并有乐观锁与关键唯一索引。
- 与主流差异：
  - 读侧部分接口仍走“全量拉取 + 内存过滤/分页”。
  - 测试 schema 与初始化 schema 双份维护，存在漂移风险。
- 风险：数据增长后查询成本陡增；测试环境与生产语义偏移。
- 结论：存储主路线正确，应跟随主流补齐“读侧分页下推 + schema 单一事实源”。
- 建议改法：
  - 将 `QueryController`/`ConsoleQueryController` 的 `findAll` 路径改为 DAO 分页与聚合 SQL。
  - 用迁移脚本/DDL 生成 test schema，取消人工双维护。

#### 2) Product Fit
- 用户价值：高。可追溯与可恢复依赖数据库落地边界。
- 为技术而技术：Redis 目前仅预留，不在主链路（`README.md:113-115`），不应提前扩展复杂缓存策略。
- 决策：保留 PostgreSQL 主轴，简化无效预留项。

#### 3) Redundancy & Stale（本路线）
- 问题 1：全量查询路径易放大负载。
- 证据：`agent-trigger/src/main/java/com/getoffer/trigger/http/QueryController.java:178-180,188`，`agent-trigger/src/main/java/com/getoffer/trigger/http/ConsoleQueryController.java:130,205`。
- 处理：下推分页/聚合到数据库，移除 `findAll` 大列表路径。
- 优先级：P1。
- 问题 2：Schema 双份定义。
- 证据：`docs/dev-ops/postgresql/sql/01_init_database.sql` 与 `agent-app/src/test/resources/sql/integration-schema.sql` 均定义全量表。
- 处理：统一由迁移源生成测试 schema。
- 优先级：P1。
- 问题 3：Redis 基础设施目录为空壳。
- 证据：`agent-infrastructure/src/main/java/com/getoffer/infrastructure/redis/package-info.java`（仅注释，无实现）。
- 处理：删除空壳或补齐真实实现；避免“假能力”。
- 优先级：P2。

### B8. 权限、多租户与分享访问

#### 1) Mainstream Check
- 当前实现：`ApiAuthFilter` 统一鉴权；`AuthSessionCommandService` 使用本地账号 + 内存 token；会话级 `userId` 归属校验；分享链接支持哈希令牌与撤销。
- 与主流差异：
  - 登录态不持久化（重启丢失），无 JWT/刷新令牌机制。
  - 多租户字段存在但未形成完整隔离策略（PRD 本轮明确不做）。
- 风险：
  - 对生态化/多实例扩展不友好。
  - `tenant_id` 长期“有字段、无治理”会造成认知负担。
- 结论：当前与 PRD 一致（`docs/01-product-requirements.md:33-38`，`docs/02-system-architecture.md:46-47`）；若进入生态阶段需升级。
- 建议改法：
  - 下一阶段切换为可持久化会话（DB/Redis/JWT 三选一）。
  - 对 `tenant_id` 先明确“保留但不启用”的治理声明，并加边界校验。

#### 2) Product Fit
- 用户价值：当前单机阶段足够；避免过度 RBAC 复杂度。
- 冲突点：当产品从“单账号”走向“用户生态”时，现方案将成为主要瓶颈。
- 决策：本阶段保留；生态化前必须升级。

#### 3) Redundancy & Stale（本路线）
- 问题：token 会话仅内存态。
- 证据：`agent-trigger/src/main/java/com/getoffer/trigger/application/command/AuthSessionCommandService.java:26`（`ConcurrentHashMap tokenSessions`）。
- 处理：改为签名 JWT 或持久化 session store；支持吊销与审计。
- 优先级：P1。
- 问题：多租户字段默认化且弱约束。
- 证据：`PlannerServiceImpl` 使用 `DEFAULT_TENANT`；`WorkflowGovernanceController` 多处 `defaultIfBlank(..., "DEFAULT")`。
- 处理：标记“预留字段”并增加 lint/文档约束；进入多租户里程碑再启用。
- 优先级：P2。

### B9. 可运维与治理

#### 1) Mainstream Check
- 当前实现：有较完整 metrics、告警规则、runbook、告警目录巡检与 API，可支持链路排障。
- 与主流差异：
  - 配置中心与灰度发布机制尚未建立（代码与配置未检索到 `spring.cloud.config/nacos/apollo/@RefreshScope/canary/feature flag`）。
  - 告警目录中存在占位替换机制，仍依赖环境配置完善度。
- 风险：跨环境配置一致性与变更控制成本高；上线策略偏“全量发布”。
- 结论：观测能力已达到良好基线，发布治理能力需补齐。
- 建议改法：
  - 引入配置分层与动态刷新策略（至少“读侧可热更，写侧灰度”）。
  - 建立发布策略开关（灰度比例、回滚阈值、Kill Switch）。

#### 2) Product Fit
- 用户价值：高（失败可定位是 PRD P0 验收项，`docs/01-product-requirements.md:115-118`）。
- 为技术而技术：无明显。
- 决策：保留并向“发布治理化”演进。

#### 3) Redundancy & Stale（本路线）
- 问题：发布治理缺失（灰度/配置中心）。
- 证据：`application*.yml` 与 `agent-*/src/main/java` 未检索到配置中心/灰度关键实现。
- 处理：新增 `release-control` 配置域与灰度执行器；先接入一条核心链路。
- 优先级：P2。

## B. 冗余与过期清理清单（强制检查汇总）

| 问题点 | 证据（路径/调用链/引用关系） | 影响 | 处理动作 | 优先级 |
| --- | --- | --- | --- | --- |
| 质量评估契约过弱（关键词 + 宽松 JSON） | `agent-domain/src/main/java/com/getoffer/domain/task/service/TaskEvaluationDomainService.java:34-72`<br>`agent-trigger/src/main/java/com/getoffer/trigger/job/TaskExecutionFlowSupport.java:88-94` | 无法稳定衡量输出质量，难形成可持续提升 | 引入 schema 化评估、结构化评分、阈值策略；critic 输出强约束 | P0 |
| 缺失 A/B 实验与反馈闭环主链路 | 代码与 SQL 未检索到 experiment/abtest/bucket/variant 相关实现；`docs/01-product-requirements.md:11` 强调持续优化 | 质量策略无法科学对比，迭代决策主观化 | 增加实验配置/分桶/事件表，接入路由与评估链路 | P0 |
| 图规则存在多处实现，存在漂移风险 | `SopSpecCompileService`、`GraphDslPolicyService`、`PlannerServiceImpl` 同时承担 Graph 规则相关逻辑 | 编译与运行期行为不一致，故障定位复杂 | 合并为统一 `WorkflowGraphPolicyKernel`（编译/校验/归一化单源） | P0 |
| `PlannerServiceImpl` 过大（单类 2000+ 行） | `agent-infrastructure/src/main/java/com/getoffer/infrastructure/planning/PlannerServiceImpl.java` | 维护风险高，改动回归面过大 | 拆分路由/草案生命周期/输入绑定/图展开子服务 | P1 |
| `WorkflowGovernanceController` 业务聚集 | `agent-trigger/src/main/java/com/getoffer/trigger/http/WorkflowGovernanceController.java`（567 行） | 分层边界弱，接口与业务耦合 | 抽离治理应用服务与领域服务，Controller 仅保留协议层 | P1 |
| 读接口存在全量加载 + 内存分页 | `agent-trigger/src/main/java/com/getoffer/trigger/http/QueryController.java:178-180,188`<br>`agent-trigger/src/main/java/com/getoffer/trigger/http/ConsoleQueryController.java:130,205` | 数据量增长后响应劣化，内存压力上升 | DAO 下推分页与聚合 SQL，移除 `findAll` 路径 | P1 |
| `toolPolicy` 抽象未闭环（治理可配、执行不控） | `toolPolicy` 主要在 Workflow 存储/哈希链路出现；执行链路未见消费 | 配置误导，策略与执行偏离 | 上线执行期工具白名单约束，或标记废弃并删除字段 | P1 |
| 会话 token 仅内存态 | `agent-trigger/src/main/java/com/getoffer/trigger/application/command/AuthSessionCommandService.java:26` | 重启失效、无法跨实例、审计能力弱 | 迁移到 JWT 或持久化 session store | P1 |
| DDL 双份维护（生产/测试） | `docs/dev-ops/postgresql/sql/01_init_database.sql` 与 `agent-app/src/test/resources/sql/integration-schema.sql` | schema 漂移与“测通过、线上不通过”风险 | 统一以迁移源生成测试 schema | P1 |
| 多租户字段长期默认化未启用 | `PlannerServiceImpl` 的 `DEFAULT_TENANT` 与 `WorkflowGovernanceController` 的 `DEFAULT` 回退 | 认知复杂度增加，容易形成“半多租户”错觉 | 明确“预留不启用”治理策略，进入生态阶段再启用 | P2 |
| Redis 能力为预留且模块空壳 | `README.md:113-115` + `agent-infrastructure/src/main/java/com/getoffer/infrastructure/redis/package-info.java` | 增加系统认知噪音 | 删除空壳或补齐真实缓存实现 | P2 |
| 缺失配置中心/灰度发布主链路 | 代码与配置未检索到相关实现（`spring.cloud.config/nacos/apollo/@RefreshScope/canary/feature flag`） | 发布治理能力不足，回滚粒度粗 | 引入配置治理与灰度开关体系 | P2 |

## C. 收敛建议与路线图

### C1. 目标收敛架构（文字版模块图）

```text
[API/BFF 层]
  Chat V3 / Workflow Governance / Auth / Observability API
      |
      v
[Orchestration Kernel]
  Session Orchestrator -> Routing Policy -> Workflow Graph Policy Kernel -> Task Runtime
      |
      +--> [Quality Engine]
      |      Validator/Critic Schema + Score + Recovery + Blackboard
      |
      +--> [Agent Runtime]
      |      Model Routing Policy + Tool Policy Enforcement + MCP/Function Plugins
      |
      v
[Data Plane]
  PostgreSQL(会话/计划/任务/事件/模板/实验) + 可选缓存
      |
      v
[Governance Plane]
  Metrics/Logs/Tracing + Alert Catalog + Release Control + Deprecation Registry
```

### C2. 统一主链路（建议合并）
- 编排统一：只保留 `Workflow Graph v2` 作为运行时编排模型；`SOP Spec` 仅作为治理源。
- 模板统一：`SOP Spec -> Compile -> Runtime Graph -> Publish` 全链路由统一治理应用服务负责，禁止在 Controller 分散写规则。
- 评估统一：`TaskEvaluation` 统一使用强契约 schema 与评分模型，critic/validator 结果都落统一质量事件模型。

### C3. 插件/扩展点（建议保留）
- Root 规划器插件：保留 `IRootWorkflowDraftPlanner`，支持不同规划模型替换。
- 工具生态插件：保留 MCP/函数工具机制（`McpClientManager` + ToolCallback），但增加 `toolPolicy` 执行约束。
- 模型路由插件：引入 `ModelRoutingPolicy`，允许按任务类型/成本/质量动态选模。
- 评估插件：引入 `EvaluationProvider`，支持规则评分、模型评分、混合评分。

### C4. 分阶段执行计划

#### Phase 1（低风险，2-3 周）
- 目标：清理冗余、降低维护噪音、补齐边界守护。
- 涉及模块/路径：
  - `agent-trigger/src/main/java/com/getoffer/trigger/http/QueryController.java`
  - `agent-trigger/src/main/java/com/getoffer/trigger/http/ConsoleQueryController.java`
  - `agent-infrastructure/src/main/java/com/getoffer/infrastructure/redis/package-info.java`
  - `docs/dev-ops/postgresql/sql/01_init_database.sql`
  - `agent-app/src/test/resources/sql/integration-schema.sql`
- 具体改动：
  - 读接口移除 `findAll` 路径，改为数据库分页与聚合。
  - 删除或明确废弃空壳 Redis 模块。
  - 建立 schema 一致性校验脚本，保证测试 DDL 与生产 DDL 同源。
  - 为非 V3 只读接口建立使用度埋点与废弃标记。
- 验收标准（可测指标）：
  - `/api/tasks/paged`、`/api/dashboard/overview` 不再触发全表拉取。
  - schema drift CI 检查通过率 100%。
  - 非 V3 接口访问量有可观测基线。
- 风险与回滚：
  - 风险：读接口 SQL 改造可能影响控制台兼容。
  - 回滚：保留旧查询实现开关（feature toggle）并灰度切换。

#### Phase 2（结构性，4-6 周）
- 目标：统一技术路线，收敛核心抽象。
- 涉及模块/路径：
  - `agent-infrastructure/src/main/java/com/getoffer/infrastructure/planning/PlannerServiceImpl.java`
  - `agent-trigger/src/main/java/com/getoffer/trigger/http/WorkflowGovernanceController.java`
  - `agent-trigger/src/main/java/com/getoffer/trigger/application/command/SopSpecCompileService.java`
  - `agent-domain/src/main/java/com/getoffer/domain/planning/service/GraphDslPolicyService.java`
  - `agent-domain/src/main/java/com/getoffer/domain/task/service/TaskEvaluationDomainService.java`
- 具体改动：
  - 落地 `WorkflowGraphPolicyKernel`，统一编译/校验/归一化。
  - 拆分治理与规划“巨型类”，形成稳定应用服务边界。
  - 质量链路升级为 schema 化评估，接入评分与阈值。
  - `toolPolicy` 执行期生效（allowlist/blocklist + 审计）。
  - 新增实验分桶与质量事件落库（A/B 主链路）。
- 验收标准（可测指标）：
  - Graph 规则实现单源，重复逻辑显著下降。
  - 质量评估结果可量化（评分、通过率、回归趋势可视化）。
  - A/B 试验可在单条链路稳定跑通并可回溯。
- 风险与回滚：
  - 风险：核心链路重构带来回归风险。
  - 回滚：按模块开关分批上线（先评估、再 toolPolicy、最后路由策略）。

#### Phase 3（治理化，持续）
- 目标：建立长期演进机制，避免再次发散。
- 涉及模块/路径：
  - 全仓 `agent-*` 分层依赖
  - `docs/` 架构与运维文档
  - CI/CD 配置（发布策略、依赖规则）
- 具体改动：
  - 引入依赖规则（ArchUnit/Enforcer）防止跨层边界坍塌。
  - 建立 Owner、废弃策略、API 生命周期与迁移模板。
  - 建立 Workflow 版本迁移策略（v2 -> 后续版本）。
  - 建立配置治理/灰度发布/回滚标准作业流。
- 验收标准（可测指标）：
  - 架构规则违规数持续为 0。
  - 废弃接口具备公告窗口、迁移文档与下线记录。
  - 灰度发布覆盖核心链路，回滚时间可量化。
- 风险与回滚：
  - 风险：治理建设短期影响交付节奏。
  - 回滚：治理策略先“警告模式”运行，再升级为“阻断模式”。

## D. 收敛执行 TODO 清单（更新至 2026-02-19）

### D1 已完成（本轮已落地）
- [x] Graph 规则单源：`WorkflowGraphPolicyKernel` 接管 Planner/SOP 编译/治理入口，消除多处规则漂移。
- [x] 鉴权收敛：登录态升级为 `JWT + auth_session_blacklist(jti)`，支持跨实例立即吊销。
- [x] 治理入口瘦身：`WorkflowGovernanceController` 业务逻辑下沉到 `WorkflowGovernanceApplicationService`。
- [x] Planner 拆分第一阶段：落地 `WorkflowTaskMaterializationService`、`WorkflowPlanSnapshotService`、`WorkflowDraftLifecycleService`、`WorkflowInputPreparationService`。
- [x] Planner 拆分第二阶段：落地 `WorkflowRoutingResolveService`（路由解析）+ `WorkflowRoutingDecisionService`（路由落库与指标），`PlannerServiceImpl` 从 1500+ 行收敛到约 400 行。
- [x] 执行支持接口收口：`TaskExecutionRunner.ExecutionSupport` 收敛为 `CallSupport/EvaluationSupport/PersistenceSupport` 三组接口，适配器按域拆分，去除单一大适配器。
- [x] 架构治理门禁：新增 `ArchitectureDependencyRuleTest`（ArchUnit）与 Maven Enforcer（Java/Maven 基线）。
- [x] Enforcer 规则增强：开启 `banDuplicatePomDependencyVersions` + `requirePluginVersions`，并补齐 `maven-site-plugin` 版本。
- [x] 生命周期专项测试：新增 `WorkflowDraftLifecycleServiceTest`（去重复用、Root 禁用降级、不可用 agentKey 自动回退）。

### D2 已完成（本轮落地）
- [x] `QueryController/ConsoleQueryController` 去除全量加载与内存分页，统一 DAO 分页与聚合 SQL。
  - `ConsoleQueryController#/api/logs/paged` 无 `planId` 分支改为 `agentPlanRepository.findRecent(100)`（DAO `LIMIT` 下推），移除全量 `findAll + 内存排序`。
  - `QueryController#/api/agents/tools`、`/api/agents/vector-stores` 改为 `findRecent(limit)`（默认 `100`，上限 `500`），新增 DAO/Mapper `selectRecent` SQL。
- [x] `toolPolicy` 执行期闭环补齐审计字段（命中 allow/block 的结构化事件）并补回放查询。
  - 执行期新增结构化事件：`TaskExecutionClientResolver` 在策略生效时发布 `TASK_LOG`，事件字段包含 `auditCategory=tool_policy`、`policyAction`（`allow_hit/block_hit/disabled_block/enforced`）、`policyMode`、`allowHit/blockHit`、`allowedTools/blockedTools`、`selectionSource` 等。
  - 新增回放查询接口：`GET /api/logs/tool-policy/paged`，支持 `policyAction/policyMode/keyword` 过滤；仓储新增 `countToolPolicyLogs/findToolPolicyLogsPaged` 与对应 DAO/Mapper SQL。
- [x] A/B 主链路落地（分桶、质量事件关联、查询 API），对齐 PRD 的“输出质量持续提升”闭环。
  - 分桶与关联：`TaskPersistenceApplicationService` 在持久化执行记录后，基于 `qualityExperiment*` 配置写入 `experiment_key/experiment_variant`，并在 payload 补齐 `bucket/rolloutPercent`。
  - 查询 API：新增 `GET /api/quality/evaluations/paged`（分页回放）与 `GET /api/quality/evaluations/experiments/summary`（按 `experiment_key + variant` 聚合 `total/passCount/passRate/avgScore`）。
  - 数据访问：`IQualityEvaluationEventRepository` 新增过滤分页/计数/聚合接口，DAO/Mapper 新增对应 SQL（DB 侧过滤与聚合）。

### D3 待启动（Phase 3 治理化）
- [x] 废弃治理：建立非 V3 接口 Deprecation Registry（公告窗口、迁移文档、下线基线）。
  - 注册表与查询 API：`agent-app/src/main/resources/governance/deprecation-registry.json`、`agent-trigger/src/main/java/com/getoffer/trigger/http/DeprecationRegistryController.java`、`agent-trigger/src/main/java/com/getoffer/trigger/application/query/DeprecationRegistryQueryService.java`。
  - 迁移文档：`docs/design/09-deprecation-registry.md`。
  - 下线执行：`QueryController` 已删除 `/api/plans/{id}`、`/api/plans/{id}/tasks`、`/api/tasks/{id}/executions`，注册表对应条目状态更新为 `REMOVED`。
- [ ] Workflow 版本迁移策略：补齐 `v2 -> vNext` 迁移模板、兼容矩阵与回滚脚本规范。

## 附录：关键证据索引
- 产品定位与范围：`docs/01-product-requirements.md:10-13`、`docs/01-product-requirements.md:24-39`。
- 架构与统一术语：`docs/02-system-architecture.md:55`、`docs/02-system-architecture.md:116-127`、`docs/02-system-architecture.md:200-209`。
- 路由与 Root 兜底：`agent-infrastructure/src/main/java/com/getoffer/infrastructure/planning/PlannerServiceImpl.java`、`agent-domain/src/main/java/com/getoffer/domain/planning/service/PlannerFallbackPolicyDomainService.java`。
- Root 启动可用性校验：`agent-app/src/main/java/com/getoffer/config/RootAgentHealthCheckRunner.java`。
- 模板治理：`agent-trigger/src/main/java/com/getoffer/trigger/http/WorkflowGovernanceController.java`、`agent-trigger/src/main/java/com/getoffer/trigger/application/command/SopSpecCompileService.java`、`agent-domain/src/main/java/com/getoffer/domain/planning/service/GraphDslPolicyService.java`。
- 执行与终态：`agent-trigger/src/main/java/com/getoffer/trigger/job/TaskExecutor.java`、`TaskExecutionRunner.java`、`PlanStatusDaemon.java`、`PlanStatusSyncApplicationService.java`、`TurnFinalizeApplicationService.java`、`agent-domain/src/main/java/com/getoffer/domain/planning/service/PlanFinalizationDomainService.java`。
- 质量判定与黑板写回：`agent-domain/src/main/java/com/getoffer/domain/task/service/TaskEvaluationDomainService.java`、`TaskRecoveryDomainService.java`、`TaskBlackboardDomainService.java`、`agent-trigger/src/main/java/com/getoffer/trigger/application/command/TaskPersistenceApplicationService.java`。
- 数据落地边界：`docs/dev-ops/postgresql/sql/01_init_database.sql`。
- 权限与会话：`agent-app/src/main/java/com/getoffer/config/ApiAuthFilter.java`、`agent-trigger/src/main/java/com/getoffer/trigger/application/command/AuthSessionCommandService.java`、`agent-domain/src/main/java/com/getoffer/domain/session/service/SessionConversationDomainService.java`。
- 可观测与巡检：`agent-trigger/src/main/java/com/getoffer/trigger/http/ObservabilityAlertCatalogController.java`、`agent-trigger/src/main/java/com/getoffer/trigger/job/ObservabilityAlertCatalogProbeJob.java`、`docs/dev-ops/observability/README.md`、`scripts/devops/observability-gate.sh`。
