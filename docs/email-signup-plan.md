# 邮箱验证码与注册票据实施记录

日期：2026-11-17。状态：实施与验收完成；结果见[邮箱注册验收报告](testing/email-signup-report.md)。

实施分支：`feat/email-signup-verification`，起点为 `main` 的 `6a321a5`。本次方案及代码改造均在该开发分支完成；实施记录保留开发分支名称用于追溯，交付合入本地main。

## 1. 已确认的需求和范围

本地开发采用三阶段注册：发送邮箱验证码 → 验证成功取得 `signupTicket` → 设置密码并创建账号。Mailpit 接收测试邮件，用户从 Mailpit 网页读取验证码；注册后沿用邮箱密码登录和 JWT 鉴权。

- 验证码为六位数字，有效期五分钟；重发覆盖该邮箱的旧验证码。
- 票据为随机 UUID 字符串，绑定已验证邮箱，有效期五分钟，注册时消费一次。
- 通过邮箱验证时不创建用户，最终注册才创建 `users` 和 `USER` 角色关联。
- 页面分为“验证邮箱”“设置密码”两步，票据保存在页面内存；刷新重新开始。
- 本次错误场景仅为：验证码错误或过期、票据无效或已消费、重复邮箱不能注册。票据过期或邮箱不匹配统一归入票据无效，不增加新的错误分类。
- 不扩展限流、防刷、安全加固、并发专项、基础设施故障补偿、分布式事务或异步邮件队列。沿用现有参数校验和全局异常处理，不新增这些方向的验收任务。
- 常驻环境按 **test → dev → demo** 顺序逐套验收；上一套前后端停止并核实端口释放后才能启动下一套。共享 Compose 服务可持续运行。

现行行为仍以 [API](api.md)、[模块设计](module-design.md)、[环境说明](environments.md) 为准；本文保留实施设计，实际验收结果见测试报告。

## 2. 当前代码基础与本地资源

| 已有内容 | 本次利用方式 |
| --- | --- |
| `AuthController`、`AuthService`、`AuthServiceImpl` | 扩展现有认证入口和注册流程 |
| `RegisterRequest` 的邮箱、密码校验 | 增加必填 `signupTicket` |
| BCrypt、注册事务、`USER` 角色、邮箱唯一约束 | 保留并复用，无需数据库迁移 |
| `StringRedisTemplate`、Redis 7.4.9 | 存放验证码和票据，消费票据用 `getAndDelete` |
| `AuthView.vue` 同时处理注册和登录 | 注册模式改为两步，登录模式沿用现状 |
| Compose 和 `scripts/local_env.py` | 新服务加入现有依赖启停流程 |

实施前（2026-11-17）只读核实：本地存在 `axllent/mailpit:v1.31`，镜像短 ID 为 `74d609a42ec2`；尚无 Mailpit 容器。现有 `appoint-mysql`、`appoint-redis`、`appoint-rabbitmq` 均为停止状态。这是实施前快照。实施时由Compose创建了 `appoint-mailpit`，完成三环境验收后四个共享容器均停止；既有数据卷保留。

## 3. 接口契约

全部接口位于现有匿名路径 `/api/v1/auth/**`，沿用 `Result<T>` 响应包装；不增加 Security 放行规则。JSON 使用项目现有 camelCase，概念上的 `signup_ticket` 对外字段统一为 `signupTicket`。

### 3.1 发送验证码

`POST /api/v1/auth/register/code`

```json
{"email":"user@example.com"}
```

处理：规范化邮箱 → 检查邮箱重复 → 生成验证码 → Redis 保存五分钟 → 同步发送邮件。返回 HTTP 200：

```json
{"code":0,"message":"success","data":{"expiresIn":300}}
```

验证码只出现在邮件中，响应不返回验证码。邮件标题示例：`[test] 医院预约注册验证码`；正文包含验证码及五分钟有效期。

### 3.2 验证并签发票据

`POST /api/v1/auth/register/verify`

```json
{"email":"user@example.com","code":"012345"}
```

`code` 使用字符串，保留前导零。后端读取并比较验证码；错误时保留当前验证码，用户可重新输入。正确时删除验证码，生成票据并保存邮箱关联。返回 HTTP 200：

```json
{"code":0,"message":"success","data":{"signupTicket":"<随机UUID>","expiresIn":300}}
```

验证码过期由 Redis TTL 判定，不使用预约模块的可调业务 Clock。

### 3.3 注册

`POST /api/v1/auth/register`

```json
{"email":"user@example.com","password":"example-password","signupTicket":"<上一步返回的票据>"}
```

