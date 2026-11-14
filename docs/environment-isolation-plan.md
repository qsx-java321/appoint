# dev、test、demo 环境与自动化回归隔离改造方案

状态：需求已确认，待实施。确认日期：2026-11-14。

本轮交付方案与任务拆分，不修改配置、代码、数据库、容器或服务。当前实际入口仍见[环境总览](environments.md)；下文新增的文件、命令和接口均为待实现设计，不能当作当前可执行能力。[任务地图](../.scratch/environment-isolation/map.md)记录实施依赖和状态。

## 1. 已确认的需求

| 项目 | dev | test | demo |
| --- | --- | --- | --- |
| 用途 | 日常开发与真实体验 | test环境应用联调验收 | 首次体验、对外演示 |
| Spring profile | `dev` | `test` | `demo` |
| 前端 → 后端 | `5173 → 8080` | `5174 → 8081` | `5175 → 8082` |
| 物理数据库 | **沿用现有 `appoint`** | `appoint_test` | `appoint_demo` |
| Redis namespace | `dev` | `test` | `demo` |
| RabbitMQ vhost | `/appoint-dev` | `/appoint-test` | `/appoint-demo` |
| 默认数据策略 | 保留已有账号和业务数据 | 保留；显式重置 | 每次启动恢复初始数据 |
| 补充账号 | 不修改已有账号 | 初始化专用联调管理员 | 初始化用户、医生、管理员，绑定示例医生 |
| 业务时间 | 上海真实时间 | 默认真实时间；运行中可设时并继续走秒 | 上海真实时间 |
| MockPay | 启用，继续使用现有内存实现 | 同左 | 同左 |

三环境同时运行，共用 Compose 管理的一组 MySQL、Redis、RabbitMQ；数据库、缓存键与消息相互隔离。开发库逻辑上归属 dev，不改名、不搬库，不复制 dev 数据到 test/demo。

三环境使用同一套 Flyway 迁移与基础种子；业务数据可各自变化。test 管理员、demo 三类账号属于环境初始化补充，不改变共同基础种子。账号密码只保存于本地被忽略的配置；可公开的演示账号仅用于本地 MockPay 演示，文档明确标识其用途。

**test环境应用联调验收**允许通过页面或接口读写 `appoint_test`。**自动化回归验收**包括测试类、自动 HTTP 与浏览器回归，只使用对象替身、H2 或独占 Testcontainers，不连接共享 Compose 的业务或管理端口，不创建共享容器内的临时库/vhost，不读写三套常驻资源。由人还是 AI 执行不改变分类。

test 数据默认保留；时间偏移仅在进程内有效，重启恢复真实时间。demo 的自动重置覆盖脚本、Maven 和 IDE 启动，拒绝在已有 demo 实例运行时清理。自动化回归结束或失败均清理临时容器，保留脱敏诊断证据。

## 2. 现状核查与改造影响

源码检查点：`d791e43fabb4b1a84ec4675d67e50072620e5487`。此前环境调查产生的未提交 README 与环境总览继续保留；历史业务测试报告不是本次改造的验证结果。

