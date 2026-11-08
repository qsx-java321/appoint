# 预约、支付、协调与查询模块深度化改造：目标设计与实施方案

Status: ready-for-agent

本文是 `appointment`、`payment`、`coordination`、`bookingquery` 四个模块的渐进式重构规格，已完成源码复核，并配套 [任务地图](map.md) 和 10 个实施 issue。本文描述目标结构、目标 interface、改造后的业务流程、实施阶段、测试门禁和回退策略；不直接授权改变外部业务契约。

## 2026-11-08 已确认的业务修订

用户已确认：迟到成功事件在预约仍待支付时，同事务将预约置为 EXPIRED、释放暂占、删除有效占用并确保退款；成功事件消费最多处理三次，间隔一秒，永久无效消息或重试耗尽进入 DLQ，转发确认成功后才 ACK。历史候选数据只读盘点，另行决定修复。上述决定取代本文原有将这两项列为待决或要求保留缺陷行为的描述；实施状态以任务地图和 issue 为准。

## 一、决策摘要

本次改造采用“深 module + 简洁 application 编排”的方向：

1. 主业务方法负责表达用例顺序、事务边界和少量清晰分支，读起来应接近业务流程说明。
2. 多字段、多状态、多时间条件的复杂判断下沉为纯 Policy 或值对象方法；Policy 不访问数据库、不调用外部系统。
3. 锁顺序、库存更新顺序、就诊序号分配、有效占用删除等预约内部知识收回 `appointment`。
4. 支付状态迁移、回调幂等、金额校验、Outbox 构造和退款事实继续归属 `payment`。
5. `coordination` 只拥有跨模块规则和流程编排，不使用写模块 Mapper、Entity 或 implementation。
6. `bookingquery` 继续作为跨写模块的只读投影，以 Projector 和 Assembler 分离状态组合与响应组装。
7. 保留模块化单体、MyBatis、同库事务、数据库条件更新、Transactional Outbox、RabbitMQ/Inbox、退款补偿与对账；不改造成微服务，不引入 Saga 框架或事件溯源。
8. 正式 HTTP 契约、数据库业务状态、库存不变量、支付截止语义和消息可靠性在重构阶段保持不变。唯一已授权的 HTTP 例外是 issue 08 删除 dev/test 的 `/simulate-success`；2026-11-08 用户明确同意，不影响正式接口。
9. 默认使用 package-private implementation；只有模块外 seam、真实多实现或外部系统依赖才新增 public interface。
10. 按完整用例逐步替换，禁止四个模块一次性重写。

## 二、背景与当前基线

### 2.1 当前模块职责

| 模块 | 当前职责 | 主要入口 |
| --- | --- | --- |
| `appointment` | 预约暂占、预约单、有效占用、库存、就诊序号、预约幂等 | `AppointmentHoldService`、`AppointmentLifecycleOperations`、`AppointmentCreationRequestService` |
| `payment` | 支付单、支付尝试、渠道回调、退款、Outbox、渠道 Adapter | `PaymentAttemptService`、`PaymentNotificationIngress`、`PaymentLifecycleOperations`、`PaymentRefundOperations` |
| `coordination` | 创建预约、取消、过期、支付成功消费、对账等跨模块编排 | `BookingCoordinator`、`BookingLifecycleCoordinator`、`PaymentSucceededProcessor` |
| `bookingquery` | 跨预约、支付、尝试、退款的只读组合投影 | `DefaultBookingQueryService` |

模块 ownership 和依赖方向已经通过 `src/test/java/com/qsx/appoint/architecture/ModuleDependencyTest.java` 约束，是本次改造必须保留的基础。

### 2.2 当前值得保留的设计

- `BookingCreationTransaction` 已经是清晰的 application 编排：幂等登记、预约暂占、支付单创建和回执完成在同一事务中执行。
- `PaymentAttemptServiceImpl` 已将本地 T1、事务外渠道调用和本地 T2 分开，避免外部调用长期占用数据库事务。
- 支付回调将资金事实、回调审计和 `PaymentSucceeded` Outbox 原子提交。
- `PaymentSucceededProcessor` 将 Inbox、库存确认和预约确认原子提交，消费者在事务成功后 ACK。
- 预约、支付写模块互不依赖对方的 Mapper、Entity 和 implementation。
- `bookingquery` 集中承载跨写模块 SQL，且只读。
- 条件 UPDATE、唯一约束、`FOR UPDATE` 和统一锁序共同维护并发正确性。
- MySQL、RabbitMQ Testcontainers 和 ArchUnit 已覆盖关键约束与故障场景。

### 2.3 当前主要问题

#### 2.3.1 appointment 生命周期 interface 偏浅

`AppointmentLifecycleOperations` 暴露了 `lockSchedule`、`lockAppointment`、`confirmInventory`、`confirmAppointment`、`releaseHeldInventory` 等低层方法。调用者必须知道：

- 场次必须先于预约单加锁；
- 确认库存与确认预约必须处于同一事务；
- `nextVisitNumber` 的读取和递增方式；
- 关闭预约时还要删除有效占用；
- 释放暂占库存和释放确认库存是不同操作。

这使 interface 接近 implementation 的复杂度，降低了 module depth。

#### 2.3.2 coordination 同时承担决策和底层步骤

`DefaultBookingLifecycleCoordinator` 同时处理数据查找、锁定、归属完整性、字符串状态组合、截止时间、库存更新、退款和日志。跨模块规则确实属于 coordination，但预约内部更新顺序不应由 coordination 掌握。

#### 2.3.3 payment 回调职责过密

`PaymentNotificationServiceImpl` 同时承担：

- 通知幂等检查；
- 支付尝试和支付单锁定；
- 金额、币种、渠道订单校验；
- 回调状态决策；
- 支付尝试与支付单更新；
- 回调审计构造；
- `PaymentSucceeded` Outbox 构造；
- 迟到支付退款触发。

状态判断难以脱离数据库进行完整决策表测试。

#### 2.3.4 bookingquery 映射和流程投影混合

`DefaultBookingQueryService.toResponse` 同时完成完整性校验、状态组合、权限派生、DTO 嵌套构造、证件脱敏和时间判断。查询没有写事务问题，但认知负担集中在一个方法中。

#### 2.3.5 状态枚举未成为 application 语言

`PaymentOrderStatus`、`PaymentAttemptStatus`、`RefundStatus` 已存在，但 application 和 coordination 仍大量比较字符串。预约单尚无统一的 `AppointmentStatus` 类型。状态组合规则因此分散，非法组合只能在运行时发现。

#### 2.3.6 兼容支付路径模糊正式流程

`LegacySimulatePaymentController` 仅在 dev/test 注册。它不是纯转发：`UNPAID` 时依次预检、创建正式 attempt、生成签名回调、调用统一通知入口，再直接调用 `PaymentSucceededProcessor`，最后调用同步协调兜底并返回 HTTP 202。另有 application 测试直接调用 `simulatePaymentSuccess`，会经过 `markPaid` 构造 `compat-*` 成功尝试。两条兼容路径必须分别迁移，不能把“端点返回 202”误认为已完全异步。后续删除范围已获用户确认。

### 2.4 2026-11-08 源码复核与证据

本轮复核基线为 `9d20e301db5896c08896730a91a09f4a1b8cb058`。旧支付改造 spec/map 的“当前实现”表是 2026-10-29 的历史设计基线，不能当作本分支现状；其 16 个 issue 已记录 resolved。当前 Flyway 为 V1–V9，四场次、统一设置、按需生成和固定创建后 15 分钟截止以 [ADR 0005](../../docs/adr/0005-four-session-on-demand-booking.md) 为准，ADR 0001 的更早截止/同步支付和 ADR 0004 的停诊描述已被局部取代。

以下路径均相对 `src/`；结论来自实现和测试正文，而非仅搜索类名：

