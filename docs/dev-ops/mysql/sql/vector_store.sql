CREATE TABLE vector_store_registry (
                                       id                  BIGSERIAL PRIMARY KEY,
                                       name                VARCHAR(100) NOT NULL,

    -- Spring Bean Name (用于工厂加载)
                                       bean_name           VARCHAR(100) NOT NULL UNIQUE,
                                       description         TEXT,

    -- 默认检索参数
                                       default_search_args JSONB DEFAULT '{"top_k": 4, "threshold": 0.7}'::jsonb,

                                       created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);