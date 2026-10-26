# 医院预约项目

用于学习 Spring Boot、Spring Security、JWT、MyBatis-Plus 与事务状态流转的后端项目。当前实现注册、登录、JWT 鉴权、医院资源查询、预约凭证、提交与取消预约、支付超时释放号源及模拟支付成功。

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
- `POST /api/v1/appointments/{id}/cancel`
- `POST /api/v1/payments/{id}/simulate-success`

使用 `http/auth.http` 和 `http/appointment.http` 可按“注册 → 登录 → 查询 → 预约 → 模拟支付”验证整个流程。`.http` 文件适用于 IntelliJ IDEA HTTP Client。
预约详情和“我的预约”列表会返回就诊日期、时段、医院地址、支付截止时间、关联支付单及 `canPay`、`canCancel` 等操作标记。医院、科室、医生和就诊时段均保存创建时快照。
新预约默认保留号源 15 分钟，且支付截止时间不会晚于就诊开始时间。后台默认每 30 秒扫描过期预约，关闭支付单并释放号源；即使扫描尚未运行，到期支付也会立即被拒绝。
医生列表会返回全局唯一的 `doctorNo`。预约创建时会保存医生、科室和医院信息快照，预约详情与“我的预约”中的 `doctor` 对象不会因后续资料变更而改变。

## 状态流转

```text
号源：AVAILABLE → HELD → BOOKED
                  ↘ AVAILABLE（取消或过期）
预约单：PENDING_PAYMENT → CONFIRMED / CANCELLED / EXPIRED
支付单：UNPAID → PAID / CLOSED
```

提交预约时锁定并条件更新号源。支付、取消和过期处理统一按“预约单 → 支付单 → 号源”的顺序加锁并在单个事务中推进状态；重复请求不会再次释放或确认号源。

## 测试

```bash
./mvnw test
```

测试使用 H2 的 MySQL 兼容模式运行 Flyway 迁移，覆盖注册、登录、认证、凭证快照、取消和过期逻辑。Docker 可用时运行真实 MySQL 迁移与并发测试：

```bash
./mvnw -Pmysql-it verify
```

## 当前边界

项目不包含真实支付、已支付预约的取消退款、改签、登出、Token 黑名单、权限管理后台和 Redis/RabbitMQ 业务接入。Redis 与 RabbitMQ 由 Compose 提供，待出现明确业务需求时再接入应用。
