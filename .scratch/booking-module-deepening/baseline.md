# 四模块改造基线与风险记录

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。12项已于2026-11-09交付。后续三天窗口、visit、消息设计及测试整理已完成；正文草图、旧类名及当时数量仅解释该检查点。 当前能力与进度见[项目状态](../../docs/project-status.md)。

## 改造前基线

基线提交 `9d20e301db5896c08896730a91a09f4a1b8cb058`，2026-11-08 实测常规 98 项、MySQL 24 项、MySQL+RabbitMQ 可靠性 9 项均通过。以上为改造前结果，不代表当前代码的最终验收。

## 原始状态矩阵及已确认目标

| 场景 | 改造前 | 已确认目标 |
| --- | --- | --- |
| 待支付，paidAt 早于截止 | 确认预约，即使消息延迟 | 保持 |
| 待支付，paidAt 等于或晚于截止 | 退款但暂占仍保留 | EXPIRED、释放暂占、删除有效占用并确保退款，和 Inbox 同事务 |
| 已确认的重复成功事件 | 幂等返回 | 保持 |
| 已关闭后成功 | 确保退款，不恢复预约 | 保持 |
| 过期前扫描 | 返回不处理 | 保持 |
| 已确认预约超过取消截止 | 拒绝取消 | 保持 |
| 用户取消待确认已支付预约 | 按取消时刻关闭并退款 | 保持原因和布尔返回 |
| 成功事件一般处理失败 | NACK 并重新入队 | 总共三次，重试间隔一秒，耗尽 DLQ |
| 永久无效成功消息 | 直接 DLQ | 保持 |

## 支付锁序风险

改造前静态检查发现回调及尝试 T2 有 attempt→payment 路径，退款 worker 有 refund→payment 路径，可能与 payment→子记录的写入形成锁环。这里只记录静态风险，未证明实际发生过死锁。05已统一父 payment 先锁并通过9项确定性真实MySQL交错测试，09最终全矩阵再次通过；原始风险不等于已经复现过死锁。

## 历史候选盘点

[只读 SQL](historical-candidates.sql) 仅筛选候选，不证明记录一定属于该缺陷。最初盘点时未发现本地3306服务，未报告数量；该描述仅适用于早期环境。2026-11-09最新只读复核默认库V9、预约0、支付0，完整SQL候选0；没有查询线上或自动修复历史数据。

## 实施中验证记录

- 迟到事件和 Inbox 集成测试新增独立事务回滚验证。
- 查询流程投影新增合法、历史及非法组合决策表。
- 首轮改动后常规 115 项与双容器 10 项通过；该轮不包含后续支付锁序和 Policy 改动。
- 首次锁序 MySQL 回归因同时运行另一个 Maven 编译，共用 target 导致测试上下文类加载失败；该结果无效，已改为串行重跑。

- 最新生产代码串行运行常规 124 项、MySQL 24 项、双容器可靠性 10 项，全部通过、零跳过。之后新增不同 eventId 重复迟到支付断言，聚焦 H2 测试通过。

- 2026-11-08：通过 Compose 只读检查默认业务库，Flyway 版本为 V3，预约单和支付单均为 0 条；该库没有 `payment_refunds`，因此完整历史候选 SQL 不可执行。未迁移或修改该业务库。隔离验收库迁移至 V9 并完成真实预约、支付、确认、取消、退款和库存校验。

- 2026-11-08：阶段 1 暂存快照已执行 Compose 隔离真实业务验收，以及常规 98、MySQL 24、双容器可靠性 9 项，均通过。RabbitMQ Testcontainers 使用 4.1，Compose 使用 4.3.2，版本对齐仍待完成。跨零点最终占号测试和完整决策矩阵仍待补齐。

- 2026-11-08：`BookingMidnightIntegrationTest` 用可控 Clock 验证最终占号前跨零点整笔回滚及成功订单跨日幂等重放；当前代码和原生产代码暂存快照均通过。

## 2026-11-09 本轮续作

- 当前源码起点 `ea5bbeb`；已有改动已经提交，不重复重写。状态决策、错误顺序、退款原因、返回值和测试映射见 [状态矩阵](state-matrix.md)。
- 新增聚焦断言运行45项通过、零失败/错误/跳过；独立暂存快照三层及 Compose 门禁已通过。
- Compose MySQL/Redis/RabbitMQ 均 healthy。默认业务库只读复核最新 Flyway V9、预约0、支付0、退款表存在；执行完整 historical-candidates.sql 返回0条。该状态已不同于11月8日记录，未对该库执行迁移或写入。
- 未处理旧失败现场 accept_fa1f0c42b045；后续仅在确认归属和需要时处理，不触碰其他业务数据。

### 01 暂存快照实际验收

2026-11-09，快照 `/tmp/booking-01-snapshot-cr7kbp14`：常规154、MySQL24、双容器可靠性10，均零失败、零错误、零跳过；三个 Maven 命令均串行、离线执行。Compose 真实预约、支付、异步确认、取消、退款和库存守恒通过，成功清理本次隔离资源。日志 `/tmp/booking-01-{test,mysql,reliability,compose}.log`。后续仅更新报告文字，测试/生产内容与该快照一致。

