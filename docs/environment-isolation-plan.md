# 三环境设计

状态：按2026-11-14最新范围交付环境实现。现有业务功能按正确处理，本次不进行功能测试、应用联调验收、HTTP验收、浏览器回归或Testcontainers改造。此前方案中的测试完成条件已撤销，不作为环境交付的前置条件。

## 结构

一组Compose服务提供真实MySQL、Redis、RabbitMQ。三套Spring Boot profile使用相同业务代码和Flyway迁移，分别读取.env.dev/.env.test/.env.demo。前后端在宿主机启动。

| 环境 | 前端→后端 | MySQL | Redis namespace | RabbitMQ vhost |
| --- | --- | --- | --- | --- |
| dev | 5173→8080 | appoint | dev | /appoint-dev |
| test | 5174→8081 | appoint_test | test | /appoint-test |
| demo | 5175→8082 | appoint_demo | demo | /appoint-demo |

操作时一次按需启动一套，切换前停止上一套。不增加代码级跨环境互斥。

## 配置和身份

- 默认profile为dev；IDE/Maven选择单个profile，工作目录统一为项目根目录。
- Spring Boot不全局导入旧根.env；首次准备dev配置时保留原数据库和必要密钥。
- 三环境数据库账号、RabbitMQ账号/权限、JWT密钥分别管理；test/demo账号不拥有其他环境数据库或vhost权限。
- 启动时校验最终端口、数据库、缓存、消息目标，避免Shell/IDE覆盖串环境。
- 前端每个mode对应固定端口和API代理，浏览器会话随origin隔离。

## 数据生命周期

- 共用Flyway迁移与基础目录初始化，不复制dev业务数据。
- dev沿用原appoint及现有账号、预约、支付记录，不重建、不搬库。
- test普通重启保留数据，初始化专用管理员；停止后显式reset才重建自身数据库、缓存和消息，并轮换私密配置中的JWT密钥。
- demo每次后端启动清理自身数据库、缓存和消息，再迁移并初始化用户、医生、管理员，绑定示例医生；每次启动生成进程JWT密钥。
- Spring启动生命周期统一覆盖脚本、Maven和IDE；同环境实例锁及端口检查先于清理，前端刷新/重启不触发重置。

## 初始化和使用入口

scripts/local_env.py提供init、cutover-dev、start/stop/status、reset test及clock test。init幂等创建缺少的资源，保留已有卷、密码和业务数据。配置与卷内凭据不一致时停止，不擅自改密码。

现有dev切换前先私密备份、停止旧应用；资金或消息未完成时中止切换，不删除旧vhost、缓存或历史资源。三个环境通过各自资源和权限隔离，允许在test操作业务而不影响dev数据。

## test业务时间

仅test注册可调整业务Clock和ADMIN接口，支持查询、设置、恢复实时；设置后继续走秒，偏移仅存当前进程。dev/demo使用上海真实时间。系统、数据库、浏览器与JWT时间不变，回拨不撤销已完成事实。

MockPay保持内存实现，跨重启渠道查询和退款续接有限制。demo不代表生产环境，本次不建设生产部署。

## 交付边界

本次交付配置、资源准备、启动生命周期、前端入口和使用文档。已有业务规则、测试类、HTTP示例与回归设施不在本轮继续处理；其早期工作区改动保留，不纳入本次环境提交。不再补齐旧方案E01–E14的测试证据。

使用命令见[环境说明](environments.md)，交付记录见[阶段总结](../.scratch/archive/environment-isolation/summary.md)。
