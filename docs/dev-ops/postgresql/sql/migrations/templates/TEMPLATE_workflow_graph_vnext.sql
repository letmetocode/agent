-- TEMPLATE: Workflow Graph v2 -> vNext 迁移脚本
-- 使用方式：
-- 1) 复制为正式文件：VYYYYMMDD_NN_workflow_graph_vnext.sql
-- 2) 替换 params 中 run_id / target_version
-- 3) 预发验证通过后再用于生产

BEGIN;

-- Step 0: 参数（必须替换）
WITH params AS (
    SELECT 'replace-with-run-id'::VARCHAR(64) AS run_id,
           3::INTEGER AS target_version
)
SELECT run_id, target_version FROM params;

-- Step 1: 审计表（用于回滚）
CREATE TABLE IF NOT EXISTS workflow_graph_version_migration_audit (
    id                          BIGSERIAL PRIMARY KEY,
    run_id                      VARCHAR(64) NOT NULL,
    entity_type                 VARCHAR(32) NOT NULL,   -- DRAFT / DEFINITION
    entity_id                   BIGINT NOT NULL,
    before_graph                JSONB NOT NULL,
    after_graph                 JSONB,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_graph_migration_audit_run
    ON workflow_graph_version_migration_audit(run_id, entity_type, entity_id);

-- Step 2: 备份 workflow_drafts（当前 v2）
WITH params AS (
    SELECT 'replace-with-run-id'::VARCHAR(64) AS run_id
)
INSERT INTO workflow_graph_version_migration_audit (run_id, entity_type, entity_id, before_graph)
SELECT p.run_id,
       'DRAFT',
       d.id,
       d.graph_definition
FROM workflow_drafts d
CROSS JOIN params p
WHERE
    CASE
        WHEN d.graph_definition ? 'version'
             AND (d.graph_definition ->> 'version') ~ '^[0-9]+$'
            THEN (d.graph_definition ->> 'version')::INTEGER
        ELSE 2
    END = 2
  AND NOT EXISTS (
      SELECT 1
      FROM workflow_graph_version_migration_audit a
      WHERE a.run_id = p.run_id
        AND a.entity_type = 'DRAFT'
        AND a.entity_id = d.id
  );

-- Step 3: 备份 workflow_definitions（当前 v2）
WITH params AS (
    SELECT 'replace-with-run-id'::VARCHAR(64) AS run_id
)
INSERT INTO workflow_graph_version_migration_audit (run_id, entity_type, entity_id, before_graph)
SELECT p.run_id,
       'DEFINITION',
       f.id,
       f.graph_definition
FROM workflow_definitions f
CROSS JOIN params p
WHERE
    CASE
        WHEN f.graph_definition ? 'version'
             AND (f.graph_definition ->> 'version') ~ '^[0-9]+$'
            THEN (f.graph_definition ->> 'version')::INTEGER
        ELSE 2
    END = 2
  AND NOT EXISTS (
      SELECT 1
      FROM workflow_graph_version_migration_audit a
      WHERE a.run_id = p.run_id
        AND a.entity_type = 'DEFINITION'
        AND a.entity_id = f.id
  );

-- Step 4: 更新 workflow_drafts 到 target_version
WITH params AS (
    SELECT 'replace-with-run-id'::VARCHAR(64) AS run_id,
           3::INTEGER AS target_version
)
UPDATE workflow_drafts d
SET graph_definition = jsonb_set(COALESCE(d.graph_definition, '{}'::JSONB),
                                 '{version}',
                                 to_jsonb(p.target_version),
                                 true),
    updated_at = CURRENT_TIMESTAMP
FROM params p
WHERE d.id IN (
    SELECT a.entity_id
    FROM workflow_graph_version_migration_audit a
    WHERE a.run_id = p.run_id
      AND a.entity_type = 'DRAFT'
);

-- Step 5: 更新 workflow_definitions 到 target_version
WITH params AS (
    SELECT 'replace-with-run-id'::VARCHAR(64) AS run_id,
           3::INTEGER AS target_version
)
UPDATE workflow_definitions f
SET graph_definition = jsonb_set(COALESCE(f.graph_definition, '{}'::JSONB),
                                 '{version}',
                                 to_jsonb(p.target_version),
                                 true),
    updated_at = CURRENT_TIMESTAMP
FROM params p
WHERE f.id IN (
    SELECT a.entity_id
    FROM workflow_graph_version_migration_audit a
    WHERE a.run_id = p.run_id
      AND a.entity_type = 'DEFINITION'
);

-- Step 6: 回填 after_graph（用于回滚核对）
WITH params AS (
    SELECT 'replace-with-run-id'::VARCHAR(64) AS run_id
)
UPDATE workflow_graph_version_migration_audit a
SET after_graph = d.graph_definition
FROM workflow_drafts d, params p
WHERE a.run_id = p.run_id
  AND a.entity_type = 'DRAFT'
  AND a.entity_id = d.id;

WITH params AS (
    SELECT 'replace-with-run-id'::VARCHAR(64) AS run_id
)
UPDATE workflow_graph_version_migration_audit a
SET after_graph = f.graph_definition
FROM workflow_definitions f, params p
WHERE a.run_id = p.run_id
  AND a.entity_type = 'DEFINITION'
  AND a.entity_id = f.id;

COMMIT;

-- 校验 SQL（执行后手动检查）：
-- 1) 迁移审计覆盖数
-- SELECT entity_type, COUNT(*) FROM workflow_graph_version_migration_audit
-- WHERE run_id = 'replace-with-run-id'
-- GROUP BY entity_type;
--
-- 2) 版本分布（应包含 target_version）
-- SELECT graph_definition ->> 'version' AS version, COUNT(*)
-- FROM workflow_drafts
-- GROUP BY graph_definition ->> 'version'
-- ORDER BY version;
--
-- SELECT graph_definition ->> 'version' AS version, COUNT(*)
-- FROM workflow_definitions
-- GROUP BY graph_definition ->> 'version'
-- ORDER BY version;
