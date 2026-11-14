# 06 三前端入口与HTTP变量隔离

Type: enhancement
Triage: ready-for-agent
Status: resolved
Blocked by: 04

## 工作与文件

调整 `frontend/package.json`、Vite与mode配置、Playwright及HTTP Client环境/脚本。按[正式方案](../../../docs/environment-isolation-plan.md)第6节建立三个常驻前端入口，迁出回归端口，隔离手工HTTP变量。

把显式test应用联调入口与自动化E2E分开；真实回归不得回退常驻地址。保持现有sessionStorage和相对API请求结构。

## 完成条件与验证

- E01前端映射、E13手工变量/浏览器origin、E14端口与target边界通过。
- 前端类型检查、相关单元、构建及默认模拟E2E通过；普通 `npm test`仍只运行Vitest。
- 常驻test联调入口明确告知数据写入test；dev/demo目标不能通过隐式代理覆盖误用。

## Comments

- 已有工作区实现；动态资源托管归07，当前缺口见下方核对。

- 本轮核对：package/Vite/Playwright与http文件已有工作区实现；前端22项单元、10项夹具浏览器及类型/构建有通过记录。临时端口继承修复后夹具浏览器通过；之后还有源码改动，不能统一认作最终版本通过。
- 未完成：HTTP Client实际执行；appointment.http仍有文件级固定paidKey/cancelKey/rebookKey，部分手工字段仍需按环境梳理；调时专用.http示例尚不存在。文档先前写“变量全部隔离”过于宽泛，当前只确认捕获的Token/ID已加环境前缀。
- 状态修正为claimed，等待04收口；不借整理任务运行IDE或浏览器。

## 最新范围与关闭说明（2026-11-14）

本任务收敛为三个前端mode、端口与代理映射。HTTP Client、Playwright和测试脚本移出本次范围，其已有工作区改动保留。 以上最新说明取代正文中原测试/验收完成条件，旧记录仅供追溯。交付见[环境总结](../../archive/environment-isolation/summary.md)。
