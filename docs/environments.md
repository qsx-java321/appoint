# 本地环境指南

首次启动见 [README](../README.md#快速启动)。在项目根目录运行以下命令。

## 环境对应关系

| 环境 | 用途 | MySQL 库 | 数据保留 |
| --- | --- | --- | --- |
| `demo` | 演示体验，默认环境 | `appoint_demo` | 每次后端启动重建演示数据，旧登录失效 |
| `dev` | 日常开发 | `appoint` | 普通重启保留，显式重置才清空 |
| `test` | 手动验收和业务调时 | `appoint_test` | 普通重启保留，显式重置才清空 |

三套环境共用 MySQL、Redis、RabbitMQ 和 Mailpit 容器，各使用独立的数据库账号、Redis 命名空间与 RabbitMQ vhost。前端 [5173](http://127.0.0.1:5173)、后端 8080 共用，一次运行一套。注册验证码在 [Mailpit](http://127.0.0.1:8025) 按 `[dev]`、`[test]` 或 `[demo]` 标题前缀和收件人查找。

`init` 创建缺失的 `compose.env` 与 `.env.dev`、`.env.test`、`.env.demo`，启动容器并准备数据库账号和 RabbitMQ 权限。重复执行沿用已有配置和数据卷。已有数据卷使用初始化时的凭据，修改配置文件不会改写卷内账号。

## 启动和切换

例如从 demo 切到 test：

```bash
python3 scripts/local_env.py stop demo
python3 scripts/local_env.py start test
python3 scripts/local_env.py status test
```

脚本把前后端日志写入 `.local-env/<环境>-backend.log` 和 `.local-env/<环境>-frontend.log`。首次启动后端可能需要下载 Maven 依赖，脚本最多等待 10 分钟。`status` 返回 `{}` 时没有脚本托管的运行进程，通过 IDE 或 npm 单独启动的应用需从原入口停止。

停止脚本托管的前后端后，可按需停止或恢复共享容器。Compose 的 `stop` 保留数据卷。

```bash
docker compose --env-file compose.env ps -a
docker compose --env-file compose.env stop
docker compose --env-file compose.env up -d --wait
```

单独用 IDE、Maven 或 JAR 启动后端时，工作目录为项目根目录，默认使用 demo。可通过 `SPRING_PROFILES_ACTIVE` 或启动参数选择环境。单独启动前端见[前端说明](../frontend/README.md#开发与构建)。

## 预置目录和账号

首次初始化提供 2 家医院、6 个科室和 18 名医生。

| 角色 | 登录邮箱 |
| --- | --- |
| 管理员 | `<环境>-admin@example.local` |
| 医生 | `<环境>-doctor-001@example.local` 至 `<环境>-doctor-018@example.local` |
| 演示用户 | `demo-user@example.local` |

初始密码取自对应 `.env.<环境>` 的 `LOCAL_SEED_PASSWORD`。dev/test 普通启动保留已有账号密码。使用管理员登录后，在「管理工作台 → 医生账号查询」查看实际的医生账号和绑定状态。dev/test 普通用户可以自行注册。

## 重置环境

**重置会删除所选环境的业务数据和自行注册的账号，重建样例目录与预置账号，同时清理该环境的目录缓存、注册验证数据、预约令牌和通知队列。JWT 密钥会轮换，旧登录失效。** 先停止当前前后端并保持共享容器运行，再按环境执行对应的命令。

```bash
python3 scripts/local_env.py reset dev
python3 scripts/local_env.py reset test
```

`reset` 完成后脚本退出。继续使用时运行对应的 `start dev` 或 `start test`。重建 `demo` 时运行 `python3 scripts/local_env.py start demo`，需要结束演示时执行 `python3 scripts/local_env.py stop demo`。通知从重置后的新预约开始生成，不补发历史预约；重置同时清空该环境的通知主队列和死信队列。Mailpit 已保存的邮件随服务保留。

## test 业务调时

在 test 就绪后，从项目根目录运行：

```bash
python3 scripts/local_env.py clock test get
python3 scripts/local_env.py clock test set --instant 2026-11-20T07:20:00+08:00
python3 scripts/local_env.py clock test reset
```

设置后业务时钟继续走秒，后端重启或执行 `clock test reset` 后恢复实时。调时会影响下单、支付、过期扫描和签到，并可能使已有待支付预约到期。回拨时间保留已提交的操作结果。Redis 有效期和 JWT 使用真实时间。脚本使用预置管理员初始密码，修改过该账号密码时可使用有效管理员凭据调用[调时 API](api.md#test环境业务调时)。

## 通知与 RabbitMQ

每套环境使用自己的 RabbitMQ vhost：

| 资源 | 名称 |
| --- | --- |
| Exchange | `appoint.notification.exchange`（topic，durable） |
| 主队列 | `appoint.notification.queue`（durable） |
| Routing key | `appointment.notification` |
| 死信 Exchange | `appoint.notification.dlx` |
| 死信队列 | `appoint.notification.dlq` |

消息持久化，发布开启 confirm 和 mandatory return。`appoint.notification.messaging-enabled=false` 时应用不声明拓扑、不启动发布器和消费者；自动化测试中的 `regression`、`mysql-it` 使用该设置。

RabbitMQ 管理界面为 [15672](http://127.0.0.1:15672)，使用 `compose.env` 中的管理员凭据，选择对应环境 vhost 查看队列、未确认消息和死信。`init` 会授予该管理员访问各环境 vhost 的权限，环境业务账号仍保持 vhost 隔离。重复投递或消费失败排查时可先看 `appoint.notification.dlq`。

`rabbit-it` 是独立测试档位，使用 Testcontainers 启动临时 MySQL、Redis 和 RabbitMQ，验证 Outbox → 发布 → 消费 → 通知表、重复投递幂等和无效消息重试后进入死信队列。运行命令见 [README 自动化测试](../README.md#自动化测试)。

## Flyway 初始化基线

后端启动时自动迁移，空库依次执行 V1、V2、V3。V1 是当前业务基线；V2 追加样例医疗目录；V3 只新增 `notification_outbox_events` 和 `user_notifications`，不修改已有业务表。修改 V1 后，已有 dev/test 库需通过[重置环境](#重置环境)重建，demo 每次启动自动重建。当前迁移链不再兼容旧基线的业务数据。
