#!/usr/bin/env bash
# 采集一次容器和 MySQL 状态快照；建议在压测前后执行，结果写入 loadtest/results/。
set -euo pipefail
cd "$(dirname "$0")/../.."

SCENARIO=""
PHASE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --scenario)
      SCENARIO="${2:-}"; shift 2 ;;
    --phase)
      PHASE="${2:-}"; shift 2 ;;
    *)
      echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

PREFIX=""
if [[ -n "$SCENARIO" || -n "$PHASE" ]]; then
  PREFIX="${SCENARIO:-scenario}-${PHASE:-snapshot}-"
fi

set -a
. ./.env.test
set +a

mkdir -p loadtest/results
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}' \
  > "loadtest/results/${PREFIX}docker-stats.txt"
docker compose --env-file compose.env exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" \
  mysql mysql -u"$MYSQL_USER" appoint_test -N -B -e "
    SHOW GLOBAL STATUS WHERE Variable_name IN (
      'Threads_connected','Threads_running','Com_select','Com_insert','Com_update',
      'Slow_queries','Innodb_row_lock_current_waits','Innodb_row_lock_waits',
      'Innodb_row_lock_time_avg','Innodb_row_lock_time_max'
    );" > "loadtest/results/${PREFIX}mysql-status.txt"
echo "已写入 loadtest/results/${PREFIX}docker-stats.txt 和 ${PREFIX}mysql-status.txt"
