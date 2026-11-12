# 03 整理时钟与业务夹具

Type: test/refactor
Status: ready-for-agent
Blocked by: 02

## 工作内容

就诊测试共享 support 中的配置与 MutableAppointmentClock，解除测试类间依赖；每例重置时钟，只提取明确重复的准备操作，保留特殊输入和断言。

## 验收

受影响 HTTP/H2/MySQL、跨午夜及支付截止测试通过。

## Comments

- 尚未验收，相关命令与结果写入 verification/commands.md。
