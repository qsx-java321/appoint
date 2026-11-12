> 整理前正式文档的历史快照，检查点 `1e83c0c`。旧路径及代码步骤按当时理解；当前内容见[项目状态](../../../../docs/project-status.md)。

# 医院预约系统实现与验证报告

## 最新交付概况：全项目测试整理（2026-11-12）

八项测试整理全部完成，生产业务、schema和消息拓扑保持。收紧约束与故障点断言，整理时钟与真实提交隔离，合并消息重复职责，MockPay契约取消Spring上下文，架构扫描复用且正反例共用规则；测试写法见[测试约定](../../../../docs/testing.md)。

本轮新执行干净常规340、完整MySQL38、可靠性16及隔离curl92请求全部通过，零失败/错误/跳过。原生产基线963051f，最终测试源码9c5878b，360文件摘要、逐用例结果和资源复核见[最终验收](test-suite-refactor/final-acceptance.md)；各阶段提交见[任务地图](../summary.md)。

测试类57→56，SpringBootTest使用文件33→31；原与最终各260个注解方法，合并/改名有逐项接收位置。常规323→340是补足传输边界和历史样本参数后的净变化，不把数量增长当作覆盖证明。历史八份样本和架构负例不变。

隔离运行`277f4dc1aa1f`的数据库、账号和RabbitMQ vhost由脚本清理，独立只读检查确认不存在；验收应用退出，Compose三依赖健康保留运行，旧数据和卷未删除。渠道仍是MockPay，第二阶段使用测试业务Clock；未接入真实支付或验证前端。

## 2026-11-12 消息代码设计改造交付概况（历史记录）

Inbox/Outbox/RabbitMQ消息代码改造九项完成。四类v1事件类型化、事务内PaymentOutbox、不可变发布输入、Rabbit适配器、严格消费契约、结果enum、共用失败处理和受控重放接口已落地，注释和架构正反例同步。

最终常规323、真实MySQL38、消息可靠性16及真实curl92次通过，零失败/错误/跳过；profile重复常规不累计。生产实现检查点 `88b66ba`，最终阶段补充普通契约测试和文档；实际被测文件摘要及逐场景证据见[本轮验证目录](../../messaging-design/evidence/messaging-design-refactor/README.md)。业务、schema V1～V10、合法v1和拓扑保持；非法输入加严与发布数据库异常传播为明确技术修正。

## 历史交付概况（2026-11-11）

最新交付为三天预约窗口及场次截止：今天至后天十二项、场次开始前十五分钟停止新下单、锁后准入时间和预约/支付一致的截止快照。场次查询追加三个字段，没有新增 REST 路径、数据库迁移或事件版本；数据库仍为 V10。

本轮完整 `./mvnw -o -Pmysql-it verify` 中常规 263 项、MySQL 37 项通过，零失败、零错误、零跳过；真实 Compose/curl 92 次业务请求符合预期。最新执行详情见文末“三天预约窗口与截止规则交付”章节及[场景证据](../../booking-time/evidence/three-day-booking-time/scenario-evidence.md)。本轮未重跑 `reliability-it` 专用 profile；此前的 15 项可靠性结果属于各自历史检查点。

本文保留此前四模块、到院签到和专项 curl 的历史记录。旧的测试数量和二十八项目录只代表当时行为，不作为本轮结果重复累计。

## 2026-11-09 四模块历史基线

验证日期：2026-11-09（Asia/Shanghai）。最终代码检查点：`a61b808`（09）；独立暂存树 `990dc71f121d17c7441621ebce9490727d3d4753` 导出为 `/tmp/booking-09-snapshot-8eo2fl37`。提交后逐字节核对源码、构建及验收文件与被测快照一致；测试后只补记issue状态和结果。10仅更新文档/HTTP示例，复用该代码基线。

## 2026-11-09 历史自动化结果

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

01原覆盖与最终测试类对应关系见 [baseline](../../payment-lifecycle/evidence/booking-module-deepening/baseline.md) 和 [状态矩阵](../../payment-lifecycle/evidence/booking-module-deepening/state-matrix.md)。快速正式支付helper驱动真实attempt、签名通知、Outbox、Codec和Processor，不能代替RabbitMQ传输测试；H2不能证明InnoDB锁和唯一约束。

Compose使用 `mysql:8.4`、`redis:7.4.9`、`rabbitmq:4.3.2-management`，新建独立数据库、用户、vhost与宿主应用，验证正常支付确认、取消退款和迟到支付释放退款，并核对库存、序号、唯一退款、Outbox/Inbox；成功清理本次资源。迟到场景只在本次隔离库缩短截止，禁用扫描以确保由事件释放。Broker破坏性故障仅在Testcontainers注入，共享Compose保持健康。

