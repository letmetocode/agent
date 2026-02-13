# Planner 告警处置手册（Runbook）

## 1. 适用范围

- 服务：`agent-app`
- 模块：`planner`
- 规则文件：`docs/dev-ops/observability/prometheus/planner-alert-rules.yml`

## 2. 告警定义

### 2.1 绝对值告警（Spike）

- `PlannerFallbackSpikeWarningStaging`：预发 5 分钟 fallback 增量 > 10 且持续 10 分钟。
- `PlannerFallbackSpikeWarningProd`：生产 5 分钟 fallback 增量 > 20 且持续 5 分钟。

### 2.2 比例告警（Ratio）

- `PlannerFallbackRatioCriticalStaging`：预发 fallback 比例 > 0.40 且持续 10 分钟。
- `PlannerFallbackRatioCriticalProd`：生产 fallback 比例 > 0.30 且持续 5 分钟。

### 2.3 指标断流告警（Missing）

- `PlannerRouteMetricMissingStaging` / `PlannerRouteMetricMissingProd`：15 分钟采集不到 `agent_planner_route_total`。

## 3. 分级与通知

- `warning`：发送到项目告警群，值班同学 15 分钟内确认。
- `critical`：发送到值班升级通道（电话/短信/IM 强提醒），5 分钟内响应。

## 4. 5 分钟内排障步骤

1. 在监控面板确认 `route/fallback/ratio` 曲线是否真实异常。
2. 检查最近 30 分钟 `ROUTING_DECIDED` 日志中 `fallbackReason/sourceType/plannerAttempts` 分布。
3. 检查 root Agent 可用性：
   - `agent_registry` 中 `root` 是否激活
   - root 模型供应商可达性、限流与错误率
4. 检查近期变更：
   - 应用发布
   - `planner.root.*` 配置变更
   - 数据库或网络异常

## 5. 临时止损动作

1. 回滚最近一次变更（配置优先，必要时版本回滚）。
2. 若 root 异常持续：
   - 修复 root 可用性（模型配置/密钥/网络）
   - 必要时切换到稳定模型配置
3. 如果告警由指标断流触发：
   - 检查 Prometheus 抓取状态与 target 标签（`env`）
   - 检查 `/actuator/prometheus` 是否暴露异常

## 6. 恢复判定

满足以下条件连续 30 分钟可视为恢复：

- `agent:planner_fallback_total_5m` 回到阈值以下
- `agent:planner_fallback_ratio_5m` 回到阈值以下
- `agent_planner_route_total` 持续可采集

## 7. 发布与回滚流程

1. 修改规则文件并评审。
2. Prometheus 加载规则并热更新（`/-/reload` 或滚动重启）。
3. 观察 1 个采集周期确认规则状态。
4. 若误报严重，回滚到上一版规则并记录原因。
