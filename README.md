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
- `agent-types`：通用枚举、异常与共享类型

### DDD 充血重构基线（2026-02）

- 会话规则下沉：`SessionConversationDomainService`（标题/Agent 默认选择/上下文组装/错误语义）。
- 终态收敛规则下沉：`PlanFinalizationDomainService`（Plan->Turn 终态映射与输出汇总）。
- Plan 聚合状态推进下沉：`PlanTransitionDomainService`（Task 统计 -> Plan 状态迁移）。
- Root 降级策略下沉：`PlannerFallbackPolicyDomainService`（重试判定、fallbackReason 归一、指标标签规范化）。
- Planner 路由链路拆分：`WorkflowRoutingResolveService`（命中/候选解析）+ `WorkflowRoutingDecisionService`（决策落库与指标），`PlannerServiceImpl` 聚焦编排。
- Task 持久化策略下沉：`TaskPersistencePolicyDomainService`（乐观锁冲突识别、重试判定、错误归一化）。
- Task 持久化写用例收口：`TaskPersistenceApplicationService`（claimed update / execution save / plan context retry）。
- 调度依赖策略接口化：`TaskDependencyPolicy` + `TaskDependencyPolicyDomainService`（PENDING 依赖判定规则）。
- job 壳层化收口：`TaskScheduleApplicationService`（TaskScheduler 编排）与 `PlanStatusSyncApplicationService`（PlanStatus 编排）。
- 执行链路分层第一步：`TaskExecutor` 负责 claim/dispatch 协调，`TaskExecutionRunner` 负责单任务执行流程。
- 执行支持接口收口：`TaskExecutionRunner` 将执行依赖统一为 `CallSupport/EvaluationSupport/PersistenceSupport` 三组接口，并由 `TaskExecution*SupportAdapter` 分域适配，降低样板透传与耦合扩散。
- 执行支持适配器分域化：`TaskExecutionCallSupportAdapter`、`TaskExecutionEvaluationSupportAdapter`、`TaskExecutionPersistenceSupportAdapter` 分别承接调用/评估/持久化职责，执行器主类聚焦调度编排。
- 执行流支持组件化：`TaskExecutionFlowSupport` 承接提示词构造、评估解析、回滚与黑板写回，进一步收敛 `TaskExecutor` 职责。
- 客户端选路组件化：`TaskExecutionClientResolver` 承接 TaskClient 选路与默认 Agent 缓存（configured/fallback/default），避免执行器内聚合过多运行时选路细节。
- 评估契约升级：`TaskEvaluationDomainService` 支持 `validationSchema`（`requiredFields/passThreshold/passField/scoreField/feedbackField/strict`）结构化判定，并保留关键词兼容路径。
- 质量事件落库：`TaskPersistenceApplicationService` 在写入 `TaskExecution` 后，会把 `isValid/validationFeedback/score` 及 `qualityExperiment*` 分桶信息写入 `quality_evaluation_events`，用于质量回溯与 A/B 分析；控制台提供 `/api/quality/evaluations/paged` 与 `/api/quality/evaluations/experiments/summary` 查询与聚合接口。
- 工具策略闭环：Workflow `toolPolicy` 已透传到执行期，`TaskExecutionClientResolver` 与 `AgentFactoryImpl` 对工具启用集合执行 allowlist/blocklist/disabled 约束（可选 strict）。
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

脚本会初始化核心表、枚举以及基线 AgentProfile（`assistant` 与 `root`），并包含质量评估事件表 `quality_evaluation_events` 与 JWT 吊销黑名单表 `auth_session_blacklist`。

会话与规划 V2 增量迁移脚本：

