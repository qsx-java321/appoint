# 医院预约系统

医院预约系统提供预约挂号、模拟支付、到院签到和医生接诊。管理员维护医院、科室、医生档案、挂号费和每场容量。项目使用 Spring Boot、Vue 3、MySQL 和 Redis，适用于本地演示和开发。

## 快速启动

在 Linux 或 WSL2 中准备 Git、JDK 21、Python 3、Node.js `>=24.12.0 <25`（含 npm）和支持 `docker compose up --wait` 的 Docker Compose。Windows 使用 Docker Desktop 时，在 Docker Desktop 启用当前 WSL 发行版的集成。首次启动需要下载依赖和镜像。

克隆仓库后，在项目根目录执行：

```bash
python3 scripts/local_env.py init
npm ci --prefix frontend
python3 scripts/local_env.py start demo
```

1. 打开[系统首页](http://127.0.0.1:5173)，使用 `demo-user@example.local` 登录。初始密码取自根目录 `.env.demo` 的 `LOCAL_SEED_PASSWORD`。
2. 在「就诊人」添加一条[演示资料](docs/patient-examples.md)。回到预约页，选择医院、科室、医生和可预约场次。
3. 提交预约并点击「模拟支付」。预约详情会显示确认状态和就诊序号。

执行 `python3 scripts/local_env.py stop demo` 停止前后端。每次启动 demo 后端都会重建 demo 数据并使旧登录失效。

## 文档

### 了解项目

先看[业务流程](docs/business-flow.md)了解下单、支付、过期、签到和接诊，再看[模块设计](docs/module-design.md)定位代码、事务和数据关系。业务术语见[术语表](GLOSSARY.md)。

### 按任务查阅

| 想做什么 | 阅读入口 |
| --- | --- |
| 填写演示就诊人 | [就诊人示例](docs/patient-examples.md) |
| 联调 HTTP 接口 | [REST API](docs/api.md) |
| 切换或重置环境、调时或查日志 | [本地环境指南](docs/environments.md) |
| 单独开发或构建前端 | [前端说明](frontend/README.md) |

## 自动化测试

从项目根目录运行：

```bash
./mvnw clean test                         # 后端单元、Mock 和 H2/MockMvc
./mvnw -Pmysql-it clean verify             # 再运行 Testcontainers MySQL 和 Redis 集成测试
npm test --prefix frontend                # 前端 Vitest
python3 -B -m unittest discover -s src/test/python  # 环境脚本回归
```

MySQL 和 Redis 集成测试需要 Docker，自行启动临时容器。各套自动化测试使用独立资源。
