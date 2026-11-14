# dev、test、demo三环境

Type: enhancement
Triage: ready-for-agent
Status: resolved

## 最新需求

用户于2026-11-14明确收敛范围：现有业务功能全部按正确处理，只关心如何设计并提供dev/test/demo三套环境。三者使用Compose中真实MySQL、Redis、RabbitMQ，资源和身份隔离；不执行任何测试或验收，不再处理测试类、HTTP验收和Testcontainers。

dev保留原appoint；test默认保留、显式重置并支持业务调时；demo每次后端启动恢复初始状态。共用迁移和基础目录逻辑，不复制dev业务数据。同环境重复实例与重置保护保留；资源有限，操作时一次启动一套，不增加跨环境互斥。

## 交付

环境配置、启动入口、初始化与重置生命周期、test业务时间及前端mode已纳入交付。使用说明见[环境文档](../../docs/environments.md)，设计见[现行方案](../../docs/environment-isolation-plan.md)，提交范围和限制见[交付记录](../archive/environment-isolation/summary.md)。

此前01–03提交及测试结果按原检查点保留。04–06按最新环境交付范围关闭；07自动化回归任务撤销；08只做配置与文档交付。不将历史测试数量列为本轮完成条件。

## Comments

- 259e368：原方案；748eca3：早期回归配置隔离；0ec8470：三环境配置与启动目标校验。
- 用户随后明确先停止自动化测试相关工作，再取消全部测试/验收要求；最新范围覆盖此前验收计划。
- 本轮不启动或重置环境、不修改现有业务数据、不运行测试、构建或验收。原有测试及回归工作区文件保留，不随本次环境实现提交。
