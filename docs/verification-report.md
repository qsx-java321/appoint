# 四模块深度化改造验证报告

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
