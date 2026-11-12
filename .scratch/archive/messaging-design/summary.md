# 消息代码设计交付

## 阶段与交付

归档核对基线：`1e83c0c6bb4ddfcdfe22196d1309e7e0c04c5bef`，日期2026-11-12。以下记录阶段交付，当前行为由[项目状态](../../../docs/project-status.md)及其现行文档定义。

已确认只整理契约、职责、输入校验、接口和注释，保持业务、V1～V10、合法事件v1和拓扑。允许加严非法输入；保留同模块Mapper，不全面Repository化，不为每类加interface。PaymentOutbox以MANDATORY加入事务，发布适配器隔离Rabbit，Listener共享失败传输处理，重放用逻辑白名单。

主要交付提交：963051f（九步收口）、88b66ba（真实故障窗口）。原issue的关闭记录提交列在下表，可能是验收文档提交，不等同于全部实现修改所在提交。

## 演进与边界

后续测试整理合并MessagingBaselineContractTest及Listener重复分支；当前S01～S32入口见正式测试覆盖。Outbox租约、性能压测、运维HTTP、通知和真实渠道仍属范围外。

## messaging-design-refactor

历史文件提交范围：2026-11-12至2026-11-12（含后续文档复核，具体实现/验收时间以记录为准）。

原任务均已完成；下表保留编号、标题和状态关闭记录，原始全文按文末Git方法读取。

| 任务 | 标题 | 状态 | 关闭记录提交 |
| --- | --- | --- | --- |
| 01 | 01 基线与消息契约样本 | resolved | `5c83d16` |
| 02 | 02 类型化事件与事务内 Outbox 写入 | resolved | `22d6331` |
| 03 | 03 严格消费契约与类型化处理结果 | resolved | `9c1a1b5` |
| 04 | 04 发布接口收紧与 Rabbit 适配器迁移 | resolved | `a03047d` |
| 05 | 05 共用消费失败处理与完整确认矩阵 | resolved | `6f50f09` |
| 06 | 06 DLQ 重放接口与适配器 | resolved | `e9f61fd` |
| 07 | 07 架构约束与注释复核 | resolved | `c1cbf0a` |
| 08 | 08 真实事务与 Rabbit 故障窗口门禁 | resolved | `88b66ba` |
| 09 | 09 正式文档同步与完整验收 | resolved | `963051f` |

### 验收与实施记录

下列记录属于该阶段；旧规则、旧类名和临时路径保持历史含义。

**任务01**

- 2026-11-12：基线验收完成。常规263；聚焦MySQL16；可靠性profile重跑常规264（含新增契约测试）及可靠性15，全部零失败/错误/跳过。日志见 verification/commands.md。

**任务02**

- 2026-11-12：任务已定义，依赖 01；未开始实现。

- 2026-11-12：完成类型化四事件、MANDATORY PaymentOutbox 与三处生产者迁移；聚焦54项、真实MySQL支付/退款父锁15项通过，零失败/错误/跳过。命令：./mvnw -o -Dtest=PaymentOutboxTransactionTest,PaymentNotificationAtomicityTest,PaymentNotificationBoundaryTest,PaymentSucceededProcessorIntegrationTest,ModuleDependencyTest test；同过滤的-Pmysql-it -Dit.test=PaymentNotificationMySqlIT,PaymentParentLockMySqlIT verify。日志/tmp/messaging-02-unit.log及/tmp/messaging-02-mysql.log。初次编译暴露遗留构造器/导入，修复后完整聚焦及集成重跑成功。

**任务03**

- 2026-11-12：完成双Codec严格输入矩阵、退款envelope关联校验及内部结果enum；聚焦68项、MySQL10项、PaymentConfirmationReliabilityIT7项通过，零失败/错误/跳过。命令见/tmp/messaging-03-unit.log（-Dtest=MessageConsumerContractTest,PaymentSucceededEventCodecTest,PaymentSucceededListenerTest,PaymentSucceededProcessorIntegrationTest,AppointmentLifecycleIntegrationTest）、/tmp/messaging-03-mysql.log（-Dtest=MessageConsumerContractTest -Dit.test=LatePaymentMySqlIT,PaymentParentLockMySqlIT）、/tmp/messaging-03-reliability.log（-Dtest=MessageConsumerContractTest -Dit.test=PaymentConfirmationReliabilityIT），均 ./mvnw -o 对应profile。新增拒绝浮点/越界/非正ID、错误类型与版本形态、缺字段及聚合不符；合法数字/文本ID和额外字段兼容。

**任务04**

- 2026-11-12：任务已定义，依赖 03；仍采用持锁发布，未做租约优化。

