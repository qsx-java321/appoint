# 03 类型化状态、关闭原因与退款原因

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。12项已于2026-11-09交付。后续三天窗口、visit、消息设计及测试整理已完成；正文草图、旧类名及当时数量仅解释该检查点。 当前能力与进度见[项目状态](../../../docs/project-status.md)。

Type: refactor
Triage: ready-for-agent
Status: resolved
Blocked by: 01

设计依据：[spec](../spec.md)，执行位置：[map](../map.md)。初始 Status 使用 triage 标签表示待认领；开始实施改为 claimed，验收完成改为 resolved。Triage 不代表依赖已解除。

## 背景和目标

已有 PaymentOrderStatus/PaymentAttemptStatus/RefundStatus 未成为 application 语言，预约仍是字符串。引入稳定业务类型，保持 persistence、HTTP 和事件中的既有字面值，使后续 Policy 只处理明确状态。

## 范围

- 新增 appointment.domain.AppointmentStatus、AppointmentCloseReason 和 payment.domain.RefundReason；复用现有支付 enum。
- 类型化 AppointmentLifecycleView、PaymentLifecycleView 等本轮 application 快照，逐个编译修正 coordination、Legacy 适配及返回 DTO。
- 将本轮持久化实体读取集中转换为明确 enum，保持 Entity/Mapper 字符串存储；退款 interface 改接收原因类型，并在写数据库/事件时映射原值。
- 已实施 02 时将 Projector 输入及 Assembler 本轮状态判断类型化；若 02 尚未实施，则本 issue 不建立它的依赖，02 后续落地时直接复用本 issue 类型。
- 回调/attempt/退款请求的本轮判断采用 enum；不要为此重写 OutboxPublisher 或通用 worker。

## 非范围

- 不引入全局 MyBatis TypeHandler，不改 schema、事件版本、JSON 字段类型或稳定值。
- 不把 WorkflowStatus 持久化，不把 refund reason 与 appointment close reason 混为一个 enum。
- 不新定义状态迁移或未知状态自动容错。

## 前置依赖

[01 基线行为、状态决策表与风险证据](01-baseline-state-matrix.md)。
只有全部前置任务 Status 为 resolved 才能开始；最终实施状态见本文 Status 和最新 Comments。推荐按map串行顺序执行，避免共享快照和调用点冲突。

## 需要修改的模块和主要文件

下列源码路径相对项目根目录；新增类型明确标注，implementation命名可沿用现有风格，不强制为改名搬文件。

- 新增 `src/main/java/com/qsx/appoint/appointment/domain/{AppointmentStatus,AppointmentCloseReason}.java` 和 `payment/domain/RefundReason.java`。
- `appointment/application/{AppointmentLifecycleView,AppointmentHoldServiceImpl,AppointmentLifecycleOperations,AppointmentLifecycleOperationsImpl}.java`
- `payment/application/{PaymentLifecycleView,PaymentLifecycleOperationsImpl,PaymentNotificationServiceImpl,PaymentAttemptTransaction,PaymentRefundOperations,DefaultPaymentRefundOperations}.java`
- `coordination/application/{DefaultBookingLifecycleCoordinator,DefaultPaymentSucceededProcessor,DefaultLegacyPaymentSimulationPrecheck}.java` 与 `payment/controller/LegacySimulatePaymentController.java` 的编译调用点。
- 已存在的 `bookingquery/application` 投影内部映射；`PaymentDomainEnumTest` 及新增状态/映射契约测试。

## 目标代码形态

Entity 字符串 → 单一显式转换方法 → 类型化 View/上下文 → enum 比较；Mapper 参数、公开 DTO 和 Outbox Factory 再转换为原稳定字符串。原因 enum 的值与当前数据库 reason 一一对应。不要把新增纯映射升格成跨模块服务或让两个写模块互相导入 domain 类型。

## 改造后的业务逻辑

