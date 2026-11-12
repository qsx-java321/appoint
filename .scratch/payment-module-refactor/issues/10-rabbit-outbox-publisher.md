# 10 RabbitMQ 拓扑与 Outbox 发布可靠性

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。16/16已于a293afa交付；同步兼容入口后来删除、接口继续深化、消息设计与测试整理也已完成。下文目标/差距及83等数量均按原实施检查点理解，不是当前待办。 当前能力与进度见[项目状态](../../../docs/project-status.md)。

Type: feature/infrastructure
Triage: ready-for-agent
Status: resolved

## 背景和目标

将 Outbox 以至少一次语义可靠发布到 durable RabbitMQ 拓扑，支持 confirm、mandatory return、退避重试、FAILED 告警和 DLQ。

## 范围

- 引入 Spring AMQP 与 RabbitMQ Testcontainers。
- 可重复声明 exchange/queue/binding/retry/DLX/DLQ。
- 按索引顺序、行锁/`SKIP LOCKED` 分批发布；confirm 后标记 `PUBLISHED`。
- 发布失败退避、达到阈值 `FAILED`，不删除事件。

## 非范围

- 不实现业务消费者或对账仪表盘。

## 依赖关系

Blocked by: 09。

## 需要修改的模块

- payment outbox publisher、Rabbit 配置、测试 profile/containers。

## 业务不变量

- MySQL 是事件真相；Broker 停机不回滚已提交支付。
- 发布成功但标记前崩溃允许重复，绝不以删除/至多一次掩盖问题。

## 可验证的验收条件

- confirm、mandatory return、停机积压/恢复、标记前崩溃重复、退避和 DLQ 拓扑测试通过。
- 多发布器不会长期重复占用同一批事件。

## 应运行的测试

- RabbitMQ Testcontainers 集成测试。
- MySQL + RabbitMQ 双容器可靠性测试。

## 完成状态

- [x] 拓扑
- [x] publisher/confirm
- [x] 退避/FAILED
- [x] RabbitMQ Testcontainers 验收

## Comments

- 等待 issue 09 resolved。
- 2026-10-29：声明 durable domain/retry/DLX、三条业务队列及各自 retry/DLQ；消息使用版本化 JSON、持久投递和稳定 `eventId`。
- 2026-10-29：实现按索引扫描、MySQL `FOR UPDATE SKIP LOCKED`、correlated confirm、mandatory return、指数退避及耗尽 `FAILED` 告警；成功 confirm 后才标记 `PUBLISHED`。
- 2026-10-29：新增 `reliability-it` profile。真实 MySQL 8.4 + RabbitMQ 4.1 覆盖 confirm、标记前崩溃重复、Broker 停机恢复、mandatory return、FAILED、DLQ 和双发布器竞争；H2 70 项及 reliability 6 项通过。

