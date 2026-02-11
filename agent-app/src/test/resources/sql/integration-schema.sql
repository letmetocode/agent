DROP TABLE IF EXISTS plan_task_events CASCADE;
DROP TABLE IF EXISTS task_executions CASCADE;
DROP TABLE IF EXISTS agent_tasks CASCADE;
DROP TABLE IF EXISTS agent_plans CASCADE;
DROP TABLE IF EXISTS routing_decisions CASCADE;
DROP TABLE IF EXISTS workflow_drafts CASCADE;
DROP TABLE IF EXISTS workflow_definitions CASCADE;
DROP TABLE IF EXISTS session_messages CASCADE;
DROP TABLE IF EXISTS session_turns CASCADE;
DROP TABLE IF EXISTS agent_sessions CASCADE;

DROP TYPE IF EXISTS plan_task_event_type_enum CASCADE;
DROP TYPE IF EXISTS task_status_enum CASCADE;
DROP TYPE IF EXISTS task_type_enum CASCADE;
DROP TYPE IF EXISTS plan_status_enum CASCADE;
DROP TYPE IF EXISTS routing_decision_type_enum CASCADE;
DROP TYPE IF EXISTS workflow_draft_status_enum CASCADE;
DROP TYPE IF EXISTS workflow_definition_status_enum CASCADE;
DROP TYPE IF EXISTS turn_status_enum CASCADE;
DROP TYPE IF EXISTS message_role_enum CASCADE;

CREATE TYPE turn_status_enum AS ENUM ('CREATED', 'PLANNING', 'EXECUTING', 'SUMMARIZING', 'COMPLETED', 'FAILED', 'CANCELLED');
CREATE TYPE message_role_enum AS ENUM ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL');
CREATE TYPE plan_status_enum AS ENUM ('PLANNING', 'READY', 'RUNNING', 'PAUSED', 'COMPLETED', 'FAILED', 'CANCELLED');
CREATE TYPE task_type_enum AS ENUM ('WORKER', 'CRITIC');
CREATE TYPE task_status_enum AS ENUM ('PENDING', 'READY', 'RUNNING', 'VALIDATING', 'REFINING', 'COMPLETED', 'FAILED', 'SKIPPED');
CREATE TYPE plan_task_event_type_enum AS ENUM ('TASK_STARTED', 'TASK_COMPLETED', 'TASK_LOG', 'PLAN_FINISHED');
CREATE TYPE workflow_definition_status_enum AS ENUM ('ACTIVE', 'DISABLED', 'ARCHIVED');
CREATE TYPE workflow_draft_status_enum AS ENUM ('DRAFT', 'REVIEWING', 'PUBLISHED', 'ARCHIVED');
CREATE TYPE routing_decision_type_enum AS ENUM ('HIT_PRODUCTION', 'CANDIDATE', 'FALLBACK');

CREATE TABLE agent_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    title VARCHAR(200),
    is_active BOOLEAN DEFAULT TRUE,
    meta_info JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workflow_definitions (
    id BIGSERIAL PRIMARY KEY,
    definition_key VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',
    category VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    route_description TEXT NOT NULL,
    status workflow_definition_status_enum NOT NULL DEFAULT 'ACTIVE',
    graph_definition JSONB NOT NULL,
    input_schema JSONB DEFAULT '{}'::jsonb,
    default_config JSONB DEFAULT '{}'::jsonb,
    tool_policy JSONB DEFAULT '{}'::jsonb,
    input_schema_version VARCHAR(32) DEFAULT 'v1',
    constraints_json JSONB DEFAULT '{}'::jsonb,
    node_signature VARCHAR(512),
    published_from_draft_id BIGINT,
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(100) DEFAULT 'SYSTEM',
    approved_by VARCHAR(100),
    approved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_definition_identity UNIQUE (tenant_id, definition_key, version)
);

