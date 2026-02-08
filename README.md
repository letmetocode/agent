# Agent

面向任务编排与执行的 Agent 工程，采用 DDD 分层多模块结构，技术基线为 Spring Boot 3.4.3 + Spring AI 1.1.2。

## 开发阶段原则

项目当前处于开发阶段，优先保证功能完备与系统健康。  
修改与新增以长期可维护方案为先，避免临时补丁与潜在隐患。

## 模块结构

- `agent-app`：启动与全局配置装配（Spring、调度、MyBatis 资源）
- `agent-trigger`：HTTP/SSE 与守护任务入口（调度、执行、状态推进）
- `agent-domain`：领域实体、状态机、仓储端口
- `agent-infrastructure`：DAO/Mapper、仓储实现、Planner/AI/MCP 适配
- `agent-api`：对外 DTO 与统一响应
- `agent-types`：通用枚举、异常、常量

## 文档导航

- 开发设计文档：`docs/design/README.md`
- 运维与 SQL 文档：`docs/dev-ops/`
- 功能级详细设计：
  - `docs/design/00-architecture-overview.md`
  - `docs/design/01-session-and-chat.md`
  - `docs/design/02-plan-generation.md`
  - `docs/design/03-task-scheduling.md`
  - `docs/design/04-task-claim-and-execution.md`
  - `docs/design/05-plan-status-aggregation.md`
  - `docs/design/06-agent-factory-and-tools.md`
  - `docs/design/07-data-model-and-sql.md`
  - `docs/design/08-observability-and-ops.md`
  - `docs/design/09-dev-guide-and-checklist.md`

## 常用命令

- 构建：`mvn clean package`
- 启动：`mvn -pl agent-app -am spring-boot:run`
- 单测：`mvn -pl agent-app -am -DskipTests=false test`
