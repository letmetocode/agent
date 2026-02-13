-- 执行与终态收敛 3.2 回滚脚本（谨慎执行）
-- 说明：仅回滚唯一索引，已删除的历史重复消息不会自动恢复。

BEGIN;

DROP INDEX IF EXISTS uq_session_messages_turn_assistant;

COMMIT;
