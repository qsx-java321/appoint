# 05 test业务时间

Type: enhancement
Triage: ready-for-agent
Status: resolved
Blocked by: 04

仅test向管理员提供业务时间查询、设置及恢复。设置后继续走秒，偏移仅存当前进程，重启恢复实时；dev/demo使用上海真实时间。

业务Clock影响预约、支付截止、签到及相关任务；系统、数据库、浏览器、JWT时间不变，回拨不撤销已完成事实。

入口：AdjustableClock、TestClockConfig/Controller、local_env.py clock test。

## Comments

- 交付提交：`19bc004`。
