# API 字符串 ID 契约

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。六项当时任务已完成（1c570fe）；独立讲解页docs/business-flow-demo.html随后在9d20e30删除，当前没有业务前端或该说明页。此处UI与费用/就诊人范围只指2026-10-26，不是当前能力边界。 当前能力与进度见[项目状态](../../../docs/project-status.md)。

Type: task
Status: resolved

Blocked by: 01

## 验收

- 所有 `Long` 响应 ID 为 JSON 字符串。
- `scheduleId` 字符串和数字请求均可使用。
- 业务 API 鉴权边界不变，项目不新增静态业务入口。

## Comments

使用公开 MVC 边界执行红绿循环。

已完成全局包装类型 `Long` 字符串序列化，保留业务码和有效时长的数字类型；字符串 `scheduleId` 创建预约通过。业务 API 的 JWT 边界保持不变。
