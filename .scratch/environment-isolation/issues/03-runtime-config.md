# 03 环境配置与目标校验

Type: enhancement
Triage: ready-for-agent
Status: resolved
Blocked by: 01

提供dev/test/demo三个Spring profile和独立私密env来源，默认dev。固定数据库、端口、Redis namespace及RabbitMQ vhost映射，在连接和清理前校验最终目标，避免Shell或IDE配置串环境。

入口：application-{dev,test,demo}.yml、LocalEnvironmentPolicy。

## Comments

- 交付提交：`0ec8470`。
