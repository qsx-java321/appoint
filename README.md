# 医院预约系统

同仓库的Spring Boot后端与Vue 3前端，提供预约挂号、模拟支付、到院签到及医生接诊。医院、科室和医生每天正常工作；场次由首次预约按需生成。

## 主要功能

- 用户注册、JWT登录、角色权限和本人就诊人管理。
- 医院、科室、医生目录及管理员统一预约设置，Redis缓存目录查询。
- 今天至后天十二个场次、幂等下单、库存暂占、费用与就诊资料快照。
- MockPay支付、异步确认取号、取消、超时、原路退款及消息重试。
- 用户签到，医生四场看板、开始和完成接诊，双方查看同一就诊进度。

## 技术栈

Java 21、Spring Boot、Spring Security、MyBatis-Plus、MySQL 8.4、Flyway、Redis、RabbitMQ；前端使用Node 24、Vue 3、TypeScript、Vite、Pinia和Axios。测试仅保留单元/Mock与MockMvc，使用JUnit 5、Mockito和Vitest；MockMvc使用H2内存库。

## 快速启动

数据库已整理为单一Flyway V1，适用于空库重新部署；旧迁移链数据库的使用限制见[初始化基线](docs/environments.md#flyway初始化基线)。

在Linux或WSL2中准备Git、JDK 21、Python 3、Node 24（至少24.12）和Docker Compose v2，确保当前终端能访问Docker。首次安装需要联网下载镜像、Maven和npm依赖。在克隆后的项目根目录执行：

```bash
python3 scripts/local_env.py init
cd frontend
npm ci
cd ..
python3 scripts/local_env.py cutover-dev
python3 scripts/local_env.py start dev
```

`init`从四份`.example`模板创建缺失配置并初始化共享资源，已有配置保留原值；`cutover-dev`检查后端端口是否占用，以及非空dev库的必要业务表是否齐全。后端首次启动通过Flyway建表，Maven允许联网下载依赖。共享服务运行时，日常启动只需`start`。只启动Spring Boot或使用IDE的步骤见[环境说明](docs/environments.md#启动和切换)。

| 环境 | 前端 | 后端 | 数据生命周期 |
| --- | --- | --- | --- |
| dev | http://127.0.0.1:5173 | 8080 | 保留原appoint数据 |
| test | http://127.0.0.1:5174 | 8081 | 默认保留，可显式重置、调整业务时间 |
| demo | http://127.0.0.1:5175 | 8082 | 每次后端启动恢复初始状态 |

操作时一次启动一套，切换前执行`python3 scripts/local_env.py stop dev`，再启动test或demo。初始化、账号、IDE和重置方式见[环境说明](docs/environments.md)。

## 文档入口

| 想了解什么 | 文档 |
| --- | --- |
| 如何启动、切换和维护环境 | [环境说明](docs/environments.md) |
| 如何预约、付款、签到和接诊 | [业务流程](docs/business-flow.md) |
| 接口、权限、请求响应和错误码 | [REST API](docs/api.md) |
| 模块、事务、消息、缓存与恢复 | [模块设计](docs/module-design.md) |
| 如何运行测试及已完成的验收范围 | [测试说明](docs/testing.md) |
| 前端开发与构建 | [前端说明](frontend/README.md) |
| 业务术语与设计原因 | [术语表](GLOSSARY.md)、[ADR](docs/adr/) |

文档以当前实现为准，历史开发过程与验收证据保存在Git提交`7b8a370`。文档调整后运行`python3 scripts/check_documentation.py`检查链接与API契约。

## 当前状态与边界

核心业务开发、三环境改造和约定范围的业务验收已完成。自动化测试已精简为单元/Mock与MockMvc，并完成验证，结果见[测试说明](docs/testing.md#最近验证)。

系统使用MockPay，尚未接入真实资金渠道；渠道状态仅保存在当前后端进程内，跨重启查询与退款续接有限制。

未实现医院关门、医生停诊、管理员手工维护场次、自动叫号、爽约、病历处方、实时推送和短信邮件通知。退款结果队列已有拓扑但没有监听器，Outbox失败耗尽和死信可能需要人工恢复。
