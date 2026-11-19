# 模块设计

项目采用模块化单体，一套 Spring Boot 后端提供 REST API，Vue 前端通过 `/api` 访问。MySQL 保存业务数据，Redis 用于目录缓存与注册验证，Mailpit 接收本地注册邮件。RabbitMQ 随环境启动，预约、支付和过期链路在 MySQL 内完成。

| 模块 | 职责 |
| --- | --- |
| `auth`、`user`、`patient` | 邮箱注册、JWT 登录、角色和就诊人 |
| `catalog` | 医疗目录、场次查询、统一预约设置和医生账号绑定 |
| `appointment`、`payment` | 暂占与库存、预约凭证、支付单 |
| `coordination` | 创建预约、付款确认、取消和过期的事务编排 |
| `visit`、`bookingquery` | 签到与接诊写入、用户预约详情和医生工作台查询 |
| `localenv` | 环境就绪探测、启动互斥、数据初始化、重置和 test 调时 |

## 核心数据

- `schedules` 按医生、日期和场次类型唯一，保存库存、费用快照和下一个就诊序号。
- `appointment_orders` 保存预约凭证和最晚支付时间，与 `payment_orders` 一对一。
- `appointment_create_requests` 按账号记录幂等键和创建回执。`appointment_active_claims` 用唯一约束限制同场次同就诊人和同证件的有效占用。
- `visit_records` 在签到时创建，一张预约至多一条，保存就诊进度和动作时间。

## 事务与锁序

| 操作 | 写入和并发处理 |
| --- | --- |
| 创建 | 先登记幂等键，再按需建场并用条件更新暂占名额。锁定就诊人后复验下单资格，创建预约单、支付单并保存回执。失败时整笔回滚。同键冲突在回滚后查询已提交回执。 |
| 付款 | 按场次 → 预约单 → 支付单加锁。锁后采样业务时间，在同一事务记录付款、确认名额并分配序号。已付款请求重试沿用原支付时间和序号。 |
| 取消与过期 | 沿用付款锁序。过期处理锁后复核待支付状态、截止时间和支付单。关单、释放暂占和删除有效占用在同一事务提交。 |
| 签到与接诊 | 按预约归属和当前进度校验资格，仅写就诊记录。唯一约束和条件更新保护首次成功的动作时间。 |

[创建事务](../src/main/java/com/qsx/appoint/coordination/application/BookingCreationTransaction.java) · [预约生命周期协调器](../src/main/java/com/qsx/appoint/coordination/application/DefaultBookingLifecycleCoordinator.java) · [就诊服务](../src/main/java/com/qsx/appoint/visit/application/DefaultVisitService.java)

[过期任务](../src/main/java/com/qsx/appoint/appointment/job/AppointmentExpirationJob.java)默认在每轮结束 30 秒后开始下一轮，按支付截止时间和预约 ID 每批查询最多 100 条，逐单开启事务处理。单笔失败记日志，其余订单继续处理。支付接口独立校验截止时间，查询也可在扫描关单前显示过期。

## 注册与缓存

注册流程向 Mailpit 发出验证码，Redis 按环境保存验证码和一次性注册票据。目录读取优先查 Redis，失败时回源 MySQL，目录写入提交后定向失效缓存。test 调时作用于预约和就诊使用的业务时钟，Redis 过期仍按真实时间计算。

业务时间与状态见[业务流程](business-flow.md)，本地配置与 Flyway 初始化见[环境指南](environments.md)。
