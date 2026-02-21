-- 3.5 session/turn 幂等与执行记录去重约束

ALTER TABLE session_turns
    ADD COLUMN IF NOT EXISTS client_message_id VARCHAR(128);

-- 回填历史 metadata 中的 clientMessageId 到显式列，空串统一归一为 NULL
UPDATE session_turns
SET client_message_id = NULLIF(TRIM(metadata ->> 'clientMessageId'), '')
WHERE client_message_id IS NULL
  AND metadata IS NOT NULL
  AND metadata ? 'clientMessageId';

-- 处理历史重复幂等键：保留最新回合，其余清空幂等键避免唯一索引创建失败
WITH duplicated AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY session_id, client_message_id ORDER BY id DESC) AS rn
    FROM session_turns
    WHERE client_message_id IS NOT NULL
)
UPDATE session_turns st
SET client_message_id = NULL
FROM duplicated d
WHERE st.id = d.id
  AND d.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_session_turns_session_client_message
    ON session_turns(session_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

-- 处理历史重复 attempt：保留最新 execution 记录，其余删除后再建立唯一约束
WITH duplicated_exec AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY task_id, attempt_number ORDER BY id DESC) AS rn
    FROM task_executions
)
DELETE FROM task_executions te
USING duplicated_exec d
WHERE te.id = d.id
  AND d.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_task_executions_task_attempt
    ON task_executions(task_id, attempt_number);
