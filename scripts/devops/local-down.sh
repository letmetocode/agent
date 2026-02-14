#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  bash scripts/devops/local-down.sh [--volumes]

参数:
  --volumes   停机时同时删除数据卷（慎用）
EOF
}

REMOVE_VOLUMES=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --volumes)
      REMOVE_VOLUMES=1
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

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DEVOPS_DIR="${ROOT_DIR}/docs/dev-ops"
ENV_FILE="${DEVOPS_DIR}/.env"
ENV_COMPOSE_FILE="${DEVOPS_DIR}/docker-compose-environment.yml"
APP_COMPOSE_FILE="${DEVOPS_DIR}/docker-compose-app.yml"

if ! command -v docker >/dev/null 2>&1; then
  echo "缺少依赖命令: docker" >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "当前环境不可用 docker compose，请先安装 Docker Compose v2。" >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "未检测到 ${ENV_FILE}，使用模板默认值尝试停机。"
  cp "${DEVOPS_DIR}/.env.example" "${ENV_FILE}"
fi

compose_cmd=(docker compose --env-file "${ENV_FILE}" -f "${ENV_COMPOSE_FILE}" -f "${APP_COMPOSE_FILE}")
down_args=(down --remove-orphans)

if [[ "${REMOVE_VOLUMES}" -eq 1 ]]; then
  down_args+=(--volumes)
fi

"${compose_cmd[@]}" "${down_args[@]}"

echo "已停止本地 compose 资源。"