| 证据 | 核实结果 | 任务落点 |
| --- | --- | --- |
| `main/java/com/qsx/appoint/appointment/application/AppointmentHoldServiceImpl.java`、`coordination/application/BookingCreationTransaction.java` | 外层事务原子创建；幂等登记→设置锁→场次生成/锁→就诊人锁→最终日期复验→条件占号→快照和占用→支付单→幂等完成；不是只锁场次 | 01、04 |
| `appointment/application/AppointmentLifecycleOperations{,Impl}.java` | 库存和预约分步更新，调用者传递 `nextVisitNumber`；当前 implementation 没有 MANDATORY 防护 | 06 |
| `payment/application/PaymentNotificationServiceImpl.java` | 先 attempt 锁，再通知锁下复验，再 payment 锁；金额用 compareTo，paidAt 是服务端处理时间；同事务 Outbox/审计/迟到退款 | 01、05 |
| `payment/application/PaymentAttemptTransaction.java`、`DefaultPaymentRefundOperations.java`、`DefaultRefundWorker.java` | T1 是 payment→attempt；T2 失败是 attempt→payment；退款请求 payment→refund→成功 attempt；worker 完成是 refund→payment，存在与目标统一锁序相反的路径 | 01、05 |
| `coordination/application/DefaultBookingLifecycleCoordinator.java` | 过期先判断预约待支付及已到期，再锁支付；对账不要求已到期；`paidAt=null` 按不可确认退款；取消待确认已支付单也受取消时刻影响 CLOSED 状态选择 | 01、07 |
| `coordination/application/DefaultPaymentSucceededProcessor.java`、`messaging/PaymentSucceededListener.java` | Inbox 同事务；事件驱动确认不重读支付；已确认先幂等返回；迟到待支付仅退款，不关闭预约；提交后 ACK，非 VALIDATION_ERROR 异常当前 NACK/requeue | 01、07、09 |
| `bookingquery/application/DefaultBookingQueryService.java`、`persistence/BookingQueryMapper.java` | workflow 仅取预约/支付组合，不按退款状态改义；latest attempt 按最大 id；权限字段存在与写入口不同的既有语义 | 01、02 |
| `payment/controller/LegacySimulatePaymentController.java`、`coordination/application/DefaultLegacyPaymentSimulationPrecheck.java` | 旧 HTTP 包装与直接同步 application 路径共存；预检实际位于 coordination 包 | 08 |
| `test/java/com/qsx/appoint/architecture/ModuleDependencyTest.java` | 已限制写模块互依赖、协调层访问 Mapper/Entity 和 query Mapper 写注解；尚未限制所有 application implementation、返回 Entity 或所有 Controller 的 Mapper 访问 | 09 |
| `test/java/com/qsx/appoint/appointment/AppointmentLifecycleMySqlIT.java`、`payment/PaymentNotificationMySqlIT.java` | 真实回调竞争与若干旧同步路径测试共存；不存在当前停诊竞争用例 | 01、08、09 |
| `test/java/com/qsx/appoint/payment/OutboxPublisherReliabilityIT.java`、`coordination/PaymentConfirmationReliabilityIT.java` | 6+3 项覆盖发布崩溃窗口、confirm/return、SKIP LOCKED、Broker 恢复、重复 Inbox、坏消息 DLQ 和退款失败；Broker 用例是预置 Outbox，不能声称覆盖“Broker 停机时真实回调提交”或消费者所有崩溃窗口 | 01、05、09 |

### 2.5 已有缺口与本轮重构边界

1. **支付锁序偏差**：上述回调、T2 失败、退款 worker 存在反向锁路径；这是静态发现的潜在循环等待风险，不宣称已复现死锁。issue 01 建立确定性竞争证据，issue 05 在保留数据库业务语义的前提下统一支付父记录先锁，并覆盖全部相关写路径。它是维持既有架构约束的必要工作，不扩张成支付重写。
2. **迟到事件暂占未释放（已确认修复）**：改造前存在 `PENDING_PAYMENT + REFUND_PENDING` 且 held=1 的缺口。issue 11 将该分支改为同事务过期、释放、删除占用和退款；历史记录只读盘点、另行修复。
3. **消息失败分类（已确认修复）**：issue 12 对成功事件一般失败采用最多三次处理和一秒延迟，耗尽进入 DLQ；无效消息直接进入 DLQ。转发需确认成功后 ACK。

4. **支付关闭与尝试关闭不同**：`closeUnpaid` 只更新 payment 为 CLOSED，不把 PROCESSING attempt 标 CLOSED，也不调用渠道。现有回调正依赖 PROCESSING+CLOSED 接收迟到收款。此次不得擅自关闭 attempt、接受 CLOSED attempt 成功，或引入渠道调用。
5. **兼容查询权限**：待支付 UNPAID/PAYING 在扫描前 `canCancel=true`（即使已到期）；PENDING_PAYMENT+PAID 时 `canCancel=false`，但写入口允许取消退款。查询和写入口暂不合并权限 Policy，也不借重构修正该差异。

## 三、目标、非目标与完成定义

### 3.1 目标

1. 每个顶层 application 方法能按顺序读出业务用例，复杂分支不与持久化细节交织。
2. `appointment` 对 coordination 暴露完整预约生命周期操作，而不是库存表级步骤。
3. `payment` 对 coordination 暴露完整支付生命周期和退款用例，而不是支付 Mapper 级步骤。
4. 跨模块状态组合集中到 coordination 内部纯 Policy。
5. application 和 coordination 使用类型化状态、关闭原因、退款原因和决策结果。
6. `bookingquery` 将流程状态投影与响应组装拆开，并保持只读。
7. 测试主要通过 module interface 和外部可观察行为验证；纯 Policy 使用决策表测试。
8. 删除已完成使命的 Legacy 模拟支付路径。
9. 除已授权的迟到事件修复和 dev/test 旧模拟端点删除外，正式 HTTP、数据库业务语义、消息事件版本和错误码保持兼容。

### 3.2 非目标

- 不拆分微服务，不增加独立部署单元。
- 不更换 Spring Boot、MyBatis、MySQL、RabbitMQ。
- 不引入通用工作流、Saga、状态机或规则引擎框架。
- 不引入事件溯源，不将 `bookingquery` 改为异步物化读库。
- 不接入真实微信或支付宝渠道。
- 不改变支付截止时间为创建后 15 分钟且使用半开区间的规则。
- 不改变当前 `paidAt` 的时间来源；重构阶段继续使用本系统确认回调时的服务端时间。若要改为渠道发生时间，必须另立业务变更 issue。
- 不改变现有表结构或 Flyway 历史文件；类型化在 Java 映射处完成，不允许以 enum 为由添加迁移。
- 不顺带重构医院、科室、医生、就诊人等无关 CRUD。

### 3.3 可验证完成定义

- `AppointmentLifecycleOperations` 的低层锁和库存步骤被更深的业务 interface 替代。
- coordination 不读取或传递 `nextVisitNumber`，不分别调用“确认库存”和“确认预约”。
- 本轮触及的 application 用例和 coordination 使用类型化业务状态；HTTP DTO、事件 JSON、审计结果和 Mapper SQL 仍保存稳定字符串。映射集中，不强制重写无关 worker/发布器状态。
- 预约关闭、支付回调、过期与确认的复杂组合规则都有纯 Policy 决策表测试；已知缺口单独标识，不宣称已修复。
- `PaymentNotificationServiceImpl` 主方法只保留幂等短路、上下文加载、决策和执行四类步骤。
- `DefaultBookingQueryService` 主方法只保留读取、归属校验和响应组装。
- 生产 profile 继续不注册 MockPay；Legacy 模拟成功 Controller 和对应 interface 最终删除。
- `./mvnw test`、`./mvnw -Pmysql-it verify` 和 RabbitMQ 可靠性测试全部通过。
- ArchUnit 继续阻止写模块交叉依赖，并适度新增 implementation/Entity 泄漏约束；状态字符串用聚焦静态检查与 review 验证，不依赖 ArchUnit 分析字符串常量。
- 文档、HTTP 示例和验证报告与最终实现一致。

## 四、设计原则与拆分判据

### 4.1 顶层方法保留什么

顶层 application 方法保留：

- 用例步骤顺序；
- 事务边界；
- 已经命名清楚的结果分支；
- 错误传播与必要补偿；
- 返回结果组装的最终调用。

示例目标形态：

```java
@Transactional
public LifecycleResult expireIfDue(Long appointmentOrderId) {
    AppointmentLifecycleCase appointment = appointments.lock(appointmentOrderId);
    if (appointment == null) return LifecycleResult.NOT_FOUND;

    if (!appointment.pendingPayment()) return LifecycleResult.NO_ACTION;
    LocalDateTime serverTime = now();
    if (!appointment.paymentDueAt(serverTime)) return LifecycleResult.NO_ACTION;
    PaymentLifecycleCase payment = payments.lockByAppointment(appointmentOrderId);
    integrity.requireMatching(appointment, payment);
    ExpirationDecision decision = policy.decideExpiration(appointment, payment, serverTime);

    return switch (decision) {
        case NO_ACTION -> LifecycleResult.NO_ACTION;
        case CONFIRM -> confirm(appointment);
        case CLOSE_UNPAID -> closeUnpaid(appointment, payment);
        case REFUND_LATE_PAYMENT -> closeAndRefund(appointment, payment);
    };
}
```

