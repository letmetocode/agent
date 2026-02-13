# 产品需求文档（PRD）

## 1) 产品定位

构建一个面向“复杂任务分解与可追踪执行”的 Agent 控制台，支持从目标输入、规划执行、过程观测到结果沉淀的全链路闭环。

## 2) 目标用户

- 平台开发者：关注执行链路稳定性、可观测性、故障定位效率。
- 运营与业务同学：关注任务完成率、过程可解释性、结果可复用。
- 管理员：关注配置治理、权限边界、审计可追责。

## 3) 核心用户路径（P0）

1. 新建 Agent / 新建任务
2. 输入目标并触发执行
3. 执行中途控制（暂停/继续/取消/失败重试）
4. 查看结果与引用
5. 导出或分享结果
6. 历史回溯与复盘

## 4) 本期范围（In Scope）

- 会话-回合-消息模型（`session -> turn -> message`）
- Workflow 路由与 Root 候选 Draft 兜底
- Task 调度执行（claim + lease + execution attempt）
- Plan 终态自动收敛与最终回复汇总
- SSE 实时推送与断线回放
- 前端控制台主路径：工作台/对话执行/任务中心/资产中心/观测日志/设置

## 5) 暂不作为本期主目标（Out of Scope）

- 完整企业级 SSO 与组织权限中台打通
- 多租户隔离策略的全量产品化
- 复杂 BI 报表与经营分析系统
- 分享能力仅做保守维护，不作为本轮核心投入方向

## 6) 质量与成功指标

- 稳定性：核心链路“会话触发到结果落地”成功率持续提升。
- 一致性：重复 finalize 不重复写最终 assistant 消息。
- 可观测性：失败请求可通过 `traceId` 快速串联入口与执行日志。
- 效率：主路径关键操作可在 3 次点击内进入执行态。

## 7) 当前已落地能力（2026-02）

- Workflow 命中策略：优先 `PRODUCTION + ACTIVE` Definition；未命中由 Root 生成候选 Draft。
- Root 兜底策略：重试最多 3 次，失败降级为单节点候选 Draft（`AUTO_MISS_FALLBACK`）。
- Root 不硬编码：Root 配置来自 `agent_registry`，启动时执行可用性校验。
- 候选节点执行兜底：缺省 `agentId/agentKey` 时注入 `planner.root.fallback.agent-key`（默认 `assistant`）。
- 回合最终输出：仅汇总 `WORKER` 完成输出，不暴露 `CRITIC` JSON。
- Plan 黑板写回：读取最新 Plan 并在乐观锁冲突下有限重试。
- 会话与规划 V2 主链路已落地：Agent 选择/创建 -> Session 启动 -> Turn 触发 -> Routing 决策回查。
- 终态幂等：先抢占终态，再写最终消息；重复 finalize 去重。
- SSE 游标优先级：`Last-Event-ID` 高于 query 参数 `lastEventId`。

## 8) 需求验收标准（P0）

- 含 Critic 节点的执行计划完成后，用户最终回复不出现 Critic JSON。
- 同一 turn 在并发 finalize 下终态不反复、最终 assistant 消息不重复。
- SSE 断线重连后可基于游标完成回放，不丢关键状态事件。
- 会话页与任务详情页可完整完成“执行中控制 + 结果查看 + 引用定位”。
- 路由决策可追溯：可查看 `sourceType/fallbackReason/plannerAttempts`，支持故障复盘。

## 8.5) 当前版本范围声明（2026-02）

- 本轮研发重心为“会话与规划主链路 + 执行稳定性 + 可观测性”，已完成 V2 会话与规划能力闭环。
- 分享能力保持可用但降优先级，不占用 P0/P1 资源。
- 下一阶段优先补齐监控告警与旧接口收敛策略。

## 9) 文档关联

- 系统架构：`docs/02-system-architecture.md`
- UI/UX 规范：`docs/03-ui-ux-spec.md`
- 开发任务清单：`docs/04-development-backlog.md`
