# 消息代码设计改造任务地图

Type: refactor
Triage: ready-for-agent
Status: resolved

日期：2026-11-12。方案已确认，实施完成数 **9 / 9**。

## 入口与当前状态

- [详细方案](spec.md)：目标结构、事件字段、接口、事务、兼容和回退。
- [确认决策](decisions.md)：Q1～Q4 与设计树，无待答范围问题。
- [场景清单](scenarios.md)：S01～S32 的验证责任。
- [验收证据约定](verification/README.md)：实际执行记录位置。

九项代码、测试与文档改造已完成；最终常规323、MySQL38、可靠性16、真实curl92均通过。正式README/设计/runbook已同步，逐场景与源摘要见verification。

## 任务与依赖

| 顺序 | 任务 | Type | Status | Blocked by |
| --- | --- | --- | --- | --- |
| 01 | [基线与消息契约样本](issues/01-baseline-contracts.md) | test | resolved | none |
| 02 | [类型化事件与事务内 Outbox 写入](issues/02-typed-events-and-outbox-writer.md) | refactor | resolved | 01 |
| 03 | [严格消费契约与类型化处理结果](issues/03-consumer-contracts-and-results.md) | refactor/fix | resolved | 02 |
| 04 | [发布接口收紧与 Rabbit 适配器迁移](issues/04-publication-port-and-adapter.md) | refactor | resolved | 03 |
| 05 | [共用消费失败处理与完整确认矩阵](issues/05-consumer-failure-handling.md) | refactor/fix | resolved | 04 |
| 06 | [DLQ 重放接口与适配器](issues/06-dead-letter-replay.md) | refactor | resolved | 05 |
| 07 | [架构约束与注释复核](issues/07-architecture-and-comments.md) | test/docs | resolved | 06 |
| 08 | [真实事务与 Rabbit 故障窗口门禁](issues/08-reliability-gates.md) | test | resolved | 07 |
| 09 | [正式文档同步与完整验收](issues/09-documentation-and-acceptance.md) | docs/test | resolved | 08 |

```mermaid
flowchart LR
    I01[01 基线] --> I02[02 事件与写入]
    I02 --> I03[03 消费契约]
    I03 --> I04[04 发布接口]
    I04 --> I05[05 失败处理]
    I05 --> I06[06 重放]
    I06 --> I07[07 架构注释]
    I07 --> I08[08 可靠性]
    I08 --> I09[09 收口验收]
```

采用单线顺序是为了让接口、消费者、测试夹具和依赖规则逐步落地，每步完成后项目可编译和验证。不是要求技术上所有改动都必须互相依赖，也不要求并行 agent 实施。

## 执行规则

1. 开始前重新检查工作区和 HEAD，保留已有改动；先读 spec、对应 issue 和依赖的 Comments。
2. 只有所有 Blocked by 都 resolved 才能开始；认领时将任务与本表改为 claimed。
3. 先完成本任务所需代码和测试，再执行聚焦门禁；失败留在本任务解决，不转交最后一项掩盖。
4. 根据实际结果更新场景证据及 Comments；实现、注释和本任务门禁都完成才 resolved。
5. Maven 串行运行。最后四类业务验收都成功才把整个 map/spec 标为 resolved。
6. 未运行、环境阻塞、意外跳过分别记录；不引用历史通过替代本轮。
7. 用户已授权每步验收后单独提交 Git，不推送；不暂存凭据、缓存、target 或历史失败资源。

## 每阶段交付

| 阶段 | 可检查成果 |
| --- | --- |
| 01 | 旧 v1 样本、现有测试与实际基线、兼容/收紧边界 |
| 02 | 四类消息有明确结构，三处生产者通过 MANDATORY 接口写 Outbox |
| 03 | 两类消费者严格且兼容；结果 enum；退款测试确实执行渠道 |
| 04 | 发布接口无 Entity，Rabbit sender 迁包；状态写失败不能伪装成功 |
| 05 | 两 Listener 共用失败行为，ACK 与异常传播矩阵完整 |
| 06 | 重放接口无物理队列/AMQP 类型，转发原文与源确认保持 |
| 07 | 架构负例能拦违规依赖，注释与接口真实行为一致 |
| 08 | MySQL/Rabbit 真实故障窗口通过，关键保障有直接断言 |
| 09 | 正式文档一致、四类最终验收通过、逐场景证据完整 |

## 后续方向（不阻塞本轮）

- 以压测结果决定是否改造 Outbox 领取/发布长事务。
- 分析全局预约设置锁的性能影响。
- 有实际运营需求时再做受控恢复入口与操作审计。
- 退款结果通知、真实支付渠道分别建需求并明确验收。

## Comments

- 2026-11-12：完成范围确认及九项任务定义。全部尚未认领，未修改生产/测试源码、未执行本轮业务验收。
- 2026-11-12：完成只读交叉复核并修正数据库发布异常传播、退款最小消费字段、失败路径重试头校验和 S29 分阶段验收的表述。14 份 Markdown、36 个本地链接、9 项连续编号/依赖无环、32 个场景及初始状态一致性静态检查通过；不是生产改造或业务测试通过。

- 2026-11-12：01 已验收，相关常规264、MySQL16、可靠性15通过，进入02。

- 2026-11-12：02 完成类型化四事件、MANDATORY PaymentOutbox 与三处生产者迁移；聚焦54项、真实MySQL支付/退款父锁15项通过，零失败/错误/跳过。命令：./mvnw -o -Dtest=PaymentOutboxTransactionTest,PaymentNotificationAtomicityTest,PaymentNotificationBoundaryTest,PaymentSucceededProcessorIntegrationTest,ModuleDependencyTest test；同过滤的-Pmysql-it -Dit.test=PaymentNotificationMySqlIT,PaymentParentLockMySqlIT verify。日志/tmp/messaging-02-unit.log及/tmp/messaging-02-mysql.log。初次编译暴露遗留构造器/导入，修复后完整聚焦及集成重跑成功。

