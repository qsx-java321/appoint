# 独立支付模块、异步回调与预约协调：目标设计说明

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。16/16已于a293afa交付；同步兼容入口后来删除、接口继续深化、消息设计与测试整理也已完成。下文目标/差距及83等数量均按原实施检查点理解，不是当前待办。 当前能力与进度见[项目状态](../../docs/project-status.md)。

Status: resolved

确认日期：2026-10-29。

本文记录已经与需求提出者逐项确认的目标架构、业务规则、数据模型、接口、事件、迁移和验收要求，供后续制定任务地图、拆分 issue 和实施代码使用。本文描述的是**目标状态**，不是当前代码已经具备的能力；实施完成前，当前有效行为仍以 `docs/business-flow.md`、`docs/api.md` 和现有测试为准。

架构决定见 `docs/adr/0004-independent-payment-and-reliable-events.md`，领域用语见根目录 `GLOSSARY.md`。实施时如果需要改变本文已经确认的业务结果，先更新本 spec 和 ADR，再修改代码，不能让代码静默偏离文档。

## 一、决策摘要

本次改造采用以下已经确认的决定：

1. 项目继续是模块化单体：预约、支付、协调和查询模块部署在同一个 Spring Boot 应用，使用同一个 MySQL；本期不拆微服务。
2. 预约、支付和协调写模型只能通过公开接口或业务事件协作，任何模块不得直接使用另一个写模块的 Mapper 或实体。专用只读查询模块可以跨表联查，但不得修改业务表。
3. `PaymentService` 管理业务支付规则；`PaymentGateway` 是外部渠道 seam；`MockPaymentGateway` 是第一阶段 Adapter，未来可以增加 `WeChatPayGateway` 和 `AlipayGateway`。
4. 创建预约由协调模块在一个本地事务中完成“预约暂占＋支付单创建”，继续原子返回 `appointmentOrderId + paymentOrderId`。
5. 一张预约单对应一张业务支付单；一张支付单允许多次支付尝试，但同一时刻最多有一个处理中的尝试。
6. 排班配置 CNY 挂号费；创建预约时把费用复制为预约单和支付单的不可变快照。旧数据不推测未知金额。
7. MockPay 模拟“创建渠道支付→用户完成支付→渠道签名异步回调”。用户发起支付后收到 HTTP 202，并轮询预约详情，不等待全链路同步完成。
8. 支付回调在同一数据库事务中保存支付结果、回调幂等记录和 Outbox；RabbitMQ 暂时不可用不影响回调确认。
9. Outbox 事件以至少一次语义投递；协调消费者使用 Inbox `eventId` 数据库唯一约束实现幂等；重试耗尽后进入 DLQ，并支持人工重放。
10. 支付截止时，如果本地支付模块已经持久化 `PAID`，预约不能过期；否则立即释放号源。关闭后收到成功回调属于迟到支付，进入原路全额退款，不恢复预约。
11. 用户取消、医院停诊和预约超时可以结束处理中的支付尝试并释放号源；其后的迟到支付同样退款。
12. 已确认预约允许取消时，预约立即取消并释放号源，支付进入异步退款；退款失败不恢复预约。
13. 第一阶段只支持原路全额退款，不支持部分退款、多次退款或人工改额。
14. 旧活跃支付阻断迁移；旧终态支付作为 `LEGACY` 只读数据保留。
15. MySQL 和 RabbitMQ 的真实并发、重复、延迟、乱序、重试与 DLQ 行为必须由 Testcontainers 集成测试验证，不能只依赖 Mockito 或 H2。

## 二、当前基线与改造原因

### 2.1 当前结构

当前支付入口为：

```text
PaymentController
    → PaymentService
        → PaymentServiceImpl
            → AppointmentLifecycleService
```

`PaymentServiceImpl` 仅把 `simulateSuccess` 转发给 `AppointmentLifecycleService`。实际逻辑集中在 `AppointmentLifecycleServiceImpl`：它按排班、预约单、支付单顺序加锁，并在一个事务中直接推进支付单、预约单、库存和有效预约占用。

相关当前文件：

- `src/main/java/com/qsx/appoint/payment/controller/PaymentController.java`
- `src/main/java/com/qsx/appoint/payment/service/PaymentService.java`
- `src/main/java/com/qsx/appoint/payment/service/impl/PaymentServiceImpl.java`
- `src/main/java/com/qsx/appoint/appointment/service/impl/AppointmentLifecycleServiceImpl.java`
- `src/main/java/com/qsx/appoint/appointment/service/impl/AppointmentCreationTransactionService.java`
- `src/main/java/com/qsx/appoint/appointment/job/AppointmentExpirationJob.java`

### 2.2 当前设计保留的优点

改造必须保留以下已验证行为：

- 排班→预约单→支付单的统一锁顺序；
- 库存恒等式：`总名额 = 可用名额 + 暂占名额 + 已确认名额`；
- 条件更新只推进一次状态；
- 重复支付、取消和超时不重复扣减或释放库存；
- 支付截止时间立即生效，不把扫描延迟当作宽限；
- 创建预约的 UUID 幂等键和就诊人快照；
- 同排班、同就诊人或同证件的有效预约唯一约束；
- 就诊序号单调递增、不复用；
- 现有 24 小时取消窗口和医院停诊规则。

### 2.3 当前设计需要改变的问题

1. `payment` 和 `appointment` 包双向依赖，支付不是独立模块。
2. 支付实现没有金额、币种、渠道订单、失败、处理中、回调、退款失败或对账模型。
3. `PaymentServiceImpl` 是浅层转发，支付单元测试只验证 Mockito 调用。
4. 预约创建和生命周期代码直接使用 `PaymentOrderMapper`，模块 ownership 不明确。
5. 当前支付成功与预约确认在一个事务中同步完成，无法真实模拟微信、支付宝的异步回调。
6. 当前查询 SQL 直接联查预约和支付表，没有区分写模型 ownership 与跨模块只读投影。

## 三、目标、非目标与完成条件

### 3.1 目标

- 建立稳定的支付业务接口和外部支付渠道 seam。
- 使用 MockPay 验证真实渠道会遇到的异步、重复、乱序、延迟和失败。
- 让支付模块独立维护资金状态，预约模块独立维护号源与预约状态。
- 用协调模块表达跨模块流程，不把流程散落到 Controller、Mapper 或 Rabbit Listener。
- 以 Outbox、Inbox、RabbitMQ、DLQ 和对账保证最终一致性可恢复。
- 保持现有预约核心规则和库存安全。
- 为未来增加 WeChat Pay、Alipay Adapter 留出稳定接口，而不在本期接入真实资金。

