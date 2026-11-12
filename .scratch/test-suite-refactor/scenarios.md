# 保障与迁移映射

## 不可丢失的保障

| 场景 | 验证位置（基线→最终，未说明则保留） |
| --- | --- |
| 幂等、重放、账号隔离 | AppointmentIdempotencyIntegrationTest / AppointmentLifecycleMySqlIT |
| 库存、防超卖、序号、快照 | ScheduleInventoryIntegrationTest / AppointmentLifecycleMySqlIT |
| 日期、截止、午夜、支付期限 | BookingTimeRulesTest / OnDemandBookingHttpTest / BookingMidnightIntegrationTest |
| 查询、权限、历史订单 | AppointmentQueryIntegrationTest / PatientIntegrationTest / CatalogManagementIntegrationTest |
| 支付验签、回调、事务 | PaymentGatewayContractTest / PaymentNotification* / PaymentAttempt* |
| Inbox、取消、迟到支付、退款 | PaymentSucceededProcessorIntegrationTest / LatePaymentMySqlIT / PaymentParentLockMySqlIT |
| Outbox原子性、历史wire | PaymentOutboxTransactionTest / MessageConsumerContractTest |
| Broker恢复、重发、提交/ACK、DLQ | OutboxPublisherReliabilityIT / PaymentConfirmationReliabilityIT |
| 医生绑定、签到、接诊 | VisitHttpTest / VisitWorkflowMySqlIT / VisitSchema* |
| 迁移与数据库约束 | InitialSchemaMigrationTest / PaymentPersistence* / PaymentMigrationMySqlIT / CatalogManagementMySqlIT |
| prod禁Mock、旧入口缺失 | MockPayProductionProfileTest / LegacyDevRouteAbsenceTest / LegacyTestRouteAbsenceTest |
| 模块边界和规则负例 | ModuleDependencyTest及archfixture |
| 启动与全HTTP | PatientJourneyHttpMySqlIT / scripts/compose_acceptance.py |

## 删除/合并责任（执行前先登记）

- MessagingBaselineContractTest：生产Long/null→PaymentOutboxTransactionTest；消费者双ID兼容→MessageConsumerContractTest；八历史样本格式→消息契约参数化测试。05执行后记录实际结果。
- Listener中转发失败、第三次失败的重复传输分支→RabbitConsumerFailureHandlerTest；两个Listener各自保留实际失败路由与业务接线。05执行后记录具体方法。
- 其余原方法默认保留；原清单见 verification/original-methods.json。名称调整须记录。

## 旧消息场景

原 [S01～S32](../messaging-design-refactor/scenarios.md) 继续作为门禁；最终逐项映射见 verification/message-scenarios.md。

## 第04步历史暂停检查点

01～04没有删除测试类/方法；原保障位置保持。历史迁移终态方法改名已在verification/step-04-method-audit.md登记。消息基线和Listener合并尚未实施，旧S01～S32仍使用上一轮位置。四类最终验收和最终场景审计留给08/07。

## 05迁移明细（已验收）

| 原位置 | 新责任与保留内容 |
| --- | --- |
| MessagingBaselineContractTest唯一方法中的生产mapper断言 | PaymentOutboxTransactionTest.productionMapperKeepsTextLongAndExplicitNull：Spring实际mapper、Long文本、显式null |
| 同方法八份样本循环 | MessageConsumerContractTest.historicalFixturesKeepTheirEnvelopeShape（8参数）；原资源不修改 |
| 同方法PaymentSucceeded数字/文本ID断言 | MessageConsumerContractTest.historicalIntegerIdsAndAdditionalFieldsRemainCompatible：实际解析支付paymentOrderId=51、退款refundId=71，退款paymentOrderId仍由Codec关联校验 |
| 两个Listener.failedRetryPublicationRequeuesOriginal | RabbitConsumerFailureHandlerTest.uncertainPublicationRequeuesOriginalWithoutAcknowledging；额外验证NACK的IOException传播 |
| 两个Listener.thirdFailureIsDeadLettered | RabbitConsumerFailureHandlerTest.rejectsExhaustedOrMalformedHeadersWithoutOverflow，覆盖Integer/Long耗尽；真实DLQ保留 |
| 两个Listener.ackFailureAfterCommittedProcessingPropagatesForRedelivery | 改名ackFailureAfterSuccessfulProcessingPropagatesForRedelivery；对象单测只证明处理成功返回，真实提交/重投仍由PaymentConfirmationReliabilityIT负责 |

两个Listener各保留5方法：解码正文→正确业务接口→ACK、成功不检查非法头、非法业务输入、固定重试路由/头、永久非法解码、ACK异常（前两项由同一方法验证）。原TTL/确认超时/原文/ID断言集中在公共处理器；业务监听器没有改成Mock失败处理器。

## 07审计结果

[逐方法迁移审计](verification/method-audit.md)覆盖原260个方法；[当前测试清单](verification/current-inventory.md)记录各类层次、profile、时钟与隔离。原八份样本与架构负例19文件摘要未变；S01～S32当前入口均已映射，最终结果由08新执行验收确认。测试写法见[测试约定](../../docs/testing.md)。

## 08最终结果

常规340、MySQL38、可靠性16、隔离curl92请求全部通过。S01～S32已逐项关联新执行证据，原方法与独有断言没有无归属项。见[最终验收](verification/final-acceptance.md)。