| 当前源码事实 | 对改造的影响 |
| --- | --- |
| [主配置](../src/main/resources/application.yml)统一导入根 `.env`，默认连接 `appoint` | 拆分 profile 配置及本地凭据来源；旧 `.env` 的 dev 值不能覆盖 test/demo |
| [测试同名配置](../src/test/resources/regression/application.yml)默认 H2，大量用例激活 `test` | 新增主资源 `application-test.yml` 前先隔离自动化配置；仅改 profile 名不够 |
| [Compose](../compose.yaml)只初始化一个数据库，没有多库/vhost初始化流程 | 新增幂等资源准备入口，兼容已有卷；不能只挂载首次初始化 SQL |
| [MockPay](../src/main/java/com/qsx/appoint/payment/adapter/mock/MockPaymentGateway.java)等三个组件仅 `dev/test` 注册 | 增加 demo 和独立回归装配支持，保留 prod 无 MockPay 的保证 |
| [JWT](../src/main/java/com/qsx/appoint/security/JwtTokenProvider.java)只按密钥验签，没有环境校验 | 三环境分开 JWT 密钥；重置时轮换对应环境密钥，使旧 Token 失效 |
| [前端会话](../frontend/src/lib/session.ts)与预约提交状态使用 sessionStorage | 三个端口已隔离浏览器存储；统一使用 `127.0.0.1`，无需重写状态存储 |
| [Playwright](../frontend/playwright.config.ts)占用 `5174/5175`，真实 API 目标可回落 `8080` | 回归改用临时端口，真实回归只能使用本轮托管地址；应用联调单列入口 |
| [HTTP Client](../http/auth.http)把 Token/ID 写入 `client.global` | 除增加 baseUrl，还需按环境隔离变量，避免切换后沿用其他环境的身份和 ID |
| [后端验收](../scripts/compose_acceptance.py)与[前端验收](../scripts/frontend_compose_acceptance.py)调用共享 Compose 管理接口 | 保留业务断言，替换资源所有权、连接、应用启动与收尾 |
| [测试启动类](../src/test/java/com/qsx/appoint/support/VisitAcceptanceApplication.java)提供固定 Clock | 常驻 test 的可推进时钟需单独实现；不直接把测试 classpath 启动类当成常驻应用 |

既有 Flyway V1/V3 包含示例目录与历史场次，V9设置统一预约容量和费用，V10增加医生角色；它们没有登录账号。沿用这些迁移，不改历史校验和，不把当前 dev 数据导出为新种子。基础迁移完整性按实际 Flyway history 核对。

## 3. 配置与自动化装配

### 常驻环境配置

`src/main/resources/application.yml`只保留公共业务参数及默认 dev 选择；新增同目录 `application-dev.yml`、`application-test.yml`、`application-demo.yml`，分别声明端口、目标数据库、namespace、vhost及环境特有行为。

本地凭据采用根 `.env.dev`、`.env.test`、`.env.demo`，由相应 profile 导入，提供对应 `.example`，实际文件加入 Git 忽略。`compose.env`继续供 Compose 及显式资源初始化使用；服务端初始化凭据不放入前端。旧根 `.env` 的值只迁入 dev 文件，保留原文件供回溯，停止全局自动导入；已有账号密码不因迁移而重生成。

profile 文件是后端映射的来源；脚本、前端与 HTTP 文件中的映射由静态检查核对，不另建一套可互相覆盖的环境配置系统。直接 Maven/IDE启动与脚本启动使用同一应用生命周期。

常驻初始化、重置和调时组件还必须受显式的常驻运行标记控制，例如 `appoint.local-environment.enabled`，由三个主profile配置开启，回归配置默认关闭。仅使用 `@Profile("dev/test/demo")`不足以隔离装配专项测试；test调时组件同时要求常驻标记与单独的test profile。独立容器中的生命周期测试可显式开启该标记并校验本轮资源归属。

启动时先检查有效配置，再连接业务组件：三种常驻 profile 只能选一个；数据源、数据库名、namespace、vhost、端口必须与所选环境一致。尤其是 demo 的破坏性操作，校验目标必须是 `appoint_demo`、namespace `demo`、vhost `/appoint-demo`。不匹配或缺配置时停止启动，不能回退到 dev。

Shell/IDE 的 `SPRING_DATASOURCE_*`、`SPRING_CONFIG_*` 等仍可能改变有效配置，验证必须检查最终值。日志只显示环境名、无凭据的连接目标、端口与时间模式。三环境分别使用数据库账号与限于自身 vhost 的 RabbitMQ 账号；Redis继续按现有前缀隔离。

### 自动化配置先隔离

普通自动化使用 `regression` 语义，与持久化 `test` 分开。自动化基础配置移到测试资源独立目录，例如 `src/test/resources/regression/application.yml`：H2、独立测试 JWT、关闭后台消费者/任务/缓存，容器测试再显式启用需要的组件并注入本轮地址。

