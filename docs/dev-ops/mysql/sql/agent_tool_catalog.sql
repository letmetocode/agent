CREATE TABLE agent_tool_catalog (
                                    id                  BIGSERIAL PRIMARY KEY,
                                    name                VARCHAR(100) NOT NULL UNIQUE, -- 工具名 (如 'web_search')
                                    type                VARCHAR(50) NOT NULL,         -- 'SPRING_BEAN' 或 'MCP_FUNCTION'

                                    description         TEXT,
                                    input_schema        JSONB,                        -- 参数定义 (JSON Schema)

    -- 配置 (存 BeanName 或 MCP 连接信息)
                                    bean_name           VARCHAR(100),
                                    mcp_server_config   JSONB,

                                    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);