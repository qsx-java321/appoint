#!/usr/bin/env bash
# 重置 test 环境并启动前后端。重置会删除 test 业务数据。
set -euo pipefail
cd "$(dirname "$0")/../.."

if [[ "${1:-}" != "--yes" ]]; then
  read -r -p "将重置 test 环境并删除其业务数据，继续？[y/N] " answer
  [[ "$answer" == "y" || "$answer" == "Y" ]] || exit 1
fi

python3 scripts/local_env.py stop test || true
python3 scripts/local_env.py reset test
python3 scripts/local_env.py start test
curl -fsS http://127.0.0.1:8080/api/v1/local-environment
echo
