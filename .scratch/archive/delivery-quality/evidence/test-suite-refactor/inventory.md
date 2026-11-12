> 历史验收材料：正文中的时间、命令、路径和“当前”均属于原检查点；当前入口见[项目状态](../../../../../docs/project-status.md)。导航链接已迁移。

# 改造前测试清单

起始 HEAD：`963051f311466781f9763ce04841439de515a496`。职责详见方法清单；真实事务状态按每个方法注解与显式 TransactionTemplate 区分，不能仅看类注解。

| 测试类 | 层次/环境 | 时钟/配置 | 事务/并发/监听与清理 | 处置 |
| --- | --- | --- | --- | --- |
| ApiFlowIntegrationTest.java | Spring/H2 | Clock,appointmentClock | 独立标识 | 保留；按相关步骤改善 |
| PatientJourneyHttpMySqlIT.java | MySQL容器 | 无可变钟 | 独立标识 | 保留；按相关步骤改善 |
| appointment/AppointmentExpirationJobTest.java | 直接对象/H2迁移/ArchUnit | Clock | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| appointment/AppointmentIdempotencyIntegrationTest.java | Spring/H2 | 无可变钟 | 独立标识 | 保留；按相关步骤改善 |
| appointment/AppointmentLifecycleIntegrationTest.java | Spring/H2 | Clock,appointmentClock | 独立标识 | 保留；按相关步骤改善 |
| appointment/AppointmentLifecycleInterfaceTest.java | Spring/H2 | 无可变钟 | 显式事务；显式SQL清理 | 保留；按相关步骤改善 |
| appointment/AppointmentLifecycleMySqlIT.java | MySQL容器 | 无可变钟 | 跨线程；独立标识 | 保留；按相关步骤改善 |
| appointment/AppointmentQueryIntegrationTest.java | Spring/H2 | 无可变钟 | 含测试事务；独立标识 | 保留；按相关步骤改善 |
| appointment/BookingMidnightIntegrationTest.java | Spring/H2 | ClockConfig,MutableAppointmentClock,midnightClock | 独立标识 | 保留；按相关步骤改善 |
| appointment/OnDemandBookingHttpTest.java | Spring/H2 | FixedClockConfig,MutableAppointmentClock,fixedAppointmentClock | 含测试事务；独立标识 | 保留；按相关步骤改善 |
| appointment/ScheduleInventoryIntegrationTest.java | Spring/H2 | Clock,ClockConfiguration,MutableClock,resetClock,testClock | 独立标识 | 保留；按相关步骤改善 |
| architecture/ModuleDependencyTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 无写入或类内夹具；逐场景核对 | 06复用生产扫描，保留正反例 |
| bookingquery/application/AppointmentResponseAssemblerTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| bookingquery/application/BookingQueryServiceTest.java | 直接对象/H2迁移/ArchUnit | Clock,ownershipAndMissingOrderErrorsPrecedePaymentIntegrityAndClock | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| bookingquery/application/BookingWorkflowProjectorTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| catalog/BookingSettingsHttpTest.java | Spring/H2 | 无可变钟 | 含测试事务 | 保留；按相关步骤改善 |
| catalog/CatalogManagementIntegrationTest.java | Spring/H2 | Clock,appointmentClock | 含测试事务；跨线程；独立标识 | 保留；按相关步骤改善 |
| catalog/CatalogManagementMySqlIT.java | MySQL容器 | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| catalog/DoctorNumberIntegrationTest.java | Spring/H2 | 无可变钟 | 含测试事务 | 保留；按相关步骤改善 |
| catalog/InitialSchemaMigrationTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 独立标识 | 保留；按相关步骤改善 |
| catalog/SessionCatalogHttpTest.java | Spring/H2 | CatalogClock,Clock,FixedClockConfig,fixedAppointmentClock,resetClock | 含测试事务 | 保留；按相关步骤改善 |
| catalog/VisitSchemaMigrationTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 独立标识 | 保留；按相关步骤改善 |
| catalog/VisitSchemaMySqlIT.java | MySQL容器 | 无可变钟 | 显式SQL清理 | 保留；按相关步骤改善 |
| catalog/model/BookingTimeRulesTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| common/messaging/ConfirmedMessagePublisherTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| common/messaging/RabbitConsumerFailureHandlerTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| coordination/LatePaymentMySqlIT.java | MySQL容器 | 无可变钟 | 跨线程；独立标识 | 保留；按相关步骤改善 |
| coordination/LifecycleLanguageTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| coordination/PaymentConfirmationReliabilityIT.java | MySQL+Rabbit容器 | 无可变钟 | 真实监听；独立标识 | 保留；按相关步骤改善 |
| coordination/PaymentSucceededEventCodecTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 独立标识 | 保留；按相关步骤改善 |
| coordination/PaymentSucceededProcessorIntegrationTest.java | Spring/H2 | Clock,TimeConfig,appointmentClock,fixedClock | 含测试事务；显式SQL清理；独立标识 | 保留；按相关步骤改善 |
| coordination/application/BookingLifecyclePolicyTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| coordination/messaging/PaymentSucceededListenerTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 无写入或类内夹具；逐场景核对 | 05保留业务接线，合并传输矩阵 |
| patient/PatientIntegrationTest.java | Spring/H2 | 无可变钟 | 独立标识 | 保留；按相关步骤改善 |
| payment/LegacyDevRouteAbsenceTest.java | Spring/H2 | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| payment/LegacyTestRouteAbsenceTest.java | Spring/H2 | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| payment/MessagingBaselineContractTest.java | Spring/H2 | 无可变钟 | 无写入或类内夹具；逐场景核对 | 05迁移断言后删除 |
| payment/MockPayProductionProfileTest.java | Spring/H2 | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| payment/OutboxPublisherReliabilityIT.java | MySQL+Rabbit容器 | 无可变钟 | 跨线程；显式SQL清理；独立标识 | 保留；按相关步骤改善 |
| payment/PaymentAttemptIntegrationTest.java | Spring/H2 | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| payment/PaymentAttemptMySqlIT.java | MySQL容器 | 无可变钟 | 跨线程 | 保留；按相关步骤改善 |
| payment/PaymentDomainEnumTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| payment/PaymentGatewayContractTest.java | Spring/H2 | 无可变钟 | 独立标识 | 06取消Spring上下文 |
| payment/PaymentMigrationMySqlIT.java | MySQL容器 | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| payment/PaymentNotificationMySqlIT.java | MySQL容器 | 无可变钟 | 跨线程 | 保留；按相关步骤改善 |
| payment/PaymentOutboxTransactionTest.java | Spring/H2 | 无可变钟 | 显式事务；独立标识 | 保留；按相关步骤改善 |
| payment/PaymentPersistenceIntegrationTest.java | Spring/H2 | 无可变钟 | 含测试事务；独立标识 | 保留；按相关步骤改善 |
| payment/PaymentPersistenceMySqlIT.java | MySQL容器 | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| payment/application/PaymentNotificationAtomicityTest.java | Spring/H2 | 无可变钟 | 独立标识 | 保留；按相关步骤改善 |
| payment/application/PaymentNotificationBoundaryTest.java | 直接对象/H2迁移/ArchUnit | Clock | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| payment/application/PaymentNotificationPolicyTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| payment/application/PaymentParentLockMySqlIT.java | MySQL容器 | 无可变钟 | 跨线程；独立标识 | 保留；按相关步骤改善 |
| payment/messaging/DeadLetterReplayContractTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| payment/messaging/MessageConsumerContractTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 无写入或类内夹具；逐场景核对 | 保留；按相关步骤改善 |
| payment/messaging/RefundRequestedListenerTest.java | 直接对象/H2迁移/ArchUnit | 无可变钟 | 无写入或类内夹具；逐场景核对 | 05保留业务接线，合并传输矩阵 |
| visit/VisitHttpTest.java | Spring/H2 | Clock,MutableClock,TimeConfig,visitClock | 含测试事务；独立标识 | 保留；按相关步骤改善 |
| visit/VisitWorkflowMySqlIT.java | MySQL容器 | MutableClock,TimeConfig | 跨线程；独立标识 | 保留；按相关步骤改善 |