Spring 测试统一明确指定该配置根，例如通过仅供测试的组合注解封装 `@SpringBootTest` 的配置位置参数，并支持原有 properties/webEnvironment/classes。Maven、IDE直接运行 JUnit、验收子进程都使用独立配置根；仅给 Maven 插件增加参数，不能覆盖 IDE 运行这一分支。

常规用例的 `@ActiveProfiles("test")`迁到 `regression`。dev/prod 装配专项测试保留测试目标，但配置根仍在回归目录，不加载主资源的常驻连接和 demo 重置逻辑。`LegacyTestRouteAbsenceTest`仍须验证持久 test profile 的旧路由缺失，不能机械改名后丢失该保障；新增 demo 装配与调时路由边界测试。

所有自动化 Spring 上下文在任何持久资源连接或清理之前，必须已绑定 H2或本轮容器连接。容器地址来自托管者，不能由外部任意覆盖。仅判断数据库名称或仅在启动完成后检查，均不足以形成隔离保证。

## 4. 初始化、重置与旧环境过渡

### 共享资源准备

新增 `scripts/local_env.py`，只服务常驻环境。`init`在根目录执行 Compose `up -d --wait`，创建缺少的 test/demo 库、环境账号和三个 vhost；对已存在资源验证权限与匹配关系。重复运行保留业务表、已有账号密码、RabbitMQ 消息和缓存；遇到凭据不符明确报告，不自动覆盖服务内账号。

资源准备不执行环境特有业务写入；应用连接目标库后由 Flyway迁移，再执行对应账号补充。全新拉取项目和现有数据卷都走这个入口，避免依赖仅首次创建卷时执行的脚本。普通应用进程不持有 MySQL root 或 RabbitMQ 全局管理权限。

脚本提供 `start/stop/status <dev|test|demo>`，分别管理前后端子进程和日志。PID与所属实例必须核实，停止只影响该环境；Compose 三服务统一启动/停止，不把停止一个应用当作关闭共享依赖。Maven/IDE启动仍受相同应用侧校验、锁和重置规则约束。

### dev 数据保留与切换

1. 实施前只读记录实际库、Flyway版本、账号和消息状态，并保存受控备份位置；保留现有卷与凭据。
2. 保留物理库 `appoint`及其全部业务事实，dev账号默认沿用现有账号并核对权限。新 test/demo 从空库迁移，不复制开发账号、就诊人或资金记录。
3. 当前 dev 使用默认 vhost `/`和缓存 `local-dev`，目标改为 `/appoint-dev`与 `dev`。切换前停止新业务写入，等待现有支付/退款/Outbox/消费收敛，再停止旧应用。
4. 若旧 vhost 有待消费、未确认、重试或死信，或数据库有未完成资金工作，暂停消息切换并单独定位；不能删除旧消息、改写业务状态或生成新 eventId来完成环境迁移。
5. 通过预检后切到新 vhost，初始化新拓扑与空 dev缓存。旧 `/`、`local-dev`和历史 `accept_*`资源不随初始化脚本自动删除；分别依据日志确认后定向处置。

### test 显式重置

`reset test`只针对停止状态的 test；先确认无运行实例并取得独占重置锁，再清空本环境业务结构/缓存/消息，重新迁移并初始化联调管理员，轮换 test JWT密钥。操作范围固定，不接受任意数据库名。重置期间不能有 test实例启动，失败保留阶段记录并阻止半初始化应用对外服务。

test普通启动也持有同一环境的实例寿命锁，与reset共用排他机制；不能只依赖脚本PID判断，因为应用可能由IDE或Maven启动。锁与进程状态检查不读取或修改dev的业务记录。

首次初始化管理员是幂等补充：创建缺少的专用账号和角色；同名账号不属于初始化流程或字段冲突时停止并提示，已有账号密码不覆盖。普通 test重启不重建账号，也不重置业务数据；用户明确重置后才恢复初始基线。

