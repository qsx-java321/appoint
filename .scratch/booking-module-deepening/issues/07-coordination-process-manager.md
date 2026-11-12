# 07 跨模块生命周期决策与流程编排收口

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。12项已于2026-11-09交付。后续三天窗口、visit、消息设计及测试整理已完成；正文草图、旧类名及当时数量仅解释该检查点。 当前能力与进度见[项目状态](../../../docs/project-status.md)。

Type: refactor
Triage: ready-for-agent
Status: resolved
Blocked by: 05, 06

设计依据：[spec](../spec.md)，执行位置：[map](../map.md)。初始 Status 使用 triage 标签表示待认领；开始实施改为 claimed，验收完成改为 resolved。Triage 不代表依赖已解除。

## 背景和目标

深 interface 已隐藏预约内部细节，剩余复杂度属于预约/支付组合规则。提取 coordination 内部纯 BookingLifecyclePolicy 和具名决策，使取消、过期、对账与成功消费成为可读编排，保持原短路和事务边界。

## 范围

- 提取 ExpirationDecision、CancellationDecision、PaymentConfirmationDecision；必要时 decision record 携带关闭原因/退款原因/原布尔返回。
- 简化 DefaultBookingLifecycleCoordinator 的取消、过期、reconcilePaidPending、ensureRefundForClosedPaid，以及 DefaultPaymentSucceededProcessor。
- 完整性检查仅在原入口范围内复用；每个生命周期决策在原锁后位置取一次 now。
- ReconciliationService 继续扫描候选并逐 ID 调用代理事务；只做必要调用适配。
- 保留事件校验、Inbox 插入、结果字符串及消费者提交后ACK。

## 非范围

- 不把 event 消费改为先查询当前 payment，不增加事件金额/用户真实性规则。
- 不统一所有入口“先锁支付再检查状态”，不统一过期与未到期对账。
- 保留 issue 11 已修复的迟到事件释放行为，不更改 canCancel 或 issue 12 的消费者重试策略。
- 不引入 Saga/规则引擎，也不再建一层 Coordinator。

## 前置依赖

[05 支付回调决策分离与支付锁序收敛](05-payment-notification-policy.md)；[06 预约与支付生命周期 interface 深度化](06-lifecycle-interfaces.md)。
只有全部前置任务 Status 为 resolved 才能开始；最终实施状态见本文 Status 和最新 Comments。推荐按map串行顺序执行，避免共享快照和调用点冲突。

## 需要修改的模块和主要文件

下列源码路径相对项目根目录；新增类型明确标注，implementation命名可沿用现有风格，不强制为改名搬文件。

- `src/main/java/com/qsx/appoint/coordination/application/{DefaultBookingLifecycleCoordinator,DefaultPaymentSucceededProcessor,ReconciliationService}.java`
- 同包新增 `BookingLifecyclePolicy.java`、决策 enum/record（保持 package-private；不跨子包强行公开）。
- `src/test/java/com/qsx/appoint/appointment/AppointmentLifecycleIntegrationTest.java`
- `src/test/java/com/qsx/appoint/coordination/PaymentSucceededProcessorIntegrationTest.java` 和新增同包 BookingLifecyclePolicyTest。
- `src/test/java/com/qsx/appoint/appointment/AppointmentLifecycleMySqlIT.java`、`coordination/PaymentConfirmationReliabilityIT.java`。

## 目标代码形态

用例读作：必要只读定位→appointment.lock→保留原提前返回/归属检查→必要 payment.lock→完整性→纯Policy→小 switch 调完整操作。取消决策表达 closeReason/refundReason/refundPending；不要用一个没有数据的 CLOSE_PAID 覆盖所有状态和原因。消费：validate→Inbox→appointment.lock→Policy→confirm/确保退款。

## 改造后的业务逻辑

过期非待支付或未到期先 false；待支付到期时未支付关闭并过期，PAID 且 paidAt<截止确认，paidAt缺失或到达截止关闭EXPIRED并以 APPOINTMENT_NOT_CONFIRMABLE 退款。对账不等待截止即可走已付判断。用户取消 pending+PAID 始终退款 APPOINTMENT_CANCELLED，按取消时刻取 CANCELLED/EXPIRED；confirmed+PAID 严格早于24小时截止才能取消。重复取消终态仅REFUND_PENDING返回true。事件已确认先ALREADY_CONFIRMED；待支付且event.paidAt<截止确认；待支付迟到事件关闭预约、释放暂占并退款，已关闭预约只确保 APPOINTMENT_NOT_CONFIRMABLE 退款。关闭已付对账只建立缺失退款，原原因不变。

