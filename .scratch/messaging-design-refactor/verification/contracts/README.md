# 基线契约样本

单一数据源：`src/test/resources/messaging/v1/` 的八份脱敏 JSON，依据改造前的三个生产者字段逐项整理；每类包含文本/数字 ID。它们不是从新 record 反向生成的期望值。

真实 Spring ObjectMapper 的 Long 文本和 null 行为由 MessagingBaselineContractTest 验证。最终生产路径写入的四类 JSON 在任务02独立核对。
