# 功能文档：开发指南与回归清单

## 1. 目标

- 提供统一开发路径，减少临时修补。
- 将功能变更和并发一致性检查前置。
- 形成可执行的提测与上线前核对清单。

## 2. 开发流程图

```mermaid
flowchart TD
    A[需求输入] --> B[定位功能文档]
    B --> C[补充流程和时序设计]
    C --> D[设计状态机与并发语义]
    D --> E[编码实现]
    E --> F[单元和集成测试]
    F --> G[指标和日志校验]
    G --> H[更新文档并提交]
```

## 3. 开发时序图

```mermaid
sequenceDiagram
    autonumber
    participant DEV as Developer
    participant DOC as docs/design
    participant CODE as Codebase
    participant DB as SQL Mapper
    DEV->>DOC: 更新功能设计与图
    DEV->>CODE: 修改 domain trigger infrastructure
    DEV->>DB: 同步 mapper and po and entity
    DEV->>CODE: 补充测试
    DEV->>DOC: 回填实现差异和回归点
```

## 4. 新增功能标准步骤

1. 先确定落在哪个功能文档，补齐目标与边界。
2. 明确状态迁移和失败语义，再开始编码。
3. 并发路径必须定义 guard 条件和冲突处理。
4. 同步更新：
   - Controller or Daemon
   - Domain Entity
   - Repository and Mapper
   - 文档中的流程图和时序图
5. 涉及事件流时，同步验证“发布、持久化、订阅、回放”四条链路。

## 5. 代码变更清单

- 是否新增或修改了状态机迁移规则。
- 是否影响 claim 或 lease 或 executionAttempt。
- 是否影响 `version` 乐观锁路径。
- Task 类型是否统一使用 `TaskTypeEnum`（禁止字符串常量漂移）。
- 是否新增配置项并提供默认值。
- 是否新增指标和日志用于排障。
- Agent 工具配置是否只在 `ChatClient.Builder` 单点写入（避免 Options/Builder 双写）。
- SSE 变更是否遵循事件驱动，不引入每连接轮询线程。

## 6. SQL 变更清单

- 表结构字段是否与 PO Entity Mapper 三方同步。
- 索引是否覆盖关键查询条件和排序字段。
- 是否存在跨数据库方言冲突风险。
- 是否已同步更新最终版 SQL：`docs/dev-ops/postgresql/sql/01_init_database.sql`。

## 7. 回归测试清单

功能：
- 会话创建
- 聊天触发规划
- 任务依赖推进
- 任务执行完成
- Plan 自动闭环

并发：
- 多实例并发 claim
- lease 过期重领
- 旧执行者回写拒绝
- 乐观锁冲突恢复
- SSE 重连后基于 `lastEventId` 的回放正确性

观测：
- 指标可见
- 审计日志可定位
- 异常路径有明确错误分类
- 执行记录 `model_name/token_usage/error_type` 落库完整

## 8. 提交与评审建议

- 提交信息直接体现功能域，例如 `feature: claim guard hardening`。
- PR 描述必须包含：
  - 功能目标
  - 状态迁移变化
  - 并发一致性策略
  - 回归测试结果
  - 迁移脚本执行说明
