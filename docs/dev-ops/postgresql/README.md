# 项目依赖配置说明

## 已完成配置

### 1. 数据库迁移：MySQL → PostgreSQL

✅ **已更新内容：**
- 根 pom.xml：将 MySQL Connector 替换为 PostgreSQL Driver 42.7.4
- agent-app/pom.xml：更新数据库驱动依赖
- 所有环境配置文件：更新数据库连接配置

### 2. Spring AI 1.1.2 集成

✅ **已添加：**
- Spring Milestone Maven 仓库
- Spring AI BOM 1.1.2 依赖管理
- 核心依赖：
  - `spring-ai-openai-spring-boot-starter`
  - `spring-ai-starter`

### 3. MyBatis 3.0.4 配置

✅ **已配置：**
- mybatis-spring-boot-starter 3.0.4
- Mapper 位置：`classpath:/mybatis/mapper/*.xml`
- 配置文件：`classpath:/mybatis/config/mybatis-config.xml`

## 依赖版本清单

| 依赖 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.4.3 | 主框架 |
| Spring AI | 1.1.2 | AI 框架 |
| MyBatis | 3.0.4 | ORM 框架 |
| PostgreSQL | 42.7.4 | 数据库驱动 |
| Java | 17 | 运行环境 |

## 兼容性检查结果

### ✅ 完全兼容
- Spring Boot 3.4.3 + Spring AI 1.1.2
- MyBatis 3.0.4 + Spring Boot 3.4.3
- PostgreSQL Driver 42.7.4 + Java 17
- 所有依赖均支持 Java 17

### ⚠️ 需要注意

1. **JJWT 版本** (0.9.1)
   - 当前版本较老，建议升级到 0.12.x 以获得更好的 Java 17 支持
   - 如需升级，需检查 API 变更

2. **Jackson 版本** (由 Spring Boot BOM 管理)
   - 随 Spring Boot 升级统一更新

3. **Dom4j 版本** (1.6.1)
   - 版本较老，建议升级到 2.x 系列

## 数据库配置

### 连接信息
```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://127.0.0.1:5432/agent_db
    username: postgres
    password: postgres
```

### 初始化脚本
位置：`docs/dev-ops/postgresql/sql/01_init_database.sql`

执行方式：
```bash
psql -U postgres -d agent_db -f 01_init_database.sql
```

## Spring AI 配置

### OpenAI 配置
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: gpt-4
          temperature: 0.7
```

### 环境变量
```bash
export OPENAI_API_KEY=your-api-key
export OPENAI_BASE_URL=https://api.openai.com  # 可选，用于自定义端点
```

## 构建命令

```bash
# 编译整个项目
mvn clean install

# 跳过测试编译
mvn clean install -DskipTests

# 使用特定环境构建
mvn clean install -P prod

# 运行应用
mvn spring-boot:run -pl agent-app

# 指定环境运行
mvn spring-boot:run -pl agent-app -Dspring-boot.run.profiles=dev
```

## 下一步建议

1. **创建 MyBatis 配置文件**
   - `agent-app/src/main/resources/mybatis/config/mybatis-config.xml`
   - `agent-app/src/main/resources/mybatis/mapper/*.xml`

2. **创建数据库实体类和 Mapper**
   - 在 `agent-domain` 中创建实体类
   - 在 `agent-infrastructure` 中创建 Mapper 接口和 XML

3. **配置 Spring AI ChatClient**
   - 创建 ChatClient Bean
   - 实现基础的对话功能

4. **实现核心业务逻辑**
   - Planner 规划器
   - Scheduler 调度器
   - Worker 执行器
   - Critic 验证器

5. **测试数据库连接**
   - 运行应用测试数据库连接
   - 验证 SQL 脚本执行

## 常见问题

### Q1: Maven 无法下载 Spring AI 依赖
**A:** 确保 pom.xml 中已添加 Spring Milestone 仓库：
```xml
<repository>
    <id>spring-milestones</id>
    <name>Spring Milestones</name>
    <url>https://repo.spring.io/milestone</url>
</repository>
```

### Q2: PostgreSQL 连接失败
**A:** 检查：
1. PostgreSQL 服务是否启动
2. 数据库是否已创建
3. 用户名密码是否正确
4. 端口是否正确（默认 5432）

### Q3: Spring AI 配置不生效
**A:** 确保：
1. `OPENAI_API_KEY` 环境变量已设置
2. 配置文件中 `spring.ai.openai.*` 配置正确
3. 依赖已正确添加