### 11 暂存快照实际验收

2026-11-09，`/tmp/booking-11-snapshot-nbgv8xyc`：聚焦28、常规159、MySQL25、双容器10，均零失败/错误/跳过。串行命令同01，日志 `/tmp/booking-11-{focused,test,mysql,reliability,compose}.log`。Compose新增真实迟到支付释放及退款通过，仅在本次隔离库缩短截止；过期扫描关闭，确保由事件路径释放。11 resolved；历史候选0条，不自动修复。

### 12 暂存快照实际验收

2026-11-09，`/tmp/booking-12-snapshot-5zpor6w1`：常规166、MySQL25、双容器可靠性13，均零失败/错误/跳过；Maven串行离线执行，Compose正常及迟到支付链路通过并清理本次资源。日志 `/tmp/booking-12-{test,mysql,reliability,compose}.log`。Broker停机故障仅在Testcontainers中注入；共享Compose未停止。

### 02 暂存快照实际验收

2026-11-09，`/tmp/booking-02-snapshot-g9lqzgi7`：查询聚焦通过、常规168项全通过且零失败/错误/跳过；Compose正常及迟到支付链路通过并清理本次资源。SQL、DTO、锁和消息无变更，按02规格未重复MySQL和可靠性全量（相同生产代码在12快照已通过25/13项）。日志 `/tmp/booking-02-{focused,test,compose}.log`。

### 03 实施范围与兼容边界

本阶段增加AppointmentStatus、AppointmentCloseReason和RefundReason，并类型化两个生命周期快照、协调用例、回调Policy、查询内部状态判断及本轮attempt/退款请求判断。Entity/Mapper字段、Flyway、HTTP DTO和事件JSON保持原字符串；AppointmentHold/PaymentOrderResult作为创建响应事实继续使用稳定字符串，通用退款worker和发布器未因类型化重写。未知存储值通过fromStored映射INTERNAL_ERROR。聚焦74项通过；独立快照门禁运行中。

03最终快照常规170、MySQL25、可靠性13及Compose全部通过，零失败/错误/跳过；日志 `/tmp/booking-03-{test,mysql,reliability,compose}.log`。源码与最终暂存快照一致，仅补充验证记录。

### 04 暂存快照实际验收

2026-11-09，`/tmp/booking-04-snapshot-cc6ytsf8`：聚焦22、常规170、MySQL25及Compose全部通过，零失败/错误/跳过；日志 `/tmp/booking-04-{focused,test,mysql,compose}.log`。本阶段仅创建内部private步骤提取，不改消息路径；可靠性代码与03通过的13项基线相同。

### 05 暂存快照实际验收

2026-11-09，`/tmp/booking-05-snapshot-sgwopa8o`：常规173、MySQL34、可靠性14及Compose全部通过，零失败/错误/跳过，Maven串行离线执行。日志 `/tmp/booking-05-{test,mysql,reliability,compose}.log`。NOWAIT父锁探针证明测试中的callback、T2、refund请求/claim/complete/fail在等待子锁时已持有父支付锁；不把这些测试解释成证明所有可能交错均无死锁。故障回滚与真实Broker停机回调分别由AtomicityTest及PaymentConfirmationReliabilityIT覆盖。原父锁实现来自ea5bbeb，本阶段收拢回调和补齐验收，不重复改写。

### 06 深interface实际验收

独立快照 `/tmp/booking-06-snapshot-m2251go_` 已通过聚焦72、常规181、MySQL34，均零失败/错误/跳过，消息14项及Compose验收全部通过。当前事务同步器保存身份Map/Set，拒绝find/复制/跨事务Case；REQUIRES_NEW挂起恢复、库存/序号及关闭回滚有接口集成测试。coordination已无场次锁、分步库存操作及nextVisitNumber；Legacy markPaid保留至08。全部门禁通过，06 resolved。

06最终日志 `/tmp/booking-06-{focused,test,mysql,reliability,compose}.log`，代码与独立快照一致；Legacy专用markPaid及端点将在08整体删除。

### 07 暂存快照实际验收

2026-11-09，`/tmp/booking-07-snapshot-syfyevyo`：聚焦92、常规223、MySQL34、可靠性14及Compose全部通过，零失败/错误/跳过。新增42项纯Policy决策矩阵；日志 `/tmp/booking-07-{focused,test,mysql,reliability,compose}.log`。保留原短路、取时、退款原因、布尔及事件结果字符串；Legacy同步入口仅待08删除。

### 08 暂存快照实际验收

2026-11-09，`/tmp/booking-08-snapshot-ddye990u`：常规225、MySQL34、可靠性14及Compose全部通过，零失败/错误/跳过；日志 `/tmp/booking-08-{test,mysql,reliability,compose}.log`。六组旧测试经真实attempt/签名/Ingress/Outbox/Codec/Processor迁移，快速helper不冒充Broker传输。H2 JSON额外字符串编码只在helper按数据库类型适配，MySQL使用原v1文档；生产Codec、schema不变。删除全部已授权旧同步入口及无调用能力；三profile请求映射验收通过。

