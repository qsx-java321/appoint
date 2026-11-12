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

## 任务05

两个Listener共用RabbitConsumerFailureHandler，保留解码/业务/成功ACK主线；增加退款Listener、计数边界及确认顺序测试。./mvnw -o -Preliability-it -Dtest=PaymentSucceededListenerTest,RefundRequestedListenerTest,RabbitConsumerFailureHandlerTest,ConfirmedMessagePublisherTest -Dit.test=OutboxPublisherReliabilityIT,PaymentConfirmationReliabilityIT verify：聚焦28、可靠性16全通过；日志/tmp/messaging-05-reliability.log。

## 任务06

DLQ重放拆为应用接口、三个逻辑目标与Rabbit适配器；EMPTY与无ID的REPLAYED可区分。./mvnw -o clean test 常规315项通过；./mvnw -o -Preliability-it -Dtest=DeadLetterReplayContractTest -Dit.test=OutboxPublisherReliabilityIT,PaymentConfirmationReliabilityIT verify：重放契约6项及可靠性16项通过。日志/tmp/messaging-06-unit.log、/tmp/messaging-06-reliability.log，零失败/错误/跳过。

## 任务07

新增AMQP/contract/common依赖约束正反例及Entity入参/record成员泄漏负例，补足四类wire字段与接口事务/失败说明；./mvnw -o test 常规318项通过，含新增架构3项和全部Codec/Listener/重放契约测试。日志/tmp/messaging-07-unit.log。S29接口与技术说明完成，正式runbook由09收口。

## 任务08

完整 ./mvnw -o -Pmysql-it verify：常规318+MySQL38通过；完整 ./mvnw -o -Preliability-it verify：常规318+可靠性16通过，全部零失败/错误/跳过。日志/tmp/messaging-08-mysql.log、/tmp/messaging-08-reliability.log。S12实际证明整批发布标记回滚及原ID重发只确认一次；S24证明同refundNo与同providerRefundNo收敛。首次MySQL新用例的代理故障注入方式已修正后全量重跑；新spy发现旧退款夹具实际失败于未注册WECHAT渠道，已改为注册Mock渠道注入失败并验证实际refund三次调用，可靠性完整重跑成功。

## 任务09最终验收

- `./mvnw -o clean test`：318项通过（收口补充测试前），日志 `/tmp/messaging-09-unit.log`。
- 补充重放结果路由/ACK-NACK异常断言后，`./mvnw -o -Preliability-it verify`：常规322、可靠性16项通过，日志 `/tmp/messaging-09-reliability.log`。
- 再补充序列化失败回滚调用方业务写入的直接断言后，`./mvnw -o test`：最终常规323项通过，日志 `/tmp/messaging-09-unit-final.log`。新增的是普通测试，生产代码、MySQL及可靠性测试未变，已通过的完整MySQL38/可靠性16继续有效。各命令均零失败/错误/跳过，重复常规不累计。
- `python3 scripts/compose_acceptance.py --http-client curl --http-report .scratch/messaging-design-refactor/verification/curl-report.json`：92次真实业务请求，全部expectedStatus一致，日志 `/tmp/messaging-09-compose.log`。运行951cb43e2b90，200×63、201×12、202×13、403×1、409×3均符合预期；仍使用MockPay与测试专用就诊日Clock。
- 递归检查报告中的Token、密码、签名、证件及联系方式均脱敏。只读确认本次隔离数据库/账号/vhost均已清理；检查vhost捕获时初次工具返回None，改用capture=True后复核成功，没有新增/删除资源。验收应用已退出，Compose三依赖健康保留运行，既有数据卷与旧失败资源未清理。
- `git diff --check`、Markdown链接/锚点/围栏与任务状态检查通过；相关源码与测试摘要见 code-manifest.sha256。

## 任务09

正式设计、README、恢复手册、逐场景证据同步；最终常规323、完整MySQL38、可靠性16、隔离curl92全部成功，零失败/错误/跳过。四条重放普通测试及序列化失败回滚断言在收口补齐并重验。命令、源摘要、资源清理及限制见verification/；九项完成，每步验收后单独提交，不推送。