### 4.2 复杂规则何时下沉

满足任一条件时提取为 Policy、值对象方法或有领域名称的内部方法：

- 同时比较多个状态或时间字段；
- 分支形成可列举的业务决策表；
- 规则在两个以上用例重复；
- 规则需要脱离数据库进行穷举测试；
- 方法名可以准确表达被隐藏的业务知识。

### 4.3 何时提取内部 module

- 只有一个调用者、无独立依赖：优先 private 方法。
- 纯计算且需要独立测试：package-private Policy/Factory/Projector。
- 拥有独立 Mapper、Clock、ObjectMapper 或外部依赖：package-private application implementation。
- 模块外调用或多 Adapter：public interface。
- 不为单实现 Factory、Assembler、Policy 创建形式化 interface。

### 4.4 数据库仍是并发最终防线

纯 Policy 只决定业务动作，不替代：

- `FOR UPDATE`；
- 条件 UPDATE；
- 唯一约束；
- Outbox/Inbox 原子提交；
- MySQL 死锁检测与事务回滚。

Java 判断用于清晰表达业务，数据库约束用于并发收敛，两者必须同时保留。

## 五、目标模块关系

```text
HTTP / Job / Rabbit Listener
           │
           ▼
coordination.application  ──────────────┐
           │                            │
           ├── appointment.application │
           └── payment.application     │ 跨模块写流程
                                        │
appointment persistence ─┐              │
payment persistence ─────┼─ bookingquery.application ── HTTP 查询
schedule persistence ────┘

payment.application ── PaymentGateway port ── Mock/未来真实渠道 Adapter
payment.application ── OutboxMessagePublisher ── RabbitMQ Adapter
```

依赖规则：

1. `appointment` 与 `payment` 不互相依赖。
2. `coordination` 可依赖两个写模块的 public application interface 和稳定 DTO/record，不依赖其 Mapper、Entity、implementation。
3. 写模块业务层不依赖 `coordination` 或 `bookingquery`。HTTP Adapter 可以调用协调入口（例如现有支付兼容 Controller）；不能把此合法入口依赖误判为业务层反向依赖。
4. `bookingquery` 可以读取多个模块的表，但不声明写操作。
5. Controller、Job、Listener 只调用相应 application interface，不组装跨模块业务规则。

## 六、类型化业务语言

### 6.1 预约状态

新增：

```java
public enum AppointmentStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    CANCELLED_BY_HOSPITAL
}
```

`CANCELLED_BY_HOSPITAL` 仅用于历史兼容，新流程不产生。

### 6.2 原因类型

新增或收敛：

```java
public enum AppointmentCloseReason {
    USER_CANCELLED,
    PAYMENT_EXPIRED
}

public enum RefundReason {
    APPOINTMENT_CANCELLED,
    LATE_PAYMENT,
    APPOINTMENT_NOT_CONFIRMABLE,
    RECONCILIATION_CLOSED_PAID
}
```

数据库和事件中的稳定字符串仍保持原值，转换集中到 Mapper 参数或事件 Factory。

### 6.3 跨模块决策

在 `coordination` 内部新增明确结果类型：

```java
enum ExpirationDecision {
    NO_ACTION,
    CONFIRM,
    CLOSE_UNPAID,
    REFUND_LATE_PAYMENT
}

enum CancellationDecision {
    NO_ACTION,
    CLOSE_UNPAID,
    CLOSE_PAID_AND_REFUND,
    CANCEL_CONFIRMED_AND_REFUND,
    REJECT_DEADLINE
}

enum PaymentConfirmationDecision {
    CONFIRM,
    ALREADY_CONFIRMED,
    REFUND_REQUIRED
}
```

决策枚举是内部业务语言，不持久化，不加入公共 HTTP DTO。

## 七、appointment 目标设计

### 7.1 对外 interface

保留创建暂占 interface，但明确其高层语义：

```java
public interface AppointmentHoldService {
    AppointmentHold createHold(CreateAppointmentHoldCommand command);
}
```

将 `AppointmentLifecycleOperations` 替换为更深的 interface，目标名称可使用 `AppointmentLifecycle`：

```java
public interface AppointmentLifecycle {
    AppointmentLifecycleCase find(Long appointmentOrderId);

    /** 固定按场次→预约单加锁，并返回一致快照。 */
    AppointmentLifecycleCase lock(Long appointmentOrderId);

    /** 暂占库存转确认库存、分配序号并确认预约。 */
    AppointmentConfirmation confirm(AppointmentLifecycleCase appointment);

    /** 释放暂占、关闭预约并删除有效占用。 */
    void closePending(
            AppointmentLifecycleCase appointment,
            AppointmentCloseReason reason,
            LocalDateTime closedAt);

    /** 释放确认库存、取消预约并删除有效占用。 */
    void cancelConfirmed(
            AppointmentLifecycleCase appointment,
            LocalDateTime cancelledAt);
}
```

目标是将以下方法合并：

| 当前方法 | 目标方法 |
| --- | --- |
| `lockSchedule` + `lockAppointment` | `lock` |
| `confirmInventory` + `confirmAppointment` | `confirm` |
| `releaseHeldInventory` + `closePendingAppointment` | `closePending` |
| `releaseConfirmedInventory` + `cancelConfirmedAppointment` | `cancelConfirmed` |

### 7.2 生命周期快照

```java
public record AppointmentLifecycleCase(
        Long appointmentOrderId,
        Long userId,
        Long scheduleId,
        AppointmentStatus status,
        LocalDateTime paymentExpiresAt,
        LocalDate appointmentDate,
        LocalTime startTime,
        Integer visitNumber) {

    public boolean pendingPayment() {
        return status == AppointmentStatus.PENDING_PAYMENT;
    }

    public boolean confirmed() {
        return status == AppointmentStatus.CONFIRMED;
    }

    public boolean inactive() {
        return status == AppointmentStatus.CANCELLED
                || status == AppointmentStatus.EXPIRED
                || status == AppointmentStatus.CANCELLED_BY_HOSPITAL;
    }

    public boolean paymentDueAt(LocalDateTime time) {
        return !time.isBefore(paymentExpiresAt);
    }

    public LocalDateTime cancellationDeadline() {
        return LocalDateTime.of(appointmentDate, startTime).minusHours(24);
    }
}
```

该类型只暴露 coordination 作跨模块决策所需的稳定事实，不暴露 `availableCapacity`、`heldCapacity`、`nextVisitNumber` 等内部库存细节。

`lock` 和三个写操作必须经 Spring 代理以 `Propagation.MANDATORY` 加入外层事务。`lock(id)` 内部先只读定位场次 ID（不存在返回 null），再锁场次、锁预约并复验关联；现有场次不存在/历史空 session 的错误保持 RESOURCE_NOT_FOUND，不新增 OPEN 前置过滤改变关闭路径。写操作只接受当前事务中 `lock` 返回的 Case，不接受 `find` 结果或跨事务缓存；implementation 复验所需状态/关联并保留条件更新。`confirm` 在已持有的场次锁内取得下一序号，先库存转换再预约确认，返回序号；`closePending` 先释放暂占再关闭和删除占用；`cancelConfirmed` 先释放确认名额再取消和删除占用。任何一步失败整体回滚。操作失败沿用现有 INTERNAL_ERROR，不能统一改成可重试错误。重复调用由协调决策短路，不要求盲目重复写入成功。

### 7.3 `AppointmentHoldServiceImpl` 内部结构

目标主方法：

```java
@Transactional(propagation = Propagation.MANDATORY)
public AppointmentHold createHold(CreateAppointmentHoldCommand command) {
    BookingRequest request = bookingPolicy.requireValid(command, today());
    Schedule schedule = scheduleProvisioning.lockOrCreate(request);
    Patient patient = patientAccess.requireOwnedAndEnabled(
            request.patientId(), request.userId());

    // 必须在等待设置/场次/就诊人锁之后、实际占号之前重新读取日期。
    LocalDate acceptanceDate = bookingPolicy.requireStillBookable(request, today());
    inventory.holdOne(schedule.id(), acceptanceDate);

    AppointmentOrder order = appointmentFactory.create(request, schedule, patient);
    appointmentOrderMapper.insert(order);
    activeClaims.claim(schedule.id(), order, patient, request.createdAt());

    return AppointmentHold.from(order);
}
```

