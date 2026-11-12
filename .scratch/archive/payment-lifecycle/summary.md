# 支付与预约生命周期交付

## 阶段与交付

归档核对基线：`1e83c0c6bb4ddfcdfe22196d1309e7e0c04c5bef`，日期2026-11-12。以下记录阶段交付，当前行为由[项目状态](../../../docs/project-status.md)及其现行文档定义。

独立支付模块拥有attempt、通知审计、退款及Outbox；coordination负责同库跨模块规则，bookingquery只读组合。选择模块化单体与明确业务接口，保留同库事务，避免为拆分而增加服务或通用框架。锁序以场次→预约→payment父记录为基础。迟到收款关闭释放、退款与Inbox原子提交；退款worker分段事务且渠道沿用refundNo幂等。

主要交付提交：a293afa（独立支付）、ea5bbeb（本地环境与流程）、cb6931e（迟到支付）、937e717（有界重试）、ce9fcb6（文档与HTTP交付）。原issue的关闭记录提交列在下表，可能是验收文档提交，不等同于全部实现修改所在提交。

## 演进与边界

旧compat及同步模拟支付入口已删除，现走attempt→签名通知→Outbox→Inbox。三天入口与就诊属于后续交付；事件v1及同库事务边界继续有效。FAILED与DLQ可能人工恢复，不能宣称物理恰好一次或自动修复所有历史异常。

## payment-module-refactor

历史文件提交范围：2026-10-29至2026-11-12（含后续文档复核，具体实现/验收时间以记录为准）。

原任务均已完成；下表保留编号、标题和状态关闭记录，原始全文按文末Git方法读取。

| 任务 | 标题 | 状态 | 关闭记录提交 |
| --- | --- | --- | --- |
| 01 | 01 基线行为与模块契约 | resolved | `a293afa` |
| 02 | 02 Legacy 支付预检与切换 runbook | resolved | `a293afa` |
| 03 | 03 挂号费与不可变金额快照 | resolved | `a293afa` |
| 04 | 04 支付可靠性持久化模型 | resolved | `a293afa` |
| 05 | 05 创建预约协调与写模块拆分 | resolved | `a293afa` |
| 06 | 06 取消、过期与停诊生命周期协调 | resolved | `a293afa` |
| 07 | 07 PaymentGateway 与 MockPay 契约 | resolved | `a293afa` |
| 08 | 08 支付尝试状态机与单活跃尝试 | resolved | `a293afa` |
| 09 | 09 渠道回调幂等与支付 Outbox | resolved | `a293afa` |
| 10 | 10 RabbitMQ 拓扑与 Outbox 发布可靠性 | resolved | `a293afa` |
| 11 | 11 Inbox 幂等异步确认与支付 202 API | resolved | `a293afa` |
| 12 | 12 异步全额退款与迟到支付补偿 | resolved | `a293afa` |
| 13 | 13 过期协调、对账与 DLQ 重放 | resolved | `a293afa` |
| 14 | 14 真实并发与可靠性验收矩阵 | resolved | `a293afa` |
| 15 | 15 bookingquery 跨模块只读投影 | resolved | `a293afa` |
| 16 | 16 兼容清理、文档与端到端交付 | resolved | `a293afa` |

### 验收与实施记录

下列记录属于该阶段；旧规则、旧类名和临时路径保持历史含义。

**任务01**

- 2026-10-29：已认领。测试 seam 为公开 service/HTTP 行为与包依赖边界，不断言私有调用次数。
- 2026-10-29：枚举契约先因目标类型不存在而红灯，补最小枚举后绿灯。
- 2026-10-29：新增支付截止精确边界、已确认预约重复取消 characterization；新增 ArchUnit 生产代码规则及违规 fixture，证明新 appointment→payment Mapper 依赖会失败。
- 2026-10-29：保留的 Legacy 豁免为 `AppointmentLifecycleServiceImpl`、`AppointmentCreationTransactionService`、`AppointmentLifecycleService` 与 `PaymentServiceImpl`，阶段 3 删除。
- 2026-10-29：`./mvnw test` 51 项通过；`./mvnw -Pmysql-it verify` 的 H2 51 项、MySQL 11 项全部通过。

**任务02**

