# 05 支付回调决策分离与支付锁序收敛

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。12项已于2026-11-09交付。后续三天窗口、visit、消息设计及测试整理已完成；正文草图、旧类名及当时数量仅解释该检查点。 当前能力与进度见[项目状态](../../../docs/project-status.md)。

Type: refactor/fix
Triage: ready-for-agent
Status: resolved
Blocked by: 03

设计依据：[spec](../spec.md)，执行位置：[map](../map.md)。初始 Status 使用 triage 标签表示待认领；开始实施改为 claimed，验收完成改为 resolved。Triage 不代表依赖已解除。

## 背景和目标

回调混合幂等、状态判断、资金更新、审计、Outbox 和退款。源码另有 attempt→payment、refund→payment 与目标统一锁序相反的路径。本任务让回调可按决策表独立验证，并在同一可回退任务内修正涉及的支付父子锁路径；只调整并发机制，不改变数据库业务状态机。

## 范围

- 提取 package-private PaymentNotificationPolicy、Decision，以及确有独立依赖的 ContextLoader/Executor/EventFactory/AuditFactory；不要求五个类全部成为 Bean。
- 保留唯一事务入口 PaymentNotificationService.handle 与入口验签；Policy 纯计算，Executor 不开启新事务。
- 按 spec 12.2 同时调整回调、PaymentAttemptTransaction T2、DefaultRefundWorker claim/complete/fail 的父记录先锁；审查 DefaultPaymentRefundOperations 原有 payment 父锁。
- 为 providerOrderNo/attempt/refund 增加必要的无锁定位读取（可复用 BaseMapper），锁后重读并复验归属；只读定位不得作为写决策依据。
- 保持回调两次幂等检查和唯一约束兜底。

## 非范围

- 不更改 Gateway/HTTP、paidAt 来源、支付状态合法集合、尝试生命周期、事件 v1、Outbox 发布器或退款重试策略。
- 不因 CLOSED payment 自动把 attempt 关闭，不接收当前不接受的 CLOSED/FAILED attempt 成功通知。
- 保留11已验收的迟到事件释放行为，不新增预约模块依赖。

## 前置依赖

[03 类型化状态、关闭原因与退款原因](03-typed-lifecycle-language.md)。
只有全部前置任务 Status 为 resolved 才能开始；最终实施状态见本文 Status 和最新 Comments。推荐按map串行顺序执行，避免共享快照和调用点冲突。

## 需要修改的模块和主要文件

下列源码路径相对项目根目录；新增类型明确标注，implementation命名可沿用现有风格，不强制为改名搬文件。

- `src/main/java/com/qsx/appoint/payment/application/PaymentNotificationServiceImpl.java` 及同包新增 Policy/上下文/执行/Factory。
- `payment/application/{PaymentAttemptTransaction,DefaultPaymentRefundOperations,DefaultRefundWorker}.java`
- `payment/mapper/{PaymentAttemptMapper,PaymentRefundMapper,PaymentNotificationMapper,PaymentOrderMapper}.java` 的必要定位方法；不改业务条件 UPDATE。
- `src/test/java/com/qsx/appoint/payment/{PaymentNotificationMySqlIT,PaymentAttemptMySqlIT,PaymentAttemptIntegrationTest}.java`
- `src/test/java/com/qsx/appoint/appointment/AppointmentLifecycleMySqlIT.java`、两份 ReliabilityIT；新增同包 PaymentNotificationPolicyTest 和确定性交错测试。

## 目标代码形态

handle：幂等快速返回→上下文定位和 payment 父锁→attempt 锁及通知锁下复验→Policy→Executor 原子执行。T2/worker：只读定位父 ID→payment 锁→重锁子记录和复验→保持原条件更新。退款请求和 worker 统一由 payment 排他锁保护同单子记录；Gateway.createPayment/refund 始终在短事务之间执行。Policy 返回业务动作，不返回待执行 SQL 或 Runnable。

## 改造后的业务逻辑

金额 compareTo/币种校验优先于资金决策；PROCESSING+PAYING 成功变 SUCCEEDED/PAID 并写 PaymentSucceeded；PROCESSING+CLOSED 成功先记 PAID 事实再幂等退款，回调同事务写两个 Outbox。PAID+SUCCEEDED 重复成功为 ALREADY_SUCCEEDED；失败不得覆盖成功；PROCESSING+PAYING 失败恢复 UNPAID；其他状态按当前审计结果拒绝。相同通知/hash 短路；不同 hash 冲突；不同 ID 的同交易重复不得新增资金副作用。保留当前退款中重复成功的 REJECTED_STATE，而非扩展成功条件。

## 必须保持的不变量