- `docs/dev-ops/postgresql/sql/migrations/V20260212_01_session_planner_v2.sql`
- 回滚脚本：`docs/dev-ops/postgresql/sql/migrations/V20260212_01_session_planner_v2_rollback.sql`
- `docs/dev-ops/postgresql/sql/migrations/V20260213_02_executor_terminal_convergence.sql`
- 回滚脚本：`docs/dev-ops/postgresql/sql/migrations/V20260213_02_executor_terminal_convergence_rollback.sql`
- `docs/dev-ops/postgresql/sql/migrations/V20260220_04_session_turn_idempotency_and_execution_dedupe.sql`
- 回滚脚本：`docs/dev-ops/postgresql/sql/migrations/V20260220_04_session_turn_idempotency_and_execution_dedupe_rollback.sql`
- `docs/dev-ops/postgresql/sql/migrations/V20260225_05_root_planner_max_tokens_guard.sql`
- 回滚脚本：`docs/dev-ops/postgresql/sql/migrations/V20260225_05_root_planner_max_tokens_guard_rollback.sql`

Workflow Graph 版本迁移模板（v2 -> vNext）：

- `docs/dev-ops/postgresql/sql/migrations/templates/TEMPLATE_workflow_graph_vnext.sql`
- 回滚模板：`docs/dev-ops/postgresql/sql/migrations/templates/TEMPLATE_workflow_graph_vnext_rollback.sql`
- 策略说明：`docs/design/10-workflow-version-migration.md`

执行顺序建议：

- 全新环境：执行 `docs/dev-ops/postgresql/sql/01_init_database.sql`（已包含 V2 字段）。
- 存量环境：先备份数据库，再依次执行：
  - `V20260212_01_session_planner_v2.sql`
  - `V20260213_02_executor_terminal_convergence.sql`
  - `V20260213_03_observability_logs_query_optimization.sql`
  - `V20260220_04_session_turn_idempotency_and_execution_dedupe.sql`
  - `V20260225_05_root_planner_max_tokens_guard.sql`
- 回滚场景：按逆序执行回滚脚本，并同步回滚应用版本。
- 可使用脚本自动执行增量迁移：`bash scripts/devops/postgres-migrate.sh --env-file docs/dev-ops/.env`

Schema 基线漂移检查（建议在提交前执行）：

```bash
bash scripts/devops/check-schema-drift.sh
```

该脚本会校验集成测试初始化链路是否直接引用 `01_init_database.sql`，并阻断 `integration-schema.sql` 旧双份 schema 文件残留。

### 1.5) 启动本地依赖（推荐）

项目提供了可复现启动脚本与环境模板：

- 环境模板：`docs/dev-ops/.env.example`
- 一键启动：`scripts/devops/local-up.sh`
- 一键停机：`scripts/devops/local-down.sh`

首次执行时若不存在 `docs/dev-ops/.env`，脚本会自动从模板生成，并写入随机密钥。

```bash
# 启动 PostgreSQL + Redis（默认最小暴露）
bash scripts/devops/local-up.sh

# 额外启动 pgAdmin + Redis Commander
bash scripts/devops/local-up.sh --with-ops-ui

# 同时构建并启动应用容器
bash scripts/devops/local-up.sh --with-app
```

说明：`--with-app` 会在启动应用前自动执行 `migrations/V*.sql`，并校验 `OPENAI_API_KEY`。

Redis 定位说明：
- Redis / Redis Commander 当前作为缓存能力预留组件保留在本地编排中。
- 截至 2026-02，主业务链路（会话、规划、执行、SSE）未依赖 Redis 才能运行。

默认端口（可在 `docs/dev-ops/.env` 覆盖）：

- PostgreSQL：`15432`
- pgAdmin：`5050`（`--with-ops-ui`）
- Redis：`16379`
- Redis Commander：`8081`（`--with-ops-ui`）
- agent-app：`8091`（`--with-app`）

停机命令：

```bash
# 保留数据卷
bash scripts/devops/local-down.sh

# 删除数据卷（慎用）
bash scripts/devops/local-down.sh --volumes
```

如需使用阿里云镜像，复制并修改 `docs/dev-ops/.env` 中的 `POSTGRES_IMAGE/PGADMIN_IMAGE/REDIS_IMAGE` 后，执行：

```bash
docker compose --env-file docs/dev-ops/.env -f docs/dev-ops/docker-compose-environment-aliyun.yml up -d
```

启动后会自动执行 `docs/dev-ops/postgresql/sql` 下的初始化 SQL。

