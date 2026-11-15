---
status: accepted
---

# ADR 0004：支付独立为模块，并以可靠事件协调预约确认

> ADR 0005 取消了新流程中的排班关闭和停诊操作；本文停诊相关描述仅用于历史版本。可靠事件、支付截止、迟到收款退款和对账决策继续有效。

## 决策时背景与方案

提出本决策时，模拟支付由预约生命周期事务直接修改支付单、预约单和库存，能够提供强一致性，但 `payment` 与 `appointment` 包双向依赖，支付渠道、失败重试、异步回调和退款补偿没有独立模型。项目决定继续保持一个 Spring Boot 应用和同一 MySQL，将支付业务、预约业务和跨模块协调拆成独立模块：当时以统一 `PaymentService` 概括支付单、支付尝试、退款和渠道回调的接口职责，以 `PaymentGateway` 作为 MockPay、WeChat Pay、Alipay 等外部渠道的 seam；预约模块只管理预约、号源库存和就诊序号；协调模块只通过两个模块的接口和业务事件连接它们，写模块不得直接使用其他模块的 Mapper。当前落地接口已按用例拆分，见后文，不存在需要查找或调用的统一 `PaymentService`。

支付渠道异步回调先在同一数据库事务中保存资金结果、回调幂等记录和 Transactional Outbox 事件，随后由 Outbox 发布器投递到 RabbitMQ。协调消费者按至少一次投递设计，以 Inbox `eventId` 幂等处理 `PaymentSucceeded`，再确认预约和库存。支付成功与预约确认允许短暂最终一致；RabbitMQ 不可用不阻止已经持久化的支付回调，恢复后由 Outbox、DLQ 和对账任务继续收敛。到达支付截止时间时，协调模块按场次、预约单、支付单顺序加锁并检查本地支付真相：截止前已确认收款的预约不能过期，仍未支付的预约释放号源；预约取消、过期或截止后收到的迟到支付不恢复预约，而进入异步原路全额退款。

该决定局部取代 ADR 0001 中由单个 `AppointmentLifecycleService` 同步推进全部支付状态的实现选择，但保留数据库截止时间、统一锁顺序、条件更新和库存守恒。选择 RabbitMQ 和最终一致性增加了 Outbox、Inbox、重试、DLQ、对账和用户可见“预约确认中”的复杂度，换取支付渠道独立、异步回调真实性和后续接入真实支付渠道时稳定的模块 seam。第一阶段只实现 dev/test 环境的 MockPay Adapter，不接入真实资金渠道。

## 2026-11-09 实现落实

四模块深度化已完成。`AppointmentLifecycle` 内部封装场次→预约锁定、确认库存/序号及关闭释放；`PaymentLifecycle` 封装支付锁定及关闭。锁与生命周期写操作要求 MANDATORY，锁定 Case 只在获取它的事务内使用。coordination 通过完整操作和纯 Policy 编排，不再传库存更新或下一序号；支付内部统一 payment 父锁先于子记录。查询使用只读 Projector/Assembler。

迟到支付释放和有界重试按 [ADR 0006](0006-late-payment-and-consumer-retries.md) 落实。旧 dev/test 同步模拟入口及兼容尝试已删除。以上是既有模块化单体和可靠事件决定的实现收口，没有新增部署架构或领域术语；最终 interface、事务及事件契约见 [四模块代码设计](../module-design.md)，实际验证见 [测试与验证](../testing.md)。

## 当前接口与恢复边界（2026-11-10）

支付入口分别使用 `PaymentOrderCreationService`、`PaymentAttemptService`、`PaymentNotificationIngress`、`PaymentLifecycle`、`PaymentRefundOperations` 和 `RefundWorker`；创建、取消、过期和确认的跨模块规则由 coordination 编排，详情由 bookingquery 联查。上述拆分落实本 ADR 的职责边界，没有改变模块化单体决定。

支付成功处理使用 Inbox 与预约动作同事务提交；退款执行使用短事务、事务外渠道调用及原 refundNo 幂等，不能将前者的回滚保证泛化到退款全过程。Outbox 发布失败耗尽后保留 FAILED，DLQ 重放和对账也不会自动重置该状态；退款结果队列已声明但没有监听器。具体恢复能力以现行模块设计和 runbook 为准，原文“恢复后继续收敛”不表示所有异常均能自动恢复。
