# 医院预约系统

这是一个模块化单体医院预约后端。医院、科室和医生默认每天正常工作；每位医生每天固定提供四个独立预约场次。管理员只维护统一的每时段容量和挂号费，用户首次下单时系统按需生成实际场次并暂占库存。

## 当前能力

- 账号注册、JWT 登录、当前用户查询及管理员角色访问控制。
- 本人就诊人的新增、查询、资料修改与启停，以及证件校验和脱敏。
- 医院、科室、医生目录管理和查询。
- 每位医生今天、明天、后天的 12 个固定预约场次查询，返回下单截止与可预约状态。
- 管理员统一预约设置，默认每时段 20 个号源、挂号费 10.00 CNY。
- 预约幂等创建、库存暂占、跨账号同证件防重复、费用和就诊资料快照。
- 支付尝试、MockPay 回调、Transactional Outbox、RabbitMQ/Inbox、迟到支付关闭释放与退款补偿、有界重试和受控 DLQ 重放。
- 待支付 15 分钟超时、用户主动取消、已支付退款、就诊序号和库存守恒。
- 管理员绑定医生账号；用户当天签到，医生四场看板、开始及完成接诊，双方共用就诊进度。
- 我的全部预约按就诊日期、开始时间和 ID 排序，默认 DESC，可切换 ASC。

新流程不提供医院关门、医生停诊、管理员手工创建/修改/调整/关闭场次。历史 `CLOSED`、`CANCELLED_BY_HOSPITAL`、旧 `MORNING`/`AFTERNOON` 数据继续可读。

## 预约规则

四个场次为：

| 类型 | 时间 |
| --- | --- |
| `MORNING_FIRST` | 08:00–10:00 |
| `MORNING_SECOND` | 10:30–12:00 |
| `AFTERNOON_FIRST` | 14:00–16:00 |
| `AFTERNOON_SECOND` | 16:30–18:00 |

用户按 `Asia/Shanghai` 服务端日期可以创建今天、明天、后天的预约，必须严格早于场次开始前 15 分钟。四场下单截止依次为当天 07:45、10:15、13:45、16:15。查询未生成场次不会写库；首次下单在事务中生成场次并复制当时统一设置。设置更新不改变已生成场次和订单费用快照。

订单创建时间在锁后实际占号前采样；支付截止取创建时间加 15 分钟与场次开始时间的较早者，允许跨零点。停止新下单后，原订单仍可在自己的截止前支付；历史预约及截止不随三天入口滚动重算。本系统确认收款的服务端时间 `paidAt` 必须严格早于支付截止；不使用渠道上报的发生时间判定资格。扫描延迟不会延长支付资格，截止后确认的收款进入退款补偿。

## 技术栈

- Java 21、Spring Boot、Spring Security、MyBatis-Plus
- MySQL 8.4、Flyway、H2 测试数据库
- RabbitMQ、Transactional Outbox / Inbox
- Maven Wrapper、JUnit 5、Testcontainers、ArchUnit

## 本地运行

首次使用时准备两份本地配置，按需修改凭据，并确保两份文件中的 MySQL 和 RabbitMQ 连接信息一致：

```bash
cp compose.env.example compose.env
cp .env.example .env
```

`compose.env` 供 Docker Compose 初始化 MySQL、RabbitMQ；`.env` 供 Spring Boot 从项目根目录启动时加载，默认启用 `dev` profile 和 MockPay。两份实际配置均不提交 Git。已有 MySQL、RabbitMQ 数据卷时，修改初始化变量不会自动修改卷内已有账号密码。

从项目根目录启动依赖和应用：

```bash
docker compose --env-file compose.env up -d --wait mysql redis rabbitmq
./mvnw spring-boot:run
```

其他 Compose 命令同样需要 `--env-file compose.env`。IDE 的工作目录应设为项目根目录；系统环境变量可覆盖应用 `.env`。Redis 不参与当前业务，但隔离验收会检查其健康状态。

使用 Compose 实际依赖执行隔离业务验收：

```bash
python3 scripts/compose_acceptance.py
```

使用 curl 发起所有业务 HTTP 请求并保存脱敏结果（含注册、预约、签到、医生看板与接诊）：

```bash
python3 scripts/compose_acceptance.py --http-client curl --http-report /tmp/appoint-curl-report.json
```

验收入口会创建独立数据库、数据库账号和 RabbitMQ vhost，启动 dev profile，验证预约、支付、异步确认、取消退款、迟到支付释放退款及库存守恒及医生绑定、当天签到、四状态看板和接诊完成；成功后删除验收资源，失败时保留资源标识和日志。Redis 只检查健康状态。

测试分层、夹具隔离及新增用例的编写方式见 [测试约定](docs/testing.md)。

常规测试：

```bash
./mvnw test
```

包含真实 MySQL 迁移、唯一约束和并发竞争的测试：

```bash
./mvnw -Pmysql-it verify
```

消息确认、故障重试、DLQ 和提交/ACK 窗口的真实 MySQL + RabbitMQ 测试，单独执行：

```bash
./mvnw -Preliability-it verify
```

Maven 命令串行运行；两个集成 profile 均重复运行常规测试，不把报告数量重复相加。Docker 和依赖镜像须可用，不跳过容器测试。停止本地依赖可执行 `docker compose --env-file compose.env stop`，保留数据卷。

## HTTP 示例

1. [http/auth.http](http/auth.http)：注册普通用户、登录并查询当前用户；管理员需先按下文授予角色后重新登录。
2. [http/admin-catalog.http](http/admin-catalog.http)：维护医院、科室和医生目录。
3. [http/admin-schedule.http](http/admin-schedule.http)：读取和更新统一预约设置。
4. [http/appointment.http](http/appointment.http)：维护就诊人、查询三天 12 个场次并创建预约、正式 MockPay 支付、轮询确认和退款。
5. [http/visit.http](http/visit.http)：绑定医生账号、用户签到和医生看板及接诊。当天签到须使用当天已确认预约，不能修改宿主系统时间。

