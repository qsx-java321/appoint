# 08 完整验证、旧环境过渡与文档交付

Type: verification
Triage: ready-for-agent
Status: open
Blocked by: 05、06、07

## 工作与文件

逐项核对[正式方案](../../../docs/environment-isolation-plan.md)E01–E14，先在临时容器验证重置与失败分支，再按维护步骤切换现有dev消息/缓存目标；保留appoint数据库、现有凭据和卷。

更新README、AGENTS、前端README、环境总览、测试约定/清单/覆盖、API、模块设计、runbook、项目状态与文档检查工具。方案改为已实现时链接交付证据，旧历史证据保留原检查点。

## 完成条件与验证

- 适当的常规/MySQL/可靠性/Redis/前端及独立HTTP/浏览器回归完成，报告失败、跳过、未执行及限制。
- dev已有数据对照成立；旧默认vhost未完成资金或消息工作时中止切换，不用删数据绕过。
- 三环境同时运行与回归隔离有实证，文档静态检查通过。
- 保存实际命令、源码摘要、迁移/资源证据及清理结果；更新任务状态并按issue约定归档后关闭feature。

## Comments

- 尚未实施；本任务不能用此前共享Compose的历史通过结果代替新的独占容器回归。
