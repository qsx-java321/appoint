# 当前场景与测试覆盖

核对基线：`1e83c0c`。以下描述当前保障和源码入口；“历史通过”仅指对应代码检查点的实测，并非本次重跑。测试分层和运行方式见[测试约定](../testing.md)，类清单见[清单](inventory.md)。

## 业务保障

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


## 消息契约与恢复

历史通过依据：[71e4ec8，测试源码9c5878b](../../.scratch/archive/delivery-quality/evidence/test-suite-refactor/final-acceptance.md)。

| 场景 | 预期行为 | 当前测试入口或证据 |
| --- | --- | --- |
| messaging/S01 | 保存基线四类事件样本；字段名、null、Long 文本、数值金额与时间保持；旧样本不由新 record 反向生成 | PaymentOutboxTransactionTest.productionMapperKeepsTextLongAndExplicitNull；MessageConsumerContractTest.historicalFixturesKeepTheirEnvelopeShape；旧v1八份样本 |
| messaging/S02 | 三处真实业务生产路径写出的四类 Outbox JSON 与契约匹配；可被相应现有消费者解析，退款结果做结构核对 | PaymentOutboxTransactionTest.storedEventPreservesHistoricalContractAndRollsBackWithCaller；真实支付/退款生产链路回归 |
| messaging/S03 | PaymentOutbox 无事务调用失败；外层提交保留事件，外层回滚及序列化/数据库写失败不留下半笔业务或事件 | PaymentOutboxTransactionTest.recordingRequiresTheBusinessTransaction；PaymentNotificationAtomicityTest.writeFailureRollsBackMoneyAttemptAuditAndEventThenAllowsRetry |
| messaging/S04 | 相同已处理支付通知不增加成功事件；已有退款申请不再创建新单/请求事件；重试发送不生成新 ID | PaymentNotificationMySqlIT.concurrentDuplicateSuccessHasOneMoneyEffectAndStableEvent；退款唯一申请/原ID发布回归 |
| messaging/S05 | 两 Codec 接受 JSON 整数与文本整数 ID、Long 边界内正值；兼容历史合法 v1、额外未知字段、缺 Rabbit 元数据 | MessageConsumerContractTest.historicalIntegerIdsAndAdditionalFieldsRemainCompatible；真实无metadata消费夹具 |
| messaging/S06 | 小数/1.0/科学计数数字 ID、越界、零/负数、布尔/数组/对象、非法文本拒绝，不能截断/默认 | MessageConsumerContractTest.rejectsIdsWithoutTruncationOrImplicitDefaults |
| messaging/S07 | 类型错误、版本错误或非整数版本、缺关键字段、非法时间、payload 非对象、aggregateId 不一致拒绝 | MessageConsumerContractTest.rejectsWrongEnvelope |
| messaging/S08 | Codec 永久无效消息直接 DLQ；不调用 Processor/Worker、不写 Inbox、不执行渠道或业务写入 | PaymentSucceededListenerTest/RefundRequestedListenerTest.permanentInvalidMessageDoesNotRetry、invalidBusinessInputIsRejectedWithoutRetry；真实永久消息DLQ |
| messaging/S09 | confirm ACK 且无 return 才 PUBLISHED；消息持久化、路由、正文和 eventId 保持，PUBLISHED 不代表消费完成 | OutboxPublisherReliabilityIT.confirmedPersistentMessageMarksOutboxPublishedInDurableTopology |
| messaging/S10 | NACK、return、超时、断连均不记成功；原事件及失败计数/退避/错误保留，耗尽 FAILED 后不自动重置 | OutboxPublisherReliabilityIT.mandatoryReturnRetriesThenExhaustedEventBecomesFailed、brokerOutageKeepsPendingEventAndRecoveryPublishesIt |
| messaging/S11 | 多发布器 SKIP LOCKED 不领取同一锁定记录，可处理不同批；不改变原持锁策略 | OutboxPublisherReliabilityIT.concurrentPublishersUseSkipLockedAndPublishOneCopy |
| messaging/S12 | Broker 已接收后注入数据库标记或提交失败，整批发布标记回滚、不返回成功计数；再次发布沿用 eventId/正文，消费最终只产生一次业务效果 | OutboxPublisherReliabilityIT.databaseMarkFailureRollsBackAfterBrokerAcceptedThenReusesOriginalEvent：同批先前标记也回滚、实际消费者只确认一次 |
| messaging/S13 | 同 eventId 串行/并发重复消费只留一条 Inbox，确认名额、序号只推进一次；新 eventId 遇已确认事实也不重复推进 | PaymentSucceededProcessorIntegrationTest.inboxAndConfirmationCommitOnceForDuplicateEvent、newEventForConfirmedAppointmentDoesNotRequirePaymentLookup；LatePaymentMySqlIT |
| messaging/S14 | 确认、释放或退款申请失败时 Inbox 与业务一起回滚；再次处理同事件可成功 | PaymentSucceededProcessorIntegrationTest.failedClaimRemovalRollsBackInboxAndInventoryInRealTransaction、failedRefundCreationRollsBackCloseInventoryClaimAndInbox |
| messaging/S15 | 截止前收款、事件晚到仍按事实确认；等于或晚于截止则关闭、释放、删有效占用、确保唯一退款并原子提交 | PaymentSucceededProcessorIntegrationTest.paymentConfirmedAtExactDeadlineIsRefundedWithoutRestoringAppointment、expiryUsesPersistedPaidAtEvenWhenConfirmationIsDelayed |
| messaging/S16 | 业务已提交后 ACK 丢失，真实重投不重复确认或耗号；不会撤销已提交事务 | PaymentConfirmationReliabilityIT.committedConfirmationSurvivesLostAckAndBrokerRedelivery |
| messaging/S17 | 正常/幂等成功返回后才 ACK，不发布重试；Processor/Worker 仍为真实用例接口 | 两个ListenerTest.successfulBusinessDoesNotInspectMalformedRetryHeader、ackFailureAfterSuccessfulProcessingPropagatesForRedelivery |
| messaging/S18 | 第一次与第二次暂时失败分别带计数 1、2 和 TTL 1000 转发，原正文/事件ID不变；确认转发先于 ACK 原消息 | RabbitConsumerFailureHandlerTest.forwardsOriginalWithNextCountBeforeAcknowledging；OutboxPublisherReliabilityIT.retryTransportDelaysBothRetriesAndStopsAfterThreeAttempts |
| messaging/S19 | 第三次失败 reject(false) 进入正确 DLQ，不再发布重试；原计数头名称保持 | RabbitConsumerFailureHandlerTest.rejectsExhaustedOrMalformedHeadersWithoutOverflow；真实TTL/DLQ测试 |
| messaging/S20 | 重试转发 return/NACK/超时/连接失败时 NACK/requeue 原消息，不再 ACK；确认操作 IOException 传播，不被当业务异常再次转发 | RabbitConsumerFailureHandlerTest.uncertainPublicationRequeuesOriginalWithoutAcknowledging、nackFailurePropagatesWithoutAcknowledgingOrRepublishing、rejectFailurePropagatesForValidationAndExhaustion；RabbitConsumerFailureHandlerTest.retryAckFailureEscapesWithoutRequeueOrSecondPublication |
| messaging/S21 | 失败处理时缺头按首次；Integer/Long 整数头兼容，Integer.MAX_VALUE 先判断耗尽不溢出，负数/非整数/越界拒绝；正常成功不增加头校验门槛 | RabbitConsumerFailureHandlerTest.rejectsExhaustedOrMalformedHeadersWithoutOverflow；两个Listener成功不查非法头 |
| messaging/S22 | refundNo、唯一退款单与成功状态幂等；渠道调用时已释放数据库事务；较慢失败不能覆盖并发成功 | PaymentParentLockMySqlIT.refundClaimLocksParentBeforeWaitingForRefund、duplicateWorkersAndRefundRequestKeepSuccessWhenSlowerCallFails |
| messaging/S23 | 完整合法 RefundRequested 到达渠道后，渠道失败确实执行三次并进入 DLQ；不是因新 Codec 提前拒绝 | PaymentConfirmationReliabilityIT.refundGatewayFailureRetriesThenMovesToDlqWithoutRestoringAppointment：spy验证渠道refundNo实际三次调用 |
| messaging/S24 | 渠道已成功、结果落库失败，再执行沿用相同 refundNo 并能收敛；不引入预约 Inbox 作为外部退款恰好一次证明 | PaymentParentLockMySqlIT.successfulGatewayWithFailedLocalCompletionRetriesTheSameRefundNumber：相同refundNo、相同providerRefundNo、本地收敛 |
| messaging/S25 | 三种 ReplayTarget 只映射原白名单，空队列明确 EMPTY；退款结果仍按现有类型分路，无监听器不伪称通知成功 | DeadLetterReplayContractTest.logicalTargetsKeepOriginalBodyAndDistinguishEmptyFromMissingId |
| messaging/S26 | 重放原正文和 eventId 不变；两种计数头和 expiration 清除；转发确认后 ACK，失败 NACK/requeue；不写业务表 | DeadLetterReplayContractTest.uncertainPublicationKeepsOriginalDelivery；OutboxPublisherReliabilityIT.replayRequiresRoutingConfirmationAndResetsCountersWithoutChangingEventId |
| messaging/S27 | application/domain 无 AMQP/Rabbit 依赖；合同包与 common helper 不反向依赖业务实现/持久化，违规 fixture 确实失败 | ModuleDependencyTest.businessApplicationDoesNotDependOnRabbitTransport、messagingDependencyRulesRejectViolations |
| messaging/S28 | 应用接口入参/返回及其泛型/数组/record 成员不暴露 Entity；原跨模块规则保持，违规 fixture 确实失败 | ModuleDependencyTest.inputEntityLeakIsRejectedIncludingRecordMembers及原返回值泄漏负例 |
| messaging/S29 | 所有新/改接口注释说明事务、返回、幂等、失败；无错位 Javadoc；事件样本、源码阅读入口和 runbook 一致 | 生产接口/事件契约不变，原消息文档作为历史记录；本轮docs/testing.md与完整场景审计由07完成 |
| messaging/S30 | 最终常规、mysql-it、reliability-it 分别成功，零意外跳过；每条命令有实际报告和被测检查点 | 08新执行完整常规、MySQL和可靠性；数量及命令以本轮verification/commands.md为准 |
| messaging/S31 | 隔离 Compose/curl 注册→预约→支付→异步确认→签到→接诊完成及取消退款/迟到支付通过，原库存/快照不变量保持 | 08新执行隔离Compose/curl，保留脱敏报告并独立确认隔离资源清理 |
| messaging/S32 | 地图/issue/场景证据状态一致；实际配置和日志脱敏；成功隔离资源清理、旧业务数据与卷保留，记录服务终态 | 08最终状态、文档链接、源摘要和生产JAR核对；不改原业务库/数据卷 |

