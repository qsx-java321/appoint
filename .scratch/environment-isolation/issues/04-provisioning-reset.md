# 04 资源初始化与数据生命周期

Type: enhancement
Triage: ready-for-agent
Status: resolved
Blocked by: 03

init幂等准备数据库、专用账号和vhost，保留已有密码及卷。dev沿用原库；test普通重启保留，显式reset重建；demo每次后端启动清理自身资源并初始化账号和示例医生。

脚本、Maven和IDE共用Spring生命周期，同环境锁和端口检查先于清理。test重置及demo重建使旧Token失效。

入口：scripts/local_env.py、LocalEnvironmentConfig/Lifecycle/Files、V11迁移。

## Comments

- 交付提交：`19bc004`。
