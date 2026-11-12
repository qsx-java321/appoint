# 真实 HTTP 与 MySQL 并发测试

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。六项当时任务已完成（1c570fe）；独立讲解页docs/business-flow-demo.html随后在9d20e30删除，当前没有业务前端或该说明页。此处UI与费用/就诊人范围只指2026-10-26，不是当前能力边界。 当前能力与进度见[项目状态](../../../docs/project-status.md)。

Type: task
Status: resolved

Blocked by: 02

## 验收

- 隔离 MySQL 8.4 容器内启动真实 Web 端口并覆盖患者主流程和错误契约。
- 覆盖同号源竞争及支付、取消、过期竞争，验证三表终态一致。
- 独立短超时配置验证定时扫描释放号源。

## Comments

- `PatientJourneyHttpMySqlIT` 使用随机真实端口、Java HTTP Client 和 MySQL 8.4，2 项通过。
- `AppointmentLifecycleMySqlIT` 覆盖同号源竞争及支付／取消／过期竞争，4 项通过。
- `./mvnw clean -Pmysql-it verify` 于 2026-10-26 最终完整通过：H2 25 项、MySQL 6 项，均为 0 失败、0 错误、0 跳过。
