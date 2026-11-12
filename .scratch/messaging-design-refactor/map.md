# 消息代码设计改造任务地图

Type: refactor
Triage: ready-for-agent
Status: ready-for-agent

日期：2026-11-12。方案已确认，实施完成数 **4 / 9**。

## 入口与当前状态

- [详细方案](spec.md)：目标结构、事件字段、接口、事务、兼容和回退。
- [确认决策](decisions.md)：Q1～Q4 与设计树，无待答范围问题。
- [场景清单](scenarios.md)：S01～S32 的验证责任。
- [验收证据约定](verification/README.md)：实际执行记录位置。

当前只有方案文档，不代表代码、测试或业务验收已经完成。正式 README/设计/API/runbook 仍描述已实现的现状，实施收口后再同步。

## 任务与依赖

| 顺序 | 任务 | Type | Status | Blocked by |
| --- | --- | --- | --- | --- |
| 01 | [基线与消息契约样本](issues/01-baseline-contracts.md) | test | resolved | none |
| 02 | [类型化事件与事务内 Outbox 写入](issues/02-typed-events-and-outbox-writer.md) | refactor | resolved | 01 |
| 03 | [严格消费契约与类型化处理结果](issues/03-consumer-contracts-and-results.md) | refactor/fix | resolved | 02 |
| 04 | [发布接口收紧与 Rabbit 适配器迁移](issues/04-publication-port-and-adapter.md) | refactor | resolved | 03 |
| 05 | [共用消费失败处理与完整确认矩阵](issues/05-consumer-failure-handling.md) | refactor/fix | ready-for-agent | 04 |
| 06 | [DLQ 重放接口与适配器](issues/06-dead-letter-replay.md) | refactor | ready-for-agent | 05 |
| 07 | [架构约束与注释复核](issues/07-architecture-and-comments.md) | test/docs | ready-for-agent | 06 |
| 08 | [真实事务与 Rabbit 故障窗口门禁](issues/08-reliability-gates.md) | test | ready-for-agent | 07 |
| 09 | [正式文档同步与完整验收](issues/09-documentation-and-acceptance.md) | docs/test | ready-for-agent | 08 |

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
7. 无默认提交或推送授权，不暂存凭据、缓存、target 或历史失败资源。

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
