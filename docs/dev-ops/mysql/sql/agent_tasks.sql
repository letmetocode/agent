DROP TYPE IF EXISTS task_status_enum;
CREATE TYPE task_status_enum AS ENUM ('PENDING', 'READY', 'RUNNING', 'VALIDATING', 'REFINING', 'COMPLETED', 'FAILED', 'SKIPPED');

CREATE TABLE agent_tasks (
                             id                  BIGSERIAL PRIMARY KEY,
                             plan_id             BIGINT NOT NULL, -- 逻辑关联: agent_plans.id

                             node_id             VARCHAR(50) NOT NULL, -- 图谱中的节点ID
                             name                VARCHAR(200),
                             task_type           VARCHAR(50) NOT NULL, -- 'WORKER', 'CRITIC'

                             status              task_status_enum NOT NULL DEFAULT 'PENDING',

    -- DAG 依赖控制
                             dependency_node_ids JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- 上下文与配置
                             input_context       JSONB DEFAULT '{}'::jsonb,
                             config_snapshot     JSONB NOT NULL,
                             output_result       TEXT,

                             max_retries         INTEGER DEFAULT 3,
                             current_retry       INTEGER DEFAULT 0,

                             version             INTEGER DEFAULT 0, -- 乐观锁
                             created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                             updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 业务约束：同一 Plan 下 node_id 唯一
                             CONSTRAINT uq_plan_node UNIQUE (plan_id, node_id)
);
-- 手动创建关联索引
CREATE INDEX idx_tasks_plan_id ON agent_tasks(plan_id);
-- 调度器轮询索引
CREATE INDEX idx_tasks_scheduling ON agent_tasks(plan_id, status);