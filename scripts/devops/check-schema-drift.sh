#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

PROD_SCHEMA="${ROOT_DIR}/docs/dev-ops/postgresql/sql/01_init_database.sql"
TEST_SCHEMA="${ROOT_DIR}/agent-app/src/test/resources/sql/integration-schema.sql"

require_file() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    echo "缺少文件: ${file}" >&2
    exit 1
  fi
}

extract_names() {
  local kind="$1"
  local file="$2"
  grep -Eio "CREATE[[:space:]]+${kind}[[:space:]]+(IF[[:space:]]+NOT[[:space:]]+EXISTS[[:space:]]+)?[a-zA-Z_][a-zA-Z0-9_]*" "${file}" \
    | awk '{print tolower($NF)}' \
    | sort -u
}

print_diff() {
  local label="$1"
  local left_file="$2"
  local right_file="$3"

  local only_left
  local only_right
  only_left="$(comm -23 "${left_file}" "${right_file}" || true)"
  only_right="$(comm -13 "${left_file}" "${right_file}" || true)"

  if [[ -n "${only_left}" || -n "${only_right}" ]]; then
    echo "[schema-drift] ${label} 存在漂移:" >&2
    if [[ -n "${only_left}" ]]; then
      echo "  仅在 01_init_database.sql 中存在:" >&2
      while IFS= read -r line; do
        [[ -n "${line}" ]] && echo "    - ${line}" >&2
      done <<< "${only_left}"
    fi
    if [[ -n "${only_right}" ]]; then
      echo "  仅在 integration-schema.sql 中存在:" >&2
      while IFS= read -r line; do
        [[ -n "${line}" ]] && echo "    - ${line}" >&2
      done <<< "${only_right}"
    fi
    return 1
  fi
  return 0
}

require_file "${PROD_SCHEMA}"
require_file "${TEST_SCHEMA}"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

prod_types="${tmp_dir}/prod_types.txt"
test_types="${tmp_dir}/test_types.txt"
prod_tables="${tmp_dir}/prod_tables.txt"
test_tables="${tmp_dir}/test_tables.txt"

extract_names "TYPE" "${PROD_SCHEMA}" > "${prod_types}"
extract_names "TYPE" "${TEST_SCHEMA}" > "${test_types}"
extract_names "TABLE" "${PROD_SCHEMA}" > "${prod_tables}"
extract_names "TABLE" "${TEST_SCHEMA}" > "${test_tables}"

failed=0
print_diff "CREATE TYPE" "${prod_types}" "${test_types}" || failed=1
print_diff "CREATE TABLE" "${prod_tables}" "${test_tables}" || failed=1

if [[ "${failed}" -ne 0 ]]; then
  echo "[schema-drift] 检查失败，请同步 SQL 基线与集成测试 schema。" >&2
  exit 1
fi

echo "[schema-drift] 检查通过：CREATE TYPE / CREATE TABLE 无漂移。"
