-- rollback for V20260225_05_root_planner_max_tokens_guard
-- 仅回滚本迁移补齐的默认 token cap；若业务已手动调整 root 配置，请勿执行该回滚。

UPDATE agent_registry
SET model_options = COALESCE(model_options, '{}'::jsonb) - 'maxTokens' - 'maxCompletionTokens',
    updated_at = CURRENT_TIMESTAMP
WHERE key = 'root'
  AND COALESCE(model_options, '{}'::jsonb) @> '{"maxTokens":768,"maxCompletionTokens":768}'::jsonb;
