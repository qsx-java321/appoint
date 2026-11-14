# 开发与测试环境总览

核对日期：2026-11-14。本文依据当前仓库配置、测试源码、验收脚本及一次本机只读检查整理；命令清单不代表本轮重新执行了业务测试。未改动运行配置或业务数据，未输出凭据。

后续改造需求已确认，详见[三环境与自动化隔离方案](environment-isolation-plan.md)和[任务地图](../.scratch/environment-isolation/map.md)。方案尚未实施；本文仍描述当前实际配置，不能将方案端口与新命令当成现有能力。

## 1. 先分清三件事

目前仓库有一套持久化的本地开发服务，以及多种临时测试方式；没有提供独立部署的常驻测试站、预发布站或完整生产环境配置。

- **运行服务**：Compose 运行 MySQL、Redis、RabbitMQ；Spring Boot 和 Vite 分别从宿主机启动。Compose 没有打包、启动前后端。
- **Spring profile**：`dev`、`test`、`prod` 决定条件装配，例如 MockPay 只在 `dev` 或 `test` 激活时注册。
- **Maven profile**：`mysql-it`、`reliability-it`、`redis-it` 选择哪些集成测试参与 `verify`，不是部署环境，不自动启动日常 Compose。

特别容易误解：**H2 来自 `src/test/resources/application.yml`，不是来自 `test` 这个 profile 名称。** 单独设置 `SPRING_PROFILES_ACTIVE=test` 启动正常应用，仍使用主资源配置，默认连接 MySQL，不会自动变成隔离测试环境。仓库也没有 `application-dev.yml`、`application-test.yml` 或 `application-prod.yml`。

依据：[主配置](../src/main/resources/application.yml)、[测试配置](../src/test/resources/application.yml)、[Maven 配置](../pom.xml)、[MockPay 条件装配](../src/main/java/com/qsx/appoint/payment/adapter/mock/MockPaymentGateway.java)。

## 2. 全部入口对照

| 使用场景 | 入口 | 实际后端与外部服务 | 数据与生命周期 |
| --- | --- | --- | --- |
| 日常开发、手工联调 | Spring Boot `8080`；Vite `5173` | 本地 MySQL `3306`、RabbitMQ `5672`、Redis `6379`；开发文件启用 `dev` / MockPay | 开发库 `appoint`、RabbitMQ 默认 vhost `/`（未覆盖时）、缓存 namespace `local-dev`；持久保留 |
| 后端常规测试 | `./mvnw test` | 对象测试或 Spring＋H2；通常 MockMvc，不占 `8080`；默认关闭缓存、消息监听、后台任务 | 内存库，部分测试指定独立库名；并非所有用例都回滚，但不需要开发 MySQL |
| MySQL 集成测试 | `./mvnw -Pmysql-it verify` | Testcontainers 启动临时 MySQL 8.4，动态连接地址；包含随机 HTTP 端口的旅程测试 | 类独占容器/测试库；同样叫 `appoint` 也与 Compose 开发库分离 |
| 消息可靠性测试 | `./mvnw -Preliability-it verify` | 临时 MySQL＋RabbitMQ 容器；按测试开启消费者、手动驱动 Outbox 等 | 故障注入、队列清理发生在独占容器 |
| Redis 集成测试 | `./mvnw -Predis-it verify` | 临时 MySQL＋Redis 容器；打开目录缓存 | 独占容器，缓存 namespace `redis-it`；不连接开发 Redis |
| 后端真实 HTTP 验收 | `python3 scripts/compose_acceptance.py` | 自动启动/等待 Compose 依赖；另起随机端口 Spring Boot；支付为 MockPay | 同一组 Compose 服务内，新建 `accept_<run>` 库、`a_<run>` 用户、`accept-<run>` vhost/cache namespace |
| 前端单元测试 | `frontend/` 下 `npm test` | Vitest＋jsdom；客户端替身 | 不需要后端、Docker 或真实浏览器 |
| 前端默认浏览器回归 | `frontend/` 下 `npm run test:e2e` | Playwright 启动 Vite `5175`；浏览器拦截 API 并返回夹具 | 不写后端数据库；不是完整系统验收 |
| 前端轻量真实 API 联调 | `REAL_API_TEST=1 REAL_API_TARGET=... npm run test:e2e` | Vite `5175`；调用指定的已运行后端，不负责启动后端 | 会注册账号、建就诊人和预约；不自动创建隔离库或完整清理 |
| 前端真实浏览器完整验收 | `python3 scripts/frontend_compose_acceptance.py` | 要求 Compose 依赖已启动；另起测试后端 `18082`、Vite `5174`；真实 MySQL/RabbitMQ/Redis，MockPay | 新建 `front_<run>` 库、`f_<run>` 用户、`front-<run>` vhost/cache namespace |
| IDE HTTP Client | `http/*.http` 的 `local` 环境 | 默认直连 `localhost:8080` | 写入目标应用的数据源；没有自动数据隔离或整轮清理 |

