-- 会话与规划 V2 回滚脚本（谨慎执行）

BEGIN;

DROP INDEX IF EXISTS idx_routing_decision_fallback;
DROP INDEX IF EXISTS idx_routing_decision_source_type;
ALTER TABLE routing_decisions
    DROP COLUMN IF EXISTS planner_attempts,
    DROP COLUMN IF EXISTS fallback_reason,
    DROP COLUMN IF EXISTS fallback_flag,
    DROP COLUMN IF EXISTS source_type;

DROP INDEX IF EXISTS idx_sessions_scenario;
DROP INDEX IF EXISTS idx_sessions_agent_key;
ALTER TABLE agent_sessions
    DROP COLUMN IF EXISTS scenario,
    DROP COLUMN IF EXISTS agent_key;

COMMIT;
