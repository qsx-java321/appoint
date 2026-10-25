# 医院预约项目

用于学习 Spring Boot、Spring Security、JWT、MyBatis-Plus 与事务状态流转的后端项目。当前实现注册、登录、JWT 鉴权、医院资源查询、提交预约与模拟支付成功。

## 技术栈

- JDK 21、Spring Boot 3.5.16、MyBatis-Plus 3.5.17
- MySQL 8.4、Redis 7.4.9、RabbitMQ 4.3.2-management
- Flyway、Lombok、JUnit 5、MockMvc

## 启动基础设施

项目使用 `compose.yaml` 管理 MySQL、Redis、RabbitMQ。`.env` 是本地配置且已被 Git 忽略；如文件丢失，可复制 `.env.example`。

```bash
docker compose up -d
docker compose ps
```

等待三个服务显示为 healthy 后，在当前 shell 导入数据库与 JWT 配置并启动应用：

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

应用监听 `http://localhost:8080`。首次启动时 Flyway 会创建表并写入医院、科室、医生、号源、`USER` 与 `ADMIN` 演示数据。

## 接口与调试

核心接口：

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/users/me`
- `GET /api/v1/hospitals`
- `POST /api/v1/appointments`
- `GET /api/v1/appointments/{id}`
- `GET /api/v1/appointments/my`
- `POST /api/v1/payments/{id}/simulate-success`

使用 `http/auth.http` 和 `http/appointment.http` 可按“注册 → 登录 → 查询 → 预约 → 模拟支付”验证整个流程。`.http` 文件适用于 IntelliJ IDEA HTTP Client。
预约详情和“我的预约”列表都会返回关联的 `paymentOrderId` 与当前 `paymentStatus`，便于在创建响应丢失后继续支付流程。
医生列表会返回全局唯一的 `doctorNo`。预约创建时会保存医生、科室和医院信息快照，预约详情与“我的预约”中的 `doctor` 对象不会因后续资料变更而改变。

## 状态流转

```text
号源：AVAILABLE → HELD → BOOKED
预约单：PENDING_PAYMENT → CONFIRMED
支付单：UNPAID → PAID
```

提交预约时以数据库条件更新占用号源；模拟支付在一个事务中完成支付单、预约单与号源的状态推进。重复模拟支付会返回已经完成的状态，不会重复推进。

## 测试

```bash
./mvnw test
```

测试使用 H2 的 MySQL 兼容模式运行 Flyway 迁移，覆盖注册、登录、认证与关键订单状态逻辑。

## 当前边界

项目不包含真实支付、退款、超时取消、预约取消、改签、登出、Token 黑名单、权限管理后台和 Redis/RabbitMQ 业务接入。Redis 与 RabbitMQ 由 Compose 提供，待出现明确业务需求时再接入应用。
