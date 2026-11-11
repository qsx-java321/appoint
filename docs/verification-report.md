# 四模块深度化改造验证报告

## 最新交付概况（2026-11-11）

当前交付为新增 visit 的模块化单体，数据库最新迁移 V10，新增 7 个 API、扩展 2 个预约查询。最近常规测试 241 项通过；本次功能交付的 MySQL 36 项、消息可靠性 15 项通过；专项 curl 验收 92 次业务请求符合预期。两个集成 profile 重复执行的常规测试不重复累计。

本文按阶段保留历史记录。下方 2026-11-09 的代码检查点和测试数量只对应当时基线；当前就诊实现、curl 记录和最终文档核对见后续 2026-11-11 章节，代码版本以 Git 提交历史为准。

## 2026-11-09 四模块历史基线

验证日期：2026-11-09（Asia/Shanghai）。最终代码检查点：`a61b808`（09）；独立暂存树 `990dc71f121d17c7441621ebce9490727d3d4753` 导出为 `/tmp/booking-09-snapshot-8eo2fl37`。提交后逐字节核对源码、构建及验收文件与被测快照一致；测试后只补记issue状态和结果。10仅更新文档/HTTP示例，复用该代码基线。

## 最终自动化结果

以下命令在独立快照串行执行，均成功；两个集成profile分别运行，没有组合profile或并行Maven。

| 层级 | 实际命令 | 通过 | 失败 / 错误 / 跳过 |
| --- | --- | ---: | --- |
| 09聚焦 | `./mvnw -o -Dtest=ModuleDependencyTest,BookingQueryServiceTest test` | 18 | 0 / 0 / 0 |
| 常规 | `./mvnw -o test` | 233 | 0 / 0 / 0 |
| MySQL 8.4 | `./mvnw -o -Pmysql-it verify` | 常规233 + MySQL34 | 0 / 0 / 0 |
| MySQL 8.4 + RabbitMQ 4.3.2 | `./mvnw -o -Preliability-it verify` | 常规233 + 可靠性15 | 0 / 0 / 0 |
| Compose真实链路 | `python3 scripts/compose_acceptance.py` | 通过 | 无失败 |

聚焦/常规与两个profile中的常规测试有重复，不重复相加。Failsafe XML按 `*MySqlIT` / `*ReliabilityIT` 分别统计，避免把上轮遗留报告累计；MySQL为9个测试类共34项，可靠性为2个测试类共15项。常规测试包含纯Policy、契约、架构、H2事务和HTTP，不能全部称作H2测试。

日志在 `/tmp/booking-09-final-{focused,test,mysql,reliability,compose}.log`。临时快照和日志可能随系统清理丢失，验收结果已写入Git；重验应重新导出指定提交，不把历史数量当成新的执行证据。

## 行为与保证

| 范围 | 关键证据 |
| --- | --- |
| 创建与日期 | D+1至D+7、四场次28项、查询不写库、设置快照；跨零点最终占号回滚、成功键跨日重放；并发首次生成/最后库存及有效占用唯一约束 |
| 深interface | MANDATORY拒绝无外层事务；find/复制/跨事务/REQUIRES_NEW Case不能写；库存、序号、订单及占用整笔回滚；全部调用者经完整生命周期动作 |
| 决策与兼容 | 查询合法/历史/非法组合、权限、列表serverTime；状态与原因字符串、HTTP状态和DTO、事件v1、归属/完整性错误顺序及取消布尔语义 |
| 支付锁序与原子性 | 9项真实MySQL父锁/交错用例覆盖callback、attempt T2成功/失败、refund request/claim/complete/fail；通知审计或Outbox失败时资金事实回滚并可重试；渠道调用无活跃事务 |
| 迟到支付 | paidAt等于/晚于截止时过期释放/删除占用/退款/Inbox同事务；退款失败回滚；不同事件、取消及扫描竞争只有一次释放和一张退款 |
| 消息与故障 | Publisher Confirm、mandatory return、SKIP LOCKED、确认后标记前崩溃重复；真实签名回调在Broker停机时提交，恢复后确认；提交前故障回滚后重试、提交后ACK前断链重投都只产生一次库存/序号副作用 |
| 有界重试与重放 | 正常失败首次+两次一秒TTL重试、耗尽DLQ、永久无效直接DLQ；转发失败不ACK；重放保留eventId、重置计数，无路由时保留原DLQ消息；退款失败不恢复预约 |
| 旧路径删除与架构 | dev/test/prod请求映射均无旧同步模拟端点；写模块互不依赖，coordination不访问实现/Mapper/Entity，公开application返回不泄漏Entity；Controller不访问Mapper，query包括继承方法只读，违规fixture能触发规则 |

