# 01 基线行为与模块契约

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。16/16已于a293afa交付；同步兼容入口后来删除、接口继续深化、消息设计与测试整理也已完成。下文目标/差距及83等数量均按原实施检查点理解，不是当前待办。 当前能力与进度见[项目状态](../../../docs/project-status.md)。

Type: test/refactor
Triage: ready-for-agent
Status: resolved

## 背景和目标

当前有价值的支付行为主要由预约生命周期集成测试覆盖，支付单元测试只验证浅层转发。先锁定迁移前可观察行为、建立目标状态枚举，并用自动化规则防止继续扩大跨写模块依赖，为后续搬移逻辑提供安全网。

## 范围

- 补齐支付截止、重复取消等缺口的 characterization tests。
- 定义支付单、支付尝试、退款、支付渠道和查询 workflow 的稳定枚举。
- 建立包依赖约束测试；显式记录并仅豁免当前两个预约侧 Legacy 跨支付持久化类。
- 保持现有 HTTP、数据库结构和同步 `simulate-success` 行为不变。

## 非范围

- 不移动业务逻辑，不创建 V4+ 表，不接入 RabbitMQ/MockPay。
- 不删除或改成异步 `simulate-success`。
- 不用枚举一次性替换全项目字符串状态。

## 依赖关系

Blocked by: 无。

## 需要修改的模块

- `payment`：领域枚举。
- `bookingquery`：只读 workflow 枚举。
- `src/test`、`pom.xml`：characterization 与架构约束测试。

## 业务不变量

- 支付截止边界到达即不可支付。
- 重复支付/取消不重复推进库存或就诊序号。
- 库存满足 `总名额 = 可用 + 暂占 + 已确认`。
- 此 issue 不改变任何现有外部可观察行为。

## 可验证的验收条件

- 新枚举覆盖 spec 第八章和 11.6 的全部目标值。
- 架构测试阻止新的 appointment→payment Mapper/entity/impl 与 payment→appointment Mapper/entity/impl 依赖，并保留明确、可删除的 Legacy 豁免。
- characterization tests 覆盖支付截止精确边界和已确认取消重复调用。
- H2 基线全部通过；MySQL 既有并发基线继续通过。

## 应运行的测试

- `./mvnw -Dtest=PaymentDomainEnumTest,ModuleDependencyTest,ScheduleInventoryIntegrationTest test`
- `./mvnw test`
- `./mvnw -Pmysql-it verify`

## 完成状态

- [x] 资料与现状差距盘点
- [x] 红灯：枚举契约测试先失败
- [x] 绿灯：最小枚举实现
- [x] characterization tests
- [x] 模块依赖约束
- [x] H2/MySQL 验收

## Comments

- 2026-10-29：已认领。测试 seam 为公开 service/HTTP 行为与包依赖边界，不断言私有调用次数。
- 2026-10-29：枚举契约先因目标类型不存在而红灯，补最小枚举后绿灯。
- 2026-10-29：新增支付截止精确边界、已确认预约重复取消 characterization；新增 ArchUnit 生产代码规则及违规 fixture，证明新 appointment→payment Mapper 依赖会失败。
- 2026-10-29：保留的 Legacy 豁免为 `AppointmentLifecycleServiceImpl`、`AppointmentCreationTransactionService`、`AppointmentLifecycleService` 与 `PaymentServiceImpl`，阶段 3 删除。
- 2026-10-29：`./mvnw test` 51 项通过；`./mvnw -Pmysql-it verify` 的 H2 51 项、MySQL 11 项全部通过。