### 3.2 非目标

- 不拆分独立支付微服务。
- 不接入真实微信支付或支付宝账号。
- 不支持信用卡、医保、组合支付或现金支付。
- 不支持部分退款、多次退款、人工改额或退款到其他账户。
- 不实现业务前端、WebSocket 或 SSE；状态更新通过轮询详情获得。
- 不自动处理已确认预约的医院停诊退款；保持当前“显示停诊提示，联系医院”规则。
- 不承诺零停机跨版本部署；迁移仍必须安全、增量并保留历史数据。
- 不把 RabbitMQ 当作订单真相；MySQL 仍是支付、预约和事件投递状态的持久化真相。

### 3.3 可验证完成条件

完成后必须同时满足：

1. `payment` 写模块不导入 appointment Mapper、实体或实现类。
2. `appointment` 写模块不导入 payment Mapper、实体或实现类。
3. 协调模块不直接使用两个写模块的 Mapper。
4. 创建预约仍原子地产生预约暂占、支付单、有效预约占用和幂等响应。
5. 支付回调提交后，即使 RabbitMQ 停机，支付成功事实也能在恢复后确认预约。
6. 重复回调、重复事件和消费者重启不会重复确认库存或发放就诊序号。
7. 支付失败后可在截止前重试，且同一支付单不存在两个同时处理中的尝试。
8. 支付、取消、超时和停诊竞争只得到一种预约终态，库存恒等式始终成立。
9. 迟到支付不恢复预约并最终进入全额退款。
10. 已确认预约取消后立即释放号源；退款失败不恢复预约。
11. 旧终态订单可查询，旧活跃支付阻断迁移，历史未知金额不被伪造。
12. H2 快速回归、MySQL Testcontainers、RabbitMQ Testcontainers 和真实 HTTP 流程全部通过。

## 四、领域对象与不变量

| 对象 | Ownership | 业务含义 | 核心不变量 |
| --- | --- | --- | --- |
| 医生排班 | catalog/appointment side | 一位医生指定日期的半日门诊 | 开放后库存守恒；开放预约后挂号费不可改。 |
| 预约单 | appointment | 用户为就诊人对一场排班形成的预约记录 | 一张有效预约占用一个名额；只有支付成功事实可以把待支付推进为已确认。 |
| 支付单 | payment | 与预约一一对应的整体应付记录 | 金额和币种是不可变快照；一张预约至多一张支付单。 |
| 支付尝试 | payment | 通过一个渠道发起的一次支付动作 | 一张支付单可多次尝试，但同一时刻最多一个 `PROCESSING`。 |
| 退款单 | payment | 对一张已收款支付单的原路全额退款 | 第一阶段每张支付单最多一笔全额退款；失败不恢复预约。 |
| 回调通知 | payment | 渠道发送的一次异步资金结果通知 | 渠道通知 ID 唯一；签名、金额、币种和关联关系全部校验后才能改变资金状态。 |
| Outbox 事件 | payment | 与支付状态同事务保存、等待发布的业务事实 | 事件 ID 唯一；发布成功前不得丢弃。 |
| Inbox 记录 | coordination | 一个消费者对一个事件的处理凭证 | `(consumerName, eventId)` 唯一，防止重复业务副作用。 |

### 4.1 金额规则

- 金额使用 `BigDecimal` 和数据库 `DECIMAL(10,2)`，不能使用 `double`。
- 第一阶段币种固定为 `CNY`，仍显式持久化三位币种代码。
- 金额必须大于零。
- 管理员创建排班时必须设置挂号费。
- 到达排班预约开放时间后，挂号费不可修改。
- 创建预约时，排班挂号费复制到预约单和支付单；之后任何目录变更不改写快照。
- 渠道回调金额或币种与支付单不一致时，保存审计结果并拒绝推进支付状态。

### 4.2 支付截止规则

- 支付截止仍为 `min(预约暂占创建时间 + 15 分钟, 开诊时间)`。
- 当前时间到达截止时不再创建新支付尝试。
- 截止判定以本地支付结果是否已经成功持久化为准，不以客户端页面、RabbitMQ 到达时间或渠道声称的支付时间回溯恢复预约。
- 截止时已持久化 `PAID` 但预约未确认，协调模块必须保护暂占并恢复事件处理。
- 截止时仍为 `UNPAID/PAYING`，协调模块关闭预约和支付并释放暂占。
- 关闭后收到成功回调属于迟到支付，资金事实必须记录，但预约保持关闭并进入退款。

## 五、目标模块和依赖方向

```text
┌──────────────────────────────────────────────────────────┐
│ HTTP Controller / Scheduled Job / RabbitMQ Listener      │
└───────────────────────┬──────────────────────────────────┘
                        │
              ┌─────────▼─────────┐
              │ Coordination      │
              │ - booking         │
              │ - lifecycle       │
              │ - reconciliation  │
              └───────┬─────┬─────┘
                      │     │
           ┌──────────▼┐   ┌▼────────────┐
           │Appointment│   │Payment      │
           │- order    │   │- order      │
           │- inventory│   │- attempt    │
           │- claim    │   │- refund     │
           └───────────┘   │- outbox     │
                           └──────┬───────┘
                                  │ PaymentGateway
                     ┌────────────▼────────────┐
                     │ Mock / WeChat / Alipay │
                     └─────────────────────────┘

只读查询模块：跨表读取 appointment、payment、schedule，绝不写业务表。
```

### 5.1 建议包结构

```text
com.qsx.appoint
├─ appointment
│  ├─ domain
│  ├─ application
│  └─ infrastructure
├─ payment
│  ├─ domain
│  ├─ application
│  ├─ port
│  ├─ adapter
│  │  ├─ web
│  │  ├─ mock
│  │  └─ persistence
│  └─ config
├─ coordination
│  ├─ application
│  ├─ messaging
│  ├─ job
│  └─ persistence
└─ bookingquery
   ├─ controller
   ├─ dto
   └─ mapper
```

不要求为了目录形式给每个类创建接口。接口只放在真实 seam：预约写模块、支付写模块、支付渠道和协调入口。

### 5.2 模块访问规则

