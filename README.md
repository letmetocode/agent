# Agent
面向任务编排与执行的 Agent 工程，采用 DDD 分层多模块结构，技术基线为 Spring Boot 3.4.3 + Spring AI 1.1.2 + MyBatis + PostgreSQL。

## 开发阶段原则

项目当前处于开发阶段，优先保证功能完备与系统健康。  
修改与新增以长期可维护方案为先，避免临时补丁与潜在隐患。

## 产品需求文档（PRD）

完整 PRD 已独立维护在：`docs/01-product-requirements.md`。

配套文档：
- 系统架构：`docs/02-system-architecture.md`
- UI/UX 规范：`docs/03-ui-ux-spec.md`
- 开发任务清单：`docs/04-development-backlog.md`

## 模块结构

- `agent-app`：启动与全局配置装配（Spring、调度、MyBatis 资源）
- `agent-trigger`：HTTP/SSE 适配层 + `trigger.application` 用例编排层（入口协议与业务编排解耦）
- `agent-domain`：领域实体、领域服务、状态机、仓储端口
- `agent-infrastructure`：DAO/Mapper、仓储实现、Planner/AI/MCP 适配
- `agent-api`：对外 DTO 与统一响应
- `agent-types`：通用枚举、异常、常量

### DDD 充血重构基线（2026-02）

- 会话规则下沉：`SessionConversationDomainService`（标题/Agent 默认选择/上下文组装/错误语义）。
- 终态收敛规则下沉：`PlanFinalizationDomainService`（Plan->Turn 终态映射与输出汇总）。
- Plan 聚合状态推进下沉：`PlanTransitionDomainService`（Task 统计 -> Plan 状态迁移）。
- Task 持久化策略下沉：`TaskPersistencePolicyDomainService`（乐观锁冲突识别、重试判定、错误归一化）。
- Task 持久化写用例收口：`TaskPersistenceApplicationService`（claimed update / execution save / plan context retry）。
- 调度依赖策略接口化：`TaskDependencyPolicy` + `TaskDependencyPolicyDomainService`（PENDING 依赖判定规则）。
- job 壳层化收口：`TaskScheduleApplicationService`（TaskScheduler 编排）与 `PlanStatusSyncApplicationService`（PlanStatus 编排）。
- `trigger.service` 兼容包装类已删除，统一由 `trigger.application` 调用 domain。


## 前端控制台信息架构（2026-02）

前端控制台已按 Agent 主路径重构为 6 个一级导航：

- `工作台`：目标导向入口、最近执行与系统健康
- `对话与执行`：新聊天直达会话，默认设置自动执行（高级设置可选）
- `任务中心`：任务筛选、追踪与失败重试
- `资产中心`：工具与插件 + 知识库
- `观测与日志`：监控总览与日志检索
- `设置`：个人偏好

关键路由：

- `/workspace`
- `/sessions`、`/sessions/:sessionId`
- `/tasks`、`/tasks/:taskId`
- `/assets/tools`、`/assets/knowledge`、`/assets/knowledge/:kbId`
- `/observability/overview`、`/observability/logs`
- `/settings/profile`
- `/workflows/drafts`
- `/share/tasks/:taskId`（匿名分享结果页）

更多说明见：`frontend/README.md` 与 `docs/03-ui-ux-spec.md`。

## 快速开始

### 1) 初始化数据库

PostgreSQL 最终版初始化脚本：

- `docs/dev-ops/postgresql/sql/01_init_database.sql`

脚本会初始化核心表、枚举以及基线 AgentProfile（`assistant` 与 `root`）。

会话与规划 V2 增量迁移脚本：

- `docs/dev-ops/postgresql/sql/migrations/V20260212_01_session_planner_v2.sql`
- 回滚脚本：`docs/dev-ops/postgresql/sql/migrations/V20260212_01_session_planner_v2_rollback.sql`
- `docs/dev-ops/postgresql/sql/migrations/V20260213_02_executor_terminal_convergence.sql`
- 回滚脚本：`docs/dev-ops/postgresql/sql/migrations/V20260213_02_executor_terminal_convergence_rollback.sql`

执行顺序建议：

- 全新环境：执行 `docs/dev-ops/postgresql/sql/01_init_database.sql`（已包含 V2 字段）。
- 存量环境：先备份数据库，再依次执行 `V20260212_01_session_planner_v2.sql`、`V20260213_02_executor_terminal_convergence.sql`。
- 回滚场景：按逆序执行回滚脚本，并同步回滚应用版本。

### 1.5) 启动本地依赖（可选）

使用以下 compose 文件可一键启动 `PostgreSQL + pgAdmin + Redis + Redis Commander`：

- 本地环境：`docs/dev-ops/docker-compose-environment.yml`
- 阿里云镜像环境：`docs/dev-ops/docker-compose-environment-aliyun.yml`

```bash
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
```

默认端口：

- PostgreSQL：`15432`（`postgres` / `agent_db`）
- pgAdmin：`5050`
- Redis：`16379`
- Redis Commander：`8081`

启动后会自动执行 `docs/dev-ops/postgresql/sql` 下的初始化 SQL。