## 预约时间与正常就诊

历史通过依据：[cf02768交付，提交前被测摘要见证据](../../.scratch/archive/booking-time/evidence/three-day-booking-time/scenario-evidence.md)。

| 场景 | 预期行为 | 当前测试入口或证据 |
| --- | --- | --- |
| booking-time/S01 | 11/11 查询某医生 | SessionCatalogHttpTest.doctorCatalogReturnsTwelveSessionsWithOneTimeSnapshotWithoutWritingSchedules；真实 curl 场次 GET |
| booking-time/S02 | 11/11 10:05，有余号，查看当天四场 | SessionCatalogHttpTest.doctorCatalogReturnsTwelveSessionsWithOneTimeSnapshotWithoutWritingSchedules |
| booking-time/S03 | 11/11 10:15，查看或创建当天 10:30 场 | OnDemandBookingHttpTest.allFourSessionsUseStrictNewOrderCutoffs；SessionCatalogHttpTest.timeStateTakesPrecedenceOverFullInventory |
| booking-time/S04 | 11/11 13:40 创建当天 14:00 场 | OnDemandBookingHttpTest.todayBookingPersistsIdenticalAppointmentAndPaymentTimes |
| booking-time/S05 | 11/11 10:00 创建明天 08:00 场 | OnDemandBookingHttpTest.firstBookingCreatesSessionFromSettingsAndGetsFullPaymentWindow |
| booking-time/S06 | 11/11 10:00 创建后天 14:00 场 | OnDemandBookingHttpTest.settingChangesOnlyAffectSessionsCreatedAfterTheChange；BookingTimeRulesTest.paymentDeadlineIsPerOrderAndCanCrossMidnight |
| booking-time/S07 | 10:14 创建当天 10:30 场订单，10:20 发起支付 | OnDemandBookingHttpTest.existingOrderCanPayAfterNewBookingCutoffWithOneDeadlineAcrossAllResponses |
| booking-time/S08 | 正常支付完成，查询预约确认结果 | OnDemandBookingHttpTest.existingOrderCanPayAfterNewBookingCutoffWithOneDeadlineAcrossAllResponses；真实HTTP支付与轮询 |
| booking-time/S09 | 11/11 23:55 创建次日 08:00 场，次日 00:05 支付 | OnDemandBookingHttpTest.paymentAcrossMidnightUsesPersistedDeadline |
| booking-time/S10 | 请求处理跨零点，11/12 从“明天”变“今天”，仍早于该场截止 | OnDemandBookingHttpTest.midnightWhileWaitingStillAcceptsOriginalTomorrowAsToday |
| booking-time/S11 | 查询时钟从 11/11 23:59:59 推进到 11/12 00:00 | SessionCatalogHttpTest.midnightRollsAllThreeDatesWithoutPersistingSessions |
| booking-time/S12 | 查询已经存在、当前不在三天入口内的本人预约 | OnDemandBookingHttpTest.historyRemainsReadableAfterLeavingTheThreeDayWindowWithoutRecalculatingDeadline；BookingMidnightIntegrationTest.previouslyCompletedKeyReplaysEvenAfterOriginalDateLeavesCreationWindow |
| booking-time/S13 | 创建、支付尝试、详情连续查询 | OnDemandBookingHttpTest.existingOrderCanPayAfterNewBookingCutoffWithOneDeadlineAcrossAllResponses |
| booking-time/S14 | 当天场次已确认，用户签到，医生开始再完成 | VisitHttpTest.sameDayBookingCanBePaidCheckedInAndCompletedWithoutChangingInventory；真实 curl 就诊阶段 |
| booking-time/S15 | 外层请求登记后时间推进，再在合法截止前准入 | OnDemandBookingHttpTest.createdAtIsSampledAfterPatientLockRatherThanRequestRegistration |
| booking-time/S16 | 尚未截止但某场可用名额为零；另外场次仍有名额 | SessionCatalogHttpTest.timeStateTakesPrecedenceOverFullInventory |
| booking-time/S17 | 判断昨天、今天、后天、大后天的创建资格 | BookingTimeRulesTest.creationChecksBothDateWindowAndTodaySessionTime；OnDemandBookingHttpTest.newOrdersRejectPastDateCutoffBeyondThreeDatesAndUnknownSession |
| booking-time/S18 | 原订单达到自己的支付截止，查询及支付尝试资格 | OnDemandBookingHttpTest.reachingPaymentDeadlineDisablesPaymentWithoutWaitingForExpirationScan |

