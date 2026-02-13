# Observability 运维入口

## 目录说明

- Planner 规则：`docs/dev-ops/observability/prometheus/planner-alert-rules.yml`
- Planner 规则测试样例：`docs/dev-ops/observability/prometheus/planner-alert-rules.test.yml`
- Planner 告警处置手册：`docs/dev-ops/observability/planner-alert-runbook.md`
- Executor/Terminal 规则：`docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.yml`
- Executor/Terminal 规则测试样例：`docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.test.yml`
- Executor/Terminal 告警处置手册：`docs/dev-ops/observability/executor-terminal-alert-runbook.md`

## 快速校验

```bash
promtool check rules docs/dev-ops/observability/prometheus/planner-alert-rules.yml
promtool test rules docs/dev-ops/observability/prometheus/planner-alert-rules.test.yml
promtool check rules docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.yml
promtool test rules docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.test.yml
```

## 发布建议

1. 将规则文件加入 Prometheus `rule_files`。
2. 通过 `/-/reload` 或滚动重启使规则生效。
3. 在预发观察至少 24 小时后再扩大到生产。
