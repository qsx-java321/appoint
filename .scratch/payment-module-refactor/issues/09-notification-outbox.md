# 09 渠道回调幂等与支付 Outbox

Type: feature
Triage: ready-for-agent
Status: resolved

## 背景和目标

渠道通知必须先验签，再在同一数据库事务中保存通知审计、第一次资金事实和 Outbox；RabbitMQ 可用性不得影响回调落库。

## 范围

- 统一 notification Controller/Adapter 转换与 `handleNotification`。
- 校验通知 ID、payload hash、支付/尝试关联、渠道订单号、金额和币种。
- 推进 attempt/order，第一次成功写稳定 `PaymentSucceeded` Outbox。
- 重复/乱序/相同 ID 不同 payload 的持久化结果与安全日志。

## 非范围

- 不发布 RabbitMQ，不确认预约；迟到支付退款在 issue 12。

## 依赖关系

Blocked by: 08。

## 需要修改的模块

- payment web/application/persistence、MockPay 通知入口。

## 业务不变量

- 先验签后改状态；金额/币种不匹配不确认资金；已成功事实不被后续失败覆盖。
- 同一通知/交易只产生一次资金副作用和一个稳定 eventId。

## 可验证的验收条件

- 正常、伪造签名、金额不匹配、重复、payload 冲突、乱序测试通过。
- 支付状态与 Outbox 在一个事务中原子提交；无需 Broker 即可成功确认回调。

## 应运行的测试

- MockPay 回调 H2 契约测试。
- MySQL 唯一约束、重复并发通知测试。

## 完成状态

- [x] 回调入口/验签
- [x] 通知审计
- [x] 资金状态与 Outbox
- [x] H2/MySQL 验收

## Comments

- 等待 issue 08 resolved。
- 2026-10-29：新增统一免 JWT 渠道通知入口；Adapter 先验签，application transaction 再校验渠道订单、金额、币种和状态。
- 2026-10-29：首次成功在同一事务推进 attempt/order、持久化通知审计和版本化 `PaymentSucceeded` Outbox；重复、payload 冲突、金额不匹配、伪造签名与迟到失败均已覆盖。
- 2026-10-29：MySQL 并发重复通知使用锁后 current read 收敛为一次资金副作用和稳定 eventId；完整 H2 70 项、MySQL Testcontainers 15 项通过。

