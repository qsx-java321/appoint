# 02 自动化配置根与profile隔离

Type: enhancement
Triage: ready-for-agent
Status: claimed
Blocked by: 01

## 工作与文件

按[正式方案](../../../docs/environment-isolation-plan.md)第3节，为 `src/test/resources`建立独立回归配置根和明确入口；审计所有Spring测试注解、`pom.xml`及测试启动类。普通测试改用regression语义，dev/test/demo/prod装配专项保留原验证目标。

保持每个用例的事务、动态容器注入、properties、webEnvironment和Clock条件，不把本任务扩展成通用测试框架重构。Maven和IDE单类运行使用同一隔离边界。

## 完成条件与验证

- E08、E09关于自动化装配的部分通过；无配置时为H2，容器测试只取本轮连接。
- 构造主配置/外部环境变量干扰，证明在持久资源接触前拒绝或覆盖到回归隔离目标。
- 相关Spring测试及常规测试通过；验证dev/prod旧路由和MockPay装配保障未丢失，记录IDE等价入口的配置证据。

## Comments

- 尚未实施。此任务完成后才能引入主资源 `application-test.yml`。
