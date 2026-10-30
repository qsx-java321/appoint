# 16 兼容清理、文档与端到端交付

Type: delivery
Triage: ready-for-agent
Status: resolved

## 背景和目标

在新流程和迁移完整验证后收拢兼容路径，使 README、API、业务流程、HTTP 示例、演示文档与真实行为一致，并完成最终端到端验收。

## 范围

- 更新 README、`docs/api.md`、`docs/business-flow.md`、演示页、HTTP 示例和验证报告。
- `simulate-success` 先保持 dev/test 废弃包装，最终确保生产 profile 不暴露；满足删除门槛后再删除。
- 删除只验证浅层转发的旧单元测试和已消除的架构豁免。
- 真实 HTTP 覆盖正常支付、失败重试、迟到退款、超时重约、已确认取消退款和 Legacy 查询。

## 非范围

- 不接真实支付、不新增前端/WebSocket/SSE，不扩大停诊善后范围。

## 依赖关系

Blocked by: 14、15。

## 需要修改的模块

- HTTP/controllers/security profiles、docs/http、端到端测试与构建报告。

## 业务不变量

- 兼容清理不得破坏 Legacy 查询或新流程；生产不暴露 Mock/旧模拟入口。
- 文档只宣称已由自动化或人工明确验证的结果。

## 可验证的验收条件

- spec 17.5 端到端场景通过；文档示例响应与测试一致。
- H2、MySQL、RabbitMQ、真实 HTTP 全套通过；模块依赖无 Legacy 豁免。

## 应运行的测试

- `./mvnw test`
- `./mvnw -Pmysql-it verify`
- RabbitMQ/双容器完整 verify 与真实 HTTP suite。

## 完成状态

- [x] 兼容入口清理
- [x] 文档/示例同步
- [x] 端到端验收
- [x] 最终报告

## Comments

- 等待 issue 14、15 resolved。
- 2026-10-30：删除浅层旧支付单元测试；`simulate-success` 保留为 dev/test 废弃包装，prod profile 断言 Mock Adapter、Mock Controller、旧 Controller 和对应路由均不存在。
- 2026-10-30：真实 MySQL HTTP 旅程改用 attempt 202、MockPay complete 202 和轮询确认；README、API、业务流程、交互演示、HTTP 示例与支付模块验证报告已同步。
- 2026-10-30：最终门禁全部通过：H2 83 项、MySQL Testcontainers 24 项、MySQL+RabbitMQ 双容器 9 项，无失败、错误或跳过。