## 必须保持的不变量

- 创建仍为同库本地事务，BookingCreationTransaction 已清晰无需额外拆层。
- policy 不访问 Mapper、Clock、Gateway，不产生副作用；参数提供明确时间。
- Inbox 与确认/退款同事务；失败回滚Inbox，重复eventId不再分号；不把eventId换成业务ID。
- 现有关联/归属错误、结果字符串、退款原因和HTTP 200/202选择保持。
- 复杂判断下沉不改变加锁时机、校验优先级和持久化条件。

## 可验证验收条件

- 决策表覆盖所有原分支，包括 paidAt=null、恰好支付截止/取消截止、重复不同eventId、已关闭PAID与已有退款、用户取消到期pending+PAID。
- 确认/退款/无动作/拒绝分别通过 module interface 集成用例验证，Policy 测试无Spring/数据库。
- 过期未到期不会因缺失payment新增异常；未到期已付对账仍可恢复；事件成功分支不新增支付读取。
- 故障后Inbox与库存/退款均不部分提交；原布尔返回影响的取消HTTP状态码不变。
- 迟到暂占释放和退款的 issue 11 行为回归通过。

## 应运行的测试

- 新增 BookingLifecyclePolicyTest。
- `./mvnw -Dtest=AppointmentLifecycleIntegrationTest,AppointmentExpirationJobTest,PaymentSucceededProcessorIntegrationTest,PaymentSucceededEventCodecTest,ModuleDependencyTest test`
- `./mvnw test`
- `./mvnw -Pmysql-it verify`
- `./mvnw -Preliability-it verify`。

## 独立交付与回退

仅回退Policy提取和协调实现改动及本次新增纯Policy测试，保留06深interface；无数据库迁移。不得通过重新引入低层库存方法完成回退。
后续任务已使用本任务成果时，先逆序撤销依赖，或做兼容性调整；独立回退指本任务完成时可整体撤销，不保证任意抽掉已被下游使用的提交仍可构建。任何回退不得删除已提交资金、Outbox/Inbox或退款数据。

## 完成状态清单

- [x] 生命周期Policy及有数据决策完成
- [x] 取消/过期/对账/消费编排迁移
- [x] 短路、错误顺序与退款原因回归
- [x] Inbox原子性及三层测试通过
- [x] 既有缺口独立记录与Comments更新

## Comments

- 2026-11-08：根据源码复核后的spec创建。仅完成任务定义，未认领、未实施；本轮基线结果见map，不等同于本issue验收结果。 依赖尚未resolved，暂不可开始实施。

- 2026-11-09：06已在1255737验收。新增package-private纯BookingLifecyclePolicy及具名取消/过期/确认决策，取消决策分别携带closeReason/refundReason/refundPending。协调用例保持原锁/归属/完整性与提前返回时机；未到期过期入口不锁支付，未到期已付对账仍处理，关闭取消仍不取新时钟，活跃决策原位置取一次now。事件仍validate→Inbox→锁预约→Policy→完整操作，不重读支付；11释放及12重试行为保留。首轮现有聚焦通过，纯矩阵及最终快照继续验收。

- 2026-11-09：独立暂存快照 `/tmp/booking-07-snapshot-syfyevyo` 聚焦92、常规223、MySQL34、可靠性14及Compose全部通过，零失败/错误/跳过。纯Policy无Spring/Mapper/Clock/Gateway依赖，取消布尔/原因、paidAt缺失及截止边界、事件终态、入口短路和Inbox原子性回归通过。07 resolved。

- 2026-11-09：10文档收口核对：本issue原阶段独立验收证据保留；最终集成代码a61b808的常规233/MySQL34/可靠性15及Compose全部通过，零失败/错误/跳过，详见 [最终验证报告](../../../docs/verification-report.md)。09覆盖对照未遗漏本issue保证；此处不以最终数量改写原阶段结果。
