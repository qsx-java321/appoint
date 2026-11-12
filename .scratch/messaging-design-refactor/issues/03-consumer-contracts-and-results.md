# 03 严格消费契约与类型化处理结果

Type: refactor/fix
Triage: ready-for-agent
Status: ready-for-agent
Blocked by: 02

依据：[spec 第 6.3、7.3、7.4 节](../spec.md)。责任场景：S05～S08、S13～S15、S22～S23。

## 目标

两 Codec 对消息类型、版本、结构及整数 ID 的处理一致，合法历史 v1 兼容，批准的非法输入明确拒绝。Processor/Worker 用 enum 表达内部结果。

## 文件清单

- 修改 `coordination/messaging/PaymentSucceededEventCodec`、`payment/messaging/RefundRequestedEventCodec`。
- 新增 `common/messaging/MessageJsonFields`，仅集中复用的严格字段读取。
- 新增 `PaymentSucceededResult`、`RefundExecutionResult`，修改对应接口、实现和全部调用方。
- 更新 `PaymentSucceededEventCodecTest`；新增 `RefundRequestedEventCodecTest` 与入口参数化测试。
- 更新 `PaymentConfirmationReliabilityIT` 退款夹具及 BeforeCommitFailureProcessor；保留 H2 测试辅助适配。

## 实施步骤

1. 按 spec 的已批准输入矩阵写兼容和非法用例，用旧 fixture 与生产 Outbox 双来源验证。
2. 提取小型 JSON 读取 helper，明确整数节点/文本整数/Long 范围/正值；不允许隐式截断或默认值。
3. PaymentSucceeded 保留确切类型与支付聚合关联校验；RefundRequested 新增同等 envelope 校验，读取 paymentOrderId 做关联判断，再映射最小 Worker 输入。
   退款消费者不把 Worker 未使用的 amount/currency/channel 等字段新增为必填；四类完整生产消息仍由生产契约测试保护。正金额/CNY 约束属于 PaymentSucceeded 既有业务校验。
4. 允许额外字段，保持正文独立于 Rabbit metadata；不要求所有消费者使用完整 wire payload。
5. Processor/Worker 保留直接 Java 调用的必要验证，Inbox 事务与渠道事务流程不动。
6. 迁移 String 返回值及断言、测试装饰器签名；不改变数据库/HTTP/消息字符串。
   同步核对 AppointmentLifecycleIntegrationTest、PaymentParentLockMySqlIT 的退款结果断言，支付通知审计等外部 SUCCEEDED 字符串保留。
7. 补全真实退款失败测试夹具的支付关联字段、类型、版本、时间，检查它仍实际调用渠道三次，而非提前进入 DLQ。

## 行为变化清单

本任务必须在 Comments 列明新增拒绝的输入：错误类型/版本形态、小数或浮点 ID、越界/零/负值、缺字段/错误结构/时间、退款聚合关联不一致。它们是用户批准的修正，不写成纯结构移动。

不新增“必须有 messageId/header”或“拒绝未知额外字段”等未批准门槛，不为 H2 放宽生产解码器。

## 验证

- 运行两 Codec、新 helper、直接业务入口与 Processor 事务相关测试。
- 运行 `LatePaymentMySqlIT`、`PaymentParentLockMySqlIT`，保留截止、唯一退款与分段事务保障。
- 运行 `PaymentConfirmationReliabilityIT`，确认完整退款夹具确实执行渠道重试，非法消息不执行任何业务。

## 完成条件

- [ ] 合法旧 v1、文本/数字整数 ID 和无 Rabbit 元数据均通过。
- [ ] 非法矩阵逐项拒绝并可追溯，退款类型/聚合校验明确。
- [ ] 返回 enum 的调用方、装饰器和断言迁移完整。
- [ ] Inbox 原子性、迟到退款、正常确认和退款分段行为保持；门禁成功。

## Comments

- 2026-11-12：任务已定义，依赖 02；非法输入收紧已获用户确认，尚未实施。
