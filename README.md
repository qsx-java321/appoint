# 医院预约系统

这是一个模块化单体医院预约后端。医院、科室和医生默认每天正常工作；每位医生每天固定提供四个独立预约场次。管理员只维护统一的每时段容量和挂号费，用户首次下单时系统按需生成实际场次并暂占库存。

## 当前能力

- 医院、科室、医生目录管理和查询。
- 每位医生 D+1 至 D+7 的 28 个固定预约场次查询。
- 管理员统一预约设置，默认每时段 20 个号源、挂号费 10.00 CNY。
- 预约幂等创建、库存暂占、跨账号同证件防重复、费用和就诊资料快照。
- 支付尝试、MockPay 回调、Transactional Outbox、RabbitMQ/Inbox、迟到支付退款补偿。
- 待支付 15 分钟超时、用户主动取消、已支付退款、就诊序号和库存守恒。

新流程不提供医院关门、医生停诊、管理员手工创建/修改/调整/关闭场次。历史 `CLOSED`、`CANCELLED_BY_HOSPITAL`、旧 `MORNING`/`AFTERNOON` 数据继续可读。

## 预约规则

四个场次为：

| 类型 | 时间 |
| --- | --- |
| `MORNING_FIRST` | 08:00–10:00 |
| `MORNING_SECOND` | 10:30–12:00 |
| `AFTERNOON_FIRST` | 14:00–16:00 |
| `AFTERNOON_SECOND` | 16:30–18:00 |

用户按 `Asia/Shanghai` 服务端日期只能创建明天至第 7 天的预约。查询未生成场次不会写库；首次下单在事务中生成场次并复制当时统一设置。设置更新不改变已生成场次和订单费用快照。

成功下单后始终获得完整 15 分钟支付时间，允许跨零点。支付成功时间必须严格早于截止时间；扫描延迟不会延长支付资格，截止后实际收款进入退款补偿。

## 技术栈

- Java 21、Spring Boot、Spring Security、MyBatis-Plus
- MySQL 8.4、Flyway、H2 测试数据库
- RabbitMQ、Transactional Outbox / Inbox
- Maven Wrapper、JUnit 5、Testcontainers、ArchUnit

## 本地运行

```bash
docker compose up -d mysql rabbitmq
./mvnw spring-boot:run
```

常规测试：

```bash
./mvnw test
```

包含真实 MySQL 迁移、唯一约束和并发竞争的测试：

```bash
./mvnw -Pmysql-it verify
```

## HTTP 示例

1. [http/auth.http](http/auth.http)：注册并登录管理员和普通用户。
2. [http/admin-catalog.http](http/admin-catalog.http)：维护医院、科室和医生目录。
3. [http/admin-schedule.http](http/admin-schedule.http)：读取和更新统一预约设置。
4. [http/appointment.http](http/appointment.http)：维护就诊人、查询 28 个场次并创建预约。

`.http` 文件适用于 IntelliJ IDEA HTTP Client，环境变量位于 `http/http-client.env.json`。

## 文档

- [GLOSSARY.md](GLOSSARY.md)：领域术语。
- [docs/api.md](docs/api.md)：REST API 契约和边界。
- [docs/business-flow.md](docs/business-flow.md)：端到端业务流程。
- [docs/verification-report.md](docs/verification-report.md)：当前自动化验证结果和覆盖范围。
- [docs/adr/0005-four-session-on-demand-booking.md](docs/adr/0005-four-session-on-demand-booking.md)：四时段、按需生成和历史兼容决策。

## 当前边界

- 不开发签到、叫号或精确到分钟的就诊安排；每个场次仅维护独立就诊序号。
- 已确认预约只能在开诊前严格超过 24 小时主动取消。
- dev/test 使用 MockPay；真实支付渠道尚未接入。
