# 开发任务清单（按业务域）

## 1. 文档目标

- 提供可执行的业务域台账：已完成、未完成、计划优化、证据与验收。
- 作为后续迭代唯一基线，避免偏题投入与重复建设。
- 与 `docs/01`、`docs/02`、`docs/03` 保持范围一致。

## 2. 阅读约定

### 2.1 状态定义

- `已完成`：能力可用且有证据（接口/测试/提交）。
- `进行中`：已有部分实现，尚未达到验收标准。
- `计划优化`：不阻塞主链路，纳入后续版本。
- `范围外`：当前阶段明确不做（登录、RBAC 等）。

### 2.2 优先级定义

- `P0`：核心业务稳定性与性能，影响主链路可用性。
- `P1`：主链路效率与可维护性优化。
- `P2`：体验增强与低风险增量能力。

### 2.3 证据规则

每个业务域必须给出至少一项可追溯证据：
- 接口证据（API/路由/配置）
- 测试证据（单测/集成测试/构建）
- 提交证据（commit hash）

## 3. 阶段总览（2026-02）

- 核心链路已闭环：会话与规划、执行与终态、SSE、观测与日志、前端主路径。
- 前端范围已收口：删除系统配置与成员权限页面，设置仅保留个人偏好。
- 分享能力可用但降优先级，不占用核心主链路资源。
- 当前范围外：登录与 RBAC（明确延后，不在本轮交付）。

## 4. 业务域台账

### 4.1 会话与规划（Session + Planner）

- 状态：`已完成`

**已完成功能**
- V2 会话主链路：Agent 选择/创建 -> Session 创建 -> Turn 触发。
- 路由决策可回查：支持 `sourceType/fallbackReason/plannerAttempts`。
- Workflow 命中优先 `PRODUCTION + ACTIVE`，未命中触发 Root 候选与单节点 fallback。
- 旧聊天入口硬下线，避免 V1/V2 并存语义漂移。

**未完成 / 计划优化**
- `P0`：持续校准 planner fallback 绝对值与比例阈值，避免误报/漏报。
- `P1`：补充 routing 结果的前端可解释字段展示（不改后端语义）。

**证据矩阵**

| 能力项 | 接口/配置证据 | 测试证据 | 提交证据 |
| --- | --- | --- | --- |
| V2 会话主链路 | `GET /api/v2/agents/active`、`POST /api/v2/agents`、`POST /api/v2/sessions`、`POST /api/v2/sessions/{id}/turns` | `AgentV2ControllerTest`、`SessionV2ControllerTest`、`TurnV2ControllerTest` | `0db0c5a`、`6f0769c` |
| 路由决策回查 | `GET /api/v2/plans/{id}/routing` | `PlanRoutingV2ControllerTest`、`PlannerServiceRootDraftTest` | `0db0c5a` |
| 旧接口收口 | `POST /api/sessions/{id}/chat`（迁移提示） | `ChatControllerTest` | `0db0c5a` |

**验收标准**
- 主路径可稳定返回 `planId` 且路由决策可追溯。
- 未命中生产定义时可稳定进入 Root/fallback 流程。

---

### 4.2 执行与终态收敛（Executor + Plan Status + Turn Result）

- 状态：`已完成`

**已完成功能**
- claim/lease/executionAttempt 并发语义收口。
- 终态收敛幂等化：先抢占终态，再写最终 assistant 消息。
- finalize 去重与老代执行者回写拒绝，避免并发污染。
- 终态相关监控指标与告警规则已固化。

**未完成 / 计划优化**
- `P0`：周期化执行并发压测，按发布节奏校准超时与重试阈值。
- `P1`：补充执行失败分类统计（超时/工具错误/依赖失败）用于排障提效。

**证据矩阵**