01原覆盖与最终测试类对应关系见 [baseline](../.scratch/booking-module-deepening/baseline.md) 和 [状态矩阵](../.scratch/booking-module-deepening/state-matrix.md)。快速正式支付helper驱动真实attempt、签名通知、Outbox、Codec和Processor，不能代替RabbitMQ传输测试；H2不能证明InnoDB锁和唯一约束。

Compose使用 `mysql:8.4`、`redis:7.4.9`、`rabbitmq:4.3.2-management`，新建独立数据库、用户、vhost与宿主应用，验证正常支付确认、取消退款和迟到支付释放退款，并核对库存、序号、唯一退款、Outbox/Inbox；成功清理本次资源。迟到场景只在本次隔离库缩短截止，禁用扫描以确保由事件释放。Broker破坏性故障仅在Testcontainers注入，共享Compose保持健康。

## 历史数据与保留边界

2026-11-09只读复核默认业务库：Flyway V9、预约0、支付0，完整 [候选SQL](../.scratch/booking-module-deepening/historical-candidates.sql) 返回0条；没有迁移、写入默认业务库或自动修复历史记录。11月8日V3/缺退款表的记录是历史状态。旧失败现场 `accept_fa1f0c42b045` / `accept-fa1f0c42b045` 保留，未擅自清理。

- 已解决：待支付迟到事件残留暂占、普通支付成功消费无限业务重试、支付父子反向锁路径；并发测试证明覆盖场景收敛，不宣称证明任意负载下绝无死锁。
- 保持兼容：到期待支付UNPAID/PAYING的canCancel仍为true；待确认已付的canCancel=false但写入口可取消退款。若要统一权限，需另行确认业务行为。
- 保持补偿前提：closeUnpaid只关闭支付单，PROCESSING尝试保留以接收迟到收款；不擅自新增关闭渠道订单行为。
- 历史异常仅盘点。其他数据库若有候选，需先核实原因再单独制定修复，不能删Inbox或直接改库存。
- 真实微信/支付宝、线上资金与负载未验证；MockPay仅dev/test注册。静态架构规则不证明任意动态SQL语义。本次没有发现需留在本轮处理的新回归。

## 文档与历史验证

当前设计以 [模块设计](module-design.md)、[业务流程](business-flow.md)、[API](api.md) 及 [任务地图](../.scratch/booking-module-deepening/map.md) 为准。ADR0005取代旧半日场次/停诊写流程，ADR0006记录迟到释放和有界重试；无新增领域术语或部署架构决定。

2026-11-05四场次交付曾通过常规98、MySQL24；2026-11-08原代码基线也有98/24/可靠性9。这些是历史验证，不替代本报告。Flyway依赖曾提示只声明验证到MySQL8.1，V1–V9迁移已在本轮真实MySQL8.4验证；没有更改已有迁移文件。

## 10 文档交付验收

2026-11-09，独立暂存树 `e6b226bd73fb291a0b88b4de9254188921234459` 导出 `/tmp/booking-10-snapshot-s8n0x13n`。快照内32份Markdown、124个本地链接、围栏、12项issue字段/状态/依赖、两份依赖表与图及正式DTO字段检查通过；`python3 scripts/compose_acceptance.py` 再次通过，日志 `/tmp/booking-10-final-compose.log`。源码/测试/构建/脚本与09完全相同，按10约定复用三层结果。补记最终验收状态与交接后重新导出最终文档快照检查，不修改被测业务代码。所有12项issue均resolved；不推送。

## 2026-11-10 文档同步核对

本轮以开始时的 `dba84d1` 为代码参照，更新正式文档和 HTTP 示例：补齐账号、就诊人及管理员目录 API，修正幂等创建顺序、管理员初始化引用和支付时间语义，区分 Inbox 确认事务与退款分段事务，明确 Outbox/DLQ/对账恢复边界及升级起点。ADR 保留历史决定并补充当前适用范围；未修改历史任务状态或上述验收数字。

