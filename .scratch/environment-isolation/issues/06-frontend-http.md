# 06 三前端入口与HTTP变量隔离

Type: enhancement
Triage: ready-for-agent
Status: open
Blocked by: 04

## 工作与文件

调整 `frontend/package.json`、Vite与mode配置、Playwright及HTTP Client环境/脚本。按[正式方案](../../../docs/environment-isolation-plan.md)第6节建立三个常驻前端入口，迁出回归端口，隔离手工HTTP变量。

把显式test应用联调入口与自动化E2E分开；真实回归不得回退常驻地址。保持现有sessionStorage和相对API请求结构。

## 完成条件与验证

- E01前端映射、E13手工变量/浏览器origin、E14端口与target边界通过。
- 前端类型检查、相关单元、构建及默认模拟E2E通过；普通 `npm test`仍只运行Vitest。
- 常驻test联调入口明确告知数据写入test；dev/demo目标不能通过隐式代理覆盖误用。

## Comments

- 尚未实施；动态回归资源注入完整实现归07，06先提供配置与命名边界。
