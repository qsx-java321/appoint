# test 环境业务流程实测

Type: verification
Triage: ready-for-agent
Status: resolved

## 目标

在正式 test profile、真实 Compose 依赖上启动 Spring Boot，验证注册到完成就诊的完整流程及关键分支，交付可供人工体验的业务流程清单。

## 约束与验收

- 保留已有 test 数据，不重置、不更改 dev/demo 数据；测试账号与目录带独立标记。
- 使用正式 HTTP/API 与真实浏览器验证业务，调时仅用 test 管理接口，结束恢复实时。
- 覆盖身份、就诊人、目录与设置、预约库存/幂等、支付重试/退款/超时、签到接诊及权限边界。
- 保存脱敏证据、记录 ID、命令与验证限制；不把历史测试当成本次结果。
- 不修改业务实现；发现的问题作为结果报告。