- 2026-10-29：issue 01 resolved，已认领。活跃旧支付按 `PENDING_PAYMENT + UNPAID` 判断；终态数据只读保留。
- 2026-10-29：新增 V4 Java migration；H2 验证空库、活跃阻断、终态保留和重复迁移，MySQL 8.4 验证阻断不改写订单且后续表未出现。
- 2026-10-29：新增 `docs/runbooks/payment-module-cutover.md`，覆盖维护窗口、备份校验、RabbitMQ 拓扑检查、冒烟、回退边界与前向恢复。
- 2026-10-29：完整 `./mvnw -Pmysql-it verify` 通过：H2 53 项、MySQL 12 项，0 失败、0 错误、0 跳过。

**任务03**

- 等待 issue 02 resolved。
- 2026-10-29：V5、排班费用创建/更新锁定、预约与支付双快照及金额响应完成；Legacy 空费用保持可读但不能进入新预约流程。
- 2026-10-29：`./mvnw test` 57 项通过；`./mvnw -Pmysql-it verify` 运行 H2 57 项、MySQL 12 项，全部通过。

**任务04**

- 等待 issue 02 resolved。
- 2026-10-29：V6/V7、五类可靠性表、模块自有 entity/mapper、锁定与 Outbox 扫描原语完成。
- 2026-10-29：本期保留 `payment_orders.user_id` 作为兼容所有权快照；创建事务写入一致值，并由集成测试验证。
- 2026-10-29：真实 MySQL 测试发现并修复 CHECK 对 NULL 返回 UNKNOWN 的漏洞；最终 H2 58 项、MySQL 13 项全部通过。

**任务05**

- 2026-10-29：完成预约暂占、支付单创建 application interface；协调模块不引用写模块 Mapper/实体。
- 2026-10-29：幂等唯一键冲突在首次创建事务完整回滚后重放，避免 rollback-only 事务读取。
- 2026-10-29：聚焦架构/幂等/库存 25 项、完整 H2 59 项、MySQL Testcontainers 13 项全部通过。

**任务06**

- 2026-10-29：预约/支付分别提供 lifecycle application interface，协调器统一按排班→预约→支付顺序锁定与推进。
- 2026-10-29：Controller、过期 job、排班停诊入口已迁移，删除跨写模块生命周期实现及全部 ArchUnit Legacy 豁免。
- 2026-10-29：聚焦测试 26 项、完整 H2 58 项、MySQL Testcontainers 13 项通过。

**任务07**

- 2026-10-29：完成无渠道 SDK 泄漏的 Gateway DTO/port/registry，以及创建、关闭、查询、验签和全额退款契约。
- 2026-10-29：MockPay 使用 HMAC-SHA256 测试签名和支付/退款幂等键，仅在 dev/test profile 注册 Adapter 与 HTTP 202 操作入口。
- 2026-10-29：共享契约与生产隔离 8 项、完整 H2 66 项通过。

**任务08**

- 2026-10-29：完成 T1 建 attempt/PAYING、事务外 Gateway、T2 PROCESSING/失败恢复 UNPAID。
- 2026-10-29：新增 `POST /api/v1/payments/{id}/attempts` HTTP 202，校验归属、截止和 Legacy。
- 2026-10-29：H2 70 项、MySQL Testcontainers 14 项通过；并发发起仅一项成功，Gateway 测试确认无活动数据库事务。

**任务09**

- 等待 issue 08 resolved。
- 2026-10-29：新增统一免 JWT 渠道通知入口；Adapter 先验签，application transaction 再校验渠道订单、金额、币种和状态。
- 2026-10-29：首次成功在同一事务推进 attempt/order、持久化通知审计和版本化 `PaymentSucceeded` Outbox；重复、payload 冲突、金额不匹配、伪造签名与迟到失败均已覆盖。
- 2026-10-29：MySQL 并发重复通知使用锁后 current read 收敛为一次资金副作用和稳定 eventId；完整 H2 70 项、MySQL Testcontainers 15 项通过。

**任务10**

- 等待 issue 09 resolved。
- 2026-10-29：声明 durable domain/retry/DLX、三条业务队列及各自 retry/DLQ；消息使用版本化 JSON、持久投递和稳定 `eventId`。
- 2026-10-29：实现按索引扫描、MySQL `FOR UPDATE SKIP LOCKED`、correlated confirm、mandatory return、指数退避及耗尽 `FAILED` 告警；成功 confirm 后才标记 `PUBLISHED`。
- 2026-10-29：新增 `reliability-it` profile。真实 MySQL 8.4 + RabbitMQ 4.1 覆盖 confirm、标记前崩溃重复、Broker 停机恢复、mandatory return、FAILED、DLQ 和双发布器竞争；H2 70 项及 reliability 6 项通过。