### 2) 配置开发环境

默认使用：

- `agent-app/src/main/resources/application.yml`（激活 `dev`）
- `agent-app/src/main/resources/application-dev.yml`

模型相关配置位于 `application-dev.yml`：

- `spring.ai.openai.api-key`
- `spring.ai.openai.base-url`
- `spring.ai.openai.chat.completions-path`

### 3) 启动后端

```bash
mvn -pl agent-app -am spring-boot:run
```

### 4) 启动前端（可选）

```bash
cd frontend
npm install
npm run dev
```

默认前端访问后端 `http://127.0.0.1:8091`。

## 关键配置说明

### Root 规划与 Workflow Draft

配置位置：`agent-app/src/main/resources/application-dev.yml`

- `planner.root.enabled`
- `planner.root.agent-key`（Root AgentProfile Key）
- `planner.root.retry.max-attempts`
- `planner.root.retry.backoff-ms`
- `planner.root.fallback.single-node.enabled`
- `planner.root.fallback.agent-key`（Draft 节点缺省 agentKey，当前默认 `assistant`）

### 任务分享链接

配置位置：`agent-app/src/main/resources/application*.yml`

- `app.share.base-url`（任务分享链接生成的前端访问域名，默认 `http://127.0.0.1:8091`）
- `app.share.token-salt`（分享令牌哈希盐，生产环境务必覆盖）
- `app.share.max-ttl-hours`（分享链接最大有效期，默认 168 小时）

### 执行兜底 Agent

配置位置：`agent-app/src/main/resources/application.yml`

- `executor.agent.fallback-worker-keys`
- `executor.agent.fallback-critic-keys`
- `executor.agent.default-cache-ttl-ms`

### 执行超时治理

配置位置：`agent-app/src/main/resources/application.yml`

- `executor.execution.timeout-ms`（单次 TaskClient 调用超时时间）
- `executor.execution.timeout-retry-max`（超时后的额外重试次数，默认 1）

### SSE 回放参数

配置位置：`agent-app/src/main/resources/application.yml`

- `sse.replay.batch-size`（每次回放 sweep 的单批次查询上限）
- `sse.replay.max-batches-per-sweep`（每次 sweep 对单订阅者最多回放批次数）

### HTTP 入口日志与链路追踪

配置位置：`agent-app/src/main/resources/application*.yml`

- `observability.http-log.enabled`（统一入口日志开关）
- `observability.http-log.sample-rate`（入口日志采样率，生产可降载）
- `observability.http-log.slow-request-threshold-ms`（慢请求阈值）
- `observability.http-log.log-request-body`（是否记录请求体摘要，默认 `false`）
- `observability.http-log.request-body-whitelist`（请求体摘要白名单字段）
- `observability.http-log.mask-fields`（脱敏字段）

默认行为：
- 为 `/api/**` 请求注入并回传 `X-Trace-Id` / `X-Request-Id`。
- 日志以 `HTTP_IN` / `HTTP_OUT` / `HTTP_ERROR` 输出，可按 `traceId` 串联排障。

## 文档导航

- 架构文档：`docs/02-system-architecture.md`
- UI/UX 规范：`docs/03-ui-ux-spec.md`
- 开发任务清单：`docs/04-development-backlog.md`

- 产品需求文档：`docs/01-product-requirements.md`
- 运维与 SQL 文档：`docs/dev-ops/`
- 前端说明：`frontend/README.md`

## Workflow 治理接口

- `GET /api/workflows/drafts`：查询 Draft 列表（支持按状态筛选）。
- `GET /api/workflows/drafts/{id}`：查询 Draft 详情。
- `PUT /api/workflows/drafts/{id}`：更新 Draft（仅非 `ARCHIVED/PUBLISHED`）。
- `POST /api/workflows/drafts/{id}/publish`：发布 Draft 为新 Definition 版本。
- `GET /api/workflows/definitions`：查询 Definition 列表。
- `GET /api/workflows/definitions/{id}`：查询 Definition 详情。

前端会话页在识别到“未命中生产 Definition”时，会展示 Draft 提示与编辑入口。

## 会话编排 V3 接口（推荐）

- `POST /api/v3/chat/messages`：统一会话编排入口（自动创建/复用 Session + 创建 Turn + 触发 Plan）。
- `GET /api/v3/chat/sessions/{id}/history`：聚合返回会话历史（session/turns/messages + latestPlanId）。
- `GET /api/v3/chat/sessions/{id}/stream?planId=...`：聊天语义 SSE（`message.accepted`、`task.progress`、`answer.final` 等）。
- `GET /api/v3/chat/plans/{id}/routing`：查询路由决策详情（V2 路由接口替代）。
- 默认策略：优先使用 `assistant`（若存在且激活），否则使用首个激活 Agent；无可用 Agent 时返回明确错误。

## 会话与规划 V2 接口（已下线，仅保留迁移提示）

> 说明：V2 接口已全部下线，仅保留迁移提示用于存量调用识别。替代方案见 `docs/04-development-backlog.md` 第 4 节。