### 1.6) 云服务器 Docker 部署

新增部署脚本：`scripts/devops/cloud-deploy.sh`，适用于在云服务器直接发布（支持源码构建或镜像拉取）。

首次使用建议：

```bash
# 1) 准备云环境变量文件（仅首次）
cp docs/dev-ops/.env.example docs/dev-ops/.env.cloud

# 2) 编辑生产参数（至少替换以下项）
# POSTGRES_PASSWORD / DB_PASSWORD / OPENAI_API_KEY
# APP_AUTH_LOCAL_PASSWORD / APP_AUTH_JWT_SECRET / APP_SHARE_TOKEN_SALT
vim docs/dev-ops/.env.cloud
```

部署命令示例：

```bash
# 方式A：服务器源码构建并部署
bash scripts/devops/cloud-deploy.sh

# 方式B：从制品仓拉取镜像部署（推荐线上）
bash scripts/devops/cloud-deploy.sh --no-build --pull \
  --app-image registry.cn-hangzhou.aliyuncs.com/system/agent-app:1.0
```

可选参数：

- `--env-file <path>`：指定环境文件（默认 `docs/dev-ops/.env.cloud`）
- `--with-ops-ui`：同时启动 pgAdmin / Redis Commander
- `--skip-migrations`：跳过数据库迁移（默认执行 `migrations/V*.sql`）
- `--wait-seconds <n>`：健康检查超时秒数（默认 `180`）

脚本会在部署失败时自动输出 `agent` 最近日志，便于排障。

### 2) 配置开发环境

默认使用：

- `agent-app/src/main/resources/application.yml`（激活 `dev`）
- `agent-app/src/main/resources/application-dev.yml`

模型相关配置位于 `application-dev.yml`：

- `spring.ai.openai.api-key`
- `spring.ai.openai.base-url`
- `spring.ai.openai.chat.completions-path`

数据库配置支持环境变量覆盖（`DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD`），便于本地 Docker 与 CI 复用同一套参数。

生产 profile (`application-prod.yml`) 需要显式提供：

- `DB_PASSWORD`
- `OPENAI_API_KEY`
- `APP_AUTH_LOCAL_PASSWORD`
- `APP_AUTH_JWT_SECRET`
- `APP_SHARE_TOKEN_SALT`

建议按网络情况调优：

- `PLANNER_ROOT_TIMEOUT_SOFT_MS`（Root 规划软超时，默认 `60000`；跨公网模型调用可视情况提高到 `90000`）

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

## 端到端压测脚本（会话 -> SSE 终态）

压测脚本：`scripts/chat_e2e_perf.py`

示例：

```bash
python3 scripts/chat_e2e_perf.py \
  --base-url http://127.0.0.1:8091 \
  --user-id perf-user \
  --requests 20 \
  --concurrency 5 \
  --auth-username admin \
  --auth-password admin123 \
  --budget-file scripts/perf/chat_e2e_budget.json
```

基线快捷脚本：

```bash
bash scripts/perf/run_chat_e2e_baseline.sh
```

脚本会输出：
- 提交成功率、终态收敛成功率（`answer.final + stream.completed`）
- 收敛时延（平均/P95）与首事件时延
- SSE 重连率、总重连次数、每请求平均重连次数
- SLO 预算校验结果（`PASS/FAIL`，失败时退出码为 `2`）
- 明细结果 JSON（默认写入 `scripts/output/perf-chat-e2e-*.json`）

## 关键配置说明

### Root 规划与 Workflow Draft

配置位置：`agent-app/src/main/resources/application-dev.yml`、`agent-app/src/main/resources/application-prod.yml`

- `planner.root.enabled`
- `planner.root.agent-key`（Root AgentProfile Key）
- `planner.root.retry.max-attempts`
- `planner.root.retry.backoff-ms`
- `planner.root.timeout.soft-ms`（Root 规划软超时，超时后快速降级）
- `planner.root.fallback.single-node.enabled`
- `planner.root.fallback.agent-key`（Draft 节点缺省 agentKey，当前默认 `assistant`）

