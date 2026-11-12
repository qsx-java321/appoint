> 历史验收材料：正文中的时间、命令、路径和“当前”均属于原检查点；当前入口见[项目状态](../../../../../docs/project-status.md)。导航链接已迁移。

# 当前测试类清单

原始清单保留在inventory.md；本表依据07源码生成，命令与实际执行数量见commands.md。方法数包括参数化方法，不等于执行用例数。

| 测试类 | 层次/数据库 | 显式profile | 时钟 | 隔离方式 | 方法数 |
| --- | --- | --- | --- | --- | --- |
| `ApiFlowIntegrationTest` | Spring/H2 | test | 注入/固定Clock，见配置 | 新增ID清理+目录快照；真实提交 | 7 |
| `PatientJourneyHttpMySqlIT` | MySQL容器 | test | 显式时间输入 | 独占容器，类末回收 | 2 |
| `AppointmentExpirationJobTest` | 普通对象测试 | 未显式指定 | 注入/固定Clock，见配置 | 只读上下文/对象每例新建 | 1 |
| `AppointmentIdempotencyIntegrationTest` | Spring/H2 | test | 显式时间输入 | 新增ID清理+目录快照；真实提交 | 7 |
| `AppointmentLifecycleIntegrationTest` | Spring/H2 | test | 注入/固定Clock，见配置 | 新增ID清理+目录快照；真实提交 | 12 |
| `AppointmentLifecycleInterfaceTest` | Spring/H2 | 未显式指定 | 显式时间输入 | 新增ID清理+目录快照；真实提交 | 8 |
| `AppointmentLifecycleMySqlIT` | MySQL容器 | test | 显式时间输入 | 新增ID清理+目录快照；真实提交 | 13 |
| `AppointmentQueryIntegrationTest` | Spring/H2 | test | 显式时间输入 | 测试事务回滚；特殊提交显式清理 | 10 |
| `BookingMidnightIntegrationTest` | Spring/H2 | 未显式指定 | 可变上海钟，每例重置 | 新增ID清理+目录快照；真实提交 | 1 |
| `OnDemandBookingHttpTest` | Spring/H2 | test | 可变上海钟，每例重置 | 测试事务回滚；特殊提交显式清理 | 14 |
| `ScheduleInventoryIntegrationTest` | Spring/H2 | test | 可变上海钟，每例重置 | 新增ID清理+目录快照；真实提交 | 6 |
| `ModuleDependencyTest` | ArchUnit | 未显式指定 | 无专用时钟 | 只读上下文/对象每例新建 | 20 |
| `AppointmentResponseAssemblerTest` | 普通对象测试 | 未显式指定 | 显式时间输入 | 只读上下文/对象每例新建 | 1 |
| `BookingQueryServiceTest` | 普通对象测试 | 未显式指定 | 注入/固定Clock，见配置 | 只读上下文/对象每例新建 | 2 |
| `BookingWorkflowProjectorTest` | 普通对象测试 | 未显式指定 | 无专用时钟 | 只读上下文/对象每例新建 | 4 |
| `BookingSettingsHttpTest` | Spring/H2 | 未显式指定 | 无专用时钟 | 测试事务回滚；特殊提交显式清理 | 3 |
| `CatalogManagementIntegrationTest` | Spring/H2 | 未显式指定 | 注入/固定Clock，见配置 | 测试事务回滚；特殊提交显式清理 | 11 |
| `CatalogManagementMySqlIT` | MySQL容器 | 未显式指定 | 无专用时钟 | 独占容器，类末回收 | 1 |
| `DoctorNumberIntegrationTest` | Spring/H2 | 未显式指定 | 无专用时钟 | 测试事务回滚；特殊提交显式清理 | 3 |
| `InitialSchemaMigrationTest` | 独立H2迁移 | 未显式指定 | 无专用时钟 | 每例独立H2 | 4 |
| `SessionCatalogHttpTest` | Spring/H2 | 未显式指定 | 可变上海钟，每例重置 | 测试事务回滚；特殊提交显式清理 | 4 |
| `VisitSchemaMigrationTest` | 独立H2迁移 | 未显式指定 | 无专用时钟 | 每例独立H2 | 1 |
| `VisitSchemaMySqlIT` | MySQL容器 | 未显式指定 | 无专用时钟 | 独占容器，类末回收 | 1 |
| `BookingTimeRulesTest` | 普通对象测试 | 未显式指定 | 显式时间输入 | 只读上下文/对象每例新建 | 6 |
| `ConfirmedMessagePublisherTest` | 普通对象测试 | 未显式指定 | 无专用时钟 | 只读上下文/对象每例新建 | 2 |
| `RabbitConsumerFailureHandlerTest` | 普通对象测试 | 未显式指定 | 无专用时钟 | 只读上下文/对象每例新建 | 6 |
| `LatePaymentMySqlIT` | MySQL容器 | 未显式指定 | 显式时间输入 | 新增ID清理+目录快照；真实提交 | 1 |
| `LifecycleLanguageTest` | 普通对象测试 | 未显式指定 | 无专用时钟 | 只读上下文/对象每例新建 | 2 |
| `PaymentConfirmationReliabilityIT` | MySQL+Rabbit容器 | test | 显式时间输入 | 独占容器，停监听清理再恢复 | 7 |
| `PaymentSucceededEventCodecTest` | 普通对象测试 | 未显式指定 | 显式时间输入 | 只读上下文/对象每例新建 | 1 |
| `PaymentSucceededProcessorIntegrationTest` | Spring/H2 | 未显式指定 | 注入/固定Clock，见配置 | 测试事务回滚；特殊提交显式清理 | 13 |
| `BookingLifecyclePolicyTest` | 普通对象测试 | 未显式指定 | 显式时间输入 | 只读上下文/对象每例新建 | 6 |
| `PaymentSucceededListenerTest` | 普通对象测试 | 未显式指定 | 无专用时钟 | 只读上下文/对象每例新建 | 5 |
| `PatientIntegrationTest` | Spring/H2 | 未显式指定 | 无专用时钟 | 测试事务回滚；特殊提交显式清理 | 4 |
| `LegacyDevRouteAbsenceTest` | Spring/H2 | dev | 无专用时钟 | 只读上下文/对象每例新建 | 1 |
| `LegacyTestRouteAbsenceTest` | Spring/H2 | test | 无专用时钟 | 只读上下文/对象每例新建 | 1 |
| `MockPayProductionProfileTest` | Spring/H2 | prod | 无专用时钟 | 只读上下文/对象每例新建 | 1 |
| `OutboxPublisherReliabilityIT` | MySQL+Rabbit容器 | 未显式指定 | 显式时间输入 | 独占容器，停监听清理再恢复 | 9 |
| `PaymentAttemptIntegrationTest` | Spring/H2 | test | 显式时间输入 | 新增ID清理+目录快照；真实提交 | 3 |
| `PaymentAttemptMySqlIT` | MySQL容器 | test | 显式时间输入 | 新增ID清理+目录快照；真实提交 | 1 |
| `PaymentDomainEnumTest` | 普通对象测试 | 未显式指定 | 无专用时钟 | 只读上下文/对象每例新建 | 1 |
| `PaymentGatewayContractTest` | 普通对象测试 | 未显式指定 | 显式时间输入 | 只读上下文/对象每例新建 | 7 |
| `PaymentMigrationMySqlIT` | MySQL容器 | 未显式指定 | 无专用时钟 | 独占容器，类末回收 | 1 |
| `PaymentNotificationMySqlIT` | MySQL容器 | test | 显式时间输入 | 新增ID清理+目录快照；真实提交 | 6 |
| `PaymentOutboxTransactionTest` | Spring/H2 | test | 显式时间输入 | 新增ID清理+目录快照；真实提交 | 4 |
| `PaymentPersistenceIntegrationTest` | Spring/H2 | 未显式指定 | 显式时间输入 | 测试事务回滚；特殊提交显式清理 | 1 |
| `PaymentPersistenceMySqlIT` | MySQL容器 | 未显式指定 | 无专用时钟 | 独占容器，类末回收 | 1 |
| `PaymentNotificationAtomicityTest` | Spring/H2 | test | 显式时间输入 | 新增ID清理+目录快照；真实提交 | 1 |
| `PaymentNotificationBoundaryTest` | 普通对象测试 | 未显式指定 | 显式时间输入 | 只读上下文/对象每例新建 | 2 |
| `PaymentNotificationPolicyTest` | 普通对象测试 | 未显式指定 | 显式时间输入 | 只读上下文/对象每例新建 | 2 |
| `PaymentParentLockMySqlIT` | MySQL容器 | test | 显式时间输入 | 新增ID清理+目录快照；真实提交 | 8 |
| `DeadLetterReplayContractTest` | 普通对象测试 | 未显式指定 | 无专用时钟 | 只读上下文/对象每例新建 | 4 |
| `MessageConsumerContractTest` | 普通对象测试 | 未显式指定 | 显式时间输入 | 只读上下文/对象每例新建 | 4 |
| `RefundRequestedListenerTest` | 普通对象测试 | 未显式指定 | 无专用时钟 | 只读上下文/对象每例新建 | 5 |
| `VisitHttpTest` | Spring/H2 | test | 可变上海钟，每例重置 | 测试事务回滚；特殊提交显式清理 | 7 |
| `VisitWorkflowMySqlIT` | MySQL容器 | test | 可变上海钟，每例重置 | 新增ID清理+目录快照；真实提交 | 1 |

## 保留原则

- 原八份v1 JSON与架构违规fixture摘要完全不变，共19个文件。
- 57→56测试类，仅删除合并后的MessagingBaselineContractTest；普通测试43类、IT13类。
- SpringBootTest使用文件33→31；不是对实际Spring缓存中独立上下文个数的测量。
- 全限定@Test也纳入方法审计，原与当前各260方法；当前340常规执行用例的增长来自新增参数和故障矩阵。
- 并发、事务、真实Broker、profile和历史迁移的独有断言均保留，逐方法处置见method-audit.md。