## 历史数据与保留边界

2026-11-09只读复核默认业务库：Flyway V9、预约0、支付0，完整 [候选SQL](../../payment-lifecycle/evidence/booking-module-deepening/historical-candidates.sql) 返回0条；没有迁移、写入默认业务库或自动修复历史记录。11月8日V3/缺退款表的记录是历史状态。旧失败现场 `accept_fa1f0c42b045` / `accept-fa1f0c42b045` 保留，未擅自清理。

- 已解决：待支付迟到事件残留暂占、普通支付成功消费无限业务重试、支付父子反向锁路径；并发测试证明覆盖场景收敛，不宣称证明任意负载下绝无死锁。
- 保持兼容：到期待支付UNPAID/PAYING的canCancel仍为true；待确认已付的canCancel=false但写入口可取消退款。若要统一权限，需另行确认业务行为。
- 保持补偿前提：closeUnpaid只关闭支付单，PROCESSING尝试保留以接收迟到收款；不擅自新增关闭渠道订单行为。
- 历史异常仅盘点。其他数据库若有候选，需先核实原因再单独制定修复，不能删Inbox或直接改库存。
- 真实微信/支付宝、线上资金与负载未验证；MockPay仅dev/test注册。静态架构规则不证明任意动态SQL语义。本次没有发现需留在本轮处理的新回归。

## 文档与历史验证

2026-11-09 交付时，设计以模块设计、业务流程、API 及[该阶段任务地图](../../payment-lifecycle/summary.md) 为准，ADR0005取代旧半日场次/停诊写流程，ADR0006记录迟到释放和有界重试。现行设计见 [模块设计](../../../../docs/module-design.md)、[业务流程](../../../../docs/business-flow.md) 和 [API](../../../../docs/api.md)；后续三天窗口与截止决定见 [ADR0007](../../../../docs/adr/0007-three-day-booking-and-session-cutoffs.md)。

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

脱敏请求与响应保存在 [curl-2026-11-11.json](../../visit-workflow/evidence/visit-workflow/curl-2026-11-11.json)，执行日志 `/tmp/visit-current-curl-tests.log`。

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


## 2026-11-11 到院功能提交前文档核对（历史记录）

以当前源码和已执行的测试、curl 记录核对本次交付；本阶段仅修正文档，不重跑未改变的业务测试，不把历史记录当成新执行结果。

- README 增加 V10 和当前完成状态，验收说明包含签到接诊及 curl；业务流程、就诊需求/API/设计明确上海当天全天签到，医生动作不另限日期或场次时间，不自动结束就诊。
- 主 API 的列表排序、AppointmentResponse 全部 27 个字段和 visit 嵌套字段已与源码对齐；38 个 REST 操作均有正式 API 文档覆盖。
- 切换手册升级终点改为 V10，区分 V9→V10 与已到 V10 的起点，补充医生账号、DOCTOR、就诊表/索引和正常就诊冒烟；原 V2/V4 门禁及失败前向恢复规则保留。
- 就诊设计中的待实施措辞、模块依赖图和事务表已更新；改动说明的文件清单包含术语、源码、测试、脚本、迁移及脱敏 curl 记录。验证报告顶部区分最新交付和旧检查点，历史执行记录保留。
- 检查正式 Markdown 的本地文件链接、章节锚点和代码围栏、14 段 JSON 示例、6 份 HTTP 示例中的 42 个请求和 18 个 JSON 请求体、脚本语法及 curl 记录脱敏，均通过。测试证据确认常规 241、MySQL 36、可靠性 15、curl 92 次符合预期。
- 提交范围为本次就诊功能及其文档验收；实际 .env、compose.env、target/、scripts/__pycache__/ 不纳入提交。仅创建本地提交，不推送；提交标识以 Git 历史为准。


## 2026-11-11 三天预约窗口与截止规则交付

代码读取基线为 `5d49ff95d294ddfa0aef259f64d2907d93c50889`，以下验收在该基线之后的提交前工作区执行。被测实现与测试文件摘要见[代码清单](../../booking-time/evidence/three-day-booking-time/code-manifest.sha256)；设计和任务状态见[任务地图](../../booking-time/summary.md)。最终交付提交以 Git 历史为准，仅作本地提交，不推送。

### 实际实现