生产环境推荐通过环境变量覆盖：
- `PLANNER_ROOT_TIMEOUT_SOFT_MS`（默认 `60000`）

### 最小登录能力（JWT）

配置位置：`agent-app/src/main/resources/application.yml`

- `app.auth.local.username`（本地登录用户名，默认 `admin`）
- `app.auth.local.password`（本地登录密码，默认 `admin123`，生产建议环境变量覆盖）
- `app.auth.local.display-name`（控制台展示昵称）
- `app.auth.token.ttl-hours`（兼容旧配置：未显式配置 JWT 分钟级 TTL 时使用）
- `app.auth.jwt.issuer`（JWT 签发方）
- `app.auth.jwt.access-ttl-minutes`（JWT 访问令牌有效期，分钟）
- `app.auth.jwt.secret`（JWT HS256 签名密钥，生产必填）

默认行为：
- `ApiAuthFilter` 对 `/api/**` 统一鉴权；白名单仅放行 `/api/auth/login` 与 `/api/share/tasks/**`。
- 登录态采用 `JWT + auth_session_blacklist(jti)` 吊销机制，支持多实例一致失效语义。
- 浏览器 SSE 因标准限制无法携带 `Authorization` 头，`/api/v3/chat/sessions/{id}/stream` 支持 `accessToken` query 参数鉴权。
- 前端收到 `401` 会自动清理本地登录态并跳转 `/login`。

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

### 发布灰度开关（会话规划链路）

配置位置：`agent-app/src/main/resources/application*.yml`

- `release-control.chat-planning.enabled`（会话入口是否允许派发规划任务）
- `release-control.chat-planning.traffic-percent`（按 `sessionId:turnId` 哈希放量比例）
- `release-control.chat-planning.kill-switch`（紧急熔断，优先级高于 enabled）

### 规划悬挂回合恢复

配置位置：`agent-app/src/main/resources/application*.yml`

- `chat.planning-recovery.poll-interval-ms`（恢复任务轮询间隔）
- `chat.planning-recovery.timeout-minutes`（回合停留 `PLANNING` 的超时阈值）
- `chat.planning-recovery.batch-size`（每轮恢复扫描上限）

默认行为：
- 守护任务会扫描超时未推进的 `PLANNING` 回合，并兜底收敛为 `FAILED`。
- 恢复链路复用终态幂等写入（终态条件更新 + final assistant message 去重保存）。

### SSE 回放参数

配置位置：`agent-app/src/main/resources/application.yml`

- `sse.replay.batch-size`（每次回放 sweep 的单批次查询上限）
- `sse.replay.max-batches-per-sweep`（每次 sweep 对单订阅者最多回放批次数）

前端默认行为：
- SSE `onerror` 若判断为短暂链路抖动（22 秒内仍有事件/心跳）会静默等待自动恢复，不立即提示“重连中”。
- 仅在确认失联后执行指数退避重连，重连上限后切换历史自动同步兜底。

后端默认行为：
- V3 SSE 响应头默认包含 `Cache-Control: no-cache, no-transform` 与 `X-Accel-Buffering: no`，降低中间层缓冲导致的假断流。
- 断线重连时（`lastEventId > 0`）仅回放漏事件，不重复发送 `message.accepted/planning.started` 引导事件。
- 任务/计划流事件 `metadata` 统一输出标准字段 `nodeId/taskName`，并保留历史字段（如 `taskNodeId`）兼容。

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

### 告警目录看板替换

配置位置：`agent-app/src/main/resources/application*.yml`

- `observability.alert-catalog.dashboard.prod-base-url`（`env=prod` 告警项 dashboard 占位符替换）
- `observability.alert-catalog.dashboard.staging-base-url`（`env=staging` 告警项 dashboard 占位符替换）
- `observability.alert-catalog.link-check.enabled`（是否开启定时链接巡检）
- `observability.alert-catalog.link-check.interval-ms`（巡检间隔）
- `observability.alert-catalog.link-check.http-timeout-ms`（HTTP 探测超时）
- `observability.alert-catalog.link-check.history-size`（保留最近巡检快照数量，用于趋势对比）
- `observability.alert-catalog.link-check.trend-delta-threshold`（失败率趋势阈值，低于阈值按 `FLAT` 处理）
- `observability.alert-catalog.link-check.max-issue-log-count`（单次日志输出上限）

