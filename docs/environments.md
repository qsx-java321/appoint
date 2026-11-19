# 本地环境指南

首次启动见 [README](../README.md#快速启动)。在项目根目录运行以下命令。

## 环境对应关系

| 环境 | 用途 | MySQL 库 | 数据保留 |
| --- | --- | --- | --- |
| `demo` | 演示体验，默认环境 | `appoint_demo` | 每次后端启动重建演示数据，旧登录失效 |
| `dev` | 日常开发 | `appoint` | 保留 |
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

## 重置 test

**重置会删除 test 的业务数据和自行注册的账号，重建样例目录与预置账号，同时清理 test 的目录缓存、验证码和注册票据。JWT 密钥会轮换，旧登录失效。** 保持共享容器运行，执行：

```bash
python3 scripts/local_env.py stop test
python3 scripts/local_env.py reset test
```

重置完成后脚本退出。继续使用时运行 `python3 scripts/local_env.py start test`。如果共用端口有应用在运行，先停止该应用。

## test 业务调时

在 test 就绪后，从项目根目录运行：

```bash
python3 scripts/local_env.py clock test get
python3 scripts/local_env.py clock test set --instant 2026-11-20T07:20:00+08:00
python3 scripts/local_env.py clock test reset
```

设置后业务时钟继续走秒，后端重启或执行 `clock test reset` 后恢复实时。调时会影响下单、支付、过期扫描和签到，并可能使已有待支付预约到期。回拨时间保留已提交的操作结果。Redis 有效期和 JWT 使用真实时间。脚本使用预置管理员初始密码，修改过该账号密码时可使用有效管理员凭据调用[调时 API](api.md#test环境业务调时)。

## Flyway 初始化基线

后端启动时自动迁移，空库依次执行 V1、V2。已有当前 V1 的库会追加 V2。来自旧迁移链的库若与当前基线校验不符，需用兼容版本保留数据，或完成迁移后再切换。首次体验可直接使用新库。demo 启动和 test 显式重置会先清理各自的数据库。
