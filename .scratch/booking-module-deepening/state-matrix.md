# 四模块行为矩阵（2026-11-09）

核对代码基线：`ea5bbeb`。该提交已经包含交接所述的首轮 02/04/05/11/12 实现；以下描述当前源码，不将改造前 `9d20e30` 的缺陷当作目标。01 新增测试只固定行为，不修改生产代码。表中测试名相对 `src/test/java/com/qsx/appoint/`。

## 生命周期与错误顺序

| 入口/条件 | 结果、返回及副作用 | 测试证据 |
| --- | --- | --- |
| 过期：预约不存在 | false，无写入 | coordination/PaymentSucceededProcessorIntegrationTest.expirationBeforeDeadlineDoesNotRequirePaymentButCancellationChecksOwnerFirst |
| 过期：非待支付 | false，不锁支付 | 同类 expiryUsesPersistedPaidAtEvenWhenConfirmationIsDelayed 第二次调用 |
| 过期：待支付但未到期，即使支付缺失 | false，支付完整性不提前校验 | 同类 expirationBeforeDeadlineDoesNotRequirePaymentButCancellationChecksOwnerFirst |
| 到期、未付/支付中 | EXPIRED/CLOSED，释放 held、删除 claim，true | appointment/AppointmentLifecycleIntegrationTest.expiredPaymentIsRejectedAndScannerClosesOrder |
| 到期、已付且 paidAt 早于截止 | CONFIRMED，held 转 confirmed、分号，true；重复 false | coordination/PaymentSucceededProcessorIntegrationTest.expiryUsesPersistedPaidAtEvenWhenConfirmationIsDelayed |
| 到期、已付且 paidAt 等于/晚于截止 | EXPIRED、释放 held、删除 claim，退款 APPOINTMENT_NOT_CONFIRMABLE，true | 同上 |
| 对账待支付已付，paidAt 缺失/等于/晚于截止 | EXPIRED、释放 held、删除 claim、退款 APPOINTMENT_NOT_CONFIRMABLE，true | 同类 reconciliationUsesPaidAtIncludingMissingAndExactDeadline |
| 对账待支付已付，paidAt 早于截止，尚未到期也可处理 | CONFIRMED 并分号，true；重复 false | 同上 |
| 对账关闭已付 | 只确保退款 RECONCILIATION_CLOSED_PAID，不再次释放 | appointment/AppointmentLifecycleIntegrationTest.reconciliationCreatesMissingRefundForClosedPaidAppointment |
| 对账关联错误 | 记录人工项，不自动修复 | 同类 reconciliationReportsAssociationMismatchAsManualItem |
| 取消：不存在/非本人 | RESOURCE_NOT_FOUND/FORBIDDEN；归属检查在支付完整性之前 | coordination/PaymentSucceededProcessorIntegrationTest.expirationBeforeDeadlineDoesNotRequirePaymentButCancellationChecksOwnerFirst；appointment/AppointmentLifecycleIntegrationTest.onlyOwnerCanCancelAppointment |
| 取消：待支付 UNPAID/PAYING，未到期 | CANCELLED/CLOSED，释放 held、删 claim，false | coordination/PaymentSucceededProcessorIntegrationTest.pendingCancellationPreservesRefundReasonAndBoolean |
| 取消：待支付 UNPAID/PAYING，已到期 | EXPIRED/CLOSED，释放 held、删 claim，false | 同类 cancellationAfterExpiryKeepsCancellationRefundReason |
| 取消：待支付 PAID，未到期/已到期 | CANCELLED/EXPIRED，释放 held、删 claim，退款 APPOINTMENT_CANCELLED，true | 以上两个用例 |
| 重复取消关闭预约 | CLOSED/REFUNDED 返回 false，REFUND_PENDING 返回 true；其他支付状态 INTERNAL_ERROR，不重复释放 | 同类 repeatedCancellationOfClosedAppointmentChecksPaymentState |
| 已确认及时取消 | 释放 confirmed、删 claim、退款 APPOINTMENT_CANCELLED，true | appointment/AppointmentLifecycleIntegrationTest.confirmedAppointmentCancellationReleasesInventoryAndRequestsRefundAsynchronously |
| 已确认取消截止已到 | CANCELLATION_DEADLINE_REACHED，无写入；边界为严格早于开诊前24小时 | coordination/PaymentSucceededProcessorIntegrationTest.confirmedCancellationAtExactTwentyFourHoursIsRejected |