静态检查结果：

- 检查根目录及 docs 下 17 份正式 Markdown 的本地链接、章节锚点和代码围栏，以及 4 份 HTTP 示例中的 README 引用，无失效项。
- 从当前 Controller 提取的 31 个 REST 操作均在 API 文档覆盖；已删除同步模拟入口仅在历史说明中出现，不作为可用接口。
- 6 段 Markdown JSON 示例及 15 个 HTTP JSON 请求体解析通过；4 份 HTTP 示例共 30 个请求，变量都有声明、环境配置或会话赋值来源。MockPay 动态 checkout 路径另与 Gateway 返回格式及接口文档核对；幂等重放后先查详情取支付单 ID，再发起支付。
- 注册/登录、就诊人、目录与统一设置、预约及支付的请求/响应字段、成功状态、权限和主要错误码按 Controller、DTO、Service、Security 配置逐项核对。管理员授权 SQL 仅按 V1 表结构和角色约束检查，未连接数据库执行。
- runbook 的 V2/V4 只读预检与迁移实现条件一致；按 V1、V2–V3、V4–V8、V9 区分剩余迁移和失败后检查要求。消息状态、重试参数、监听器和对账范围与当前实现/配置核对。
- `git diff --check` 通过，变更范围仅为 Markdown 和 HTTP 示例。

以上是文档静态核对，不是新一轮业务运行验收。本轮未运行 Maven 测试、MySQL/RabbitMQ 容器测试或 Compose 验收，未执行授权 SQL、迁移或业务数据修复；2026-11-09 的测试结果仍只代表其注明的检查点。

## 2026-11-10 注册至预约成功的真实 HTTP 验证

文档同步核对完成后，按请求启动现有 Compose 的 MySQL、Redis、RabbitMQ 和 dev profile 的 Spring Boot 应用，在 `http://127.0.0.1:8080` 执行独立账号的真实 HTTP 链路。启动检查确认三个依赖健康、MySQL/RabbitMQ 已连接、Flyway 已是最新版本；支付渠道为 MockPay，回调仍经过正式验签、Outbox、RabbitMQ 和预约确认消费者。

本次共发起 25 个 HTTP 请求（含轮询），关键结果如下：

| 操作 | HTTP / 业务结果 |
| --- | --- |
| 未登录访问医院目录 | 401，认证入口有效 |
| 注册、登录、核对当前用户 | 201 / 200 / 200，注册角色为 USER |
| 创建、查询本人就诊人 | 201 / 200 |
| 查询医院、科室、医生、未来场次 | 均为 200，返回 D+1 至 D+7 共 28 个场次 |
| 创建预约 | 201，PENDING_PAYMENT / UNPAID，支付截止为创建时间加 15 分钟 |
| 同键同内容重试 | 200，返回同一预约标识，不再次占号 |
| 查询预约和暂占后场次 | 200，可用名额从 20 减为 19，确认前无就诊序号 |
| 发起支付尝试、MockPay 完成支付 | 均为 202 |
| 轮询详情、查询本人预约列表 | 200，最终 CONFIRMED / PAID，列表包含成功预约 |

实际观察到流程状态 `PAYMENT_PROCESSING → CONFIRMING_APPOINTMENT → CONFIRMED`。预约 ID 为 `2120092056893313025`，支付单 ID 为 `2120092056943644673`；选择示例市第一人民医院、内科、张医生，场次为 2026-11-13 08:00–10:00，金额 10.00 CNY，就诊序号为 1。

MySQL 只读核对：场次 `20 = available 19 + held 0 + confirmed 1`，下一就诊序号为 2；该预约有 1 条有效占用，对应 PaymentSucceeded Outbox 已发布 1 条、预约消费者 Inbox 已处理 1 条。没有直接修改订单状态、支付截止或库存，也没有取消该成功预约；测试账号、就诊人和预约记录保留供检查。

脱敏 HTTP 记录与详细报告暂存于 `/tmp/appoint-http-journey-2a458b4ff627/`，密码、JWT、通知签名和完整证件号未写入记录；临时文件可能随系统清理丢失，关键结果已在本文保存。本次验证的是正常预约链路及同键重试，不是重新运行完整 Maven、MySQL/RabbitMQ 故障注入或 Compose 隔离验收套件。