- `BookingTimeRules` 集中日期、下单截止与支付 min；查询使用一次上海秒精度采样，返回今天至后天十二项，并增加 bookingClosesAt、availability、serverTime。
- 创建在设置、场次、就诊人锁后、实际占号前采样准入时间，Java 和 SQL 同步检查三天及严格时刻条件；预约、支付共用创建时间与截止快照。
- 支付入口继续读取本单持久化截止，停止新下单后旧单可支付；历史预约和跨日订单不重算期限。
- 正常流程继续使用原 Controller、Service、支付确认、当天签到和医生接诊。旧创建夹具使用窗口内日期及独立测试医生，保留原测试目的。Compose 正常创建日期改为后天，补充十二项及新字段断言，报告目录在运行前初始化。
- 新增 ADR 0007，说明替代 ADR 0005 的时间规则；数据库 V1～V10、现有退款与消息语义保留。

### 实际执行结果

| 命令 | 结果 | 日志 |
| --- | --- | --- |
| `./mvnw -o -Dtest=BookingTimeRulesTest test` | 纯规则 9 项通过 | `/tmp/three-day-01.log` |
| `./mvnw -o -Dtest=SessionCatalogHttpTest,CatalogManagementIntegrationTest,ModuleDependencyTest test` | 聚焦 32 项通过 | `/tmp/three-day-02.log` |
| `./mvnw -o test` | 阶段常规 258 项通过 | `/tmp/three-day-03-all.log` |
| `./mvnw -o -Pmysql-it -Dtest=BookingTimeRulesTest -Dit.test=AppointmentLifecycleMySqlIT verify` | 规则 9 项、预约 MySQL 13 项通过 | `/tmp/three-day-03-mysql.log` |
| `./mvnw -o -Dtest=OnDemandBookingHttpTest,PaymentAttemptIntegrationTest,AppointmentResponseAssemblerTest,VisitHttpTest test` | 正常链路聚焦 30 项通过 | `/tmp/three-day-04.log` |
| `./mvnw -o -Pmysql-it -Dtest=BookingTimeRulesTest -Dit.test=PatientJourneyHttpMySqlIT verify` | 规则 9 项、真实 TCP/HTTP+MySQL 2 项通过 | `/tmp/three-day-04-mysql.log` |
| `./mvnw -o -Pmysql-it verify` | 最终常规 263 项、MySQL 37 项通过 | `/tmp/three-day-final-mysql.log` |
| `python3 scripts/compose_acceptance.py --http-client curl --http-report .scratch/three-day-booking-time/verification/curl-report.json` | 完整真实 HTTP 92 次符合预期 | `/tmp/three-day-final-compose.log` |

以上成功执行均零失败、零错误、零跳过；不同阶段及 profile 的重复常规测试不累计相加。最终常规比旧 241 项增加 22 项：纯规则 9、目录 2、创建/支付时间 10、当天就诊 1；MySQL 增加同日 SQL 资格 1 项。原零点创建用例按新规则改写，不以删除原覆盖绕过窗口变化。

### HTTP、资源与限制

成功运行编号 `e34a8b021efb`，状态分布为 200×63、201×12、202×13、403×1、409×3，均与每个请求的 expectedStatus 相同。完整脱敏结果见[curl 报告](../../booking-time/evidence/three-day-booking-time/curl-report.json)。第一阶段采用真实 MySQL、RabbitMQ 和 dev/MockPay；第二阶段沿用测试源码专用业务 Clock 构造就诊日，不修改宿主系统时间。

首次 Spring 聚焦测试受沙箱 JVM 附加限制影响；允许该操作后复测通过。创建时间测试初版对 MyBatis 代理的真实委托及测试事务可见性做了校正，最终测试通过。首次 Compose/curl 在写报告时因父目录不存在退出；补充报告目录初始化后从头重跑完整验收通过，不以局部请求代替最终结果。

首次失败保留隔离数据库 `accept_e286a01ad92f`、账号 `a_e286a01ad92f`、RabbitMQ vhost `accept-e286a01ad92f` 和应用日志 `/tmp/appoint-accept-e286a01ad92f.log`。成功运行的数据库、账号和 vhost 已由脚本清理，验收应用进程退出。Compose 三依赖保留运行，既有业务库和数据卷未删除，实际配置凭据未写入文档或报告。

本轮未重跑可靠性专用 profile，未接入真实支付渠道、未实现前端页面或部署生产。秒级截止和跨零点的精确场景由受控 Clock 的 HTTP/规则测试验证，真实 Compose 正常创建使用后天，避免执行时刻影响。文档链接、围栏、JSON、接口字段及任务依赖另做静态检查，不能代替业务验收。

