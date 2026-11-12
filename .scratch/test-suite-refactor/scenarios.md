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
