# 实际命令与结果

## 任务01

- `./mvnw -o test`：263 项通过，0 失败/错误/跳过；日志 `/tmp/messaging-01-unit.log`。此运行在新增样本测试前，后续 profile 会重新编译运行新增测试。
- `./mvnw -o -Pmysql-it -Dit.test=PaymentNotificationMySqlIT,PaymentParentLockMySqlIT,LatePaymentMySqlIT verify`：常规263、聚焦MySQL16通过；日志 `/tmp/messaging-01-mysql.log`。
- `./mvnw -o -Preliability-it -Dit.test=OutboxPublisherReliabilityIT,PaymentConfirmationReliabilityIT verify`：常规264（含新增基线契约测试）、可靠性15通过；日志 `/tmp/messaging-01-reliability.log`。全部零失败/错误/跳过。

所有 Maven 命令串行；不输出本地配置凭据。临时日志可能随系统清理丢失。

## 任务02

完成类型化四事件、MANDATORY PaymentOutbox 与三处生产者迁移；聚焦54项、真实MySQL支付/退款父锁15项通过，零失败/错误/跳过。命令：./mvnw -o -Dtest=PaymentOutboxTransactionTest,PaymentNotificationAtomicityTest,PaymentNotificationBoundaryTest,PaymentSucceededProcessorIntegrationTest,ModuleDependencyTest test；同过滤的-Pmysql-it -Dit.test=PaymentNotificationMySqlIT,PaymentParentLockMySqlIT verify。日志/tmp/messaging-02-unit.log及/tmp/messaging-02-mysql.log。初次编译暴露遗留构造器/导入，修复后完整聚焦及集成重跑成功。
