-- 执行与终态收敛 3.2 增量迁移脚本
-- 目标：
-- 1) 强制收敛同一 turn 的 assistant 最终消息为 1 条
-- 2) 回填 session_turns.final_response_message_id 指向保留消息
-- 3) 补齐唯一索引 uq_session_messages_turn_assistant

BEGIN;

-- Step 1: 统一回填 final_response_message_id 到每个 turn 的最新 assistant 消息
WITH keeper AS (
    SELECT turn_id, MAX(id) AS keep_message_id
    FROM session_messages
    WHERE role = 'ASSISTANT'::message_role_enum
    GROUP BY turn_id
)
UPDATE session_turns st
SET final_response_message_id = keeper.keep_message_id,
    updated_at = CURRENT_TIMESTAMP
FROM keeper
WHERE st.id = keeper.turn_id
  AND (st.final_response_message_id IS NULL OR st.final_response_message_id <> keeper.keep_message_id);

-- Step 2: 删除重复 assistant 消息（每个 turn 仅保留 id 最大的一条）
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY turn_id ORDER BY id DESC) AS rn
    FROM session_messages
    WHERE role = 'ASSISTANT'::message_role_enum
)
DELETE FROM session_messages sm
USING ranked r
WHERE sm.id = r.id
  AND r.rn > 1;

-- Step 3: 补齐唯一索引，保障后续并发 finalize 幂等
CREATE UNIQUE INDEX IF NOT EXISTS uq_session_messages_turn_assistant
    ON session_messages(turn_id, role)
    WHERE role = 'ASSISTANT';

COMMIT;

-- 校验 SQL：
-- 1) 不应存在重复 assistant 最终消息
-- SELECT turn_id, COUNT(*) AS cnt
-- FROM session_messages
-- WHERE role = 'ASSISTANT'::message_role_enum
-- GROUP BY turn_id
-- HAVING COUNT(*) > 1;
--
-- 2) final_response_message_id 与 assistant 消息可对应
-- SELECT st.id AS turn_id, st.final_response_message_id
-- FROM session_turns st
-- WHERE st.final_response_message_id IS NOT NULL
--   AND NOT EXISTS (
--     SELECT 1
--     FROM session_messages sm
--     WHERE sm.id = st.final_response_message_id
--       AND sm.turn_id = st.id
--       AND sm.role = 'ASSISTANT'::message_role_enum
-- );