建议的内部职责：

- `BookingPolicy`：场次类型、D+1～D+7、最终写入点日期校验；保持纯函数，由调用方传入新读取日期，不能缓存请求开始时的 today。
- `ScheduleProvisioning`：先确认医生存在，再锁统一设置，按需生成场次、重读并锁定、验证费用和 OPEN 状态；设置锁不得省略或移到场次锁之后。
- `PatientAccess`：锁定就诊人、归属和启用状态。
- `AppointmentFactory`：预约编号、快照、费用、截止时间和初始状态。
- `ActiveClaims`：插入有效占用并翻译唯一键冲突。
- `Inventory` 不必成为 public interface；可以是 application implementation 内部命名方法或 package-private 类，最终仍使用 `ScheduleMapper` 条件 UPDATE。

### 7.4 改造后预约创建逻辑

```text
接收规范化创建命令
→ 纯规则校验日期和场次类型
→ 锁定设置并按需生成/锁定场次
→ 锁定并校验就诊人
→ 最终占号前重新读取 Clock 日期并复验 D+1～D+7
→ 条件 UPDATE 暂占库存
→ Factory 生成不可变业务快照
→ 保存预约单
→ 建立有效占用
→ 返回 AppointmentHold
```

所有步骤继续参加 `BookingCreationTransaction` 的外层事务，任一步失败都回滚场次、库存、预约单、支付单和幂等记录。

## 八、payment 目标设计

### 8.1 保留的对外 interface

继续保留：

- `PaymentOrderCreationService`；
- `PaymentAttemptService`；
- `PaymentNotificationIngress`；
- `PaymentRefundOperations`；
- `PaymentGateway` port；
- Outbox 发布 port。

`PaymentAttemptServiceImpl` 已符合“本地 T1 → 外部调用 → 本地 T2/补偿”的目标，不做无必要的结构重写。

### 8.2 支付生命周期 interface

将 `PaymentLifecycleOperations` 类型化并去除 Legacy 专用能力：

```java
public interface PaymentLifecycle {
    PaymentLifecycleCase find(Long paymentOrderId);

    /** 按预约单锁定唯一支付单。 */
    PaymentLifecycleCase lockByAppointment(Long appointmentOrderId);

    /** 仅允许 UNPAID/PAYING 关闭，内部保留条件 UPDATE。 */
    void closeUnpaid(PaymentLifecycleCase payment);
}
```

`markPaid` 只服务旧模拟成功路径，应随 Legacy 删除；正式支付成功只能由验签后的回调推进。当前未使用的 `refundPaid` 删除，退款统一通过 `PaymentRefundOperations.requestFullRefund`。阶段 5 的过渡 interface 仍保留 Legacy `markPaid` 至 issue 08；`closeUnpaid` 只关闭支付单，不能改变 2.5 节尝试语义。

```java
public record PaymentLifecycleCase(
        Long paymentOrderId,
        String paymentNo,
        Long appointmentOrderId,
        Long userId,
        PaymentOrderStatus status,
        LocalDateTime paidAt) {

    public boolean unpaidOrPaying() {
        return status == PaymentOrderStatus.UNPAID
                || status == PaymentOrderStatus.PAYING;
    }

    public boolean paid() {
        return status == PaymentOrderStatus.PAID;
    }

    public boolean closed() {
        return status == PaymentOrderStatus.CLOSED;
    }

    public boolean refundingOrRefunded() {
        return status == PaymentOrderStatus.REFUND_PENDING
                || status == PaymentOrderStatus.REFUNDED;
    }

    public boolean paidBefore(LocalDateTime deadline) {
        return status == PaymentOrderStatus.PAID
                && paidAt != null
                && paidAt.isBefore(deadline);
    }
}
```

### 8.3 支付回调内部目标结构

对外仍保持一个处理入口。内部按四步组织：

```java
@Transactional
public PaymentNotificationResponse handle(
        PaymentChannel channel,
        VerifiedPaymentNotification notification,
        String payloadHash) {

    DuplicateNotification duplicate = duplicates.find(channel, notification, payloadHash);
    if (duplicate.found()) return duplicate.response();

    PaymentNotificationContext context = contexts.lock(channel, notification);
    PaymentNotificationDecision decision = policy.decide(context, notification);

    return executor.execute(context, notification, payloadHash, decision);
}
```

内部类型：

```java
enum PaymentNotificationDecision {
    SUCCEED,
    FAIL,
    RECORD_LATE_PAYMENT_AND_REFUND,
    ALREADY_SUCCEEDED,
    IGNORE_LATE_FAILURE,
    REJECT_UNKNOWN_ORDER,
    REJECT_AMOUNT_OR_CURRENCY,
    REJECT_STATE
}
```

职责划分：

- `PaymentNotificationContextLoader`：先只读由 channel/providerOrderNo 定位 payment ID，再锁 payment、重读并锁 attempt、校验关联，最后锁下复验通知幂等；不能沿用当前 attempt→payment 的反向顺序。未知订单仍可审计拒绝，关联缺失仍 INTERNAL_ERROR。
- `PaymentNotificationPolicy`：纯判断，不写数据库。
- `PaymentNotificationExecutor`：根据决策执行条件 UPDATE、审计、Outbox 和退款请求。
- `PaymentEventFactory`：集中构造版本化 `PaymentSucceeded v1` JSON。
- `PaymentNotificationAuditFactory`：集中构造回调审计记录。

这些均为 payment 内部 implementation，不暴露给其他模块。

幂等需要两次检查：第一次只读用于快速返回；第二次在 payment/attempt 串行化后以锁定读取复验。同通知同 payload 不得重复推进；同 ID 不同 hash 仍 IDEMPOTENCY_CONFLICT；未知订单并发插审计的唯一冲突仍 RETRYABLE_CONCURRENCY_ERROR。不同通知 ID 的重复成功、退款中的重复成功、FAILED/CLOSED attempt 成功等，均按当前决策表返回，不能统一归为 ALREADY_SUCCEEDED。

只在本系统首次接受成功时保存 PaymentSucceeded v1，含现有 envelope、providerTransactionNo 等字段、数字/时间格式；late 分支仍要求 attempt=PROCESSING 且 payment=CLOSED，并同时写退款单及 RefundRequested。`paidAt` 为锁后服务端处理时间（截断秒），不是渠道 occurredAt，也不是数据库提交时间。审计 receivedAt/processedAt 当前另取时钟；首次提取保留，不以“统一 now”为由改变字段来源。

### 8.4 改造后回调业务逻辑

```text
Adapter 验签并转换稳定通知
→ 通知 ID + payload hash 幂等短路
→ 只读定位后按 payment→attempt 加锁，锁下再次校验通知幂等
→ Policy 根据金额、币种、attempt/order 状态作决策
→ Executor 原子推进支付状态
→ 第一次成功写 PaymentSucceeded Outbox
→ 保存审计
→ 迟到支付同时幂等建立退款事实
```

资金事实、通知审计和 Outbox 仍必须处于同一事务；RabbitMQ 可用性仍不得影响回调落库。

## 九、coordination 目标设计

### 9.1 角色

coordination 是 Process Manager，拥有：

- 创建预约与支付单的跨模块编排；
- 取消、过期和对账的跨模块状态组合规则；
- `PaymentSucceeded` 消费后的预约确认或退款决策；
- Inbox 幂等和跨模块事务边界。

它不拥有：

- 预约库存具体列和更新顺序；
- 支付 Mapper 状态迁移；
- 渠道报文和签名；
- 查询 DTO 组装；
- Outbox JSON 序列化细节。

### 9.2 生命周期 Policy

新增 package-private `BookingLifecyclePolicy`，至少提供：

```java
ExpirationDecision decideExpiration(
        AppointmentLifecycleCase appointment,
        PaymentLifecycleCase payment,
        LocalDateTime now);

CancellationDecision decideCancellation(
        AppointmentLifecycleCase appointment,
        PaymentLifecycleCase payment,
        LocalDateTime now);

PaymentConfirmationDecision decidePaymentConfirmation(
        AppointmentLifecycleCase appointment,
        PaymentSucceededEvent event);
```

Policy 必须覆盖的核心规则：