- `payment` 可以引用 common 类型，不引用 appointment Mapper、实体或实现。
- `appointment` 可以引用 common 类型，不引用 payment Mapper、实体或实现。
- `coordination` 依赖两个模块的 application interface，不依赖其 persistence 类。
- `bookingquery` 可以只读联查多个模块的数据表，但查询对象不能被写模块复用为实体。
- Controller 只调用相应的 application/coordination interface。
- Rabbit Listener 只负责反序列化、基础消息校验和调用协调用例，不直接执行业务 SQL。
- Spring 配置在 composition root 选择 Adapter；业务类不通过 `new` 创建具体渠道。

## 六、主要接口设计

以下类型和方法名是目标语义，不要求实施时逐字照抄；如果修改，必须保持相同 ownership 和不变量。

### 6.1 预约写模块

```java
public interface AppointmentLifecycleService {
    AppointmentHold createHold(CreateAppointmentHoldCommand command);
    AppointmentConfirmation confirmPaid(ConfirmPaidCommand command);
    AppointmentCancellation cancel(CancelAppointmentCommand command);
    AppointmentExpiration expire(ExpireAppointmentCommand command);
    ScheduleClosure closeSchedule(CloseScheduleCommand command);
}
```

`AppointmentHold` 至少返回：

```text
appointmentOrderId
appointmentNo
userId
amount
currency
paymentExpiresAt
```

预约模块内部完成排班锁、库存条件更新、预约单、快照和有效预约占用，不要求协调者逐个调用 Mapper。

### 6.2 支付写模块

```java
public interface PaymentService {
    PaymentOrderResult createOrder(CreatePaymentOrderCommand command);
    PaymentAttemptResult startAttempt(StartPaymentAttemptCommand command);
    PaymentOrderResult getOwned(Long userId, Long paymentOrderId);
    void handleNotification(HandlePaymentNotificationCommand command);
    PaymentLifecycleSnapshot lockForLifecycle(Long appointmentOrderId);
    PaymentCloseResult closeUnpaid(CloseUnpaidPaymentCommand command);
    RefundResult requestFullRefund(RequestFullRefundCommand command);
}
```

`createOrder` 只接收预约模块已经确定的金额和截止时间，不查询医生、职称或排班来计算费用。

`startAttempt` 必须：

1. 校验支付单归属当前用户；
2. 校验支付单仍可支付且未到截止；
3. 锁定支付单；
4. 检查不存在活跃尝试；
5. 创建一次 `CREATED` 尝试并返回稳定的 attempt ID；
6. 在数据库事务之外调用 `PaymentGateway`；
7. 再以短事务写入渠道订单号和 `PROCESSING`，失败则记 `FAILED` 并把支付单恢复为 `UNPAID`。

### 6.3 支付渠道 seam

```java
public interface PaymentGateway {
    PaymentChannel channel();
    GatewayPaymentSession createPayment(GatewayPaymentRequest request);
    void closePayment(GatewayCloseRequest request);
    GatewayRefundSubmission refund(GatewayRefundRequest request);
    GatewayPaymentSnapshot queryPayment(String providerOrderNo);
    VerifiedPaymentNotification verifyNotification(RawPaymentNotification notification);
}
```

要求：

- 通用接口不出现微信或支付宝 SDK 类型；
- Adapter 把渠道错误码转换为本项目的稳定结果；
- `refundNo` 和 `paymentNo/attemptNo` 作为渠道幂等键；
- 回调验签在 Adapter 内完成；
- Mock Adapter 通过相同接口和契约测试，不允许绕开验签和幂等逻辑。

### 6.4 协调模块

```java
public interface BookingCoordinator {
    AppointmentCreationResult create(CreateBookingCommand command);
}

public interface BookingLifecycleCoordinator {
    AppointmentView cancel(Long userId, Long appointmentOrderId);
    void onPaymentSucceeded(PaymentSucceeded event);
    boolean expireIfDue(Long appointmentOrderId);
    ScheduleView closeSchedule(Long operatorUserId, Long scheduleId);
}
```

协调模块拥有跨模块事务顺序，但不拥有预约或支付业务字段的 SQL。

## 七、数据库目标结构

### 7.1 `schedules` 新字段

```text
registration_fee DECIMAL(10,2)
fee_currency CHAR(3)
```

新模型排班必须满足：金额大于零、币种为 `CNY`。旧排班允许为空，并在补齐前不可创建新预约。

### 7.2 `appointment_orders` 新字段

```text
fee_amount DECIMAL(10,2)
fee_currency CHAR(3)
```

新预约必须非空；Legacy 预约允许为空并保持只读。

### 7.3 `payment_orders` 调整

保留当前 ID、支付单号、预约单关联和历史时间，增加或规范：

```text
amount DECIMAL(10,2)
currency CHAR(3)
status VARCHAR(32)
expires_at DATETIME
paid_at DATETIME
closed_at DATETIME
refunded_at DATETIME
legacy BOOLEAN
created_at DATETIME
updated_at DATETIME
```

约束：

- `appointment_order_id` 唯一；
- 新支付单金额大于零、币种非空、截止时间非空；
- Legacy 记录可以没有金额，但不能进入新支付流程；
- 状态只允许枚举值。

`user_id` 当前是重复所有者字段。实施前优先评估删除并通过预约单确定所有者；如果为了查询或迁移继续保留，必须由创建事务和完整性测试保证它等于预约单用户，不能把两个独立外键误认为归属一致约束。

### 7.4 `payment_attempts`

```text
id BIGINT PRIMARY KEY
attempt_no VARCHAR(64) UNIQUE NOT NULL
payment_order_id BIGINT NOT NULL
channel VARCHAR(32) NOT NULL
provider_order_no VARCHAR(128)
provider_transaction_no VARCHAR(128)
status VARCHAR(32) NOT NULL
failure_code VARCHAR(64)
failure_message VARCHAR(255)
created_at DATETIME NOT NULL
processing_at DATETIME
completed_at DATETIME
updated_at DATETIME NOT NULL
```

索引与约束：

- `(payment_order_id, status)` 支持活跃尝试检查；
- `(channel, provider_order_no)` 唯一；
- `(channel, provider_transaction_no)` 在非空时唯一；
- 状态只允许 `CREATED/PROCESSING/SUCCEEDED/FAILED/CLOSED`。

“最多一个活跃尝试”由锁定支付单和条件更新保证；不要依赖先查后插的无锁逻辑。

### 7.5 `payment_refunds`

