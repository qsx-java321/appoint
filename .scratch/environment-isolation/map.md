# 环境改造任务地图

需求见[spec](spec.md)，文件级设计与验收编号见[正式方案](../../docs/environment-isolation-plan.md)。01–03已完成；本次只交付03配置校验，后续工作区实现尚未纳入此阶段。证据见[03阶段报告](evidence/stage03-policy/report.md)。

| 编号 | 任务 | Blocked by | 状态 |
| --- | --- | --- | --- |
| 01 | [需求访谈、源码核查与方案](issues/01-plan.md) | 无 | resolved |
| 02 | [自动化配置根与profile隔离](issues/02-regression-config.md) | 01 | resolved |
| 03 | [常驻环境配置与启动校验](issues/03-runtime-config.md) | 02 | resolved |
| 04 | [资源初始化、账号与重置生命周期](issues/04-provisioning-reset.md) | 03 | open |
| 05 | [test运行中业务调时](issues/05-test-clock.md) | 04 | open |
| 06 | [三前端入口与HTTP变量隔离](issues/06-frontend-http.md) | 04 | open |
| 07 | [独占容器HTTP与浏览器回归](issues/07-container-acceptance.md) | 02、06 | open |
| 08 | [完整验证、旧环境过渡与文档交付](issues/08-verification-delivery.md) | 05、06、07 | open |

02先消除现有自动化 `test`与未来常驻 `test`的配置碰撞，再引入真实连接；04完成demo重置后才能宣称支持demo启动。08才允许汇总改造交付结果，历史测试报告不计作本轮证据。