## 支付成功事件

| 条件 | 结果及副作用 | 测试证据 |
| --- | --- | --- |
| 格式/版本/必填字段无效 | VALIDATION_ERROR；不插 Inbox，直接 DLQ | coordination/PaymentSucceededEventCodecTest、coordination/messaging/PaymentSucceededListenerTest |
| 同 eventId | DUPLICATE；无重复库存/序号/退款 | coordination/PaymentSucceededProcessorIntegrationTest.inboxAndConfirmationCommitOnceForDuplicateEvent |
| 待支付，paidAt 早于截止 | CONFIRMED，Inbox、库存、序号和预约原子提交 | 同上；消息晚到由 PaymentConfirmationReliabilityIT 覆盖 |
| 待支付，paidAt 等于或晚于截止 | EXPIRED、释放 held、删 claim、退款 APPOINTMENT_NOT_CONFIRMABLE、RefundRequested Outbox 与 Inbox 原子提交 | 同类 paymentConfirmedAtExactDeadlineIsRefundedWithoutRestoringAppointment（等于与晚于） |
| 不同 eventId、同一迟到事实 | 只保留一张退款单，不再释放或推进序号 | 同上 |
| 已确认、新 eventId | ALREADY_CONFIRMED，保留 Inbox，不重读支付 | 同类 newEventForConfirmedAppointmentDoesNotRequirePaymentLookup |
| 已关闭、新 eventId | REFUND_REQUIRED，只确保退款，不恢复预约或释放库存 | 同上迟到事件第二 eventId；同类 lateEventForClosedAppointmentOnlyEnsuresRefund 覆盖三种历史/当前终态 |
| 删除占用失败 | 回滚 Inbox、库存和预约，不留退款 | 同类 failedClaimRemovalRollsBackInboxAndInventoryInRealTransaction |
| 退款创建失败 | 应回滚上述全部事实 | 同类 failedRefundCreationRollsBackCloseInventoryClaimAndInbox（测试事务外调用真实代理事务） |

## 通知与查询

| 条件 | 结果 | 测试证据 |
| --- | --- | --- |
| 同 notificationId/hash | 原处理结果、duplicate=true，无新资金事实和 Outbox | payment/PaymentNotificationMySqlIT.concurrentDuplicateSuccessHasOneMoneyEffectAndStableEvent |
| 同 notificationId、不同 hash | IDEMPOTENCY_CONFLICT，优先于订单状态 | 同类 sameNotificationIdWithDifferentPayloadIsRejected |
| 签名无效 | 拒绝，不推进资金 | 同类 forgedSignatureDoesNotChangeMoneyState |
| 未知渠道订单 | REJECTED_UNKNOWN_ORDER 审计 | payment/application/PaymentNotificationBoundaryTest |
| 支付关联缺失 | INTERNAL_ERROR | payment/application/PaymentNotificationBoundaryTest |
| 金额/币种不匹配 | REJECTED_AMOUNT_OR_CURRENCY 审计，优先于状态判断 | 同类 amountOrCurrencyMismatchIsAuditedWithoutChangingMoneyState |
| 成功 + PROCESSING/PAYING | SUCCEEDED，attempt/payment/审计/PaymentSucceeded 原子提交 | 同类 concurrentDuplicateSuccessHasOneMoneyEffectAndStableEvent |
| 成功 + PROCESSING/CLOSED | SUCCEEDED_REFUND_PENDING，同事务资金/审计/Outbox/退款，原因 LATE_PAYMENT | appointment/AppointmentLifecycleIntegrationTest.latePaymentAfterPendingAppointmentCancellationIsRecordedAndRefundedWithoutRestoringAppointment |
| 成功 + SUCCEEDED/PAID | ALREADY_SUCCEEDED，无重复事件 | payment/application/PaymentNotificationPolicyTest |
| 失败 + PROCESSING/PAYING | FAILED，payment 恢复 UNPAID | payment/PaymentNotificationMySqlIT.failedAttemptCanRetryAndLaterSuccessWins |
| 失败 + SUCCEEDED 或 PAID | IGNORED_LATE_FAILURE，不覆盖资金事实 | 同类 lateFailureCannotOverwriteSuccessfulMoneyFact |
| 其他通知状态组合 | REJECTED_STATE 审计 | payment/application/PaymentNotificationPolicyTest |
| 查询合法/历史 workflow | pending 三态、confirmed、三种关闭退款态、过期、历史 null | bookingquery/application/BookingWorkflowProjectorTest |
| 查询非法组合 | INTERNAL_ERROR | 同上 inconsistentCombinationIsRejected |
| 查询不存在/他人数据 | RESOURCE_NOT_FOUND/FORBIDDEN，先于支付完整性 | appointment/AppointmentQueryIntegrationTest.anotherUserCannotReadAppointment；不存在顺序静态核对 |
| 查询缺支付/支付归属不符 | INTERNAL_ERROR | 同类 appointmentQueriesFailWhenPaymentIsMissing、appointmentQueriesFailWhenPaymentBelongsToAnotherUser |
| 查询权限兼容 | 到期待支付 UNPAID/PAYING 仍 canCancel；pending+PAID 不可取消；canPay 只限未到期 UNPAID | bookingquery/application/AppointmentResponseAssemblerTest；保持与写入口差异 |

