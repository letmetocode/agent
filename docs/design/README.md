# 开发文档索引

本目录已切换为按功能划分的开发文档。  
每份文档优先说明功能目标、流程图、时序图与具体实现定位，便于开发阶段快速落地。

## 文档清单

- `00-architecture-overview.md`：系统总览与跨功能协作
- `01-session-and-chat.md`：会话创建与聊天触发规划
- `02-plan-generation.md`：Workflow 路由与 Plan/Task 生成
- `03-task-scheduling.md`：任务依赖调度与 READY 推进
- `04-task-claim-and-execution.md`：Task claim、执行、续约与回写
- `05-plan-status-aggregation.md`：Plan 状态闭环推进
- `06-agent-factory-and-tools.md`：AgentProfile 工厂、Advisor 与 MCP 工具链
- `07-data-model-and-sql.md`：数据模型、关键 SQL 与并发约束
- `08-observability-and-ops.md`：观测指标、审计与运维治理
- `09-dev-guide-and-checklist.md`：开发流程、变更模板与回归清单

## 阅读顺序建议

1. `00-architecture-overview.md`
2. `01-session-and-chat.md`
3. `02-plan-generation.md`
4. `03-task-scheduling.md`
5. `04-task-claim-and-execution.md`
6. `05-plan-status-aggregation.md`
7. `06-agent-factory-and-tools.md`
8. `07-data-model-and-sql.md`
9. `08-observability-and-ops.md`
10. `09-dev-guide-and-checklist.md`

## 维护约定

- 文档描述以当前主干代码为准，代码变更同步更新对应功能文档。
- 术语统一：`Plan` 表示总任务、`Task` 表示节点任务、`AgentProfile` 表示执行配置、`TaskClient` 表示节点执行客户端（底层 `ChatClient`）。
- 每个功能文档至少保留一张流程图和一张时序图。
- 图中节点使用简洁标识，避免复杂标点导致 Mermaid 解析失败。
- 开发阶段优先记录长期可维护方案，不保留临时补丁路径。
- 涉及数据库字段或事件流变更时，需同步更新最终版 SQL：`docs/dev-ops/postgresql/sql/01_init_database.sql`。
## 补充规范

- Git 管理与提交流程：`../dev-ops/git-management.md`

