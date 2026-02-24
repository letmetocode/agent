-- 3.6 Root planner token cap guard
-- 防止 root 规划在长输出场景持续触发软超时：补齐 root 的输出 token 上限。

UPDATE agent_registry
SET model_options = jsonb_set(COALESCE(model_options, '{}'::jsonb), '{maxTokens}', '768'::jsonb, true),
    updated_at = CURRENT_TIMESTAMP
WHERE key = 'root'
  AND NOT (COALESCE(model_options, '{}'::jsonb) ? 'maxTokens');

UPDATE agent_registry
SET model_options = jsonb_set(COALESCE(model_options, '{}'::jsonb), '{maxCompletionTokens}', '768'::jsonb, true),
    updated_at = CURRENT_TIMESTAMP
WHERE key = 'root'
  AND NOT (COALESCE(model_options, '{}'::jsonb) ? 'maxCompletionTokens');
