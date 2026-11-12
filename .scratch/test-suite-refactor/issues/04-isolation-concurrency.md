# 04 完善隔离、故障恢复和有界等待

Type: test/refactor
Status: ready-for-agent
Blocked by: 03

## 工作内容

真实提交 H2 使用专属库；容器类关闭上下文；消息类独占资源统一清理并恢复监听、Broker、binding；并发屏障10秒、结果15秒，异步一般30秒，超时释放资源。

## 验收

受影响测试两种顺序通过，完整可靠性两轮通过；不改变真实锁顺序验证。

## Comments

- 尚未验收，相关命令与结果写入 verification/commands.md。