- 2026-11-12：03 完成双Codec严格输入矩阵、退款envelope关联校验及内部结果enum；聚焦68项、MySQL10项、PaymentConfirmationReliabilityIT7项通过，零失败/错误/跳过。命令见/tmp/messaging-03-unit.log（-Dtest=MessageConsumerContractTest,PaymentSucceededEventCodecTest,PaymentSucceededListenerTest,PaymentSucceededProcessorIntegrationTest,AppointmentLifecycleIntegrationTest）、/tmp/messaging-03-mysql.log（-Dtest=MessageConsumerContractTest -Dit.test=LatePaymentMySqlIT,PaymentParentLockMySqlIT）、/tmp/messaging-03-reliability.log（-Dtest=MessageConsumerContractTest -Dit.test=PaymentConfirmationReliabilityIT），均 ./mvnw -o 对应profile。新增拒绝浮点/越界/非正ID、错误类型与版本形态、缺字段及聚合不符；合法数字/文本ID和额外字段兼容。

- 2026-11-12：04 发布接口改为不可变OutboxMessage和明确异常，Rabbit sender迁至messaging；数据库标记异常传播回滚整批。./mvnw -o clean test 常规291项通过；./mvnw -o -Preliability-it -Dtest=ConfirmedMessagePublisherTest -Dit.test=OutboxPublisherReliabilityIT verify：确认单测5项、可靠性9项通过。日志/tmp/messaging-04-unit.log、/tmp/messaging-04-reliability.log。新增故障测试修正隔离夹具及MySQL JSON规范化的期望，异常摘要保留底层原因，最终完整重跑通过。

- 2026-11-12：05 两个Listener共用RabbitConsumerFailureHandler，保留解码/业务/成功ACK主线；增加退款Listener、计数边界及确认顺序测试。./mvnw -o -Preliability-it -Dtest=PaymentSucceededListenerTest,RefundRequestedListenerTest,RabbitConsumerFailureHandlerTest,ConfirmedMessagePublisherTest -Dit.test=OutboxPublisherReliabilityIT,PaymentConfirmationReliabilityIT verify：聚焦28、可靠性16全通过；日志/tmp/messaging-05-reliability.log。

- 2026-11-12：06 DLQ重放拆为应用接口、三个逻辑目标与Rabbit适配器；EMPTY与无ID的REPLAYED可区分。./mvnw -o clean test 常规315项通过；./mvnw -o -Preliability-it -Dtest=DeadLetterReplayContractTest -Dit.test=OutboxPublisherReliabilityIT,PaymentConfirmationReliabilityIT verify：重放契约6项及可靠性16项通过。日志/tmp/messaging-06-unit.log、/tmp/messaging-06-reliability.log，零失败/错误/跳过。

- 2026-11-12：07 新增AMQP/contract/common依赖约束正反例及Entity入参/record成员泄漏负例，补足四类wire字段与接口事务/失败说明；./mvnw -o test 常规318项通过，含新增架构3项和全部Codec/Listener/重放契约测试。日志/tmp/messaging-07-unit.log。S29接口与技术说明完成，正式runbook由09收口。

- 2026-11-12：08 完整 ./mvnw -o -Pmysql-it verify：常规318+MySQL38通过；完整 ./mvnw -o -Preliability-it verify：常规318+可靠性16通过，全部零失败/错误/跳过。日志/tmp/messaging-08-mysql.log、/tmp/messaging-08-reliability.log。S12实际证明整批发布标记回滚及原ID重发只确认一次；S24证明同refundNo与同providerRefundNo收敛。首次MySQL新用例的代理故障注入方式已修正后全量重跑；新spy发现旧退款夹具实际失败于未注册WECHAT渠道，已改为注册Mock渠道注入失败并验证实际refund三次调用，可靠性完整重跑成功。

## 分步提交记录

- 01：`5c83d16 test: 固定消息改造方案与v1契约基线并完成第一步验收`
- 02：`22d6331 refactor: 类型化支付事件并封装事务内Outbox写入`
- 03：`9c1a1b5 refactor: 明确消息消费契约并收紧非法输入校验`
- 04：`a03047d refactor: 隔离Rabbit发布适配器并明确Outbox发布失败契约`
- 05：`6f50f09 refactor: 统一Rabbit消费失败转发与确认处理`
- 06：`e9f61fd refactor: 分离受控死信重放接口与Rabbit实现`
- 07：`c1cbf0a test: 固定消息接口依赖方向并完善事务契约注释`
- 08：`88b66ba test: 验证发布回滚重发和退款落库失败的真实恢复窗口`
- 09：本轮最终提交，见 Git 日志。

- 2026-11-12：09 正式设计、README、恢复手册、逐场景证据同步；最终常规323、完整MySQL38、可靠性16、隔离curl92全部成功，零失败/错误/跳过。四条重放普通测试及序列化失败回滚断言在收口补齐并重验。命令、源摘要、资源清理及限制见verification/；九项完成，每步验收后单独提交，不推送。

- 2026-11-12：最终静态门禁通过：23份Markdown、95个链接/锚点、九项resolved状态/依赖、32场景证据、被测源码摘要一致；生产JAR无测试注入类。只保留原scripts/__pycache__/未跟踪，实际凭据未暂存。
