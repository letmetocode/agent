# Repository Guidelines

## 项目结构与模块组织
- 根目录 `pom.xml` 是多模块 Maven 聚合工程（DDD 分层）。
- `agent-app/` 为 Spring Boot 启动模块；配置在 `src/main/resources`（`application-*.yml`、logback、MyBatis）。
- `agent-api/` 提供对外 DTO/响应类型。
- `agent-domain/` 放置领域实体、聚合与仓储接口。
- `agent-infrastructure/` 实现 DAO、MyBatis 仓储与适配器。
- `agent-trigger/` 为入口适配层（job/http/listener）。
- `agent-types/` 存放通用枚举/常量/异常。
- `docs/dev-ops/` 为运维资料；`data/log/` 为本地日志输出。

## 构建、测试与开发命令
- `mvn clean package` 构建全模块（`agent-app` 测试默认跳过）。
- `mvn -pl agent-app -am spring-boot:run` 启动应用。
- `mvn -pl agent-app -am -DskipTests=false test` 执行单测。
- `mvn -Ptest` / `mvn -Pprod` 切换 Maven profile（默认 dev）。

## 编码风格与命名约定
- Java 17；4 空格缩进；大括号同行。
- 包前缀 `com.getoffer.*`；模块名以 `agent-*` 开头。
- 类名 `PascalCase`，方法名 `camelCase`，常量 `UPPER_SNAKE_CASE`。
- MyBatis Mapper 放在 `agent-app/src/main/resources/mybatis/mapper`，文件名与 DAO 对应（如 `AgentTaskDao` ↔ `AgentTaskMapper.xml`）。
- 未配置格式化/静态检查工具，请保持现有风格。

## 测试指南
- 使用 JUnit 4 + Spring Boot Test，测试位于 `agent-app/src/test/java`。
- Surefire 包含 `**/*Test.java`，测试类需符合该命名。
- 测试默认跳过；本地或 CI 运行时请显式开启 `-DskipTests=false`。

## 提交与 PR 规范
- 提交信息为短标题+类型前缀（示例：`feature：init`），建议沿用 `type: message` 风格。
- PR 需写明目的、关键改动、测试命令/结果，以及涉及的配置或数据库影响（注明 `application-*.yml` profile）。

## 配置与安全提示
- 环境配置在 `agent-app/src/main/resources/application-*.yml`。
- 不要提交密钥；使用环境变量或本地覆盖配置。
- 数据库结构与 SQL 参考见 `docs/dev-ops/**/sql`。
