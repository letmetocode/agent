#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

PROD_SCHEMA="${ROOT_DIR}/docs/dev-ops/postgresql/sql/01_init_database.sql"
TEST_SUPPORT_FILE="${ROOT_DIR}/agent-app/src/test/java/com/getoffer/test/integration/PostgresIntegrationTestSupport.java"
LEGACY_TEST_SCHEMA="${ROOT_DIR}/agent-app/src/test/resources/sql/integration-schema.sql"
PROD_SCHEMA_RELATIVE_PATH="docs/dev-ops/postgresql/sql/01_init_database.sql"

require_file() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    echo "缺少文件: ${file}" >&2
    exit 1
  fi
}

require_file "${PROD_SCHEMA}"
require_file "${TEST_SUPPORT_FILE}"

if [[ -f "${LEGACY_TEST_SCHEMA}" ]]; then
  echo "[schema-drift] 检查失败：检测到 legacy 测试 schema 文件，请删除 ${LEGACY_TEST_SCHEMA}" >&2
  exit 1
fi

if ! grep -q "${PROD_SCHEMA_RELATIVE_PATH}" "${TEST_SUPPORT_FILE}"; then
  echo "[schema-drift] 检查失败：集成测试初始化未引用 ${PROD_SCHEMA_RELATIVE_PATH}" >&2
  exit 1
fi

if grep -q "integration-schema.sql" "${TEST_SUPPORT_FILE}"; then
  echo "[schema-drift] 检查失败：仍检测到 integration-schema.sql 旧初始化链路" >&2
  exit 1
fi

echo "[schema-drift] 检查通过：集成测试 schema 已与生产初始化脚本同源。"
