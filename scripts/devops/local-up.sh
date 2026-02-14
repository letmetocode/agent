#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  bash scripts/devops/local-up.sh [--with-app] [--with-ops-ui] [--no-build]

参数:
  --with-app      同时启动 agent 应用容器（默认仅启动依赖）
  --with-ops-ui   启动 pgAdmin / Redis Commander（默认关闭，减少暴露面）
  --no-build      启动应用容器时跳过镜像构建
EOF
}

WITH_APP=0
WITH_OPS_UI=0
NO_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-app)
      WITH_APP=1
      shift
      ;;
    --with-ops-ui)
      WITH_OPS_UI=1
      shift
      ;;
    --no-build)
      NO_BUILD=1
      shift
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

random_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 16
  else
    LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32
  fi
}

set_env_value() {
  local key="$1"
  local value="$2"
  local file="$3"
  local tmp_file
  tmp_file="$(mktemp)"

  awk -v key="${key}" -v value="${value}" -F= '
    BEGIN { updated = 0 }
    $1 == key {
      print key "=" value
      updated = 1
      next
    }
    { print $0 }
    END {
      if (updated == 0) {
        print key "=" value
      }
    }
  ' "${file}" > "${tmp_file}"

  mv "${tmp_file}" "${file}"
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DEVOPS_DIR="${ROOT_DIR}/docs/dev-ops"
ENV_FILE="${DEVOPS_DIR}/.env"
ENV_EXAMPLE_FILE="${DEVOPS_DIR}/.env.example"
ENV_COMPOSE_FILE="${DEVOPS_DIR}/docker-compose-environment.yml"
APP_COMPOSE_FILE="${DEVOPS_DIR}/docker-compose-app.yml"

require_command docker

if ! docker compose version >/dev/null 2>&1; then
  echo "当前环境不可用 docker compose，请先安装 Docker Compose v2。" >&2
  exit 1
fi

if [[ ! -f "${ENV_EXAMPLE_FILE}" ]]; then
  echo "缺少环境模板文件: ${ENV_EXAMPLE_FILE}" >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  cp "${ENV_EXAMPLE_FILE}" "${ENV_FILE}"
  echo "未检测到 ${ENV_FILE}，已从模板生成。"

  db_password="$(random_secret)"
  set_env_value POSTGRES_PASSWORD "${db_password}" "${ENV_FILE}"
  set_env_value DB_PASSWORD "${db_password}" "${ENV_FILE}"
  set_env_value PGADMIN_DEFAULT_PASSWORD "$(random_secret)" "${ENV_FILE}"
  set_env_value REDIS_PASSWORD "$(random_secret)" "${ENV_FILE}"
  set_env_value REDIS_COMMANDER_PASSWORD "$(random_secret)" "${ENV_FILE}"
  set_env_value APP_SHARE_TOKEN_SALT "$(random_secret)" "${ENV_FILE}"
  echo "已自动写入随机初始密钥，请按需手动调整 ${ENV_FILE}。"
fi

if grep -q "please-change-this" "${ENV_FILE}"; then
  echo "检测到未替换默认密钥（please-change-this），请先更新 ${ENV_FILE}。" >&2
  exit 1
fi

compose_base=(docker compose --env-file "${ENV_FILE}" -f "${ENV_COMPOSE_FILE}")

echo "启动基础依赖: postgres, redis"
"${compose_base[@]}" up -d postgres redis

if [[ "${WITH_OPS_UI}" -eq 1 ]]; then
  echo "启动运维 UI: pgadmin, redis-admin"
  "${compose_base[@]}" --profile ops-ui up -d pgadmin redis-admin
fi

if [[ "${WITH_APP}" -eq 1 ]]; then
  compose_with_app=(docker compose --env-file "${ENV_FILE}" -f "${ENV_COMPOSE_FILE}" -f "${APP_COMPOSE_FILE}")
  if [[ "${NO_BUILD}" -eq 1 ]]; then
    echo "启动应用容器（跳过构建）: agent"
    "${compose_with_app[@]}" up -d agent
  else
    echo "构建并启动应用容器: agent"
    "${compose_with_app[@]}" up -d --build agent
  fi
fi

echo "启动完成。"
echo "健康检查: docker compose --env-file ${ENV_FILE} -f ${ENV_COMPOSE_FILE} ps"
