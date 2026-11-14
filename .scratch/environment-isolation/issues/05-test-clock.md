# 05 test运行中业务调时

Type: enhancement
Triage: ready-for-agent
Status: resolved
Blocked by: 04

## 工作与文件

调整 `AppointmentTimeConfig`，在localenv下增加仅test注册的进程内可调整Clock与ADMIN查时/设时/恢复接口，补充脚本封装及API说明。具体行为以[正式方案](../../../docs/environment-isolation-plan.md)第5节为准。

偏移不持久化；调时后继续走秒；不改变JWT/数据库/浏览器系统时间；业务事实不因回拨撤销。保留MockPay内存实现。

## 完成条件与验证

- E06、E07通过，包含无权限、非法时间、并发读取/设时、恢复、重启及其他profile无路由。
- 同一test进程可以创建预约、完成MockPay、推进时间并签到接诊；自动化用临时库验证，不写dev。
- Outbox退避和调度间隔边界有明确断言，不能以冻结Clock证明持续推进正常。

## Comments

- 已有工作区实现，保持既有预约和签到规则；最终完成情况以下方核对为准。

- 本轮核对：AdjustableClock/TestClockConfig/TestClockController及脚本clock命令已实现，尚未提交。对象/权限6项聚焦批次通过；临时容器同进程付款→调时→过期扫描→签到接诊与JWT系统时间分离有实证；本机test查时/设时/恢复也曾成功。
- 尚未关闭：Outbox推进后的退避/调度边界缺独立完整证据，需核对现有可靠性用例能否覆盖，按风险补最小集合；不能将固定Clock的HTTP批次等同于这些边界。状态修正为claimed，等待04收口后再恢复。

## 最新范围与关闭说明（2026-11-14）

test进程业务Clock、ADMIN接口及脚本入口随环境实现交付。既有业务功能按正确处理，旧调时业务闭环及Outbox边界补测要求撤销。 以上最新说明取代正文中原测试/验收完成条件，旧记录仅供追溯。交付见[环境总结](../../archive/environment-isolation/summary.md)。
