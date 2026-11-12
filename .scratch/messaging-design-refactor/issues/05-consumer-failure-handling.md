# 05 共用消费失败处理与完整确认矩阵

Type: refactor/fix
Triage: ready-for-agent
Status: ready-for-agent
Blocked by: 04

依据：[spec 第 7.5、8 节](../spec.md)。责任场景：S08、S16～S21、S23。

## 目标

两个 Listener 保持“解码→业务接口→成功 ACK”的直观流程，把重复的失败分类、TTL 重试与原消息确认集中维护。

## 文件与动作

- 新增 `common/messaging/RabbitConsumerFailureHandler`、`RabbitRetryRoute`。
- 修改 `PaymentSucceededListener`、`RefundRequestedListener`，删除重复 private retryOrDeadLetter。
- 新增公共 helper 测试与 `RefundRequestedListenerTest`，扩展 `PaymentSucceededListenerTest`。
- 调整相关可靠性测试接线，保留真实 Rabbit 的 TTL/路由测试。

## 实施约束

1. helper 是 Rabbit 专用具体类，不承担业务、事务、解码或数据库职责，不必再增加接口。
2. 两个 route 描述由 Listener 使用原拓扑常量装配；common 不导入 payment 的 Entity/Mapper/实现或配置类，不建设业务路由注册框架。
3. 只把明确无效契约归为永久失败，其余业务运行失败保留现行三次策略；不要顺便改变所有 ErrorCode 的重试语义。
4. 原正文、eventId、messageId 及必要属性保留；计数头名称、TTL 1000、confirm 等待五秒保持。
5. 业务成功 ACK 必须在业务调用 catch 范围外；ACK IOException 不得被抓回去当成业务失败发新重试。
6. 重试确认成功后 ACK；转发失败 NACK/requeue；原消息不能同时 ACK 和 NACK。
7. 只在失败处理路径检查重试头；正常/幂等成功仍 ACK。对非负 int 范围的 Integer/Long 头先比较耗尽再加一，覆盖 Integer.MAX_VALUE；负数、非整数和越界按 spec 拒绝，避免类型异常热重投。此项记为非法传输输入修正。

## 必测矩阵

两个具体 Listener 都需要验证正常/幂等成功、解码无效、入口 VALIDATION_ERROR、暂时失败、第三次耗尽、转发失败和 ACK 失败。公共 helper 详细验证第一次/第二次计数与 TTL、完整属性保留、调用顺序、非法头和确认异常传播。

不要只测试 helper 然后忽略两个 Listener 是否传错路由、计数头或消费处理器。

## 验证

聚焦执行两个 Listener、helper 和 ConfirmedMessagePublisher 测试，再执行两类 ReliabilityIT。真实 Rabbit 至少验证两次约一秒 TTL、正确业务队列和 DLQ、三次业务执行，以及转发不确定时保留原消息。数值“三次”不声称为绝对物理投递上限。

## 完成条件

- [ ] 重复失败流程已集中，两个 Listener 仍能直接读出主线。
- [ ] 两入口接线与完整失败矩阵覆盖，确认异常不会误触发业务重试。
- [ ] S17～S21 实际证据完整，真实 TTL/DLQ/退款渠道重试保持。
- [ ] 注释解释确认顺序与重复可能性，门禁通过。

## Comments

- 2026-11-12：任务已定义，依赖 04；未改重试策略、未运行测试。
