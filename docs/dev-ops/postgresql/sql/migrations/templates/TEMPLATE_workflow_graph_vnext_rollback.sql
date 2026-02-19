-- TEMPLATE: Workflow Graph vNext -> v2 回滚脚本
-- 使用方式：
-- 1) 复制为正式文件：VYYYYMMDD_NN_workflow_graph_vnext_rollback.sql
-- 2) 替换 params.run_id 为目标迁移批次
-- 3) 回滚后执行校验 SQL

BEGIN;

-- Step 0: 参数（必须替换）
WITH params AS (
    SELECT 'replace-with-run-id'::VARCHAR(64) AS run_id
)
SELECT run_id FROM params;

-- Step 1: 按 run_id 回滚 workflow_drafts
WITH params AS (
    SELECT 'replace-with-run-id'::VARCHAR(64) AS run_id
)
UPDATE workflow_drafts d
SET graph_definition = a.before_graph,
    updated_at = CURRENT_TIMESTAMP
FROM workflow_graph_version_migration_audit a, params p
WHERE a.run_id = p.run_id
  AND a.entity_type = 'DRAFT'
  AND a.entity_id = d.id;

-- Step 2: 按 run_id 回滚 workflow_definitions
WITH params AS (
    SELECT 'replace-with-run-id'::VARCHAR(64) AS run_id
)
UPDATE workflow_definitions f
SET graph_definition = a.before_graph,
    updated_at = CURRENT_TIMESTAMP
FROM workflow_graph_version_migration_audit a, params p
WHERE a.run_id = p.run_id
  AND a.entity_type = 'DEFINITION'
  AND a.entity_id = f.id;

COMMIT;

-- 校验 SQL（执行后手动检查）：
-- 1) before_graph 与当前图定义一致性抽样
-- SELECT a.entity_type,
--        a.entity_id,
--        CASE
--            WHEN a.entity_type = 'DRAFT' THEN (a.before_graph = d.graph_definition)
--            ELSE (a.before_graph = f.graph_definition)
--        END AS rollback_matched
-- FROM workflow_graph_version_migration_audit a
-- LEFT JOIN workflow_drafts d ON a.entity_type = 'DRAFT' AND a.entity_id = d.id
-- LEFT JOIN workflow_definitions f ON a.entity_type = 'DEFINITION' AND a.entity_id = f.id
-- WHERE a.run_id = 'replace-with-run-id'
-- LIMIT 50;
--
-- 2) 版本分布（确认已回退到预期版本）
-- SELECT graph_definition ->> 'version' AS version, COUNT(*)
-- FROM workflow_drafts
-- GROUP BY graph_definition ->> 'version'
-- ORDER BY version;
--
-- SELECT graph_definition ->> 'version' AS version, COUNT(*)
-- FROM workflow_definitions
-- GROUP BY graph_definition ->> 'version'
-- ORDER BY version;