顺序固定：规范化邮箱 → 消费并验证票据 → 检查邮箱重复 → 创建用户和角色关联。消费票据优先于邮箱重复检查，使成功注册后的原请求重放明确返回“票据无效”。重复邮箱注册的验收使用另一张有效票据。

返回 HTTP 201，结构沿用现状，ID 为字符串：

```json
{"code":0,"message":"success","data":{"id":"123","email":"user@example.com","roles":["USER"]}}
```

不自动登录。前端跳转 `/login`，随后调用现有登录接口并通过 `/api/v1/users/me` 确认登录身份。

### 3.4 错误约定

| 条件 | HTTP / 业务码 | 信息 |
| --- | --- | --- |
| 输入验证码与 Redis 中不一致 | 400 / 3003 | 验证码错误 |
| 验证码不存在或已过期 | 400 / 3004 | 验证码已过期，请重新获取 |
| 票据不存在、过期、已消费或绑定邮箱不匹配 | 400 / 3005 | 注册凭证无效，请重新验证邮箱 |
| 邮箱重复，含最终数据库唯一约束冲突 | 409 / 3001 | 邮箱已注册 |

新增 `REGISTRATION_CODE_INVALID`、`REGISTRATION_CODE_EXPIRED`、`SIGNUP_TICKET_INVALID` 三个 ErrorCode；重复邮箱复用 `EMAIL_EXISTS`。参数为空或格式不合法继续沿用现有 `400 / 1001`，不另外扩展测试场景。

## 4. 后端代码和 Service 设计

### 4.1 文件职责

路径均相对项目根目录；Java 类型位于 `src/main/java/com/qsx/appoint/`。

| 文件或类型 | 具体改动 |
| --- | --- |
| `auth/controller/AuthController.java` | 增加 `sendRegistrationCode`、`verifyRegistrationCode`，只接收 DTO、调用 Service、包装 Result |
| `auth/dto/SendRegistrationCodeRequest.java` | `email`，非空且符合邮箱格式 |
| `auth/dto/VerifyRegistrationCodeRequest.java` | `email`、`code`，code 为六位数字字符串 |
| `auth/dto/RegistrationCodeResponse.java` | `long expiresIn` |
| `auth/dto/SignupTicketResponse.java` | `String signupTicket`、`long expiresIn` |
| `auth/dto/RegisterRequest.java` | 增加非空 `String signupTicket`；保留密码 8–64 字符约束 |
| `auth/service/RegistrationVerificationService.java` | 一个具体 Spring Service，组织验证码生成、Redis 读写、邮件调用、票据签发与消费；不再拆接口和 Impl |
| `auth/service/RegistrationMailSender.java` | 一个具体组件，依赖 `JavaMailSender`，用 `SimpleMailMessage` 发送文本邮件 |
| `auth/config/RegistrationProperties.java` | `appoint.registration` 下的 namespace、from、codeTtl、ticketTtl；复用项目配置属性扫描 |
| `auth/service/AuthService.java` | 注册、登录方法签名保持原有 DTO 形式 |
| `auth/service/impl/AuthServiceImpl.java` | 注入注册验证 Service，在现有注册方法写库前校验和消费票据 |
| `common/ErrorCode.java` | 增加上述三个业务错误码 |

所有相关入口使用 `email.trim().toLowerCase(Locale.ROOT)`，沿用现有邮箱身份规则。验证码和票据方法内部统一规范化，避免只依赖前端处理。

### 4.2 Service 方法

```java
// RegistrationVerificationService 方法签名；实现见同名源码
RegistrationCodeResponse sendCode(String email);
SignupTicketResponse verifyCode(String email, String code);
void consumeTicket(String email, String signupTicket);

// RegistrationMailSender
void sendCode(String email, String code);
```

依赖方向：

```text
AuthController ──发送/验证──> RegistrationVerificationService
                                      ├──> UserService（邮箱查重）
                                      ├──> StringRedisTemplate
                                      └──> RegistrationMailSender ──> JavaMailSender

AuthController ──注册──> AuthServiceImpl
                            ├──> RegistrationVerificationService.consumeTicket
                            └──> 现有 UserMapper / RoleMapper / UserRoleMapper
```

`sendCode`：用 `SecureRandom.nextInt(1_000_000)` 生成数字并格式化为六位；Redis `set(key, code, codeTtl)`；同步调用邮件组件；返回有效秒数。

