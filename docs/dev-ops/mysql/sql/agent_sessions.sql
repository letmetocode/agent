CREATE TABLE agent_sessions (
                                id                  BIGSERIAL PRIMARY KEY,
                                user_id             VARCHAR(100) NOT NULL, -- 外部用户ID
                                title               VARCHAR(200),
                                is_active           BOOLEAN DEFAULT TRUE,

    -- 扩展字段 (存租户ID等)
                                meta_info           JSONB DEFAULT '{}'::jsonb,

                                created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sessions_user ON agent_sessions(user_id);