消息S29～S32包含交付检查；不作为可单独运行的业务测试方法。原八份v1样本见 `src/test/resources/messaging/v1/`；退款结果仅验证结构与重放，当前没有通知消费者。

## 签到窗口

2026-11-13增量实现交付为`d75f73c`，实现基线`4bfc1d5`；本任务实际结果与源码摘要见[签到窗口验收](../../.scratch/visit-checkin-window/verification/report.md)。历史通过保持原检查点，不能替代本任务验证。

| 场景 | 预期行为 | 当前测试入口或证据 |
| --- | --- | --- |
| checkin-window/W01 | 四场开放前一秒4503，恰好开放允许 | VisitRulesTest.fourSessionsIncludeOpeningButExcludeClosing；VisitHttpTest.allFourWindowsUseStrictBoundariesInQueriesAndWrites |
| checkin-window/W02 | 四场截止前一秒允许，截止时刻/后一秒4504 | VisitRulesTest.fourSessionsIncludeOpeningButExcludeClosing；VisitHttpTest.allFourWindowsUseStrictBoundariesInQueriesAndWrites |
| checkin-window/W03 | 前一天/上海当天零点4503，次日首次4504 | VisitHttpTest.userAndDoctorShareProgressAndPreserveBookingFactsThroughTheWholeVisit；VisitHttpTest.staleQueryDoesNotAuthorizeCheckInAtDeadlineOrNextDay；VisitRulesTest.fourSessionsIncludeOpeningButExcludeClosing |
| checkin-window/W04 | 开诊后、截止前仍可首次签到 | VisitHttpTest.allFourWindowsUseStrictBoundariesInQueriesAndWrites |
| checkin-window/W05 | 用户列表/详情返回快照窗口，同一列表统一serverTime与资格 | VisitHttpTest.allFourWindowsUseStrictBoundariesInQueriesAndWrites；BookingQueryServiceTest.confirmedListUsesOneSampleEvenWhenClockWouldCrossCheckInDeadline |
| checkin-window/W06 | 查询尚可签，写入到截止须拒绝；写入单次秒采样 | VisitHttpTest.staleQueryDoesNotAuthorizeCheckInAtDeadlineOrNextDay；VisitServiceTest.oneSecondPrecisionSampleDeterminesEligibilityAndAllInsertTimes |
| checkin-window/W07 | 未付/取消/过期4502优先时间；归属/不存在优先；重复保留首次事实 | VisitHttpTest.appointmentStateErrorsPrecedeBothWindowErrors；VisitHttpTest.ownershipAndExistencePrecedeWindowErrorsWithoutSideEffects；VisitHttpTest.checkedInPatientCanStartAfterCloseAndCompleteNextDayWithStableWindowAndFirstTime |
| checkin-window/W08 | 失败不产生记录、不改预约/资金/库存/号码/占用/快照 | VisitHttpTest.allFourWindowsUseStrictBoundariesInQueriesAndWrites及其他拒绝用例完整行比较；VisitWorkflowMySqlIT.concurrentBindingCheckInStartAndCompletionKeepOneIdentityAndOneVisit |
| checkin-window/W09 | 两请求一成功一4502，仅一记录、首次时间稳定 | VisitWorkflowMySqlIT.concurrentBindingCheckInStartAndCompletionKeepOneIdentityAndOneVisit |
| checkin-window/W10 | 09:59签到、10:10开始、次日完成，双方进度一致 | VisitHttpTest.checkedInPatientCanStartAfterCloseAndCompleteNextDayWithStableWindowAndFirstTime；VisitWorkflowMySqlIT.concurrentBindingCheckInStartAndCompletionKeepOneIdentityAndOneVisit |
| checkin-window/W11 | 旧零点签到保留并可跨日接诊；未签用快照窗口、超时不自动关闭退款 | VisitServiceTest.legacyMidnightCheckInIsNotRevokedAndCanStartOnNextDay；AppointmentResponseAssemblerTest.legacySignedVisitKeepsFirstTimeAndSnapshotWindowAfterClosing；AppointmentLifecycleInterfaceTest.readFactsUseAppointmentStartAndEndSnapshots；VisitHttpTest.staleQueryDoesNotAuthorizeCheckInAtDeadlineOrNextDay |
| checkin-window/W12 | 隔离curl注册→预约→支付→签到→完成，窗口字段与用户/医生查询一致，库存快照不变 | scripts/compose_acceptance.py；本任务curl-report.json |

