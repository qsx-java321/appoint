# 03 常驻环境配置与启动校验

Type: enhancement
Triage: ready-for-agent
Status: resolved
Blocked by: 02

## 工作与文件

拆分主配置与dev/test/demo文件、环境专属本地凭据示例及忽略规则；调整三个MockPay组件profile；新增最终有效目标校验。沿用[正式方案](../../../docs/environment-isolation-plan.md)中的固定映射，dev保留物理appoint，停止全局导入根.env。

生成或复用环境专属JWT/MockPay密钥，校验数据库、缓存、消息和端口成套匹配。配置迁移保留旧文件与现有凭据，不连接账号后自动改密码。

## 完成条件与验证

- E01的配置映射、E09与E13签名隔离通过；缺配置、混合profile、错误demo数据源在破坏性动作前失败。
- dev/test/demo注册MockPay，prod保持关闭；常规回归不受常驻配置影响。
- 修改时暂不宣称demo可正式使用：demo完整启动与重置由04完成。

## Comments

- 实现沿用既有Flyway版本，不修改历史迁移；后续04新增V11环境账号来源标记。

- 2026-11-14：独立profile、凭据来源、最终目标校验及MockPay装配已实现。`LocalEnvironmentPolicyTest`和独占容器生命周期负例通过；回归配置不加载常驻profile文件。

- 本轮核对：状态由resolved修正为claimed。主配置、三profile、环境示例和localenv Policy/Lifecycle已存在，但尚未提交；在早期通过后又增加Flyway/Hikari/Rabbit/Redis底层连接覆盖拒绝等校验，不能据早期结果认定当前文件全部通过。恢复后只针对这批新增守卫做必要核验，不重做已确认需求或默认重跑全套。

- 2026-11-14恢复实施：修复索引列表与Shell绑定覆盖漏检，校验移入LocalEnvironmentPolicy；仅基线加03文件的独立快照9项聚焦测试全部通过。已完成03，交付内容与限制见[阶段报告](../evidence/stage03-policy/report.md)。04–08继续保留未完成状态。
