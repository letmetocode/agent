# Executor + Terminal 告警处置手册（Runbook）

## 1. 适用范围

- 服务：`agent-app`
- 模块：`executor-terminal`
- 规则文件：`docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.yml`

## 2. 告警定义

### 2.1 finalize 去重比例告警

- `ExecutorFinalizeDedupRatioWarningStaging`：预发 dedup/attempt 比例 > 0.25 且持续 10 分钟。
- `ExecutorFinalizeDedupRatioCriticalProd`：生产 dedup/attempt 比例 > 0.20 且持续 5 分钟。

### 2.2 claimed update guard 拒绝比例告警

- `ExecutorClaimGuardRejectWarningStaging`：预发 guard_reject 比例 > 0.08 且持续 10 分钟。
- `ExecutorClaimGuardRejectCriticalProd`：生产 guard_reject 比例 > 0.05 且持续 5 分钟。

### 2.3 timeout 最终失败比例告警

- `ExecutorTimeoutFinalFailCriticalStaging`：预发 timeout_final_fail/execution 比例 > 0.03 且持续 10 分钟。
- `ExecutorTimeoutFinalFailCriticalProd`：生产 timeout_final_fail/execution 比例 > 0.02 且持续 5 分钟。

### 2.4 指标断流告警

- `ExecutorFinalizeMetricMissingStaging` / `ExecutorFinalizeMetricMissingProd`：15 分钟采集不到 `agent_plan_finalize_attempt_total`。

## 3. 分级与响应

- `warning`：15 分钟内人工确认，定位是否由回放/重试抖动导致。
- `critical`：5 分钟内响应，优先排查并发冲突、执行器堆积、模型超时。

## 4. 5 分钟排障步骤

1. 查看以下指标 30 分钟走势：
   - `agent_plan_finalize_attempt_total`
   - `agent_plan_finalize_dedup_total`
   - `agent_task_claimed_update_guard_reject_total`
   - `agent_task_claimed_update_success_total`
   - `agent_task_execution_timeout_final_fail_total`
2. 检查执行器运行态：
   - `agent_task_worker_inflight_current`
   - `agent_task_worker_queue_current`
   - `agent_task_expired_running_current`
3. 按 `traceId` 抽样检查日志：
   - `claimed_update_guard_reject`
   - `lease_renew_guard_reject`
   - `Task execution timed out`
4. 核对发布变更：
   - 执行器参数（claim/lease/heartbeat/timeout）
   - 模型配置与外部依赖可用性

## 5. 临时止损动作

1. 若 timeout 失败率异常升高：
   - 临时放宽 `executor.execution.timeout-ms` 或降低并发
   - 回滚最近模型/网络变更
2. 若 guard reject 比例异常：
   - 检查是否存在跨实例时钟漂移、lease 配置过短
   - 适度提高 `claim-lease-seconds` 并观察
3. 若 dedup 比例异常：
   - 排查计划终态推进是否重复触发
   - 重点检查同一 plan 的重复 reconcile 频率

## 6. 恢复判定

满足以下条件连续 30 分钟视为恢复：

- `dedup_ratio_5m` 回落到阈值以下
- `guard_reject_ratio_5m` 回落到阈值以下
- `timeout_final_fail_ratio_5m` 回落到阈值以下
- `agent_plan_finalize_attempt_total` 连续可采集

## 7. 发布与回滚

1. 修改规则并评审。
2. 执行校验：
   - `promtool check rules docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.yml`
   - `promtool test rules docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.test.yml`
3. 加载规则并观察一个采集周期。
4. 若误报严重，回滚到上一版规则。
