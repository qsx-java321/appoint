# 11 Inbox 幂等异步确认与支付 202 API

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。16/16已于a293afa交付；同步兼容入口后来删除、接口继续深化、消息设计与测试整理也已完成。下文目标/差距及83等数量均按原实施检查点理解，不是当前待办。 当前能力与进度见[项目状态](../../../docs/project-status.md)。

Type: feature
Triage: ready-for-agent
Status: resolved

## 背景和目标

支付成功不再同步确认预约。消费者用 Inbox 幂等驱动预约确认，用户发起尝试与 MockPay 完成均返回 HTTP 202，并通过详情轮询观察“预约确认中”。

## 范围

- `PaymentSucceeded` 版本化 JSON listener，仅反序列化/校验/调用协调入口。
- Inbox 插入与预约确认/库存/序号同事务；提交后 ACK。
- 短暂失败重试，永久损坏进入 DLQ。
- 新尝试/MockPay 完成 HTTP 202；`simulate-success` 保留 dev/test 兼容包装并废弃标记。

## 非范围

- 不实现退款补偿或最终查询模块搬移。

## 依赖关系

Blocked by: 06、10。

## 需要修改的模块

- coordination messaging/application/persistence、appointment application、payment web、初步查询投影。

## 业务不变量

- 重复 eventId 不重复确认库存/序号；业务事务提交后才 ACK。
- `PAID + PENDING_PAYMENT` 是允许的短暂组合，不新增预约领域状态。

## 可验证的验收条件

- Broker 停机时回调仍持久化；恢复后自动确认。
- 重复消息/消费者重启只确认一次；用户可观察 `CONFIRMING_APPOINTMENT` 后最终 `CONFIRMED`。

## 应运行的测试

- H2 application/HTTP 测试。
- MySQL + RabbitMQ Testcontainers：重复、提交前失败、ACK 前失败、DLQ。

## 完成状态

- [x] Inbox consumer/coordinator
- [x] HTTP 202/兼容入口
- [x] 确认中投影
- [x] MySQL/RabbitMQ 验收

## Comments

- 等待 issue 06、10 resolved。
- 2026-10-30：`PaymentSucceeded` 改为版本化 envelope；listener 严格校验 v1，Inbox 与预约确认/库存/就诊序号在同一事务提交，返回后才手工 ACK，永久坏消息 reject 到 DLQ，临时异常 requeue。
- 2026-10-30：MockPay complete/fail 在 HTTP 202 后异步进入统一验签回调；废弃 `simulate-success` 仅在 dev/test 注册并返回 202，其兼容实现也通过支付尝试、MockPay、通知、Outbox 和 Inbox 推进，生产 profile 不暴露 Mock 或旧入口。
- 2026-10-30：预约详情增加目标词汇内的 `workflowStatus`，覆盖 `PAID + PENDING_PAYMENT → CONFIRMING_APPOINTMENT`；原无资金取消组合继续可读但不臆造目标词汇外状态。
- 2026-10-30：H2 73 项、MySQL Testcontainers 15 项、MySQL + RabbitMQ reliability 8 项全部通过。可靠性覆盖 Broker 停机积压/恢复确认、重复投递单副作用和永久坏消息 DLQ。

