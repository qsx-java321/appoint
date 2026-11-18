# 医院预约系统

同仓库的Spring Boot后端与Vue 3前端，提供预约挂号、模拟支付、到院签到及医生接诊。医院、科室和医生每天正常工作；场次由首次预约按需生成。

注册时先验证邮箱，再设置密码。验证码邮件在[Mailpit](http://127.0.0.1:8025)查看；首次创建或更新共享服务使用 `docker compose --env-file compose.env up -d --wait`。

## 主要功能

- 邮箱验证码与票据注册、JWT登录、角色权限和本人就诊人管理。
- 医院、科室、医生目录及管理员统一预约设置，Redis缓存目录查询。
- 管理员查询医生所属医院、科室、绑定账号及初始密码配置位置，支持医生账号首次绑定。
- 今天至后天十二个场次、幂等下单、库存暂占、费用与就诊资料快照。
- MockPay支付、异步确认取号、取消、超时、原路退款及消息重试。
- 用户签到，医生四场看板、开始和完成接诊，双方查看同一就诊进度。

## 技术栈

Java 21、Spring Boot、Spring Security、MyBatis-Plus、MySQL 8.4、Flyway、Redis、RabbitMQ、JavaMailSender和Mailpit；前端使用Node 24、Vue 3、TypeScript、Vite、Pinia和Axios。测试使用JUnit 5、Mockito、MockMvc、Testcontainers和Vitest；MockMvc使用独立H2内存库，MySQL事务与并发测试使用临时容器。

## 快速启动

数据库以Flyway V1初始化完整结构，V2扩充预置医疗目录；空库自动依次执行，已使用现行V1的库追加V2。旧迁移链数据库的使用限制见[初始化基线](docs/environments.md#flyway初始化基线)。

在Linux或WSL2 Ubuntu中准备Git、JDK 21、Python 3、Node 24（至少24.12且低于25）、npm和Docker Compose v2。先启动Docker服务；Windows使用Docker Desktop时，确保已启用当前WSL发行版的集成。Java、Python、Node/npm均使用WSL内的版本，当前终端应能访问Docker。

首次运行通过 Maven Wrapper 下载 Maven，并下载 Docker 镜像及应用依赖。本地环境管理统一使用 `scripts/local_env.py`。Compose 启动 MySQL、Redis、RabbitMQ 和 Mailpit，脚本在宿主机启动 Spring Boot 和 Vite。

- [首次克隆并启动](#首次克隆并启动)
- [日常启动](#日常启动)
- [停止前后端和Docker容器](#停止前后端和docker容器)
- [切换环境](#切换环境)
- [状态和日志](#状态和日志)

### 首次克隆并启动

将仓库地址和目录名替换为实际值。完成初始化并安装前端依赖后，直接启动默认的 demo 环境。

```bash
git clone "<GitHub仓库地址>"
cd "<克隆后的项目目录>"
python3 scripts/local_env.py init
npm ci --prefix frontend
python3 scripts/local_env.py start
```

打开[项目页面](http://127.0.0.1:5173)。`init` 创建缺失的本地配置，启动 MySQL、Redis、RabbitMQ 和 Mailpit，并准备三套环境的数据库、账号和 vhost。后端启动时由 Flyway 初始化所选环境，再准备预置账号。首次体验使用 demo 普通用户，登录信息见[账号及重置](docs/environments.md#账号及重置)。

| 环境 | 启动命令 | 数据生命周期 |
| --- | --- | --- |
| demo（默认） | `python3 scripts/local_env.py start` | 每次后端启动恢复演示数据，旧登录失效 |
| dev | `python3 scripts/local_env.py start dev` | 保留开发数据 |
| test | `python3 scripts/local_env.py start test` | 默认保留数据，支持显式重置和业务调时 |

### 日常启动

确认前后端均已停止，启动共享依赖后选择需要的环境。三套环境共用前端 `5173` 和后端 `8080`，同一时间只运行一套。

```bash
docker compose --env-file compose.env up -d --wait
python3 scripts/local_env.py start
```

初始化完成后，首次使用 dev 也可直接执行 `python3 scripts/local_env.py start dev`。前端依赖变化时重新执行 `npm ci --prefix frontend`。

### 停止前后端和Docker容器

以 demo 为例，先停止前后端。要停止共享依赖时，再执行 Compose 的停止命令。

```bash
python3 scripts/local_env.py stop demo
docker compose --env-file compose.env stop
```

将 `demo` 换成当前环境名称。`stop` 管理脚本启动的前后端。通过 IDE、JAR 或 npm 单独启动的进程从原入口停止。Compose 停止时保留四个命名卷，包括 Mailpit 邮件库。见[Compose 服务与数据卷](docs/environments.md#compose-服务与数据卷)。

### 切换环境

先停当前整套应用，再启动目标环境，随后在同一网页地址刷新。共享依赖可保持运行。以下以从 demo 切到 dev 为例。

```bash
python3 scripts/local_env.py stop demo
python3 scripts/local_env.py start dev
```

### 状态和日志

查看三套环境的托管进程和共享容器：

```bash
python3 scripts/local_env.py status dev
python3 scripts/local_env.py status test
python3 scripts/local_env.py status demo
docker compose --env-file compose.env ps -a
```

`status`输出`{}`表示未检测到仍存活的脚本托管进程，不包括IDE手动启动的实例，也不是业务健康检查。执行 Compose 的停止命令后，容器状态应为 `Exited`。

按需查看对应环境最近100行日志：

```bash
# dev
tail -n 100 .local-env/dev-backend.log
tail -n 100 .local-env/dev-frontend.log

# test
tail -n 100 .local-env/test-backend.log
tail -n 100 .local-env/test-frontend.log

# demo
tail -n 100 .local-env/demo-backend.log
tail -n 100 .local-env/demo-frontend.log
```

日志在对应进程启动后生成。首次Maven下载与后端启动最多等待10分钟；等待期间可在另一终端跟踪后端日志：

```bash
# 根据当前环境将dev替换为test或demo
tail -f .local-env/dev-backend.log
```

在日志跟踪终端按`Ctrl+C`只结束跟踪；停止应用使用上述脚本命令。初始化配置细节、只启动Spring Boot、IDE运行、test重置及业务调时见[环境说明](docs/environments.md)。

## 自动化测试

在项目根目录执行后端测试：

```bash
# 单元/Mock与H2/MockMvc，无需Docker
./mvnw clean test

# 上述测试加MySQL集成测试，需要Docker
./mvnw -Pmysql-it clean verify
```

MySQL测试自行创建临时容器，不连接dev/test/demo常驻资源，无需启动项目Compose。前端测试命令见[前端说明](frontend/README.md)。

环境脚本的回归用例在 Linux 或 WSL 项目根目录运行。

```bash
python3 -B -m unittest discover -s src/test/python
```

最新的自动化测试和三环境启停、切换与 demo 业务验收见[测试报告](docs/testing/latest-report.md)和[JSON 报告](docs/testing/latest-report.json)。

历史验收按当时的代码和配置记录。详见[旧版 test 主流程报告](docs/testing/api-flow-report.md)、[2026-11-17 医生账号查询验收](docs/testing/2026-11-17-doctor-accounts-report.md)和[2026-11-17 邮箱注册验收](docs/testing/email-signup-report.md)。

## 文档入口

| 想了解什么 | 文档 |
| --- | --- |
| 邮箱验证码与注册票据历史实施记录 | [实施方案](docs/email-signup-plan.md) |
| 如何启动、切换和维护环境 | [环境说明](docs/environments.md) |
| 如何预约、付款、签到和接诊 | [业务流程](docs/business-flow.md) |
| 新增就诊人时如何填写、可复制的多位示例 | [就诊人信息填写示例](docs/patient-examples.md) |
| 接口、权限、请求响应和错误码 | [REST API](docs/api.md) |
| 模块、事务、消息、缓存与恢复 | [模块设计](docs/module-design.md) |
| 测试结果及验证范围 | [测试报告](docs/testing/latest-report.md) |
| 旧版 test 从注册到就诊完成的 HTTP 记录 | [历史 API 主流程报告](docs/testing/api-flow-report.md) |
| 前端开发与构建 | [前端说明](frontend/README.md) |
| 业务术语与设计原因 | [术语表](GLOSSARY.md)、[ADR](docs/adr/) |

首次体验新增就诊人时，可参考 [docs/patient-examples.md](docs/patient-examples.md)。文档提供8组可直接复制到页面的就诊人资料，包含姓名、身份证号码、联系电话和联系邮箱，并说明填写步骤、字段含义与常见错误处理；示例身份证号码已通过项目校验器检查，适用于本地演示和手动体验。

## 当前状态与边界

当前支持本地演示、开发和手动测试。最近一轮已验收默认 demo、三环境共用端口、互斥启停及演示业务流程。执行范围见[三环境人工验收](docs/testing/latest-report.md#三环境人工验收)。

系统使用MockPay，尚未接入真实资金渠道；渠道状态仅保存在当前后端进程内，跨重启查询与退款续接有限制。

未实现医院关门、医生停诊、管理员手工维护场次、自动叫号、爽约、病历处方、实时推送及业务短信邮件通知（已有注册验证码邮件）。退款结果队列已有拓扑但没有监听器，Outbox失败耗尽和死信可能需要人工恢复。
