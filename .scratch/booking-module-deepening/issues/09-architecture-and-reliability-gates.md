# 09 架构、并发与消息可靠性最终门禁

Type: test
Triage: ready-for-agent
Status: ready-for-agent
Blocked by: 02, 07, 08

设计依据：[spec](../spec.md)，执行位置：[map](../map.md)。初始 Status 使用 triage 标签表示待认领；开始实施改为 claimed，验收完成改为 resolved。Triage 不代表依赖已解除。

## 背景和目标

四模块实现和Legacy清理已完成，需要把新的深interface固定为测试表面，补齐现有架构规则的缺项和可靠性测试窗口。不能把只检查注解当作完整只读证明，也不能把预置Outbox的Broker测试当作真实回调停机验证。

## 范围

- 扩展ModuleDependencyTest：coordination不得依赖两个写模块implementation、公开application返回值不得泄漏Entity、Controller不得直接用Mapper、query Mapper只读。
- 保留合法HTTP Adapter→coordination及稳定enum/DTO依赖，限定写业务层规则，不一刀切禁止整个payment包引用coordination。
- 规则有故意违规fixture/反例验证；只读检查覆盖现有Mapper全部方法/注解/可继承写接口等，避免只检查方法名称。
- 保留MySQL真实竞争、条件更新、唯一约束、SKIP LOCKED，以及RabbitMQ confirm/return、崩溃重复、DLQ和退款重试。
- 补消费者提交前失败、提交后ACK前失败的可控故障窗口，验证重投单副作用；补05尚未覆盖的真实回调Broker停机链路。
- 清理本次改造造成的重复浅调用测试；没有此类旧测试就不为达成删除数量而删测试。

## 非范围

- 不用ArchUnit声称能分析所有字符串常量/SQL语义；本轮状态字面量通过聚焦rg、映射测试及review验证。
- 保留 issue 12 的有界重试与可靠转发，不新增其他重试产品机制。
- 不复活ADR0005取消的停诊写入口或停诊竞争用例。
- 不以H2替代MySQL锁/约束，也不以Mock替代RabbitMQ故障验证。

## 前置依赖

[02 查询流程投影与响应组装分离](02-booking-query-projection.md)；[07 跨模块生命周期决策与流程编排收口](07-coordination-process-manager.md)；[08 迁移支付测试并删除 dev/test 旧模拟路径](08-remove-legacy-payment-simulation.md)。
只有全部前置任务 Status 为 resolved 才能开始；当前仅01可开始。推荐按map串行顺序执行，避免共享快照和调用点冲突。

## 需要修改的模块和主要文件

下列源码路径相对项目根目录；新增类型明确标注，implementation命名可沿用现有风格，不强制为改名搬文件。

- `src/test/java/com/qsx/appoint/architecture/ModuleDependencyTest.java`、架构违规fixture。
- `src/test/java/com/qsx/appoint/appointment/AppointmentLifecycleMySqlIT.java`
- `src/test/java/com/qsx/appoint/payment/{PaymentNotificationMySqlIT,PaymentAttemptMySqlIT,OutboxPublisherReliabilityIT}.java`
- `src/test/java/com/qsx/appoint/coordination/{PaymentConfirmationReliabilityIT,PaymentSucceededProcessorIntegrationTest}.java`
- 本轮新增Policy/深interface测试；`.scratch/booking-module-deepening/baseline.md`验证对照。

## 目标代码形态

行为测试跨公开module interface；Policy纯决策表；MySQL独立事务竞争；RabbitMQ真实发布/监听并以数据库最终事实断言。故障注入位于测试配置/传输seam，不为了测试把内部implementation设为public或在生产加入通用开关。新增规则对应明确禁止依赖，有真实反例触发失败。

## 改造后的业务逻辑

不改业务顺序。发布confirm后标记失败产生重复，消费提交前故障回滚Inbox与业务，提交后ACK前故障重投命中Inbox；退款失败仍保留预约终态和库存释放，三次既有重试后DLQ；重放保留eventId。普通成功消息的临时失败按 issue 12 有界重试，耗尽进入 DLQ。

## 必须保持的不变量

- 深interface不泄漏库存和Entity，query只读，写模块互不依赖。
- 库存、序号、退款与Inbox单副作用，资金/Outbox原子提交。
- 不跳过容器测试、不改弱断言，不用测试数量相同代替覆盖等价。
- 测试环境隔离，不访问开发/生产业务库。

## 可验证验收条件

- 每条新增架构规则至少有明确反例会失败；实际生产类全部通过。
- 01每项覆盖均保留或有新的行为测试替代，02查询和03映射兼容门禁不遗漏。
- 05父锁路径的MySQL竞争全通过，故障后可重试收敛，无永久等待或部分提交。
- 消费两个故障窗口证明Inbox/库存/序号只有一次副作用；Broker停机时真实回调提交，恢复后完成有效确认或已关闭迟到退款。
- 三层全量命令均通过、零跳过；报告区分已知业务缺口、缺少的保证和新引入回归。

## 应运行的测试

- `./mvnw -Dtest=ModuleDependencyTest test` 及架构负例。
- `./mvnw test`
- `./mvnw -Pmysql-it verify`
- `./mvnw -Preliability-it verify`
- MySQL/reliability profile分别执行，检查Failsafe报告实际用例及跳过数。

## 独立交付与回退

独立回退新增架构fixture/规则与测试调整即可，生产代码和数据不变；发现生产问题转对应issue修复，不能在门禁任务悄悄改业务。
后续任务已使用本任务成果时，先逆序撤销依赖，或做兼容性调整；独立回退指本任务完成时可整体撤销，不保证任意抽掉已被下游使用的提交仍可构建。任何回退不得删除已提交资金、Outbox/Inbox或退款数据。

## 完成状态清单

- [ ] 架构规则及违规反例完成
- [ ] 01覆盖对照和浅测试整理
- [ ] MySQL全部锁竞争通过
- [ ] 真实消息故障窗口/重试/DLQ通过
- [ ] 三层结果及已知限制记录

## Comments

- 2026-11-08：根据源码复核后的spec创建。仅完成任务定义，未认领、未实施；本轮基线结果见map，不等同于本issue验收结果。 依赖尚未resolved，暂不可开始实施。
