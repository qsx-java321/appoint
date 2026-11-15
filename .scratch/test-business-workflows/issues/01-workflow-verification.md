# 01 正式 test 环境业务测试

Type: verification
Triage: ready-for-agent
Status: resolved
Blocked by: none

## 完成条件

运行正式 test 环境，完成主流程与分支验证，保存脱敏证据并编写人工体验清单，恢复业务实时时钟，明确保留服务和数据。

## Comments

- 基线提交：`6c1ffe57b709cd0a8a7c7d994f4b1863b31a17dc`。
- 启动前没有运行中的前后端及 Compose 容器；使用 `docker compose --env-file compose.env up -d --wait` 和 Node 24 下的 `python3 scripts/local_env.py start test` 启动正式服务。
- 接口20组检查、253次请求（含3次并发）通过；真实页面完整复跑通过，143次响应、页面异常0。首次旧脚本冻结时钟断言失败已保留，修正后用新账号复验。
- SQL/Redis/RabbitMQ审计通过，原设置与实时时钟已恢复；服务及测试记录保留。详见[报告](../report.md)与[体验清单](../../../docs/test-business-workflows.md)。