`verifyCode`：`get(codeKey)`；不存在返回 3004；不匹配返回 3003；匹配后删除验证码，使用 `UUID.randomUUID().toString()` 生成票据，`set(ticketKey, email, ticketTtl)`；返回票据及有效秒数。按本次本地顺序操作需求实现，不引入 Lua 或并发控制。

`consumeTicket` 核心实现：

```java
String verifiedEmail = redis.opsForValue().getAndDelete(ticketKey(signupTicket));
if (verifiedEmail == null || !verifiedEmail.equals(normalizeEmail(email))) {
    throw new BusinessException(ErrorCode.SIGNUP_TICKET_INVALID);
}
```

现有注册方法保留 `@Transactional`：在原有邮箱查重之前调用 `consumeTicket`，之后复用 BCrypt 和用户/角色写入。该事务保证 MySQL 用户及角色关系一致，Redis 消费不随 MySQL 回滚；本次不实现补偿或恢复机制。不为此额外拆分事务服务。

### 4.3 Redis 结构与生命周期

| 用途 | Key | Value | TTL |
| --- | --- | --- | --- |
| 验证码 | `appoint:{env}:auth:register-code:{email}` | 六位数字字符串 | 5m |
| 票据 | `appoint:{env}:auth:signup-ticket:{ticket}` | 规范化邮箱 | 5m |

沿用现有 Redis 实例和运行策略。dev/test/demo 的 `appoint.registration.namespace` 分别为 dev/test/demo，不依赖目录缓存是否启用。

`LocalEnvironmentConfig` 的既有重置分支增加对当前环境 `appoint:{env}:auth:*` 的扫描清理。demo 后端启动、test 显式重置时清理；普通 dev/test 启动保留，由 TTL 自然过期。本次验收不显式重置 test，也不清空共享 Mailpit 收件箱。

## 5. Mailpit 与配置

### 5.1 Compose 服务

以下服务已合并进 `compose.yaml` 的 `services`，复用已有镜像，由 Compose 创建并管理。

```yaml
  mailpit:
    image: axllent/mailpit:v1.31
    container_name: appoint-mailpit
    ports:
      - "127.0.0.1:1025:1025"
      - "127.0.0.1:8025:8025"
    networks:
      - appoint-network
    restart: "no"
```

采用 Mailpit 默认 SMTP 行为，不配置认证、TLS、转发或中继。使用默认临时邮件存储，不增加持久卷；邮件用于当前开发验收，不要求跨 Mailpit 退出保留。已核实该镜像自带 `/mailpit readyz` 健康检查（间隔15秒、启动宽限10秒），直接复用，创建后核实 SMTP 和网页可用。

共享一个 Mailpit，标题通过 namespace 标注环境。后端运行于 WSL 宿主，SMTP 地址为 `127.0.0.1:1025`；浏览器访问 `http://127.0.0.1:8025`。

实施阶段已执行：

```bash
docker compose --env-file compose.env config --quiet
docker compose --env-file compose.env up -d --wait mailpit
docker compose --env-file compose.env ps -a
```

后续 `docker compose --env-file compose.env up -d --wait` 和 `stop` 统一管理四个共享服务。现有 `local_env.py init` 已执行整套 Compose up，无需添加第二套容器管理逻辑；脚本的 start/stop 仍只负责对应环境前后端。

### 5.2 Java 邮件配置

`pom.xml` 增加 `org.springframework.boot:spring-boot-starter-mail`，版本由当前 Spring Boot 管理。

共用配置写入 `application.yml`，无需改写已有本机私密 env 文件：

```yaml
spring:
  mail:
    host: 127.0.0.1
    port: 1025
    default-encoding: UTF-8
    properties:
      "[mail.smtp.auth]": false
      "[mail.smtp.starttls.enable]": false
      "[mail.smtp.connectiontimeout]": 5000
      "[mail.smtp.timeout]": 3000
      "[mail.smtp.writetimeout]": 5000
```

三份 `application-dev/test/demo.yml` 分别补充 `appoint.registration.namespace`。`RegistrationProperties`为from提供 `no-reply@appoint.local` 默认值，为codeTtl和ticketTtl提供 `5m` 默认值，不在application.yml重复声明；独立测试配置显式使用 regression/mysql-it namespace，邮件使用测试替身，不连接本地 SMTP。

## 6. 前端实现

`frontend/src/api/types.ts` 增加 `RegistrationCodeResponse`、`SignupTicketResponse`；`api/index.ts` 增加 `sendRegistrationCode(email)`、`verifyRegistrationCode(email, code)`，修改 `register(email, password, signupTicket)`。HTTP 客户端已经不会给 `/auth/` 请求附加登录 Token，可直接复用。

