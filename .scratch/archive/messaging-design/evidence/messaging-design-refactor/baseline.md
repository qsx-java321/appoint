> 历史验收材料：正文中的时间、命令、路径和“当前”均属于原检查点；当前入口见[项目状态](../../../../../docs/project-status.md)。导航链接已迁移。

# 消息改造基线（任务01）

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。消息设计9/9已交付，生产契约仍有效；原323等验收与源码摘要为963051f历史快照。基线测试类后来合并，当前测试入口和结果以测试整理交付为准。 当前能力与进度见[项目状态](../../../../../docs/project-status.md)。 测试迁移见[当前消息场景](../../../delivery-quality/evidence/test-suite-refactor/message-scenarios.md)。

日期：2026-11-12。生产源码基线：`cf027682bd516b57d0d2f0a830e3d592feee9eb3`。

初始工作区：方案目录与 scripts/__pycache__/ 未跟踪；无业务代码改动。Java 21.0.11，Docker Desktop 可用；沙箱直接连接 socket 拒绝，授权运行后 Testcontainers 可正常创建隔离容器。Compose 初始无运行服务；不改凭据或卷。

## 消息契约来源

三个生产者：PaymentNotificationServiceImpl.payload/insertOutbox、DefaultPaymentRefundOperations.insertRequestedEvent、DefaultRefundWorker.insertResultEvent。四类事件的字段名和 null 来自上述现有代码；样本统一使用脱敏合成标识。

ApiJsonConfig 对 Long.class 注册 ToStringSerializer；生产 ObjectMapper 的真实行为由新增 MessagingBaselineContractTest 验证。八份固定样本位于 src/test/resources/messaging/v1/，四类各含文本/数字ID；样本不由未来 record 生成。

## 不变与允许修正

保持 envelope 六字段、eventVersion=1、原 routing key/队列、payload字段、原事件ID和消费者名。发布器只转发原正文；支付确认 Inbox 同事务，退款执行分段事务且无预约 Inbox。

允许拒绝小数/浮点/越界ID、不支持版本形态、错误类型、结构缺失及非法时间；合法整数文本/数字均保留。退款现有最小IT夹具须补支付聚合字段；不新增 Rabbit metadata 必填要求。

## 现有覆盖入口

| 保障 | 测试 |
| --- | --- |
| 资金/审计/事件回滚 | PaymentNotificationAtomicityTest、PaymentNotificationMySqlIT |
| Inbox与业务原子性/迟到退款 | PaymentSucceededProcessorIntegrationTest、LatePaymentMySqlIT |
| 支付父锁、退款事务外渠道 | PaymentParentLockMySqlIT |
| confirm/return、TTL、重放与故障窗口 | ConfirmedMessagePublisherTest、OutboxPublisherReliabilityIT、PaymentConfirmationReliabilityIT |
| 依赖方向 | ModuleDependencyTest |

本轮实际执行结果见 commands.md。历史 263/37/92/15 不作为改造验收结果。