最终静态核对通过：29 份 Markdown、205 个本地链接/锚点、16 段 JSON 示例、18 个 HTTP JSON 请求体；场次示例与 14 个公开 DTO 字段一致。五项任务均 resolved、依赖图一致，十八个场景均已关联证据；29 个代码/测试文件摘要与被测实现匹配，92 次 curl 状态及敏感字段脱敏检查通过。Python 语法与 `git diff --check` 通过；数据库迁移与 pom.xml 未变。


### 三天预约提交前文档收口

对照现有实现、任务状态和被测代码摘要复核，修正设计文档仍将改造前代码称为“当前实现”的表述；ADR 0001、0005 补充当前适用范围与 ADR 0007 的替代关系。到院设计明确 V10 已落地，到院改动说明和旧验收章节明确历史范围；历史七天窗口及旧测试数字保留为带日期的记录。

本阶段仅修正文档，未改变已验收的业务源码、测试和脚本。重新检查 29 份 Markdown、205 个本地链接/锚点、16 段 JSON 示例、42 个 HTTP 请求及 18 个 JSON 请求体；38 个 REST 路径均有 API 文档覆盖，场次示例与 14 个公开 DTO 字段一致。五项任务状态与依赖、十八个场景证据、29 个被测文件摘要、curl 的 92 项预期状态与敏感字段脱敏均通过核对。现有 Maven 报告及最终执行日志确认常规 263 项、MySQL 37 项通过，未将目录中保留的旧可靠性 XML 计入本轮结果。

交付范围包含三天预约实现、测试、现行文档、任务与脱敏验收证据。实际 `.env`、`compose.env`、`target/` 和原有 `scripts/__pycache__/` 不纳入提交；详细本地提交以 Git 历史为准，不推送。

## 2026-11-12 消息代码设计改造最终验收

详细命令、阶段失败与修复、独立提交和S01～S32映射见[任务地图](../../messaging-design/summary.md)和[命令记录](../../messaging-design/evidence/messaging-design-refactor/commands.md)。

- `./mvnw -o -Pmysql-it verify`：常规318与完整MySQL38通过；新增退款渠道成功、本地结果写失败后同refundNo/providerRefundNo收敛。
- `./mvnw -o -Preliability-it verify`：最终相关运行常规322与完整可靠性16通过；发布标记失败使先前同批标记回滚，原ID重发经真实消费者只确认一次；真实ACK丢失与TTL/DLQ/重放继续通过。
- `./mvnw -o test`：最后补齐序列化失败回滚业务写入断言，最终323项通过；该补充未改生产实现或MySQL/可靠性测试。此前干净构建318通过，日志分开保留，不伪称最后干净构建运行了323项。
- 隔离Compose/curl运行 `951cb43e2b90`：92请求全部符合预期，涵盖注册到接诊完成、取消退款与迟到收款，报告脱敏。隔离数据库、账号、vhost只读复核已清理；Compose三依赖健康保留运行，原业务数据/卷不变。

测试修正包括：新用例对MyBatis接口代理撤销故障stub后恢复真实委托；原退款可靠性夹具使用未注册WECHAT渠道，新增调用次数断言揭示覆盖缺口，改为已注册Mock渠道注入退款失败，证明实际三次渠道调用；发布测试按MySQL已存JSON正文比较，避免误把格式规范化当作正文重建。

MockPay仍为开发测试渠道，未接入真实资金、通知消费者、租约发布或运维HTTP。瞬时发布/消费故障可重试，FAILED/DLQ仍可能需要人工恢复，不声称恰好一次物理投递或所有故障自动恢复。


## 2026-11-12 文档与项目进度一致性复核

在71e4ec8之后核对现行说明与历史任务：修正旧总状态、过期的“最新263/37”及doctorId待实现文字，明确独立讲解页已删除；历史旧接口、七天窗口与阶段数字保留其检查点，新增[当前项目状态](../../../../docs/project-status.md)作为进度与边界入口。

本次仅修改文档和HTTP示例占位说明，没有运行新的业务测试。源码、测试、构建、Compose与验收脚本共360文件保持上一轮被测摘要；常规340、MySQL38、可靠性16及curl92沿用上述真实交付记录。新执行的只读检查覆盖实际REST/DTO、Markdown链接、JSON及HTTP示例，详细结果见[文档复核记录](documentation-alignment/report.md)。
