# 08 支付尝试状态机与单活跃尝试

Type: feature
Triage: ready-for-agent
Status: resolved

## 背景和目标

一张支付单允许多次尝试，但同一时刻最多一个处理中尝试；渠道网络调用必须位于数据库事务之外。

## 范围

- 实现 `PaymentService.startAttempt/getOwned`。
- T1 创建 `CREATED`、支付单转 `PAYING`；事务外调用 Gateway；T2 转 `PROCESSING` 或 `FAILED` 并安全恢复 `UNPAID`。
- MockPay checkout URL/DTO 与用户归属、截止、Legacy 校验。
- MySQL 并发创建尝试与长期 `CREATED` 可识别测试。

## 非范围

- 不处理渠道成功通知，不发布 Outbox。

## 依赖关系

Blocked by: 06、07。

## 需要修改的模块

- `payment.application/domain/persistence`、Mock web adapter、API DTO。

## 业务不变量

- 每单同一时刻至多一个 `CREATED/PROCESSING`；截止后、CLOSED/PAID/Legacy 不可新建。
- 渠道失败不结束预约暂占；并发与进程中断不会产生第二个活跃尝试。

## 可验证的验收条件

- 成功返回稳定 attempt ID、渠道订单号和 checkout URL；失败可再次发起。
- MySQL 并发请求只有一个成功；测试证明 Gateway 调用不在长事务/行锁内。

## 应运行的测试

- 支付 application/HTTP 测试。
- MySQL Testcontainers 并发与锁测试；`./mvnw -Pmysql-it verify`。

## 完成状态

- [x] T1/T2 编排
- [x] HTTP 202 尝试基础响应
- [x] 失败恢复
- [x] MySQL 并发验收

## Comments

- 2026-10-29：完成 T1 建 attempt/PAYING、事务外 Gateway、T2 PROCESSING/失败恢复 UNPAID。
- 2026-10-29：新增 `POST /api/v1/payments/{id}/attempts` HTTP 202，校验归属、截止和 Legacy。
- 2026-10-29：H2 70 项、MySQL Testcontainers 14 项通过；并发发起仅一项成功，Gateway 测试确认无活动数据库事务。
