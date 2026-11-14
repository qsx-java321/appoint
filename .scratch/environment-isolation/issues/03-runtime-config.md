# 03 常驻环境配置与启动校验

Type: enhancement
Triage: ready-for-agent
Status: open
Blocked by: 02

## 工作与文件

拆分主配置与dev/test/demo文件、环境专属本地凭据示例及忽略规则；调整三个MockPay组件profile；新增最终有效目标校验。沿用[正式方案](../../../docs/environment-isolation-plan.md)中的固定映射，dev保留物理appoint，停止全局导入根.env。

生成或复用环境专属JWT/MockPay密钥，校验数据库、缓存、消息和端口成套匹配。配置迁移保留旧文件与现有凭据，不连接账号后自动改密码。

## 完成条件与验证

- E01的配置映射、E09与E13签名隔离通过；缺配置、混合profile、错误demo数据源在破坏性动作前失败。
- dev/test/demo注册MockPay，prod保持关闭；常规回归不受常驻配置影响。
- 修改时暂不宣称demo可正式使用：demo完整启动与重置由04完成。

## Comments

- 尚未实施；不为环境配置修改已有Flyway迁移。