三个 Maven 集成 profile 都会先跑常规测试，再跑各自匹配的 `*MySqlIT`、`*ReliabilityIT`、`*RedisIT`。因此普通 `./mvnw verify` 不能代表这些容器测试都已执行；不同命令中的常规测试数量也不能重复累计。

依据：[测试约定](testing.md)、[pom.xml](../pom.xml)、[HTTP 旅程测试](../src/test/java/com/qsx/appoint/PatientJourneyHttpMySqlIT.java)、[消息可靠性测试](../src/test/java/com/qsx/appoint/coordination/PaymentConfirmationReliabilityIT.java)、[缓存容器测试](../src/test/java/com/qsx/appoint/catalog/cache/CatalogDirectoryCacheRedisIT.java)、[两个验收脚本](../scripts/)、[Playwright 配置](../frontend/playwright.config.ts)、[浏览器 API 夹具](../frontend/tests/e2e/backend.ts)、[HTTP 环境](../http/http-client.env.json)。

## 3. 每个配置文件由谁读取

| 文件 | 读取者与作用 | 需要记住的边界 |
| --- | --- | --- |
| `compose.yaml` | Docker Compose：镜像、端口、卷、健康检查 | 只有三个基础服务，没有应用容器 |
| `compose.env` | 显式 `--env-file compose.env`：MySQL 初始化库/账号、RabbitMQ 初始化账号 | 已忽略；已有数据卷中的账号不会随文件修改自动更新 |
| `.env` | 正常 Spring Boot 的主配置通过 `optional:file:./.env[.properties]` 导入 | 是 properties 文件，按项目根目录相对路径读取；不是自动执行的 Shell 脚本 |
| `src/main/resources/application.yml` | 正常应用默认项 | MySQL、Flyway、JWT、Redis及消息/任务参数；缓存默认关闭 |
| `src/test/resources/application.yml` | 测试 classpath 中的同名配置 | H2、测试 JWT、缓存/监听/任务默认关闭；不是 `application-test.yml` |
| 测试类中的 properties / `DynamicPropertySource` | 单个测试的开关、库名、容器地址和凭据 | 最终是否启用消息、缓存或扫描，要看具体测试覆盖 |
| `frontend/.env.local` | Vite 本地代理和前端开关；示例为 `.env.example` | 与根 `.env` 分开；`VITE_` 变量会供前端代码使用，不放后端凭据 |
| `frontend/playwright.config.ts` | 选择测试文件、Vite 端口、代理、浏览器与报告 | 测试进程覆盖 `API_PROXY_TARGET` 和 `VITE_ENABLE_MOCK_PAY` |
| 两个 Python 验收脚本 | 为单次应用子进程设置隔离数据源、vhost、namespace、端口和开关 | 不修改日常 `.env`；前端脚本还显式清空 `SPRING_CONFIG_IMPORT` |
| `http/http-client.env.json` | IDE 请求的 `baseUrl` | 改它不改变 Spring Boot 数据源；只改变请求发给哪个应用 |