`AuthView.vue` 的注册模式维护 `step: 'verify' | 'password'`、`code`、`signupTicket`、`verifiedEmail`、`confirmPassword`，沿用现有提交中状态和错误展示。

1. 验证步骤显示邮箱、验证码、“发送验证码”“下一步”。发送按钮是普通 button，不触发表单提交；验证码输入按文本处理。
2. 下一步调用 verify，保存返回票据与当时邮箱，清空验证码，切换到密码步骤。
3. 密码步骤展示已验证邮箱，填写密码和确认密码；两次一致后提交 verifiedEmail、password、signupTicket。
4. 返回修改邮箱时清空票据和密码，回到第一步。注册/登录路由切换时清理注册步骤状态；刷新自然重新开始。
5. 3003 显示错误并允许改正验证码；3004 提示重新发送；3005 清空票据并回到验证步骤；3001 提示邮箱已注册并提供现有登录入口。
6. 注册成功后清理状态，携带原有安全 redirect 跳转登录页；使用既有 Auth Store 完成登录。

不新增 Pinia 注册 Store、注册中间页面或浏览器持久化票据。不增加发送倒计时、失败次数限制等功能。

## 7. 实施步骤及检查点

| 阶段 | 工作 | 完成条件 | 状态 |
| --- | --- | --- | --- |
| 0 | 创建本地分支、核实代码和镜像、编写本方案并增加 README 入口 | 文档路径和契约检查通过 | 已完成 |
| 1 | Compose Mailpit、mail starter、配置属性及三个环境 namespace | Compose 配置有效，Mailpit SMTP/UI 可用 | 已完成 |
| 2 | DTO、三个接口、验证 Service、邮件组件、票据消费、重置清理 | 注册必须提交票据，基本服务测试通过 | 已完成 |
| 3 | 两步注册页面、API 类型、调用及提示 | 前端测试与构建通过 | 已完成 |
| 4 | 适配现有注册测试、执行独立回归 | 实际执行结果记录完整 | 已完成 |
| 5 | test → 停止 → dev → 停止 → demo → 停止，逐套真实验收 | 每套完整注册登录流程及约定错误场景通过 | 已完成 |
| 6 | 更新现行业务/API/模块/环境文档和测试报告 | 代码、文档、验收证据一致 | 已完成 |

全部阶段已完成；具体执行结果和范围见验收报告。

## 8. 测试与验收

### 8.1 独立自动化检查

- 使用真实验证Service的MockMvc流程覆盖约定错误，没有另增重复的Service单元测试；验证码过期用测试替身中的键不存在覆盖，真实五分钟TTL在三环境分别验证。
- 新增 Auth MockMvc 流程测试，使用真实注册 Service、独立 H2 和测试专用临时状态替身；仅替换 Redis/SMTP 边界，不 mock 整个验证 Service，从而覆盖三个接口到用户入库、登录、me 的完整调用。
- 测试状态替身及邮件捕获工具只放在 `src/test`，由测试配置导入；不提供生产“跳过验证码”开关。专用注册测试每例清理替身；其他业务测试通过独立邮箱隔离注册数据。
- 适配 `ApiFlowIntegrationTest`、`PatientIntegrationTest`、`AppointmentIdempotencyIntegrationTest`、`AppointmentQueryIntegrationTest`、`AppointmentLifecycleIntegrationTest` 中的直接注册请求。使用公共测试辅助方法走发送→验证→注册，登录请求仍只发送邮箱和密码。
- 检查独立 regression/mysql-it 配置及其上下文，确保新增配置/依赖可以加载，且绝不连接 dev/test/demo Redis、SMTP、数据库。与注册无关的业务测试仅适配夹具，保留既有断言。
- 前端新增两步注册组件测试，覆盖成功操作和下表约定错误提示；沿用现有测试工具，不新增测试框架。

已执行 `./mvnw clean test` 及 `./mvnw -Pmysql-it verify`；前端在frontend下执行 `npm test`、`npm run build`，改动文件通过Prettier检查。337项后端、12项临时MySQL、30项前端测试通过；自动化与常驻验收分别统计。

### 8.2 三环境真实验收清单

每环境使用独立验收邮箱，包含环境名和执行标识，邮件通过收件人和标题定位。正向流程必须实际操作前端与 Mailpit；错误分支可通过真实 HTTP 验证。

