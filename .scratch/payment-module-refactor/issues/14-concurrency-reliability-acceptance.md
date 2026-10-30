# 14 真实并发与可靠性验收矩阵

Type: verification
Triage: ready-for-agent
Status: resolved

## 背景和目标

用真实 MySQL 8.4 与 RabbitMQ 验证锁、唯一约束、重复、延迟、乱序、重试和 DLQ；把 spec 第十七章转成持续回归门禁。

## 范围

- 支付与取消/过期/停诊竞争、同单并发尝试、重复成功事件、重复退款。
- Publisher/consumer 各崩溃窗口、Broker 停机恢复、重试耗尽与重放。
- 统一锁顺序、可重试锁异常和所有终态库存恒等式。
- 收敛测试稳定性、超时和容器隔离，不掩盖偶发失败。

## 非范围

- 不增加新业务能力，不用 Mockito/H2 替代真实可靠性验收。

## 依赖关系

Blocked by: 13。

## 需要修改的模块

- Testcontainers 测试基础设施、MySQL/RabbitMQ IT suites、构建 profile。

## 业务不变量

- 竞争只能得到一种合法预约终态；资金事实不丢失；库存永远守恒。
- 至少一次投递可重复但业务副作用仅一次。

## 可验证的验收条件

- spec 17.2、17.3、17.4 的用例全部有真实容器覆盖并通过。
- 测试报告区分 H2、MySQL、RabbitMQ、双容器数量与结果。

## 应运行的测试

- `./mvnw test`
- `./mvnw -Pmysql-it verify`
- 新增 RabbitMQ/可靠性 profile 的完整 `verify`。

## 完成状态

- [x] MySQL 竞争矩阵
- [x] RabbitMQ 可靠性矩阵
- [x] MockPay 回调矩阵
- [x] 全套稳定通过

## Comments

- 2026-10-30：真实支付回调与取消、过期、停诊竞争均收敛到合法关闭态、唯一退款和守恒库存；重复取消仅一笔退款。
- 2026-10-30：MockPay 覆盖有效/无效签名、金额错、通知冲突、失败重试、重复成功与乱序失败；MySQL 25 项通过。
- 2026-10-30：Outbox confirm/return、Broker 中断恢复、重复投递、退款重试/DLQ、原 eventId 重放的双容器 9 项通过；完整 H2 无失败。

