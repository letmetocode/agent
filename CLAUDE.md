# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 提供在此代码库中工作的指导。

## 项目概述

这是一个基于 Spring Boot 3.4.3 和领域驱动设计 (DDD) 架构构建的 AI Agent 框架。系统实现了一个复杂的任务自动化平台，通过规划、执行和验证循环使用 LLM 执行复杂工作流。

**架构模式**: 清洁架构 + DDD 分层 (API → Application → Domain → Infrastructure → Trigger)

## 开发命令

### 构建和运行
```bash
# 构建整个多模块项目
mvn clean install

# 使用指定环境构建 (dev/test/prod)
mvn clean install -P prod

# 运行应用
mvn spring-boot:run -pl agent-app

# Docker 构建
./docs/dev-ops/app/build.sh

# Docker 启动
./docs/dev-ops/app/start.sh
```

### 数据库初始化
```bash
# 使用 schema 文件初始化数据库
# 位置: docs/dev-ops/mysql/sql/
# 按顺序执行 SQL 文件:
# - agent.sql (agent 注册表)
# - agent_sessions.sql
# - agent_plans.sql
# - agent_tasks.sql
# - task_executions.sql
# - sop_templates.sql
# - agent_tool_catalog.sql
# - vector_store.sql
```

## 架构设计

### 多模块结构
```
agent/
├── agent-api/              # API 层 (DTOs, 响应对象)
├── agent-app/              # 应用层 (主入口, 配置管理)
├── agent-domain/           # 领域层 (业务逻辑, 实体, 服务)
├── agent-infrastructure/   # 基础设施层 (持久化, 外部集成)
├── agent-trigger/          # 触发器层 (HTTP 端点, 事件监听)
├── agent-types/            # 共享类型和通用工具
└── docs/dev-ops/           # 文档和 Docker 配置
```

### Agent 执行流程

系统实现类似 ReAct 的三阶段模式：

**阶段 1: 规划 (Planning)**
- 用户提交请求 → 规划器检索 SOP 模板 → 创建执行计划
- SOP (标准作业程序) 模板定义可复用工作流 (CHAIN 或 DAG 结构)

**阶段 2: 调度与执行 (Scheduling & Execution)**
- 调度器轮询 READY 状态的任务
- Worker 通过调用 LLM 执行任务
- 任务支持 DAG 依赖关系以实现并行执行

**阶段 3: 验证与优化 (Validation & Refinement)**
- Critic 根据规则验证任务输出
- 失败任务触发带反馈的重试
- 持续循环直到验证通过或达到最大尝试次数

### 核心组件

- **Planner (规划器)**: 使用 SOP 模板将用户请求转换为执行计划
- **Scheduler (调度器)**: 编排任务执行, 管理状态转换
- **Worker (执行器)**: 通过调用 LLM 执行单个任务
- **Critic (验证器)**: 验证输出并提供优化反馈
- **SOP Templates**: 存储在数据库中的可复用工作流定义

### 数据库设计要点

**核心表:**
- `agent_registry`: Agent 配置、模型设置、系统提示词
- `agent_sessions`: 用户会话跟踪
- `agent_plans`: 高级执行计划及状态跟踪
- `agent_tasks`: 单个任务, 支持 DAG 依赖 (JSONB)
- `task_executions`: 详细执行历史, 包含尝试和验证结果
- `sop_templates`: 可复用工作流模板
- `agent_tool_catalog`: Agent 可用的已注册工具/函数
- `vector_store_registry`: RAG 能力的向量存储

**关键设计模式:**
- JSONB 列实现灵活配置和 DAG 结构
- 乐观锁 (version 字段)
- 完整审计跟踪 (包含 prompt 快照)
- ENUM 类型状态跟踪

## 技术栈

- **框架**: Spring Boot 3.4.3, Java 17
- **数据库**: MySQL 8.0 + HikariCP
- **AI/LLM**: Spring AI 1.1.2 (已配置但实现待完成)
- **JSON**: FastJSON 2.0.28
- **工具库**: Guava, Apache Commons Lang3, Lombok
- **安全**: JWT (JJWT 和 Auth0)
- **容器**: Docker + Docker Compose

## 配置说明

- **环境**: dev (默认), test, prod
- **服务端口**: 8091 (可按环境配置)
- **线程池**: 核心大小 20, 最大 50
- **JVM 参数**: 在 pom.xml 中按环境配置

## 入口点

- **主应用**: `agent-app/src/main/java/com/getoffer/Application.java`
- **HTTP 端点**: 定义在 `agent-trigger` 模块
- **Docker 入口**: `java -jar $JAVA_OPTS /agent-app.jar`

## 重要概念

### DDD 分层职责

- **agent-api**: 外部契约、DTOs、响应对象
- **agent-app**: 应用协调、配置、主入口
- **agent-domain**: 业务逻辑、领域实体、服务 (核心业务规则)
- **agent-infrastructure**: 数据持久化、外部服务集成
- **agent-trigger**: HTTP 端点、消息监听器、事件处理器
- **agent-types**: 共享数据类型和工具

### 状态管理

- **全局上下文**: 黑板模式, 在任务间共享状态
- **执行图**: 基于 JSONB 的 DAG 结构定义工作流
- **重试机制**: 内置重试, 支持可配置尝试次数和反馈

### 当前开发状态

项目处于早期开发阶段：
- ✅ DDD 架构结构已建立
- ✅ 数据库 schema 已完整设计
- ✅ Docker 配置完成
- ✅ 配置系统就位
- ⚠️ 业务逻辑实现较少 (大部分为空包)
- ⚠️ LLM 集成待完成 (Spring AI 已配置但未使用)

## 参考文档

- **DDD 教程**: https://bugstack.cn/md/road-map/ddd.html
- **Docker 指南**: https://bugstack.cn/md/road-map/docker.html