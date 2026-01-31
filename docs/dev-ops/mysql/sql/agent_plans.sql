DROP TYPE IF EXISTS plan_status_enum;
CREATE TYPE plan_status_enum AS ENUM ('PLANNING', 'READY', 'RUNNING', 'PAUSED', 'COMPLETED', 'FAILED', 'CANCELLED');

CREATE TABLE agent_plans (
                             id                  BIGSERIAL PRIMARY KEY,
                             session_id          BIGINT NOT NULL, -- 逻辑关联: agent_sessions.id
                             sop_template_id     BIGINT,          -- 逻辑关联: sop_templates.id (可空)

                             plan_goal           TEXT NOT NULL,
                             execution_graph     JSONB NOT NULL,  -- 运行时图谱副本
                             global_context      JSONB DEFAULT '{}'::jsonb, -- 黑板

                             status              plan_status_enum NOT NULL DEFAULT 'PLANNING',
                             priority            INTEGER DEFAULT 0,

                             error_summary       TEXT,
                             version             INTEGER NOT NULL DEFAULT 0, -- 乐观锁

                             created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                             updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
-- 手动创建关联索引
CREATE INDEX idx_plans_session_id ON agent_plans(session_id);
-- 状态查询索引
CREATE INDEX idx_plans_status_priority ON agent_plans(status, priority DESC);