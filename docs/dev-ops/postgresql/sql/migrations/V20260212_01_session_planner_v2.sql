-- 会话与规划 V2 增量迁移脚本
-- 目标：补齐 session 绑定 Agent 语义与 routing 决策可观测字段

BEGIN;

ALTER TABLE agent_sessions
    ADD COLUMN IF NOT EXISTS agent_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS scenario VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_sessions_agent_key ON agent_sessions(agent_key);
CREATE INDEX IF NOT EXISTS idx_sessions_scenario ON agent_sessions(scenario);

ALTER TABLE routing_decisions
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS fallback_flag BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS fallback_reason VARCHAR(128),
    ADD COLUMN IF NOT EXISTS planner_attempts INTEGER DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_routing_decision_source_type
    ON routing_decisions(source_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_routing_decision_fallback
    ON routing_decisions(fallback_flag, created_at DESC);

-- 历史数据兜底
UPDATE routing_decisions
SET source_type = COALESCE(source_type, metadata ->> 'sourceType', 'UNKNOWN'),
    fallback_flag = COALESCE(fallback_flag, false),
    fallback_reason = COALESCE(fallback_reason, metadata ->> 'fallbackReason'),
    planner_attempts = COALESCE(planner_attempts, 0)
WHERE source_type IS NULL
   OR fallback_reason IS NULL
   OR planner_attempts IS NULL;

COMMIT;

-- 校验 SQL：
-- SELECT COUNT(*) FROM agent_sessions WHERE agent_key IS NOT NULL;
-- SELECT source_type, fallback_flag, COUNT(*) FROM routing_decisions GROUP BY source_type, fallback_flag;
