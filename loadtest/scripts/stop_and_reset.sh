#!/usr/bin/env bash
# 停止 test 并重置其数据，不重新启动。用于压测通过后的环境清理。
set -euo pipefail
cd "$(dirname "$0")/../.."

if [[ "${1:-}" != "--yes" ]]; then
  read -r -p "将停止 test 并重置其业务数据，继续？[y/N] " answer
  [[ "$answer" == "y" || "$answer" == "Y" ]] || exit 1
fi

python3 scripts/local_env.py stop test || true
python3 scripts/local_env.py reset test
echo "test 已停止并重置"
