#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  bash scripts/devops/postgres-migrate.sh [选项]

选项:
  --env-file <path>         指定环境变量文件，默认 docs/dev-ops/.env
  --compose-file <path>     指定环境 compose 文件，默认 docs/dev-ops/docker-compose-environment.yml
  --migrations-dir <path>   指定迁移目录，默认 docs/dev-ops/postgresql/sql/migrations
  --skip-postgres-up        跳过 `docker compose up -d postgres`
  --wait-seconds <n>        等待 postgres 就绪超时秒数，默认 120
  -h, --help                显示帮助

示例:
  bash scripts/devops/postgres-migrate.sh --env-file docs/dev-ops/.env.cloud
EOF
}

SKIP_POSTGRES_UP=0
WAIT_SECONDS=120

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DEVOPS_DIR="${ROOT_DIR}/docs/dev-ops"

ENV_FILE="${DEVOPS_DIR}/.env"
ENV_COMPOSE_FILE="${DEVOPS_DIR}/docker-compose-environment.yml"
MIGRATIONS_DIR="${DEVOPS_DIR}/postgresql/sql/migrations"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --compose-file)
      ENV_COMPOSE_FILE="$2"
      shift 2
      ;;
    --migrations-dir)
      MIGRATIONS_DIR="$2"
      shift 2
      ;;
    --skip-postgres-up)
      SKIP_POSTGRES_UP=1
      shift
      ;;
    --wait-seconds)
      WAIT_SECONDS="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "未知参数: $1" >&2
      usage
      exit 1
      ;;
  esac
done

require_command() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "缺少依赖命令: ${cmd}" >&2
    exit 1
  fi
}

read_env_value() {
  local key="$1"
  local default_value="${2:-}"
  local value
  value="$(awk -F= -v key="${key}" '$1==key{print substr($0, index($0, "=") + 1)}' "${ENV_FILE}" | tail -n 1)"
  if [[ -z "${value}" ]]; then
    echo "${default_value}"
  else
    echo "${value}"
  fi
}

wait_for_postgres() {
  local deadline=$((SECONDS + WAIT_SECONDS))
  while (( SECONDS < deadline )); do
    if "${COMPOSE_ENV_CMD[@]}" exec -T postgres pg_isready -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

require_command docker
require_command awk
require_command find
require_command sort

if ! docker compose version >/dev/null 2>&1; then
  echo "当前环境不可用 docker compose，请先安装 Docker Compose v2。" >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "缺少环境变量文件: ${ENV_FILE}" >&2
  exit 1
fi

if [[ ! -f "${ENV_COMPOSE_FILE}" ]]; then
  echo "缺少 compose 文件: ${ENV_COMPOSE_FILE}" >&2
  exit 1
fi

if [[ ! -d "${MIGRATIONS_DIR}" ]]; then
  echo "缺少迁移目录: ${MIGRATIONS_DIR}" >&2
  exit 1
fi

if ! [[ "${WAIT_SECONDS}" =~ ^[0-9]+$ ]] || [[ "${WAIT_SECONDS}" -le 0 ]]; then
  echo "--wait-seconds 必须是正整数，当前=${WAIT_SECONDS}" >&2
  exit 1
fi

POSTGRES_DB="$(read_env_value POSTGRES_DB agent_db)"
POSTGRES_USER="$(read_env_value POSTGRES_USER agent)"

COMPOSE_ENV_CMD=(
  docker compose
  --env-file "${ENV_FILE}"
  -f "${ENV_COMPOSE_FILE}"
)

if [[ "${SKIP_POSTGRES_UP}" -eq 0 ]]; then
  echo "启动/确保 postgres 服务可用"
  "${COMPOSE_ENV_CMD[@]}" up -d postgres
fi

echo "等待 postgres 就绪..."
if ! wait_for_postgres; then
  echo "等待 postgres 就绪超时（${WAIT_SECONDS}s）。" >&2
  exit 1
fi

mapfile -t migration_files < <(find "${MIGRATIONS_DIR}" -maxdepth 1 -type f -name 'V*.sql' | sort)

if [[ "${#migration_files[@]}" -eq 0 ]]; then
  echo "未发现迁移文件，跳过。"
  exit 0
fi

echo "开始执行数据库迁移..."
for migration in "${migration_files[@]}"; do
  if [[ "${migration}" == *_rollback.sql ]]; then
    continue
  fi
  echo "  -> $(basename "${migration}")"
  "${COMPOSE_ENV_CMD[@]}" exec -T postgres \
    psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -f - < "${migration}"
done

echo "数据库迁移完成。"