```text
id BIGINT PRIMARY KEY
refund_no VARCHAR(64) UNIQUE NOT NULL
payment_order_id BIGINT UNIQUE NOT NULL
payment_attempt_id BIGINT NOT NULL
amount DECIMAL(10,2) NOT NULL
currency CHAR(3) NOT NULL
channel VARCHAR(32) NOT NULL
provider_refund_no VARCHAR(128)
status VARCHAR(32) NOT NULL
reason VARCHAR(64) NOT NULL
attempt_count INT NOT NULL
last_failure_code VARCHAR(64)
last_failure_message VARCHAR(255)
created_at DATETIME NOT NULL
updated_at DATETIME NOT NULL
completed_at DATETIME
```

第一阶段 `payment_order_id` 唯一，表示最多一次全额退款。

### 7.6 `payment_notifications`

```text
id BIGINT PRIMARY KEY
channel VARCHAR(32) NOT NULL
notification_id VARCHAR(128) NOT NULL
provider_transaction_no VARCHAR(128)
payload_hash VARCHAR(64) NOT NULL
verification_result VARCHAR(32) NOT NULL
processing_result VARCHAR(32)
received_at DATETIME NOT NULL
processed_at DATETIME
UNIQUE(channel, notification_id)
```

保存审计所需字段与报文哈希；敏感完整报文是否保存由后续安全评审决定，默认不长期保存。

### 7.7 `payment_outbox_events`

```text
event_id CHAR(36) PRIMARY KEY
aggregate_type VARCHAR(64) NOT NULL
aggregate_id BIGINT NOT NULL
event_type VARCHAR(64) NOT NULL
event_version INT NOT NULL
payload JSON NOT NULL
occurred_at DATETIME NOT NULL
status VARCHAR(16) NOT NULL
publish_attempts INT NOT NULL
next_attempt_at DATETIME NOT NULL
published_at DATETIME
last_error VARCHAR(500)
```

索引：`(status, next_attempt_at, occurred_at, event_id)`。

### 7.8 `coordination_inbox_events`

```text
id BIGINT PRIMARY KEY
consumer_name VARCHAR(128) NOT NULL
event_id CHAR(36) NOT NULL
event_type VARCHAR(64) NOT NULL
processed_at DATETIME NOT NULL
UNIQUE(consumer_name, event_id)
```

Inbox 插入和预约业务更新必须在同一个数据库事务中提交。

## 八、状态机

### 8.1 预约单

保留当前状态：

```text
PENDING_PAYMENT
CONFIRMED
CANCELLED
EXPIRED
CANCELLED_BY_HOSPITAL
```

异步处理不强制增加 `PAYMENT_CONFIRMING` 预约状态。`PAID + PENDING_PAYMENT` 是短暂的跨模块组合，由只读查询投影为用户可见“预约确认中”。

### 8.2 支付单

```text
UNPAID ──开始尝试──> PAYING ──成功──> PAID
  ▲                    │
  └────尝试失败────────┘

UNPAID/PAYING ──预约关闭──> CLOSED

PAID ──取消预约──> REFUND_PENDING ──退款成功──> REFUNDED

CLOSED ──迟到支付成功──> REFUND_PENDING
```

退款失败时支付单保持 `REFUND_PENDING`，退款记录标记 `FAILED`，等待重试或人工处理。

### 8.3 支付尝试

```text
CREATED → PROCESSING → SUCCEEDED
                     ├→ FAILED
                     └→ CLOSED
```

本地已经 `CLOSED` 后渠道仍确认收款时，外部资金事实优先记录为成功，并把业务支付单转为 `REFUND_PENDING`；不得因为本地关闭而忽略真实收款。

### 8.4 退款单

```text
PENDING → SUCCEEDED
        └→ FAILED → 重试时回到 PENDING
```

## 九、事件与 RabbitMQ

### 9.1 事件信封

所有跨模块消息使用版本化 JSON，不发送 Java 序列化对象：

```json
{
  "eventId": "UUID",
  "eventType": "PaymentSucceeded",
  "eventVersion": 1,
  "aggregateId": "401",
  "occurredAt": "2026-10-29T18:00:00",
  "payload": {}
}
```

消费者只依赖已发布版本的字段。新增非必填字段可以保持版本；删除、改义或改变必填结构必须提升 `eventVersion` 并提供兼容策略。

### 9.2 `PaymentSucceeded` payload

```text
paymentOrderId
appointmentOrderId
paymentAttemptId
amount
currency
channel
paidAt
```

`PaymentSucceeded` 表示“本系统第一次确认渠道已收款”的不可变资金事实，而不是“预约已经确认”。无论支付单当时仍可支付还是已经 `CLOSED`，第一次确认收款都只生成一个稳定 `eventId` 的 `PaymentSucceeded` Outbox；重复通知不得创建新事件。

如果支付单已经 `CLOSED`，回调事务还必须以 `payment_order_id` 唯一约束幂等建立全额退款单，并生成 `RefundRequested`。协调消费者收到同一个 `PaymentSucceeded` 后仍检查预约终态，并调用支付模块的“确保退款存在”用例；该调用与回调侧建立退款互为兜底，但只能命中同一笔退款，不能产生第二笔退款或第二次渠道退款。

### 9.3 退款事件

- `RefundRequested`：退款记录已建立，退款 worker 可以调用渠道。
- `RefundSucceeded`：渠道确认退款成功。
- `RefundFailed`：本次退款失败，可重试或转人工。

支付尝试失败不必默认发布跨模块事件，因为预约仍保持待支付；它应保存在支付查询中供用户重试。

### 9.4 RabbitMQ 拓扑

建议 durable topic exchange：

```text
appoint.domain-events
```

路由键：

```text
payment.succeeded.v1
payment.refund.requested.v1
payment.refund.succeeded.v1
payment.refund.failed.v1
```

队列：

```text
appointment.payment-succeeded.v1
payment.refund-worker.v1
notification.refund-result.v1
```

Dead Letter Exchange：`appoint.domain-events.dlx`，每个业务队列有对应 `.dlq`。

要求：

- Exchange、Queue 和 Binding 可重复声明；
- 消息持久化；
- Publisher Confirm 和 mandatory return；
- 消费事务提交后 ACK；
- 短暂异常使用可配置退避重试；
- 永久业务异常记录结果后 ACK，不进入无限热重试；
- 超过重试阈值进入 DLQ；
- 人工重放保留原 `eventId`，依赖 Inbox 保证幂等。

