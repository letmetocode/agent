# Agent 项目

基于 Spring Boot 3 与 DDD 分层的多模块 Agent 系统。

## 项目结构

- `agent-app`：Spring Boot 启动与应用配置。
- `agent-api`：对外 DTO 与响应对象。
- `agent-domain`：领域模型、聚合与仓储接口。
- `agent-infrastructure`：DAO、MyBatis 仓储实现与外部集成。
- `agent-trigger`：触发器层（HTTP/任务调度/监听）。
- `agent-types`：公共枚举、常量与异常。
- `docs/dev-ops`：部署、数据库初始化与运维脚本。

## 本地开发

```bash
# 全模块构建
mvn clean package

# 启动应用
mvn -pl agent-app -am spring-boot:run

# 运行测试（默认测试跳过，需要显式打开）
mvn -pl agent-app -am -DskipTests=false test
```

## 配置说明

- 环境配置位于：`agent-app/src/main/resources/application-*.yml`
- 默认 profile：`dev`
- 数据库初始化 SQL：`docs/dev-ops/mysql/sql` 与 `docs/dev-ops/postgresql/sql`

## 注意事项

- 请勿提交密钥、密码等敏感信息。
- 建议通过环境变量或本地覆盖配置注入私密参数。
