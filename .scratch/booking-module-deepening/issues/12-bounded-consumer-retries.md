# 12 消费有界重试与可靠转发

Type: fix
Triage: ready-for-agent
Status: resolved
Blocked by: 01

## 背景和目标

依据用户确认的业务决定修复当前缺口。

## 范围

成功事件处理最多三次，间隔一秒；无效或耗尽进入 DLQ。重试和重放发送经 confirm 与 mandatory return 检查后才 ACK；保留 eventId 并重置重放计数。

## 非范围

不修改数据库 schema、事件版本或正式 HTTP 契约；不自动修复历史数据。

## 前置依赖

01 基线与状态矩阵。

## 需要修改的模块和主要文件

`coordination/messaging/PaymentSucceededListener.java`、`payment/messaging/RefundRequestedListener.java`、`payment/application/DeadLetterReplayService.java`、`RabbitOutboxMessageSender.java` 和公共确认发布器；涉及行为测试及 RabbitMQ Testcontainers。

## 目标代码形态

复用现有 application interface、事务和消息拓扑，业务分支清晰，传输确认逻辑集中。

## 改造后的业务逻辑

成功事件处理最多三次，间隔一秒；无效或耗尽进入 DLQ。重试和重放发送经 confirm 与 mandatory return 检查后才 ACK；保留 eventId 并重置重放计数。

## 必须保持的不变量

事务原子性、库存守恒、Inbox 幂等、退款唯一性、条件更新及 eventId 不变。

## 可验证验收条件

三次处理、间隔一秒、无效消息直达 DLQ；confirm NACK、return、超时或连接失败不提前 ACK；DLQ 重放保留 eventId 并清除旧重试计数；事务提交后 ACK 前重投仅一次业务副作用。

## 应运行的测试

`./mvnw test`、`./mvnw -Pmysql-it verify`、`./mvnw -Preliability-it verify`。

## 独立交付与回退

整组回退 Listener、公共发布器、Outbox 和 DLQ 重放调用及对应测试；保留队列和全部消息，并记录重新暴露的无限重投与转发确认风险。

## 完成状态清单

- [x] 实现与必要测试
- [x] 三层验证及文档记录

## Comments

- 2026-11-08：根据用户接受的方案创建，待完成依赖及完整验收。

- 2026-11-08：已实现三次有界重试、确认后 ACK、退款重试与 Outbox/DLQ 重放确认；新增单元和真实 RabbitMQ 测试，尚待最终验收。

- 2026-11-09：01已在c4f1dc0验收；11已在cb6931e验收。现有生产实现来自ea5bbeb。本阶段补充ConfirmedMessagePublisher的ACK/NACK/mandatory return/超时/连接失败单测；真实RabbitMQ逐次收取重试，检查两次间隔均至少900ms（配置1000ms）且总共调用三次；真实mandatory return重放失败保留DLQ原消息，恢复路由后保留eventId并清除两类计数/TTL。真实MySQL提交后注入ACK前IOException，再物理关闭channel触发Broker重投，验证Inbox、库存及序号单副作用。两个可靠性类聚焦运行均通过；完整快照 `/tmp/booking-12-snapshot-5zpor6w1` 三层与Compose门禁运行中，保持claimed。

- 2026-11-09：独立快照常规166、MySQL25、可靠性13及Compose全链路全部通过，零失败/错误/跳过。三次有界处理、两次TTL延迟、确认后ACK、DLQ重放和提交后ACK故障窗口已验证；先前生产实现随ea5bbeb提交，本阶段提交验收证据。
