# 最终场景证据

日期：2026-11-12。各场景对应实际测试/核对，命令与限制见 commands.md。不是按测试数量代替覆盖。

| 场景 | 状态 | 实际证据 |
| --- | --- | --- |
| S01 | 通过 | MessagingBaselineContractTest.productionMapperKeepsTextLongAndNullWhileHistoricalFixturesRemainReadable；旧v1八份样本 |
| S02 | 通过 | PaymentOutboxTransactionTest.storedEventPreservesHistoricalContractAndRollsBackWithCaller；真实支付/退款生产链路回归 |
| S03 | 通过 | PaymentOutboxTransactionTest.recordingRequiresTheBusinessTransaction；PaymentNotificationAtomicityTest.writeFailureRollsBackMoneyAttemptAuditAndEventThenAllowsRetry |
| S04 | 通过 | PaymentNotificationMySqlIT.concurrentDuplicateSuccessHasOneMoneyEffectAndStableEvent；退款唯一申请/原ID发布回归 |
| S05 | 通过 | MessageConsumerContractTest.historicalIntegerIdsAndAdditionalFieldsRemainCompatible；真实无metadata消费夹具 |
| S06 | 通过 | MessageConsumerContractTest.rejectsIdsWithoutTruncationOrImplicitDefaults |
| S07 | 通过 | MessageConsumerContractTest.rejectsWrongEnvelope |
| S08 | 通过 | PaymentSucceededListenerTest/RefundRequestedListenerTest.permanentInvalidMessageDoesNotRetry、invalidBusinessInputIsRejectedWithoutRetry；真实永久消息DLQ |
| S09 | 通过 | OutboxPublisherReliabilityIT.confirmedPersistentMessageMarksOutboxPublishedInDurableTopology |
| S10 | 通过 | OutboxPublisherReliabilityIT.mandatoryReturnRetriesThenExhaustedEventBecomesFailed、brokerOutageKeepsPendingEventAndRecoveryPublishesIt |
| S11 | 通过 | OutboxPublisherReliabilityIT.concurrentPublishersUseSkipLockedAndPublishOneCopy |
| S12 | 通过 | OutboxPublisherReliabilityIT.databaseMarkFailureRollsBackAfterBrokerAcceptedThenReusesOriginalEvent：同批先前标记也回滚、实际消费者只确认一次 |
| S13 | 通过 | PaymentSucceededProcessorIntegrationTest.inboxAndConfirmationCommitOnceForDuplicateEvent、newEventForConfirmedAppointmentDoesNotRequirePaymentLookup；LatePaymentMySqlIT |
| S14 | 通过 | PaymentSucceededProcessorIntegrationTest.failedClaimRemovalRollsBackInboxAndInventoryInRealTransaction、failedRefundCreationRollsBackCloseInventoryClaimAndInbox |
| S15 | 通过 | PaymentSucceededProcessorIntegrationTest.paymentConfirmedAtExactDeadlineIsRefundedWithoutRestoringAppointment、expiryUsesPersistedPaidAtEvenWhenConfirmationIsDelayed |
| S16 | 通过 | PaymentConfirmationReliabilityIT.committedConfirmationSurvivesLostAckAndBrokerRedelivery |
| S17 | 通过 | 两个ListenerTest.successfulBusinessDoesNotInspectMalformedRetryHeader、ackFailureAfterCommittedProcessingPropagatesForRedelivery |
| S18 | 通过 | RabbitConsumerFailureHandlerTest.forwardsOriginalWithNextCountBeforeAcknowledging；OutboxPublisherReliabilityIT.retryTransportDelaysBothRetriesAndStopsAfterThreeAttempts |
| S19 | 通过 | 两个ListenerTest.thirdFailureIsDeadLettered；真实TTL/DLQ测试 |
| S20 | 通过 | 两个ListenerTest.failedRetryPublicationRequeuesOriginal；RabbitConsumerFailureHandlerTest.retryAckFailureEscapesWithoutRequeueOrSecondPublication |
| S21 | 通过 | RabbitConsumerFailureHandlerTest.rejectsExhaustedOrMalformedHeadersWithoutOverflow；两个Listener成功不查非法头 |
| S22 | 通过 | PaymentParentLockMySqlIT.refundClaimLocksParentBeforeWaitingForRefund、duplicateWorkersAndRefundRequestKeepSuccessWhenSlowerCallFails |
| S23 | 通过 | PaymentConfirmationReliabilityIT.refundGatewayFailureRetriesThenMovesToDlqWithoutRestoringAppointment：spy验证渠道refundNo实际三次调用 |
| S24 | 通过 | PaymentParentLockMySqlIT.successfulGatewayWithFailedLocalCompletionRetriesTheSameRefundNumber：相同refundNo、相同providerRefundNo、本地收敛 |
| S25 | 通过 | DeadLetterReplayContractTest.logicalTargetsKeepOriginalBodyAndDistinguishEmptyFromMissingId |
| S26 | 通过 | DeadLetterReplayContractTest.uncertainPublicationKeepsOriginalDelivery；OutboxPublisherReliabilityIT.replayRequiresRoutingConfirmationAndResetsCountersWithoutChangingEventId |
| S27 | 通过 | ModuleDependencyTest.businessApplicationDoesNotDependOnRabbitTransport、messagingDependencyRulesRejectViolations |
| S28 | 通过 | ModuleDependencyTest.inputEntityLeakIsRejectedIncludingRecordMembers及原返回值泄漏负例 |
| S29 | 通过 | 任务07接口注释；任务09模块设计、runbook、README、Markdown/代码链接静态检查 |
| S30 | 通过 | 完整常规323、MySQL38、可靠性16；实际命令与日志见commands.md，重复常规不累计 |
| S31 | 通过 | 隔离Compose/curl报告curl-report.json，逐请求expectedStatus一致；真实MySQL/Rabbit，支付为MockPay |
| S32 | 通过 | 最终git diff --check、脱敏报告检查、Compose健康与隔离资源清理记录；原业务库/卷与缓存保留 |
