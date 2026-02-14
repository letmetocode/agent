#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8091}"
USER_ID="${USER_ID:-perf-user}"
REQUESTS="${REQUESTS:-20}"
CONCURRENCY="${CONCURRENCY:-5}"
AUTH_USERNAME="${AUTH_USERNAME:-admin}"
AUTH_PASSWORD="${AUTH_PASSWORD:-admin123}"

python3 scripts/chat_e2e_perf.py \
  --base-url "${BASE_URL}" \
  --user-id "${USER_ID}" \
  --requests "${REQUESTS}" \
  --concurrency "${CONCURRENCY}" \
  --auth-username "${AUTH_USERNAME}" \
  --auth-password "${AUTH_PASSWORD}" \
  --budget-file scripts/perf/chat_e2e_budget.json
