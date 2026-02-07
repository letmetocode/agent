# Repository Guidelines

## 沟通语言
本仓库协作与讨论统一使用中文，便于团队沟通与文档一致性。

## 开发阶段原则
项目当前处于开发阶段，优先保证功能完备与系统健康。
所有修改与新增应以长期可维护的最优方案为先，避免临时性补丁与潜在隐患。

## 项目结构与模块组织
根目录 `pom.xml` 是多模块 Maven 聚合工程（DDD 分层）。主要模块如下：
- `agent-app/`：Spring Boot 启动模块，资源在 `agent-app/src/main/resources`。
- `agent-api/`：对外 DTO 与响应类型。
- `agent-domain/`：领域实体、聚合、仓储接口。
- `agent-infrastructure/`：DAO、MyBatis 仓储实现与适配器。
- `agent-trigger/`：入口适配层（job/http/listener）。
- `agent-types/`：通用枚举/常量/异常。
其他常用路径：`docs/dev-ops/`（运维资料）、`data/log/`（本地日志）。

## 构建、测试与开发命令
- `mvn clean package`：构建全模块（`agent-app` 测试默认跳过）。
- `mvn -pl agent-app -am spring-boot:run`：启动应用。
- `mvn -pl agent-app -am -DskipTests=false test`：运行单测。
- `mvn -Ptest` / `mvn -Pprod`：切换 Maven profile（默认 dev）。

## 编码风格与命名约定
- Java 17，4 空格缩进，大括号同行。
- 包前缀 `com.getoffer.*`，模块名以 `agent-*` 开头。
- 类名 `PascalCase`，方法名 `camelCase`，常量 `UPPER_SNAKE_CASE`。
- MyBatis Mapper 放在 `agent-app/src/main/resources/mybatis/mapper`，文件名与 DAO 对应（如 `AgentTaskDao` ↔ `AgentTaskMapper.xml`）。
- 未配置自动格式化/静态检查工具，请保持现有风格。

## 测试指南
- 使用 JUnit 4 + Spring Boot Test。
- 测试位于 `agent-app/src/test/java`，命名需匹配 `**/*Test.java`。
- 测试默认跳过，需显式 `-DskipTests=false` 才会执行。

## 提交与 PR 规范
- 提交信息建议采用类型前缀，当前历史常见示例：`feature：init`、`feature：Agent 工厂与核心配置`。
- PR 需写明目的、关键改动、测试命令/结果；如涉及配置或数据库变更，请注明对应 `application-*.yml` profile 与 SQL 位置。

## 配置与安全提示
- 环境配置在 `agent-app/src/main/resources/application-*.yml`。
- 不要提交密钥；使用环境变量或本地覆盖配置。
- 数据库结构与 SQL 参考 `docs/dev-ops/**/sql`。
