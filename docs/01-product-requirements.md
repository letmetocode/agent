# 产品需求文档（PRD）

## 1) 产品定位

构建一个面向“复杂任务分解与可追踪执行”的 Agent 控制台，支持从目标输入、规划执行、过程观测到结果沉淀的全链路闭环。

## 2) 目标用户

- 平台开发者：关注执行链路稳定性、可观测性、故障定位效率。
- 运营与业务同学：关注任务完成率、过程可解释性、结果可复用。
- 平台维护者：关注运行基线、告警阈值、发布与回滚可控性。

## 3) 核心用户路径（P0）

1. 新建 Agent / 新建任务
2. 输入目标并触发执行
3. 执行中途控制（暂停/继续/取消/失败重试）
4. 查看结果与引用
5. 导出或分享结果
6. 历史回溯与复盘

## 4) 当前版本范围（In Scope）

- 会话-回合-消息模型（`session -> turn -> message`）
- Workflow 路由与 Root 候选 Draft 兜底
- Task 调度执行（claim + lease + execution attempt）
- Plan 终态自动收敛与最终回复汇总
- SSE 实时推送与断线回放
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

- 会话与规划 V2 主链路闭环：Agent 选择/创建 -> Session 启动 -> Turn 触发 -> Routing 决策回查。
- 终态幂等收敛：先抢占终态，再写最终消息；重复 finalize 去重。
- SSE 语义统一：`Last-Event-ID` 优先于 query `lastEventId`，支持回放补偿。
- 观测能力收口：日志分页 SQL 化、告警目录接口、总览页下钻链路可用。
- 前端 IA 收口：移除系统配置与成员权限页面，设置仅保留个人偏好。

### 7.1 证据索引（接口 / 测试 / 提交）

| 领域 | 关键接口 | 关键测试 | 关键提交 |
| --- | --- | --- | --- |
| 会话与规划 | `POST /api/v2/sessions`、`POST /api/v2/sessions/{id}/turns`、`GET /api/v2/plans/{id}/routing` | `AgentV2ControllerTest`、`SessionV2ControllerTest`、`TurnV2ControllerTest`、`PlanRoutingV2ControllerTest`、`PlannerServiceRootDraftTest` | `0db0c5a`、`6f0769c` |
| 执行与终态 | `POST /api/tasks/{id}/pause`、`/resume`、`/cancel`、`/retry-from-failed` | `TaskExecutorPlanBoundaryTest`、`TurnResultServiceTest`、`PlanStatusDaemonTest`、`ExecutorTerminalConvergenceIntegrationTest` | `b8acdde`、`50b15c5` |
| SSE | `GET /api/plans/{id}/stream` | `SessionChatPlanSseIntegrationTest` | `246e8f9`、`8ff4231` |
| 观测与日志 | `GET /api/dashboard/overview`、`GET /api/logs/paged`、`GET /api/observability/alerts/catalog` | `PlanStreamControllerTest`、`ConsoleQueryControllerPerformanceTest`、`ObservabilityAlertCatalogControllerTest` | `924ee1e`、`6e43b60` |
| 前端主路径 | `/workspace`、`/sessions`、`/tasks`、`/observability/*`、`/settings/profile` | 前端构建校验 `npm run build` | `8eb3daa`、`6f0769c`、`5a02163` |
| 分享 | `POST /api/tasks/{id}/share-links`、`GET /api/share/tasks/{id}` | `TaskShareLinkControllerIntegrationTest`、`ShareAccessControllerIntegrationTest` | `aed6c29`、`c0339b7`、`28eb2f5` |

## 8) 需求验收标准（P0）

- 含 Critic 节点的执行计划完成后，用户最终回复不出现 Critic JSON。
- 同一 turn 在并发 finalize 下终态不反复、最终 assistant 消息不重复。
- SSE 断线重连后可基于游标完成回放，不丢关键状态事件。
- 会话页与任务详情页可完整完成“执行中控制 + 结果查看 + 引用定位”。
- 路由决策可追溯：可查看 `sourceType/fallbackReason/plannerAttempts`，支持故障复盘。
- 设置域仅保留个人偏好入口，不出现系统配置与成员权限入口。

## 9) 下一阶段优化目标（仅核心业务）

- P0：会话/规划/执行/SSE/观测五条主链路稳定性与性能持续压测和阈值调优。
- P1：前端执行视图信息密度优化（不改变业务边界）。
- P2：分享能力小步安全增强，不挤占 P0/P1 资源。

## 10) 文档关联

- 系统架构：`docs/02-system-architecture.md`
- UI/UX 规范：`docs/03-ui-ux-spec.md`
- 开发任务清单：`docs/04-development-backlog.md`