默认行为：
- 启动时会巡检告警目录中未替换的 dashboard 占位符，并输出告警日志。
- 若开启 `link-check.enabled`，后台会定时探测每条告警的 `dashboard/runbook` 可达性并输出汇总日志。
- 监控总览页会展示巡检状态快照（`status/failureRate/env/module`）及最近趋势，并支持 `window` 时间窗口切换。
- 监控总览页支持直接打开 dashboard 链接。

## 文档导航

- 架构文档：`docs/02-system-architecture.md`
- 数据模型与 SQL：`docs/design/07-data-model-and-sql.md`
- UI/UX 规范：`docs/03-ui-ux-spec.md`
- 开发任务清单：`docs/04-development-backlog.md`

- 产品需求文档：`docs/01-product-requirements.md`
- 运维与 SQL 文档：`docs/dev-ops/`
- 前端说明：`frontend/README.md`

## Workflow 治理接口

- `GET /api/workflows/drafts`：查询 Draft 列表（支持按状态筛选）。
- `GET /api/workflows/drafts/{id}`：查询 Draft 详情。
- `PUT /api/workflows/drafts/{id}`：更新 Draft（仅非 `ARCHIVED/PUBLISHED`）；支持提交 `sopSpec`，服务端自动编译为 Runtime Graph。
- `POST /api/workflows/sop-spec/drafts/{id}/compile`：编译 `sopSpec` 并返回 Runtime Graph 预览、`compileHash` 与警告信息。
- `POST /api/workflows/sop-spec/drafts/{id}/validate`：校验 `sopSpec`，返回 `pass/issues/warnings`。
- `POST /api/workflows/drafts/{id}/publish`：发布 Draft 为新 Definition 版本。
- `GET /api/workflows/definitions`：查询 Definition 列表。
- `GET /api/workflows/definitions/{id}`：查询 Definition 详情。

前端 `Workflow Draft` 页面已支持 SOP 图形化编排（节点拖拽、依赖连线、策略编辑、分组批量操作、循环依赖路径定位高亮、自动修复预演、编译预览与保存）。

### Workflow Graph DSL v2（SOP 编排）

- 治理层单一事实源为 `sopSpec`；执行层使用编译产物 `graphDefinition(version=2)`。
- 术语约定：历史文档中的 `SOP/DRG/DAG` 在运行时统一映射为 `Workflow Graph`。
- 发布前会校验 `compileHash` 与当前 Runtime Graph 一致性，不一致则阻断发布并提示重新编译保存。
- `WorkflowGraphPolicyKernel` 作为 Graph 规则内核单源，统一承载 Graph DSL v2 归一化/校验/策略解析；`GraphDslPolicyService` 负责基础校验与签名/哈希算法。
- 当前运行时统一以 `graphDefinition.version = 2` 执行；发布与更新 Draft 时会强校验 `version=2`。
- 候选 Draft（Root 规划产物）允许版本兼容升级：当仅缺失/非 2 但节点结构可执行时，Planner 会自动补齐为 `version=2`（并补齐缺省 `groups/edges`）。
- 候选 Draft 结构性非法（如边指向不存在节点）会判定为不可重试错误，Root 规划直接快速降级，避免 3 次无效重试。
- Root 规划增加软超时（`planner.root.timeout.soft-ms`），超时视为不可重试并直接降级单节点 Draft，缩短入口等待。
- Root 基线 Agent（`root`）默认补齐 `model_options.maxTokens/maxCompletionTokens=768`，抑制长输出导致的规划软超时。
- Root 降级策略统一由 `PlannerFallbackPolicyDomainService` 承载（重试判定、fallbackReason 归一、指标标签规范化）。
- 最小结构：`{ version, nodes, edges, groups }`，其中 `nodes` 必填，`groups` 可为空数组。
- 节点/分组可配置依赖汇聚策略：
  - `joinPolicy`: `all | any | quorum`
  - `failurePolicy`: `failFast | failSafe`
  - `quorum`: 当 `joinPolicy=quorum` 时生效
