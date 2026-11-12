# 15 bookingquery 跨模块只读投影

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。16/16已于a293afa交付；同步兼容入口后来删除、接口继续深化、消息设计与测试整理也已完成。下文目标/差距及83等数量均按原实施检查点理解，不是当前待办。 当前能力与进度见[项目状态](../../../docs/project-status.md)。

Type: refactor/feature
Triage: ready-for-agent
Status: resolved

## 背景和目标

当前预约 Mapper 直接联查支付表。将跨表读取移入专用 `bookingquery`，提供金额、支付尝试、退款摘要和用户可读 workflow，同时保持写模型 ownership 清晰。

## 范围

- 移动详情/列表联查 SQL、DTO、映射到 `bookingquery`。
- 计算 spec 11.6 的 `workflowStatus`，展示费用、尝试和退款摘要。
- Legacy 空金额/旧终态继续可读；查询对象不被写模块作为实体复用。
- 保持所有权校验与 Long ID 字符串序列化。

## 非范围

- 查询模块不写任何业务表，不新增预约领域状态。

## 依赖关系

Blocked by: 11、12。

## 需要修改的模块

- 新增 `bookingquery` controller/dto/mapper，收窄 appointment query 职责。

## 业务不变量

- `workflowStatus` 仅为投影；`PAID + PENDING_PAYMENT` 显示确认中。
- 查询异常关联不静默伪造数据，跨账号仍禁止读取。

## 可验证的验收条件

- 等待支付、处理中、确认中、已确认、退款中、已退款、过期映射正确。
- 写模块不使用 bookingquery DTO/Mapper；Legacy 查询可用。

## 应运行的测试

- 查询集成/MVC 契约测试、Legacy 迁移查询测试。
- `./mvnw test` 与相关 MySQL IT。

## 完成状态

- [x] 查询模块搬移
- [x] workflow/摘要
- [x] Legacy/所有权验证
- [x] 架构/H2/MySQL 验收

## Comments

- 等待 issue 11、12 resolved。
- 2026-10-30：跨预约、支付、排班的 SQL 与内部查询记录已迁入只读 `bookingquery`；公开查询接口仍由 appointment 包提供，避免写模块反向依赖查询模块。
- 2026-10-30：投影增加金额/币种、支付尝试数量及最新尝试、退款摘要，并覆盖等待支付、处理中、确认中、已确认、退款中、已退款和过期；Legacy 空金额终态保持可读。
- 2026-10-30：查询/架构聚焦测试通过；完整 H2 83 项、MySQL Testcontainers 24 项通过。