| 场景 | 预约事实 | 支付事实 | 决策 |
| --- | --- | --- | --- |
| 未到截止 | 待支付 | 任意支付事实 | 过期入口在锁支付前直接返回 false，不新增完整性校验 |
| 到期未付 | 待支付 | 未支付/支付中 | 关闭预约和支付，释放暂占 |
| 截止前已付 | 待支付 | `PAID` 且 `paidAt < expiresAt` | 确认预约 |
| 到期后已付 | 待支付 | `PAID` 且 `paidAt == null` 或 `paidAt >= expiresAt` | 过期/对账关闭为 EXPIRED，退款原因 APPOINTMENT_NOT_CONFIRMABLE |
| 重复成功事件 | 已确认 | 事件结构有效 | ALREADY_CONFIRMED，不新增支付重读或归属校验 |
| 已关闭后收款 | 已取消/过期/历史医院取消 | 成功事件 | 确保退款 APPOINTMENT_NOT_CONFIRMABLE，不恢复预约 |
| 迟到成功事件 | 待支付 | event.paidAt >= expiresAt | 关闭为 EXPIRED、释放暂占并确保退款（issue 11） |
| 用户取消待确认支付 | 待支付 | PAID | 按取消时刻选择 CANCELLED/EXPIRED，始终退款 APPOINTMENT_CANCELLED，不先确认 |
| 用户取消未支付 | 待支付 | UNPAID/PAYING | 按取消时刻选择 CANCELLED/EXPIRED，关闭支付 |
| 重复取消终态 | 已关闭 | CLOSED/REFUND_PENDING/REFUNDED | 不重复释放，布尔返回仅 REFUND_PENDING 为 true，其他组合 INTERNAL_ERROR |
| 已确认及时取消 | 已确认 | 已支付且未到取消截止 | 取消并退款 |
| 已确认逾期取消 | 已确认 | 已支付且到达取消截止 | 拒绝 |

### 9.3 `DefaultBookingLifecycleCoordinator` 目标形态

每个 public 方法按其现有短路时机遵循（不能强制所有入口都锁支付）：

```text
只读定位必要 ID
→ appointment.lock（内部完成场次→预约锁）
→ 必要时 payment.lockByAppointment
→ 完整性校验
→ Policy 决策
→ 简单 switch 编排完整模块操作
→ 返回显式结果
```

示例：

```java
@Transactional
public boolean expireIfDue(Long appointmentOrderId) {
    AppointmentLifecycleCase appointment = appointments.find(appointmentOrderId);
    if (appointment == null) return false;

    appointment = appointments.lock(appointmentOrderId);
    if (!appointment.pendingPayment()) return false;
    LocalDateTime serverTime = now();
    if (!appointment.paymentDueAt(serverTime)) return false;
    PaymentLifecycleCase payment = payments.lockByAppointment(appointmentOrderId);
    integrity.requireMatching(appointment, payment);

    return switch (policy.decideExpiration(appointment, payment, serverTime)) {
        case NO_ACTION -> false;
        case CONFIRM -> {
            appointments.confirm(appointment);
            yield true;
        }
        case CLOSE_UNPAID -> {
            appointments.closePending(appointment, PAYMENT_EXPIRED, serverTime);
            payments.closeUnpaid(payment);
            yield true;
        }
        case REFUND_LATE_PAYMENT -> {
            appointments.closePending(appointment, PAYMENT_EXPIRED, serverTime);
            refunds.requestFullRefund(payment.paymentOrderId(), APPOINTMENT_NOT_CONFIRMABLE);
            yield true;
        }
    };
}
```

实际实现必须在锁后复用一次决策 `now`，避免一次生命周期决策中截止边界漂移；不将此规则推广为创建流程缓存日期。`reconcilePaidPending` 即使未到期也可确认，不能直接复用只处理到期的 `expireIfDue`。完整性校验保留 appointment/payment ID、userId 和错误顺序，不借重构扩展金额或事件真实性规则。

决策必须携带或能明确派生关闭原因、退款原因和原布尔返回；单个动作 enum 不足以区分 CANCELLED/EXPIRED 时，可使用 package-private decision record。取消已确认预约时，appointment 内部释放库存和取消合并为完整操作，退款仍参加同一事务，任意失败回滚全部事实。

### 9.4 `DefaultPaymentSucceededProcessor` 目标形态

```text
校验事件结构
→ Inbox insert-ignore
→ 锁定预约生命周期上下文
→ Policy 决定确认、重复或退款
→ appointment.confirm 或 payment.requestFullRefund
→ 事务提交后 Listener ACK
```

Inbox 插入必须与预约确认/退款请求处于同一事务。无论 interface 如何改造，重复 eventId 不得重复分配就诊序号或重复创建退款。

### 9.5 创建协调保持简单

`BookingCreationTransaction` 已符合目标，只做小范围整理：

- 提取 `now()`；
- 将响应构造改为具名 Factory 或静态构造方法，但仅在确实降低认知负担时执行；
- 保留 `Propagation.MANDATORY` 子用例和单一外层事务；
- 不为现有四步流程增加新的 Coordinator 层。

## 十、bookingquery 目标设计

### 10.1 目标结构

```text
DefaultBookingQueryService
├── BookingQueryMapper
├── BookingWorkflowProjector
└── AppointmentResponseAssembler
```

目标主方法：

```java
public AppointmentResponse getOwned(Long userId, Long orderId) {
    BookingQueryRecord record = mapper.selectById(orderId);
    ownership.requireOwner(userId, record);
    return assembler.assemble(record, now());
}

public List<AppointmentResponse> listMine(Long userId) {
    LocalDateTime serverTime = now();
    return mapper.selectByUserId(userId).stream()
            .map(record -> assembler.assemble(record, serverTime))
            .toList();
}
```

### 10.2 `BookingWorkflowProjector`

输入为类型化的预约和支付状态，输出 `WorkflowStatus` 或显式的 Legacy 兼容结果。退款状态仅参与摘要，不新增一致性拒绝或改变 workflow 优先级。issue 02 初次提取保留现有字符串输入，03 再将内部输入改为稳定 domain enum；若03先实施，02直接复用类型，不建立临时重复enum（允许只读依赖稳定 enum，写模块仍不反向依赖 query）。覆盖：

- `WAITING_FOR_PAYMENT`；
- `PAYMENT_PROCESSING`；
- `CONFIRMING_APPOINTMENT`；
- `CONFIRMED`；
- `CANCELLED_REFUNDING`（CANCELLED/EXPIRED/CANCELLED_BY_HOSPITAL + REFUND_PENDING）；
- `CANCELLED_REFUNDED`（同上三种预约终态 + REFUNDED）；
- `EXPIRED`；
- 历史取消且支付关闭时的空 workflow；
- 非法组合抛出内部完整性错误并记录关联 ID。

### 10.3 `AppointmentResponseAssembler`

负责：

- 医院、科室、医生 DTO；
- 就诊人 DTO 和证件脱敏；
- 最新支付尝试摘要；
- 退款摘要；
- `paymentExpired`、`canPay`、`canCancel`；
- 调用 `BookingWorkflowProjector`。

保持先查归属再校验支付完整性的错误顺序、列表同一 serverTime、完整 DTO 字段和证件脱敏；尤其保留 2.5 节权限差异与历史空金额。

Assembler 是 package-private concrete class，不建立无第二实现的 interface。

### 10.4 保持只读

`BookingQueryMapper` 继续是跨模块 SQL 的唯一归属，不新增 INSERT/UPDATE/DELETE。写模块仍不得依赖 `bookingquery`。

## 十一、改造后的完整业务逻辑

### 11.1 创建预约

```text
AppointmentController
→ BookingCoordinator 规范化幂等键和请求摘要
→ BookingCreationTransaction 开启本地事务
→ AppointmentCreationRequestService.register
→ AppointmentHoldService.createHold
   → BookingPolicy 校验
   → ScheduleProvisioning 按需创建并锁定场次
   → PatientAccess 校验就诊人
   → 条件 UPDATE 暂占库存
   → AppointmentFactory 创建快照
   → 保存预约单和有效占用
→ PaymentOrderCreationService.createOrder
→ AppointmentCreationRequestService.complete
→ 事务提交并返回 201
```

结果保持：预约 `PENDING_PAYMENT`、支付 `UNPAID`、库存 `held + 1`。

### 11.2 发起支付尝试

```text
PaymentController
→ PaymentAttemptService
→ T1 锁支付单、校验用户/截止/状态/单活跃尝试
→ attempt=CREATED，payment=PAYING，T1 提交
→ 事务外调用 PaymentGateway Adapter
→ T2 attempt=PROCESSING（支付父锁在先）
→ 失败时独立事务 attempt=FAILED、payment 条件恢复 UNPAID（支付父锁在先）
```