### demo 每次启动重置

重置由应用启动生命周期执行，不只放在脚本中，也不使用 HTTP服务已经可用后才执行的普通业务 Runner 清表。

顺序固定为：最终配置校验 → 检查 `8082`占用并取得覆盖整个实例寿命的 demo独占锁 → 核验目标 → 在监听器/定时任务工作前清理 demo库、demo缓存与demo消息 → Flyway完整迁移 → 创建三类账号及医生绑定 → 开启拓扑/消费者/任务和 HTTP服务。任何步骤失败均停止启动并释放本进程资源，不继续提供半初始化演示。

锁需要覆盖 Maven、脚本和 IDE，并在清理前防止第二实例。优先使用同一 MySQL服务的应用级锁，持有连接直至实例关闭；不能只检查端口后就假定没有竞争。启动失败清理和进程退出必须释放锁；端口已被其他程序占用时，在破坏性清理前拒绝启动。

数据库清理限定于 demo schema，可通过受限于 demo的 Flyway策略实现 clean/migrate；只在 demo允许该清理能力，不能把 clean全局开启。RabbitMQ清理通过 demo vhost内允许的拓扑操作，普通 demo进程不获取删除任意 vhost的管理能力；Redis只定向清理 `appoint:demo:catalog:v1:*`。重置整个三种容器或其卷不属于方案。

每次 demo启动轮换 JWT签名密钥，使上次演示留下的管理员 Token失效，避免重建后复用同一ID带来的身份混淆；新密钥只在本次进程中使用，无需打印。test显式重置采用相同失效原则，dev保持自己的独立密钥。

## 5. test 调时与支付边界

在正常主应用中新增仅 `test`注册的可调整 `Clock`，默认系统上海时间。设时记录一个线程安全的进程内偏移，此后继续走秒；恢复实时将偏移归零；重启不恢复旧偏移。dev/demo/prod仍使用现有系统 Clock。

新增仅 test存在、要求本环境 ADMIN身份的查时/设时/恢复接口，拟用 `GET/PUT/DELETE /api/v1/test-control/clock`；`scripts/local_env.py clock test`封装调用，Token通过受保护的本地输入或标准输入传入，不写到命令行与公开报告。记录操作者、调整前后业务时间和真实操作时间，不记录Token。无需增加前端调时页面。

统一使用带偏移量的时间输入或明确的上海本地时间格式；非法时间被拒绝。设时不改宿主、MySQL、Redis、RabbitMQ或浏览器时间，不改变 JWT有效期和前端登录倒计时。测试用户可以前后调整业务时间，但已提交的支付、过期、签到或接诊事实不回滚；从头演练使用 test显式重置。

业务 Clock控制预约窗口、支付准入、签到及相关持久化业务时间；过期、对账和Outbox按新的业务时间判断到期，调度器仍按实际间隔运行。已有 Outbox重试时间会受到业务调时影响；设时完成不承诺所有异步任务已经执行。SQL默认时间和 MockPay报文的宿主时间不强制改成同一时间源，验收需区分字段语义。

保留 MockPay进程内存模型，不做渠道事实持久化。dev/test重启后数据库订单仍在，但旧支付会话、渠道查询和退款续接可能失败。应用联调应在同一进程内完成资金闭环；test调时不重启进程。demo每次重建数据，也不承诺保留上次演示的渠道状态。prod仍不注册 MockPay，禁止用“同时启用dev/prod”表达生产环境。

拟更新业务时钟配置、MockPay profile边界、Security权限和接口文档，并明确 [ADR0008](adr/0008-visit-checkin-window.md) 的真实签到规则保持不变；可控时钟只是 test运行能力，不新增业务调时权限到 dev/demo/prod。

## 6. 前端与手工 HTTP

