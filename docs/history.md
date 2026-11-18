# 项目历史记录

本文索引各次实施与验收记录。下表中的记录形成于三套环境共用端口之前，端口、配置和请求示例按当时的代码保留。当前地址见[环境对应关系](environments.md#环境对应关系)，现行接口和验收见 [REST API](api.md) 与[最新测试报告](testing/latest-report.md)。

| 日期 | 历史记录 | 内容 |
| --- | --- | --- |
| 2026-11-17 | [邮箱注册实施记录](email-signup-plan.md) · [验收报告](testing/email-signup-report.md) | 验证码和一次性注册票据的实现过程与三环境验收 |
| 2026-11-17 | [医生账号查询验收](testing/2026-11-17-doctor-accounts-report.md) | 管理员查询、绑定状态及三环境权限验证 |
| 2026-11-16 | [预约到接诊的 API 主流程](testing/api-flow-report.md) | test 环境的 34 次实际 HTTP 请求、业务结果和环境恢复记录 |

设计决策及后续演进见[架构决策记录](adr/)。
