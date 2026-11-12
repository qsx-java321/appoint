# 四时段预约改造任务地图

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。5/5已于9d20e30交付；四场/按需生成/统一设置仍有效，D+1～D+7及28项已由ADR0007替代为D～D+2十二项，不按旧规格重新实施。 当前能力与进度见[项目状态](../../docs/project-status.md)。

Status: resolved

- `01-database-and-settings.md`：统一设置、四场次数据库迁移与管理接口
- `02-session-catalog.md`：未来 7 天四场次只读查询
- `03-on-demand-booking.md`：按需生成、预约创建、幂等与时间窗口
- `04-payment-lifecycle.md`：固定 15 分钟支付窗口和停诊流程退出
- `05-verification-docs.md`：迁移、并发、全链路测试与文档

