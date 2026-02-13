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
    agent_key           VARCHAR(128),
    scenario            VARCHAR(64),
    is_active           BOOLEAN DEFAULT TRUE,

    -- 扩展字段 (存租户ID等)
    meta_info           JSONB DEFAULT '{}'::jsonb,

    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sessions_user ON agent_sessions(user_id);

COMMENT ON TABLE agent_sessions IS '用户会话表：跟踪用户与 Agent 的交互会话';

-- =====================================================
-- 3. 会话回合与消息表
-- =====================================================
DROP TYPE IF EXISTS turn_status_enum CASCADE;
DROP TYPE IF EXISTS message_role_enum CASCADE;
CREATE TYPE turn_status_enum AS ENUM ('CREATED', 'PLANNING', 'EXECUTING', 'SUMMARIZING', 'COMPLETED', 'FAILED', 'CANCELLED');
CREATE TYPE message_role_enum AS ENUM ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL');

CREATE TABLE IF NOT EXISTS session_turns (
    id                        BIGSERIAL PRIMARY KEY,
    session_id                BIGINT NOT NULL, -- 逻辑关联: agent_sessions.id
    plan_id                   BIGINT,          -- 逻辑关联: agent_plans.id（创建后回填）
    user_message              TEXT NOT NULL,
    status                    turn_status_enum NOT NULL DEFAULT 'CREATED',
    final_response_message_id BIGINT,
    assistant_summary         TEXT,
    metadata                  JSONB DEFAULT '{}'::jsonb,
    created_at                TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at              TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_session_turns_session ON session_turns(session_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_session_turns_status ON session_turns(status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_session_turns_plan_not_null ON session_turns(plan_id) WHERE plan_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS session_messages (
    id                        BIGSERIAL PRIMARY KEY,
    session_id                BIGINT NOT NULL, -- 逻辑关联: agent_sessions.id
    turn_id                   BIGINT NOT NULL, -- 逻辑关联: session_turns.id
    role                      message_role_enum NOT NULL,
    content                   TEXT NOT NULL,
    metadata                  JSONB DEFAULT '{}'::jsonb,
    created_at                TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_session_messages_session ON session_messages(session_id, id);
CREATE INDEX IF NOT EXISTS idx_session_messages_turn ON session_messages(turn_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_session_messages_turn_assistant ON session_messages(turn_id, role) WHERE role = 'ASSISTANT';

COMMENT ON TABLE session_turns IS '会话回合表：记录一次用户提问到最终回复的完整生命周期';
COMMENT ON TABLE session_messages IS '会话消息表：记录用户/助手/工具消息，用于对话时间线展示';

-- =====================================================
-- 4. Workflow 定义/草案/路由决策
-- =====================================================
DROP TYPE IF EXISTS workflow_definition_status_enum CASCADE;
DROP TYPE IF EXISTS workflow_draft_status_enum CASCADE;
DROP TYPE IF EXISTS routing_decision_type_enum CASCADE;
CREATE TYPE workflow_definition_status_enum AS ENUM ('ACTIVE', 'DISABLED', 'ARCHIVED');
CREATE TYPE workflow_draft_status_enum AS ENUM ('DRAFT', 'REVIEWING', 'PUBLISHED', 'ARCHIVED');
CREATE TYPE routing_decision_type_enum AS ENUM ('HIT_PRODUCTION', 'CANDIDATE', 'FALLBACK');

CREATE TABLE IF NOT EXISTS workflow_definitions (
    id                          BIGSERIAL PRIMARY KEY,
    definition_key              VARCHAR(128) NOT NULL,
    tenant_id                   VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',
    category                    VARCHAR(50) NOT NULL,
    name                        VARCHAR(200) NOT NULL,
    version                     INTEGER NOT NULL DEFAULT 1,
    route_description           TEXT NOT NULL,
    graph_definition            JSONB NOT NULL,
    input_schema                JSONB DEFAULT '{}'::jsonb,
    default_config              JSONB DEFAULT '{}'::jsonb,
    tool_policy                 JSONB DEFAULT '{}'::jsonb,
    input_schema_version        VARCHAR(32) DEFAULT 'v1',
    constraints_json            JSONB DEFAULT '{}'::jsonb,
    node_signature              VARCHAR(512),
    status                      workflow_definition_status_enum NOT NULL DEFAULT 'ACTIVE',
    published_from_draft_id     BIGINT,
    is_active                   BOOLEAN DEFAULT TRUE,
    created_by                  VARCHAR(100) DEFAULT 'SYSTEM',
    approved_by                 VARCHAR(100),
    approved_at                 TIMESTAMP WITH TIME ZONE,
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_workflow_definition_identity UNIQUE (tenant_id, definition_key, version)
);

CREATE INDEX IF NOT EXISTS idx_workflow_definition_route
    ON workflow_definitions(tenant_id, status, category, name, version DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_definition_graph_gin
    ON workflow_definitions USING GIN (graph_definition);

COMMENT ON TABLE workflow_definitions IS 'Workflow Definition：生产流程定义，版本不可变';
COMMENT ON COLUMN workflow_definitions.route_description IS '路由匹配描述文本（仅路由用）';

CREATE TABLE IF NOT EXISTS workflow_drafts (
    id                          BIGSERIAL PRIMARY KEY,
    draft_key                   VARCHAR(128) NOT NULL,
    tenant_id                   VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',
    category                    VARCHAR(50) NOT NULL,
    name                        VARCHAR(200) NOT NULL,
    route_description           TEXT NOT NULL,
    graph_definition            JSONB NOT NULL,
    input_schema                JSONB DEFAULT '{}'::jsonb,
    default_config              JSONB DEFAULT '{}'::jsonb,
    tool_policy                 JSONB DEFAULT '{}'::jsonb,
    input_schema_version        VARCHAR(32) DEFAULT 'v1',
    constraints_json            JSONB DEFAULT '{}'::jsonb,
    node_signature              VARCHAR(512),
    dedup_hash                  VARCHAR(128),
    source_type                 VARCHAR(32),
    source_definition_id        BIGINT,
    status                      workflow_draft_status_enum NOT NULL DEFAULT 'DRAFT',
    created_by                  VARCHAR(100) DEFAULT 'SYSTEM',
    approved_by                 VARCHAR(100),
    approved_at                 TIMESTAMP WITH TIME ZONE,
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_draft_status
    ON workflow_drafts(tenant_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_draft_dedup
    ON workflow_drafts(tenant_id, dedup_hash, status);
CREATE INDEX IF NOT EXISTS idx_workflow_draft_graph_gin
    ON workflow_drafts USING GIN (graph_definition);

COMMENT ON TABLE workflow_drafts IS 'Workflow Draft：运行时候选草案与治理对象';

CREATE TABLE IF NOT EXISTS routing_decisions (
    id                          BIGSERIAL PRIMARY KEY,
    session_id                  BIGINT NOT NULL,
    turn_id                     BIGINT,
    decision_type               routing_decision_type_enum NOT NULL,
    strategy                    VARCHAR(64),
    score                       NUMERIC(8, 4),
    reason                      TEXT,
    definition_id               BIGINT,
    definition_key              VARCHAR(128),
    definition_version          INTEGER,
    draft_id                    BIGINT,
    draft_key                   VARCHAR(128),
    source_type                 VARCHAR(32),
    fallback_flag               BOOLEAN DEFAULT FALSE,
    fallback_reason             VARCHAR(128),
    planner_attempts            INTEGER DEFAULT 0,
    metadata                    JSONB DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_routing_decision_session_turn
    ON routing_decisions(session_id, turn_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_routing_decision_decision_type
    ON routing_decisions(decision_type, created_at DESC);

COMMENT ON TABLE routing_decisions IS '路由决策审计表：记录命中/兜底/候选决策';

-- =====================================================
-- 5. Agent 计划表
-- =====================================================
DROP TYPE IF EXISTS plan_status_enum CASCADE;
CREATE TYPE plan_status_enum AS ENUM ('PLANNING', 'READY', 'RUNNING', 'PAUSED', 'COMPLETED', 'FAILED', 'CANCELLED');

CREATE TABLE IF NOT EXISTS agent_plans (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          BIGINT NOT NULL, -- 逻辑关联: agent_sessions.id
    route_decision_id   BIGINT NOT NULL,
    workflow_definition_id BIGINT,
    workflow_draft_id   BIGINT,

    plan_goal           TEXT NOT NULL,
    execution_graph     JSONB NOT NULL,  -- 运行时图谱副本
    definition_snapshot JSONB NOT NULL,  -- 定义/草案审计快照（非执行事实）
    global_context      JSONB DEFAULT '{}'::jsonb, -- 黑板

    status              plan_status_enum NOT NULL DEFAULT 'PLANNING',
    priority            INTEGER DEFAULT 0,

    error_summary       TEXT,
    version             INTEGER NOT NULL DEFAULT 0, -- 乐观锁

    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE agent_plans DROP CONSTRAINT IF EXISTS fk_agent_plans_route_decision;
ALTER TABLE agent_plans
    ADD CONSTRAINT fk_agent_plans_route_decision
    FOREIGN KEY (route_decision_id) REFERENCES routing_decisions(id);

CREATE INDEX IF NOT EXISTS idx_plans_session_id ON agent_plans(session_id);
CREATE INDEX IF NOT EXISTS idx_plans_status_priority ON agent_plans(status, priority DESC);
CREATE INDEX IF NOT EXISTS idx_plans_route_decision_id ON agent_plans(route_decision_id);
CREATE INDEX IF NOT EXISTS idx_plans_workflow_definition_id ON agent_plans(workflow_definition_id);
CREATE INDEX IF NOT EXISTS idx_plans_workflow_draft_id ON agent_plans(workflow_draft_id);

COMMENT ON TABLE agent_plans IS 'Agent 执行计划表：存储任务执行计划和状态';

-- =====================================================
-- 6. Agent 任务表
-- =====================================================
DROP TYPE IF EXISTS task_type_enum CASCADE;
DROP TYPE IF EXISTS task_status_enum CASCADE;
CREATE TYPE task_type_enum AS ENUM ('WORKER', 'CRITIC');
CREATE TYPE task_status_enum AS ENUM ('PENDING', 'READY', 'RUNNING', 'VALIDATING', 'REFINING', 'COMPLETED', 'FAILED', 'SKIPPED');

CREATE TABLE IF NOT EXISTS agent_tasks (
    id                  BIGSERIAL PRIMARY KEY,
    plan_id             BIGINT NOT NULL, -- 逻辑关联: agent_plans.id

    node_id             VARCHAR(50) NOT NULL, -- 图谱中的节点ID
    name                VARCHAR(200),
    task_type           task_type_enum NOT NULL,

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
-- 7. 任务执行记录表
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
    error_type          VARCHAR(64),

    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_executions_task_id ON task_executions(task_id);
CREATE INDEX IF NOT EXISTS idx_executions_lookup ON task_executions(task_id, attempt_number DESC);

COMMENT ON TABLE task_executions IS '任务执行记录表：存储每次执行的详细历史';


-- =====================================================
-- 8. 任务分享链接表
-- =====================================================
CREATE TABLE IF NOT EXISTS task_share_links (
    id                  BIGSERIAL PRIMARY KEY,
    task_id             BIGINT NOT NULL, -- 逻辑关联: agent_tasks.id

    share_code          VARCHAR(64) NOT NULL,
    token_hash          VARCHAR(128) NOT NULL,
    scope               VARCHAR(32) NOT NULL DEFAULT 'RESULT_AND_REFERENCES',
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,

    revoked             BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at          TIMESTAMP WITH TIME ZONE,
    revoked_reason      VARCHAR(128),

    created_by          VARCHAR(64),
    version             INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_task_share_links_token_hash ON task_share_links(token_hash);
CREATE UNIQUE INDEX IF NOT EXISTS uq_task_share_links_task_code ON task_share_links(task_id, share_code);
CREATE INDEX IF NOT EXISTS idx_task_share_links_task_created ON task_share_links(task_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_share_links_revoked_expire ON task_share_links(revoked, expires_at);

COMMENT ON TABLE task_share_links IS '任务分享链接表：存储可审计、可吊销的任务分享令牌元数据';

-- =====================================================
-- 9. Plan/Task 事件表
-- =====================================================
DROP TYPE IF EXISTS plan_task_event_type_enum CASCADE;
CREATE TYPE plan_task_event_type_enum AS ENUM ('TASK_STARTED', 'TASK_COMPLETED', 'TASK_LOG', 'PLAN_FINISHED');

CREATE TABLE IF NOT EXISTS plan_task_events (
    id                  BIGSERIAL PRIMARY KEY,
    plan_id             BIGINT NOT NULL,
    task_id             BIGINT,
    event_type          plan_task_event_type_enum NOT NULL,
    event_data          JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plan_task_events_plan_id_id ON plan_task_events(plan_id, id);
CREATE INDEX IF NOT EXISTS idx_plan_task_events_created_at ON plan_task_events(created_at);

COMMENT ON TABLE plan_task_events IS 'Plan/Task 事件流表：用于 SSE 增量分发与审计';

-- =====================================================
-- 9. Agent 工具目录表
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
-- 10. Agent 工具关联表
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
-- 11. 向量存储注册表
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

-- 插入/更新默认 Assistant Agent（执行阶段使用，复用 Root 的模型配置）
INSERT INTO agent_registry (
    key, name, model_provider, model_name, model_options, base_system_prompt, advisor_config, is_active
)
VALUES (
    'assistant',
    '通用助手',
    'openai',
    'doubao-seed-1-8-251228',
    '{"temperature": 0.1}'::jsonb,
    '你是一个可靠的通用任务执行助手，请直接、准确地完成用户任务。',
    '{}'::jsonb,
    true
)
ON CONFLICT (key) DO UPDATE SET
    name = EXCLUDED.name,
    model_provider = EXCLUDED.model_provider,
    model_name = EXCLUDED.model_name,
    model_options = EXCLUDED.model_options,
    base_system_prompt = EXCLUDED.base_system_prompt,
    advisor_config = EXCLUDED.advisor_config,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;

-- 插入/更新默认 Root Agent（未命中生产 Definition 时用于规划 Draft 草案）
INSERT INTO agent_registry (
    key, name, model_provider, model_name, model_options, base_system_prompt, advisor_config, is_active
)
VALUES (
    'root',
    'Root 规划器',
    'openai',
    'doubao-seed-1-8-251228',
    '{"temperature": 0.1}'::jsonb,
    '你是系统级Workflow规划器。你的职责是将用户请求拆解为可执行Draft草案。你必须仅输出严格JSON，不允许Markdown代码块。JSON必须包含字段：category,name,routeDescription,graphDefinition,inputSchema,defaultConfig,toolPolicy,constraints,inputSchemaVersion,nodeSignature；graphDefinition.nodes每个节点至少包含id,name,type,config，type仅允许WORKER或CRITIC；若无法判断复杂流程，至少输出一个可执行WORKER节点。',
    '{}'::jsonb,
    true
)
ON CONFLICT (key) DO UPDATE SET
    name = EXCLUDED.name,
    model_provider = EXCLUDED.model_provider,
    model_name = EXCLUDED.model_name,
    model_options = EXCLUDED.model_options,
    base_system_prompt = EXCLUDED.base_system_prompt,
    advisor_config = EXCLUDED.advisor_config,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;

-- 插入默认 Workflow Definition 示例（可选）
INSERT INTO workflow_definitions (
    definition_key, tenant_id, category, name, version, route_description,
    graph_definition, default_config, tool_policy, status, is_active
)
VALUES (
    'java-crud-generator',
    'DEFAULT',
    'CODE_GENERATION',
    'java_crud_generator',
    1,
    '生成 Java CRUD 代码，包括 Entity、Mapper、Service、Controller',
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
    '{"max_retries": 3, "timeout": 300}'::jsonb,
    '{"mode":"standard","allowWriteTools":true}'::jsonb,
    'ACTIVE'::workflow_definition_status_enum,
    true
) ON CONFLICT (tenant_id, definition_key, version) DO NOTHING;

-- =====================================================
-- 结束
-- =====================================================
