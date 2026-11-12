# 04 发布接口收紧与 Rabbit 适配器迁移

Type: refactor
Triage: ready-for-agent
Status: ready-for-agent
Blocked by: 03

依据：[spec 第 5、7.2、11 节](../spec.md)。责任场景：S04、S09～S12、S28。

## 目标

应用发布事务依赖不可变消息和明确发布契约，Rabbit 适配器迁出 application；不改数据库领取/发布策略。

## 文件与动作

1. 新增 `payment/application/OutboxMessage`，只保留 eventId/type/version/payload。
2. 修改 `OutboxMessagePublisher` 参数及异常，新增 `OutboxPublicationException`，说明超时可能已送达。
3. `OutboxPublishTransaction` 从已锁 Entity 创建 record，不把 Entity 传出发布接口。
4. 将 `RabbitOutboxMessageSender` 移到 `payment/messaging`；仍用原拓扑、持久化、消息 ID 与正文，不重建 envelope。
5. 在 Adapter 处转换已知发布失败并保留原因，保持 ConfirmedMessagePublisher 的 confirm/return 检查和中断恢复。
6. 仅在 publisher.publish 周围捕获 OutboxPublicationException 并记录发布失败；markPublished、影响行数检查及 recordFailure 的数据库异常直接传播、整批回滚，不返回成功计数、不计为 Broker 失败，允许原事件重发。
7. 更新测试构造器/注入及文档代码链接，保留独立 Spring 事务 Bean，避免迁包引入自调用。

## 契约边界

- 发布正常返回表示获 Broker 确认且无 return，不表示消费者已处理。
- 失败重试沿用原 eventId/正文，不能生成新事件。
- `PaymentOutbox` 负责创建事件，Publisher 负责发送既存事件，二者不能混用。
- 不改变 PENDING/PUBLISHED/FAILED、失败次数、退避、批量和 SKIP LOCKED。
- 不解析历史 payload 的业务完整性；传输专用不完整 JSON fixture 继续可发布。

## 验证

- `ConfirmedMessagePublisherTest` 与新增 Adapter 契约测试，涵盖确认成功、return、NACK、超时、断连。
- 运行 `OutboxPublisherReliabilityIT`，检查持久化、原文、路由、并发领取与失败耗尽。
- 为数据库标记失败增加真实事务故障测试；可以在 08 完善完整消费收敛，但本步必须已证明不虚报成功且 DB 回滚。
- 保留全局原子性/幂等测试，不以简单 mock void 调用代替可靠发布证明。
- 迁包后至少执行一次 `./mvnw clean test`，排除旧 class 残留；不要为重复组件放宽扫描/架构规则。

## 完成条件

- [ ] 发布接口无 Entity/AMQP 参数，异常契约明确。
- [ ] Rabbit sender 已迁移，原实现删除且仅注册一个 Adapter。
- [ ] 原文、ID、状态与退避保持；数据库标记失败不能伪装发布成功。
- [ ] 聚焦及真实发布门禁成功，场景证据和注释更新。

## Comments

- 2026-11-12：任务已定义，依赖 03；仍采用持锁发布，未做租约优化。