正常启动时，命令行参数、系统环境变量可能覆盖文件配置；导入的 `.env` 又可以覆盖主 YAML 默认项。不要只看某一个文件判断最终连接。例如 `SPRING_DATASOURCE_URL` 直接指定数据源，而 `DB_URL` 通过主 YAML 的占位符指定数据源；`SPRING_RABBITMQ_*` 可覆盖文件中的 `spring.rabbitmq.*`。测试另有类级覆盖和容器动态属性。

根目录的 `DB_URL`、`MYSQL_USER`、`MYSQL_PASSWORD` 是应用采用的连接项；Compose 的 `MYSQL_DATABASE` 等是服务初始化项。两份文件描述同一开发库的两端，需要保持匹配，却不会互相同步。Compose 命令都显式指定 `--env-file compose.env`，避免把应用 `.env` 误当作 Compose 的配置入口。

依据：[Compose](../compose.yaml)、[后端示例](../.env.example)、[Compose 示例](../compose.env.example)、[前端示例](../frontend/.env.example)、[忽略规则](../.gitignore)、[主配置](../src/main/resources/application.yml)。

## 4. 日常开发环境的实际开关

按本次检查的本地文件，正常从根目录启动时的预期配置如下。未读取当前进程的完整有效配置，因此不能据文件推断所有运行时覆盖均不存在。

| 项目 | 当前本地文件或主 YAML 的设置 |
| --- | --- |
| Spring profile | `.env` 指定 `dev`；主 YAML 本身没有默认 `dev` |
| 数据源 | `localhost:3306/appoint`；Flyway 启用，启动时执行待应用迁移 |
| RabbitMQ | `localhost:5672`；未在 `.env` 覆盖 vhost，默认 `/` |
| Redis | `localhost:6379`；`.env` 打开目录缓存，namespace 为 `local-dev` |
| 支付 | 后端 MockPay；没有接入真实资金渠道 |
| 消息消费 | 主 YAML `appoint.messaging.enabled=true` |
| Outbox | 启用，每轮间隔 `5s` |
| 预约过期扫描 | 启用，每轮间隔 `30s` |
| 对账 | 启用，每轮间隔 `5m`，滞留阈值 `5m` |
| 业务时间 | `Asia/Shanghai` 的系统时钟 |
| 前端代理 | 当前无 `.env.local` / 前端 `.env`；配置默认将 `/api` 代理到 `8080`，仍可能受启动进程环境影响 |
| 前端模拟支付界面 | 仅 `VITE_ENABLE_MOCK_PAY` 严格等于 `true` 时开启；后端启用 MockPay 不会自动打开前端界面 |

“模拟”有两层：前端默认 E2E 的 API 夹具模拟了整个后端；MockPay 则只模拟支付渠道。日常开发和 Compose 完整验收可以同时使用真实 HTTP、数据库、消息及 MockPay。

MockPay 的渠道支付/退款记录保存在应用进程内存，应用重启会丢失这一侧状态；MySQL 中的订单和支付记录仍然保留。不能把重启前未完成的 MockPay 会话视作持久化支付渠道记录。

新库由 Flyway 初始化结构与示例目录，角色存在不等于已有可登录演示账号。普通注册赋予 USER；管理员需按 [README](../README.md) 授权并重新登录，医生需通过管理员绑定账号。开发库中的手工账号、目录修改和预约不会因为重启应用或 `docker compose stop` 自动还原。

依据：[主配置](../src/main/resources/application.yml)、[业务时钟](../src/main/java/com/qsx/appoint/appointment/config/AppointmentTimeConfig.java)、[MockPay](../src/main/java/com/qsx/appoint/payment/adapter/mock/MockPaymentGateway.java)、[预约页面](../frontend/src/views/AppointmentView.vue)、[初始迁移](../src/main/resources/db/migration/V1__init_schema.sql)。

## 5. 两种 Compose 验收的区别