前端新增三个显式启动入口，拟为 `npm run dev`、`npm run dev:test`、`npm run dev:demo`，加载对应 Vite mode并严格占用 `5173/5174/5175`，代理分别固定到 `8080/8081/8082`。`npm test`继续代表 Vitest，避免把常驻 test启动与单元测试混为一谈。

mode对应的已提交配置只放公开端口、代理和演示开关；`.env.local`或Shell中的旧 `API_PROXY_TARGET`与所选环境矛盾时明确失败，不悄悄连另一套后端。三环境使用 MockPay并显式打开模拟支付界面，页面请求仍相对 `/api/v1`，不增加CORS。浏览器sessionStorage保持现有结构，统一文档和脚本host为 `127.0.0.1`。

HTTP Client增加 dev/test/demo三套baseUrl及环境标识；Token、角色账号、幂等键和资源ID按环境存取，旧 `client.global`无前缀变量迁移后不自动继承。切换或重置后重新登录；demo重启后的旧Token必须失败。普通 `.http`操作属于应用联调，不纳入“自动化回归不写持久环境”的承诺。

现有 `REAL_API_TEST`任意目标模式改成显式的应用联调入口，仅允许选择 `test`并清楚声明会写入常驻数据；不再借用 `npm run test:e2e`的自动化回归名称。自动化中的轻量真实API用例由临时容器托管运行，不从常驻入口回落获取后端。

## 7. 自动化 HTTP 与浏览器回归迁移

### 托管边界

新增测试侧 Java `RegressionAcceptanceLauncher`，复用已有 Java Testcontainers依赖管理 MySQL、Redis、RabbitMQ。它持有资源生命周期，启动真实 Spring Boot子进程，调用现有Python断言与Playwright；HTTP验收允许在同一批临时服务上重启测试应用并更换Clock，浏览器验收沿用自己的受控Clock场景。

只复用真正重复的容器/进程/报告准备，不合并两种验收的业务断言。Java将本轮容器身份、动态连接、后端地址和资源所有权通过受保护的运行清单交给子进程；容器凭据不进入公开证据或命令行。Java生命周期需等待Python/浏览器和所有应用进程退出后才销毁容器。

脚本中的 SQL/Redis/Rabbit helper只能操作清单登记的本轮容器，删除所有共享 `docker compose exec`、`compose.env`读取和 `up`调用。后端/前端验收保留HTTP脱敏、业务断言、SQL库存核查、缓存与消息检查。子进程显式使用回归配置根及本轮连接，不继承宿主的常驻数据源/profile/import配置。

回归后端和前端均使用临时端口；排除 `8080–8082`、`5173–5175`。Playwright读取本轮Vite地址，保留 `strictPort`、不复用已有服务器，端口争用时重新分配或报告失败，不连入已运行应用。模拟API的默认E2E无需启动后端，但也使用独立前端端口。

真实回归启动前验证本轮应用和资源归属；不能仅靠“目标不是8080”判断隔离。只允许启动器生成的运行清单，缺失则失败。给进程注入伪造常驻连接的负例必须在任何常驻访问前被拦截。

### 命名与兼容

拟新增 `scripts/http_regression.py`、`scripts/browser_regression.py`作为清晰命名入口，内部调用Java托管者并让其以内部worker模式执行Python断言；worker必须有本轮受保护的运行清单。默认没有 `--target`任意服务参数。

原 `compose_acceptance.py`、`frontend_compose_acceptance.py`先改成打印迁移说明并转发新回归入口的薄包装，保留原 `--http-client`、`--http-report`、`--business-date`能力；包装不再操作共享Compose。内部导入关系迁到新的专用helper，避免“前端导入旧Compose脚本”继续暗含共享服务访问。

Maven新增独立完整流程回归入口，和 `mysql-it/reliability-it/redis-it`分开；普通 `./mvnw test`不隐式下载浏览器或启动完整流程。Python入口与Maven入口最终调用同一托管者，避免双份资源生命周期。

### 结束与诊断

