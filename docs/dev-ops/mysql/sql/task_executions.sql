CREATE TABLE task_executions (
                                 id                  BIGSERIAL PRIMARY KEY,
                                 task_id             BIGINT NOT NULL, -- 逻辑关联: agent_tasks.id

                                 attempt_number      INTEGER NOT NULL,

    -- 审计字段
                                 prompt_snapshot     TEXT, -- 包含 System + User + History
                                 llm_response_raw    TEXT,

                                 model_name          VARCHAR(100),
                                 token_usage         JSONB,
                                 execution_time_ms   BIGINT,

    -- 验证结果
                                 is_valid            BOOLEAN,
                                 validation_feedback TEXT,
                                 error_message       TEXT,

                                 created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
-- 手动创建关联索引
CREATE INDEX idx_executions_task_id ON task_executions(task_id);
-- 历史回溯索引
CREATE INDEX idx_executions_lookup ON task_executions(task_id, attempt_number DESC);