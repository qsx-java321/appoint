# 05 合并消息测试重复职责

Type: test/refactor
Status: ready-for-agent
Blocked by: 04

## 工作内容

基线独有断言迁入 Outbox 与消费契约，保留八份历史 JSON；公共失败处理器负责传输矩阵，两个 Listener 保留各自业务接线、路由、拒绝和 ACK 异常。

## 验收

聚焦消息测试及完整可靠性通过；旧 S01～S32 无遗漏。

## Comments

- 尚未验收，相关命令与结果写入 verification/commands.md。