CREATE TABLE workflow_drafts (
    id BIGSERIAL PRIMARY KEY,
    draft_key VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',
    category VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    route_description TEXT NOT NULL,
    graph_definition JSONB NOT NULL,
    input_schema JSONB DEFAULT '{}'::jsonb,
    default_config JSONB DEFAULT '{}'::jsonb,
    tool_policy JSONB DEFAULT '{}'::jsonb,
    input_schema_version VARCHAR(32) DEFAULT 'v1',
    constraints_json JSONB DEFAULT '{}'::jsonb,
    node_signature VARCHAR(512),
    dedup_hash VARCHAR(128),
    source_type VARCHAR(64),
    source_definition_id BIGINT,
    status workflow_draft_status_enum NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(100) DEFAULT 'SYSTEM',
    approved_by VARCHAR(100),
    approved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_draft_key UNIQUE (tenant_id, draft_key)
);

CREATE TABLE routing_decisions (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    turn_id BIGINT,
    decision_type routing_decision_type_enum NOT NULL,
    strategy VARCHAR(64),
    score NUMERIC(6,4),
    reason VARCHAR(128),
    definition_id BIGINT,
    definition_key VARCHAR(128),
    definition_version INTEGER,
    draft_id BIGINT,
    draft_key VARCHAR(128),
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agent_plans (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    route_decision_id BIGINT NOT NULL,
    workflow_definition_id BIGINT,
    workflow_draft_id BIGINT,
    plan_goal TEXT NOT NULL,
    execution_graph JSONB NOT NULL,
    definition_snapshot JSONB NOT NULL,
    global_context JSONB DEFAULT '{}'::jsonb,
    status plan_status_enum NOT NULL DEFAULT 'PLANNING',
    priority INTEGER DEFAULT 0,
    error_summary TEXT,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agent_tasks (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    node_id VARCHAR(50) NOT NULL,
    name VARCHAR(200),
    task_type task_type_enum NOT NULL,
    status task_status_enum NOT NULL DEFAULT 'PENDING',
    dependency_node_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    input_context JSONB DEFAULT '{}'::jsonb,
    config_snapshot JSONB NOT NULL,
    output_result TEXT,
    max_retries INTEGER DEFAULT 3,
    current_retry INTEGER DEFAULT 0,
    claim_owner VARCHAR(128),
    claim_at TIMESTAMP WITH TIME ZONE,
    lease_until TIMESTAMP WITH TIME ZONE,
    execution_attempt INTEGER NOT NULL DEFAULT 0,
    version INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_plan_node UNIQUE (plan_id, node_id)
);

CREATE TABLE task_executions (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    attempt_number INTEGER NOT NULL,
    prompt_snapshot TEXT,
    llm_response_raw TEXT,
    model_name VARCHAR(100),
    token_usage JSONB,
    execution_time_ms BIGINT,
    is_valid BOOLEAN,
    validation_feedback TEXT,
    error_message TEXT,
    error_type VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE session_turns (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    plan_id BIGINT,
    user_message TEXT NOT NULL,
    status turn_status_enum NOT NULL DEFAULT 'CREATED',
    final_response_message_id BIGINT,
    assistant_summary TEXT,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE session_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    turn_id BIGINT NOT NULL,
    role message_role_enum NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE plan_task_events (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    task_id BIGINT,
    event_type plan_task_event_type_enum NOT NULL,
    event_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_definition_active ON workflow_definitions(is_active);
CREATE INDEX idx_definition_match ON workflow_definitions(tenant_id, status, category, name, version DESC);
CREATE INDEX idx_draft_status ON workflow_drafts(status, created_at DESC);
CREATE INDEX idx_draft_dedup ON workflow_drafts(dedup_hash, status, created_at DESC);
CREATE INDEX idx_routing_session ON routing_decisions(session_id, created_at DESC);
CREATE INDEX idx_plans_session_id ON agent_plans(session_id);
CREATE INDEX idx_tasks_plan_id ON agent_tasks(plan_id);
CREATE INDEX idx_tasks_scheduling ON agent_tasks(plan_id, status);
CREATE INDEX idx_tasks_claim_scan ON agent_tasks(status, lease_until, plan_id, created_at);
CREATE INDEX idx_plan_task_events_plan_id_id ON plan_task_events(plan_id, id);
CREATE INDEX idx_session_turns_session ON session_turns(session_id, id DESC);
CREATE INDEX idx_session_messages_turn ON session_messages(turn_id, id);