## Redis目录缓存

完整R01～R15行为标准见[实施方案](../redis-catalog-cache-design.md)。以下为本轮新增责任入口，HTTP与完整回归实际结果由任务交付证据保存。

| 场景 | 验证入口 |
| --- | --- |
| R01/R02/R03/R15 命中、SQL减少、JSON、TTL和指标 | CatalogDirectoryCacheRedisIT.repeatedDirectoryReadsAvoidSqlAndPreserveLargeIdsAndNulls；CatalogDirectoryCacheTest.redisReadFailureLoadsDatabaseWithoutRetryingRedisWrite |
| R04 九种提交后失效及无关列表保留 | CatalogDirectoryCacheRedisIT.allNineMutationsEvictOnlyRelatedListsAfterRealCommit |
| R05 事务读己之写、回滚不污染 | CatalogDirectoryCacheRedisIT.rollbackPreservesSharedCacheWhileTransactionReadsOwnWrites |
| R06 旧值回填与到期刷新、不续期 | CatalogDirectoryCacheRedisIT.staleInFlightFillIsAllowedButExpiresWithoutReadRenewal |
| R07/R08 Redis中断、失效失败、损坏值回源 | CatalogDirectoryCacheRedisIT.unavailableRedisFallsBackAndCommittedWriteStillSucceeds；CatalogDirectoryCacheTest.malformedAndIncompleteValuesRecoverThroughDatabase；CatalogDirectoryCacheTest.writeFailurePreservesLoadedResult |
| R09 数据库错误只传播一次且不缓存 | CatalogDirectoryCacheTest.databaseFailureIsPropagatedOnceAndNeverCached |
| R10 两个独立应用共享及失效可见 | CatalogDirectoryCacheRedisIT.separateApplicationSharesCacheAndSeesCommittedInvalidation |
| R11 namespace隔离及定向清理 | CatalogDirectoryCacheRedisIT.isolatedNamespacesAndMalformedValuesCannotPoisonCatalog；compose_acceptance.py |
| R12/R13 原认证、场次、预约支付接诊契约 | 原常规/MySQL/可靠性回归；compose_acceptance.py |
| R14 关闭绕过及配置校验 | CatalogDirectoryCacheTest.disabledCacheNeverReadsOrRegistersInvalidation；CatalogDirectoryCacheTest.configurationRejectsUnsafeNamespaceAndInvalidTtlOnlyWhenEnabled |
