# dev、test、demo环境交付

交付日期：2026-11-14。配置提交`0ec8470`，启动与数据生命周期提交`19bc004`，均为本地提交。

## 设计与结果

三环境共用Compose中的真实MySQL、Redis、RabbitMQ，前后端在宿主机启动。每个环境独立配置数据库、缓存namespace、消息vhost及凭据，使用相同业务代码和迁移。

| 环境 | 前端→后端 | 数据生命周期 |
| --- | --- | --- |
| dev | 5173→8080 | 沿用appoint，保留账号及业务数据 |
| test | 5174→8081 | appoint_test，默认保留，可显式重置和调时 |
| demo | 5175→8082 | appoint_demo，每次后端启动恢复初始状态 |

## 实现入口

- 三份application profile与私密env：配置和身份来源；LocalEnvironmentPolicy：最终连接目标校验。
- scripts/local_env.py：资源准备、启动/停止、dev过渡、test重置与调时。
- LocalEnvironmentConfig/Lifecycle/Files：迁移、账号初始化、同环境锁、重置及JWT轮换；V11保存账号来源标记。
- AdjustableClock、TestClockConfig/Controller：test进程内业务调时。
- frontend/package.json、vite.config.ts：三个前端mode及对应代理。

重复初始化保留已有密码与卷；dev业务数据不复制到其他环境。demo与test重置只操作自身资源。MockPay保持内存实现，跨重启渠道查询和退款续接有限制。

[设计方案](../../../docs/environment-isolation-plan.md) · [使用说明](../../../docs/environments.md) · [任务记录](../../environment-isolation/map.md)