两者都复用开发基础服务，但业务数据分别用数据库、RabbitMQ vhost、Redis key namespace 隔离。它们不是独占 MySQL/RabbitMQ/Redis 实例，会共享服务容量、固定基础服务端口和持久卷。

| 比较项 | 后端 HTTP 验收 | 前端完整浏览器验收 |
| --- | --- | --- |
| 自动准备依赖 | 执行 Compose `up -d --wait` | 仅检查配置并使用服务；先手动启动 Compose |
| 应用端口 | 动态选择空闲端口 | 固定 `18082`；前端固定 `5174`，需空闲 |
| 启动入口 | 先 `spring-boot:run`，后重启为 `spring-boot:test-run` / `VisitAcceptanceApplication` | 全程测试入口 `VisitAcceptanceApplication` |
| 时钟 | 先真实时间；接诊部分固定到预约日上海 `08:00` | 默认上海当天 `13:40`，可用 `--business-date` 指定日期 |
| 后台任务 | 关闭过期扫描、对账；重启后的接诊部分再关闭消息与 Outbox | 开启消息与 Outbox，关闭过期扫描、对账 |
| 主要覆盖 | HTTP、异步确认、取消退款、迟到支付、缓存、签到接诊 | 真实 Vue 页面及管理员/用户/医生闭环，再核对数据库、队列和缓存 |
| 成功收尾 | 停止本次应用，删除本次库、用户、vhost、缓存键 | 同左，并删除运行账号文件 |
| 失败收尾 | 应用运行阶段失败保留资源与日志，供定位 | 同样保留本轮资源与证据 |
| 日志/证据 | `/tmp/appoint-accept-<run>.log`；可指定脱敏 HTTP JSON | `.scratch/frontend-compose-live/evidence/<run>/`；私密运行文件在被忽略的 `runtime/<run>/` |

资源准备位于应用运行的 `try/finally` 之前，准备阶段中途失败也可能留下部分资源；成功清理过程本身也可能失败。看到残留只能判断未完成清理，不能只凭资源名断定当时的业务断言失败。

两个脚本内部都用 Maven `-o`，需要先缓存相应依赖和插件。前端脚本还要求当前终端能找到 Node/npm、已安装前端依赖以及可用 Chromium。它不要求日常 `8080/5173` 先运行。脚本末尾的 `normalServices: running` 是固定输出，不是开发服务状态探测。

测试 Clock 和测试启动入口位于 `src/test`，不会进入正常 JAR；普通 `spring-boot:run` 不提供调时接口。

依据：[HTTP 验收源码](../scripts/compose_acceptance.py)、[前端验收源码](../scripts/frontend_compose_acceptance.py)、[测试启动入口](../src/test/java/com/qsx/appoint/support/VisitAcceptanceApplication.java)。

## 6. 本次本机检查结果

这是 2026-11-14 的一次快照，之后以实时状态为准。

- `compose.env` 与根 `.env` 均存在；两份文件中的 MySQL 用户/密码、RabbitMQ 用户/密码逐项一致，只比较一致性，不记录值。它不能单独证明卷内账号也与文件一致。
- Compose 的 `appoint-mysql`（`mysql:8.4`）、`appoint-redis`（`redis:7.4.9`）、`appoint-rabbitmq`（`rabbitmq:4.3.2-management`）均为运行且 healthy；运行容器列表中未见额外测试容器。
- `8080` 的 `/api/v1/hospitals` 返回未认证 `401`，`5173` 的首页返回 `200`；`18082` 与 `5174` 连接被拒绝，本轮检查时未运行前端完整验收服务。HTTP 探测证明入口可达，不等于完整业务验收通过。
- 除开发库 `appoint` 外，发现三个隔离库：`accept_d8e4c6b2ea51`、`accept_e286a01ad92f`、`accept_fa1f0c42b045`；RabbitMQ 中存在三个对应 `accept-...` vhost，另有默认 `/`。这些符合后端验收脚本的资源命名，属于待核对的历史残留，未清理；未进一步核实对应数据库用户及 Redis namespace。
- 当前检查 Shell 可找到 Java 21、Python、Docker、curl；未找到 Node/npm，不能据此断言机器没安装 Node，现有 `5173` 服务已可访问。仓库前端要求 Node `>=24.12.0 <25`；新终端跑前端命令前需加载对应运行环境。
- 当前检查 Shell 中未发现 `SPRING_*`、`DB_*`、`MYSQL_*`、`APPOINT_*` 等相关覆盖变量；这不代表 IDE 或已运行服务的环境相同。未验证浏览器安装及 Maven 离线缓存是否完整。