验证结束后按请求向本次 Maven/Spring Boot 进程组发送 SIGTERM，确认应用退出及 8080 端口关闭；执行 `docker compose --env-file compose.env stop` 后，MySQL、Redis、RabbitMQ 均为 Exited (0)。容器、数据卷和测试业务数据保留，没有执行 down、删卷或数据库清理。


## 2026-11-11 到院签到、医生看板与门诊接诊

本阶段在已整理的就诊需求和设计文档基础上实现 A—E，保留已有工作；实施与验收成果统一纳入本次功能交付，提交信息以 Git 历史为准。新增 7 个 API，扩展原我的预约列表和详情两个查询；原取消响应复用新增就诊字段，创建响应保持原结构。

### 实际代码与行为

- 数据：新增 V10，增加 doctors.user_id 可空唯一关联与外键、DOCTOR 角色、visit_records 一对一关联及两个预约查询索引；不改历史迁移，不回填历史就诊事实。
- 身份：catalog 负责首次绑定及账号解析，user 负责角色授予，绑定同事务提交并保留既有角色；需要重新登录，不提供换绑/解绑。
- 写业务：新增 visit 的 Service、规则、Entity、Mapper 与两类 Controller，只执行当天签到及 WAITING → IN_PROGRESS → COMPLETED；重复和并发重复请求返回 409，首次时间保留。
- 查询：原 AppointmentResponse 追加 sessionType、visit，旧列表签名委托 DESC；bookingquery 增加医生四场聚合、号码升序名单、快照及脱敏详情。身份、日期、状态和按钮资格均在对应入口校验。
- 不变量：就诊过程保留预约 CONFIRMED、支付 PAID、快照、库存、有效占用和号码。完成就诊不释放名额。
- 接口与架构：医生需要 DOCTOR，绑定需要 ADMIN，普通用户只能签到本人预约；新增模块依赖及只读 Mapper 由架构测试覆盖。新增错误码为绑定冲突 4501、就诊资格冲突 4502；缺失必填参数等使用统一 400/1001。

### 实际执行结果

| 实际命令 | 结果 | 日志 |
| --- | --- | --- |
| `./mvnw -o test` | 241 项通过，0 失败、0 错误、0 跳过 | `/tmp/visit-unit-all.log` |
| `./mvnw -o -Pmysql-it verify` | 常规 241 项再次通过，MySQL 集成 36 项通过，0 跳过 | `/tmp/visit-mysql-all.log` |
| `./mvnw -o -Preliability-it verify` | 常规 241 项再次通过，可靠性 15 项通过，0 跳过 | `/tmp/visit-reliability-all.log` |
| `python3 scripts/compose_acceptance.py` | 完整真实 HTTP 验收通过 | `/tmp/visit-compose.log` |
| `git diff --check`、脚本语法、Markdown 链接/围栏与 JSON 示例 | 通过 | 静态检查输出 |

上述命令串行执行。两个集成 profile 会重复常规测试，常规 241 项不重复累计。新增专门测试共 9 项：VisitHttpTest 6 项、H2 升级 1 项、MySQL 升级 1 项、MySQL 并发 1 项；另扩充既有架构检查。

测试覆盖上海零点前后及就诊日前/当天/次日签到资格，重复动作与首次时间保留，身份/角色/归属、医生重新登录，全部预约 ASC/DESC 和同时间 ID 稳定排序，空四场不写库，四状态查看及按钮矩阵，非队首和无跨行忙碌限制，双方共享状态与时间。真实 MySQL 验证 V9 升级不改历史预约、一对一/外键/状态/账号唯一约束，以及并发绑定、签到、开始和完成只允许一次成功。原预约、支付、退款、库存与消息故障恢复套件均通过。

### 真实 HTTP 验收方法与结果

脚本在 Compose 的 MySQL、RabbitMQ 上创建独立数据库、账号和 vhost。第一阶段正常启动，保留原预约支付确认、取消退款、迟到支付释放和库存守恒场景；另注册医生账号并经管理员绑定 API 授权，重新登录后经真实 HTTP 创建和支付确认四条就诊预约。就诊日前签到返回 409，查询不创建就诊记录。