`.http` 文件适用于 IntelliJ IDEA HTTP Client，环境变量位于 `http/http-client.env.json`。日期变量运行前按目录返回的上海服务端日期填写；示例不预置会过期的固定预约日期。

## 本地开发管理员初始化

Flyway 创建 `USER`、`ADMIN`、`DOCTOR` 角色和演示医疗目录，不创建账号。注册接口只赋予 `USER`，也不提供自助授予管理员角色的 HTTP 接口。

1. 先用 `http/auth.http` 注册目标账号。下例沿用 `student@example.com`；如需同时演示普通用户权限，另注册一个不授予 `ADMIN` 的账号。
2. 在确认过的本地开发数据库中，由有权限的维护者通过数据库客户端执行以下 SQL。先核对目标邮箱对应的用户 ID 和现有角色；邮箱应与注册后保存的小写邮箱一致。

```sql
SELECT u.id, u.email, r.code AS role_code
FROM users u
LEFT JOIN user_roles ur ON ur.user_id = u.id
LEFT JOIN roles r ON r.id = ur.role_id
WHERE u.email = 'student@example.com';

SELECT id, code FROM roles WHERE code = 'ADMIN';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = 'ADMIN'
WHERE u.email = 'student@example.com';

SELECT u.id, u.email, r.code AS role_code
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id
WHERE u.email = 'student@example.com';
```

目标用户和 `ADMIN` 角色都必须存在；否则 INSERT 不会插入记录，需回到前置步骤检查。`INSERT IGNORE` 借助 `(user_id, role_id)` 唯一约束跳过已有关系，使重复执行不重复授予；操作后必须查询确认实际角色。

3. 确认结果包含 `ADMIN` 后重新执行登录请求，更新 HTTP Client 的 `accessToken`。旧 JWT 的角色不会随数据库授权即时改变；`GET /api/v1/users/me` 可核对数据库中的当前角色，管理员接口权限仍以本次请求 JWT 中的角色为准。

以上 SQL 仅作为受控本地开发操作说明，不随应用启动或 HTTP 示例自动执行。

## 文档

- [docs/project-status.md](docs/project-status.md)：当前能力、交付进度、未实现边界及历史记录阅读规则。
- [docs/testing.md](docs/testing.md)：测试分层、隔离、夹具和验收约定。
- [GLOSSARY.md](GLOSSARY.md)：领域术语。
- [docs/api.md](docs/api.md)：REST API 契约和边界。
- [docs/business-flow.md](docs/business-flow.md)：端到端业务流程。
- [docs/module-design.md](docs/module-design.md)：预约支付及就诊模块 interface、代码入口、MANDATORY、锁序与事件恢复。
- [docs/verification-report.md](docs/verification-report.md)：带日期和代码检查点的历史验证基线，以及后续文档核对记录；不代表每次文档更新都重跑了业务验收。
- [docs/adr/0005-four-session-on-demand-booking.md](docs/adr/0005-four-session-on-demand-booking.md)：四时段、按需生成和历史兼容决策。
- [docs/adr/0007-three-day-booking-and-session-cutoffs.md](docs/adr/0007-three-day-booking-and-session-cutoffs.md)：三天窗口、锁后准入与下单/支付截止决策，替代 ADR 0005 的对应时间规则。

到院签到与门诊接诊：

- [就诊需求](docs/visit/requirements.md)：用户“我的预约”、医生看板、状态与按钮需求。
- [docs/visit/design.md](docs/visit/design.md)：visit模块、数据表、Service和查询组合设计。
- [docs/visit/api.md](docs/visit/api.md)：新增 7 个接口、扩展 2 个查询接口的现行契约。
- [docs/visit/change-scope.md](docs/visit/change-scope.md)：实施前后行为对照、代码改动范围与验收说明。

## 当前进度与阅读路线

本阶段开发已交付，系统为模块化单体、数据库V10。完整能力、未实现范围和最新实测检查点由[项目状态](docs/project-status.md)维护。

- 学习项目：[项目状态](docs/project-status.md) → [术语](GLOSSARY.md) → [业务流程](docs/business-flow.md) → [模块设计](docs/module-design.md) → [就诊设计](docs/visit/design.md) → [API](docs/api.md) → [测试覆盖](docs/testing/scenario-coverage.md)。
- 运行维护：本页本地运行 → [API](docs/api.md)和HTTP示例 → [测试约定](docs/testing.md) → [恢复手册](docs/runbooks/payment-module-cutover.md) → [验收证据](docs/verification-report.md)。
- 演进追溯：[六组交付档案](.scratch/README.md)，原任务全文按[文件去向清单](.scratch/archive-manifest.json)从Git读取。

## 当前边界

- 已实现当天签到与正常接诊主线；自动叫号、爽约、状态回退、病历处方、实时推送和精确到分钟的就诊安排不在当前范围内。
- 签到仅按上海时区就诊日期判断，当天 00:00 至次日 00:00 前均可签到，不限制在预约时段或提前若干分钟。医生开始/完成按归属和状态校验，没有额外的当天或场次时间限制，不自动结束就诊。
- 已确认预约只能在开诊前严格超过 24 小时主动取消。
- dev/test 使用 MockPay；真实支付渠道尚未接入。
- 退款结果队列已声明，尚无对应监听器，未实现短信或邮件通知；Outbox 发布失败耗尽、消费进入 DLQ 等情况可能需要人工恢复，详见模块设计与切换手册。
