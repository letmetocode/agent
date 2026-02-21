# 07 Data Model and SQL（结果态）

更新时间：2026-02-20  
权威 Schema：`docs/dev-ops/postgresql/sql/01_init_database.sql`

## 1. 数据库落地范围

- 会话链路：`agent_sessions` / `session_turns` / `session_messages`
- 路由与规划：`workflow_definitions` / `workflow_drafts` / `routing_decisions` / `agent_plans` / `agent_tasks`
- 执行与事件：`task_executions` / `plan_task_events` / `quality_evaluation_events`
- Agent 资产：`agent_registry` / `agent_tool_catalog` / `agent_tools` / `vector_store_registry`
- 访问控制：`auth_session_blacklist` / `task_share_links`

## 2. 本轮关键约束（2026-02）

- 会话提交幂等：
  - `session_turns.client_message_id` 为显式列（不再仅依赖 metadata JSON 字段）。
  - 唯一索引：`uq_session_turns_session_client_message (session_id, client_message_id)`（`client_message_id IS NOT NULL`）。
- 回合终态幂等：
  - `session_messages` 维持同一 `turn_id` 仅 1 条 `ASSISTANT` 最终消息的约束。
  - Plan 进入 `COMPLETED/FAILED/CANCELLED` 时统一触发 Turn finalize。
- 执行记录幂等：
  - `task_executions` 新增唯一索引 `uq_task_executions_task_attempt (task_id, attempt_number)`。
  - 仓储层在唯一冲突时复用已有执行记录，避免重复审计写入。

## 3. 查询与映射基线

- Turn 映射：`agent-app/src/main/resources/mybatis/mapper/SessionTurnMapper.xml`
  - `client_message_id` 已纳入 `resultMap/insert/update/select`。
  - `selectLatestBySessionIdAndClientMessageId` 兼容旧数据回查（列优先，metadata 回退）。
- Execution 映射：`agent-app/src/main/resources/mybatis/mapper/TaskExecutionMapper.xml`
  - 按 `attempt_number` 有序查询。
  - 由数据库唯一索引兜底防重。

## 4. 迁移与校验

- 全新环境：直接执行 `docs/dev-ops/postgresql/sql/01_init_database.sql`。
- 存量环境：
  - 执行迁移：`docs/dev-ops/postgresql/sql/migrations/V20260220_04_session_turn_idempotency_and_execution_dedupe.sql`。
  - 回滚脚本：`docs/dev-ops/postgresql/sql/migrations/V20260220_04_session_turn_idempotency_and_execution_dedupe_rollback.sql`。
  - 发布前建议执行 `bash scripts/devops/check-schema-drift.sh`。
- 历史设计说明参考：`docs/archive/design/07-data-model-and-sql.md`。