- 资金事实、尝试、审计、PaymentSucceeded 及必要退款/RefundRequested 同事务；任何序列化/审计/条件更新失败均回滚。
- 同 payment 全部相关写先锁父 payment，禁止持有子锁再请求父锁；不反向访问预约。
- 回调成功时间仍为锁后服务端截断秒时间；审计原有取时来源不被顺手替换。
- 相同审计 ID 冲突行为、通知响应 duplicate/空字段/eventId 保持；快速与锁下重复响应细节不能强制统一。
- payment_order_id 唯一退款、provider transaction 唯一约束和事件 v1 字段/类型均保留。
- 库存由 appointment 管理，支付模块不写库存。

## 可验证验收条件

- Policy 不启动 Spring/数据库，覆盖当前所有成功、失败、重复、未知订单、金额币种、PROCESSING/CLOSED/FAILED 组合和审计输出。
- 存在性、幂等和金额校验优先级与 01 一致；影响行数不为1仍沿用当前错误码。
- MySQL 在重复回调、回调/补偿、回调/退款请求、退款请求/worker、同 payment worker 重投交错下，无新增循环等待，最终资金状态唯一；竞争失败允许既有可重试语义但不能丢事件或只提交部分事实。
- 故障注入在 Outbox/审计写失败后核实整笔回滚；Broker 停机时真实回调可提交，再恢复发布/消费。
- 外部 Gateway 调用期间没有活跃业务事务；不更改 T1/T2 分割。
- 代码 diff 无业务条件 SQL、事件 v1、schema 语义变更；02/03 基线兼容。

## 应运行的测试

- 新增 PaymentNotificationPolicyTest 和支付锁序竞争测试（实际类名记录于 Comments）。
- `./mvnw -Dtest=PaymentAttemptIntegrationTest,PaymentGatewayContractTest,AppointmentLifecycleIntegrationTest,PaymentSucceededEventCodecTest,ModuleDependencyTest test`
- `./mvnw test`
- `./mvnw -Pmysql-it verify`
- `./mvnw -Preliability-it verify`（需增加 Broker 停机真实回调提交用例，现有预置 Outbox 用例不足以替代）。

## 独立交付与回退

锁序修正和回调提取作为一个可构建任务提交；回退整个任务的生产改动及依赖新锁图的测试，不只回退单个锁方法。无 schema/数据回滚；回退会重新暴露已记录锁风险。
后续任务已使用本任务成果时，先逆序撤销依赖，或做兼容性调整；独立回退指本任务完成时可整体撤销，不保证任意抽掉已被下游使用的提交仍可构建。任何回退不得删除已提交资金、Outbox/Inbox或退款数据。

## 完成状态清单

- [x] 回调 Policy/Executor 与幂等复验完成
- [x] 回调/T2/worker 全部 payment 父锁在先
- [x] 资金/审计/Outbox 回滚与 v1 契约验证
- [x] 真实 MySQL 竞争与 Broker 故障验证
- [x] 无事务外调用回归且 Comments 更新

## Comments

- 2026-11-08：根据源码复核后的spec创建。仅完成任务定义，未认领、未实施；本轮基线结果见map，不等同于本issue验收结果。 依赖尚未resolved，暂不可开始实施。

- 2026-11-08：已调整回调、支付尝试 T2 和退款 worker 父记录先锁；回调 Policy 拆分和真实竞争回归尚待完成。

- 2026-11-09：04已在199c90a验收。回调提取private NotificationContext加载、纯Policy和execute步骤，未知订单/金额币种纳入纯决策；保留两次幂等复验、锁后服务端paidAt、独立审计取时及原v1构造。PaymentParentLockMySqlIT用真实InnoDB子锁及NOWAIT父锁探针确定性覆盖callback、T2成功/失败、refund claim/complete/fail和退款请求，另覆盖worker重投/请求及callback/退款竞争；Gateway port检查渠道调用无活跃事务。首轮worker测试屏障误占MockPay synchronized monitor导致超时，改由port注入后7项通过，非业务死锁。新增最后2项与完整快照仍须验收。PaymentNotificationAtomicityTest的audit/Outbox故障回滚和重试通过，Broker停机期间真实签名回调提交并恢复确认聚焦通过。

- 2026-11-09：独立暂存快照 `/tmp/booking-05-snapshot-sgwopa8o` 常规173、MySQL34（含9项新增支付父锁/交错测试）、可靠性14及Compose全部通过，零失败/错误/跳过。回调资金/审计/Outbox失败回滚与可重试、真实Broker停机回调提交恢复确认、全部相关支付父锁路径已覆盖；条件SQL、schema和v1构造无修改。05 resolved。

- 2026-11-09：10文档收口核对：本issue原阶段独立验收证据保留；最终集成代码a61b808的常规233/MySQL34/可靠性15及Compose全部通过，零失败/错误/跳过，详见 [最终验证报告](../../../docs/verification-report.md)。09覆盖对照未遗漏本issue保证；此处不以最终数量改写原阶段结果。
