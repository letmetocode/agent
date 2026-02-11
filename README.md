# Agent

面向任务编排与执行的 Agent 工程，采用 DDD 分层多模块结构，技术基线为 Spring Boot 3.4.3 + Spring AI 1.1.2 + MyBatis + PostgreSQL。

## 开发阶段原则

项目当前处于开发阶段，优先保证功能完备与系统健康。  
修改与新增以长期可维护方案为先，避免临时补丁与潜在隐患。

## 核心能力（当前基线）

- 会话与回合管理：`session -> turn -> message` 全链路可追踪。
- Workflow 路由：优先命中生产 Definition；未命中时由 Root 生成 Draft 草案。
- Workflow 治理：未命中生产 Definition 的回合可在会话页直接查看/编辑 Draft，并发布为新 Definition 版本。
- Root 兜底：Root 草案重试 3 次失败后，降级为单节点 Draft。
- 执行调度：Task claim + lease + heartbeat，多实例并发安全。
- Plan 闭环：`READY -> RUNNING -> COMPLETED/FAILED` 自动推进。
- 结果汇总：最终用户回复仅汇总 `WORKER` 输出，不直接展示 `CRITIC` JSON。
- 事件流：SSE 实时推送 + `lastEventId` 回放补偿。

## 模块结构

- `agent-app`：启动与全局配置装配（Spring、调度、MyBatis 资源）
- `agent-trigger`：HTTP/SSE 与守护任务入口（调度、执行、状态推进）
- `agent-domain`：领域实体、状态机、仓储端口
- `agent-infrastructure`：DAO/Mapper、仓储实现、Planner/AI/MCP 适配
- `agent-api`：对外 DTO 与统一响应
- `agent-types`：通用枚举、异常、常量

## 快速开始

### 1) 初始化数据库

PostgreSQL 最终版初始化脚本：

- `docs/dev-ops/postgresql/sql/01_init_database.sql`

脚本会初始化核心表、枚举以及基线 AgentProfile（`assistant` 与 `root`）。

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

### 执行兜底 Agent

配置位置：`agent-app/src/main/resources/application.yml`

- `executor.agent.fallback-worker-keys`
- `executor.agent.fallback-critic-keys`
- `executor.agent.default-cache-ttl-ms`

### 执行超时治理

配置位置：`agent-app/src/main/resources/application.yml`

- `executor.execution.timeout-ms`（单次 TaskClient 调用超时时间）
- `executor.execution.timeout-retry-max`（超时后的额外重试次数，默认 1）

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

- 开发设计文档索引：`docs/design/README.md`
- 运维与 SQL 文档：`docs/dev-ops/`
- Git 管理规范：`docs/dev-ops/git-management.md`
- 前端说明：`frontend/README.md`

## Workflow 治理接口

- `GET /api/workflows/drafts`：查询 Draft 列表（支持按状态筛选）。
- `GET /api/workflows/drafts/{id}`：查询 Draft 详情。
- `PUT /api/workflows/drafts/{id}`：更新 Draft（仅非 `ARCHIVED/PUBLISHED`）。
- `POST /api/workflows/drafts/{id}/publish`：发布 Draft 为新 Definition 版本。
- `GET /api/workflows/definitions`：查询 Definition 列表。
- `GET /api/workflows/definitions/{id}`：查询 Definition 详情。

前端会话页在识别到“未命中生产 Definition”时，会展示 Draft 提示与编辑入口。

## 常用命令

- 构建：`mvn clean package`
- 启动：`mvn -pl agent-app -am spring-boot:run`
- 全量单测：`mvn -pl agent-app -am -DskipTests=false test`
- 初始化 Git hooks：`bash scripts/setup-git-hooks.sh`
- 指定回归：
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=PlannerServiceRootDraftTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=TaskExecutorPlanBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl agent-app -am -DskipTests=false -Dtest=TurnResultServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

## 术语约定

- `Workflow Definition`：生产流程定义，版本不可变。
- `Workflow Draft`：运行时草案与治理对象。
- `Routing Decision`：路由命中/兜底的审计记录。
- `Plan`：一次用户输入触发的执行实例，执行事实源是 `execution_graph`。
- `Task`：Plan 内节点任务。
- `AgentProfile`：`agent_registry` 中的执行配置。
- `TaskClient`：任务执行时创建的运行时客户端（底层 `ChatClient`）。
