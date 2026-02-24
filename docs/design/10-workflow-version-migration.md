# Workflow Graph 版本迁移策略（v2 -> vNext）

## 1. 目标与范围

本策略用于约束 Workflow Graph 版本演进，避免出现“治理端已升级、运行时仍按旧版执行”的割裂状态。  
覆盖范围：

- 数据落地：`workflow_drafts.graph_definition`、`workflow_definitions.graph_definition`
- 运行时校验：Planner 创建 Plan 前的图归一化与校验
- 治理发布：`sopSpec -> graphDefinition` 编译与发布一致性
- 运维执行：SQL 迁移、回滚与审计记录

## 2. 当前基线（2026-02）

- 运行时唯一主版本：`graphDefinition.version = 2`
- 治理层编译产物：`SopSpecCompileService` 固定输出 `version=2`
- Planner 校验：
  - 生产 Definition：仅接受 `version=2`
  - Root 候选 Draft：允许在节点结构可执行时自动升级到 `version=2`
- Root 候选规划软超时：
  - 生产默认 `planner.root.timeout.soft-ms=60000`（可通过 `PLANNER_ROOT_TIMEOUT_SOFT_MS` 调优）
  - 超时视为不可重试并降级单节点候选 Draft，确保入口可用性
  - Root 基线模型参数补齐 `maxTokens/maxCompletionTokens=768`，抑制候选规划长输出超时
- 生产 Definition 缺参降级：
  - 命中生产 Definition 后若 `inputSchema.required` 仍缺失，Planner 自动降级为候选 Draft
  - 路由原因标记为 `PRODUCTION_DEFINITION_INPUT_MISSING`，避免回合直接失败
- 会话上下文必填补齐：
  - Chat V3 在构建 Plan 上下文时默认注入 `userId`（来源于 Session）
  - 当 Workflow `inputSchema.required` 包含 `userId` 时，Planner 可从运行时上下文补齐，避免异步规划误失败

关键证据：

- `agent-trigger/src/main/java/com/getoffer/trigger/application/command/SopSpecCompileService.java`
- `agent-domain/src/main/java/com/getoffer/domain/planning/service/WorkflowGraphPolicyKernel.java`
- `agent-infrastructure/src/main/java/com/getoffer/infrastructure/planning/PlannerServiceImpl.java`
- `agent-infrastructure/src/main/java/com/getoffer/infrastructure/planning/WorkflowDraftLifecycleService.java`
- `agent-infrastructure/src/main/java/com/getoffer/infrastructure/planning/WorkflowRoutingResolveService.java`
- `agent-domain/src/main/java/com/getoffer/domain/session/service/SessionConversationDomainService.java`
- `agent-app/src/main/resources/application-prod.yml`
- `docs/dev-ops/postgresql/sql/01_init_database.sql`
- `docs/dev-ops/postgresql/sql/migrations/V20260225_05_root_planner_max_tokens_guard.sql`

## 3. 兼容矩阵（主链路）

| 链路 | 写入版本 | 读取版本 | 当前策略 |
| --- | --- | --- | --- |
| `SopSpecCompileService`（治理编译） | v2 | N/A | 固定输出 v2 |
| `WorkflowGovernanceApplicationService`（Draft 更新/发布） | v2 | v2 | 发布前校验 `compileHash` 与 Runtime Graph 一致 |
| `PlannerServiceImpl`（命中生产 Definition） | N/A | v2 | 严格校验，非 v2 直接失败 |
| `WorkflowDraftLifecycleService`（候选 Draft） | v2（归一化后） | v2 / 非 v2 候选 | 仅候选路径允许自动升级到 v2 |
| `WorkflowTaskMaterializationService` / 执行链路 | N/A | v2（Plan 快照） | 仅消费已归一化版本 |

结论：当前属于“**单写单读 v2 + 候选兼容升级**”，尚未进入 vNext 双栈期。

## 4. v2 -> vNext 标准迁移流程

### Phase A：运行时兼容准备（先兼容，后切换）

目标：发布前先具备 vNext 可读能力，避免切换窗口出现不可执行图。

必做项：

1. 在图内核增加 vNext 读兼容（建议通过显式开关控制）。
2. 编译链路仍保持 v2 写入，先完成灰度验证。
3. 建立兼容矩阵并确认回滚路径。

### Phase B：数据迁移（可审计、可回放）

目标：把落库图定义从 v2 迁移到 vNext，并保留完整审计快照。

模板文件：

- `docs/dev-ops/postgresql/sql/migrations/templates/TEMPLATE_workflow_graph_vnext.sql`
- `docs/dev-ops/postgresql/sql/migrations/templates/TEMPLATE_workflow_graph_vnext_rollback.sql`

执行要求：

1. 每次迁移必须使用唯一 `run_id`。
2. 迁移前必须把 `before_graph` 写入审计表。
3. 迁移后必须回填 `after_graph` 并执行校验 SQL。

### Phase C：写路径切换（治理端切到 vNext）

目标：将 `sopSpec` 编译输出从 v2 切换为 vNext，运行时保持双读一段时间。

必做项：

1. 编译器切换写版本后，持续观察 Planner 成功率与错误率。
2. 通过门禁验证后再关闭旧版本写入。
3. 观察期结束后，清理 v2 专用兼容逻辑。

## 5. 回滚脚本规范

回滚必须满足：

1. 按 `run_id` 精确回滚，禁止“全量无条件覆盖”。
2. 仅回滚 `graph_definition`，不混入业务字段回滚。
3. 回滚后执行一致性校验：`before_graph == current graph_definition`。
4. 回滚脚本与正向脚本成对提交，命名与版本号一一对应。

## 6. 迁移验收标准（可测）

1. 数据一致性：
   - 迁移批次内 `workflow_drafts/workflow_definitions` 命中行数与审计行数一致。
2. 可回滚性：
   - 指定 `run_id` 回滚后，抽样比对 `before_graph` 全量一致。
3. 运行稳定性：
   - Planner 创建 Plan 成功率不下降（相对迁移前无显著回退）。
4. 文档完整性：
   - `README.md`、`docs/02-system-architecture.md`、本文件同步更新。

## 7. 版本迁移实施清单

- 定义 vNext 语义与 JSON Schema（字段、默认值、兼容规则）。
- 完成 Phase A 双读能力与灰度开关。
- 基于模板生成正式 SQL 与回滚脚本。
- 迁移执行与验收。
- 切换写路径到 vNext。
- 观察期结束后清理 v2 写路径。