### 9.5 Outbox 发布器

- 按 `(next_attempt_at, occurred_at, event_id)` 分批读取 `PENDING`。
- 多实例使用数据库行锁和 `SKIP LOCKED`，不重复长期占用同一批记录。
- 发布成功并收到 confirm 后标记 `PUBLISHED`。
- 发布成功但标记前进程崩溃会产生重复消息，因此消费者必须幂等。
- 失败增加 `publish_attempts` 并计算下一次重试时间；达到阈值标记 `FAILED` 并告警，不删除事件。
- 第一阶段使用数据库轮询，不引入 Debezium/CDC。

## 十、事务和并发规则

### 10.1 统一锁顺序

涉及多个写模型的本地协调事务继续使用：

```text
医生排班 → 预约单 → 支付单 → 支付尝试/退款单
```

单独支付回调只锁支付单及其支付尝试，不反向获取预约锁。Rabbit 消费稍后锁预约，因此不会与过期协调形成相反的跨模块锁环。

### 10.2 创建预约

`BookingCoordinator.create` 在一个本地事务内：

1. 登记或校验创建幂等键；
2. 调用预约模块锁排班、就诊人并暂占库存；
3. 预约模块创建预约单、快照和有效预约占用；
4. 预约模块返回金额快照和截止时间；
5. 支付模块创建 `UNPAID` 支付单；
6. 完成幂等响应记录；
7. 一起提交。

任一步失败全部回滚。

### 10.3 创建支付尝试

外部渠道调用不得处于长数据库事务中：

1. 短事务 T1：锁支付单、校验归属/状态/截止/活跃尝试，创建 `CREATED`，支付单转 `PAYING`，提交。
2. 事务外：调用 `PaymentGateway.createPayment`。
3. 短事务 T2：成功则写渠道订单号并转 `PROCESSING`；失败则尝试转 `FAILED`，支付单在仍未被其他结果推进时恢复 `UNPAID`。
4. T1 后进程崩溃产生的长期 `CREATED` 由对账任务查询渠道或安全关闭。

### 10.4 支付回调

1. 回调路径不使用用户 JWT；先根据渠道选择 Adapter 验签。
2. 校验通知 ID、支付单号、渠道订单号、金额和币种。
3. 在事务中登记通知、锁支付单/尝试、推进资金状态并插入 Outbox。
4. 事务提交成功后向渠道返回成功，不等待 RabbitMQ。
5. 完全相同的重复通知返回成功且不重复产生业务副作用；相同通知 ID 携带不同 payload hash 记录安全异常。

### 10.5 支付成功消费

1. 开启事务并插入 Inbox；重复事件直接返回成功。
2. 锁排班和预约单。
3. 若预约仍为有效待支付且支付在截止前已持久化成功，把暂占转为已确认、发放就诊序号并确认预约。
4. 若预约已取消、过期或医院停诊取消，不恢复预约，确保支付模块已建立全额退款。
5. 业务更新和 Inbox 一起提交后 ACK。
6. 临时数据库异常回滚并重试；确定性数据损坏进入 DLQ 和人工处理，不能盲目释放库存。

### 10.6 取消、超时和停诊

- 待支付取消：预约关闭、暂占释放，支付单 `UNPAID/PAYING → CLOSED`，活跃尝试请求关闭。
- 已确认取消：预约立即取消、已确认库存释放，支付单 `PAID → REFUND_PENDING`，创建退款单和 `RefundRequested` Outbox。
- 超时：协调模块锁定预约和支付；已 `PAID` 则保护暂占并恢复确认，未支付则预约过期并关闭支付。
- 医院关闭：待支付预约按当前规则变为 `CANCELLED_BY_HOSPITAL` 或 `EXPIRED` 并释放；已确认预约保持原状态并显示停诊提示。
- 任何关闭后的支付成功都进入退款，不重新占用号源。

## 十一、HTTP 接口目标

### 11.1 创建排班

请求增加：

```json
{
  "doctorId": "1",
  "appointmentDate": "2026-11-08",
  "sessionType": "MORNING",
  "totalCapacity": 20,
  "registrationFee": "50.00",
  "feeCurrency": "CNY"
}
```

到达预约开放时刻后费用不可修改。排班查询展示费用和币种。

### 11.2 创建预约

`POST /api/v1/appointments` 请求保持 `scheduleId + patientId + Idempotency-Key`。成功响应继续返回预约和支付 ID，并增加金额：

```json
{
  "appointmentOrderId": "301",
  "paymentOrderId": "401",
  "appointmentStatus": "PENDING_PAYMENT",
  "paymentStatus": "UNPAID",
  "amount": "50.00",
  "currency": "CNY",
  "paymentExpiresAt": "2026-11-01T08:15:00",
  "serverTime": "2026-11-01T08:00:00"
}
```

### 11.3 创建支付尝试

```http
POST /api/v1/payments/{paymentOrderId}/attempts
Authorization: Bearer ...
Content-Type: application/json

{ "channel": "MOCK" }
```

成功返回 HTTP 202：

```json
{
  "paymentOrderId": "401",
  "paymentAttemptId": "501",
  "channel": "MOCK",
  "paymentStatus": "PAYING",
  "attemptStatus": "PROCESSING",
  "checkoutUrl": "/api/v1/mock-pay/attempts/501",
  "expiresAt": "2026-11-01T08:15:00"
}
```

### 11.4 MockPay

仅 `dev/test` 暴露模拟入口。建议流程：

```http
POST /api/v1/mock-pay/attempts/{attemptId}/complete
POST /api/v1/mock-pay/attempts/{attemptId}/fail
```

入口返回 HTTP 202，Mock Adapter 生成带测试签名的通知并异步进入统一回调处理。测试还应可以直接构造重复、延迟和乱序签名通知。

生产 profile 不注册 Mock Controller 和 Mock Adapter。

### 11.5 渠道回调

建议路径：

```http
POST /api/v1/payment-notifications/{channel}
```

该路径不要求 JWT，但必须通过对应 Adapter 验签。回调响应格式由 Adapter 转换为渠道要求的确认格式；Controller 不自行解释渠道报文。

### 11.6 查询投影

预约详情继续返回预约状态和支付摘要，并增加用户可读的 `workflowStatus`：

