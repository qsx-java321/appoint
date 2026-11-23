# 模块设计

项目采用模块化单体，一套 Spring Boot 后端提供 REST API，Vue 前端通过 `/api` 访问。MySQL 保存业务数据，Redis 用于目录缓存、注册验证和预约令牌，Mailpit 接收本地注册邮件。RabbitMQ 用于站内通知的异步投递；预约、支付、签到和接诊的业务写入仍在 MySQL 内完成。

| 模块 | 职责 |
| --- | --- |
| `auth`、`user`、`patient` | 邮箱注册、JWT 登录、角色和就诊人 |
| `catalog` | 医疗目录、场次查询、统一预约设置和医生账号绑定 |
| `appointment`、`payment` | 暂占与库存、预约凭证、支付单 |
| `coordination` | 创建预约、付款确认、取消和过期的事务编排 |
| `visit`、`bookingquery` | 签到与接诊写入、用户预约详情和医生工作台查询 |
| `notification` | 通知事件记录、Outbox 发布、RabbitMQ 消费和消息中心查询 |
| `localenv` | 环境就绪探测、启动互斥、数据初始化、重置和 test 调时 |

## 核心数据

- `schedules` 按医生、日期和场次类型唯一，保存库存、费用快照和下一个就诊序号。
- `appointment_orders` 保存预约凭证和最晚支付时间，与 `payment_orders` 一对一。
- `appointment_create_requests` 按账号记录幂等键和创建回执。`appointment_active_claims` 用唯一约束限制同场次同就诊人和同证件的有效占用。
- `visit_records` 在签到时创建，一张预约至多一条，保存就诊进度和动作时间。
- `notification_outbox_events` 保存待发布通知事件，按预约和通知类型唯一；`user_notifications` 保存消息中心已入库快照，同样按预约和通知类型唯一，另有事件 ID 唯一约束。

当前基线只保留现行业务状态：预约为 `PENDING_PAYMENT`、`CONFIRMED`、`CANCELLED`、`EXPIRED`，支付为 `UNPAID`、`PAID`、`CLOSED`。已退出的管理员手工排班、支付尝试/退款和历史支付通知结构不再保留；现行站内通知使用 V3 新增的 `notification_outbox_events`，不使用通用 Inbox 表。修改 V1 后需重置 dev/test 数据库，不再兼容旧迁移链数据。

## 事务与锁序

| 操作 | 写入和并发处理 |
| --- | --- |
| 创建 | 查询回执或消费令牌后进入 MySQL 事务。先登记幂等键，再按需建场并用条件更新暂占名额。锁定就诊人后复验下单资格，创建预约单、支付单并保存回执。失败时整笔回滚。[下单幂等设计](booking-idempotency.md) |
| 付款 | 按场次 → 预约单 → 支付单加锁。锁后采样业务时间，在同一事务记录付款、确认名额、分配序号并写入「预约成功」待发布事件。已付款请求重试沿用原支付时间和序号，不重复写事件。 |
| 取消与过期 | 沿用付款锁序。过期处理锁后复核待支付状态、截止时间和支付单。关单、释放暂占和删除有效占用在同一事务提交，不产生通知。 |
| 签到与接诊 | 按预约归属和当前进度校验资格，写就诊记录并在同一事务追加对应待发布事件。唯一约束和条件更新保护首次成功的动作时间和唯一通知。 |

[创建事务](../src/main/java/com/qsx/appoint/coordination/application/BookingCreationTransaction.java) · [预约生命周期协调器](../src/main/java/com/qsx/appoint/coordination/application/DefaultBookingLifecycleCoordinator.java) · [就诊服务](../src/main/java/com/qsx/appoint/visit/application/DefaultVisitService.java)

[过期任务](../src/main/java/com/qsx/appoint/appointment/job/AppointmentExpirationJob.java)默认在每轮结束 30 秒后开始下一轮，按支付截止时间和预约 ID 每批查询最多 100 条，逐单开启事务处理。单笔失败记日志，其余订单继续处理。支付接口独立校验截止时间，查询也可在扫描关单前显示过期。

## 通知投递

通知采用事务内 Outbox 和至少一次异步投递：

1. 付款确认、签到、开始接诊或完成接诊成功后，通知事件记录器读取预约单快照，渲染固定文案，并在同一 MySQL 事务写入 `notification_outbox_events`。写入失败时业务整体回滚。
2. 启用消息投递时，发布器每秒扫描最多 50 条到期 `PENDING` 事件，发送到 `appoint.notification.exchange`。只有 RabbitMQ 确认且未被退回才标记 `PUBLISHED`；失败累加尝试次数，按 1 秒起步的指数退避重试，最大间隔 60 秒，不设置永久失败终态。
3. 消费者单并发读取 `appoint.notification.queue`，解析事件并幂等写入 `user_notifications`。失败时在同一次消费处理线程内再重试 3 次，间隔 1 秒、2 秒、4 秒，仍失败则拒绝消息一次并进入 `appoint.notification.dlq`。
4. 消息持久化，`messageId` 为事件 UUID。重复投递由事件 ID 和「预约 + 通知类型」唯一约束共同去重。

`appoint.notification.messaging-enabled` 控制拓扑声明、发布器和消费者。本地 `demo`、`dev`、`test` 与 `rabbit-it` 为 `true`；`regression` 和 `mysql-it` 为 `false`，避免自动化测试连接 Broker。每个环境使用自己的 RabbitMQ vhost，队列名不带环境前缀。

通知模块只通过预约模块提供的快照端口读取预约单，不访问就诊人或目录当前表。[事件记录器](../src/main/java/com/qsx/appoint/notification/application/DefaultNotificationEventRecorder.java) · [Outbox发布器](../src/main/java/com/qsx/appoint/notification/application/NotificationOutboxPublisher.java) · [消费者](../src/main/java/com/qsx/appoint/notification/messaging/NotificationConsumer.java)

## 注册与缓存

注册流程向 Mailpit 发出验证码，Redis 按环境保存验证码和一次性注册票据。目录读取优先查 Redis，失败时回源 MySQL，目录写入提交后定向失效缓存。test 调时作用于预约和就诊使用的业务时钟，Redis 过期仍按真实时间计算。

业务时间与状态见[业务流程](business-flow.md)，本地配置与 Flyway 初始化见[环境指南](environments.md)。
