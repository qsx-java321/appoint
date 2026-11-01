# 医院预约系统

这是一个使用模块化单体实现的医院预约后端。管理员维护医院、科室、医生、排班、CNY 挂号费和统一号源库存；用户暂占名额后通过独立支付模块发起支付。支付回调经 Transactional Outbox、RabbitMQ 和 Inbox 异步确认预约，取消与迟到支付通过原路全额退款补偿。目前没有业务前端或真实支付渠道，dev/test 使用 MockPay Adapter。

业务规则与各状态的实际含义见 [预约业务流程](docs/business-flow.md)，请求、响应字段及错误契约见 [HTTP API](docs/api.md)。领域词汇见 [术语表](GLOSSARY.md)，统一库存与安全迁移决策见 [ADR 0003](docs/adr/0003-unified-schedule-inventory-and-safe-migration.md)。

## 已实现业务能力

| 业务领域 | 已实现能力 |
| --- | --- |
| 账号与权限 | 邮箱注册、密码登录、JWT 鉴权、查询当前账号；区分 `USER` 与 `ADMIN` 权限 |
| 就诊人管理 | 创建、查询、修改、启用和停用就诊人；校验居民身份证并保证账号内证件唯一 |
| 医疗资源管理 | 管理员创建、修改和受限删除医院、科室与医生档案；医生编号由系统生成且保持稳定 |
| 就诊资源查询 | 查询医院、科室、医生档案和医生排班，返回排班的未开放、可预约、约满、截止和关闭状态 |
| 排班与号源库存 | 管理员创建、开放前修改、调整容量和关闭排班；统一管理可用、暂占和已确认名额 |
| 预约 | 幂等地暂占名额，保存就诊人、医院、科室、医生、排班和费用快照；防止同场有效预约重复占用 |
| 支付与确认 | 创建支付尝试、支付失败重试、回调验签与金额核对；异步确认预约并分配就诊序号 |
| 取消与退款 | 待支付取消、超时释放、已确认预约在规定时间内取消，以及原路全额退款和迟到支付补偿 |
| 可靠性与运维 | Transactional Outbox、RabbitMQ、Inbox 幂等、Publisher Confirm、重试、DLQ、退款 worker、过期扫描和对账 |

## 已覆盖的主要流程场景

1. **管理员构建资源链**：依次创建医院、科室、医生和排班，新资源立即进入用户查询；仍有下级资源时禁止删除。
2. **正常预约支付**：注册登录、维护就诊人、查询排班、暂占名额、支付、异步确认并取得就诊序号。
3. **支付失败重试**：一次支付尝试失败不结束支付单，截止前可重新发起。
4. **待支付取消与超时**：用户主动取消或到达支付截止时间后，关闭支付并释放暂占名额。
5. **已确认取消退款**：在开诊前严格超过 24 小时取消，立即释放名额并异步原路全额退款。
6. **医院停诊**：管理员关闭排班，释放未支付预约；已确认预约保留并显示停诊提示。
7. **迟到支付补偿**：预约取消、过期或因停诊关闭后收到成功回调，不恢复预约，转入全额退款。
8. **幂等与并发竞争**：覆盖重复创建、同证件跨账号占用、末位名额竞争，以及支付与取消、过期、停诊竞争。
9. **消息与渠道异常恢复**：支付事实先持久化，Broker 恢复后继续确认；重复投递、退款失败和长时间中间态由幂等、重试与对账处理。

具体状态流转、时间边界和补偿规则见 [预约业务流程](docs/business-flow.md)。

## 技术栈

- JDK 21、Spring Boot 3.5.16、Spring Security、JWT、MyBatis-Plus 3.5.17
- MySQL 8.4、RabbitMQ 4.3、Flyway、Lombok
- JUnit 5、MockMvc、H2；真实 MySQL/RabbitMQ 可靠性测试使用 Testcontainers
- Transactional Outbox、Inbox 幂等、Publisher Confirm、retry、DLX/DLQ 和对账

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

默认 API 地址为 `http://localhost:8080`。Flyway V1–V3 建立旧结构并升级统一库存；V4 在支付结构扩展前阻断仍活跃的 Legacy 支付；V5 增加费用快照；V6–V7 增加支付尝试、通知、退款、Outbox/Inbox 及其约束和扫描索引；V8 增加同一医院内科室名称唯一约束。迁移只前向增量演进，保留旧终态数据且不推测未知金额。

## 从用户角度验证

1. 在 [http/auth.http](http/auth.http) 注册、登录，取得 Bearer Token。
2. 由运维授予 `ADMIN` 后重新登录，使用 [http/admin-catalog.http](http/admin-catalog.http) 创建医院、科室和医生。
3. 使用 [http/admin-schedule.http](http/admin-schedule.http) 为新医生创建排班并调整库存。
4. 在 [http/appointment.http](http/appointment.http) 创建就诊人，查询排班并携带 UUID 幂等键暂占名额。
5. 发起 MockPay 支付尝试并完成异步回调，轮询预约详情；再验证失败重试、取消退款和超时释放。

业务讲解可直接打开 [交互式业务流程说明页](docs/business-flow-demo.html)。该文件是独立文档，只使用示例数据，不属于应用前端，也不会访问业务 API。本轮自动化与真实 HTTP 结果见 [支付模块验证报告](docs/payment-module-verification-report.md)；旧版统一库存报告保留在 `docs/verification-report.md`。

`.http` 文件适用于 IntelliJ IDEA HTTP Client，`http/http-client.env.json` 提供 `local` 环境的 `baseUrl`。V3 内置排班日期在 2099 年，当前会显示为尚未开放；可运行预约示例应先创建当前日期后 1–7 天内的排班。

除注册和登录外，所有 API 都要求 `Authorization: Bearer <accessToken>`。响应统一为 `{ "code": 0, "message": "success", "data": ... }`；错误使用实际 HTTP 状态码和业务 `code`。API 响应中的 64 位 ID 为字符串，便于浏览器安全使用。

## 当前业务边界

- 第一阶段只支持 CNY、MockPay 和原路全额退款；不接真实微信/支付宝。Mock 与旧 `simulate-success` 兼容入口仅在 dev/test 注册，生产不暴露。
- 已确认预约只有在开诊前严格超过 24 小时时可自行取消；没有改签流程。
- 预约只能使用当前账号已启用的就诊人；证件号码做格式、日期和校验位校验，不核验号码是否属于提交者。
- 成功创建使用过的幂等键长期保留；重新预约必须生成新键。
- MySQL 是支付与事件真相，RabbitMQ 至少一次投递；允许短暂出现 `PAID + PENDING_PAYMENT`（预约确认中）和 `REFUND_PENDING`。
- 排班关闭后已确认预约只显示停诊提示，不自动通知、改约或退款。
- 医院、科室和医生资源创建后立即可见，不提供上下架；存在直接下级资源或任意排班时不能物理删除。
- 医生编号和已有预约快照不会随医院、科室或医生档案修改而变化。
- 没有业务前端、公开角色管理、登出、Token 黑名单、部分退款或自动改约。

## 测试

```bash
./mvnw test
./mvnw -Pmysql-it verify
./mvnw -Preliability-it verify
```

第一条运行 H2 快速回归；第二条验证 MySQL 8.4 的迁移、唯一约束、行锁、竞争和真实 HTTP；第三条同时启动 MySQL 与 RabbitMQ，验证 confirm、重投、Inbox 幂等、退款 worker 和崩溃窗口。后两条需要 Docker，数据只写入临时容器。
