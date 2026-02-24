#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  bash scripts/devops/cloud-deploy.sh [选项]

选项:
  --env-file <path>      指定环境变量文件，默认 docs/dev-ops/.env.cloud
  --with-ops-ui          同时启动 pgAdmin / Redis Commander（默认关闭）
  --no-build             不在服务器构建镜像，直接使用 APP_IMAGE 启动
  --pull                 启动前显式 docker pull APP_IMAGE（仅 --no-build 模式）
  --app-image <image>    覆盖 APP_IMAGE（例如 registry.example.com/agent/app:20260224）
  --skip-migrations      跳过数据库迁移（默认执行）
  --wait-seconds <n>     健康检查超时时间（秒），默认 180
  -h, --help             显示帮助

示例:
  # 服务器本地源码构建 + 部署
  bash scripts/devops/cloud-deploy.sh

  # 使用制品仓镜像部署（推荐线上）
  bash scripts/devops/cloud-deploy.sh --no-build --pull \
    --app-image registry.cn-hangzhou.aliyuncs.com/system/agent-app:1.0
EOF
}

WITH_OPS_UI=0
NO_BUILD=0
PULL_IMAGE=0
SKIP_MIGRATIONS=0
WAIT_SECONDS=180
APP_IMAGE_OVERRIDE=""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DEVOPS_DIR="${ROOT_DIR}/docs/dev-ops"
ENV_FILE="${DEVOPS_DIR}/.env.cloud"
ENV_TEMPLATE_FILE="${DEVOPS_DIR}/.env.example"
ENV_COMPOSE_FILE="${DEVOPS_DIR}/docker-compose-environment.yml"
APP_COMPOSE_FILE="${DEVOPS_DIR}/docker-compose-app.yml"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --with-ops-ui)
      WITH_OPS_UI=1
      shift
      ;;
    --no-build)
      NO_BUILD=1
      shift
      ;;
    --pull)
      PULL_IMAGE=1
      shift
      ;;
    --app-image)
      APP_IMAGE_OVERRIDE="$2"
      shift 2
      ;;
    --skip-migrations)
      SKIP_MIGRATIONS=1
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
  local value
  value="$(awk -F= -v key="${key}" '$1==key{print substr($0, index($0, "=") + 1)}' "${ENV_FILE}" | tail -n 1)"
  echo "${value}"
}

ensure_env_non_empty() {
  local key="$1"
  local value
  value="$(read_env_value "${key}")"
  if [[ -z "${value}" ]]; then
    echo "缺少必填配置 ${key}，请在 ${ENV_FILE} 中设置后重试。" >&2
    exit 1
  fi
}

compose_cmd() {
  local -a args=(
    docker compose
    --env-file "${ENV_FILE}"
    -f "${ENV_COMPOSE_FILE}"
    -f "${APP_COMPOSE_FILE}"
  )
  if [[ -n "${APP_IMAGE_OVERRIDE}" ]]; then
    env APP_IMAGE="${APP_IMAGE_OVERRIDE}" "${args[@]}" "$@"
  else
    "${args[@]}" "$@"
  fi
}

wait_for_agent_healthy() {
  local deadline=$((SECONDS + WAIT_SECONDS))
  while (( SECONDS < deadline )); do
    local cid
    cid="$(compose_cmd ps -q agent 2>/dev/null || true)"
    if [[ -z "${cid}" ]]; then
      sleep 2
      continue
    fi

    local state health
    state="$(docker inspect --format '{{.State.Status}}' "${cid}" 2>/dev/null || true)"
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "${cid}" 2>/dev/null || true)"

    if [[ "${health}" == "healthy" ]]; then
      return 0
    fi

    if [[ "${health}" == "no-healthcheck" && "${state}" == "running" ]]; then
      return 0
    fi

    if [[ "${state}" == "exited" || "${state}" == "dead" ]]; then
      echo "agent 容器异常退出，状态=${state}" >&2
      return 1
    fi

    echo "等待 agent 健康检查通过... state=${state}, health=${health}"
    sleep 3
  done

  echo "等待超时（${WAIT_SECONDS}s），agent 未达到健康状态。" >&2
  return 1
}

