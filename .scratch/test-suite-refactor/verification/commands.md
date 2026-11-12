# 实际执行记录

每条记录包括阶段、命令、日志、数量及失败/错误/跳过；初次失败和修复后的运行分别记录。/tmp日志为本机临时证据，统计和结论在本文保存。


## 01 常规基线

- 命令：`./mvnw -o clean test`
- 日志：`/tmp/test-refactor-01-unit.log`
- Maven汇总（普通/集成分别计）：[('323', '0', '0', '0')]；耗时：['43.906 s']; BUILD SUCCESS=True。

## 01 MySQL基线

- 命令：`./mvnw -o -Pmysql-it verify`
- 日志：`/tmp/test-refactor-01-mysql.log`
- Maven汇总（普通/集成分别计）：[('323', '0', '0', '0'), ('38', '0', '0', '0')]；耗时：['03:02 min']; BUILD SUCCESS=True。

## 01 可靠性基线

- 命令：`./mvnw -o -Preliability-it verify`
- 日志：`/tmp/test-refactor-01-reliability.log`
- Maven汇总（普通/集成分别计）：[('323', '0', '0', '0'), ('16', '0', '0', '0')]；耗时：['01:43 min']; BUILD SUCCESS=True。

## 02 约束与迁移

- 命令：`./mvnw -o -Pmysql-it -Dtest=InitialSchemaMigrationTest,VisitSchemaMigrationTest,PaymentPersistenceIntegrationTest,DoctorNumberIntegrationTest,MockPayProductionProfileTest -Dit.test=CatalogManagementMySqlIT,VisitSchemaMySqlIT,PaymentPersistenceMySqlIT,PaymentMigrationMySqlIT verify`
- 日志：`/tmp/test-refactor-02.log`
- Maven汇总（普通/集成分别计）：[('10', '0', '0', '0'), ('4', '0', '0', '0')]；耗时：['55.632 s']; BUILD SUCCESS=True。

## 03 时钟与夹具

- 命令：`./mvnw -o -Pmysql-it -Dtest=VisitHttpTest,ScheduleInventoryIntegrationTest,SessionCatalogHttpTest,OnDemandBookingHttpTest,BookingMidnightIntegrationTest,BookingTimeRulesTest -Dit.test=VisitWorkflowMySqlIT verify`
- 日志：`/tmp/test-refactor-03.log`
- Maven汇总（普通/集成分别计）：[('41', '0', '0', '0'), ('1', '0', '0', '0')]；耗时：['34.831 s']; BUILD SUCCESS=True。

## 04 聚焦清理首轮

- 命令：`./mvnw -o -Dtest=ApiFlowIntegrationTest,AppointmentIdempotencyIntegrationTest,AppointmentLifecycleIntegrationTest,ScheduleInventoryIntegrationTest,BookingMidnightIntegrationTest,PaymentAttemptIntegrationTest,PaymentNotificationAtomicityTest,PaymentOutboxTransactionTest,PatientIntegrationTest,OnDemandBookingHttpTest,CatalogManagementIntegrationTest test`
- 日志：`/tmp/test-refactor-04-unit.log`
- Maven汇总（普通/集成分别计）：[('73', '0', '0', '0')]；耗时：['24.301 s']; BUILD SUCCESS=True。

## 04 顺序A全量

- 命令：`./mvnw -o -Pmysql-it -Dsurefire.runOrder=alphabetical -Dfailsafe.runOrder=alphabetical -Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$MethodName verify`
- 日志：`/tmp/test-refactor-04-order-a.log`
- Maven汇总（普通/集成分别计）：[('323', '0', '0', '0'), ('38', '0', '0', '0')]；耗时：['03:15 min']; BUILD SUCCESS=True。

## 04 顺序B复验

