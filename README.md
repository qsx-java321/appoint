# 医院预约系统

医院预约系统包含 Spring Boot 后端和 Vue 3 前端，支持预约挂号、MockPay 模拟支付、到院签到和医生接诊。管理员可以维护医院、科室、医生档案和统一预约设置，并查询或首次绑定医生账号。

## 快速启动

在 Linux 或 WSL2 中准备 Git、JDK 21、Python 3、Node.js 24（`>=24.12.0 <25`）、npm 和 Docker。确认当前终端可以访问 Docker，且 `docker compose up` 支持 `--wait`。Windows 使用 Docker Desktop 时，需启用当前 WSL 发行版的集成。首次运行需要联网下载镜像和依赖。

### 首次克隆并启动

将示例中的仓库地址和目录名替换为实际值，然后运行以下命令。已有仓库时，从项目根目录执行最后三行。

```bash
git clone "<GitHub 仓库地址>"
cd "<克隆后的项目目录>"
python3 scripts/local_env.py init
npm ci --prefix frontend
python3 scripts/local_env.py start
```

打开[项目页面](http://127.0.0.1:5173)，使用 `demo-user@example.local` 登录，初始密码在仓库根目录的 `.env.demo` 中，配置项为 `LOCAL_SEED_PASSWORD`。登录后可[添加示例就诊人](docs/patient-examples.md)，再按[业务流程](docs/business-flow.md)体验挂号和支付。自行注册时，在 [Mailpit](http://127.0.0.1:8025) 查看邮箱验证码。

无参数的 `start` 启动 demo。**每次启动 demo 后端都会恢复演示数据，并使旧登录失效。** 想保留体验数据时，可使用 dev 或 test。前端和后端分别共用 `5173` 和 `8080`，切换前先停止当前应用。

停止当前 demo 的前后端可执行 `python3 scripts/local_env.py stop demo`。共享容器可继续运行。日常启停、切换和日志命令集中在[环境指南](docs/environments.md#启动和切换)。已有数据库来自旧迁移链时，先确认[数据库兼容范围](docs/environments.md#flyway初始化基线)。

## 文档入口

| 要做什么 | 阅读入口 |
| --- | --- |
| 启停、切换、查看日志、重置 test 或调时 | [本地环境指南](docs/environments.md) |
| 挂号、付款、签到和接诊 | [业务流程](docs/business-flow.md) |
| 填写就诊人资料 | [就诊人填写示例](docs/patient-examples.md) |
| 联调接口、查询字段和错误码 | [REST API](docs/api.md) |
| 开发、测试或构建前端 | [前端说明](frontend/README.md) |
| 了解模块职责、事务、消息与恢复 | [模块设计](docs/module-design.md) |
| 查询业务术语与设计原因 | [术语表](GLOSSARY.md)、[架构决策记录](docs/adr/) |
| 查询测试结果及覆盖范围 | [最新测试报告](docs/testing/latest-report.md)、[历史记录](docs/history.md) |

## 自动化测试

在项目根目录执行后端测试。

```bash
# 单元测试、Mock 和 H2/MockMvc，无需 Docker
./mvnw clean test

# 加跑 Testcontainers MySQL 集成测试，需要 Docker
./mvnw -Pmysql-it clean verify
```

MySQL 集成测试自行创建临时容器，无需启动项目 Compose。前端测试命令见[前端说明](frontend/README.md#开发与构建)。

在 Linux 或 WSL 的项目根目录运行环境脚本回归测试。

```bash
python3 -B -m unittest discover -s src/test/python
```

执行日期、验证范围及各组结果见[最新测试报告](docs/testing/latest-report.md)。逐类结果见 [JSON 报告](docs/testing/latest-report.json)。

## 技术栈与使用边界

| 部分 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot、Spring Security、MyBatis-Plus、Flyway |
| 前端 | Node.js 24、Vue 3、TypeScript、Vite、Pinia、Axios |
| 本地依赖 | MySQL 8.4、Redis、RabbitMQ、Mailpit（通过 JavaMailSender 发送注册邮件） |
| 测试 | JUnit 5、Mockito、MockMvc、H2、Testcontainers、Vitest |

项目面向本地演示、开发和测试。支付使用 MockPay，渠道状态只保存在后端进程内，重启后查询旧渠道订单和续接退款受限。当前邮件用于注册验证码。退款结果队列的消费和人工恢复边界见[模块设计](docs/module-design.md#维护与恢复)。

业务范围见[体验路线与边界](docs/business-flow.md#体验路线与边界)。项目按医院、科室和医生每天正常工作设计，管理员通过统一预约设置管理之后首次生成的场次。
