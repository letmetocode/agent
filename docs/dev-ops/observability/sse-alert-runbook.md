# SSE 告警处置手册（Runbook）

## 1. 适用范围

- 服务：`agent-app`
- 模块：`sse`
- 规则文件：`docs/dev-ops/observability/prometheus/sse-alert-rules.yml`

## 2. 告警定义

### 2.1 推送失败比例告警

- `SsePushFailRatioWarningStaging`：预发 5 分钟窗口推送失败比例 > 0.05 且持续 10 分钟。
- `SsePushFailRatioCriticalProd`：生产 5 分钟窗口推送失败比例 > 0.02 且持续 5 分钟。

### 2.2 回放命中率告警

- `SseReplayHitRatioLowWarningStaging`：预发回放命中率 < 0.20 且 `replay_batch_total_5m > 20`。
- `SseReplayHitRatioLowCriticalProd`：生产回放命中率 < 0.10 且 `replay_batch_total_5m > 20`。

### 2.3 回放耗时告警

- `SseReplayAvgDurationHighWarningStaging`：预发 5 分钟窗口回放平均耗时 > 1.50 秒。
- `SseReplayAvgDurationHighCriticalProd`：生产 5 分钟窗口回放平均耗时 > 1.20 秒。

### 2.4 指标断流告警

- `SseReplayMetricMissingStaging` / `SseReplayMetricMissingProd`：15 分钟采集不到 `agent_sse_replay_batch_total`。

## 3. 分级与响应

- `warning`：15 分钟内人工确认，判断是否由网络抖动或短时流量峰值导致。
- `critical`：5 分钟内响应，优先排查 SSE 链路可用性与回放积压。

## 4. 5 分钟排障步骤

1. 查看以下指标最近 30 分钟走势：
   - `agent_sse_push_attempt_total`
   - `agent_sse_push_fail_total`
   - `agent_sse_replay_batch_total`
   - `agent_sse_replay_hit_total`
   - `agent_sse_replay_duration_seconds_sum/count`
2. 结合入口日志检查异常连接：
   - SSE 建连是否突增
   - 客户端断开是否集中（网关/浏览器版本/地区）
3. 核查数据库与通知链路：
   - `plan_task_events` 写入是否延迟
   - `pg_notify/listen` 连接是否异常重连
4. 核查应用参数与发布变更：
   - `sse.replay.batch-size`
   - `sse.replay.max-batches-per-sweep`
   - 最近版本发布、连接池与线程池配置变更

## 5. 临时止损动作

1. 若推送失败比例升高：
   - 优先排查网关/负载均衡与客户端网络稳定性。
   - 必要时临时提高客户端重连退避并降低连接峰值。
2. 若回放命中率持续偏低：
   - 检查是否存在事件写入延迟或游标异常。
   - 优先修复事件写入链路，再观察回放命中恢复。
3. 若回放耗时偏高：
   - 临时下调热点会话并发，观察 DB 负载。
   - 结合压测结果谨慎调整 `sse.replay.batch-size`。

## 6. 恢复判定

满足以下条件连续 30 分钟可视为恢复：

- `agent:sse_push_fail_ratio_5m` 回到阈值以下
- `agent:sse_replay_hit_ratio_5m` 回到阈值以上
- `agent:sse_replay_avg_duration_seconds_5m` 回到阈值以下
- `agent_sse_replay_batch_total` 连续可采集

## 7. 发布与回滚

1. 修改规则并评审。
2. 执行校验：
   - `promtool check rules docs/dev-ops/observability/prometheus/sse-alert-rules.yml`
   - `promtool test rules docs/dev-ops/observability/prometheus/sse-alert-rules.test.yml`
3. Prometheus 加载规则后，观察至少一个采集周期。
4. 若误报严重，先回滚 `sse-alert-rules.yml` 并记录阈值调整依据。
