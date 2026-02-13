# 产品需求文档（PRD）

## 1) 产品定位

构建一个面向“复杂任务分解与可追踪执行”的 Agent 控制台，支持从目标输入、规划执行、过程观测到结果沉淀的全链路闭环。

## 2) 目标用户

- 平台开发者：关注执行链路稳定性、可观测性、故障定位效率。
- 运营与业务同学：关注任务完成率、过程可解释性、结果可复用。
- 平台维护者：关注运行基线、告警阈值、发布与回滚可控性。

## 3) 核心用户路径（P0）

1. 点击“新聊天”并输入目标
2. 系统自动创建/复用会话并触发执行
3. 执行中查看实时状态（流式）
4. 输出最终结果与引用
5. 导出或分享结果
6. 历史回溯与复盘

## 4) 当前版本范围（In Scope）

- 会话-回合-消息模型（`session -> turn -> message`）
- V3 会话编排聚合接口（后端统一编排，前端不再拼接旧接口）
- Workflow 路由与 Root 候选 Draft 兜底
- Task 调度执行（claim + lease + execution attempt）
- Plan 终态自动收敛与最终回复汇总
- SSE 实时推送与断线回放（含聊天语义流映射）
- 前端控制台主路径：工作台/对话执行/任务中心/资产中心/观测日志/设置（仅个人偏好）
- 分享能力保持可用（低优先级维护）

## 5) 当前版本范围外（Out of Scope）

- 登录体系接入（SSO/OAuth）
- RBAC 成员权限与系统配置治理后台
- 多租户隔离策略的全量产品化
- 复杂 BI 报表与经营分析系统

## 6) 质量与成功指标

- 稳定性：核心链路“会话触发到结果落地”成功率持续提升。
- 一致性：重复 finalize 不重复写最终 assistant 消息。
- 可观测性：失败请求可通过 `traceId` 快速串联入口与执行日志。
- 效率：主路径关键操作可在 3 次点击内进入执行态。
- 性能：列表与日志检索优先 DB 侧过滤与分页，避免内存全量扫描。

## 7) 已完成功能摘要（2026-02）

- 会话编排 V3 主链路闭环：`POST /api/v3/chat/messages` 统一处理会话创建/复用 + Turn 创建 + Plan 触发。
- 对话历史聚合：`GET /api/v3/chat/sessions/{id}/history` 一次返回会话、回合、消息与 `latestPlanId`。
- 聊天语义流：`GET /api/v3/chat/sessions/{id}/stream` 输出 `message.accepted/task.progress/answer.final` 等事件。
- 路由决策 V3：`GET /api/v3/chat/plans/{id}/routing`。
- 会话编排 V2 入口全量下线（`/api/v2/agents/*`、`/api/v2/sessions*`、`/api/v2/plans/{id}/routing`）。
- 终态幂等收敛：先抢占终态，再写最终消息；重复 finalize 去重。
- DDD 充血收口：会话策略/终态汇总/Plan 聚合迁移/Task 执行策略与提示词/判定/回滚/Agent 选择/黑板写回/JSON 解析/持久化策略下沉至 domain service，并由 trigger.application 写用例统一承接持久化重试。
- 观测能力收口：日志分页 SQL 化、告警目录接口、总览页下钻链路可用。

### 7.1 证据索引（接口 / 测试 / 提交）

| 领域 | 关键接口 | 关键测试 | 关键提交 |
| --- | --- | --- | --- |
| 会话编排 V3 | `POST /api/v3/chat/messages`、`GET /api/v3/chat/sessions/{id}/history`、`GET /api/v3/chat/sessions/{id}/stream` | `ConversationOrchestratorServiceTest`、`ChatV3ControllerTest`、`ChatStreamV3ControllerTest`、`SessionConversationDomainServiceTest` | 本次重构 |
| 路由决策 V3 | `GET /api/v3/chat/plans/{id}/routing` | `ChatRoutingV3ControllerTest` | 本次重构 |
| V2 兼容入口下线 | `GET/POST /api/v2/agents/*`、`POST /api/v2/sessions`、`POST /api/v2/sessions/{id}/turns`、`GET /api/v2/plans/{id}/routing` | `AgentV2ControllerTest`、`SessionV2ControllerTest`、`TurnV2ControllerTest`、`PlanRoutingV2ControllerTest` | 本次重构 |
| 执行与终态 | `POST /api/tasks/{id}/pause`、`/resume`、`/cancel`、`/retry-from-failed` | `TaskExecutorPlanBoundaryTest`、`TurnResultServiceTest`、`PlanStatusDaemonTest`、`PlanFinalizationDomainServiceTest`、`PlanTransitionDomainServiceTest`、`TaskExecutionDomainServiceTest`、`TaskPromptDomainServiceTest`、`TaskEvaluationDomainServiceTest`、`TaskRecoveryDomainServiceTest`、`TaskAgentSelectionDomainServiceTest`、`TaskBlackboardDomainServiceTest`、`TaskJsonDomainServiceTest`、`TaskPersistencePolicyDomainServiceTest`、`TaskPersistenceApplicationServiceTest` | `b8acdde`、`50b15c5` |
| SSE（底层） | `GET /api/plans/{id}/stream` | `PlanStreamControllerTest` | `246e8f9`、`8ff4231` |
| 观测与日志 | `GET /api/dashboard/overview`、`GET /api/logs/paged`、`GET /api/observability/alerts/catalog` | `ConsoleQueryControllerPerformanceTest`、`ObservabilityAlertCatalogControllerTest` | `924ee1e`、`6e43b60` |
| 前端主路径 | `/workspace`、`/sessions`、`/tasks`、`/observability/*` | 前端构建校验 `npm run build` | 本次重构 |

## 8) 需求验收标准（P0）

- 前端发送消息仅调用 V3 聚合接口，不再依赖前端拼装旧链路。
- 流式状态可实时展示执行进度，中间态不作为最终回复落地。
- `answer.final` 到达后，聊天区最终消息与 `session_messages` 保持一致。
- 含 Critic 节点的执行计划完成后，用户最终回复不出现 Critic JSON。
- 同一 turn 在并发 finalize 下终态不反复、最终 assistant 消息不重复。
- SSE 断线重连后可基于游标完成回放，不丢关键状态事件。
- V2 编排接口调用返回明确迁移提示，且文档同步替换为 V3。

## 9) 下一阶段优化目标（仅核心业务）

- P0：会话/规划/执行/SSE/观测五条主链路稳定性与性能持续压测和阈值调优。
- P1：前端执行视图信息密度优化（不改变业务边界）。
- P2：分享能力小步安全增强，不挤占 P0/P1 资源。

## 10) 文档关联

- 系统架构：`docs/02-system-architecture.md`
- UI/UX 规范：`docs/03-ui-ux-spec.md`
- 开发任务清单：`docs/04-development-backlog.md`
