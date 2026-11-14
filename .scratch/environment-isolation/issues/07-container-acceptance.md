# 07 独占容器HTTP与浏览器回归

Type: enhancement
Triage: wontfix
Status: resolved
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

- 已有工作区实现；自动化独立，不调用常驻初始化/重置命令，不能据旧状态认定尚未开始。

- 本轮核对：Java托管者、新Python入口/worker/helper、旧名薄包装和Maven入口已有实现。HTTP批次47b29d405b6c（96请求）和浏览器批次6c40e30bfc88通过；四个已有cleanup.json均记录零存活进程且failures为空。
- 两次浏览器失败分别为缺浏览器、保存提示定位器多匹配，随后修正通过；这些仅覆盖实际发生的失败，不等于准备失败/应用崩溃/Ctrl+C/清理失败全分支通过。
- 托管者后加“清理失败使总结果失败”、browser worker日志位置、入口参数帮助等改动还未形成对应最终批次；当前无故障注入开关或完整源码清单实现。HTTP96批次未传--http-report，缺逐请求JSON；成功浏览器批次的backend.log未保留，后续修复不补造历史日志。
- 状态修正为claimed，保留现有JSON/SQL/截图与历史结果，不新增自动化运行。

## 最新范围与关闭说明（2026-11-14）

用户撤销全部测试/验收及Testcontainers相关工作。本任务按wontfix关闭，不继续实现、提交或运行自动化回归；已有工作区文件保留。 以上最新说明取代正文中原测试/验收完成条件，旧记录仅供追溯。交付见[环境总结](../../archive/environment-isolation/summary.md)。
