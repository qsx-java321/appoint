# 02 Legacy 支付预检与切换 runbook

Type: migration
Triage: ready-for-agent
Status: resolved

## 背景和目标

V4 必须在任何支付结构 DDL 前阻断仍可推进的旧活跃支付，并把维护窗口、备份、验证和前向恢复步骤变成可演练交付物。

## 范围

- Java V4 预检 `PENDING_PAYMENT + UNPAID` 等活跃旧组合。
- 编写维护窗口、停止写入口、备份校验、迁移校验、冒烟和前向恢复 runbook。
- H2 与 MySQL 验证预检失败时后续列/表尚未出现。

## 非范围

- 不添加费用列或支付新表；不处理真实生产数据。

## 依赖关系

Blocked by: 01。

## 需要修改的模块

- Flyway Java migration、迁移测试、`docs/runbooks`。

## 业务不变量

- 不删除或改写 Legacy 订单；预检与 DDL 之间不得继续产生旧写入；失败保持原 schema 可用。

## 可验证的验收条件

- 空库/旧终态通过；任一旧活跃支付阻断；失败时 V5+ 结构不存在。
- runbook 明确不可破坏性 down migration，写入新模型后只允许前向修复。

## 应运行的测试

- `./mvnw -Dtest=InitialSchemaMigrationTest test`
- `./mvnw -Pmysql-it verify`

## 完成状态

- [x] V4 预检
- [x] 切换与恢复 runbook
- [x] H2/MySQL 迁移验证

## Comments

- 2026-10-29：issue 01 resolved，已认领。活跃旧支付按 `PENDING_PAYMENT + UNPAID` 判断；终态数据只读保留。
- 2026-10-29：新增 V4 Java migration；H2 验证空库、活跃阻断、终态保留和重复迁移，MySQL 8.4 验证阻断不改写订单且后续表未出现。
- 2026-10-29：新增 `docs/runbooks/payment-module-cutover.md`，覆盖维护窗口、备份校验、RabbitMQ 拓扑检查、冒烟、回退边界与前向恢复。
- 2026-10-29：完整 `./mvnw -Pmysql-it verify` 通过：H2 53 项、MySQL 12 项，0 失败、0 错误、0 跳过。