- Planner 在展开 Task 时会把策略写入 `task.configSnapshot.graphPolicy`，调度与 Plan 收敛统一按该策略执行。
- Workflow 版本迁移策略（兼容矩阵、SQL 模板、回滚规范）见：`docs/design/10-workflow-version-migration.md`。

## 会话编排 V3 接口（推荐）

- `POST /api/v3/chat/messages`：统一会话编排入口（自动创建/复用 Session + 创建 Turn，先 ACK 后异步触发 Plan）。
  - 请求建议携带 `clientMessageId` 作为幂等键；重复提交会复用已有 Turn。
  - 数据库对 `session_turns(session_id, client_message_id)` 建有唯一索引，并发重复提交冲突会自动回退到“复用已有 Turn”语义。
  - 响应新增 `accepted/submissionState/acceptedAt`，`planId` 可能延后出现在 history 中。
- `GET /api/v3/chat/sessions/{id}/history`：聚合返回会话历史分页（session/turns/messages + latestPlanId + `hasMore/nextCursor/limit/order`）。
  - 支持参数：`cursor`（游标）、`limit`（默认 50，最大 200）、`order`（`asc|desc`，前端默认 `desc` 拉取最新页）。
- `GET /api/v3/chat/sessions/{id}/stream?planId=...`：聊天语义 SSE（`message.accepted`、`task.progress`、`answer.final`、`stream.completed` 等）。
- `GET /api/v3/chat/plans/{id}/routing`：查询路由决策详情（V2 路由接口替代）。
- 默认策略：优先使用 `assistant`（若存在且激活），否则使用首个激活 Agent；无可用 Agent 时返回明确错误。
- 输入校验策略：当 Workflow `inputSchema.required` 包含系统上下文字段（如 `sessionId`）时，由 Planner 从运行时上下文自动注入，避免误报 `Missing required input`。
- 生产 Definition 缺参降级：若命中生产 Definition 后仍缺少必填输入，Planner 自动回退至 Root 候选 Draft，路由 `reason=PRODUCTION_DEFINITION_INPUT_MISSING`，避免回合直接失败。

## 执行幂等与终态收敛补充

- `task_executions` 新增唯一约束 `(task_id, attempt_number)`，同一次尝试重复写入会复用已有执行记录。
- `POST /api/tasks/{id}/cancel` 触发 Plan 取消后，会同步执行 Turn 终态收敛并输出 `CANCELLED` 终态消息。

## 接口基线说明

- 当前仅维护 V3 会话主链路：`/api/v3/chat/messages`、`/api/v3/chat/sessions/{id}/history?cursor=&limit=&order=`、`/api/v3/chat/sessions/{id}/stream`、`/api/v3/chat/plans/{id}/routing`。
- 历史入口代码已删除，不再提供兼容路由。
- 只读查询统一为 `/api/v3/chat/sessions/{id}/history`、`/api/sessions/list`、`/api/tasks/paged`、`/api/logs/paged`、`/api/logs/tool-policy/paged`、`/api/quality/evaluations/paged`、`/api/quality/evaluations/experiments/summary`、`/api/agents/tools?limit={N}`、`/api/agents/vector-stores?limit={N}`。
- 已移除遗留只读接口：`/api/plans/{id}`、`/api/plans/{id}/tasks`、`/api/tasks/{id}/executions`。
- 已下线知识库检索测试占位接口：`/api/knowledge-bases/{id}/retrieval-tests`（待真实召回链路接入后再恢复）。
- 查询性能：`/api/tasks/paged`、`/api/dashboard/overview` 已使用批量 latestExecution 查询，避免 N+1。
- 观测闭环：`/api/logs/paged` 已改为 DB 侧分页查询；`/api/logs/tool-policy/paged` 支持按 `policyAction/policyMode` 回放工具策略命中事件；`/api/quality/evaluations/paged` + `/api/quality/evaluations/experiments/summary` 支持 A/B 分桶质量事件回放与聚合统计；`/api/agents/tools` 与 `/api/agents/vector-stores` 默认仅读取最近 100 条（最大 500）；`GET /api/observability/alerts/catalog` 提供告警目录与 runbook 入口；`GET /api/observability/alerts/probe-status` 返回告警目录巡检状态快照（支持 `window` 参数筛选趋势窗口）。

