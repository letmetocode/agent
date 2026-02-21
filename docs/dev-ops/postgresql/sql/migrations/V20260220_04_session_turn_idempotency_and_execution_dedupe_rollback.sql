-- rollback for V20260220_04_session_turn_idempotency_and_execution_dedupe
-- 注意：正向迁移中已清理重复 execution 记录，回滚不会恢复被清理数据。

DROP INDEX IF EXISTS uq_task_executions_task_attempt;
DROP INDEX IF EXISTS uq_session_turns_session_client_message;

ALTER TABLE session_turns
    DROP COLUMN IF EXISTS client_message_id;