**任务11**

- 等待 issue 06、10 resolved。
- 2026-10-30：`PaymentSucceeded` 改为版本化 envelope；listener 严格校验 v1，Inbox 与预约确认/库存/就诊序号在同一事务提交，返回后才手工 ACK，永久坏消息 reject 到 DLQ，临时异常 requeue。
- 2026-10-30：MockPay complete/fail 在 HTTP 202 后异步进入统一验签回调；废弃 `simulate-success` 仅在 dev/test 注册并返回 202，其兼容实现也通过支付尝试、MockPay、通知、Outbox 和 Inbox 推进，生产 profile 不暴露 Mock 或旧入口。
- 2026-10-30：预约详情增加目标词汇内的 `workflowStatus`，覆盖 `PAID + PENDING_PAYMENT → CONFIRMING_APPOINTMENT`；原无资金取消组合继续可读但不臆造目标词汇外状态。
- 2026-10-30：H2 73 项、MySQL Testcontainers 15 项、MySQL + RabbitMQ reliability 8 项全部通过。可靠性覆盖 Broker 停机积压/恢复确认、重复投递单副作用和永久坏消息 DLQ。

**任务12**

- 已完成幂等 `requestFullRefund`、每支付单唯一全额退款和版本化 `RefundRequested` Outbox。
- 已确认预约取消立即释放号源并返回 HTTP 202；无资金待支付取消保持 HTTP 200。
- 支付中取消后的迟到成功回调记录真实收款，回调侧与 `PaymentSucceeded` 消费侧均幂等确保退款，不恢复预约。
- refund worker 使用 T1 领取、事务外 Gateway、T2 成功/失败落库；重复投递不重复渠道退款，失败按 1 秒退避重试三次后进入 DLQ。
- 验证：`./mvnw test` 74 项通过；`./mvnw -Pmysql-it verify` H2 74 + MySQL 15 项通过；`./mvnw -Preliability-it verify` H2 74 + 双容器 9 项通过。

**任务13**

- 2026-10-30：过期扫描按持久化 `paidAt` 判断，保护按时支付并恢复确认；迟到支付关闭并退款。
- 2026-10-30：对账可修复 `PAID + PENDING_PAYMENT` 和关闭后缺退款，其他陈旧 Outbox/退款/尝试及关联不一致形成分类人工项；单条修复失败不阻断批次。
- 2026-10-30：受控 DLQ 重放保留原 eventId，重复消息由 Inbox 去重。H2 聚焦 4 项、完整 H2 76 项、reliability 双容器 9 项通过。

**任务14**

- 2026-10-30：真实支付回调与取消、过期、停诊竞争均收敛到合法关闭态、唯一退款和守恒库存；重复取消仅一笔退款。
- 2026-10-30：MockPay 覆盖有效/无效签名、金额错、通知冲突、失败重试、重复成功与乱序失败；MySQL 25 项通过。
- 2026-10-30：Outbox confirm/return、Broker 中断恢复、重复投递、退款重试/DLQ、原 eventId 重放的双容器 9 项通过；完整 H2 无失败。

**任务15**

- 等待 issue 11、12 resolved。
- 2026-10-30：跨预约、支付、排班的 SQL 与内部查询记录已迁入只读 `bookingquery`；公开查询接口仍由 appointment 包提供，避免写模块反向依赖查询模块。
- 2026-10-30：投影增加金额/币种、支付尝试数量及最新尝试、退款摘要，并覆盖等待支付、处理中、确认中、已确认、退款中、已退款和过期；Legacy 空金额终态保持可读。
- 2026-10-30：查询/架构聚焦测试通过；完整 H2 83 项、MySQL Testcontainers 24 项通过。

**任务16**

- 等待 issue 14、15 resolved。
- 2026-10-30：删除浅层旧支付单元测试；`simulate-success` 保留为 dev/test 废弃包装，prod profile 断言 Mock Adapter、Mock Controller、旧 Controller 和对应路由均不存在。
- 2026-10-30：真实 MySQL HTTP 旅程改用 attempt 202、MockPay complete 202 和轮询确认；README、API、业务流程、交互演示、HTTP 示例与支付模块验证报告已同步。
- 2026-10-30：最终门禁全部通过：H2 83 项、MySQL Testcontainers 24 项、MySQL+RabbitMQ 双容器 9 项，无失败、错误或跳过。

