# 05 test运行中业务调时

Type: enhancement
Triage: ready-for-agent
Status: open
Blocked by: 04

## 工作与文件

调整 `AppointmentTimeConfig`，在localenv下增加仅test注册的进程内可调整Clock与ADMIN查时/设时/恢复接口，补充脚本封装及API说明。具体行为以[正式方案](../../../docs/environment-isolation-plan.md)第5节为准。

偏移不持久化；调时后继续走秒；不改变JWT/数据库/浏览器系统时间；业务事实不因回拨撤销。保留MockPay内存实现。

## 完成条件与验证

- E06、E07通过，包含无权限、非法时间、并发读取/设时、恢复、重启及其他profile无路由。
- 同一test进程可以创建预约、完成MockPay、推进时间并签到接诊；自动化用临时库验证，不写dev。
- Outbox退避和调度间隔边界有明确断言，不能以冻结Clock证明持续推进正常。

## Comments

- 尚未实施。更新与时钟相关的现行描述，保持既有预约和签到规则。
