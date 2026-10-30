# 12 异步全额退款与迟到支付补偿

Type: feature
Triage: ready-for-agent
Status: resolved

## 背景和目标

已确认预约取消应立即释放号源，资金退款异步进行；关闭后收到成功回调属于迟到支付，必须退款且不恢复预约。

## 范围

- `requestFullRefund` 幂等建立每单唯一全额退款与 `RefundRequested`。
- 已确认取消转 `REFUND_PENDING`，HTTP 202；预约/库存立即完成。
- 迟到支付在回调侧和协调消费侧双重确保同一退款存在。
- refund worker 调用 Gateway，处理成功/失败、重试与退款事件。

## 非范围

- 不支持部分退款、多次退款、人工改额或已确认停诊自动退款。

## 依赖关系

Blocked by: 08、10、11。

## 需要修改的模块

- payment refund application/worker、coordination lifecycle、查询摘要。

## 业务不变量

- 一支付单最多一笔原路全额退款；金额/币种来自不可变快照。
- 退款失败不恢复预约、库存或就诊序号；迟到支付不恢复预约。

## 可验证的验收条件

- 已确认取消立即释放，最终退款成功/失败均保持预约取消。
- 重复取消/迟到事件/worker 重投不产生第二笔退款或第二次渠道退款。

## 应运行的测试

- MockGateway 退款契约与 H2 生命周期测试。
- MySQL 唯一约束/竞争、RabbitMQ retry/DLQ 集成测试。

## 完成状态

- [x] 退款 application/outbox
- [x] 取消/迟到支付协调
- [x] refund worker
- [x] MySQL/RabbitMQ 验收

## Comments

- 已完成幂等 `requestFullRefund`、每支付单唯一全额退款和版本化 `RefundRequested` Outbox。
- 已确认预约取消立即释放号源并返回 HTTP 202；无资金待支付取消保持 HTTP 200。
- 支付中取消后的迟到成功回调记录真实收款，回调侧与 `PaymentSucceeded` 消费侧均幂等确保退款，不恢复预约。
- refund worker 使用 T1 领取、事务外 Gateway、T2 成功/失败落库；重复投递不重复渠道退款，失败按 1 秒退避重试三次后进入 DLQ。
- 验证：`./mvnw test` 74 项通过；`./mvnw -Pmysql-it verify` H2 74 + MySQL 15 项通过；`./mvnw -Preliability-it verify` H2 74 + 双容器 9 项通过。
