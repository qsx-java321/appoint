# 支付模块独立改造切换与前向恢复 Runbook

本 runbook 用于 V4–V7 支付结构迁移和新应用切换。执行人应记录开始/结束时间、应用版本、数据库实例、备份位置、校验值和每一步结果。不得把数据库密码、JWT/RabbitMQ 密钥或完整敏感报文写入记录。

## 1. 发布前门禁

- 确认待发布构建的 H2、MySQL 8.4、RabbitMQ Testcontainers 和真实 HTTP 验收全部通过。
- 确认 V4 是任何支付结构 DDL 之前的第一项迁移，V5–V7 只做增量加列、加表、约束和索引。
- 确认应用和 RabbitMQ 声明可使用同一版本配置启动，MockPay 仅在 dev/test profile 注册。
- 约定维护窗口、执行人、复核人和前向修复版本；不要安排会自动清表或执行 down migration 的工具。

## 2. 进入维护窗口

1. 停止创建预约、模拟支付/MockPay、取消、过期扫描、退款 worker、对账和排班关闭等写入口；查询可继续。
2. 停止所有应用实例或切换为只读维护版本，确认没有旧版本实例仍能接收写请求。
3. 等待正在执行的写事务结束，记录数据库活动事务检查结果。
4. 在同一数据库执行只读预检：

```sql
SELECT COUNT(*) AS active_legacy_payments
FROM appointment_orders appointment
JOIN payment_orders payment ON payment.appointment_order_id = appointment.id
WHERE appointment.status = 'PENDING_PAYMENT'
  AND payment.status = 'UNPAID';
```

结果必须为 `0`。非零时终止发布：不执行 V5–V7，不手工删除或改写订单；先按既有流程完成或关闭旧订单，再重新安排维护窗口。

## 3. 备份与基线校验

- 按现有发布规范做 MySQL 逻辑备份，备份文件放在受控位置。
- 记录备份文件大小与 SHA-256；执行一次只读恢复演练或使用最近一次已验证的恢复记录。
- 记录迁移前 Flyway 版本、预约/支付各状态数量、库存恒等式异常数和活跃旧支付计数。
- 任一校验不明确时停止，不以猜测或补写历史金额继续发布。

## 4. 执行迁移和部署

1. 在写入口仍关闭时启动单个迁移实例，执行 V4。V4 失败表示旧活跃支付仍存在；保持维护状态并终止发布。
2. V4 通过后顺序执行 V5–V7，禁止跳过、重排或手工标记成功。
3. 部署新应用配置；声明 durable exchange、queue、binding、retry、DLX 和 DLQ。
4. 消费者启动前核对拓扑名称、routing key、publisher confirm、mandatory return 和死信绑定。
5. 启动一个应用实例，完成健康检查后再逐步扩容。

## 5. 切换后验证

- Flyway schema history 只包含成功迁移且版本顺序正确；重复执行迁移为零项。
- Legacy 终态订单数量、状态和未知金额空值保持不变。
- 新表、唯一约束、扫描索引和金额/状态约束存在；库存恒等式异常数为零。
- 用 dev/test 等价环境完成一条 MockPay 冒烟链路，核对预约、支付尝试、通知、Outbox、Inbox 与库存。
- 检查 Outbox 最老事件、DLQ、`PAID + PENDING_PAYMENT`、`REFUND_PENDING` 和长期 `CREATED/PROCESSING` 指标。
- 全部通过后恢复写流量；先小流量观察，再完全开放。

## 6. 失败处理与前向恢复

- V4 阻断：保持旧 schema，不执行后续迁移；完成/关闭旧活跃订单后重新发布。
- V5–V7 或应用启动失败且尚无新模型写入：停止应用，保留数据库与迁移证据，可回退应用版本；不删除已成功添加的表/列。
- 已产生 attempt、notification、Outbox、Inbox 或 refund 数据后：关闭新写入口，保留所有资金和事件记录，部署修复版本向前恢复。
- RabbitMQ 不可用：不回退已提交的支付结果；保持 Outbox 积压，恢复 Broker/绑定后重发。
- 消费或退款异常：停止对应 worker，保留 eventId/refundNo，使用 Inbox/DLQ/对账恢复；不得手工复制事件并生成新 eventId。
- 任何无法证明安全的资金/库存不一致转人工处理，不擅自修改支付成功事实或重新占用号源。

## 7. 收尾记录

记录最终应用版本、Flyway 版本、备份校验值、迁移耗时、冒烟订单标识、关键指标和遗留人工项。确认维护模式已解除、定时任务和消费者按计划恢复，并由复核人签字确认。
