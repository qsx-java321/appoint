# dev、test、demo三环境使用说明

三套环境共用Docker Compose中的真实MySQL、Redis、RabbitMQ，前后端在宿主机启动。dev用于日常业务体验，test用于独立操作业务，demo用于首次体验和演示。三环境的业务代码和规则一致，通过独立配置、身份和数据生命周期区分用途。

## 环境对应关系

| 项目 | dev | test | demo |
| --- | --- | --- | --- |
| Spring profile | dev（默认） | test | demo |
| 前端→后端 | 5173→8080 | 5174→8081 | 5175→8082 |
| MySQL数据库 | appoint（原库） | appoint_test | appoint_demo |
| Redis namespace | dev | test | demo |
| RabbitMQ vhost | /appoint-dev | /appoint-test | /appoint-demo |
| 私密配置 | .env.dev | .env.test | .env.demo |
| 数据生命周期 | 一直保留 | 默认保留，显式重置 | 每次后端启动恢复初始状态 |
| 业务时间 | 上海真实时间 | 默认实时，可运行中调时 | 上海真实时间 |

三个数据库使用同一套Flyway迁移及基础目录初始化，不复制dev已有账号、预约和支付数据。数据库账号、RabbitMQ权限和JWT密钥按环境隔离。Redis共享实例按前缀隔离，不提供独立容量配额。

电脑资源有限，使用时按需启动一套，切换前停止上一套；程序只保留同环境重复实例与重置保护，不增加跨环境互斥。

## 首次准备

已有compose.env保持原凭据；仅在文件缺失时从compose.env.example复制。项目根目录执行：

```bash
python3 scripts/local_env.py init
```

init启动共享Compose并创建缺少的数据库、账号和vhost，以及被Git忽略的.env.dev/.env.test/.env.demo。重复执行保留已有密码和业务数据；卷内凭据或权限不匹配时停止，不擅自改密码或删除数据卷。旧根.env保留，只在首次生成dev配置时读取原配置，Spring Boot不再全局导入它。

现有dev切换前先私密备份原库、停止旧应用，再执行：

```bash
python3 scripts/local_env.py cutover-dev
```

预检发现未完成资金或消息工作时中止。通过后保存本机切换标记；保留原appoint库、旧默认vhost、旧缓存及数据卷。新安装项目完成初始化后也执行一次此步骤。

Compose操作始终使用`docker compose --env-file compose.env ...`。停止共享依赖用`docker compose --env-file compose.env stop`，保留卷。

## 启动和切换

先加载Node 24，在frontend目录安装依赖：

```bash
npm ci
```

回到项目根目录，选择需要的环境：

```bash
python3 scripts/local_env.py start dev
python3 scripts/local_env.py stop dev
# 停止后可选择 start test 或 start demo
```

浏览器使用http://127.0.0.1:5173（dev）、5174（test）、5175（demo）。`status <环境>`查看脚本托管的进程；日志及进程记录位于被忽略的.local-env目录。脚本会同时启动对应前后端，stop只停止该环境的托管进程，保持Compose运行。

也可分别启动。后端从项目根目录执行：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=test
```

将test替换为dev或demo即可。IDE的工作目录设为项目根目录，选择一个Spring profile；默认dev。前端在frontend目录分别使用`npm run dev`、`npm run dev:test`、`npm run dev:demo`。

前端代理固定到同环境后端；API_PROXY_TARGET或命令行端口与环境冲突时拒绝启动。后端在连接和重置前校验最终数据库、消息、缓存与端口目标，拒绝独立Flyway/Hikari连接覆盖等会绕开目标配置的方式。

## 账号及重置

- dev不自动新增或修改已有账号。
- test补充管理员test-admin@example.local，普通重启保留业务数据和用户已修改的密码。
- demo启动后补充demo-user@example.local、demo-doctor@example.local、demo-admin@example.local，并将示例医生1绑定到医生账号。

初始化密码保存在对应私密环境文件的LOCAL_SEED_PASSWORD，不写入公开文档或Git。

demo清理仅针对自身数据库、缓存和消息；脚本、Maven、IDE入口共用Spring启动生命周期。同环境第二实例或后端端口占用时，在清理前拒绝。刷新页面或重启前端不会重置数据。每次后端启动使用新的JWT密钥，旧Token失效。

test只有显式重置才清数据。停止test后执行：

```bash
python3 scripts/local_env.py reset test
```

reset和正常启动共用同环境锁，重置后重新初始化管理员，原子轮换.env.test中的JWT密钥；旧Token失效，普通重启继续使用轮换后的密钥。IDE启动的实例也持有锁，不能只凭脚本无PID就认为可以重置。

## test业务调时

启动test后，管理员可查询、设置或恢复业务时间：

```bash
python3 scripts/local_env.py clock test get
python3 scripts/local_env.py clock test set --instant 2030-01-01T00:00:00Z
python3 scripts/local_env.py clock test reset
```

命令用LOCAL_SEED_PASSWORD登录专用管理员；若已修改管理员密码，应使用有效凭据调用[调时API](api.md#test环境业务调时)。

设置后时间继续走秒；偏移仅存当前进程，重启恢复实时。调时影响使用业务Clock的预约、支付截止、签到及相关任务，不修改系统、数据库、浏览器或JWT时间。回拨不撤销已完成事实。dev/demo不注册调时接口。

MockPay继续保存在内存，应用重启后旧渠道支付查询和退款续接存在限制。demo是本地演示环境，不代表生产部署。
