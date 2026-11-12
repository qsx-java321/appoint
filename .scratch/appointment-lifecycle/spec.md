# 预约凭证、取消预约与超时释放号源

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。本阶段已交付，单号源BOOKED/AVAILABLE、同步模拟支付及已确认不可取消均属旧模型；现行库存、异步支付和严格24小时取消规则以现行文档为准。 当前能力与进度见[项目状态](../../docs/project-status.md)。

Status: resolved

## 目标

补齐预约的医院地址和就诊时段快照；待支付预约支持用户取消和 15 分钟超时关闭；关闭时同步关闭支付单并释放号源。支付截止时间不晚于就诊开始时间。

## 接口

- 扩展 `POST /api/v1/appointments`、预约详情和“我的预约”的截止时间与凭证字段。
- 新增 `POST /api/v1/appointments/{id}/cancel`。
- 模拟支付在截止后返回 `PAYMENT_EXPIRED / 4004`。
- 已开诊号源不再展示，提交时返回 `SCHEDULE_STARTED / 4005`。

## 状态约束

- 支付成功：`CONFIRMED + PAID + BOOKED`。
- 主动取消：`CANCELLED + CLOSED + AVAILABLE`。
- 超时关闭：`EXPIRED + CLOSED + AVAILABLE`。
- 已取消和已过期的重复取消幂等；已确认预约不能取消。
- 所有写路径按预约单、支付单、号源顺序加锁。

## 验收

H2 回归测试与 MySQL 8.4 迁移、并发测试均通过；旧预约快照与截止时间可由 V4、V5 迁移回填。