仅替换表达方式。USER_CANCELLED 映射 CANCELLED，PAYMENT_EXPIRED 映射 EXPIRED；用户在到期后取消仍选择 EXPIRED，退款原因仍 APPOINTMENT_CANCELLED。过期/对账不可确认退款仍 APPOINTMENT_NOT_CONFIRMABLE；关闭收款回调为 LATE_PAYMENT；关闭已付对账为 RECONCILIATION_CLOSED_PAID。历史 CANCELLED_BY_HOSPITAL 可读取，不由新流程产生。

## 必须保持的不变量

- 01 固定的状态/优先级、库存、事件、HTTP、错误和事务均不变。
- 两个写模块仍不互相依赖；coordination 可使用稳定 domain enum 和 application DTO。
- Java enum 不改变数据库状态合法集合；未知状态转 INTERNAL_ERROR，不改为用户输入校验错误。
- DTO 仍使用原契约字段和序列化形式。

## 可验证验收条件

- 所有受影响 application/coordination 调用编译通过；不存在散落的本轮业务状态字面量比较；SQL/事件/审计稳定结果字符串明确保留。
- enum、数据库值和事件/HTTP 字面值有契约断言，未知数据库状态有内部错误断言。
- 四种退款原因全部保持原值；paidAt=null 和历史状态判断不丢失。
- 不改 Flyway、实体列类型及公开 HTTP DTO 类型；ModuleDependencyTest 通过。

## 应运行的测试

- `./mvnw -Dtest=PaymentDomainEnumTest,AppointmentLifecycleIntegrationTest,AppointmentQueryIntegrationTest,PaymentAttemptIntegrationTest,PaymentSucceededProcessorIntegrationTest,PaymentSucceededEventCodecTest,ModuleDependencyTest test`
- 新增 enum/映射测试按实际类名聚焦运行；`./mvnw test`。
- `./mvnw -Pmysql-it verify` 验证持久化字符串兼容。

## 独立交付与回退

状态无数据迁移；单独回退本 issue 的类型与所有调用点。若 02 已采用本 issue 类型，应先回退或调整依赖调用；不得保留一半字符串一半 enum 的不可编译状态。
后续任务已使用本任务成果时，先逆序撤销依赖，或做兼容性调整；独立回退指本任务完成时可整体撤销，不保证任意抽掉已被下游使用的提交仍可构建。任何回退不得删除已提交资金、Outbox/Inbox或退款数据。

## 完成状态清单

- [x] 状态与原因类型及集中映射完成
- [x] 全部调用点及 DTO 边缘转换完成
- [x] query 已存在部分类型化或交接说明完成
- [x] 持久化/HTTP/事件兼容验证
- [x] Comments/地图更新

## Comments

- 2026-11-08：根据源码复核后的spec创建。仅完成任务定义，未认领、未实施；本轮基线结果见map，不等同于本issue验收结果。 依赖尚未resolved，暂不可开始实施。

- 2026-11-09：01/11/12/02已分别验收。开始类型化生命周期快照、关闭及退款原因；持久化Entity/Mapper、公开HTTP DTO及事件v1保留字符串；回调Policy、attempt与query本轮状态判断迁移到enum。未知/null存储状态统一映射INTERNAL_ERROR。初轮常规168通过，新增契约测试后须独立快照重新验收。

- 2026-11-09：独立快照 `/tmp/booking-03-snapshot-_y2bra_r` 常规170、MySQL25、可靠性13及Compose真实正常/迟到支付链路全部通过，零失败/错误/跳过，Maven串行执行。退款四种原因、数据库/HTTP/事件稳定字面值、未知存储状态内部错误和架构约束均保留。03 resolved。

- 2026-11-09：10文档收口核对：本issue原阶段独立验收证据保留；最终集成代码a61b808的常规233/MySQL34/可靠性15及Compose全部通过，零失败/错误/跳过，详见 [最终验证报告](../../../docs/verification-report.md)。09覆盖对照未遗漏本issue保证；此处不以最终数量改写原阶段结果。
