-- 定义枚举 (如环境不支持枚举，可用 VARCHAR + CHECK 替代)
DROP TYPE IF EXISTS sop_structure_enum;
CREATE TYPE sop_structure_enum AS ENUM ('CHAIN', 'DAG');

CREATE TABLE sop_templates (
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

    -- 业务主键约束 (保留唯一索引以确保逻辑正确)
                               CONSTRAINT uq_sop_identity UNIQUE (category, name, version)
);
-- 索引
CREATE INDEX idx_sop_lookup ON sop_templates(category, name, version, is_active);
CREATE INDEX idx_sop_graph_gin ON sop_templates USING GIN (graph_definition);