require_command docker
require_command awk

if ! docker compose version >/dev/null 2>&1; then
  echo "当前环境不可用 docker compose，请先安装 Docker Compose v2。" >&2
  exit 1
fi

if [[ ! -f "${ENV_COMPOSE_FILE}" || ! -f "${APP_COMPOSE_FILE}" ]]; then
  echo "缺少 compose 文件，请确认仓库完整。" >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  if [[ ! -f "${ENV_TEMPLATE_FILE}" ]]; then
    echo "缺少环境模板文件: ${ENV_TEMPLATE_FILE}" >&2
    exit 1
  fi
  cp "${ENV_TEMPLATE_FILE}" "${ENV_FILE}"
  echo "未检测到 ${ENV_FILE}，已从模板创建。请先修改密钥与数据库配置后重试。" >&2
  exit 1
fi

if grep -q "please-change-this" "${ENV_FILE}"; then
  echo "检测到 ${ENV_FILE} 仍包含默认密钥 please-change-this，请先修改。" >&2
  exit 1
fi

ensure_env_non_empty POSTGRES_PASSWORD
ensure_env_non_empty DB_PASSWORD
ensure_env_non_empty APP_SHARE_TOKEN_SALT
ensure_env_non_empty APP_AUTH_LOCAL_PASSWORD
ensure_env_non_empty APP_AUTH_JWT_SECRET
ensure_env_non_empty OPENAI_API_KEY

if ! [[ "${WAIT_SECONDS}" =~ ^[0-9]+$ ]] || [[ "${WAIT_SECONDS}" -le 0 ]]; then
  echo "--wait-seconds 必须是正整数，当前=${WAIT_SECONDS}" >&2
  exit 1
fi

echo "启动基础依赖服务: postgres, redis"
compose_cmd up -d postgres redis

if [[ "${SKIP_MIGRATIONS}" -eq 0 ]]; then
  echo "执行数据库迁移"
  bash "${SCRIPT_DIR}/postgres-migrate.sh" \
    --env-file "${ENV_FILE}" \
    --compose-file "${ENV_COMPOSE_FILE}" \
    --skip-postgres-up
fi

if [[ "${WITH_OPS_UI}" -eq 1 ]]; then
  echo "启动运维 UI: pgadmin, redis-admin"
  compose_cmd --profile ops-ui up -d pgadmin redis-admin
fi

if [[ "${NO_BUILD}" -eq 1 ]]; then
  if [[ "${PULL_IMAGE}" -eq 1 ]]; then
    image_to_pull="${APP_IMAGE_OVERRIDE}"
    if [[ -z "${image_to_pull}" ]]; then
      image_to_pull="$(awk -F= '$1=="APP_IMAGE" {print $2}' "${ENV_FILE}" | tail -n 1)"
    fi
    if [[ -z "${image_to_pull}" ]]; then
      echo "未找到 APP_IMAGE，请通过 --app-image 指定或在 ${ENV_FILE} 中配置 APP_IMAGE。" >&2
      exit 1
    fi
    echo "拉取镜像: ${image_to_pull}"
    docker pull "${image_to_pull}"
  fi
  echo "部署 agent（使用镜像，不构建）"
  compose_cmd up -d --no-build agent
else
  echo "部署 agent（服务器源码构建）"
  compose_cmd up -d --build agent
fi

if ! wait_for_agent_healthy; then
  echo "部署失败，输出最近容器日志：" >&2
  compose_cmd logs --tail=200 agent >&2 || true
  exit 1
fi

echo
echo "部署完成。"
echo "服务状态："
compose_cmd ps
echo
app_host_port="$(awk -F= '$1=="APP_HOST_PORT" {print $2}' "${ENV_FILE}" | tail -n 1 | sed '/^$/d')"
if [[ -z "${app_host_port}" ]]; then
  app_host_port="8091"
fi
echo "健康检查地址："
echo "  http://127.0.0.1:${app_host_port}/actuator/health"