等待资金与 Outbox 工作完成后，关闭第一阶段应用，使用 `spring-boot:test-run` 启动仅存在于测试源码的 VisitAcceptanceApplication，在同一隔离库注入就诊日 08:00 上海业务 Clock；不改宿主时间，不提供业务调时 API。第二阶段关闭消息、过期及对账扫描，仅执行同步就诊动作与查询，JWT 仍按系统时间校验。测试启动类经检查不在生产 JAR 中。

通过真实 HTTP 构造同一场的待签到、候诊中、就诊中、已就诊四行，验证号码固定排序、按钮矩阵、四场人数和各状态人数之和；同场多行可开始，未签到首行不阻塞其他人。逐行比较双方状态与三个时间，并验证医生详情证件脱敏。随后将剩余三人推进至完成。用户列表仍保留原取消、过期预约，ASC 与 DESC 互为反向。

在动作前、中间四状态、全员完成三个检查点，直接比较 appointment_orders、schedules、appointment_active_claims 和 payment_orders 的完整行，保持完全相同；就诊记录是唯一随三个动作变化的业务数据。

### 运行限制与资源记录

本轮未验证前端页面（仓库为后端）、真实支付渠道或生产部署；这些不在本次范围。历史无就诊记录不推断真实到院或就诊结果。验收时钟仅供测试，普通启动继续使用真实上海 Clock。

首次 HTTP 验收的第二阶段因使用 `spring-boot:run` 未包含测试启动类而失败；改用本地插件的 `spring-boot:test-run` 后从头重跑完整验收通过，不以局部重试替代最终结果。首次失败保留数据库 `accept_d8e4c6b2ea51`、数据库账号 `a_d8e4c6b2ea51`、RabbitMQ vhost `accept-d8e4c6b2ea51` 和 `/tmp/appoint-accept-d8e4c6b2ea51.log`；未记录凭据值。

成功运行的隔离数据库、账号及 vhost 已由脚本清理，所有验收应用进程退出；Compose MySQL、Redis、RabbitMQ 保留健康运行，原有数据卷及业务库未删除。临时日志可能随系统清理丢失。


## 2026-11-11 注册至就诊完成及医生看板专项复测（curl）

按本轮请求重新执行测试，不复用上一轮结果。未修改业务 Java 代码；对现有隔离验收脚本增加 `--http-client curl` 与 `--http-report`，保留默认 urllib 模式。curl 请求的 Token 和 JSON body 经 stdin 传入，不写入进程命令行；结果文件将 Token、密码、签名、完整证件号及联系方式脱敏。

### 测试类

实际执行 `./mvnw -o test`，共 241 项通过，0 失败、0 错误、0 跳过，日志 `/tmp/visit-current-unit-tests.log`。这是包含纯单元和 Spring/MockMvc 集成的常规测试集合，不将全部项目统称为纯单元测试。主要相关测试类：

| 测试类 | 项数 | 验证内容 |
| --- | --- | --- |
| ApiFlowIntegrationTest | 7 | 注册登录、当前用户、错误密码和未登录、目录及预约支付 |
| PatientIntegrationTest | 4 | 就诊人创建读取、证件校验、修改启停、归属权限 |
| OnDemandBookingHttpTest | 4 | 按需场次、设置快照、日期窗口、预约幂等 |
| VisitHttpTest | 6 | 当天签到、三个动作、双方进度、四场统计、名单按钮、排序和权限 |

以上是 241 项中的相关子集，不再次累加。本轮未重跑 MySQL 和可靠性专用 profile；真实 MySQL、RabbitMQ 的正常业务链路由下述 curl 验收覆盖，先前两类 profile 的记录仍保留在上一节。

### 真实 curl HTTP

实际执行：

```bash
python3 scripts/compose_acceptance.py --http-client curl --http-report .scratch/visit-workflow/verification/curl-2026-11-11.json
```

运行编号 `131ba4a46ef3`，进程退出码 0，共 92 次业务请求；状态分布为 200×63、201×12、202×13、403×1、409×3，全部符合预期。403 为普通用户访问医生接口；三次 409 分别为就诊日前签到、重复签到、重复完成接诊，均为预期拒绝，不是验收失败。启动就绪检查仍使用 urllib；所有报告中的业务请求均由 curl 8.5.0 发起。