| 能力项 | 接口/配置证据 | 测试证据 | 提交证据 |
| --- | --- | --- | --- |
| 并发 claim/回写 guard | 执行器 claim + guard 条件（`claim_owner`、`execution_attempt`） | `TaskExecutorPlanBoundaryTest` | `b8acdde` |
| 终态幂等与去重 | `session_messages` 终态唯一约束与 finalize 语义 | `TurnResultServiceTest`、`PlanStatusDaemonTest`、`ExecutorTerminalConvergenceIntegrationTest` | `50b15c5`、`b8acdde` |
| 终态观测与告警 | `docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.yml` | `promtool check/test rules`（规则校验） | `b8acdde` |

**验收标准**
- 并发 finalize 不重复写最终 assistant 消息。
- 旧代执行者回写被拒绝，不覆盖新代执行结果。

---

### 4.3 实时流与回放（SSE）

- 状态：`已完成`

**已完成功能**
- 事件持久化 + `pg_notify/listen` + replay sweep 三层模型。
- 游标优先级统一：`Last-Event-ID` > query `lastEventId`。
- SSE 指标补强（推送失败、回放命中、回放耗时）。
- 告警规则与 runbook 已固化。

**未完成 / 计划优化**
- `P0`：多实例网络抖动场景下的长时回放稳定性回归脚本常态化。
- `P1`：回放批次参数按环境差异化模板沉淀。

**证据矩阵**

| 能力项 | 接口/配置证据 | 测试证据 | 提交证据 |
| --- | --- | --- | --- |
| SSE 主链路 | `GET /api/plans/{id}/stream` | `SessionChatPlanSseIntegrationTest` | `246e8f9`、`e1e553f` |
| 游标优先级 | `Last-Event-ID` 覆盖 query `lastEventId` | `SessionChatPlanSseIntegrationTest` | `e1e553f`、`8c85012` |
| 告警与运维文档 | `sse-alert-rules.yml` + runbook | `promtool check/test rules`（规则校验） | `8ff4231` |

**验收标准**
- 断线重连后可连续回放，不丢关键状态事件。
- SSE 指标与告警规则可覆盖异常注入场景。

---

### 4.4 观测与日志（Observability）

- 状态：`已完成`

**已完成功能**
- HTTP 入口统一日志与 `traceId/requestId` 注入。
- 总览指标支持 P95/P99、慢任务、SLA 违约。
- 日志查询改为 DB 侧过滤 + 计数 + 分页（替代内存全量扫描）。
- 告警目录接口已上线，并支持总览页下钻联动。

**未完成 / 计划优化**
- `P0`：按环境替换告警目录中的 dashboard 实际地址并定期巡检。
- `P1`：扩大日志检索字段覆盖（在不增加高代价索引前提下）。

**证据矩阵**

| 能力项 | 接口/配置证据 | 测试证据 | 提交证据 |
| --- | --- | --- | --- |
| 总览与日志检索 | `GET /api/dashboard/overview`、`GET /api/logs/paged` | `ConsoleQueryControllerPerformanceTest` | `924ee1e` |
| 告警目录 | `GET /api/observability/alerts/catalog` | `ObservabilityAlertCatalogControllerTest` | `924ee1e` |
| 前端下钻联动 | 总览页到日志检索 URL 参数联动（`level/taskId/keyword`） | 前端构建 `npm run build` | `6e43b60` |

**验收标准**
- 任意失败请求可通过 `traceId` 关联入口日志与执行日志。
- 日志列表在大数据量下保持可分页、可过滤、可回放。

---

### 4.5 前端控制台主路径（UI 主链路）

- 状态：`已完成（按当前范围收口）`

**已完成功能**
- IA 重构完成：工作台/对话执行/任务中心/资产中心/观测日志/设置。
- 会话页、任务页、任务详情页主链路可用。
- `PageHeader/StateView/StatusTag` 统一页面与状态表达。
- 设置域已收口为仅 `/settings/profile`，移除系统配置与成员权限入口。