## 创建与锁风险

- `appointment/BookingMidnightIntegrationTest` 已在 `30b8d28` 固定最终占号跨零点整笔回滚和已成功订单跨日幂等重放。
- 创建锁序仍为设置→场次→就诊人；实际占号前重新读取日期；支付截止固定创建后15分钟。
- 改造前回调 attempt→payment、T2失败 attempt→payment、worker refund→payment 是静态锁环风险，未宣称复现过死锁。
- `ea5bbeb` 已将回调、T2、worker claim/complete/fail 改为只读定位→payment 父锁→子记录，并锁后复验关联。当前 MySQL 并发测试尚不足以证明全部父锁路径；05仍需确定性交错测试。
- 真实 RabbitMQ 已有发布重复、confirm/return、Broker恢复及 Inbox 重复覆盖；提交前/ACK前窗口和可靠重放仍须12/09逐项验收。

本矩阵中的迟到事件原子退款失败、历史状态参数化、MySQL锁竞争和消息故障窗口分别交由11、05、12/09验收；不将它们计为01新增保证。01固定现有行为及风险证据，历史数字不替代本次快照结果。

11新增 `coordination/LatePaymentMySqlIT.lateEventsCancellationAndExpirySerializeToOneReleaseAndRefund`，用有界屏障及Future并发两个不同成功事件、用户取消和过期；校验只释放一次、序号不变、每单一张退款及一个RefundRequested。Compose扩展使用真实HTTP/回调/RabbitMQ验证迟到收款，时间缩短仅在独立验收数据库执行。11独立快照三层及扩展Compose验收已通过，实际数量见baseline。

## 12 新增传输与故障覆盖

- common/messaging/ConfirmedMessagePublisherTest：确认成功才返回；NACK、return、超时、连接异常均失败。监听器测试证明转发失败不ACK原消息。
- payment/OutboxPublisherReliabilityIT.retryTransportDelaysBothRetriesAndStopsAfterThreeAttempts：真实RabbitMQ承载三次手动消费、两次TTL重试及DLQ；业务processor/codec为测试替身，仅承担传输保证。
- 同类 replayRequiresRoutingConfirmationAndResetsCountersWithoutChangingEventId：实际去掉绑定触发mandatory return，原消息仍在DLQ；路由恢复后重放清除计数并保留正文/eventId。
- coordination/PaymentConfirmationReliabilityIT.committedConfirmationSurvivesLostAckAndBrokerRedelivery：真实MySQL代理事务已提交，ACK前注入IOException并物理关闭RabbitMQ channel，真实重投后Inbox、库存和序号只有一次效果。
- 完整12快照常规166、MySQL25、可靠性13及Compose全部通过，零跳过。