### 会话编排 V3 最小验证流程

- 发送消息：`POST /api/v3/chat/messages`
- 回查历史：`GET /api/v3/chat/sessions/{sessionId}/history`
- 当 history 出现 `planId` 后订阅流式执行：`GET /api/v3/chat/sessions/{sessionId}/stream?planId={planId}`
- 回查路由：`GET /api/v3/chat/plans/{planId}/routing`
- 前端会话页具备自动收敛机制：SSE 断链最多自动重连 3 次，仍失败则自动轮询历史（30s 超时）以同步最终结果。
- 移动端会话页支持“历史对话”抽屉化入口，保留主聊天区聚焦同时可快速切换会话。

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
- 本地依赖启动：`bash scripts/devops/local-up.sh`
- 本地依赖停机：`bash scripts/devops/local-down.sh`
- 容器化启动应用：`bash scripts/devops/local-up.sh --with-app`
- 初始化 Git hooks：`bash scripts/setup-git-hooks.sh`
- 指定回归：
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=PlannerServiceRootDraftTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=ConversationOrchestratorServiceTest,ChatV3ControllerTest,ChatStreamV3ControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=TaskExecutorPlanBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=TaskExecutionRunnerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=TurnResultServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=SessionConversationDomainServiceTest,PlanFinalizationDomainServiceTest,PlanTransitionDomainServiceTest,TaskExecutionDomainServiceTest,TaskPromptDomainServiceTest,TaskEvaluationDomainServiceTest,TaskRecoveryDomainServiceTest,TaskAgentSelectionDomainServiceTest,TaskBlackboardDomainServiceTest,TaskJsonDomainServiceTest,TaskPersistencePolicyDomainServiceTest,TaskPersistenceApplicationServiceTest,TaskDependencyPolicyDomainServiceTest,TaskScheduleApplicationServiceTest,PlanStatusSyncApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=PlanStatusDaemonTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dit.docker.enabled=true -Dtest=SessionChatPlanSseIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dit.docker.enabled=true -Dtest=ExecutorTerminalConvergenceIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 分享闭环（需 Docker）：`mvn -pl agent-app -am -DskipTests=false -Dit.docker.enabled=true -Dtest=TaskShareLinkControllerIntegrationTest,ShareAccessControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Docker 集成测试前置（Docker Desktop on macOS）：

- `export DOCKER_HOST=unix:///Users/${USER}/.docker/run/docker.sock`
- `export DOCKER_API_VERSION=1.44`

## CI 基线

仓库新增 GitHub Actions：`.github/workflows/ci.yml`，包括：

- 后端构建：`mvn -DskipTests clean package`
- 后端冒烟回归：`PlannerServiceRootDraftTest`、`TaskExecutorPlanBoundaryTest`、`TurnResultServiceTest`
- 前端构建：`frontend` 下 `npm ci && npm run build`
- Compose 校验：对 `docs/dev-ops/docker-compose-environment.yml` 与 `docker-compose-app.yml` 执行 `docker compose config`
- Observability 门禁：`bash scripts/devops/observability-gate.sh`（`promtool check/test` + 告警目录 `TODO:` 链接阻断）

## 术语约定

- `Workflow Definition`：生产流程定义，版本不可变。
- `Workflow Draft`：运行时草案与治理对象。
- `Routing Decision`：路由命中/兜底的审计记录。
- `Plan`：一次用户输入触发的执行实例，执行事实源是 `execution_graph`。
- `Task`：Plan 内节点任务。
- `AgentProfile`：`agent_registry` 中的执行配置。
- `TaskClient`：任务执行时创建的运行时客户端（底层 `ChatClient`）。