- `GET /api/v2/agents/active`：已下线，返回迁移提示（请使用 `/api/v3/chat/messages`）。
- `POST /api/v2/agents`：已下线，返回迁移提示（请使用 `/api/v3/chat/messages`）。
- `POST /api/v2/sessions`：已下线，返回迁移提示（请使用 `/api/v3/chat/messages`）。
- `POST /api/v2/sessions/{id}/turns`：已下线，返回迁移提示（请使用 `/api/v3/chat/messages`）。
- `GET /api/v2/plans/{id}/routing`：已下线，返回迁移提示（请使用 `/api/v3/chat/plans/{id}/routing`）。
- 旧 `POST /api/sessions/{id}/chat` 已下线，调用会返回迁移提示。
- 查询性能：`/api/tasks`、`/api/plans/{id}/tasks`、`/api/sessions/{id}/overview`、`/api/dashboard/overview` 已使用批量 latestExecution 查询，避免 N+1。
- 观测闭环：`/api/logs/paged` 已改为 DB 侧分页查询；`GET /api/observability/alerts/catalog` 提供告警目录与 runbook 入口。

### 会话编排 V3 最小验证流程

- 发送消息：`POST /api/v3/chat/messages`
- 回查历史：`GET /api/v3/chat/sessions/{sessionId}/history`
- 订阅流式执行：`GET /api/v3/chat/sessions/{sessionId}/stream?planId={planId}`
- 回查路由：`GET /api/v3/chat/plans/{planId}/routing`

详细回归项见：`docs/04-development-backlog.md`。


## 监控告警规则（Planner + Executor/Terminal + SSE）

告警阈值已固化为 Prometheus 规则文件：

- Planner：`docs/dev-ops/observability/prometheus/planner-alert-rules.yml`
- Planner 单测样例：`docs/dev-ops/observability/prometheus/planner-alert-rules.test.yml`
- Planner 处置手册：`docs/dev-ops/observability/planner-alert-runbook.md`
- Executor/Terminal：`docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.yml`
- Executor/Terminal 单测样例：`docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.test.yml`
- Executor/Terminal 处置手册：`docs/dev-ops/observability/executor-terminal-alert-runbook.md`
- SSE：`docs/dev-ops/observability/prometheus/sse-alert-rules.yml`
- SSE 单测样例：`docs/dev-ops/observability/prometheus/sse-alert-rules.test.yml`
- SSE 处置手册：`docs/dev-ops/observability/sse-alert-runbook.md`
- 告警目录配置：`agent-app/src/main/resources/observability/alert-catalog.json`（总览页闭环入口）

常用校验命令：

- `promtool check rules docs/dev-ops/observability/prometheus/planner-alert-rules.yml`
- `promtool test rules docs/dev-ops/observability/prometheus/planner-alert-rules.test.yml`
- `promtool check rules docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.yml`
- `promtool test rules docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.test.yml`
- `promtool check rules docs/dev-ops/observability/prometheus/sse-alert-rules.yml`
- `promtool test rules docs/dev-ops/observability/prometheus/sse-alert-rules.test.yml`

## 常用命令

- 构建：`mvn clean package`
- 启动：`mvn -pl agent-app -am spring-boot:run`
- 全量单测：`mvn -pl agent-app -am -DskipTests=false test`
- 初始化 Git hooks：`bash scripts/setup-git-hooks.sh`
- 指定回归：
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=PlannerServiceRootDraftTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=AgentV2ControllerTest,SessionV2ControllerTest,TurnV2ControllerTest,PlanRoutingV2ControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=ChatControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=ConversationOrchestratorServiceTest,ChatV3ControllerTest,ChatStreamV3ControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=TaskExecutorPlanBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=TurnResultServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=SessionConversationDomainServiceTest,PlanFinalizationDomainServiceTest,PlanTransitionDomainServiceTest,TaskExecutionDomainServiceTest,TaskPromptDomainServiceTest,TaskEvaluationDomainServiceTest,TaskRecoveryDomainServiceTest,TaskAgentSelectionDomainServiceTest,TaskBlackboardDomainServiceTest,TaskJsonDomainServiceTest,TaskPersistencePolicyDomainServiceTest,TaskPersistenceApplicationServiceTest,TaskDependencyPolicyDomainServiceTest,TaskScheduleApplicationServiceTest,PlanStatusSyncApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=PlanStatusDaemonTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dit.docker.enabled=true -Dtest=SessionChatPlanSseIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dit.docker.enabled=true -Dtest=ExecutorTerminalConvergenceIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 分享闭环（需 Docker）：`mvn -pl agent-app -am -DskipTests=false -Dit.docker.enabled=true -Dtest=TaskShareLinkControllerIntegrationTest,ShareAccessControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

## 术语约定

- `Workflow Definition`：生产流程定义，版本不可变。
- `Workflow Draft`：运行时草案与治理对象。
- `Routing Decision`：路由命中/兜底的审计记录。
- `Plan`：一次用户输入触发的执行实例，执行事实源是 `execution_graph`。
- `Task`：Plan 内节点任务。
- `AgentProfile`：`agent_registry` 中的执行配置。
- `TaskClient`：任务执行时创建的运行时客户端（底层 `ChatClient`）。
