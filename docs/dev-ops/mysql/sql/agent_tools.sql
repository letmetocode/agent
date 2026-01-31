CREATE TABLE agent_tool_relations (
                                      agent_id            BIGINT NOT NULL, -- 逻辑关联: agent_registry.id
                                      tool_id             BIGINT NOT NULL, -- 逻辑关联: agent_tool_catalog.id

                                      created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

                                      PRIMARY KEY (agent_id, tool_id)
);
-- 手动为非首列创建索引，优化 JOIN 性能
CREATE INDEX idx_tool_rel_tool_id ON agent_tool_relations(tool_id);