- 命令：`./mvnw -o -Pmysql-it -Dsurefire.runOrder=reversealphabetical -Dfailsafe.runOrder=reversealphabetical -Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random -Djunit.jupiter.execution.order.random.seed=20261112 -Dit.test=AppointmentLifecycleMySqlIT,PaymentNotificationMySqlIT,PaymentAttemptMySqlIT,LatePaymentMySqlIT,VisitWorkflowMySqlIT,PaymentParentLockMySqlIT verify`
- 日志：`/tmp/test-refactor-04-order-b.log`
- Maven汇总（普通/集成分别计）：[('323', '0', '0', '0'), ('32', '0', '0', '0')]；耗时：['02:19 min']; BUILD SUCCESS=True。

## 04 可靠性顺序A

- 命令：`./mvnw -o -Preliability-it -Dtest=AppointmentLifecycleInterfaceTest,CatalogManagementIntegrationTest -Dsurefire.runOrder=alphabetical -Dfailsafe.runOrder=alphabetical -Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$MethodName verify`
- 日志：`/tmp/test-refactor-04-reliability-a.log`
- Maven汇总（普通/集成分别计）：[('19', '0', '0', '0'), ('16', '0', '0', '0')]；耗时：['01:30 min']; BUILD SUCCESS=True。

## 04 可靠性顺序B最终

- 命令：`./mvnw -o -Preliability-it -Dtest=ConfirmedMessagePublisherTest -Dsurefire.runOrder=reversealphabetical -Dfailsafe.runOrder=reversealphabetical -Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random -Djunit.jupiter.execution.order.random.seed=20261112 verify`
- 日志：`/tmp/test-refactor-04-reliability-b.log`
- Maven汇总（普通/集成分别计）：[('5', '0', '0', '0'), ('16', '0', '0', '0')]；耗时：['01:24 min']; BUILD SUCCESS=True。

## 执行备注与限制

- 02最初临时编辑脚本存在缩进错误，没有修改源码；随后预执行`/tmp/test-refactor-02-pre-edit.log`验证的是旧代码，不计本步改造验收。修正脚本后实际首轮`/tmp/test-refactor-02-first.log`常规10通过、MySQL4中2失败，暴露CHECK错误的真实包装类型；精确验证3819/HY000及约束名后完整重跑通过。
- 04顺序A后完善了接口测试与管理并发用例的finally清理；顺序B完整常规覆盖最终版本，可靠性A再次按字母顺序验收这两类19项。
- 可靠性A完成后补充两处失败路径收尾：Broker连接重置进入finally，匿名ACK重投队列在finally删除；可靠性B完整16项验证最终源码。两轮均保留全部原故障窗口断言。
- 本轮没有删除测试方法，57类共258个注解测试方法保留，仅历史迁移方法改名明确V4/V5；323/38/16为实际参数化执行数量，不重复加总多轮常规。
- src/main、pom.xml、compose.yaml摘要与起点一致；`sha256sum -c .scratch/test-suite-refactor/verification/production-baseline.sha256`在项目根目录执行通过，日志`/tmp/test-refactor-production-check.log`。生产JAR静态核对：没有support测试辅助类、archfixture和Test类。
- 用户要求04后暂停。没有执行05～08，也没有执行08隔离Compose/curl验收，不宣称整个八步方案完成。未改动现有Compose基础服务和数据卷。

- 05首次编译发现泛型getHeader断言重载歧义及退款最小事件无paymentOrderId访问器；仅修正测试为显式Object及refundId=71后重新执行完整门禁。初次日志：/tmp/test-refactor-05-compile-first.log。

## 05 消息职责合并

- 命令：`./mvnw -o -Preliability-it -Dtest=PaymentOutboxTransactionTest,MessageConsumerContractTest,PaymentSucceededEventCodecTest,PaymentSucceededListenerTest,RefundRequestedListenerTest,RabbitConsumerFailureHandlerTest,ConfirmedMessagePublisherTest,DeadLetterReplayContractTest verify`
- 日志：`/tmp/test-refactor-05.log`
- Maven汇总（普通/集成分别计）：[('85', '0', '0', '0'), ('16', '0', '0', '0')]；耗时：['01:34 min']; BUILD SUCCESS=True。

