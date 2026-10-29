# 医院预约系统

这是一个用于学习 Spring Boot、JWT 鉴权、关系数据库事务与预约状态流转的后端项目，目前没有业务前端或真实支付渠道。管理员配置医生上午或下午排班及统一号源库存；用户注册、登录并维护就诊人后，可以暂占一个名额，在支付期限内模拟支付取得就诊序号，也可以按规则取消、模拟退款或等待超时释放。

业务规则与各状态的实际含义见 [预约业务流程](docs/business-flow.md)，请求、响应字段及错误契约见 [HTTP API](docs/api.md)。领域词汇见 [术语表](GLOSSARY.md)，统一库存与安全迁移决策见 [ADR 0003](docs/adr/0003-unified-schedule-inventory-and-safe-migration.md)。

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

默认 API 地址为 `http://localhost:8080`。Flyway V1 建立基础结构；V2 在旧有效订单存在时阻断升级；V3 增量增加统一库存、有效预约占用、调整审计和就诊序号。迁移保留旧数据，不清库。种子只写入 `USER`、`ADMIN` 角色、目录资料、三个只读旧号源及两场新模型演示排班，不创建演示用户、就诊人或订单。

## 从用户角度验证

1. 在 [http/auth.http](http/auth.http) 注册、登录，取得 Bearer Token。
2. 由运维授予 `ADMIN` 后重新登录，使用 [http/admin-schedule.http](http/admin-schedule.http) 创建测试排班并调整库存。
3. 在 [http/appointment.http](http/appointment.http) 创建就诊人，查询排班并携带 UUID 幂等键暂占名额。
4. 模拟支付、退款、待支付取消和超时，核对就诊序号、历史快照及库存释放。

业务讲解可直接打开 [交互式业务流程说明页](docs/business-flow-demo.html)。该文件是独立文档，只使用示例数据，不属于应用前端，也不会访问业务 API。本轮自动化与真实 HTTP 结果见 [验证报告](docs/verification-report.md)。

`.http` 文件适用于 IntelliJ IDEA HTTP Client，`http/http-client.env.json` 提供 `local` 环境的 `baseUrl`。V3 内置排班日期在 2099 年，当前会显示为尚未开放；可运行预约示例应先创建当前日期后 1–7 天内的排班。

除注册和登录外，所有 API 都要求 `Authorization: Bearer <accessToken>`。响应统一为 `{ "code": 0, "message": "success", "data": ... }`；错误使用实际 HTTP 状态码和业务 `code`。API 响应中的 64 位 ID 为字符串，便于浏览器安全使用。

## 当前业务边界

- 支付和退款仅推进持久化状态，不接入支付渠道，也没有费用计算或失败分支。
- 已确认预约只有在开诊前严格超过 24 小时时可自行取消；没有改签流程。
- 预约只能使用当前账号已启用的就诊人；证件号码做格式、日期和校验位校验，不核验号码是否属于提交者。
- 成功创建使用过的幂等键长期保留；重新预约必须生成新键。
- 查询只读：订单到期后、扫描关闭前，数据库中可暂留 `PENDING_PAYMENT + UNPAID` 和一份暂占库存，但支付已经不可用。默认扫描间隔为 30 秒。
- 排班关闭后已确认预约只显示停诊提示，不自动通知、改约或退款。
- 没有业务前端、公开角色管理、登出、Token 黑名单或 Redis/RabbitMQ 业务接入。

## 测试

```bash
./mvnw test
./mvnw -Pmysql-it verify
```

第一条运行 H2/MySQL 兼容模式的快速回归；第二条通过 Testcontainers 的 MySQL 8.4 验证迁移、行锁、并发、真实 HTTP 端口和短支付期限扫描，需要本机 Docker 可用。测试数据只写入临时数据库容器。
