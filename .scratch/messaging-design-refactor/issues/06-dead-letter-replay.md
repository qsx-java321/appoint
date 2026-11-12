# 06 DLQ 重放接口与适配器

Type: refactor
Triage: ready-for-agent
Status: ready-for-agent
Blocked by: 05

依据：[spec 第 7.6、9 节](../spec.md)。责任场景：S25～S26。

## 目标

把受控重放能力表示为应用接口，将 Rabbit 队列读取、路由、转发与确认实现移入 messaging，不新增 REST 或恢复能力。

## 文件与动作

1. `payment/application/DeadLetterReplayService` 由具体服务改为接口。
2. 新增 `ReplayTarget`（三个固定逻辑目标）与 `ReplayResult`（EMPTY/REPLAYED + 可空 eventId）。
3. 新增 `payment/messaging/RabbitDeadLetterReplayService`，搬迁原实现并映射既有 DLQ。
4. 更新 `OutboxPublisherReliabilityIT`、`PaymentConfirmationReliabilityIT` 及直接 new 的测试，统一依赖应用接口或显式测试 Adapter。
5. 去除旧实现 Bean，避免旧新同时扫描；更新受影响文档路径但不宣称整轮完成。

## 必须保留

- 一次最多领取一条，只允许原三个目标，不开放任意队列读取。
- 原正文与 eventId 不变；不补造缺失 ID，不绕开消费者幂等。
- 清除 `payment-succeeded-attempt`、`refund-attempt`、expiration，其他必要属性保留。
- 转发 confirm 且无 return 后 ACK；解析、路由、发布或确认失败时尝试 NACK/requeue 并传播。
- 固定业务队列按原映射选路；退款结果根据原成功/失败事件类型分流。
- 不写 Outbox/Inbox/资金/库存，不自动重置 FAILED；退款结果仍无监听器。
- 不顺便把完整消费者 Codec 校验塞进重放器，避免重放能力与业务变更耦合。

## 验证

新增重放应用契约/Adapter 单测，覆盖空队列、三个目标、无 eventId 的转发结果、未知退款结果类型、原文/属性、发布失败、ACK/NACK 异常。

执行现有真实重放 ReliabilityIT，并确认源消息失败保留、成功移出、eventId 不变、计数清除。新应用接口的 EMPTY 与 REPLAYED 要能区分，不把缺 ID 的已转发消息误报为空。

旧服务具体类删除或迁包后执行 `./mvnw clean test`，避免残留旧 class 造成重复扫描；不靠放宽 Bean/架构约束解决。

## 完成条件

- [ ] 应用接口没有物理队列字符串、AMQP 类型或具体 Rabbit 实现依赖。
- [ ] 三个逻辑目标精确映射既有白名单，原文及确认顺序保持。
- [ ] 旧实现和构造调用迁移完整，运行时只有一个实现。
- [ ] 聚焦/真实重放通过，场景和接口注释更新。

## Comments

- 2026-11-12：任务已定义，依赖 05；未新增管理入口或恢复自动化。
