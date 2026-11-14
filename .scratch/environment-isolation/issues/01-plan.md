# 01 环境设计

Type: documentation
Triage: ready-for-agent
Status: resolved
Blocked by: 无

三套环境共用Compose真实依赖，分别管理配置、身份和数据生命周期。dev保留原appoint，test默认保留，demo启动恢复基线。

设计见[三环境方案](../../../docs/environment-isolation-plan.md)，操作见[使用说明](../../../docs/environments.md)。

## Comments

- 交付提交：`19bc004`。
