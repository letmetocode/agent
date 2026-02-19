# 非 V3 接口 Deprecation Registry（治理基线）

## 1. 目标

为非 V3 接口建立统一废弃治理基线，避免“文档写了废弃、代码仍被依赖”的失控状态。  
本基线明确三项约束：

- 公告窗口：接口进入废弃状态时必须给出公告起始日期与下线日期。
- 迁移文档：每个废弃项必须绑定明确迁移路径与文档锚点。
- 下线基线：每个废弃项必须绑定目标下线版本/里程碑标识。

## 2. 注册表来源

- 配置文件：`agent-app/src/main/resources/governance/deprecation-registry.json`
- 查询接口：`GET /api/governance/deprecations`
- 过滤参数：
  - `status`：`ANNOUNCED | MIGRATING | REMOVED`
  - `includeRemoved`：是否包含已下线项（默认 `true`）

接口返回内容包含：

- `items`：废弃项明细（含 `legacyPath/replacementPath/migrationDoc/sunsetBaseline`）
- `statusSummary`：按状态聚合计数
- `policy.minNoticeWindowDays`：公告窗口最小基线（当前 `30` 天）
- `generatedAt`：生成时间

## 3. 状态定义

- `ANNOUNCED`：已公告、仍可调用，处于迁移窗口。
- `MIGRATING`：迁移推进中，通常已进入灰度限制。
- `REMOVED`：接口已下线，仅保留历史记录。

## 4. 当前迁移映射（摘要）

- `v2` 历史接口（`/api/v2/agents/*`、`/api/v2/sessions*`、`/api/v2/plans/{id}/routing`）已下线，统一迁移到 V3。
- 非 V3 读接口（`/api/plans/{id}`、`/api/plans/{id}/tasks`、`/api/tasks/{id}/executions`）已在 `2026-02-19` 硬删除，迁移到分页/质量事件/V3 history 主链路。

## 5. 迁移条目

### v2-agents
- 旧接口：`/api/v2/agents/*`
- 替代：`/api/v3/chat/messages` + `/api/v3/chat/sessions/{id}/history` + `/api/v3/chat/sessions/{id}/stream`

### v2-sessions
- 旧接口：`/api/v2/sessions*`
- 替代：`/api/v3/chat/messages` + `/api/v3/chat/sessions/{id}/history`

### v2-routing
- 旧接口：`/api/v2/plans/{id}/routing`
- 替代：`/api/v3/chat/plans/{id}/routing`

### legacy-plan-query
- 旧接口：`/api/plans/{id}`
- 替代：`/api/tasks/{id}` + `/api/plans/{id}/events` + `/api/v3/chat/sessions/{id}/history`

### legacy-plan-task-query
- 旧接口：`/api/plans/{id}/tasks`
- 替代：`/api/tasks/paged?planId={id}` + `/api/v3/chat/sessions/{id}/history`

### legacy-task-execution-query
- 旧接口：`/api/tasks/{id}/executions`
- 替代：`/api/quality/evaluations/paged?taskId={id}`

## 6. 维护规则

- 新增废弃项时必须同步更新：
  - `deprecation-registry.json`
  - 本文档对应迁移段落
  - `README.md` 与 `docs/02-system-architecture.md` 的接口基线说明
- 下线执行完成后，必须将状态更新为 `REMOVED`，并保留历史条目以支持审计与回放。