**未完成 / 计划优化**
- `范围外`：登录与 RBAC 暂不纳入当前版本。
- `P1`：会话执行页信息密度优化（减少滚动切换，强化关键状态可见性）。
- `P1`：任务中心筛选条件保留与恢复策略优化。

**证据矩阵**

| 能力项 | 接口/路由证据 | 测试/校验证据 | 提交证据 |
| --- | --- | --- | --- |
| IA 与主路径 | `/workspace`、`/sessions`、`/tasks`、`/observability/*` | 前端构建 `npm run build` | `8eb3daa`、`8c27832` |
| V2 会话接入 | `/api/v2/*` 主链路适配 | 前端构建 `npm run build` | `6f0769c` |
| 设置域收口 | `/settings/profile`（移除 `/settings/system`、`/settings/access`） | 前端构建 `npm run build` | `5a02163` |

**验收标准**
- 核心路径在 3 次点击内进入执行。
- 导航与文档一致，不出现已下线路由入口。

---

### 4.6 分享与对外交付

- 状态：`已完成（能力可用，优先级下调）`

**已完成功能**
- 分享链接创建、列表、单条撤销、全部撤销。
- 匿名分享读取、token/code 校验、撤销与过期处理。
- 分享链路集成测试已补齐。

**未完成 / 计划优化**
- `P2`：安全与体验小步优化（不占用 P0/P1 资源）。

**证据矩阵**

| 能力项 | 接口证据 | 测试证据 | 提交证据 |
| --- | --- | --- | --- |
| 分享链路 | `POST /api/tasks/{id}/share-links`、`GET /api/tasks/{id}/share-links`、`GET /api/share/tasks/{id}` | `TaskShareLinkControllerIntegrationTest`、`ShareAccessControllerIntegrationTest` | `aed6c29`、`c0339b7`、`28eb2f5` |

**验收标准**
- 分享创建、访问、撤销、过期语义一致且可回归。

## 5. 未完成 / 计划优化总清单

### 5.1 P0（必须优先）

- 会话/规划：持续校准 fallback 告警阈值与异常分布基线。
- 执行/终态：并发 finalize 与 claim/lease 压测常态化，发布前必须回归。
- SSE：跨实例网络抖动与断流场景回放稳定性巡检。
- 观测：告警目录 dashboard 链接按环境替换并校验可达性。

### 5.2 P1（核心效率）

- 会话页：路由决策和执行进度信息密度优化。
- 任务中心：筛选条件保持、恢复与跨页面回跳体验优化。
- 观测：日志检索字段覆盖扩展与检索效率平衡。

### 5.3 P2（体验增强）

- 分享功能小步安全增强与文案统一。
- 视觉一致性和键盘可达性持续优化。

## 6. 回归基线命令

- 联编校验：`mvn -pl agent-app -am -DskipTests test-compile`
- 核心单测：
  - `PlannerServiceRootDraftTest`
  - `AgentV2ControllerTest`
  - `SessionV2ControllerTest`
  - `TurnV2ControllerTest`
  - `PlanRoutingV2ControllerTest`
  - `ChatControllerTest`
  - `TaskExecutorPlanBoundaryTest`
  - `TurnResultServiceTest`
  - `PlanStatusDaemonTest`
  - `PlanStreamControllerTest`
  - `ConsoleQueryControllerPerformanceTest`
  - `ObservabilityAlertCatalogControllerTest`
- 集成回归（按需）：
  - `SessionChatPlanSseIntegrationTest`
  - `ExecutorTerminalConvergenceIntegrationTest`
  - `TaskShareLinkControllerIntegrationTest`
  - `ShareAccessControllerIntegrationTest`

## 7. 当前范围声明（强约束）

- 当前阶段聚焦核心业务闭环与性能稳定性。
- 登录、RBAC、系统配置治理后台为范围外事项，不在本轮交付。
- 任何新增需求需先映射到本清单业务域并标注优先级与证据策略。
