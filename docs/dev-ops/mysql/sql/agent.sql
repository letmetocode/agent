CREATE TABLE agent_registry (
                                id                  BIGSERIAL PRIMARY KEY,
                                key                 VARCHAR(100) NOT NULL UNIQUE, -- 业务唯一标识 (如 'java_coder')
                                name                VARCHAR(100) NOT NULL,

    -- 模型配置
                                model_provider      VARCHAR(50) NOT NULL DEFAULT 'openai',
                                model_name          VARCHAR(100) NOT NULL,
                                model_options       JSONB DEFAULT '{"temperature": 0.7}'::jsonb,

    -- 基础人设 (System Prompt)
                                base_system_prompt  TEXT,

    -- [Spring AI 1.1.2 核心] Advisors 配置
    -- 示例: {"memory": {"enabled": true}, "rag": {"enabled": true}}
                                advisor_config      JSONB DEFAULT '{}'::jsonb,

                                is_active           BOOLEAN DEFAULT TRUE,
                                created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);