| 预约状态 | 支付状态 | workflowStatus | 用户含义 |
| --- | --- | --- | --- |
| `PENDING_PAYMENT` | `UNPAID` | `WAITING_FOR_PAYMENT` | 等待发起支付。 |
| `PENDING_PAYMENT` | `PAYING` | `PAYMENT_PROCESSING` | 渠道处理中。 |
| `PENDING_PAYMENT` | `PAID` | `CONFIRMING_APPOINTMENT` | 已支付，预约确认中。 |
| `CONFIRMED` | `PAID` | `CONFIRMED` | 预约成功并有就诊序号。 |
| `CANCELLED` | `REFUND_PENDING` | `CANCELLED_REFUNDING` | 预约已取消，退款处理中。 |
| `CANCELLED` | `REFUNDED` | `CANCELLED_REFUNDED` | 预约已取消且退款完成。 |
| `EXPIRED` | `CLOSED` | `EXPIRED` | 未支付超时。 |

`workflowStatus` 是只读投影，不是新的预约单领域状态。

### 11.7 取消响应

- 待支付取消完成且不涉及资金：HTTP 200。
- 已确认预约取消后需要异步退款：HTTP 202，预约立即显示 `CANCELLED`，支付显示 `REFUND_PENDING`。
- 原 `POST /api/v1/payments/{id}/simulate-success` 在 dev/test 暂时作为兼容包装触发 MockPay，返回 HTTP 202 并标记废弃；新测试和示例使用支付尝试接口。

## 十二、改造后的完整业务场景

### 场景 A：正常预约并异步支付成功

1. 用户选择排班和就诊人。
2. `BookingCoordinator` 在一个事务中暂占号源、创建预约单和支付单。
3. 用户看到金额、截止时间和 `WAITING_FOR_PAYMENT`。
4. 用户选择 MockPay，系统创建支付尝试并返回 HTTP 202 和 checkout URL。
5. 用户完成 MockPay，Mock Adapter 生成签名异步回调。
6. 支付模块验签、去重，支付尝试转 `SUCCEEDED`、支付单转 `PAID`，同时写 `PaymentSucceeded` Outbox。
7. Outbox 发布 RabbitMQ；协调消费者以 Inbox 幂等确认预约。
8. 库存从暂占转为已确认，预约转 `CONFIRMED` 并取得就诊序号。
9. 用户轮询期间可能短暂看到 `CONFIRMING_APPOINTMENT`，最终看到预约凭证。

### 场景 B：支付尝试失败后重试

1. 第一次尝试 `PROCESSING → FAILED`。
2. 支付单在没有其他成功结果时 `PAYING → UNPAID`。
3. 预约保持 `PENDING_PAYMENT`，暂占继续保留到原截止时间。
4. 用户可以创建第二次尝试；未来可以选择不同渠道。
5. 同时提交的两个新尝试通过支付单行锁只允许一个进入处理。

### 场景 C：支付成功但 RabbitMQ 暂时不可用

1. 渠道回调事务提交 `PAID + Outbox(PENDING)` 并正常确认回调。
2. 用户看到支付已成功、预约确认中。
3. Outbox 发布器持续退避重试；RabbitMQ 恢复后发布事件。
4. 协调消费者确认预约。支付事实不会因 Broker 故障丢失。

### 场景 D：重复回调、重复消息和消费者重启

- 相同 `(channel, notificationId)` 回调只处理一次。
- 相同渠道交易号不能绑定第二次支付成功。
- Outbox 可能重复发布，但 Inbox 只接受一次 `eventId`。
- 预约确认 SQL 仍有状态条件，库存和就诊序号只推进一次。
- 消费者在业务提交前崩溃会重投；提交后 ACK 前崩溃会被 Inbox 判定为重复。

### 场景 E：未支付超时

1. 截止任务锁排班、预约单和支付单。
2. 支付仍为 `UNPAID/PAYING`。
3. 预约转 `EXPIRED`，支付转 `CLOSED`，活跃尝试关闭。
4. 暂占转回可用库存，有效预约占用删除。
5. 用户不能再创建支付尝试。

### 场景 F：截止前已经支付，但确认事件延迟

1. 支付单已经 `PAID`，预约仍为 `PENDING_PAYMENT`。
2. 截止任务不得释放暂占或标记过期。
3. 协调对账检查对应 Outbox/Inbox，重发或重新驱动确认。
4. 最终预约转 `CONFIRMED`，不会发生钱已付但号源被其他人抢走。

### 场景 G：预约关闭后收到迟到支付

1. 预约已 `EXPIRED/CANCELLED/CANCELLED_BY_HOSPITAL`，支付单已 `CLOSED`。
2. 渠道回调仍必须记录真实收款，支付尝试记成功。
3. 支付单转 `REFUND_PENDING`，创建全额退款单和 `RefundRequested`。
4. 预约不恢复，号源不重新扣减。
5. 退款成功后支付单 `REFUNDED`；失败则保持待退款并告警。

### 场景 H：待支付或支付处理中主动取消

1. 用户在截止前取消预约。
2. 协调事务取消预约、释放暂占并关闭支付单/活跃尝试。
3. 没有真实收款时直接完成，无退款。
4. 如果随后出现渠道成功回调，按迟到支付退款。

### 场景 I：已确认预约取消并异步退款

1. 当前时间严格早于开诊前 24 小时，用户申请取消。
2. 协调事务立即把预约转 `CANCELLED`、释放已确认库存，支付单转 `REFUND_PENDING` 并建立退款单。
3. 接口返回 HTTP 202“预约已取消，退款处理中”。
4. 退款 worker 调用原支付渠道；成功后支付单转 `REFUNDED`。
5. 退款失败时预约仍保持取消，系统重试或转人工，不恢复预约和就诊序号。

### 场景 J：医院停诊

- 待支付/支付处理中预约立即 `CANCELLED_BY_HOSPITAL` 或到期记 `EXPIRED`，暂占释放，支付关闭；迟到支付退款。
- 已确认预约维持现有规则：保留预约和序号，详情显示停诊提示；本期不自动退款、通知或改约。

### 场景 K：支付成功与用户取消竞争

协调事务和支付回调都依赖持久化状态与固定锁顺序：

- 取消先完成：预约关闭、支付关闭；后续成功属于迟到支付并退款。
- 支付先完成但事件未消费：取消看到 `PAID`，预约取消并进入退款，不再确认。
- 支付成功事件先确认预约：取消按已确认预约和 24 小时规则处理。

任一路径都不能同时留下有效预约和退款完成，也不能重复释放库存。

## 十三、安全迁移策略

### 13.1 迁移原则

