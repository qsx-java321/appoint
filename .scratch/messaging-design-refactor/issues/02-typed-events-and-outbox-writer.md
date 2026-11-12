# 02 类型化事件与事务内 Outbox 写入

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。消息设计9/9已交付，生产契约仍有效；原323等验收与源码摘要为963051f历史快照。基线测试类后来合并，当前测试入口和结果以测试整理交付为准。 当前能力与进度见[项目状态](../../../docs/project-status.md)。 测试迁移见[当前消息场景](../../test-suite-refactor/verification/message-scenarios.md)。

Type: refactor
Triage: ready-for-agent
Status: resolved
Blocked by: 01

依据：[spec 第 6、7.1、8 节](../spec.md)。责任场景：S01～S04、S14、S22、S24 的写入部分。

## 目标

用四类明确 payload 和统一 envelope 描述现行 wire contract，提供 PaymentOutbox 接口，把三处手工 JSON/Outbox 初始化集中。现行业务与事务顺序保持。

## 文件清单

- 新增 `payment/contract/event/PaymentEventEnvelope.java` 及四个 payload record。
- 新增 `payment/application/PaymentOutbox.java`、`DefaultPaymentOutbox.java`。
- 修改 `PaymentNotificationServiceImpl`、`DefaultPaymentRefundOperations`、`DefaultRefundWorker`。
- 增加 `PaymentOutboxContractTest`、`PaymentOutboxTransactionTest`，更新受影响事务测试。

生产路径以 `src/main/java/com/qsx/appoint/` 为根；测试以 `src/test/java/com/qsx/appoint/` 为根。

## 实施步骤

1. 按基线字段创建纯 record，不带 Jackson 多态类名、不改变 envelope 结构。
2. PaymentOutbox 提供四个明确 record 方法，返回新事件 ID；接口说明“未提交、未发送”的语义。
3. 实现方法使用 MANDATORY，由调用方注入的 Spring Bean 调用；内部 private 方法统一 UUID、envelope、序列化和 Entity 初始化。
4. 从 payload.paymentOrderId 得到 aggregateId，保持 aggregateType 为数据库字段 PaymentOrder。
5. 使用生产 ObjectMapper，保留文本 Long、数值金额、现行时间格式以及结果事件的 null 字段。
6. 依次迁移成功收款、退款申请、退款结果三处；在原调用位置记录事件，保持锁序、事务及退款处理顺序。
7. 删除被替代的 Map/序列化方法和未使用依赖，修复错位审计 Javadoc；不顺手拆分其他业务方法。

## 必须证明

- 无事务调用拒绝；事务提交时事件存在，回滚时业务与事件均不残留。
- 注入序列化/Outbox 写失败，支付、attempt、通知审计及相应退款事实整体回滚。
- 退款 complete/fail 的事件跟随各自短事务，而非包住整个渠道调用。
- 已有幂等短路不额外创建事件；发送和重放不经过本写入器重新生成 ID。
- 四事件实际生产 JSON 与旧 fixture 逐字段一致，不只是 record 与 record 自比较。

## 验证

聚焦执行新契约/事务测试以及 `PaymentNotificationAtomicityTest`、`PaymentSucceededProcessorIntegrationTest`、`ModuleDependencyTest`。真实 MySQL 运行 `PaymentNotificationMySqlIT`、`PaymentParentLockMySqlIT`，证明旧原子性和退款事务边界继续成立。

测试故障注入可调整到新委托位置，但必须从真实业务入口调用事务代理，不能只 mock 整个业务用例得出回滚结论。新测试不要只验证 getter 或私有拼装实现。

## 完成条件

- [x] 三处迁移完成，四类事件字段与状态初始化不再散落。
- [x] Appender 契约/代理/MANDATORY 和事务回滚有直接测试。
- [x] 既有资金、退款和幂等测试保持，相关场景证据已更新。
- [x] 注释与实际代码一致，聚焦门禁通过，地图同步。

## Comments

- 2026-11-12：任务已定义，依赖 01；未开始实现。

- 2026-11-12：完成类型化四事件、MANDATORY PaymentOutbox 与三处生产者迁移；聚焦54项、真实MySQL支付/退款父锁15项通过，零失败/错误/跳过。命令：./mvnw -o -Dtest=PaymentOutboxTransactionTest,PaymentNotificationAtomicityTest,PaymentNotificationBoundaryTest,PaymentSucceededProcessorIntegrationTest,ModuleDependencyTest test；同过滤的-Pmysql-it -Dit.test=PaymentNotificationMySqlIT,PaymentParentLockMySqlIT verify。日志/tmp/messaging-02-unit.log及/tmp/messaging-02-mysql.log。初次编译暴露遗留构造器/导入，修复后完整聚焦及集成重跑成功。
