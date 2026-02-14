# Observability 运维入口

## 目录说明

- Planner 规则：`docs/dev-ops/observability/prometheus/planner-alert-rules.yml`
- Planner 规则测试样例：`docs/dev-ops/observability/prometheus/planner-alert-rules.test.yml`
- Planner 告警处置手册：`docs/dev-ops/observability/planner-alert-runbook.md`
- Executor/Terminal 规则：`docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.yml`
- Executor/Terminal 规则测试样例：`docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.test.yml`
- Executor/Terminal 告警处置手册：`docs/dev-ops/observability/executor-terminal-alert-runbook.md`
- SSE 规则：`docs/dev-ops/observability/prometheus/sse-alert-rules.yml`
- SSE 规则测试样例：`docs/dev-ops/observability/prometheus/sse-alert-rules.test.yml`
- SSE 告警处置手册：`docs/dev-ops/observability/sse-alert-runbook.md`
- 告警目录配置：`agent-app/src/main/resources/observability/alert-catalog.json`

## 快速校验

```bash
promtool check rules docs/dev-ops/observability/prometheus/planner-alert-rules.yml
promtool test rules docs/dev-ops/observability/prometheus/planner-alert-rules.test.yml
promtool check rules docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.yml
promtool test rules docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.test.yml
promtool check rules docs/dev-ops/observability/prometheus/sse-alert-rules.yml
promtool test rules docs/dev-ops/observability/prometheus/sse-alert-rules.test.yml
```

## 发布建议

1. 将规则文件加入 Prometheus `rule_files`。
2. 通过 `/-/reload` 或滚动重启使规则生效。
3. 在预发观察至少 24 小时后再扩大到生产。

## 告警目录链接巡检

- 巡检开关：`observability.alert-catalog.link-check.enabled`
- 巡检间隔：`observability.alert-catalog.link-check.interval-ms`
- HTTP 超时：`observability.alert-catalog.link-check.http-timeout-ms`
- 快照历史：`observability.alert-catalog.link-check.history-size`
- 趋势阈值：`observability.alert-catalog.link-check.trend-delta-threshold`
- 日志输出上限：`observability.alert-catalog.link-check.max-issue-log-count`

开启后后台会定时探测 `alert-catalog.json` 中每条告警的 `dashboard/runbook`，并按摘要日志输出失败项。

可视化读取接口：`GET /api/observability/alerts/probe-status`（支持 `window` 参数返回最近 N 次快照；监控总览页会展示最新巡检快照）。