| 项目 | 操作与预期 |
| --- | --- |
| 注册并登录 | 页面发送验证码；Mailpit 收到邮件；填码进入密码步骤；注册 201；邮箱密码登录 200；me 返回新用户及 USER 角色 |
| 错误验证码 | 提交不同于邮件的六位数字，返回 400/3003；不创建账号 |
| 过期验证码 | 发码后等待五分钟 TTL 自然过期，再提交邮件中的验证码，返回 400/3004；不创建账号 |
| 无效票据 | 用不存在的票据注册，返回 400/3005；不创建账号 |
| 已消费票据 | 成功注册后重放原票据，返回 400/3005；不重复创建用户 |
| 重复邮箱 | 已注册邮箱发送验证码返回 409/3001；另在创建前为同一新邮箱依次取得两张有效票据，第一张完成注册，第二张提交注册返回 409/3001；最终只有一个用户 |

这里只新增上述错误验收，不做其他错误、并发、压力或故障注入测试。密码确认一致等页面基本交互随正向流程检查，不扩展后端错误分类。

实际五分钟等待期间服务继续运行；执行者用短间隔检查和进度更新，不阻塞整个交流。不要通过 test 业务调时制造验证码过期，也不要增加验证码读取后门。

### 8.3 严格串行启停

先记录三套托管状态、8080/8081/8082 与 5173/5174/5175 端口状态及共享容器状态，确认没有其他环境前后端运行。需要时先停止上一套，再开始以下顺序：

```bash
docker compose --env-file compose.env up -d --wait
python3 scripts/local_env.py start test
# 完成 test 清单并记录结果
python3 scripts/local_env.py stop test
# 检查 8081、5174 已释放，其他环境也未运行

# 仅在 dev 预检标记缺失时，且 dev 已停止时执行 cutover-dev
python3 scripts/local_env.py start dev
# 完成 dev 清单并记录结果
python3 scripts/local_env.py stop dev
# 检查 8080、5173 已释放，其他环境也未运行

python3 scripts/local_env.py start demo
# 按既有生命周期重建 demo；完成清单并记录结果
python3 scripts/local_env.py stop demo
# 检查 8082、5175 已释放
```

上述步骤逐步执行，不把整段作为无人检查的连续脚本运行。dev 保留原数据，test 不执行 reset，demo 遵守现有启动重置规则。验收账号保留并记录邮箱与用户 ID，便于用户复查；不记录密码、验证码或有效票据。结束后恢复本轮开始时的服务启停状态；若共享服务由本轮启动且原先停止，执行 `docker compose --env-file compose.env stop`，保留既有数据卷。

## 9. 文档交付与验收记录

已分别更新：

- `docs/business-flow.md`：用户两步注册体验。
- `docs/api.md`：三个接口、请求响应和本次错误码。
- `docs/module-design.md`：Service 职责、Redis 数据和 MySQL 事务范围。
- `docs/environments.md`：四个共享服务、Mailpit 查看方式、注册数据环境隔离及重置规则。
- `README.md`、`frontend/README.md`：启动及功能入口；将“未实现邮件通知”准确区分为已有注册验证码邮件与未实现业务通知。
- `docs/testing/latest-report.md/json`：按实际运行范围更新自动化结果。
- `docs/testing/email-signup-report.md`：新增本功能串行验收报告，记录代码版本、命令结果、每环境检查项、验收账号及最终启停状态。

现行契约文档随实现更新；本文保留实施过程，现行行为查阅各主文档。

## 10. 参考依据

以下均于2026-11-17查阅；采用适合本地项目的最小配置，不照搬生产部署方案。

- [Mailpit 官方 Docker / Compose 示例](https://mailpit.axllent.org/docs/install/docker/)：官方镜像、SMTP 1025、Web 8025 端口及 Compose 配置方式。
- [Mailpit 运行配置](https://mailpit.axllent.org/docs/configuration/runtime-options/)：默认临时邮件存储；本次不增加持久卷。
- [Spring Boot 3.5 邮件文档](https://docs.spring.io/spring-boot/3.5/reference/io/email.html)：mail starter、JavaMailSender 自动配置和 SMTP 超时设置。
- [Redis GETDEL](https://redis.io/docs/latest/commands/getdel/)：Redis 6.2 起支持原子读取并删除；本项目 Redis 7.4.9 可用于一次性票据消费。

## 实施核对补充

实际测试采用真实 RegistrationVerificationService 的 MockMvc 流程，SMTP 捕获替身和 Redis 状态替身统一放在 RegistrationTestSupport 中，并复用于原有注册夹具；未另外添加重复的Service单元测试。过期在该测试中用键不存在覆盖，五分钟真实TTL由三环境验收分别确认。RegistrationProperties提供from及TTL默认值，公共application.yml配置SMTP，各profile明确namespace。