本阶段主要替换字符串映射，不改变事务切分。

### 11.3 支付成功回调

```text
PaymentNotificationController 或 Mock Dispatcher
→ PaymentNotificationIngress
→ PaymentGateway.verifyNotification
→ PaymentNotificationHandler
   → 幂等短路
   → 加载并锁定支付上下文
   → PaymentNotificationPolicy 决策
   → Executor 更新 attempt/payment
   → EventFactory 创建 PaymentSucceeded v1
   → 保存 Outbox 和审计
→ 事务提交
```

结果保持：支付尝试 `SUCCEEDED`、支付单 `PAID`，预约允许短暂保持 `PENDING_PAYMENT`。

### 11.4 异步确认预约

```text
OutboxPublisher → RabbitMQ → PaymentSucceededListener
→ Codec 校验 v1
→ PaymentSucceededProcessor 事务
→ Inbox 去重
→ appointment.lock
→ Policy 判断
   ├─ 可确认：appointment.confirm
   ├─ 已确认：幂等返回
   └─ 不可确认：待支付迟到时关闭并释放暂占，再确保退款；已关闭时仅确保退款
→ 提交后 ACK
```

`appointment.confirm` 内部完成暂占转确认、分配就诊序号和预约状态更新，coordination 不再参与内部顺序。

### 11.5 待支付取消

```text
锁定预约生命周期上下文
→ 锁定支付单
→ Policy 判断为 CLOSE_UNPAID
→ appointment.closePending(USER_CANCELLED)
→ payment.closeUnpaid
→ 提交
```

结果保持：释放暂占、删除有效占用、预约 `CANCELLED`、支付 `CLOSED`。

### 11.6 已确认取消

```text
锁定预约和支付
→ Policy 校验开诊前严格超过 24 小时
→ appointment.cancelConfirmed
→ refunds.requestFullRefund(APPOINTMENT_CANCELLED)
→ 同事务建立退款和 RefundRequested Outbox
```

结果保持：确认库存释放、预约 `CANCELLED`、支付 `REFUND_PENDING`，渠道退款异步执行。

### 11.7 超时与对账

```text
过期扫描只选候选 ID
→ 每个 ID 独立调用 expireIfDue
→ Policy 根据持久化 payment.paidAt 决定关闭、确认或退款

对账扫描
→ PAID + PENDING_PAYMENT：复用相同 Policy/Coordinator 恢复确认或退款
→ 已关闭且 PAID 无退款：确保退款
→ 无法安全改写的陈旧 attempt/outbox/refund：保留人工项
```

扫描延迟不延长支付资格；截止前已持久化支付事实必须保护暂占。待支付迟到事件按 issue 11 关闭并释放暂占。

### 11.8 查询

```text
AppointmentController
→ AppointmentService（由 DefaultBookingQueryService 实现）
→ BookingQueryMapper 跨表读取
→ WorkflowProjector 计算用户流程状态
→ ResponseAssembler 计算 canPay/canCancel 并组装 DTO
```

写模型状态和 `workflowStatus` 继续分离。

## 十二、事务、锁与一致性约束

### 12.1 创建事务

以下内容必须保持单事务：

```text
幂等请求登记
+ 场次按需生成
+ 库存暂占
+ 预约单和有效占用
+ 支付单
+ 幂等完成记录
```

### 12.2 生命周期锁序

跨模块写流程继续遵循：

```text
预约场次 → 预约单 → 支付单 → 退款/支付尝试等支付子记录
```

改造后由：

- `appointment.lock` 内部保证“场次 → 预约单”；
- coordination 随后调用 `payment.lockByAppointment`；
- payment 内部需要退款/尝试锁时保持支付单在先。当前源码不完全符合，修正责任在 issue 05。

具体锁图及迁移要求：

| 路径 | 当前锁序 | 目标 |
| --- | --- | --- |
| 回调 | attempt→notification→payment | 只读定位→payment→attempt→notification 锁下复验；迟到退款后续锁 refund 时 payment 已被本事务独占 |
| attempt T1 | payment→attempt 集合 | 保持 |
| attempt T2 成功/失败 | attempt；失败后 UPDATE payment | 只读定位→payment→attempt，保留 CREATED 条件和补偿语义 |
| 退款请求 | payment→refund→成功 attempt | 保持，payment 父锁串行化全部子记录操作 |
| refund worker claim | refund→attempt | 只读定位→payment→refund→attempt，重读 ID/关联 |
| refund worker complete | refund→UPDATE payment | payment→refund，保留同事务退款状态/结果 Outbox；fail 也父锁在先 |

这里退款记录和尝试记录不定义脱离父锁的全局次序：同一 payment 的子记录操作由其排他锁串行化，不同 payment 不共享子记录。不得从业务表外任意子记录锁开始再请求父锁；只读定位不授权状态写入，锁后必须复验关联。跨预约路径仍只能场次→预约→payment，不从 payment 反向锁预约。Inbox 幂等插入在场次前，notification 和 Outbox 唯一键也应纳入竞争验证；不能把高层锁图当作“已证明无死锁”。

任何 issue 修改锁顺序前必须增加或更新 MySQL 并发测试。

### 12.3 支付回调事务

回调继续原子提交：

```text
支付尝试状态
+ 支付单资金状态
+ 回调审计
+ PaymentSucceeded Outbox
+ 必要的迟到支付退款事实
```

### 12.4 消费事务

消费继续原子提交：

```text
Inbox
+ 预约库存转换
+ 就诊序号
+ 预约状态
```

若进入退款分支，则 Inbox、退款单、支付状态和 `RefundRequested` Outbox 必须处于同一事务。

### 12.5 外部调用

以下调用不得在持有业务数据库事务时执行：

- 创建渠道支付；
- 关闭渠道支付；
- 渠道退款；
- 主动查询渠道支付。

RabbitMQ Outbox 发布器保持现有 confirm/mandatory-return 语义；其内部事务优化不属于本次首要范围。

## 十三、建议包结构

目标是少量新增有业务含义的类型，不要求一次性搬动所有现有文件：

```text
appointment
├─ application
│  ├─ AppointmentHoldService
│  ├─ AppointmentLifecycle
│  ├─ AppointmentLifecycleCase
│  ├─ internal（可选；Java 子包不是同包）
│     ├─ BookingPolicy
│     ├─ ScheduleProvisioning
│     ├─ PatientAccess
│     └─ AppointmentFactory
├─ domain
│  ├─ AppointmentStatus
│  └─ AppointmentCloseReason
├─ mapper
└─ entity

payment
├─ application
│  ├─ PaymentAttemptService
│  ├─ PaymentNotificationIngress
│  ├─ PaymentLifecycle
│  ├─ PaymentLifecycleCase
│  └─ internal
│     ├─ PaymentNotificationPolicy
│     ├─ PaymentNotificationContextLoader
│     ├─ PaymentNotificationExecutor
│     └─ PaymentEventFactory
├─ domain
├─ port
├─ adapter
├─ mapper
└─ entity

coordination
├─ application
│  ├─ BookingCoordinator
│  ├─ BookingLifecycleCoordinator
│  ├─ PaymentSucceededProcessor
│  └─ BookingLifecyclePolicy
├─ model
│  ├─ ExpirationDecision
│  ├─ CancellationDecision
│  └─ PaymentConfirmationDecision
├─ messaging
└─ persistence

bookingquery
├─ application
│  ├─ DefaultBookingQueryService
│  ├─ BookingWorkflowProjector
│  └─ AppointmentResponseAssembler
├─ model
└─ persistence
```

如果 `internal` 子包导致 package-private 访问反而复杂，可直接将内部类放在 `application` 包并保持 package-private。包结构服从可见性和 locality，不为形式对称而增加目录。

## 十四、实施阶段与阶段门禁

### 阶段 0：基线和状态决策表

改造内容：

- 固定当前 HTTP、application、数据库和消息可观察行为；
- 将预约/支付/退款组合整理成决策表测试；
- 补齐当前缺失的截止边界、重复事件和非法组合测试；
- 记录当前测试数量和执行命令。

完成条件：

- 正常支付、重复回调、截止边界、取消、过期、迟到支付、对账恢复均有现有行为测试；
- `./mvnw test` 通过；
- MySQL 和 RabbitMQ 关键测试基线通过；
- 本阶段不改变生产代码行为。

### 阶段 1：查询投影内部重构

改造内容：

