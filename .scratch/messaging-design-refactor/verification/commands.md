# 实际命令与结果

## 任务01

- `./mvnw -o test`：263 项通过，0 失败/错误/跳过；日志 `/tmp/messaging-01-unit.log`。此运行在新增样本测试前，后续 profile 会重新编译运行新增测试。
- `./mvnw -o -Pmysql-it -Dit.test=PaymentNotificationMySqlIT,PaymentParentLockMySqlIT,LatePaymentMySqlIT verify`：常规263、聚焦MySQL16通过；日志 `/tmp/messaging-01-mysql.log`。
- `./mvnw -o -Preliability-it -Dit.test=OutboxPublisherReliabilityIT,PaymentConfirmationReliabilityIT verify`：常规264（含新增基线契约测试）、可靠性15通过；日志 `/tmp/messaging-01-reliability.log`。全部零失败/错误/跳过。

所有 Maven 命令串行；不输出本地配置凭据。临时日志可能随系统清理丢失。

## 任务02

完成类型化四事件、MANDATORY PaymentOutbox 与三处生产者迁移；聚焦54项、真实MySQL支付/退款父锁15项通过，零失败/错误/跳过。命令：./mvnw -o -Dtest=PaymentOutboxTransactionTest,PaymentNotificationAtomicityTest,PaymentNotificationBoundaryTest,PaymentSucceededProcessorIntegrationTest,ModuleDependencyTest test；同过滤的-Pmysql-it -Dit.test=PaymentNotificationMySqlIT,PaymentParentLockMySqlIT verify。日志/tmp/messaging-02-unit.log及/tmp/messaging-02-mysql.log。初次编译暴露遗留构造器/导入，修复后完整聚焦及集成重跑成功。

## 任务03

完成双Codec严格输入矩阵、退款envelope关联校验及内部结果enum；聚焦68项、MySQL10项、PaymentConfirmationReliabilityIT7项通过，零失败/错误/跳过。命令见/tmp/messaging-03-unit.log（-Dtest=MessageConsumerContractTest,PaymentSucceededEventCodecTest,PaymentSucceededListenerTest,PaymentSucceededProcessorIntegrationTest,AppointmentLifecycleIntegrationTest）、/tmp/messaging-03-mysql.log（-Dtest=MessageConsumerContractTest -Dit.test=LatePaymentMySqlIT,PaymentParentLockMySqlIT）、/tmp/messaging-03-reliability.log（-Dtest=MessageConsumerContractTest -Dit.test=PaymentConfirmationReliabilityIT），均 ./mvnw -o 对应profile。新增拒绝浮点/越界/非正ID、错误类型与版本形态、缺字段及聚合不符；合法数字/文本ID和额外字段兼容。

## 任务04

发布接口改为不可变OutboxMessage和明确异常，Rabbit sender迁至messaging；数据库标记异常传播回滚整批。./mvnw -o clean test 常规291项通过；./mvnw -o -Preliability-it -Dtest=ConfirmedMessagePublisherTest -Dit.test=OutboxPublisherReliabilityIT verify：确认单测5项、可靠性9项通过。日志/tmp/messaging-04-unit.log、/tmp/messaging-04-reliability.log。新增故障测试修正隔离夹具及MySQL JSON规范化的期望，异常摘要保留底层原因，最终完整重跑通过。
