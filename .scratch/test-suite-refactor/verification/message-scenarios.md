# 消息S01～S32当前责任映射

旧消息改造证据保留历史含义；本表更新05合并后的入口，最终通过状态必须由08新执行结果确认。生产代码和消息契约不变。

| 场景 | 当前状态 | 本轮对应入口/证据 |
| --- | --- | --- |
| S01 | 映射已核对；待08最终复验 | PaymentOutboxTransactionTest.productionMapperKeepsTextLongAndExplicitNull；MessageConsumerContractTest.historicalFixturesKeepTheirEnvelopeShape；旧v1八份样本 |
| S02 | 映射已核对；待08最终复验 | PaymentOutboxTransactionTest.storedEventPreservesHistoricalContractAndRollsBackWithCaller；真实支付/退款生产链路回归 |
| S03 | 映射已核对；待08最终复验 | PaymentOutboxTransactionTest.recordingRequiresTheBusinessTransaction；PaymentNotificationAtomicityTest.writeFailureRollsBackMoneyAttemptAuditAndEventThenAllowsRetry |
| S04 | 映射已核对；待08最终复验 | PaymentNotificationMySqlIT.concurrentDuplicateSuccessHasOneMoneyEffectAndStableEvent；退款唯一申请/原ID发布回归 |
| S05 | 映射已核对；待08最终复验 | MessageConsumerContractTest.historicalIntegerIdsAndAdditionalFieldsRemainCompatible；真实无metadata消费夹具 |
| S06 | 映射已核对；待08最终复验 | MessageConsumerContractTest.rejectsIdsWithoutTruncationOrImplicitDefaults |
| S07 | 映射已核对；待08最终复验 | MessageConsumerContractTest.rejectsWrongEnvelope |
| S08 | 映射已核对；待08最终复验 | PaymentSucceededListenerTest/RefundRequestedListenerTest.permanentInvalidMessageDoesNotRetry、invalidBusinessInputIsRejectedWithoutRetry；真实永久消息DLQ |
| S09 | 映射已核对；待08最终复验 | OutboxPublisherReliabilityIT.confirmedPersistentMessageMarksOutboxPublishedInDurableTopology |
| S10 | 映射已核对；待08最终复验 | OutboxPublisherReliabilityIT.mandatoryReturnRetriesThenExhaustedEventBecomesFailed、brokerOutageKeepsPendingEventAndRecoveryPublishesIt |
| S11 | 映射已核对；待08最终复验 | OutboxPublisherReliabilityIT.concurrentPublishersUseSkipLockedAndPublishOneCopy |
| S12 | 映射已核对；待08最终复验 | OutboxPublisherReliabilityIT.databaseMarkFailureRollsBackAfterBrokerAcceptedThenReusesOriginalEvent：同批先前标记也回滚、实际消费者只确认一次 |
| S13 | 映射已核对；待08最终复验 | PaymentSucceededProcessorIntegrationTest.inboxAndConfirmationCommitOnceForDuplicateEvent、newEventForConfirmedAppointmentDoesNotRequirePaymentLookup；LatePaymentMySqlIT |
| S14 | 映射已核对；待08最终复验 | PaymentSucceededProcessorIntegrationTest.failedClaimRemovalRollsBackInboxAndInventoryInRealTransaction、failedRefundCreationRollsBackCloseInventoryClaimAndInbox |
| S15 | 映射已核对；待08最终复验 | PaymentSucceededProcessorIntegrationTest.paymentConfirmedAtExactDeadlineIsRefundedWithoutRestoringAppointment、expiryUsesPersistedPaidAtEvenWhenConfirmationIsDelayed |
| S16 | 映射已核对；待08最终复验 | PaymentConfirmationReliabilityIT.committedConfirmationSurvivesLostAckAndBrokerRedelivery |
| S17 | 映射已核对；待08最终复验 | 两个ListenerTest.successfulBusinessDoesNotInspectMalformedRetryHeader、ackFailureAfterSuccessfulProcessingPropagatesForRedelivery |
| S18 | 映射已核对；待08最终复验 | RabbitConsumerFailureHandlerTest.forwardsOriginalWithNextCountBeforeAcknowledging；OutboxPublisherReliabilityIT.retryTransportDelaysBothRetriesAndStopsAfterThreeAttempts |
| S19 | 映射已核对；待08最终复验 | RabbitConsumerFailureHandlerTest.rejectsExhaustedOrMalformedHeadersWithoutOverflow；真实TTL/DLQ测试 |
| S20 | 映射已核对；待08最终复验 | RabbitConsumerFailureHandlerTest.uncertainPublicationRequeuesOriginalWithoutAcknowledging、nackFailurePropagatesWithoutAcknowledgingOrRepublishing、rejectFailurePropagatesForValidationAndExhaustion；RabbitConsumerFailureHandlerTest.retryAckFailureEscapesWithoutRequeueOrSecondPublication |
| S21 | 映射已核对；待08最终复验 | RabbitConsumerFailureHandlerTest.rejectsExhaustedOrMalformedHeadersWithoutOverflow；两个Listener成功不查非法头 |
| S22 | 映射已核对；待08最终复验 | PaymentParentLockMySqlIT.refundClaimLocksParentBeforeWaitingForRefund、duplicateWorkersAndRefundRequestKeepSuccessWhenSlowerCallFails |
| S23 | 映射已核对；待08最终复验 | PaymentConfirmationReliabilityIT.refundGatewayFailureRetriesThenMovesToDlqWithoutRestoringAppointment：spy验证渠道refundNo实际三次调用 |
| S24 | 映射已核对；待08最终复验 | PaymentParentLockMySqlIT.successfulGatewayWithFailedLocalCompletionRetriesTheSameRefundNumber：相同refundNo、相同providerRefundNo、本地收敛 |
| S25 | 映射已核对；待08最终复验 | DeadLetterReplayContractTest.logicalTargetsKeepOriginalBodyAndDistinguishEmptyFromMissingId |
| S26 | 映射已核对；待08最终复验 | DeadLetterReplayContractTest.uncertainPublicationKeepsOriginalDelivery；OutboxPublisherReliabilityIT.replayRequiresRoutingConfirmationAndResetsCountersWithoutChangingEventId |
| S27 | 映射已核对；待08最终复验 | ModuleDependencyTest.businessApplicationDoesNotDependOnRabbitTransport、messagingDependencyRulesRejectViolations |
| S28 | 映射已核对；待08最终复验 | ModuleDependencyTest.inputEntityLeakIsRejectedIncludingRecordMembers及原返回值泄漏负例 |
| S29 | 映射已核对；待08最终复验 | 生产接口/事件契约不变，原消息文档作为历史记录；本轮docs/testing.md与完整场景审计由07完成 |
| S30 | 映射已核对；待08最终复验 | 08新执行完整常规、MySQL和可靠性；数量及命令以本轮verification/commands.md为准 |
| S31 | 映射已核对；待08最终复验 | 08新执行隔离Compose/curl，保留脱敏报告并独立确认隔离资源清理 |
| S32 | 映射已核对；待08最终复验 | 08最终状态、文档链接、源摘要和生产JAR核对；不改原业务库/数据卷 |
