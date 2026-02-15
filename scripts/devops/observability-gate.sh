#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

PROM_IMAGE="${PROM_IMAGE:-prom/prometheus:v2.55.1}"
CATALOG_FILE="agent-app/src/main/resources/observability/alert-catalog.json"

RULE_FILES=(
  "docs/dev-ops/observability/prometheus/planner-alert-rules.yml"
  "docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.yml"
  "docs/dev-ops/observability/prometheus/sse-alert-rules.yml"
)

RULE_TEST_FILES=(
  "docs/dev-ops/observability/prometheus/planner-alert-rules.test.yml"
  "docs/dev-ops/observability/prometheus/executor-terminal-alert-rules.test.yml"
  "docs/dev-ops/observability/prometheus/sse-alert-rules.test.yml"
)

require_command() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "缺少依赖命令: ${cmd}" >&2
    exit 1
  fi
}

run_promtool() {
  docker run --rm \
    --entrypoint=/bin/promtool \
    -v "${ROOT_DIR}:/workspace" \
    -w /workspace \
    "${PROM_IMAGE}" \
    "$@"
}

require_command docker

for file in "${RULE_FILES[@]}" "${RULE_TEST_FILES[@]}" "${CATALOG_FILE}"; do
  if [[ ! -f "${ROOT_DIR}/${file}" ]]; then
    echo "缺少文件: ${file}" >&2
    exit 1
  fi
done

echo "[observability-gate] 校验告警目录中不存在 TODO dashboard 占位"
if grep -nE '"dashboard"[[:space:]]*:[[:space:]]*"TODO:' "${ROOT_DIR}/${CATALOG_FILE}" >/dev/null; then
  grep -nE '"dashboard"[[:space:]]*:[[:space:]]*"TODO:' "${ROOT_DIR}/${CATALOG_FILE}" >&2
  echo "检测到 TODO dashboard 占位，请替换后再提交。" >&2
  exit 1
fi

echo "[observability-gate] promtool check rules"
for file in "${RULE_FILES[@]}"; do
  run_promtool check rules "${file}"
done

echo "[observability-gate] promtool test rules"
for file in "${RULE_TEST_FILES[@]}"; do
  run_promtool test rules "${file}"
done

echo "[observability-gate] 所有检查通过"