## booking-module-deepening

历史文件提交范围：2026-11-08至2026-11-12（含后续文档复核，具体实现/验收时间以记录为准）。

原任务均已完成；下表保留编号、标题和状态关闭记录，原始全文按文末Git方法读取。

| 任务 | 标题 | 状态 | 关闭记录提交 |
| --- | --- | --- | --- |
| 01 | 01 基线行为、状态决策表与风险证据 | resolved | `c4f1dc0` |
| 02 | 02 查询流程投影与响应组装分离 | resolved | `b6a12da` |
| 03 | 03 类型化状态、关闭原因与退款原因 | resolved | `fd6e446` |
| 04 | 04 预约暂占创建内部深度化 | resolved | `199c90a` |
| 05 | 05 支付回调决策分离与支付锁序收敛 | resolved | `ea89132` |
| 06 | 06 预约与支付生命周期 interface 深度化 | resolved | `1255737` |
| 07 | 07 跨模块生命周期决策与流程编排收口 | resolved | `0a983b6` |
| 08 | 08 迁移支付测试并删除 dev/test 旧模拟路径 | resolved | `b71c89f` |
| 09 | 09 架构、并发与消息可靠性最终门禁 | resolved | `a61b808` |
| 10 | 10 最终 interface、流程与验证文档交付 | resolved | `acc2bd0` |
| 11 | 11 迟到支付释放暂占 | resolved | `cb6931e` |
| 12 | 12 消费有界重试与可靠转发 | resolved | `937e717` |

### 验收与实施记录

下列记录属于该阶段；旧规则、旧类名和临时路径保持历史含义。

**任务01**

- 2026-11-08：已建立 Compose 隔离验收入口；阶段 1 快照常规 98、MySQL 24、双容器 9 项通过，真实预约支付取消退款链路通过。跨零点最终占号测试和完整决策矩阵仍待补齐，暂不标记 resolved。

- 2026-11-08：新增真实事务跨零点最终占号回滚与跨日幂等重放测试；在当前工作区及原生产代码暂存快照均通过。状态矩阵及其余验证仍需逐项核对。

- 2026-11-09：基于 ea5bbeb 补全 [状态矩阵](evidence/booking-module-deepening/state-matrix.md)，新增30项边界/优先级/权限断言（总常规154）。独立暂存快照 `/tmp/booking-01-snapshot-cr7kbp14`：`./mvnw -o test` 154通过；`./mvnw -o -Pmysql-it verify` 常规154及MySQL24通过；`./mvnw -o -Preliability-it verify` 常规154及双容器10通过，均零失败/错误/跳过；`python3 scripts/compose_acceptance.py` 通过且清理本次隔离资源。生产代码无改动。11/05/12/09仍须按矩阵补齐各自故障/竞争保证，不以本次通过提前关闭。默认库只读盘点 V9、预约/支付0条，历史候选0条。

**任务02**

- 2026-11-08：已拆分 Projector 和 Assembler，并添加状态决策表；尚待最终全量验收。

- 2026-11-09：查询拆分已随ea5bbeb实现；01/11/12分别在c4f1dc0、cb6931e、937e717验收。核对Mapper SQL与公开DTO相对9d20e30无差异；Projector覆盖全部合法/历史组合及其余持久化组合拒绝，Assembler保留权限差异/脱敏/摘要。新增BookingQueryServiceTest验证列表只读取一次Clock并截断秒，以及不存在/非本人优先于支付完整性与取时间。查询聚焦通过，独立快照 `/tmp/booking-02-snapshot-g9lqzgi7` 运行聚焦、常规及Compose；本阶段无锁/SQL/消息变更，按本issue规格不重复容器全量。

- 2026-11-09：独立快照查询聚焦、常规168项及Compose正常/迟到支付真实链路通过，零失败/错误/跳过；SQL和DTO无差异。02 resolved。

**任务03**

- 2026-11-09：01/11/12/02已分别验收。开始类型化生命周期快照、关闭及退款原因；持久化Entity/Mapper、公开HTTP DTO及事件v1保留字符串；回调Policy、attempt与query本轮状态判断迁移到enum。未知/null存储状态统一映射INTERNAL_ERROR。初轮常规168通过，新增契约测试后须独立快照重新验收。