- 2026-11-12：发布接口改为不可变OutboxMessage和明确异常，Rabbit sender迁至messaging；数据库标记异常传播回滚整批。./mvnw -o clean test 常规291项通过；./mvnw -o -Preliability-it -Dtest=ConfirmedMessagePublisherTest -Dit.test=OutboxPublisherReliabilityIT verify：确认单测5项、可靠性9项通过。日志/tmp/messaging-04-unit.log、/tmp/messaging-04-reliability.log。新增故障测试修正隔离夹具及MySQL JSON规范化的期望，异常摘要保留底层原因，最终完整重跑通过。

**任务05**

- 2026-11-12：任务已定义，依赖 04；未改重试策略、未运行测试。

- 2026-11-12：两个Listener共用RabbitConsumerFailureHandler，保留解码/业务/成功ACK主线；增加退款Listener、计数边界及确认顺序测试。./mvnw -o -Preliability-it -Dtest=PaymentSucceededListenerTest,RefundRequestedListenerTest,RabbitConsumerFailureHandlerTest,ConfirmedMessagePublisherTest -Dit.test=OutboxPublisherReliabilityIT,PaymentConfirmationReliabilityIT verify：聚焦28、可靠性16全通过；日志/tmp/messaging-05-reliability.log。

**任务06**

- 2026-11-12：任务已定义，依赖 05；未新增管理入口或恢复自动化。

- 2026-11-12：DLQ重放拆为应用接口、三个逻辑目标与Rabbit适配器；EMPTY与无ID的REPLAYED可区分。./mvnw -o clean test 常规315项通过；./mvnw -o -Preliability-it -Dtest=DeadLetterReplayContractTest -Dit.test=OutboxPublisherReliabilityIT,PaymentConfirmationReliabilityIT verify：重放契约6项及可靠性16项通过。日志/tmp/messaging-06-unit.log、/tmp/messaging-06-reliability.log，零失败/错误/跳过。

**任务07**

- 2026-11-12：任务已定义，依赖 06；不要求全面隔离同模块 Mapper。

- 2026-11-12：新增AMQP/contract/common依赖约束正反例及Entity入参/record成员泄漏负例，补足四类wire字段与接口事务/失败说明；./mvnw -o test 常规318项通过，含新增架构3项和全部Codec/Listener/重放契约测试。日志/tmp/messaging-07-unit.log。S29接口与技术说明完成，正式runbook由09收口。

**任务08**

- 2026-11-12：任务已定义，依赖 07；没有本轮真实依赖验收结果。

- 2026-11-12：完整 ./mvnw -o -Pmysql-it verify：常规318+MySQL38通过；完整 ./mvnw -o -Preliability-it verify：常规318+可靠性16通过，全部零失败/错误/跳过。日志/tmp/messaging-08-mysql.log、/tmp/messaging-08-reliability.log。S12实际证明整批发布标记回滚及原ID重发只确认一次；S24证明同refundNo与同providerRefundNo收敛。首次MySQL新用例的代理故障注入方式已修正后全量重跑；新spy发现旧退款夹具实际失败于未注册WECHAT渠道，已改为注册Mock渠道注入失败并验证实际refund三次调用，可靠性完整重跑成功。

**任务09**

- 2026-11-12：正式设计、README、恢复手册、逐场景证据同步；最终常规323、完整MySQL38、可靠性16、隔离curl92全部成功，零失败/错误/跳过。四条重放普通测试及序列化失败回滚断言在收口补齐并重验。命令、源摘要、资源清理及限制见verification/；九项完成，每步验收后单独提交，不推送。

## 证据与当前阅读

- [messaging-design-refactor/README.md](evidence/messaging-design-refactor/README.md).
- [messaging-design-refactor/baseline.md](evidence/messaging-design-refactor/baseline.md).
- [messaging-design-refactor/code-manifest.sha256](evidence/messaging-design-refactor/code-manifest.sha256).
- [messaging-design-refactor/commands.md](evidence/messaging-design-refactor/commands.md).
- [messaging-design-refactor/README.md](evidence/messaging-design-refactor/contracts/README.md).
- [messaging-design-refactor/curl-report.json](evidence/messaging-design-refactor/curl-report.json).
- [messaging-design-refactor/implementation-checkpoint.md](evidence/messaging-design-refactor/implementation-checkpoint.md).
- [messaging-design-refactor/scenario-evidence.md](evidence/messaging-design-refactor/scenario-evidence.md).
- [business-flow.md](../../../docs/business-flow.md).
- [module-design.md](../../../docs/module-design.md).
- [scenario-coverage.md](../../../docs/testing/scenario-coverage.md).
- [verification-report.md](../../../docs/verification-report.md).

## 原文追溯

逐文件去向见[清单](../../archive-manifest.json)。原始路径与摘要由清单保存；例如：

```bash
git show 1e83c0c6bb4ddfcdfe22196d1309e7e0c04c5bef:.scratch/messaging-design-refactor/spec.md
```

旧报告中的/tmp日志不承诺持久存在；历史通过不代表本次重跑。