成功、断言失败、准备中断、应用启动失败、浏览器退出和 Ctrl+C均依次停止浏览器/应用/worker，再关闭临时容器；准备阶段也在统一收尾保护内。若Docker本身不可用导致清理失败，明确报告遗留容器标识与原因，不把“预期清理”写成“已清理”。

容器销毁前在可用范围内保存后端日志、脱敏HTTP结果、浏览器失败截图、SQL状态/库存审计、消息计数与缓存摘要；采集失败不覆盖原始失败，也不阻止后续清理尝试。运行清单和账号文件属私密临时文件，结束删除；公开证据不含密码、JWT或完整个人信息。历史证据不重写，保留原检查点与“共享Compose验收”的历史含义。

## 8. 文件级改造清单

新类/脚本名称是实现建议；可按现有组织微调，行为与验收条件不变。

| 范围 | 现有文件 | 新增或调整内容 |
| --- | --- | --- |
| 常驻后端 | `src/main/resources/application.yml` | 拆公共项；新增 `application-dev.yml`、`application-test.yml`、`application-demo.yml` |
| 本地凭据 | `.env.example`、`compose.env.example`、`.gitignore` | 增 `.env.dev/test/demo.example`和忽略规则，提供旧dev配置迁移；停用全局根 `.env`导入 |
| 共享资源 | `compose.yaml`、新增 `scripts/local_env.py` | 保留三服务/现有卷；多库/账号/vhost幂等准备，应用启动/停止/状态、test重置、时钟命令 |
| 生命周期 | 新增 `src/main/java/com/qsx/appoint/localenv/` | 最终目标校验、demo/test重置协调、实例锁、环境账号初始化；后台工作前完成 |
| 时钟 | `appointment/config/AppointmentTimeConfig.java`、上述 `localenv/` | test可调整Clock与仅ADMIN管理接口；系统Clock注册条件保持互斥 |
| 支付装配 | `payment/adapter/mock/MockPaymentGateway.java`、`MockPaymentNotificationDispatcher.java`、`MockPayController.java` | 支持demo/regression；保留prod关闭与内存模型 |
| 自动化配置 | `src/test/resources/application.yml`、`pom.xml`、Spring测试类的注解 | 回归独立配置目录/组合注解，审计全部profile分支及IDE运行 |
| 自动化支持 | `src/test/java/.../support/VisitAcceptanceApplication.java`及新增托管者 | 统一回归配置根，容器所有权、应用/worker/证据收尾 |
| 自动化脚本 | 两个 `scripts/*compose_acceptance.py` | 提取可复用断言与helper，新增HTTP/浏览器回归入口，旧名仅兼容转发 |
| 前端启动 | `frontend/package.json`、`vite.config.ts`、`.env.example` | 三mode配置及命令，检查代理/端口映射 |
| 前端回归 | `frontend/playwright.config.ts`、`tests/e2e/real.spec.ts`、`compose-real.spec.ts` | 临时端口、清单注入；拆常驻应用联调与独立回归，业务断言继续保留 |
| 手工接口 | `http/http-client.env.json`、`auth.http`、`appointment.http`、`visit.http`等 | 环境baseUrl及变量隔离，补test查时/设时示例 |
| 验证与导航 | `scripts/check_documentation.py`、README、AGENTS、前端README、环境总览、测试约定/清单/覆盖、API、模块设计、runbook、项目状态 | 在实施交付时更新现行行为、命令和证据；历史记录保持原样 |

`frontend/src/lib/session.ts`、`stores/booking.ts`、`api/client.ts`不因端口分环境而重写；既有 Flyway版本不为环境拆分而修改。新增时钟管理接口需同步API/权限文档；若检查工具有测试类清单，随新增回归支持用例同步。

## 9. 实施顺序与验证条件

按[任务地图](../.scratch/environment-isolation/map.md)推进：先隔离自动化配置，再新增常驻profile，随后资源初始化/demo重置、test调时、前端与HTTP、容器化完整回归，最后验证与文档交付。02–07全部完成前不声称新环境可用。