## 09 覆盖保留与替换对照

对照 [01 状态矩阵](state-matrix.md) 的全部入口与副作用，原有用例保留；08 只替换旧支付驱动方式，09 将查询时间的浅调用验证替换为真实响应断言。下表列最终测试表面，不以数量增长代替行为覆盖。

| 01 覆盖组 | 最终保留/替代证据（相对测试包 com.qsx.appoint） |
| --- | --- |
| 创建幂等、日期窗口、跨零点回滚、凭证快照 | appointment/BookingMidnightIntegrationTest、OnDemandBookingHttpTest、AppointmentLifecycleMySqlIT（首次场次、最后库存、幂等键、就诊人竞争） |
| 取消/过期/对账的错误优先级、布尔返回、原因和截止边界 | coordination/PaymentSucceededProcessorIntegrationTest、BookingLifecyclePolicyTest；appointment/AppointmentLifecycleIntegrationTest |
| Inbox重复、已确认新事件、已关闭补偿、迟到等于/晚于截止 | PaymentSucceededProcessorIntegrationTest；coordination/LatePaymentMySqlIT（不同事件+取消+过期竞争）；Compose真实迟到收款验收 |
| 释放/退款失败原子回滚、库存及序号单副作用 | 同上集成测试；appointment/AppointmentLifecycleInterfaceTest（事务契约）；PaymentConfirmationReliabilityIT 提交前故障和提交后ACK前断链 |
| 通知幂等、验签、金额币种、错误顺序、迟到失败与状态组合 | payment/application/PaymentNotificationPolicyTest、PaymentNotificationBoundaryTest；payment/PaymentNotificationMySqlIT |
| payment父锁、尝试T2/退款worker竞争和Outbox原子性 | payment/application/PaymentParentLockMySqlIT、PaymentNotificationAtomicityTest；payment/PaymentAttemptMySqlIT；具体路径见05记录 |
| workflow合法/历史/非法组合、查询归属和权限、兼容字符串 | bookingquery/application/BookingWorkflowProjectorTest、AppointmentResponseAssemblerTest、BookingQueryServiceTest；appointment/AppointmentQueryIntegrationTest；coordination/LifecycleLanguageTest |
| Outbox确认/return/崩溃重复/SKIP LOCKED、Broker停机 | payment/OutboxPublisherReliabilityIT；coordination/PaymentConfirmationReliabilityIT.realSignedCallbackCommitsWhileBrokerIsDownThenConfirmsAfterRecovery |
| 两次TTL重试、耗尽DLQ、确认失败不ACK及受控重放 | common/messaging/ConfirmedMessagePublisherTest、coordination/messaging/PaymentSucceededListenerTest、OutboxPublisherReliabilityIT、PaymentConfirmationReliabilityIT |
| 正式支付链路与旧入口删除 | FormalPaymentTestSupport组合真实attempt/签名/Ingress/Outbox/Codec/Processor；三profile映射测试；Compose承担真实Broker和HTTP全链路 |
| 模块依赖、深interface不泄漏Entity、query只读 | architecture/ModuleDependencyTest 的生产检查与故意违规fixture，包含继承公开方法、泛型Entity、Controller→Mapper、继承写接口/写注解/锁定读 |

快速helper不证明消息传输；H2不证明MySQL锁/唯一约束。架构SQL检查是现有静态Mapper的保守门禁，不宣称证明任意动态SQL语义。真实资金渠道、线上负载与历史异常自动修复不在本轮验收范围。

### 09 最终暂存快照实际验收

2026-11-09，暂存树 `990dc71f121d17c7441621ebce9490727d3d4753`，快照 `/tmp/booking-09-snapshot-8eo2fl37`：聚焦18、常规233、MySQL34、RabbitMQ4.3.2可靠性15全部通过，失败/错误/跳过均0；Compose正常支付、异步确认、取消退款及迟到支付释放退款通过并清理本次资源。日志 `/tmp/booking-09-final-{focused,test,mysql,reliability,compose}.log`。三层按profile分别统计，常规重复执行不重复相加。全部源码/构建/验收文件与快照相同；仅本节、issue状态和map验收结果在测试后补记。最终报告将在10交付。

### 10 文档暂存快照实际验收

2026-11-09，`/tmp/booking-10-snapshot-s8n0x13n`，树 `e6b226bd73fb291a0b88b4de9254188921234459`：32份Markdown、124个本地链接、围栏、12项issue字段/状态/依赖、两份依赖图及正式DTO字段检查通过；独立快照Compose真实链路通过，日志 `/tmp/booking-10-final-compose.log`。源码/测试/构建/脚本与09相同，复用09三层实测结果；仅验收状态和报告补记后再次导出最终文档快照检查。全部12项已resolved，未推送。当前结果详见 [最终报告](../../docs/verification-report.md)。
