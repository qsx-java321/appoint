# 04 支付可靠性持久化模型

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。16/16已于a293afa交付；同步兼容入口后来删除、接口继续深化、消息设计与测试整理也已完成。下文目标/差距及83等数量均按原实施检查点理解，不是当前待办。 当前能力与进度见[项目状态](../../../docs/project-status.md)。

Type: feature/migration
Triage: ready-for-agent
Status: resolved

## 背景和目标

建立支付尝试、退款、通知、Outbox 与 Inbox 的数据库真相和最小 persistence 类型，为拆分模块与可靠事件提供承载结构。

## 范围

- V6 创建 `payment_attempts`、`payment_refunds`、`payment_notifications`、`payment_outbox_events`、`coordination_inbox_events`。
- V7 增加状态、金额、唯一约束和扫描索引，兼容 Legacy 空值。
- 添加对应 entity/mapper，仅实现独立 CRUD/锁定原语。
- 评估并记录 `payment_orders.user_id` 保留或移除决定及完整性验证。

## 非范围

- 不实现渠道调用、消息发布、消费者或退款 worker。

## 依赖关系

Blocked by: 02。

## 需要修改的模块

- `payment` persistence、`coordination` persistence、Flyway、迁移测试。

## 业务不变量

- 一预约一支付单；一支付单最多一笔第一阶段全额退款。
- 渠道通知 ID、attempt/refund/event ID 唯一；Inbox `(consumerName,eventId)` 唯一。
- 历史未知金额不被伪造，Legacy 不进入新生命周期。

## 可验证的验收条件

- 空库、Legacy 终态、二次迁移均通过；约束在 MySQL 8.4 实际生效。
- 扫描索引与 spec 一致；entity/mapper ownership 不跨写模块。

## 应运行的测试

- `./mvnw -Dtest=InitialSchemaMigrationTest test`
- MySQL Testcontainers 约束测试与 `./mvnw -Pmysql-it verify`。

## 完成状态

- [x] V6/V7
- [x] persistence 类型
- [x] user_id 决策记录
- [x] H2/MySQL 验收

## Comments

- 等待 issue 02 resolved。
- 2026-10-29：V6/V7、五类可靠性表、模块自有 entity/mapper、锁定与 Outbox 扫描原语完成。
- 2026-10-29：本期保留 `payment_orders.user_id` 作为兼容所有权快照；创建事务写入一致值，并由集成测试验证。
- 2026-10-29：真实 MySQL 测试发现并修复 CHECK 对 NULL 返回 UNKNOWN 的漏洞；最终 H2 58 项、MySQL 13 项全部通过。
