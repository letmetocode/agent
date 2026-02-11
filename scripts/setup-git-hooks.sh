#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

chmod +x .githooks/commit-msg .githooks/pre-commit

git config core.hooksPath .githooks

echo "[ok] 已设置 core.hooksPath=.githooks"
echo "[ok] 本地提交将启用 commit-msg 与 pre-commit 校验"