- 提取 `BookingWorkflowProjector`；
- 提取 `AppointmentResponseAssembler`；
- 简化 `DefaultBookingQueryService`；
- 保持 SQL、DTO 和 HTTP JSON 不变。

完成条件：

- Projector 决策表覆盖全部合法组合和非法组合；
- 查询集成测试响应完全兼容；
- `BookingQueryMapper` 仍无写方法。

选择先做此阶段是因为其无写事务，能验证拆分方式而不触碰并发核心。

### 阶段 2：类型化状态与原因

改造内容：

- 新增 `AppointmentStatus`；
- application/coordination view 改用状态 enum；保留现有 HTTP DTO 字符串；02 的 query 内部映射在此统一；
- 收敛关闭原因、退款原因；
- persistence/事件边缘负责字符串转换；
- 暂不改变数据库列和事件 JSON 值。

完成条件：

- 本轮触及的 application/coordination 决策不直接比较预约、支付、退款状态字符串；
- 数据库兼容和 JSON 兼容测试通过；
- 非法数据库值转换为明确内部错误。

### 阶段 3：appointment 创建内部深度化

改造内容：

- 提取 `BookingPolicy`、`ScheduleProvisioning`、`AppointmentFactory` 等必要内部 implementation；
- 简化 `AppointmentHoldServiceImpl.createHold`；
- 保留外部 interface 和事务传播。

完成条件：

- 创建主方法能直接读出“校验→场次→就诊人→暂占→快照→有效占用”；
- 幂等、按需场次、跨零点、库存竞争和快照测试通过；
- 未增加无第二实现的 public interface。

### 阶段 4：payment 回调分离与支付父子锁序收敛

改造内容：

- 提取 `PaymentNotificationPolicy` 和决策枚举；
- 提取 Context Loader、Executor、Event/Audit Factory；
- 简化回调主方法；
- 保留原子事务和事件 v1；同时消除回调、attempt T2 和 refund worker 的父子反向锁，不能仅提取 Policy 而保留潜在锁环。

完成条件：

- Policy 决策表覆盖成功、失败、重复、乱序、金额不匹配、未知订单和迟到支付；
- 同一通知和同一交易仍只产生一次资金副作用；
- 无 Broker 时回调仍能持久化成功；
- MySQL 并发回调、回调/补偿、回调/退款请求、退款请求/worker 竞争测试通过，所有子记录写先锁 payment。

### 阶段 5：加深 appointment/payment 生命周期 interface

改造内容：

- 用 `AppointmentLifecycle.lock/confirm/closePending/cancelConfirmed` 替代低层方法；
- 类型化 `PaymentLifecycle`；
- 删除未使用的 `refundPaid`；
- 暂时保留 Legacy 所需 `markPaid`，直到阶段 7。

完成条件：

- coordination 不读取 `nextVisitNumber`；
- coordination 不分别确认库存和预约；
- coordination 不分别释放库存和关闭预约；
- 固定锁序和条件 UPDATE 保持不变；
- MySQL 支付/取消/过期竞争测试通过。

### 阶段 6：coordination Policy 化与编排简化

改造内容：

- 新增 `BookingLifecyclePolicy` 和决策结果；
- 简化取消、过期、支付成功消费、对账恢复；
- 在已有入口适用范围内统一关联完整性校验，不给事件消费者额外增加支付读取；
- 每个生命周期决策锁后获取一次 `now`；创建最终占号前仍需重新读取日期。

完成条件：

- Coordinator public 方法遵循“锁定上下文→完整性→Policy→switch 执行”；
- 复杂状态组合不再散落；
- Inbox、退款补偿和对账行为保持不变；
- H2、MySQL、RabbitMQ 竞争矩阵通过；保留 issue 11、12 的已确认修复结果。

### 阶段 7：删除 Legacy 模拟成功路径

改造内容：

- 将仍依赖 `/simulate-success` 的测试迁移到 MockPay attempt/complete/notification 流程；
- 删除 `LegacySimulatePaymentController`；
- 删除 `LegacyPaymentSimulationPrecheck` 及 implementation；
- 删除 `BookingLifecycleCoordinator.simulatePaymentSuccess`；
- 删除 `PaymentLifecycle.markPaid` 和兼容支付尝试构造。

完成条件：

- 生产、dev、test 均不存在旧路由；
- 所有支付成功测试通过正式回调/事件链路；
- 正式链路仍可验证幂等确认和退款；
- 无未使用方法、类和导入。

### 阶段 8：测试表面和架构门禁整理

改造内容：

- 将浅 implementation 测试替换为 Policy 测试和 module interface 集成测试；
- 保留真实 MySQL/RabbitMQ 故障与并发测试；
- 更新 ArchUnit 规则；
- 删除仅验证转发调用的测试。

完成条件：

- 测试断言可观察结果，不依赖内部方法调用顺序；
- 内部重命名不要求大面积修改用例测试；
- 架构规则能拒绝写模块交叉 Mapper、coordination 依赖 implementation、bookingquery 写操作。

### 阶段 9：文档与交付

改造内容：

- 更新 ADR、业务流程、API、验证报告和 HTTP 示例；
- 明确正式支付主链路、状态语义和异常恢复；
- 记录最终接口和删除的兼容能力。

完成条件：

- 当前操作文档不再指导调用旧路由；历史 ADR/spec/issue 可以保留旧类名和路由作为演进证据；
- 完整测试矩阵通过；
- 任务地图中所有 issue 都有验证结果和 Comments 记录。

## 十五、建议 issue 拆分

方案评审通过后，建议创建 `.scratch/booking-module-deepening/map.md` 和以下 issue：

| 顺序 | Issue | 类型 | 前置依赖 | 独立验收重点 |
| ---: | --- | --- | --- | --- |
| 1 | `01-baseline-state-matrix.md` | test | 无 | 锁定外部行为和状态决策表 |
| 2 | `02-booking-query-projection.md` | refactor | 01 | Projector/Assembler，查询兼容 |
| 3 | `03-typed-lifecycle-language.md` | refactor | 01 | 状态、原因、映射类型化 |
| 4 | `04-appointment-hold-deepening.md` | refactor | 03 | 创建暂占主方法和内部 implementation |
| 5 | `05-payment-notification-policy.md` | refactor/fix | 03 | 回调 Policy/Executor，原子事务与支付父锁保证 |
| 6 | `06-lifecycle-interfaces.md` | refactor | 03、04、05 | appointment/payment 深 interface |
| 7 | `07-coordination-process-manager.md` | refactor | 05、06 | 取消/过期/确认/对账 Policy 化 |
| 8 | `08-remove-legacy-payment-simulation.md` | cleanup | 07 | 测试迁移和旧链路删除 |
| 9 | `09-architecture-and-reliability-gates.md` | test | 02、07、08 | ArchUnit、MySQL、RabbitMQ 完整门禁 |
| 10 | `10-documentation-delivery.md` | docs | 09 | ADR/API/流程/验证报告交付 |

推荐顺序为 01→02→03→04→05→06→07→08→09→10；依赖图（与 issue 的 Blocked by 一致）：

```text
01 → 02 ──────────────────────────────┐
 └→ 03 → 04 ────────┐                 │
       └→ 05 ───────┴→ 06 → 07 → 08 → 09 → 10
                         └───────→ 09
```

06 依赖 03、04、05；07 依赖 05、06；09 依赖 02、07、08。

`04` 和 `05` 在 `03` 完成后可以独立实施，但为了降低共享代码冲突，默认仍按顺序串行推进。

## 十六、测试与验收矩阵

### 16.1 纯 Policy 测试

| Policy | 必测内容 |
| --- | --- |
| `BookingWorkflowProjector` | 所有合法组合、Legacy 空 workflow、非法组合 |
| `BookingPolicy` | 四场次、D+1/D+7、越界、跨零点最终验收 |
| `PaymentNotificationPolicy` | 成功、失败、重复、迟到失败、迟到成功、金额币种不匹配、非法状态 |
| `BookingLifecyclePolicy` | 取消、过期、截止前已付、截止时刻支付、迟到支付、重复确认 |

纯 Policy 测试不启动 Spring，不访问数据库。

### 16.2 H2 application/HTTP 测试

- 创建预约幂等和失败回滚；
- 预约快照稳定；
- 支付尝试失败恢复；
- 回调审计和 Outbox；
- Inbox 重复处理；
- 查询投影和权限字段；
- Controller 状态码与 JSON 兼容。

### 16.3 MySQL Testcontainers

