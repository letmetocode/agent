-- =====================================================
-- Agent 系统 PostgreSQL 数据库初始化脚本
-- =====================================================

-- 创建数据库（如果需要）
-- CREATE DATABASE agent_db;
-- CREATE DATABASE agent_db_test;

-- =====================================================
-- 1. Agent 注册表
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_registry (
    id                  BIGSERIAL PRIMARY KEY,
    key                 VARCHAR(100) NOT NULL UNIQUE, -- 业务唯一标识 (如 'java_coder')
    name                VARCHAR(100) NOT NULL,

    -- 模型配置
    model_provider      VARCHAR(50) NOT NULL DEFAULT 'openai',
    model_name          VARCHAR(100) NOT NULL,
    model_options       JSONB DEFAULT '{"temperature": 0.7}'::jsonb,

    -- 基础人设 (System Prompt)
    base_system_prompt  TEXT,

    -- [Spring AI 1.1.2 核心] Advisors 配置
    -- 示例: {"memory": {"enabled": true}, "rag": {"enabled": true}}
    advisor_config      JSONB DEFAULT '{}'::jsonb,

    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE agent_registry IS 'Agent 注册表：存储不同类型 Agent 的配置信息';
COMMENT ON COLUMN agent_registry.key IS 'Agent 唯一标识，如 java_coder、python_coder';
COMMENT ON COLUMN agent_registry.model_options IS '模型参数配置，JSON 格式';
COMMENT ON COLUMN agent_registry.advisor_config IS 'Spring AI Advisors 配置';

-- =====================================================
-- 2. Agent 会话表
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_sessions (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             VARCHAR(100) NOT NULL, -- 外部用户ID
    title               VARCHAR(200),
    is_active           BOOLEAN DEFAULT TRUE,

    -- 扩展字段 (存租户ID等)
    meta_info           JSONB DEFAULT '{}'::jsonb,

    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sessions_user ON agent_sessions(user_id);

COMMENT ON TABLE agent_sessions IS '用户会话表：跟踪用户与 Agent 的交互会话';

-- =====================================================
-- 3. SOP 模板表
-- =====================================================
DROP TYPE IF EXISTS sop_structure_enum CASCADE;
CREATE TYPE sop_structure_enum AS ENUM ('CHAIN', 'DAG');

CREATE TABLE IF NOT EXISTS sop_templates (
    id                  BIGSERIAL PRIMARY KEY,
    category            VARCHAR(50) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    version             INTEGER NOT NULL DEFAULT 1,

    trigger_description TEXT NOT NULL, -- Planner 语义检索用

    structure_type      sop_structure_enum NOT NULL DEFAULT 'DAG',
    graph_definition    JSONB NOT NULL, -- 核心流程图 (Nodes/Edges)

    input_schema        JSONB DEFAULT '{}'::jsonb,
    default_config      JSONB DEFAULT '{}'::jsonb,

    is_active           BOOLEAN DEFAULT TRUE,
    created_by          VARCHAR(100) DEFAULT 'SYSTEM',
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 业务主键约束
    CONSTRAINT uq_sop_identity UNIQUE (category, name, version)
);

CREATE INDEX IF NOT EXISTS idx_sop_lookup ON sop_templates(category, name, version, is_active);
CREATE INDEX IF NOT EXISTS idx_sop_graph_gin ON sop_templates USING GIN (graph_definition);

COMMENT ON TABLE sop_templates IS 'SOP（标准作业程序）模板表：定义可复用的工作流';
COMMENT ON COLUMN sop_templates.trigger_description IS '用于 Planner 语义检索的描述文本';

-- =====================================================
-- 4. Agent 计划表
-- =====================================================
DROP TYPE IF EXISTS plan_status_enum CASCADE;
CREATE TYPE plan_status_enum AS ENUM ('PLANNING', 'READY', 'RUNNING', 'PAUSED', 'COMPLETED', 'FAILED', 'CANCELLED');

CREATE TABLE IF NOT EXISTS agent_plans (
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

CREATE INDEX IF NOT EXISTS idx_plans_session_id ON agent_plans(session_id);
CREATE INDEX IF NOT EXISTS idx_plans_status_priority ON agent_plans(status, priority DESC);

COMMENT ON TABLE agent_plans IS 'Agent 执行计划表：存储任务执行计划和状态';

-- =====================================================
-- 5. Agent 任务表
-- =====================================================
DROP TYPE IF EXISTS task_status_enum CASCADE;
CREATE TYPE task_status_enum AS ENUM ('PENDING', 'READY', 'RUNNING', 'VALIDATING', 'REFINING', 'COMPLETED', 'FAILED', 'SKIPPED');

CREATE TABLE IF NOT EXISTS agent_tasks (
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
    claim_owner         VARCHAR(128),
    claim_at            TIMESTAMP WITH TIME ZONE,
    lease_until         TIMESTAMP WITH TIME ZONE,
    execution_attempt   INTEGER NOT NULL DEFAULT 0,

    version             INTEGER DEFAULT 0, -- 乐观锁
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 业务约束：同一 Plan 下 node_id 唯一
    CONSTRAINT uq_plan_node UNIQUE (plan_id, node_id)
);

CREATE INDEX IF NOT EXISTS idx_tasks_plan_id ON agent_tasks(plan_id);
CREATE INDEX IF NOT EXISTS idx_tasks_scheduling ON agent_tasks(plan_id, status);
CREATE INDEX IF NOT EXISTS idx_tasks_claim_scan ON agent_tasks(status, lease_until, plan_id, created_at);
CREATE INDEX IF NOT EXISTS idx_tasks_claim_owner_lease ON agent_tasks(claim_owner, lease_until);

COMMENT ON TABLE agent_tasks IS 'Agent 任务表：存储计划中的具体任务及执行状态';

-- =====================================================
-- 6. 任务执行记录表
-- =====================================================
CREATE TABLE IF NOT EXISTS task_executions (
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

CREATE INDEX IF NOT EXISTS idx_executions_task_id ON task_executions(task_id);
CREATE INDEX IF NOT EXISTS idx_executions_lookup ON task_executions(task_id, attempt_number DESC);

COMMENT ON TABLE task_executions IS '任务执行记录表：存储每次执行的详细历史';

-- =====================================================
-- 7. Agent 工具目录表
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_tool_catalog (
    id                  BIGSERIAL PRIMARY KEY,
    tool_name           VARCHAR(100) NOT NULL UNIQUE,
    tool_type           VARCHAR(50) NOT NULL, -- 'FUNCTION', 'API', 'PLUGIN'
    description         TEXT,

    -- 工具配置
    tool_config         JSONB NOT NULL,
    input_schema        JSONB DEFAULT '{}'::jsonb,
    output_schema       JSONB DEFAULT '{}'::jsonb,

    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE agent_tool_catalog IS 'Agent 工具目录：注册和管理可用的工具/函数';

-- =====================================================
-- 8. Agent 工具关联表
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_tools (
    id                  BIGSERIAL PRIMARY KEY,
    agent_id            BIGINT NOT NULL, -- 逻辑关联: agent_registry.id
    tool_id             BIGINT NOT NULL, -- 逻辑关联: agent_tool_catalog.id

    is_enabled          BOOLEAN DEFAULT TRUE,
    priority            INTEGER DEFAULT 0,

    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_agent_tool UNIQUE (agent_id, tool_id)
);

CREATE INDEX IF NOT EXISTS idx_agent_tools_agent ON agent_tools(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_tools_tool ON agent_tools(tool_id);

COMMENT ON TABLE agent_tools IS 'Agent 与工具的关联表：定义哪些 Agent 可以使用哪些工具';

-- =====================================================
-- 9. 向量存储注册表
-- =====================================================
CREATE TABLE IF NOT EXISTS vector_store_registry (
    id                  BIGSERIAL PRIMARY KEY,
    store_name          VARCHAR(100) NOT NULL UNIQUE,
    store_type          VARCHAR(50) NOT NULL, -- 'PGVECTOR', 'CHROMA', 'FAISS', 'MILVUS'

    -- 存储配置
    connection_config   JSONB NOT NULL,
    collection_name     VARCHAR(100),

    dimension           INTEGER DEFAULT 1536,
    is_active           BOOLEAN DEFAULT TRUE,

    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE vector_store_registry IS '向量存储注册表：管理 RAG 功能的向量数据库配置';

-- =====================================================
-- 初始化数据
-- =====================================================

-- 插入默认 SOP 模板示例（可选）
INSERT INTO sop_templates (category, name, trigger_description, structure_type, graph_definition, default_config)
VALUES (
    'CODE_GENERATION',
    'java_crud_generator',
    '生成 Java CRUD 代码，包括 Entity、Mapper、Service、Controller',
    'DAG'::sop_structure_enum,
    '{
        "nodes": [
            {"id": "generate_entity", "type": "WORKER", "name": "生成实体类"},
            {"id": "validate_entity", "type": "CRITIC", "name": "验证实体类"},
            {"id": "generate_mapper", "type": "WORKER", "name": "生成 Mapper"},
            {"id": "generate_service", "type": "WORKER", "name": "生成 Service"},
            {"id": "generate_controller", "type": "WORKER", "name": "生成 Controller"}
        ],
        "edges": [
            {"from": "generate_entity", "to": "validate_entity"},
            {"from": "validate_entity", "to": "generate_mapper"},
            {"from": "generate_mapper", "to": "generate_service"},
            {"from": "generate_service", "to": "generate_controller"}
        ]
    }'::jsonb,
    '{"max_retries": 3, "timeout": 300}'::jsonb
) ON CONFLICT (category, name, version) DO NOTHING;

-- =====================================================
-- 结束
-- =====================================================
