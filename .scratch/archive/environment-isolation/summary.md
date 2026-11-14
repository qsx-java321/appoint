# 三环境配置交付

日期：2026-11-14。需求与任务：[原任务入口](../../environment-isolation/spec.md)。使用方式：[环境说明](../../../docs/environments.md)。本总结随环境实现本地提交，可用`git log -1 -- .scratch/archive/environment-isolation/summary.md`追溯；不推送。

## 目标与范围调整

用户明确现有业务功能全部正确，当前只需提供dev/test/demo三环境，使用Compose真实MySQL、Redis、RabbitMQ；取消所有测试和验收，不再关心测试类、HTTP回归或Testcontainers。此前补测待办不再阻塞环境交付。

## 实现

- 原03配置提交0ec8470保留；三份profile、私密env来源、独立身份与目标校验已经存在。
- scripts/local_env.py统一准备资源、启动/停止/状态、dev过渡、test显式重置及调时命令。
- LocalEnvironmentConfig/Lifecycle/Files统一Flyway、账号补充、demo启动重建、test重置密钥轮换及同环境锁；V11只增加账号来源标记。
- AdjustableClock与TestClockConfig/Controller仅为test提供进程内调时；保持业务时间和JWT系统时间分离。
- 前端提供dev/test/demo三mode及固定端口/代理；后端对应8080/8081/8082，前端对应5173/5174/5175。
- dev保留appoint和原业务数据；test默认保留；demo每次后端启动恢复基线；三环境只共用初始化逻辑，不复制dev业务数据。

## 操作与限制

本轮仅整理和交付已有环境实现与文档；未运行测试、HTTP请求、浏览器回归、构建、Testcontainers或服务启动，没有执行init/reset/cutover，没有修改私密凭据、业务数据或数据卷。按用户指示不补充运行结果，不将旧测试结果当作本轮验证。

原始spec/issue和既有JSON、日志、截图保留，历史正文以最新范围说明为准。原自动化回归、测试类、HTTP模板、Playwright及pom改动留在工作区，未纳入本次提交。前端package/Vite仅暂存三环境部分，其原自动化相关改动继续保留。

MockPay继续使用内存实现；应用重启后旧渠道支付查询和退款续接有限制。demo为本地演示，本次不建设生产部署。
