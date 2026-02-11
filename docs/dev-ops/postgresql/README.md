# PostgreSQL 运维说明

## 1. 目标

本目录用于维护 PostgreSQL 运行与初始化基线，确保开发/测试环境可一键落库并与当前代码行为一致。

## 2. 目录说明

- `docker-compose.yml`：本地 PostgreSQL（含 pgvector）启动编排。
- `sql/01_init_database.sql`：唯一最终版初始化脚本（DDL + 索引 + 基线数据）。

## 3. 快速启动

```bash
cd docs/dev-ops/postgresql
docker compose up -d
```

默认端口映射请以当前 `docker-compose.yml` 为准。

## 4. 初始化数据库

```bash
psql -h 127.0.0.1 -p 15432 -U postgres -d agent_db -f sql/01_init_database.sql
```

说明：
- 该脚本可用于空库初始化，也可用于基线对齐（使用 upsert 覆盖部分基线数据）。
- 涉及类型重建时，建议先在非生产环境验证执行影响。

## 5. 基线数据约束（必须存在）

`agent_registry` 至少应存在并激活以下 profile：
- `assistant`
- `root`

当前脚本内两者默认模型一致（复用 Root 模型）：
- `model_name = doubao-seed-1-8-251228`
- `model_options = {"temperature": 0.1}`

## 6. 与应用配置的对应关系

### 6.1 模型网关配置（application-dev.yml）

- `spring.ai.openai.api-key`
- `spring.ai.openai.base-url`
- `spring.ai.openai.chat.completions-path`

这些是运行时调用模型网关的连接参数；数据库中 `agent_registry.model_provider/model_name/model_options` 仅定义“使用哪个模型配置”。

### 6.2 规划兜底配置（application-dev.yml）

- `planner.root.agent-key`：Root 规划使用的 AgentProfile Key（默认 `root`）。
- `planner.root.fallback.agent-key`：候选节点缺省执行兜底 Key（当前默认 `assistant`）。

## 7. 排障建议

### 7.1 Root 草案反复失败（404/调用异常）

优先检查：
1. `application-dev.yml` 的 `api-key/base-url/completions-path` 是否正确。
2. `agent_registry` 中 `root` 是否存在且 `is_active=true`。
3. `planner.root.agent-key` 是否指向 `root`。

### 7.2 规划降级为单节点候选 Workflow Draft

出现 `Root candidate planning exhausted retries` 日志时：
- 系统仍可执行（已降级），但表示 Root 规划不可用。
- 需尽快修复 Root 调用链路，避免长期退化。

### 7.3 候选节点缺少 agentKey 报错

检查：
1. `planner.root.fallback.agent-key` 是否为可用 key（建议 `assistant`）。
2. `agent_registry` 中 `assistant` 是否存在且激活。

## 8. 文档同步要求

以下任一变更发生后，必须同步更新本 README 与相关设计文档：
- 初始化 SQL 结构/索引变更；
- `agent_registry` 基线 profile 变更；
- Root/Assistant 兜底语义变更；
- 数据库连接端口与部署方式变更。
