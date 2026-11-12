> 历史验收材料：正文中的时间、命令、路径和“当前”均属于原检查点；当前入口见[项目状态](../../../../../docs/project-status.md)。导航链接已迁移。

# 消息S01～S32当前责任映射

旧消息改造证据保留历史含义；本表更新05合并后的入口，08新执行常规、MySQL、可靠性和隔离HTTP均成功，最终证据见[最终验收](final-acceptance.md)、[HTTP报告](curl-report.json)、[资源复核](resources.json)及逐用例JSON。生产代码和消息契约不变。

| 场景 | 当前状态 | 本轮对应入口/证据 |
| --- | --- | --- |
| S01 | 08通过 | PaymentOutboxTransactionTest.productionMapperKeepsTextLongAndExplicitNull；MessageConsumerContractTest.historicalFixturesKeepTheirEnvelopeShape；旧v1八份样本 |
| S02 | 08通过 | PaymentOutboxTransactionTest.storedEventPreservesHistoricalContractAndRollsBackWithCaller；真实支付/退款生产链路回归 |
| S03 | 08通过 | PaymentOutboxTransactionTest.recordingRequiresTheBusinessTransaction；PaymentNotificationAtomicityTest.writeFailureRollsBackMoneyAttemptAuditAndEventThenAllowsRetry |
| S04 | 08通过 | PaymentNotificationMySqlIT.concurrentDuplicateSuccessHasOneMoneyEffectAndStableEvent；退款唯一申请/原ID发布回归 |
| S05 | 08通过 | MessageConsumerContractTest.historicalIntegerIdsAndAdditionalFieldsRemainCompatible；真实无metadata消费夹具 |
| S06 | 08通过 | MessageConsumerContractTest.rejectsIdsWithoutTruncationOrImplicitDefaults |
| S07 | 08通过 | MessageConsumerContractTest.rejectsWrongEnvelope |
| S08 | 08通过 | PaymentSucceededListenerTest/RefundRequestedListenerTest.permanentInvalidMessageDoesNotRetry、invalidBusinessInputIsRejectedWithoutRetry；真实永久消息DLQ |
| S09 | 08通过 | OutboxPublisherReliabilityIT.confirmedPersistentMessageMarksOutboxPublishedInDurableTopology |
| S10 | 08通过 | OutboxPublisherReliabilityIT.mandatoryReturnRetriesThenExhaustedEventBecomesFailed、brokerOutageKeepsPendingEventAndRecoveryPublishesIt |
| S11 | 08通过 | OutboxPublisherReliabilityIT.concurrentPublishersUseSkipLockedAndPublishOneCopy |
| S12 | 08通过 | OutboxPublisherReliabilityIT.databaseMarkFailureRollsBackAfterBrokerAcceptedThenReusesOriginalEvent：同批先前标记也回滚、实际消费者只确认一次 |
| S13 | 08通过 | PaymentSucceededProcessorIntegrationTest.inboxAndConfirmationCommitOnceForDuplicateEvent、newEventForConfirmedAppointmentDoesNotRequirePaymentLookup；LatePaymentMySqlIT |
| S14 | 08通过 | PaymentSucceededProcessorIntegrationTest.failedClaimRemovalRollsBackInboxAndInventoryInRealTransaction、failedRefundCreationRollsBackCloseInventoryClaimAndInbox |
| S15 | 08通过 | PaymentSucceededProcessorIntegrationTest.paymentConfirmedAtExactDeadlineIsRefundedWithoutRestoringAppointment、expiryUsesPersistedPaidAtEvenWhenConfirmationIsDelayed |
| S16 | 08通过 | PaymentConfirmationReliabilityIT.committedConfirmationSurvivesLostAckAndBrokerRedelivery |
| S17 | 08通过 | 两个ListenerTest.successfulBusinessDoesNotInspectMalformedRetryHeader、ackFailureAfterSuccessfulProcessingPropagatesForRedelivery |
| S18 | 08通过 | RabbitConsumerFailureHandlerTest.forwardsOriginalWithNextCountBeforeAcknowledging；OutboxPublisherReliabilityIT.retryTransportDelaysBothRetriesAndStopsAfterThreeAttempts |
| S19 | 08通过 | RabbitConsumerFailureHandlerTest.rejectsExhaustedOrMalformedHeadersWithoutOverflow；真实TTL/DLQ测试 |
| S20 | 08通过 | RabbitConsumerFailureHandlerTest.uncertainPublicationRequeuesOriginalWithoutAcknowledging、nackFailurePropagatesWithoutAcknowledgingOrRepublishing、rejectFailurePropagatesForValidationAndExhaustion；RabbitConsumerFailureHandlerTest.retryAckFailureEscapesWithoutRequeueOrSecondPublication |
| S21 | 08通过 | RabbitConsumerFailureHandlerTest.rejectsExhaustedOrMalformedHeadersWithoutOverflow；两个Listener成功不查非法头 |
| S22 | 08通过 | PaymentParentLockMySqlIT.refundClaimLocksParentBeforeWaitingForRefund、duplicateWorkersAndRefundRequestKeepSuccessWhenSlowerCallFails |
| S23 | 08通过 | PaymentConfirmationReliabilityIT.refundGatewayFailureRetriesThenMovesToDlqWithoutRestoringAppointment：spy验证渠道refundNo实际三次调用 |
| S24 | 08通过 | PaymentParentLockMySqlIT.successfulGatewayWithFailedLocalCompletionRetriesTheSameRefundNumber：相同refundNo、相同providerRefundNo、本地收敛 |
| S25 | 08通过 | DeadLetterReplayContractTest.logicalTargetsKeepOriginalBodyAndDistinguishEmptyFromMissingId |
| S26 | 08通过 | DeadLetterReplayContractTest.uncertainPublicationKeepsOriginalDelivery；OutboxPublisherReliabilityIT.replayRequiresRoutingConfirmationAndResetsCountersWithoutChangingEventId |
| S27 | 08通过 | ModuleDependencyTest.businessApplicationDoesNotDependOnRabbitTransport、messagingDependencyRulesRejectViolations |
| S28 | 08通过 | ModuleDependencyTest.inputEntityLeakIsRejectedIncludingRecordMembers及原返回值泄漏负例 |
| S29 | 08通过 | 生产接口/事件契约不变，原消息文档作为历史记录；本轮docs/testing.md与完整场景审计由07完成 |
| S30 | 08通过 | 08新执行完整常规、MySQL和可靠性；数量及命令以本轮verification/commands.md为准 |
| S31 | 08通过 | 08新执行隔离Compose/curl，保留脱敏报告并独立确认隔离资源清理 |
| S32 | 08通过 | 08最终状态、文档链接、源摘要和生产JAR核对；不改原业务库/数据卷 |
