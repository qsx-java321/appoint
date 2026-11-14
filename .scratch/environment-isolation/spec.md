# dev、test、demo三环境需求

Type: enhancement
Triage: ready-for-agent
Status: resolved

让同一个项目具有dev、test、demo三套配置，使用Compose中的真实MySQL、Redis、RabbitMQ。dev用于日常体验，test用于独立操作业务，demo用于演示。环境之间的数据和身份独立，业务规则沿用现有实现。

设计要求：dev沿用appoint并保留数据；test默认保留，支持显式重置及运行中业务调时；demo每次后端启动恢复初始数据。共用迁移和基础目录初始化，不复制dev的业务记录。同环境保护在清理前生效；操作时按资源情况一次启动一套。

配置矩阵和生命周期以[设计方案](../../docs/environment-isolation-plan.md)为准，命令与账号见[使用说明](../../docs/environments.md)，实现记录见[交付总结](../archive/environment-isolation/summary.md)。
