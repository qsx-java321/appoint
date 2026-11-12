# 07 架构约束与注释复核

Type: test/docs
Triage: ready-for-agent
Status: ready-for-agent
Blocked by: 06

依据：[spec 第 5、8 节](../spec.md)。责任场景：S27～S29。

## 目标

让接口和依赖约定可由测试执行，并核对所有本轮注释足够解释事务/故障责任。前序任务已经同步编写注释，本任务负责整体复核，不为无关源码补注释或重排。

## 架构规则

扩展现有 `ModuleDependencyTest` 与相关反例 fixture：

1. payment/coordination 的 application/domain 不依赖 Spring AMQP 或 RabbitMQ Client。
2. contract/event 不依赖 Spring/Jackson/Mapper/Entity/消费者实现。
3. common messaging helper 不反向依赖 payment/appointment/coordination 的持久化、实现或业务配置；route 信息由外部接线提供。
4. 应用接口的参数和返回类型都不得暴露 Entity，覆盖泛型、数组、record 成员等可达类型；不要只查返回值，也不要把所有实现字段纳入对外契约检查。
5. 保留既有模块间依赖、只读查询、Controller 与 Mapper 隔离规则。

每类新规则都有故意违规 fixture，确认测试会拒绝。fixture 留在测试源码，不能以 allowEmptyShould 或大范围 exclude 掩盖生产违规。共享 contract/新增 enum 导致旧规则误判时精确修正规则的对象定义。

## 注释复核

- PaymentOutbox 的 MANDATORY、事件 ID 生成、序列化失败回滚和返回未提交语义。
- Publisher 的 confirm/return、超时不确定、原文重发、PUBLISHED 不等于消费完成。
- Processor 的 Inbox 同事务与新事件重复事实保护。
- Worker 的分段事务与 refundNo 渠道幂等，不写成整段本地原子事务。
- 两 Listener/helper 的业务提交、重试转发、ACK/NACK 顺序。
- Replay 的目标白名单、原文、源确认和退款结果无监听器边界。
- 修复错位 Javadoc 和陈旧 @see、类名、包路径；不堆“设置字段”之类冗余说明。

## 验证

执行 `ModuleDependencyTest` 及必要的契约反例测试，再执行常规测试确认反射/扫描/注入没有受损。静态核对 JavaDoc 引用与文档代码入口；可做范围内 Javadoc/doclint 检查，记录实际覆盖，不把它等同业务验证。

## 完成条件

- [ ] 新旧架构规则同时通过，每类新规则的违规 fixture 均被拒绝。
- [ ] 所有本轮接口契约与实现一致，关键顺序有原因说明。
- [ ] 不存在 application 的 AMQP 依赖或实体接口泄漏。
- [ ] 常规门禁通过，S27～S28 及 S29 的接口注释/源码引用子项完成；S29 的正式文档一致性由 09 收口，不提前标整项通过。

## Comments

- 2026-11-12：任务已定义，依赖 06；不要求全面隔离同模块 Mapper。