- 2026-11-09：独立快照 `/tmp/booking-03-snapshot-_y2bra_r` 常规170、MySQL25、可靠性13及Compose真实正常/迟到支付链路全部通过，零失败/错误/跳过，Maven串行执行。退款四种原因、数据库/HTTP/事件稳定字面值、未知存储状态内部错误和架构约束均保留。03 resolved。

**任务04**

- 2026-11-08：已提取就诊人校验和预约快照构造；其余整理及最新代码回归尚待完成。

- 2026-11-09：03已验收，提取private lockOrCreateSchedule、holdOne和claimActiveAppointment；保留现有就诊人校验与快照Factory。主方法表达校验、场次锁定、就诊人/目录资料、最终占号、快照和占用保存。设置→场次→就诊人锁序及最终占号前Clock重读次数/错误优先级不变；只删除已明确非空doctor的冗余别名和空检查，不引入单实现interface或新框架。快照验收待执行。

- 2026-11-09：独立快照 `/tmp/booking-04-snapshot-cc6ytsf8` 聚焦22、常规170、MySQL25及Compose正常/迟到支付真实链路全部通过，零失败/错误/跳过。无条件SQL、公开interface或schema变更，MANDATORY与外层同库创建事务保持。03提交fd6e446。04 resolved。

**任务05**

- 2026-11-08：已调整回调、支付尝试 T2 和退款 worker 父记录先锁；回调 Policy 拆分和真实竞争回归尚待完成。

- 2026-11-09：04已在199c90a验收。回调提取private NotificationContext加载、纯Policy和execute步骤，未知订单/金额币种纳入纯决策；保留两次幂等复验、锁后服务端paidAt、独立审计取时及原v1构造。PaymentParentLockMySqlIT用真实InnoDB子锁及NOWAIT父锁探针确定性覆盖callback、T2成功/失败、refund claim/complete/fail和退款请求，另覆盖worker重投/请求及callback/退款竞争；Gateway port检查渠道调用无活跃事务。首轮worker测试屏障误占MockPay synchronized monitor导致超时，改由port注入后7项通过，非业务死锁。新增最后2项与完整快照仍须验收。PaymentNotificationAtomicityTest的audit/Outbox故障回滚和重试通过，Broker停机期间真实签名回调提交并恢复确认聚焦通过。

- 2026-11-09：独立暂存快照 `/tmp/booking-05-snapshot-sgwopa8o` 常规173、MySQL34（含9项新增支付父锁/交错测试）、可靠性14及Compose全部通过，零失败/错误/跳过。回调资金/审计/Outbox失败回滚与可重试、真实Broker停机回调提交恢复确认、全部相关支付父锁路径已覆盖；条件SQL、schema和v1构造无修改。05 resolved。

**任务06**

- 2026-11-09：全部依赖已resolved（05提交ea89132）。替换旧Operations/View/ScheduleLifecycleView为AppointmentLifecycle/Case/Confirmation与PaymentLifecycle/Case，全部调用者迁移，删除refundPaid及无调用Mapper能力。预约内部封装场次→预约锁、库存、序号及占用，锁/写入MANDATORY；事务同步器中的身份集合限制Case仅当前事务使用，能随REQUIRES_NEW挂起/恢复，不创建全局工作流框架。Legacy markPaid仅限08前过渡保留。首轮聚焦55项通过，新快照还将包含支付关闭回滚断言。

- 2026-11-09：独立全新快照 `/tmp/booking-06-snapshot-m2251go_` 聚焦72、常规181、MySQL34、可靠性14及Compose全部通过，零失败/错误/跳过。旧Operations/View/ScheduleLifecycleView与无调用refundPaid全部删除；新快照从零编译验证，无旧target类掩盖依赖。无事务拒绝、find/跨事务拒绝、挂起恢复、库存/序号/占用回滚及真实竞争均通过。06 resolved。

**任务07**

- 2026-11-09：06已在1255737验收。新增package-private纯BookingLifecyclePolicy及具名取消/过期/确认决策，取消决策分别携带closeReason/refundReason/refundPending。协调用例保持原锁/归属/完整性与提前返回时机；未到期过期入口不锁支付，未到期已付对账仍处理，关闭取消仍不取新时钟，活跃决策原位置取一次now。事件仍validate→Inbox→锁预约→Policy→完整操作，不重读支付；11释放及12重试行为保留。首轮现有聚焦通过，纯矩阵及最终快照继续验收。

