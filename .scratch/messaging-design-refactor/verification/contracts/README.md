# 基线契约样本

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。消息设计9/9已交付，生产契约仍有效；原323等验收与源码摘要为963051f历史快照。基线测试类后来合并，当前测试入口和结果以测试整理交付为准。 当前能力与进度见[项目状态](../../../../docs/project-status.md)。 测试迁移见[当前消息场景](../../../test-suite-refactor/verification/message-scenarios.md)。

单一数据源：`src/test/resources/messaging/v1/` 的八份脱敏 JSON，依据改造前的三个生产者字段逐项整理；每类包含文本/数字 ID。它们不是从新 record 反向生成的期望值。

真实 Spring ObjectMapper 的 Long 文本和 null 行为由 MessagingBaselineContractTest 验证。最终生产路径写入的四类 JSON 在任务02独立核对。
