# 测试报告

更新日期为 2026-11-18。本轮验证默认 demo、端口复用和跨环境互斥。相关命令见[自动化测试](../../README.md#自动化测试)。

| 检查 | 通过 | 失败 / 错误 / 跳过 |
| --- | --- | --- |
| 后端单元、Mock 和 H2/MockMvc | 340（39 类） | 0 / 0 / 0 |
| Testcontainers MySQL 集成 | 12（4 类） | 0 / 0 / 0 |
| 前端 Vitest | 31（8 文件） | 0 / 0 / 0 |
| Python 环境脚本 | 3 | 0 / 0 / 0 |

`./mvnw -Pmysql-it clean verify`、前端类型检查、默认及 demo 模式构建、改动文件的 Prettier 检查和 Python 脚本测试均通过。分组和类级统计见 [JSON 报告](latest-report.json)。后端自动化使用 H2、临时 MySQL 和测试替身。

## 三环境人工验收

本轮在独立源码副本和全新 Compose 数据卷上进行，使用真实 MySQL、Redis、RabbitMQ 和 Mailpit。运行应用的端口为前端 5173 和后端 8080。

| 场景 | 实测结果 |
| --- | --- |
| 首次启动与默认配置 | 完成 `init` 和 `npm ci` 后，无参数的 `start` 进入 demo，前后端就绪标识匹配，网页可访问。单独直接运行 JAR 也默认进入 demo，监听 8080。 |
| 脚本与 JAR 并发 | 第二个脚本在第一个启动途中被拒绝。暂停已持锁、尚未监听的 dev JAR 后尝试启动 demo JAR，demo 被拒绝，演示库中的就诊人记录保留。 |
| 切换与进程归属 | 实测 demo → dev → test → demo，浏览器地址始终为 5173。仅剩 dev 前端时，脚本和直接启动 JAR 都拒绝启动另一环境。停止整套后可继续切换。Vite 拒绝覆盖固定地址、端口或关闭严格端口检查。 |
| 前端失败恢复 | 注入不匹配的 Vite 代理目标，前端启动失败后，本次后端退出、托管记录清理。恢复配置后同环境启动成功。 |
| 数据与维护命令 | dev/test 普通重启保留新增记录。demo 重启清除演示记录并使旧 JWT 失效。test 调时可查询、设置和恢复。整套停机后执行 `reset test`，结果仅影响 test。 |
| 页面环境与会话隔离 | Chrome 保留原 demo 标签页后切到 test，旧页面请求收到 409 / 1003，刷新后未加载 demo 登录或草稿。Vitest 验证会话和草稿按环境隔离。 |
| demo 业务链路 | 直接启动 JAR 选择 demo，手动启动不匹配的 test 前端被拒绝。通过 Vite 代理完成挂号、MockPay 付款、到院签到、开始接诊和完成接诊。Chrome 页面显示已就诊。 |

demo 的预约和接诊时段通过临时调整隔离验收 JVM 的时钟覆盖。业务操作通过真实 HTTP 执行，Chrome 核验登录、最后的就诊页面和环境切换。本轮覆盖本地脚本和 JAR 入口。历史注册、权限及业务异常见[邮箱注册报告](email-signup-report.md)、[医生账号报告](2026-11-17-doctor-accounts-report.md)和[历史 API 主流程报告](api-flow-report.md)。
