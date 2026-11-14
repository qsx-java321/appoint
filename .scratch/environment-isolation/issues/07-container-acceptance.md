# 07 独占容器HTTP与浏览器回归

Type: enhancement
Triage: ready-for-agent
Status: open
Blocked by: 02、06

## 工作与文件

按[正式方案](../../../docs/environment-isolation-plan.md)第7节新增Java Testcontainers托管者、清晰的Python回归入口及worker/helper，改造两个旧Compose验收脚本为兼容转发，调整pom.xml和Playwright运行清单。

优先保留原业务流程、HTTP脱敏、SQL/消息/缓存断言；仅替换资源获取、连接、应用/进程生命周期。不把HTTP与浏览器用例合并成难以维护的巨型测试。

## 完成条件与验证

- E10–E12、E14通过；旧脚本不读compose.env、不访问共享管理端口，独立回归无任意常驻target。
- 新回归保留支付/取消退款/迟到支付/缓存/签到接诊场景，证据含对应断言和源码检查点。
- 准备失败、测试失败、应用崩溃、浏览器失败及中断的证据与清理都验证；失败尽力清理，异常如实报告。
- 容器、应用及Vite地址归属同一本轮清单；凭据/Token不进入公开报告或命令行。

## Comments

- 尚未实施。需要03、04的常驻行为由06的前置依赖间接保证；自动化仍独立，不调用常驻初始化/重置命令。
