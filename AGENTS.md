# Agent 协作说明

本项目是一个医院预约系统，默认使用中文沟通、编写文档和描述业务概念；技术框架、类名、接口名和必要的英文专用名词保持原文。

## 工作原则

以下原则强调审慎与简洁；对很小的任务按实际复杂度执行。

### 编码前明确问题

- 开始实现前说明目标、关键假设和可验证的完成条件。
- 存在会改变业务结果的不同解释时，讲清选择及取舍；无法从项目约定判断时，指出具体疑点并询问。
- 发现更简单的实现方式或需求与现有设计冲突时，及时说明。常规实现细节可依据现有代码自行判断。

### 优先简单实现

- 只实现本次需求需要的行为，选择足以解决问题的最小方案。
- 单次使用的逻辑保持直接；确有重复或明确扩展需求时再抽象、配置。
- 错误处理覆盖实际可能发生的边界，避免为不可能的情况增加分支。
- 完成后检查实现是否可以在保持行为的前提下进一步简化。

### 控制改动范围

- 每处改动都应能对应本次需求；沿用现有代码风格，仅整理本次改动影响到的部分。
- 清理本次改动造成的未使用导入、变量和函数。发现无关的旧问题时记录或告知，不顺手重构。

### 以验收结果推进

- 将需求转为可检查的结果，例如无效输入被拒绝、缺陷有复现用例、重构前后行为一致。
- 多步骤工作先列简短计划，并为每一步写出验证方式；完成后逐项核对。
- 对适合自动化验证的变更运行有意义的测试；报告已验证的结果和仍未验证的部分。

## Agent skills

### Issue tracker

项目使用本地 Markdown 文件管理 issue 和需求说明，文件放在 `.scratch/<feature-slug>/` 下。详见 `docs/agents/issue-tracker.md`。

### Triage labels

使用默认的 `triage` 标签：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human` 和 `wontfix`。详见 `docs/agents/triage-labels.md`。

### Domain docs

项目采用 single-context 布局，使用根目录下的 `GLOSSARY.md` 和 `docs/adr/` 保存领域术语与架构决策。详见 `docs/agents/domain.md`。

## 本地环境、启动与验证

- Compose 使用根目录 `compose.env`，Spring Boot 使用根目录 `.env`；首次配置分别从 `compose.env.example`、`.env.example` 复制。实际文件含凭据，已被 Git 忽略，不在日志或文档中输出其值。两份文件的 MySQL、RabbitMQ 连接凭据须一致；Shell 环境变量可能覆盖文件配置。
- 在项目根目录执行 `docker compose --env-file compose.env up -d --wait` 启动并等待 MySQL、Redis、RabbitMQ 健康；查看状态用 `docker compose --env-file compose.env ps`，停止服务用 `docker compose --env-file compose.env stop`。其他 Compose 命令也显式指定该文件。
- 在项目根目录执行 `./mvnw spring-boot:run`；应用从 `.env` 加载配置，默认本地 `dev` profile 启用 MockPay。IDE 的工作目录同样设为项目根目录。确认启动日志显示 MySQL、RabbitMQ 已连接及应用已启动；未认证访问 `/api/v1/hospitals` 返回 401 可检查 HTTP 入口。
- 运行 `./mvnw test` 执行常规测试；`./mvnw -Pmysql-it verify` 使用 Testcontainers 验证真实 MySQL；`./mvnw -Preliability-it verify` 验证消息可靠性。Docker 和镜像可用时，`python3 scripts/compose_acceptance.py` 使用隔离数据库及 RabbitMQ vhost 验证真实 HTTP、支付和退款；脚本使用 Maven 离线模式，需预先缓存依赖。成功后清理隔离资源，失败时保留资源标识和日志。
- 现有 MySQL、RabbitMQ 数据卷中的账号不会因修改 Compose 初始化变量而自动更新。保留卷及现有凭据；需要变更时同步处理服务内账号和应用配置。不要为了排查启动问题删除数据卷。生产切换和迁移见 `docs/runbooks/payment-module-cutover.md`；真实支付渠道尚未接入。