- 并发首次创建场次；
- 并发抢最后库存；
- 有效预约唯一约束；
- 支付、取消、过期竞争；
- 重复并发回调；
- 固定锁序无新增循环依赖，覆盖 12.2 节潜在反向锁路径；
- Outbox `SKIP LOCKED`；
- 退款唯一约束。

### 16.4 RabbitMQ Testcontainers

- Broker 不可用时回调成功落库；
- Broker 恢复后 Outbox 发布；
- 增补业务提交前失败与提交后 ACK 前失败的可控窗口，重复消息时 Inbox 单副作用（当前测试不等于已覆盖两个窗口）；
- 永久坏消息进入 DLQ；
- DLQ 重放保留 eventId；
- 退款 worker 重试和 DLQ；
- 截止前支付最终收敛 CONFIRMED+PAID；待支付迟到事件过期释放并退款，已关闭后的迟到支付保持关闭并退款。

### 16.5 架构测试

至少保留并扩展：

- payment 不依赖 appointment；
- appointment 不依赖 payment；
- coordination 不依赖两个写模块的 Mapper、Entity、implementation；
- 写模块不依赖 bookingquery；
- bookingquery Mapper 只读；
- Controller 不直接依赖 Mapper；
- MockPay Adapter/Controller 只在 dev/test 注册（profile隔离用Spring上下文测试验证，而非仅靠ArchUnit）；其他未来真实Adapter不套用Mock限制；
- public application interface 不返回 persistence Entity。

### 16.6 每阶段最低命令

- 每个 issue：运行该 issue 聚焦测试；
- 每阶段结束：`./mvnw test`；
- 涉及锁、唯一约束、条件更新：`./mvnw -Pmysql-it verify`；
- 涉及 Outbox/Inbox/退款消息：`./mvnw -Preliability-it verify`（MySQL + RabbitMQ 双容器）；与 mysql-it 分开执行，避免两个 profile 对 Failsafe includes 的合并误解；
- 最终交付：三层测试全部执行并更新 `docs/verification-report.md`。

## 十七、风险、取舍与回退

### 17.1 interface 迁移范围过大

风险：同时修改 appointment、payment、coordination 会产生大 diff。

控制：先稳定基线和类型，再在 issue 06 一个可构建提交单元中迁移整个生命周期 interface 家族及全部调用者。无需为过渡新增一层 Adapter；issue 完成时删除旧低层调用，只有明确限期到08的Legacy专用方法暂留。禁止长期保留新旧两套正式 interface。

### 17.2 提取过度导致跳转增加

风险：为每几行代码创建 Bean、interface、Manager 或 Helper。

控制：默认 private 方法或 package-private concrete class；新增类型必须能隐藏规则、独立依赖或形成稳定测试表面。删除该类型后如果复杂度不会回到多个调用者，则不应成为外部 seam。

### 17.3 Java Policy 与数据库条件不一致

风险：Policy 认为可迁移，但条件 UPDATE 失败。

控制：数据库条件继续作为最终防线；UPDATE 影响行数不是 1 时回滚并沿用调用点当前错误码（预约为 INTERNAL_ERROR、回调为 RETRYABLE_CONCURRENCY_ERROR 等）；MySQL 竞争测试覆盖。

### 17.4 锁序回归

风险：把锁封装后隐藏了错误顺序。

控制：在 `AppointmentLifecycle.lock` 的方法契约和测试中固定“场次→预约”；coordination 始终先 appointment 后 payment；改动锁方法必须运行真实 MySQL 竞争测试。

### 17.5 类型化状态破坏持久化兼容

风险：enum 名称、数据库值和 JSON 值不一致。

控制：先显式转换而不是立即全局 TypeHandler；固定序列化值测试；数据库迁移不在本阶段发生。

### 17.6 Legacy 删除影响测试便利性

风险：旧测试依赖同步 `simulate-success`。

控制：先提供测试 helper 驱动正式 MockPay 链路，再删除旧 Controller；helper 只能组合公开测试入口，不能绕开验签、回调、Outbox 或 Inbox。

### 17.7 回退策略

- 每个 issue 保持可独立提交和回退；
- 不在同一提交中混合大规模移动、重命名和行为变化；
- 新旧正式 interface 过渡只存在于当前 issue 内，issue 完成前删除旧低层调用；Legacy专用markPaid保留至08是明确限期例外；
- 任一阶段完整测试失败时，停止后续 issue，修复当前阶段，不以兼容分支掩盖失败；
- 数据库无迁移时，代码回退不需要数据回滚。

## 十八、实现守则

### 18.1 推荐命名

- 顶层用例：`Coordinator`、`Processor`、明确业务动词 interface；
- 纯判断：`Policy`、`Decision`；
- 对象构造：`Factory` 或类型自身静态构造；
- 查询转换：`Projector`、`Assembler`；
- 外部实现：`Adapter`；
- 加锁后快照：`LifecycleCase` 或 `Context`，全项目选择一种后保持一致。

### 18.2 避免命名

避免无领域含义的：

- `BaseService`；
- `CommonManager`；
- `BusinessHelper`；
- `AbstractProcessor`；
- `ContextUtils`。

### 18.3 主方法审查问题

每次 review 检查：

1. 只看主方法能否说出业务顺序？
2. 调用者是否仍需要知道被调用 module 的表和锁顺序？
3. 复杂判断能否用决策表说明？
4. 新增类是否隐藏了真实复杂度，还是只转发一行调用？
5. 事务边界是否仍覆盖必须原子提交的事实？
6. 数据库条件更新和唯一约束是否仍存在？
7. 测试是否通过 interface 验证可观察行为？

## 十九、已确认的设计决定与待决边界

以下决策与任务地图配套，实施不得超出已确认范围：

本轮已核实/确认的决定如下；两项业务修复和历史数据边界以文首修订及 ADR 0006 为准：

1. 生命周期锁定结果命名为 `AppointmentLifecycleCase` / `PaymentLifecycleCase`，不使用 persistence Entity。
2. appointment 的 `confirm` 内部分配就诊序号，coordination 不传入序号。
3. `BookingLifecyclePolicy` 位于 coordination，因为它判断预约和支付的组合事实。
4. payment 回调使用 Policy + Executor 分离，但保持单一事务入口。
5. `bookingquery` 使用 Projector + Assembler，不引入独立读库。
6. Legacy 模拟成功链路在正式测试迁移后删除，不永久保留。
7. 首轮改造不修改数据库 schema、不改变正式 HTTP JSON、不升级事件版本；issue 08 可删除 dev/test 旧端点（用户于 2026-11-08 确认）。
8. `paidAt` 继续表示本系统确认渠道收款的服务端时间。

## 二十、最终交付清单

- 四个模块的目标 interface 和 implementation 已落地；
- 状态、原因和决策类型完成；
- appointment 创建和生命周期内部知识完成收口；
- payment 回调决策与执行完成分离；
- coordination 成为清晰 Process Manager；
- bookingquery 完成 Projector/Assembler 拆分；
- Legacy 支付模拟路径删除；
- ArchUnit、H2、MySQL、RabbitMQ 测试通过；
- ADR、业务流程、API、HTTP 示例、验证报告更新；
- `.scratch/booking-module-deepening/map.md` 和所有 issue Comments 记录实际验证结果；
- 无无关重构、未使用导入、未使用方法或长期双轨 interface；05→08 的 Legacy 专用能力明确限期至 08，不算永久双轨。

## Comments

- 2026-11-06：根据当前 `appointment`、`payment`、`coordination`、`bookingquery` 实现整理初版目标设计；本文件仅为任务拆分前规格，尚未创建 issue 或修改生产代码。

- 2026-11-08：现有基线实际运行 `./mvnw -o test`（98项）、`./mvnw -o -Pmysql-it verify`（常规98+MySQL24项）和 `./mvnw -o -Preliability-it verify`（常规98+双容器9项），全部通过、零跳过；详见map。
- 2026-11-08：完整阅读指定资料并核对四模块实现、Mapper、现有 H2/MySQL/RabbitMQ/ArchUnit 测试。修订 ADR 0005 优先级、创建最终日期复验、支付锁序偏差及纠正范围、生命周期事务前置条件、查询真实组合与权限、退款原因/取消布尔语义、消息失败分类与测试缺口；记录迟到事件残留暂占为待决业务问题。用户明确授权 issue 08 删除 dev/test 旧模拟端点，正式 HTTP 契约不变。创建 map 和 01–10 issue；未修改生产代码。