- 2026-11-09：独立暂存快照 `/tmp/booking-07-snapshot-syfyevyo` 聚焦92、常规223、MySQL34、可靠性14及Compose全部通过，零失败/错误/跳过。纯Policy无Spring/Mapper/Clock/Gateway依赖，取消布尔/原因、paidAt缺失及截止边界、事件终态、入口短路和Inbox原子性回归通过。07 resolved。

**任务08**

- 2026-11-09：07已在0a983b6验收。迁移六组旧HTTP/同步调用到FormalPaymentTestSupport：真实attempt→MockPay签名→Ingress验签→读取真实Outbox→v1 Codec→公开Processor；H2仅解开JSON列额外字符串编码，不承担RabbitMQ传输保证。实际HTTP+MySQL/RabbitMQ由PatientJourney和Compose保留。删除旧Controller/预检/同步方法/markPaid及compat构造、无调用payIfUnpaid和PaymentResponse。dev/test新增请求映射不存在断言，prod沿用映射与Mock禁用断言。干净构建常规225项通过；MySQL竞争增加REFUND_PENDING唯一退款事实及重复支付单Inbox/库存/序号断言，待独立快照验收。

- 2026-11-09：独立全新快照 `/tmp/booking-08-snapshot-ddye990u` 常规225、MySQL34、可靠性14及Compose全部通过，零失败/错误/跳过。正式尝试拒绝场景同时设置预约与支付截止fixture；重复支付先创建同一正式attempt，再并发签名回调，断言一个Outbox/Inbox、一次库存转换和序号。支付/取消竞争允许且验证REFUND_PENDING唯一退款事实。生产源码和测试无旧同步/compat业务残留；dev/test/prod映射均无旧路由，当前操作文档原已使用正式attempt/complete，无旧用法。08 resolved。

**任务09**

- 2026-11-09：08已在b71c89f验收，依赖全部resolved。新增架构规则及故意违规fixture：写implementation（含未注解公开具体类型）、公开返回Entity及泛型、Controller→Mapper、query继承写接口/写注解/伪装SELECT写语句/锁定读；生产规则和反例聚焦16项通过。补真实消费者业务写后提交前故障窗口（测试配置装饰公开Processor，回滚后暂停以核实Inbox/库存/序号/占用，再放行实际Broker重试）；保留12提交后ACK前故障窗口与05Broker停机真实回调。RabbitMQ两份IT镜像对齐Compose的4.3.2。完整快照尚待验收。

- 2026-11-09：最终暂存树 `990dc71f121d17c7441621ebce9490727d3d4753` 导出 `/tmp/booking-09-snapshot-8eo2fl37`，串行执行 `./mvnw -o -Dtest=ModuleDependencyTest,BookingQueryServiceTest test`（18）、`./mvnw -o test`（233）、`./mvnw -o -Pmysql-it verify`（常规233+MySQL34）、`./mvnw -o -Preliability-it verify`（常规233+可靠性15）及 `python3 scripts/compose_acceptance.py`，全部通过、零失败/错误/跳过。按profile筛选XML核对，未把保留的MySQL报告累计为可靠性数量。RabbitMQ统一4.3.2；初次聚焦遇到非持久非独占测试队列被Broker拒绝，改为持久测试队列后通过，生产拓扑无改动。查询Clock测试已改为真实Assembler响应断言，重复只读浅检查由完整规则替代；01覆盖对照见baseline。源码/构建/验收281个文件与被测快照逐字节相同。日志 `/tmp/booking-09-final-{focused,test,mysql,reliability,compose}.log`；09 resolved，未改变生产行为或业务库数据。只读复核默认库V9、预约0、支付0、历史候选0。已知限制：真实支付未接入；保留查询与写入口权限差异及PROCESSING+CLOSED迟到补偿契约；测试不宣称证明任意负载/动态SQL。

**任务10**

- 2026-11-09：09在a61b808完成最终独立快照门禁，开始文档交付；本阶段仅修改文档/HTTP示例及本地issue记录，生产与测试基线复用09。