- 只做 Flyway 增量迁移，不重建业务库。
- 先检查旧活跃数据，再执行 DDL。
- 不删除或覆盖历史支付、预约和快照。
- 不为未知历史支付伪造金额、币种或渠道。
- 新代码启动前全部新表和必要列已经存在。
- Legacy 数据继续可读，但不进入新支付、退款和库存生命周期。

### 13.2 建议迁移序列

#### V4：活跃旧支付预检

Java migration 在任何支付结构 DDL 前检查：

- 预约处于 `PENDING_PAYMENT`；
- 支付处于 `UNPAID`；
- 存在当前版本仍可能推进的活跃组合。

存在时中止迁移，保留原结构和数据，要求先关闭或完成旧订单。

#### V5：费用与支付单扩展

- 给 schedules、appointment_orders、payment_orders 增量加列；
- 新列先兼容 Legacy 空值；
- 历史终态支付标记 `legacy=true`；
- 旧支付状态映射到同名新状态，不重写业务含义。

#### V6：支付尝试、退款、通知、Outbox、Inbox

创建新表、外键、唯一约束和扫描索引。

#### V7：状态与金额约束

在回填和兼容验证后增加 `CHECK`，区分 Legacy 与新记录的非空规则。

### 13.3 跨模块外键

第一阶段仍为同一数据库，保留 `payment_orders.appointment_order_id` 外键和唯一约束以保护数据完整性。未来真正拆分支付服务时再通过迁移和事件投影移除跨模块外键；本期不为假设中的微服务牺牲当前数据库保护。

### 13.4 正式切换与回退

V4 预检不能与仍在产生旧活跃支付的流量并行，否则“预检通过”和 V5 DDL 之间仍可能出现新旧订单竞态。正式切换必须按以下顺序执行：

1. 进入维护窗口，停止创建预约、模拟支付、取消、过期扫描和排班关闭等写入口；查询可以继续。
2. 等待正在执行的写事务结束，再执行 V4 活跃旧支付预检；存在活跃支付则终止发布，不执行后续迁移。
3. 按现有发布规范完成逻辑备份并记录校验值。
4. 执行 V5–V7 增量迁移，部署新应用和 RabbitMQ 声明；消费者启动前确认 Exchange、Queue、DLQ 和 Binding 正确。
5. 运行迁移校验、模块健康检查和一条 MockPay 冒烟链路，核对预约、支付、Outbox、Inbox 和库存。
6. 恢复写流量，持续观察 Outbox 最老事件、DLQ、`PAID + PENDING_PAYMENT` 和 `REFUND_PENDING` 指标。

所有 DDL 以增量加表、加列为主。新版本尚未产生新模型数据时，可以停止应用并回退应用版本；一旦已经写入支付尝试、通知、Outbox、Inbox 或退款记录，不执行破坏性 down migration，也不删除新表，而是关闭新入口、保留数据并采用修复版本向前恢复。RabbitMQ 不可用时不回退已经提交的数据库资金结果。

## 十四、对账、自愈与可观测性

### 14.1 对账任务检查

- 支付 `PAID` 但预约长期未确认；
- Outbox `PENDING/FAILED` 超过阈值；
- 消息进入 DLQ；
- 预约已关闭但支付 `PAID` 且没有退款单；
- 支付 `REFUND_PENDING` 长期未退款；
- 支付尝试长期停留在 `CREATED/PROCESSING`；
- 支付金额、币种、用户或预约关联不一致。

任务优先重发事件、查询渠道或重试退款；无法证明安全的情况转人工，不擅自改写资金结果或库存。

### 14.2 日志关联字段

结构化日志至少携带适用的：

```text
eventId
appointmentOrderId/appointmentNo
paymentOrderId/paymentNo
paymentAttemptId/attemptNo
refundId/refundNo
channel
providerOrderNo
providerTransactionNo
```

日志不输出密钥、签名原文、完整身份证、完整渠道敏感报文。

### 14.3 指标

至少暴露：

- Outbox 待发布数量和最老事件年龄；
- Inbox 重复事件数量；
- 各队列消费成功、重试和 DLQ 数量；
- `PAID + PENDING_PAYMENT` 数量和最大持续时间；
- `REFUND_PENDING` 数量和最大持续时间；
- 长期 `CREATED/PROCESSING` 尝试数量；
- 回调验签失败和金额不匹配数量。

## 十五、安全要求

- 普通支付创建、查询和 MockPay 用户操作仍校验当前登录用户对支付单/预约的所有权。
- 渠道回调不使用 JWT；以渠道签名、通知 ID、渠道订单号和金额校验身份与完整性。
- MockPay 使用测试密钥签名并只在 `dev/test` profile 注册；生产 profile 不暴露模拟成功/失败入口。
- 回调先验签再返回详细业务结果，避免未验签报文改变任何状态。
- 相同通知 ID 携带不同 payload hash 视为安全异常。
- 配置密钥来自环境变量或安全配置，不写入仓库和日志。
- Controller、DTO 和日志继续把 64 位 ID 作为字符串输出，避免浏览器精度损失。

## 十六、实施顺序与每步完成条件

后续任务地图应按以下顺序拆 issue；每一步都必须保持仓库可构建并有对应验证。

### 阶段 1：基线与模块契约

工作：

- 为当前支付、取消、过期和并发行为补齐 characterization tests；
- 定义支付、尝试、退款、渠道和 workflow 枚举；
- 建立模块依赖约束测试；
- 不改变现有外部流程。

完成条件：现有 H2 43 项和 MySQL 11 项基线继续通过，模块依赖测试能够在引入反向 Mapper 依赖时失败。

### 阶段 2：费用与数据库模型

工作：

- 实施 V4–V7 安全迁移；
- 编写并演练维护窗口、备份、迁移校验和前向恢复 runbook；
- 排班管理加入挂号费；
- 预约和支付保存金额快照；
- 建立 attempt/refund/notification/outbox/inbox entity 与 mapper。

完成条件：空库迁移、Legacy 终态迁移、活跃旧支付阻断和第二次迁移幂等验证全部通过；切换演练能够阻止预检期间的新写入并验证前向恢复；库存与旧数据未被删除或改写。

### 阶段 3：拆分写模块与本地协调

工作：

- 把支付状态逻辑移出 `AppointmentLifecycleServiceImpl`；
- 把预约创建中的支付单创建移给 `BookingCoordinator`；
- 把用户取消、过期和停诊的跨模块顺序移到协调模块；
- 支付和预约写模块不再互相引用 Mapper。

