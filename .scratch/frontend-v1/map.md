# 前端任务地图

实施基线：[方案](../../docs/frontend-implementation-plan.md)。任务按顺序执行；实际检查写入verification.md，不把设计原型检查当业务验收。

| 编号 | 任务 | Blocked by | 状态 |
| --- | --- | --- | --- |
| 01 | [方案与契约](issues/01-plan.md) | 无 | resolved |
| 02 | [工程与身份](issues/02-foundation.md) | 01 | resolved |
| 03 | [预约与资料](issues/03-booking.md) | 02 | resolved |
| 04 | [资金与就诊详情](issues/04-appointment.md) | 03 | resolved |
| 05 | [医生与管理](issues/05-staff.md) | 04 | resolved |
| 06 | [验收与维护](issues/06-verification.md) | 05 | resolved |

## Comments

- 提交记录：实现与验收已纳入`e91d870`，证据移入[前端交付档案](../archive/frontend/summary.md)，原任务正文保留。

- 2026-11-14：先完成方案，按顺序实施；交付提交暂为空，本轮不自动提交。
- 2026-11-14：01–06完成。22项单元、10项接口夹具浏览器、1项真实H2联调及构建/文档检查通过；[验收与限制](verification.md)。未提交原文保留。