## 7. 日常使用的最短路线

### 正常开发与手工体验

首次配置才复制示例；已有文件保留，不覆盖已有凭据：

```bash
cp compose.env.example compose.env
cp .env.example .env
```

项目根目录启动依赖：

```bash
docker compose --env-file compose.env up -d --wait
docker compose --env-file compose.env ps
```

单独一个终端，从项目根目录启动后端：

```bash
./mvnw spring-boot:run
```

另一个终端，加载 Node 24 环境后启动前端：

```bash
cd frontend
npm ci
npm run dev
```

访问 `http://127.0.0.1:5173`。需要演示支付时，在 `frontend/.env.local` 设置 `VITE_ENABLE_MOCK_PAY=true` 并重启 Vite；API 代理仍指向开发后端。根 `.env` 和前端 `.env.local` 各管一端。

### 验证改动

按改动风险选择入口，无需每次全部执行。后端命令在根目录执行，容器测试要求 Docker 和对应镜像可用：

```bash
./mvnw test
./mvnw -Pmysql-it verify
./mvnw -Preliability-it verify
./mvnw -Predis-it verify
```

前端代码检查和默认模拟浏览器回归在 `frontend/` 执行，浏览器回归需预先安装 Chromium：

```bash
npm run typecheck
npm test
npm run build
npm run test:e2e
```

需要真实系统闭环时，从根目录使用隔离验收脚本；前端验收前先确保 Compose 已启动：

```bash
python3 scripts/compose_acceptance.py --http-client curl --http-report /tmp/appoint-http-report.json
python3 scripts/frontend_compose_acceptance.py
```

`REAL_API_TEST=1` 的轻量方式没有隔离保护：遗漏 `REAL_API_TARGET` 会回退到日常 `8080` 并写入开发数据。优先使用上述完整隔离脚本；确需轻量联调时，先自行准备隔离后端，再显式指定目标。前端 README 中的 `18081` 是历史隔离后端示例，不是当前脚本自动启动的常驻服务。

### 停止与保留数据

前后端各自用所在终端的 Ctrl+C 停止；Compose 依赖用：

```bash
docker compose --env-file compose.env stop
```

MySQL、Redis、RabbitMQ 都挂载持久卷。停止不清数据；不使用删除数据卷的方式排查凭据、迁移或启动问题。已有验收残留需先结合对应日志确认用途，再单独定向处理。

## 8. 测试、构建和上线的边界

`prod` 目前只有排除 MockPay 的装配验证；[对应测试](../src/test/java/com/qsx/appoint/payment/MockPayProductionProfileTest.java) 本身仍运行在 H2 上，没有连接生产支付渠道。只激活 `prod` 不会自动得到独立库、域名、TLS、生产密钥或真实支付；同时激活 `dev` 或 `test` 仍会满足 MockPay 的注册条件。

Maven 构建不调用 npm；前端 `npm run build` 生成 `dist/`，需要另行提供静态托管、`/api` 反向代理及 history 路由回退。仓库没有一套现成的生产 Compose。`npm run preview` 是独立预览命令，不作为生产部署入口，仓库未单独固定其端口。

需要进一步了解测试职责看 [测试约定](testing.md)，前端工具与部署看 [前端 README](../frontend/README.md)，数据库升级与消息恢复看 [支付切换手册](runbooks/payment-module-cutover.md)。