完成条件：现有同步兼容路径行为不变，包依赖无循环，MySQL 并发测试保持通过。

### 阶段 4：PaymentGateway 与 MockPay

工作：

- 实现 PaymentGateway registry；
- 实现 Mock Adapter、checkout、测试签名和回调验签；
- 实现支付尝试创建、失败恢复和单活跃尝试；
- Mock 路由受 profile 限制。

完成条件：成功、失败、处理中、重复回调、乱序回调、金额不匹配和伪造签名的契约测试通过。

### 阶段 5：Outbox 与 RabbitMQ

工作：

- 声明 Exchange、Queue、Binding、Retry 和 DLQ；
- 实现 Outbox 扫描、Publisher Confirm、退避和失败告警；
- 实现 Inbox 幂等消费者；
- 支付成功改为异步预约确认；
- API 改为 HTTP 202 和轮询流程。

完成条件：Broker 停机期间回调仍成功持久化；恢复后预约自动确认；重复消息不重复确认；重试耗尽进入 DLQ。

### 阶段 6：异步退款与补偿

工作：

- 已确认预约取消创建退款；
- 迟到支付自动创建退款；
- refund worker 调用 Mock Adapter；
- 退款成功/失败回调和重试；
- 退款中查询投影。

完成条件：预约取消后立即释放库存；退款成功最终收敛；退款失败不恢复预约；重复退款请求不产生第二笔全额退款。

### 阶段 7：过期协调与对账

工作：

- 把 `AppointmentExpirationJob` 改为跨模块协调；
- 保护截止前已持久化支付；
- 实现 Outbox、已支付未确认、迟到支付、长期尝试和退款对账；
- 提供受控 DLQ 重放方式。

完成条件：支付与过期、支付与取消、支付与停诊真实竞争测试全部得到一致终态；故障注入后系统能自动或明确转人工收敛。

### 阶段 8：查询、兼容清理和文档

工作：

- 移动跨模块只读 SQL 到 bookingquery；
- 增加 workflowStatus、金额、支付尝试和退款摘要；
- 更新 `docs/api.md`、`docs/business-flow.md`、README、HTTP 示例和演示文档；
- 标记并最终删除旧 `simulate-success`；
- 删除只验证浅层转发的旧单元测试。

完成条件：新用户流程文档与真实 HTTP 测试一致；Legacy 查询仍可用；没有旧同步支付入口被生产 profile 暴露。

## 十七、测试与验收矩阵

### 17.1 PaymentGateway 契约测试

每个 Adapter 必须共享同一套测试：

- 创建支付返回稳定渠道订单标识；
- 同幂等键重复创建不产生第二笔渠道订单；
- 成功、失败和处理中结果映射正确；
- 回调签名验证；
- 重复通知幂等；
- 关闭支付；
- 全额退款幂等；
- 渠道查询映射。

### 17.2 MySQL 集成测试

- 多用户竞争最后库存不超卖；
- 同支付单并发创建尝试只有一个成功；
- 支付回调与取消竞争；
- 支付回调与过期竞争；
- 支付回调与停诊竞争；
- 重复 PaymentSucceeded 只确认一次；
- 退款与重复取消只创建一笔退款；
- 统一锁顺序无死锁，锁异常返回可重试错误；
- 所有终态库存恒等式成立。

### 17.3 RabbitMQ Testcontainers

- Outbox 发布并收到 confirm；
- 发布后、标记前崩溃产生重复消息仍幂等；
- 消费业务提交前失败会重试；
- 业务提交后 ACK 前失败由 Inbox 去重；
- Broker 停机积压、恢复后自动收敛；
- 重试耗尽进入正确 DLQ；
- DLQ 重放保留 eventId 且不重复业务副作用。

### 17.4 MockPay 回调测试

- 正常签名成功；
- 无效签名不改变状态；
- 金额/币种不匹配不改变资金结果；
- 同通知重复；
- 相同通知 ID 不同 payload；
- 失败后重试成功；
- 预约关闭后的迟到成功进入退款；
- 乱序失败通知不能覆盖已成功资金事实。

### 17.5 端到端场景

- 创建预约→支付→最终确认→取得序号；
- 支付失败→第二次尝试→成功；
- 支付处理中取消→迟到成功→退款；
- 未支付超时→释放→其他用户重新预约；
- 支付成功但事件延迟→过期任务保护→最终确认；
- 已确认取消→立即释放→退款成功/失败；
- 医院关闭待支付预约；
- 旧终态订单查询与活跃旧支付迁移阻断。

## 十八、风险与取舍

1. **复杂度上升**：RabbitMQ 不是简单替代方法调用；Outbox、Inbox、DLQ、对账和用户可见中间态都是必要成本。
2. **失去同步原子确认**：支付和预约不再一个事务完成，因此必须接受并正确展示 `PAID + PENDING_PAYMENT`。
3. **同库模块不是微服务**：当前选择优化代码 ownership 和未来 Adapter 扩展，不声称已获得独立部署能力。
4. **数据库轮询 Outbox 的吞吐有限**：符合当前规模；只有实际事件量证明需要时才考虑 CDC。
5. **截止规则偏保守**：预约关闭后即使渠道声称截止前已支付也退款，保护库存不超卖，代价是极少数用户需要等待退款。
6. **退款可能失败**：预约和号源结果不回滚，必须用重试、告警和人工处理解决资金异常。
7. **医院停诊仍有缺口**：已确认预约不会自动退款，本期只保持现状，真实医院使用前必须另行设计完整善后流程。

## 十九、后续暂不实施的扩展

- WeChat Pay、Alipay 真实 SDK 和商户配置；
- 支付模块独立部署与独立数据库；
- Debezium/CDC Outbox；
- 部分退款、多次退款、人工退款和退款改额；
- WebSocket/SSE 支付结果通知；
- 自动通知、停诊改约和已确认预约批量退款；
- 对账文件下载、渠道账单核对和财务报表；
- 多币种、医保或组合支付。

## Comments

- 2026-10-29：通过多轮设计访谈确认模块 ownership、PaymentGateway seam、模块化单体、MockPay 异步回调、Transactional Outbox、RabbitMQ 至少一次投递、Inbox 幂等、DLQ、对账、金额快照、多次支付尝试、迟到支付退款、异步全额退款、安全迁移和测试标准；尚未实施代码改动。