脱敏请求与响应保存在 [curl-2026-11-11.json](../.scratch/visit-workflow/verification/curl-2026-11-11.json)，执行日志 `/tmp/visit-current-curl-tests.log`。

| 阶段 | 实际结果 |
| --- | --- |
| 注册与登录 | 普通账号和医生待绑定账号注册 201、登录 200 |
| 医疗目录与就诊人 | 查询医院/科室/医生，创建及读取本人就诊人成功 |
| 预约与支付 | 创建预约 201，支付尝试/MockPay 202，经过真实 RabbitMQ 确认为 CONFIRMED/PAID |
| 医生身份 | 在隔离库初始化管理员角色，通过绑定 API 授予医生身份，重新登录取得 DOCTOR |
| 空医生看板 | 四个固定场次全部返回，人数为零 |
| 就诊日之前 | 详情 canCheckIn=false，签到请求被拒绝 |
| 就诊日 | 测试专用 Clock 切换至就诊日，四条预约可签到 |
| 四状态名单 | 同时出现 NOT_CHECKED_IN、WAITING、IN_PROGRESS、COMPLETED，按钮分别为禁用/开始可用/完成可用/禁用 |
| 四状态概览 | 已确认 4 人，待签到、候诊、就诊中、已就诊分别为 1、1、1、1 |
| 号码与接诊选择 | 名单号码始终为 2、3、4、5；未签到首行不阻塞后续接诊，无跨行忙碌限制 |
| 用户/医生详情 | 状态和三个业务时间一致，医生证件脱敏，用户列表仍含取消与过期记录 |
| 最终完成 | 四人全部 COMPLETED；概览 confirmedCount=completedCount=4，其余三项人数为零；开始和完成均不可再次操作 |
| 原流程回归 | 取消退款、迟到支付释放退款通过 |
| 业务不变量 | 三个检查点的预约、支付、场次库存、有效占用完整行一致，号码和快照未变化 |

第一阶段使用正常运行的 Spring Boot、真实 MySQL 与 RabbitMQ；资金和 Outbox 工作完成后重启同一隔离库，由测试源码专用启动类提供就诊日 Clock。第二阶段的签到接诊仍通过真实 TCP/HTTP 和 MySQL 执行，不修改宿主时间，也不直接修改预约日期或就诊状态。支付渠道为 MockPay，未验证真实支付和前端页面。

本轮验收应用进程已退出，成功运行的隔离数据库 `accept_131ba4a46ef3`、账号 `a_131ba4a46ef3` 和 vhost `accept-131ba4a46ef3` 由脚本清理。原有业务数据库、数据卷及前次失败诊断资源均未清理；Compose 依赖保留运行。脚本语法、脱敏字段、预期 HTTP 状态及 `git diff --check` 已检查。


## 2026-11-11 提交前文档核对

以当前源码和已执行的测试、curl 记录核对本次交付；本阶段仅修正文档，不重跑未改变的业务测试，不把历史记录当成新执行结果。

- README 增加 V10 和当前完成状态，验收说明包含签到接诊及 curl；业务流程、就诊需求/API/设计明确上海当天全天签到，医生动作不另限日期或场次时间，不自动结束就诊。
- 主 API 的列表排序、AppointmentResponse 全部 27 个字段和 visit 嵌套字段已与源码对齐；38 个 REST 操作均有正式 API 文档覆盖。
- 切换手册升级终点改为 V10，区分 V9→V10 与已到 V10 的起点，补充医生账号、DOCTOR、就诊表/索引和正常就诊冒烟；原 V2/V4 门禁及失败前向恢复规则保留。
- 就诊设计中的待实施措辞、模块依赖图和事务表已更新；改动说明的文件清单包含术语、源码、测试、脚本、迁移及脱敏 curl 记录。验证报告顶部区分最新交付和旧检查点，历史执行记录保留。
- 检查正式 Markdown 的本地文件链接、章节锚点和代码围栏、14 段 JSON 示例、6 份 HTTP 示例中的 42 个请求和 18 个 JSON 请求体、脚本语法及 curl 记录脱敏，均通过。测试证据确认常规 241、MySQL 36、可靠性 15、curl 92 次符合预期。
- 提交范围为本次就诊功能及其文档验收；实际 .env、compose.env、target/、scripts/__pycache__/ 不纳入提交。仅创建本地提交，不推送；提交标识以 Git 历史为准。