## 06 完整常规

- 命令：`./mvnw -o test`
- 日志：`/tmp/test-refactor-06.log`
- Maven汇总（普通/集成分别计）：[('340', '0', '0', '0')]；耗时：['36.942 s']; BUILD SUCCESS=True。

## 07 可读性与故障点

- 命令：`./mvnw -o -Pmysql-it -Dtest=ModuleDependencyTest,PaymentOutboxTransactionTest,PaymentNotificationAtomicityTest,PaymentGatewayContractTest,MessageConsumerContractTest,DeadLetterReplayContractTest,AppointmentLifecycleInterfaceTest,VisitHttpTest,ScheduleInventoryIntegrationTest,OnDemandBookingHttpTest -Dit.test=PaymentParentLockMySqlIT verify`
- 日志：`/tmp/test-refactor-07.log`
- Maven汇总（普通/集成分别计）：[('111', '0', '0', '0'), ('10', '0', '0', '0')]；耗时：['42.881 s']; BUILD SUCCESS=True。

## 07 Outbox真实故障点

- 命令：`./mvnw -o -Preliability-it -Dtest=ConfirmedMessagePublisherTest -Dit.test=OutboxPublisherReliabilityIT verify`
- 日志：`/tmp/test-refactor-07-reliability.log`
- Maven汇总（普通/集成分别计）：[('5', '0', '0', '0'), ('9', '0', '0', '0')]；耗时：['30.293 s']; BUILD SUCCESS=True。

## 08 干净构建全量常规

- 命令：`./mvnw -o clean test`
- 日志：`/tmp/test-refactor-08-unit.log`
- Maven汇总（普通/集成分别计）：[('340', '0', '0', '0')]；耗时：['35.468 s']; BUILD SUCCESS=True。

## 08 完整MySQL

- 命令：`./mvnw -o -Pmysql-it verify`
- 日志：`/tmp/test-refactor-08-mysql.log`
- Maven汇总（普通/集成分别计）：[('340', '0', '0', '0'), ('38', '0', '0', '0')]；耗时：['02:52 min']; BUILD SUCCESS=True。

## 08 完整消息可靠性

- 命令：`./mvnw -o -Preliability-it verify`
- 日志：`/tmp/test-refactor-08-reliability.log`
- Maven汇总（普通/集成分别计）：[('340', '0', '0', '0'), ('16', '0', '0', '0')]；耗时：['03:48 min']; BUILD SUCCESS=True。

## 08隔离HTTP与独立资源复核

- 命令：`python3 scripts/compose_acceptance.py --http-client curl --http-report .scratch/test-suite-refactor/verification/curl-report.json`；日志`/tmp/test-refactor-08-compose.log`，退出0。
- 运行277f4dc1aa1f，92请求的status全部等于expectedStatus；注册、预约、支付、Rabbit确认、取消退款、迟到支付、签到与接诊完成均由脚本实际断言。报告递归脱敏。
- 成功后独立只读查询本轮精确资源名：数据库accept_277f4dc1aa1f、账号a_277f4dc1aa1f、vhost accept-277f4dc1aa1f均不存在；验收应用端口已关闭。Compose MySQL/Redis/RabbitMQ健康保留运行，旧业务库/卷未清理。结果见resources.json。
- Failsafe目录保留先前11份MySQL XML；最终可靠性摘要按本轮日志实际Running类名筛选，仅2类16项，不把目录13份54项错误累计为本轮可靠性。MySQL11类38项和常规43类340项分别保存逐用例摘要。
- 最终生产JAR不包含support/archfixture、测试类或JUnit/Mockito/Testcontainers/ArchUnit/H2测试库；273份生产摘要、360份被测代码/配置/脚本摘要保持一致。测试源码检查点9c5878b，08只补文档与证据。
