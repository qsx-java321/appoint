# 06 取消、过期与停诊生命周期协调

Type: refactor
Triage: ready-for-agent
Status: resolved

## 背景和目标

把取消、过期和停诊中的跨模块顺序从 `AppointmentLifecycleServiceImpl` 移到协调模块，让预约与支付各自推进自有写模型，并保留同步兼容行为。

## 范围

- 建立预约确认/取消/过期/停诊 application commands。
- 建立支付生命周期锁定快照与关闭接口。
- `BookingLifecycleCoordinator` 按排班→预约→支付顺序编排。
- Controller、job、排班管理只调用相应协调/application interface。

## 非范围

- 不实现异步支付确认或退款 worker。

## 依赖关系

Blocked by: 05。

## 需要修改的模块

- `appointment`、`payment`、`coordination`、过期 job、排班管理。

## 业务不变量

- 统一锁顺序与条件更新；取消/过期/停诊只释放一次库存与有效预约占用。
- 外部渠道调用不在该本地事务中；当前同步兼容结果不变。

## 可验证的验收条件

- appointment/payment 写模块互不导入对方 Mapper/entity/impl；协调模块不导入 persistence。
- 现有取消、过期、停诊及其竞争测试全部通过，无循环依赖。

## 应运行的测试

- `./mvnw test`
- MySQL 支付/取消/过期/停诊竞争：`./mvnw -Pmysql-it verify`。

## 完成状态

- [x] lifecycle interfaces
- [x] coordinator 编排
- [x] 入口迁移
- [x] 架构/H2/MySQL 验收

## Comments

- 2026-10-29：预约/支付分别提供 lifecycle application interface，协调器统一按排班→预约→支付顺序锁定与推进。
- 2026-10-29：Controller、过期 job、排班停诊入口已迁移，删除跨写模块生命周期实现及全部 ArchUnit Legacy 豁免。
- 2026-10-29：聚焦测试 26 项、完整 H2 58 项、MySQL Testcontainers 13 项通过。

