# 本地环境指南

dev 用于日常开发，test 用于手动验收和业务调时，demo 用于初次体验。三套环境共用 Docker Compose 中的 MySQL、Redis、RabbitMQ 和 Mailpit，前后端在宿主机启动。首次运行从 [README 快速启动](../README.md#快速启动)进入。

[启停与日志](#启动和切换) · [账号及重置](#账号及重置) · [test 调时](#test业务调时) · [Mailpit](#本地注册邮件) · [Flyway 基线](#flyway初始化基线)

## 环境对应关系

| 项目 | dev | test | demo |
| --- | --- | --- | --- |
| Spring profile | dev | test | demo（默认） |
| 前端 → 后端 | `5173` → `8080` | `5173` → `8080` | `5173` → `8080` |
| MySQL 数据库 | `appoint`（原库） | `appoint_test` | `appoint_demo` |
| Redis namespace | `dev` | `test` | `demo` |
| RabbitMQ vhost | `/appoint-dev` | `/appoint-test` | `/appoint-demo` |
| Mailpit 邮件标题前缀 | `[dev]` | `[test]` | `[demo]` |
| 私密配置 | `.env.dev` | `.env.test` | `.env.demo` |
| 数据生命周期 | 一直保留 | 默认保留，显式重置 | 每次后端启动恢复初始状态 |
| 业务时间 | 上海真实时间 | 默认实时，可运行中调时 | 上海真实时间 |

- **数据隔离** 三套环境使用相同的 Flyway 迁移和基础目录，各自维护业务数据。数据库账号、RabbitMQ 权限及 JWT 密钥独立，Redis 按前缀隔离。
- **启动互斥** 脚本先检查托管进程和共用端口。脚本、Maven 和 IDE 使用同一套后端生命周期，由后端校验连接目标，并在初始化或重置前取得项目级 MySQL 运行锁。

## 首次准备

依赖和命令见 [README 快速启动](../README.md#快速启动)。WSL 中的 Java、Python 和 Node.js 应来自当前发行版。项目使用 Maven Wrapper 下载 Maven。

在项目根目录执行 `python3 scripts/local_env.py init` 时，脚本依次完成以下操作。

1. 用对应的 `.example` 模板补齐缺失的 `compose.env`、`.env.dev`、`.env.test` 和 `.env.demo`。新文件权限为 `600`，沿用模板中的配置。实际文件和模板均缺失时，脚本会在启动容器前报错。
2. 启动共享容器，准备数据库、账号和 RabbitMQ vhost。重复执行会保留已有配置和业务数据，卷内凭据或权限不匹配时脚本会停止。

业务表和预置应用账号在后端启动时初始化。安装前端依赖后即可启动所选环境，首次使用 dev 可直接运行 `python3 scripts/local_env.py start dev`。

## Compose 服务与数据卷

Compose 项目名为 `appoint`。四个共享服务使用 `appoint-network`，其镜像、端口和挂载由 [compose.yaml](../compose.yaml) 定义。

| 容器 | 镜像 | 宿主机端口 | 命名卷 | 容器挂载目录 |
| --- | --- | --- | --- | --- |
| `appoint-mysql` | `mysql:8.4` | `3306` | `appoint_appoint-mysql-data` | `/var/lib/mysql` |
| `appoint-redis` | `redis:7.4.9` | `6379` | `appoint_appoint-redis-data` | `/data` |
| `appoint-rabbitmq` | `rabbitmq:4.3.2-management` | `5672`、`15672` | `appoint_appoint-rabbitmq-data` | `/var/lib/rabbitmq` |
| `appoint-mailpit` | `axllent/mailpit:v1.31` | `127.0.0.1:1025`、`127.0.0.1:8025` | `appoint_appoint-mailpit-data` | `/data` |

Compose 操作使用 `docker compose --env-file compose.env ...`。执行 `docker compose --env-file compose.env stop` 后，容器停止并继续引用这些卷，下次启动时复用原数据。

## 启动和切换

### 日常启动与停止

完成初始化后，在项目根目录启动共享依赖和所选环境。以下以 demo 为例，`start` 也可以省略 `demo`。启动命令在前后端均通过身份和就绪检查后返回。

```bash
docker compose --env-file compose.env up -d --wait
python3 scripts/local_env.py start demo
```

停止时先关闭当前前后端。若也需要关停共享依赖，再执行 Compose 命令。

```bash
python3 scripts/local_env.py stop demo
docker compose --env-file compose.env stop
```

`stop <环境>` 只管理脚本启动的进程。通过 IDE、JAR 或 npm 单独启动的进程从原入口停止。前端依赖变更后，在项目根目录重新执行 `npm ci --prefix frontend`。

### 切换环境

一次运行一套环境。切换时先停当前前后端，共享容器可以继续运行。例如从 demo 切到 dev。

```bash
python3 scripts/local_env.py stop demo
python3 scripts/local_env.py start dev
```

启动成功后刷新原来的 [项目页面](http://127.0.0.1:5173)。从使用独立端口的旧版本升级时，先关闭旧版前后端，启动新版后刷新所有项目标签页。

### 状态和日志

下面以 demo 为例。通过参数换成 dev 或 test 可查看对应环境。

```bash
python3 scripts/local_env.py status demo
docker compose --env-file compose.env ps -a
tail -n 100 .local-env/demo-backend.log
tail -n 100 .local-env/demo-frontend.log
```

`status` 返回 `{}` 表示没有仍存活的脚本托管进程。手动启动的进程需从原入口核实。Compose 停止后容器状态应为 `Exited`。

脚本首次启动最多等待后端 10 分钟，其中包含 Maven 下载和初始化。在另一个终端可运行 `tail -f .local-env/demo-backend.log` 跟踪启动日志。按 `Ctrl+C` 可退出日志跟踪。

### 单独启动后端或前端

在项目根目录启动后端。直接运行 IDE 中的 `AppointApplication.main`、`./mvnw spring-boot:run` 或打包后的 JAR 时，默认加载 demo 和 `.env.demo`。需要切换时在 IDE、`SPRING_PROFILES_ACTIVE` 环境变量或命令行显式设置 profile。

可以用 Maven 启动 test。

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=test
```

也可以先打包，再直接启动默认的 demo。改用 test 时，在 JAR 启动命令后加 `--spring.profiles.active=test`。

```bash
./mvnw -DskipTests package
java -jar target/appoint-0.0.1-SNAPSHOT.jar
```

后端监听 `127.0.0.1:8080`。在对应后端就绪后，从 `frontend` 目录分别使用 `npm run dev`、`npm run dev:test` 或 `npm run dev:demo` 启动前端。前端验证后端的实际 profile，并从 `127.0.0.1:5173` 代理请求到 `8080`。切换后刷新旧标签页，浏览器会按环境读取登录和预约草稿。

## 账号及重置

### 预置目录和账号

三套环境使用相同的样例目录：示例市第一人民医院、示例市第二人民医院，每院设内科、骨科、皮肤科，每科室3名医生，共18名。它是首次初始化的数据规模，管理员仍可增删改目录，不是业务数量限制。

每套环境有1个管理员、18个独立医生账号，医生档案与账号一一绑定；demo另有1个普通演示用户。新建环境的初始用户总数分别为19、19、20。

| 环境 | 管理员邮箱 | 医生邮箱范围 | 普通演示用户 |
| --- | --- | --- | --- |
| dev | dev-admin@example.local | dev-doctor-001@example.local至dev-doctor-018@example.local | 无，从页面注册 |
| test | test-admin@example.local | test-doctor-001@example.local至test-doctor-018@example.local | 无，从页面注册 |
| demo | demo-admin@example.local | demo-doctor-001@example.local至demo-doctor-018@example.local | demo-user@example.local |

医生邮箱中的三位序号是稳定的样例序号，三环境对应关系相同：

| 医院 | 科室 | 医生姓名及邮箱序号 |
| --- | --- | --- |
| 示例市第一人民医院 | 内科 | 张医生001、王医生003、赵医生004 |
| 示例市第一人民医院 | 骨科 | 李医生002、刘医生005、陈医生006 |
| 示例市第一人民医院 | 皮肤科 | 杨医生007、周医生008、吴医生009 |
| 示例市第二人民医院 | 内科 | 徐医生010、孙医生011、胡医生012 |
| 示例市第二人民医院 | 骨科 | 朱医生013、高医生014、林医生015 |
| 示例市第二人民医院 | 皮肤科 | 何医生016、郭医生017、马医生018 |

例如dev第二医院皮肤科马医生使用`dev-doctor-018@example.local`登录。样例序号通过编号分配记录关联医生，不假定数据库自增ID或医生编号的数值恰好等于序号；已有人工新增目录时，这些数值可能不同。

登录当前环境管理员账号后，通过右上角“管理工作台”进入“医生账号查询”（`/admin/doctor-accounts`），可查看全部医院、科室下医生的实际登录邮箱、账号ID、绑定状态及密码说明，点击“刷新列表”读取最新关系。新增医生尚未绑定时仍显示；“首次绑定账号”进入原绑定功能（`/admin/doctor-accounts/bind`）。列表按当前数据库关系展示，管理员修改目录后可能与上方初始对应表不同。

### 初始密码与重复启动

对应环境`.env.dev`、`.env.test`或`.env.demo`中的`LOCAL_SEED_PASSWORD`，用于该环境管理员、全部预置医生及普通演示用户的首次创建。三环境均要求至少12位；配置由模板首次复制时沿用模板值，不在文档中展示密码。

dev/test启动时只补充缺失的预置账号及空医生绑定，保留已有账号密码、角色和业务数据。修改`LOCAL_SEED_PASSWORD`不会直接更新已创建账号的密码，重复`init`也不会恢复旧密码。遇到同邮箱非预置账号，或样例医生已绑定其他账号时，初始化事务回滚并停止启动，不覆盖账号或抢占绑定；管理员已删除的样例医生不会在普通启动时重新创建。医生账号自带USER和DOCTOR角色，管理员自带USER和ADMIN角色。

这些行为由Java后端执行，脚本、Maven和IDE启动方式一致；只需选择对应profile。跨环境账号和密码保存在各自数据库中，不会因为切换profile而同步。

### demo自动重置与test显式重置

demo清理仅针对自身数据库、缓存和消息；脚本、Maven、IDE入口共用Spring启动生命周期。已有环境在启动、运行或重置，或任一共用端口被占用时，在清理前拒绝。刷新页面或重启前端不会重置数据。每次后端启动使用新的JWT密钥，旧Token失效。

demo每次后端启动重新执行V1和V2，再按当前`.env.demo`的`LOCAL_SEED_PASSWORD`创建上述20个用户及医生绑定。

test普通重启保留数据，只有显式重置才清空自己的业务数据（包括自行注册的用户和预约），重建表和样例目录、清理目录缓存、注册验证码/票据及应用消息队列。保持共享容器运行，先停止当前整套应用，再重置并启动test。以下以当前运行test为例。

```bash
docker compose --env-file compose.env up -d --wait
python3 scripts/local_env.py stop test
python3 scripts/local_env.py reset test
python3 scripts/local_env.py start test
```

reset和正常启动共用项目级运行锁，重置后按当前`.env.test`的`LOCAL_SEED_PASSWORD`重新初始化管理员和18名医生账号，原子轮换.env.test中的JWT密钥；旧Token失效，普通重启继续使用轮换后的密钥。reset完成后退出，最后的start才会持续运行前后端。IDE启动的实例也持有锁，需在IDE中先停止，不能只凭脚本无PID就认为可以重置。dev没有自动重置入口，保留其数据。

## dev管理员授权

dev已预置`dev-admin@example.local`。注册只授予USER；如果还需要给其他已注册用户授予管理员权限，可由有权限的本地维护者核对目标库及账号后执行以下SQL，再重新登录取得ADMIN角色。替换示例邮箱，不在共享环境随意授权：

```sql
SELECT u.id, u.email, r.code FROM users u
LEFT JOIN user_roles ur ON ur.user_id=u.id
LEFT JOIN roles r ON r.id=ur.role_id WHERE u.email='student@example.com';
INSERT IGNORE INTO user_roles(user_id,role_id)
SELECT u.id,r.id FROM users u JOIN roles r ON r.code='ADMIN'
WHERE u.email='student@example.com';
```

操作后重查确认ADMIN已存在。JWT角色不会随数据库变更即时更新，医生账号首次绑定后也需要重新登录。

## test业务调时

启动test后，管理员可查询、设置或恢复业务时间：

```bash
python3 scripts/local_env.py clock test get
python3 scripts/local_env.py clock test set --instant 2030-01-01T00:00:00Z
python3 scripts/local_env.py clock test reset
```

命令用LOCAL_SEED_PASSWORD登录专用管理员；若已修改管理员密码，应使用有效凭据调用[调时API](api.md#test环境业务调时)。

设置后时间继续走秒；偏移仅存当前进程，重启恢复实时。调时影响使用业务Clock的预约、支付截止、签到及相关任务，不修改系统、数据库、浏览器、JWT或注册验证码/票据的有效时间。回拨不撤销已完成事实。dev/demo不注册调时接口。

MockPay继续保存在内存，应用重启后旧渠道支付查询和退款续接存在限制。demo是本地演示环境，不代表生产部署。

## Flyway初始化基线

由后端启动时执行 Flyway，无需手工导入 SQL。[V1](../src/main/resources/db/migration/V1__init_schema.sql) 合并了原 V1 至 V11 的迁移结果，创建 22 张业务表以及相应索引、约束和基础数据。[V2](../src/main/java/db/migration/V2__expand_sample_catalog.java) 追加样例目录，空库执行完成后默认包含 2 家医院、6 个科室和 18 名医生。应用启动流程随后创建[预置账号](#预置目录和账号)。

以下按保留现有数据库的普通启动说明。demo 启动和 test 显式重置先[重建各自的数据](#demo自动重置与test显式重置)，再执行 V1 和 V2。

| 数据库状态 | 启动结果 |
| --- | --- |
| 空库 | 依次执行 V1、V2 |
| 已执行现行 V1 | 追加 V2，保留旧医院、科室、医生的 ID、资料、绑定、预约引用和人工添加的目录数据 |
| 已执行现行 V1 和 V2 | 跳过已应用的迁移，保留管理员对目录的变更 |
| 执行过旧版迁移链 | Flyway 历史和 V1 校验和与当前基线不兼容，需按下方说明处理 |

旧版迁移历史可从 Git 提交 `eb84890` 追溯。需要保留旧数据时，继续使用兼容版本，并另行制定数据迁移方案。确认可以重新初始化后，使用空库。重复运行 `init` 或复制配置无法转换历史，`repair`、删除历史记录和 `baseline` 也不能替代数据迁移。

V2 沿用医生编号分配表来避免与人工新增医生冲突，因此已维护过目录的数据库可能超过样例规模。历史示例场次保持可读，新场次仍按首次下单生成。后续迁移从 V3 追加。

## 自动化测试与常驻环境

常驻`test`是可手动体验和调整业务时间的运行环境；Maven的`mysql-it`用于启动Testcontainers临时MySQL执行自动化测试，二者互不依赖。自动化测试不读取三环境私密配置，不要求运行Compose初始化或环境重置。测试运行命令见[README](../README.md#自动化测试)，已有结果见[测试报告](testing/latest-report.md)。

## 本地注册邮件

Mailpit 复用镜像健康检查。宿主后端通过 `127.0.0.1:1025` 发送本地测试邮件，浏览器打开[Mailpit](http://127.0.0.1:8025)查看验证码。

Mailpit 将 SQLite 邮件数据库保存到 `/data/mailpit.db`，该目录挂载命名卷 `appoint_appoint-mailpit-data`。保留该卷时，停止或重建容器后仍可读取已保存的邮件。验证码有效期由后端和 Redis 管理。

已完成初始化时，使用 `docker compose --env-file compose.env up -d --wait` 启动四个共享服务。脚本的 `start` 和 `stop` 管理前后端，Compose 的 `stop` 管理共享服务。

三环境共用收件页面，邮件标题通过 `[dev]`、`[test]`、`[demo]` 区分。SMTP配置位于application.yml；注册namespace位于对应profile配置。验证码和票据在Redis按环境前缀隔离，demo每次后端启动与test显式重置会清理自身auth键；普通dev/test重启保留并自然过期。已有账号及预置账号仍直接邮箱密码登录，新公开注册必须验证邮箱。