- 2026-11-09：明确暂存24个文档/HTTP示例/issue文件，导出树 `e6b226bd73fb291a0b88b4de9254188921234459` 到 `/tmp/booking-10-snapshot-s8n0x13n`。快照内核对32份Markdown及124个本地链接、围栏、12项issue字段、两份依赖表与图、正式DTO字段及HTTP示例均通过；`python3 scripts/compose_acceptance.py` 真实支付确认、取消退款、迟到支付释放退款及库存事实验收通过，成功清理本次隔离资源，日志 `/tmp/booking-10-final-compose.log`。源码/测试/构建/脚本与09已测快照逐字节一致，按本issue约定复用233/34/15三层零失败/错误/跳过结果，无需重复全容器矩阵。随后只补记验收状态、报告及交接，重新导出最终文档快照检查。ADR0004补实现落实而不重写已接受决定；GLOSSARY无新增业务术语，保持不变。无待决业务修复被隐藏，历史数据及兼容差异见最终报告。10 resolved，01–12全部交付，不推送。

**任务11**

- 2026-11-08：根据用户接受的方案创建，待完成依赖及完整验收。

- 2026-11-08：已实现迟到事件关闭预约并释放暂占，补齐占用 fixture；聚焦 H2 集成测试 2 项通过。独立事务回滚、真实并发、历史只读盘点及三层验收尚未完成；前置 01 基线矩阵尚待补齐。

- 2026-11-09：前置01已在 c4f1dc0 resolved。首轮生产修复已随 ea5bbeb 提交，本阶段补充退款创建失败的独立事务回滚、截止后成功、三种关闭状态及不同eventId幂等、MySQL四路竞争和Compose真实迟到支付。快照 `/tmp/booking-11-snapshot-nbgv8xyc` 验收中，尚不标记resolved。默认业务库只读盘点为V9、预约/支付0条，完整历史候选查询0条；未修复历史数据。

- 2026-11-09：独立快照聚焦28项、常规159项、MySQL25项、可靠性10项全部通过且零失败/错误/跳过；Maven串行执行。扩展Compose验收同时通过正常取消退款和真实迟到事件释放/退款，成功清理本次隔离资源。代码修复起点ea5bbeb，前置01提交c4f1dc0，本阶段提交SHA见Git日志。

**任务12**

- 2026-11-08：根据用户接受的方案创建，待完成依赖及完整验收。

- 2026-11-08：已实现三次有界重试、确认后 ACK、退款重试与 Outbox/DLQ 重放确认；新增单元和真实 RabbitMQ 测试，尚待最终验收。

- 2026-11-09：01已在c4f1dc0验收；11已在cb6931e验收。现有生产实现来自ea5bbeb。本阶段补充ConfirmedMessagePublisher的ACK/NACK/mandatory return/超时/连接失败单测；真实RabbitMQ逐次收取重试，检查两次间隔均至少900ms（配置1000ms）且总共调用三次；真实mandatory return重放失败保留DLQ原消息，恢复路由后保留eventId并清除两类计数/TTL。真实MySQL提交后注入ACK前IOException，再物理关闭channel触发Broker重投，验证Inbox、库存及序号单副作用。两个可靠性类聚焦运行均通过；完整快照 `/tmp/booking-12-snapshot-5zpor6w1` 三层与Compose门禁运行中，保持claimed。

- 2026-11-09：独立快照常规166、MySQL25、可靠性13及Compose全链路全部通过，零失败/错误/跳过。三次有界处理、两次TTL延迟、确认后ACK、DLQ重放和提交后ACK故障窗口已验证；先前生产实现随ea5bbeb提交，本阶段提交验收证据。

## 证据与当前阅读

- [booking-module-deepening/baseline.md](evidence/booking-module-deepening/baseline.md).
- [booking-module-deepening/historical-candidates.sql](evidence/booking-module-deepening/historical-candidates.sql).
- [booking-module-deepening/state-matrix.md](evidence/booking-module-deepening/state-matrix.md).
- [business-flow.md](../../../docs/business-flow.md).
- [module-design.md](../../../docs/module-design.md).
- [scenario-coverage.md](../../../docs/testing/scenario-coverage.md).
- [verification-report.md](../../../docs/verification-report.md).

## 原文追溯

逐文件去向见[清单](../../archive-manifest.json)。原始路径与摘要由清单保存；例如：

```bash
git show 1e83c0c6bb4ddfcdfe22196d1309e7e0c04c5bef:.scratch/payment-module-refactor/spec.md
```

旧报告中的/tmp日志不承诺持久存在；历史通过不代表本次重跑。
