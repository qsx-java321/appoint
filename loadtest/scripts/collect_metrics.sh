#!/usr/bin/env bash
# 采集一次容器和 MySQL 状态快照；建议在压测结束后执行，结果写入 loadtest/results/。
set -euo pipefail
cd "$(dirname "$0")/../.."
set -a
. ./.env.test
set +a

mkdir -p loadtest/results
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}' \
  > loadtest/results/docker-stats.txt
docker compose --env-file compose.env exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" \
  mysql mysql -u"$MYSQL_USER" appoint_test -N -B -e "
    SHOW GLOBAL STATUS WHERE Variable_name IN (
      'Threads_connected','Threads_running','Com_select',
      'Innodb_row_lock_current_waits','Innodb_row_lock_waits','Innodb_row_lock_time_avg'
    );" > loadtest/results/mysql-status.txt
echo "已写入 loadtest/results/docker-stats.txt 和 loadtest/results/mysql-status.txt"
