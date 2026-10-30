# 05 创建预约协调与写模块拆分

Type: refactor
Triage: ready-for-agent
Status: resolved

## 背景和目标

当前预约创建直接构造 `PaymentOrder` 并调用 `PaymentOrderMapper`。引入 `BookingCoordinator`，让预约模块只创建预约暂占，支付模块只创建支付单，同时保留同库本地事务的原子创建。

## 范围

- 提取预约写模块 application interface 与 hold 结果。
- 支付模块实现 `createOrder` application interface。
- 协调模块编排幂等登记、预约暂占、支付单创建和回执完成。
- 保留原 HTTP 创建契约，由 Controller 调用协调入口；预约查询/取消 service 不反向依赖协调层。

## 非范围

- 不改变支付成功路径，不接入渠道/消息。

## 依赖关系

Blocked by: 03、04。

## 需要修改的模块

- `appointment`、`payment`、新增 `coordination.application`。

## 业务不变量

- 排班→就诊人锁顺序、幂等键、有效预约唯一约束、库存守恒与全事务回滚保持不变。
- 协调者不使用两个写模块 Mapper/实体。

## 可验证的验收条件

- 原子返回 `appointmentOrderId + paymentOrderId + amount + currency + expiresAt`。
- 预约创建类不再导入 payment Mapper/entity；支付创建不查询排班计算费用。
- 既有幂等、快照和 MySQL 抢号测试通过。

## 应运行的测试

- 预约创建、幂等、库存 H2 测试。
- MySQL 并发创建与回滚测试；`./mvnw -Pmysql-it verify`。

## 完成状态

- [x] 写模块 interfaces
- [x] BookingCoordinator
- [x] 兼容入口
- [x] H2/MySQL 验收

## Comments

- 2026-10-29：完成预约暂占、支付单创建 application interface；协调模块不引用写模块 Mapper/实体。
- 2026-10-29：幂等唯一键冲突在首次创建事务完整回滚后重放，避免 rollback-only 事务读取。
- 2026-10-29：聚焦架构/幂等/库存 25 项、完整 H2 59 项、MySQL Testcontainers 13 项全部通过。

