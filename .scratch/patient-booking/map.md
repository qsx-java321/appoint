# 任务地图：patient-booking

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。就诊人/快照/幂等已交付；V1归一化与本地库重建是当时记录。当前V1～V10必须增量迁移，不据本页清库或重建业务卷。 当前能力与进度见[项目状态](../../docs/project-status.md)。

Status: resolved

| Issue | 类型 | 依赖 | 状态 |
|---|---|---|---|
| [01 领域与初始结构](issues/01-domain-and-schema.md) | feature | 无 | resolved |
| [02 就诊人管理](issues/02-patient-management.md) | feature | 01 | resolved |
| [03 预约快照](issues/03-appointment-snapshot.md) | feature | 02 | resolved |
| [04 创建预约幂等](issues/04-idempotent-creation.md) | feature | 03 | resolved |
| [05 验收与交付](issues/05-verification-and-delivery.md) | verification | 01-04 | resolved |
