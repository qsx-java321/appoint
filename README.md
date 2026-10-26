# 医院预约系统

这是一个用于学习 Spring Boot、JWT 鉴权、关系数据库事务与预约状态流转的后端项目，目前没有业务前端或真实支付渠道。用户可以注册、登录，按医院 → 科室 → 医生 → 未来可用号源查询资源，提交预约后查看凭证，在支付期限内模拟支付或取消；到期未支付的预约由后台关闭并释放号源。

业务规则与各状态的实际含义见 [预约业务流程](docs/business-flow.md)，请求、响应字段及错误契约见 [HTTP API](docs/api.md)。领域词汇见 [术语表](GLOSSARY.md)，关键事务选择见 [ADR 0001](docs/adr/0001-appointment-lifecycle-transactions.md)。

## 技术栈

- JDK 21、Spring Boot 3.5.16、Spring Security、JWT、MyBatis-Plus 3.5.17
- MySQL 8.4、Flyway、Lombok
- JUnit 5、MockMvc、H2；真实 MySQL 集成测试使用 Testcontainers
- Docker Compose 提供 Redis 7.4.9 和 RabbitMQ 4.3.2-management，目前应用未接入其业务能力

## 本地启动

复制配置并启动基础设施：

```bash
cp .env.example .env
docker compose up -d
docker compose ps
```

等待容器健康检查通过，在同一个 shell 导入配置并启动应用：

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

默认 API 地址为 `http://localhost:8080`。Flyway 首次启动会建表，写入 `USER`、`ADMIN` 两个**角色**，以及示例医院、科室、医生和三个日期在 2099 年的号源；**不会创建演示用户账号**。先调用注册接口，再使用自己的账号登录。`.env` 是本地文件，已被 Git 忽略；不要把真实密钥写入仓库。

## 从用户角度验证

1. 在 [http/auth.http](http/auth.http) 注册、登录，取得 Bearer Token。
2. 在 [http/appointment.http](http/appointment.http) 查询医院、科室、医生和未来可用号源，提交预约。
3. 用返回的 `appointmentOrderId` 查询详情或“我的预约”，核对医院地址、就诊日期与时段、医生资料、支付截止时间。
4. 在不同的待支付预约上分别模拟支付、主动取消和等待超时；取消或超时释放的号源可再次预约。

业务讲解可直接打开 [交互式业务流程说明页](docs/business-flow-demo.html)。该文件是独立文档，只使用示例数据，不属于应用前端，也不会访问业务 API。本轮自动化与真实 HTTP 结果见 [验证报告](docs/verification-report.md)。

`.http` 文件适用于 IntelliJ IDEA HTTP Client，`http/http-client.env.json` 提供 `local` 环境的 `baseUrl`。内置号源 ID `1`、`2`、`3` 仅在全新数据库中初始可用；重复运行时应先查询可用号源，并替换示例中的 `scheduleId`。

除注册和登录外，所有 API 都要求 `Authorization: Bearer <accessToken>`。响应统一为 `{ "code": 0, "message": "success", "data": ... }`；错误使用实际 HTTP 状态码和业务 `code`。API 响应中的 64 位 ID 为字符串，便于浏览器安全使用。

## 当前业务边界

- 支付仅由 `POST /api/v1/payments/{id}/simulate-success` 模拟成功，不接入支付渠道，也没有费用计算。
- 只能取消待支付预约；已确认预约没有取消、退款或改签流程。
- 预约凭证记录的是注册用户当前提交的预约，没有就诊人管理。
- 查询只读：订单到期后、扫描关闭前，数据库中可暂留 `PENDING_PAYMENT + UNPAID + HELD`，但支付已经不可用。默认扫描间隔为 30 秒；故障或积压时释放可能更晚。
- 没有业务前端、管理后台、登出、Token 黑名单或 Redis/RabbitMQ 业务接入。

## 测试

```bash
./mvnw test
./mvnw -Pmysql-it verify
```

第一条运行 H2/MySQL 兼容模式的快速回归；第二条通过 Testcontainers 的 MySQL 8.4 验证迁移、行锁、并发、真实 HTTP 端口和短支付期限扫描，需要本机 Docker 可用。测试数据只写入临时数据库容器。
