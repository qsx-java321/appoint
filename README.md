# 医院预约系统

同仓库的Spring Boot后端与Vue 3前端，提供预约挂号、模拟支付、到院签到及医生接诊。医院、科室和医生每天正常工作；场次由首次预约按需生成。

## 主要功能

- 用户注册、JWT登录、角色权限和本人就诊人管理。
- 医院、科室、医生目录及管理员统一预约设置，Redis缓存目录查询。
- 今天至后天十二个场次、幂等下单、库存暂占、费用与就诊资料快照。
- MockPay支付、异步确认取号、取消、超时、原路退款及消息重试。
- 用户签到，医生四场看板、开始和完成接诊，双方查看同一就诊进度。

## 技术栈

Java 21、Spring Boot、Spring Security、MyBatis-Plus、MySQL 8.4、Flyway、Redis、RabbitMQ；前端使用Node 24、Vue 3、TypeScript、Vite、Pinia和Axios。测试使用JUnit 5、Mockito、MockMvc、Testcontainers和Vitest；MockMvc使用独立H2内存库，MySQL事务与并发测试使用临时容器。

## 快速启动

数据库以Flyway V1初始化完整结构，V2扩充预置医疗目录；空库自动依次执行，已使用现行V1的库追加V2。旧迁移链数据库的使用限制见[初始化基线](docs/environments.md#flyway初始化基线)。

在Linux或WSL2 Ubuntu中准备Git、JDK 21、Python 3、Node 24（至少24.12且低于25）、npm和Docker Compose v2。先启动Docker服务；Windows使用Docker Desktop时，确保已启用当前WSL发行版的集成。Java、Python、Node/npm均使用WSL内的版本，当前终端应能访问Docker。

项目使用Maven Wrapper，无需另装Maven；首次运行需要联网下载Docker镜像、Maven和应用依赖。本文使用Python脚本同时管理前后端，无需在IDE中重复运行main类、手动修改`spring.profiles.default`或另开`npm run dev`。

- [首次克隆并启动](#首次克隆并启动)
- [日常启动](#日常启动)
- [停止前后端和Docker容器](#停止前后端和docker容器)
- [切换环境](#切换环境)
- [状态和日志](#状态和日志)

### 首次克隆并启动

将下面的仓库地址和目录名替换为实际值，逐步执行；每一步成功后再继续。以下以首次启动dev为例；首次就使用test或demo时，只执行到`npm ci`后返回项目根目录，再按下文选择目标环境的启动命令：

```bash
git clone <GitHub仓库地址>
cd <克隆后的项目目录>

# 创建缺失配置，启动共享容器，准备三套环境的基础资源
python3 scripts/local_env.py init

# 安装前端依赖；完成后返回项目根目录
cd frontend
npm ci
cd ..

# 首次使用dev时，确保dev后端未运行，再执行预检
python3 scripts/local_env.py cutover-dev

# 同时启动dev后端和前端
python3 scripts/local_env.py start dev
```

启动成功后打开[dev页面](http://127.0.0.1:5173)。各命令的结果如下：

| 步骤 | 结果和影响 |
| --- | --- |
| `git clone` | 下载源码、配置模板和Git历史，不启动服务 |
| `init` | 从四份`.example`创建缺失的`compose.env`、`.env.dev`、`.env.test`和`.env.demo`，已有配置保留；内部执行`docker compose --env-file compose.env up -d --wait`，启动MySQL、Redis、RabbitMQ，准备三套数据库及账号、RabbitMQ用户、vhost和权限，不创建业务表 |
| `npm ci` | 按锁文件安装前端依赖到`frontend/node_modules`，不启动前端 |
| `cutover-dev` | 检查8080端口，以及非空dev库的必要业务表；空库允许通过，通过后写入本机预检标记，不填充业务数据 |
| `start dev` | 用Maven Wrapper启动dev后端，等待后端端口可连接后启动对应Vue前端，保存日志及进程记录 |

后端首次启动通过Flyway创建当前环境的22张应用表和迁移记录表，预置2家医院、每院3个科室、每科室3名医生；随后创建1个管理员和18个医生账号并完成医生绑定，demo另有普通演示用户。账号命名、密码位置和样例对应关系见[账号及重置](docs/environments.md#账号及重置)。后端还会声明当前RabbitMQ vhost中的交换机、队列和绑定，Redis目录缓存由后续查询按需生成。只启动dev不会初始化test/demo的业务表，也不会运行它们的前后端。

首次就要使用test或demo时，同样先完成`init`和`npm ci`，随后执行下列启动命令之一即可；它们不需要`cutover-dev`：

```bash
# 选择test时执行这一条
python3 scripts/local_env.py start test
```

```bash
# 选择demo时执行这一条；每次后端启动都会恢复demo初始状态
python3 scripts/local_env.py start demo
```

以下所有命令均在项目根目录执行，一次按需运行一套前后端。初始账号、密码配置及重置规则见[账号及重置](docs/environments.md#账号及重置)。

| 环境 | 前端 | 后端 | 数据生命周期 |
| --- | --- | --- | --- |
| dev | [5173](http://127.0.0.1:5173) | 8080 | 保留appoint数据，预置管理员和医生账号，普通用户从页面注册 |
| test | [5174](http://127.0.0.1:5174) | 8081 | 默认保留，可显式重置、调整业务时间；不会因启动而运行自动化测试 |
| demo | [5175](http://127.0.0.1:5175) | 8082 | 每次后端启动恢复自身数据库、目录缓存和应用消息队列的初始状态，旧Token失效 |

### 日常启动

每次先确保Docker服务已启动，并确认上一套前后端已停止。选择下列一组命令执行，不要把三组连续执行。容器已经运行时，也可以执行`up -d --wait`。

启动dev：

```bash
# 启动共享依赖并等待就绪
docker compose --env-file compose.env up -d --wait
# 启动dev前后端；浏览器打开http://127.0.0.1:5173
python3 scripts/local_env.py start dev
```

启动test：

```bash
docker compose --env-file compose.env up -d --wait
# 浏览器打开http://127.0.0.1:5174
python3 scripts/local_env.py start test
```

启动demo：

```bash
docker compose --env-file compose.env up -d --wait
# 浏览器打开http://127.0.0.1:5175；本次后端启动将重置demo数据
python3 scripts/local_env.py start demo
```

配置、资源、前端依赖和dev预检标记仍在时，无需重复`init`、`npm ci`和`cutover-dev`。前端依赖变更后重新在`frontend`执行`npm ci`；dev预检标记缺失时，在共享容器就绪、dev后端停止的情况下重新执行`cutover-dev`。`start`不会自动安装前端依赖或启动共享容器。

### 停止前后端和Docker容器

按当前运行的环境选择一组，先停止前后端，再停止共享依赖：

停止dev及共享容器：

```bash
python3 scripts/local_env.py stop dev
docker compose --env-file compose.env stop
```

停止test及共享容器：

```bash
python3 scripts/local_env.py stop test
docker compose --env-file compose.env stop
```

停止demo及共享容器：

```bash
python3 scripts/local_env.py stop demo
docker compose --env-file compose.env stop
```

脚本的`stop`只停止它托管的对应环境前后端，不停止容器；Compose的`stop`停止三个共享容器，保留容器和数据卷，不退出Docker Desktop或停止Docker服务本身。dev/test下次启动继续保留数据库业务记录；demo本次停止不会清空数据，但下次后端启动会重置。MockPay渠道状态仅存后端内存，三套环境都不会在重启后恢复该状态。

如果只是临时停止前后端或准备切换环境，仅执行对应的`python3 scripts/local_env.py stop ...`，保持共享容器运行即可。IDE手动启动的进程需要在IDE中停止，脚本不会接管这些进程。

### 切换环境

共享容器正在运行时，无需停止或重新初始化容器；先停止当前环境，再启动目标环境。以下每个代码块是一个独立场景，按需选择。

dev切到test：

```bash
python3 scripts/local_env.py stop dev
python3 scripts/local_env.py start test
```

dev切到demo：

```bash
python3 scripts/local_env.py stop dev
python3 scripts/local_env.py start demo
```

test切到dev：

```bash
python3 scripts/local_env.py stop test
python3 scripts/local_env.py start dev
```

test切到demo：

```bash
python3 scripts/local_env.py stop test
python3 scripts/local_env.py start demo
```

demo切到dev：

```bash
python3 scripts/local_env.py stop demo
python3 scripts/local_env.py start dev
```

demo切到test：

```bash
python3 scripts/local_env.py stop demo
python3 scripts/local_env.py start test
```

目标为dev时必须已经完成首次预检；目标为demo时每次启动都会重置demo自身数据。切换后使用目标环境的前端地址，原页面不会自动切换环境。

### 状态和日志

查看三套环境的托管进程和共享容器：

```bash
python3 scripts/local_env.py status dev
python3 scripts/local_env.py status test
python3 scripts/local_env.py status demo
docker compose --env-file compose.env ps -a
```

`status`输出`{}`表示未检测到仍存活的脚本托管进程，不包括IDE手动启动的实例，也不是业务健康检查。正常停止后，容器状态应为`Exited`等已停止状态。

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

测试结果见[测试报告](docs/testing/latest-report.md)与[JSON报告](docs/testing/latest-report.json)。重新测试后直接覆盖对应结果，保留实际测试日期和未验证范围；旧结果通过Git历史追溯。

常驻test环境的真实HTTP主流程实测见[API主流程测试报告](docs/testing/api-flow-report.md)，记录注册到就诊完成的实际请求和响应。该实测与上述独立自动化测试分别统计。

## 文档入口

| 想了解什么 | 文档 |
| --- | --- |
| 如何启动、切换和维护环境 | [环境说明](docs/environments.md) |
| 如何预约、付款、签到和接诊 | [业务流程](docs/business-flow.md) |
| 新增就诊人时如何填写、可复制的多位示例 | [就诊人信息填写示例](docs/patient-examples.md) |
| 接口、权限、请求响应和错误码 | [REST API](docs/api.md) |
| 模块、事务、消息、缓存与恢复 | [模块设计](docs/module-design.md) |
| 测试结果及验证范围 | [测试报告](docs/testing/latest-report.md) |
| 用户注册到就诊完成的API实测 | [API主流程测试报告](docs/testing/api-flow-report.md) |
| 前端开发与构建 | [前端说明](frontend/README.md) |
| 业务术语与设计原因 | [术语表](GLOSSARY.md)、[ADR](docs/adr/) |

首次体验新增就诊人时，可参考 [docs/patient-examples.md](docs/patient-examples.md)。文档提供8组可直接复制到页面的就诊人资料，包含姓名、身份证号码、联系电话和联系邮箱，并说明填写步骤、字段含义与常见错误处理；示例身份证号码已通过项目校验器检查，适用于本地演示和手动体验。

文档以当前实现为准，历史开发过程与验收证据保存在Git提交`7b8a370`。文档调整后运行`python3 scripts/check_documentation.py`检查链接与API契约。

## 当前状态与边界

核心业务开发、三环境改造和约定范围的业务验收已完成。自动化测试包含单元/Mock、H2/MockMvc和按需执行的MySQL集成测试；已执行的测试数量、日期及适用范围见[最新测试报告](docs/testing/latest-report.md)。三环境预置目录、账号登录及权限的历史核验范围另见[三环境人工验收](docs/testing/latest-report.md#三环境人工验收)。

系统使用MockPay，尚未接入真实资金渠道；渠道状态仅保存在当前后端进程内，跨重启查询与退款续接有限制。

未实现医院关门、医生停诊、管理员手工维护场次、自动叫号、爽约、病历处方、实时推送和短信邮件通知。退款结果队列已有拓扑但没有监听器，Outbox失败耗尽和死信可能需要人工恢复。