| 验收编号 | 可检查结果 | 方式 |
| --- | --- | --- |
| E01 | 三前后端同时启动且映射正确，互相登录/写入不串数据 | 应用联调；只在test/demo写演示数据，dev仅核查已有数据与配置 |
| E02 | 重复初始化保留dev/test数据和已有密码，新库Flyway结构与基础种子一致 | 独占容器集成测试；真实过渡另核对dev快照 |
| E03 | demo从脚本/Maven/IDE启动都重建三类账号；第二实例在清理前失败 | 独占容器模拟各启动入口、并发与端口占用 |
| E04 | demo重置只影响demo库/前缀/vhost；重置后旧Token不能访问 | 临时服务中模拟三环境与守卫数据；常驻联调补充确认 |
| E05 | test普通重启保留数据、恢复真实时间；显式重置恢复基线并使旧Token失效 | 独占容器与测试Clock、应用联调 |
| E06 | test ADMIN可查时/设时/恢复，持续走秒；普通用户403；dev/demo/prod无调时路由 | 对象测试＋隔离Spring/HTTP测试 |
| E07 | test推进后签到、过期、Outbox按业务时间工作，JWT仍按系统时间 | 控制Clock的自动化测试；同进程应用联调闭环 |
| E08 | 常规JUnit及IDE单类运行不加载常驻连接；dev/test/demo/prod装配专项仍独立 | 隔离配置测试及profile负例，审核全部Spring入口 |
| E09 | 常驻profile/数据源不匹配时，在任何重置/业务访问前失败 | 独占容器构造错误环境、恶意Shell覆盖和混合profile负例 |
| E10 | 三常驻环境运行时，所有自动化仍能执行且不访问它们 | 网络/容器归属证据＋预置守卫验证；不能只比较总行数，后台任务可能合法更新 |
| E11 | HTTP/浏览器业务断言迁移后保留支付、退款、迟到支付、缓存、签到接诊覆盖 | 新回归报告与旧场景责任逐项对照 |
| E12 | 任一准备或执行阶段失败均尽力保存脱敏证据并清理本轮进程/容器 | 独立资源中故障注入；明确记录清理失败 |
| E13 | 三环境Token互斥、HTTP变量分开、浏览器存储随origin隔离 | 独占资源负例＋前端测试 |
| E14 | 旧脚本兼容转发不再访问Compose，回归无默认常驻target且不占六个端口 | 静态配置检查＋真实回归启动证据 |

自动化验证使用临时容器中的三组模拟资源，不能为了测试demo重置就清理真实demo，或为测试隔离修改真实dev。用户明确的常驻应用联调才进入 `appoint_test`；dev过渡以只读对照和维护步骤保护现有记录。

实施后按风险运行常规、MySQL、可靠性、Redis、前端类型/单元/构建/模拟E2E，以及新的HTTP/浏览器独立回归和文档检查；记录准确命令、源码检查点、通过/失败/未执行及重复统计规则。不能用历史通过数替代本次测试。

## 10. 需求访谈结论与本轮边界

Q1–Q3确认数据独立、dev沿用 `appoint`、test显式重置及demo预置账号；Q4–Q6选择test可调时、demo每次启动重置、保留MockPay内存模型；Q7–Q9确认运行中调时持续走秒、覆盖所有demo启动方式及自动化清容器留证据；Q10–Q12确认偏移不持久化、test联调管理员和Java托管并复用断言；Q13确认本方案范围。本轮没有待用户决定的需求分支；具体类名和脚本内部实现由后续任务按验收结果收敛。

不包含生产部署、真实支付渠道、MockPay持久化、全局修改系统时间、dev数据库改名、共享卷重建和历史验收残留自动清理。生产部署需要另行设计，`demo`不代表生产。

本轮只核查源码并编写文档；业务实现与上述E01–E14尚未执行。文档检查结果记录在[方案任务](../.scratch/environment-isolation/issues/01-plan.md)。
