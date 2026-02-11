# 系统架构总览

## 1. 目标与当前阶段

- 当前项目处于开发阶段，优先目标是功能完备与系统健康。
- 架构与实现以长期可维护为优先，避免临时性补丁。
- 技术基线：Java 17、Spring Boot 3.4.3、Spring AI 1.1.2、MyBatis、PostgreSQL。

## 2. 模块与分层

- `agent-app`：应用启动、配置装配、调度线程池与 MyBatis 资源承载。
- `agent-trigger`：入口适配层，负责 HTTP/SSE 接口与后台守护任务。
- `agent-domain`：领域模型、状态机、仓储接口与领域服务接口。
- `agent-infrastructure`：DAO、Mapper 适配、仓储实现、AI 工厂、MCP 适配、规划实现。
- `agent-api`：对外 DTO 和统一响应对象。
- `agent-types`：通用枚举、异常、常量。

分层原则：
- `trigger` 仅依赖 `domain` 接口，不直接操作 SQL。
- `domain` 不依赖 `infrastructure` 具体实现。
- `infrastructure` 实现 `domain` 端口，并通过 MyBatis 与外部服务落地。

## 2.1 术语词典（统一约束）

- `Plan`：一次用户输入触发的完整执行编排（总任务）。
- `Task`：Plan 内的节点任务（Workflow 节点实例）。
- `Workflow Definition`：生产流程定义（版本不可变）。
- `Workflow Draft`：运行时草案与治理对象。
- `Routing Decision`：路由命中/兜底决策审计记录。
- `AgentProfile`：可复用执行配置（来源 `agent_registry`，包含模型、工具、advisor）。
- `TaskClient`：Task 执行时创建的运行时客户端（底层类型是 `ChatClient`）。

命名约束：
- 不再使用“agent=总任务”的表述。
- 总任务统一叫 `Plan`；节点执行客户端统一叫 `TaskClient`。

## 3. 核心业务主链路

### 3.1 会话与规划

1. 客户端调用 `POST /api/sessions` 创建会话。
2. 客户端调用 `POST /api/sessions/{id}/chat` 触发规划。
3. `PlannerService` 先路由 Workflow，再创建 Plan，展开 DAG 生成 Task。
4. Plan 从 `PLANNING` 进入 `READY`，等待执行。

### 3.2 任务调度与执行

1. `TaskSchedulerDaemon` 周期检查 `PENDING` 任务依赖，满足后推进为 `READY`。
2. `TaskExecutor` 原子 claim 可执行任务，状态置 `RUNNING`，并写入 `claim_owner` 与 `lease`。
3. 执行过程中按 lease 周期续约，结束后按 `claim_owner + execution_attempt` 条件写回终态。

### 3.3 计划状态闭环

1. `PlanStatusDaemon` 对 `READY/RUNNING` 计划按分页批量统计任务状态。
2. 依据聚合结果推进 Plan：`READY -> RUNNING -> COMPLETED/FAILED`。
3. 终态 Plan 不再推进，避免无效更新与日志噪声。

### 3.4 SSE 事件驱动推送

1. `TaskExecutor` 和 `PlanStatusDaemon` 在关键状态变更时发布 `PlanTaskEvent`。
2. `PlanTaskEventPublisher` 执行双轨处理：
   - 持久化到 `plan_task_events`（用于断线回放与审计）
   - PostgreSQL `NOTIFY/LISTEN` 广播跨实例事件信号
   - 进程内总线实时分发给 SSE 订阅者
3. `PlanStreamController` 连接建立时先按 `lastEventId` 回放，再进入实时订阅。
4. 连接存活期间通过固定周期增量 replay 兜底，避免跨实例通知丢失导致卡流。

### 3.5 页面查询读模型

1. `QueryController` 提供页面只读 API，覆盖会话、计划、任务、执行记录查询。
2. 首屏优先使用 `GET /api/sessions/{id}/overview` 一次拉取：
   - 会话详情
   - 会话下计划列表（按 `updatedAt` 倒序）
   - 最新计划任务统计
   - 最新计划任务明细
3. SSE 负责“增量变化”，查询 API 负责“首屏基线与历史回看”，两者职责分离。

## 4. 并发与一致性策略

### 4.1 乐观锁

- `agent_plans` 与 `agent_tasks` 均采用 `version` 乐观锁更新。
- SQL 以 `WHERE id = ? AND version = ?` 约束并发写入。
- 更新成功后再提升实体 version，禁止 Java 侧提前自增。

### 4.2 任务 claim 与 lease

- claim 使用数据库原子语义，支持多实例并发消费。
- `execution_attempt` 用作执行代际，防止旧执行者覆盖新执行结果。
- 续约与终态回写必须带 `claim_owner + execution_attempt` 条件。

## 5. 可观测性

已具备：
- 执行器审计日志开关（成功/失败）。
- 过期运行任务数量统计入口。
- `task_executions` 落库补齐 `model_name`、`token_usage`、`error_type`。

建议持续完善：
- claim 成功量、空批量、重领次数。
- 续约成功率与失败原因分布。
- 执行耗时分位与失败分类。

## 6. 配置与运行要点

- 环境配置位于 `agent-app/src/main/resources/application-*.yml`。
- 调度线程建议隔离并显式设置池大小，避免守护任务互相阻塞。
- 核心轮询参数：
  - `scheduler.poll-interval-ms`
  - `executor.poll-interval-ms`
  - `plan-status.poll-interval-ms`
  - claim/lease 相关参数
- SSE 事件推送参数：
  - `sse.heartbeat-interval-ms`
  - `sse.replay-interval-ms`
  - `event.notify.channel`

## 7. 开发约束

- 新增功能优先补齐领域语义，不允许以临时 if 分支绕过状态机。
- 状态迁移规则变更必须同步更新测试与设计文档。
- 任何并发路径变更都必须明确“幂等性、冲突策略、可观测性”三项内容。

## 8. 功能文档映射

- 会话与触发入口：`01-session-and-chat.md`
- 规划与建图：`02-plan-generation.md`
- 依赖调度：`03-task-scheduling.md`
- claim 与执行：`04-task-claim-and-execution.md`
- Plan 聚合推进：`05-plan-status-aggregation.md`
- AgentProfile 工厂与工具：`06-agent-factory-and-tools.md`
- 数据与 SQL：`07-data-model-and-sql.md`
- 观测与运维：`08-observability-and-ops.md`
- 开发与回归清单：`09-dev-guide-and-checklist.md`

## 9. 本轮关键升级

- Task 类型统一为 `TaskTypeEnum`（领域/仓储/Planner/Executor/DB 一致）。
- 执行记录补齐模型与 token 用量采集，失败原因结构化。
- SSE 从“每连接轮询”升级为“事件驱动 + 回放补偿”。
- 新增页面查询 API 与 `overview` 聚合接口，补齐前端首屏渲染与历史查看能力。
- 未命中生产 Definition 时引入 Root Draft 生成链路（最多 3 次重试），失败后自动降级单节点 Draft。
- `routing_decisions + agent_plans + agent_tasks` 同事务原子提交，避免半记录。
- `execution_graph` 作为唯一执行事实源；`definition_snapshot` 仅用于审计解释。
- Root/Assistant 均通过 `agent_registry` 配置，不做代码硬编码；启动阶段执行 Root 可用性健康检查。
- 回合最终输出改为仅汇总 `WORKER` 成果，避免 `CRITIC` JSON 泄露到用户可见回复。
- Task 执行黑板写回改为“读取最新 Plan + 乐观锁有限重试”，降低并发更新告警噪声。
