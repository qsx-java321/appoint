# 05 合并消息测试重复职责

Type: test/refactor
Status: resolved
Blocked by: 04

## 工作内容

基线独有断言迁入 Outbox 与消费契约，保留八份历史 JSON；公共失败处理器负责传输矩阵，两个 Listener 保留各自业务接线、路由、拒绝和 ACK 异常。

## 验收

聚焦消息测试及完整可靠性通过；旧 S01～S32 无遗漏。

## Comments

- 尚未验收，相关命令与结果写入 verification/commands.md。

- 聚焦85、完整可靠性16通过，零失败/错误/跳过。删除独立基线类和两个Listener各2项重复分支；独有断言迁移、新增I/O确认矩阵及S01～S32入口映射已登记。
