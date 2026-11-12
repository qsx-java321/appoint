# 三天预约窗口与场次截止改造设计

> 已交付需求/任务记录。2026-11-12复核：本阶段业务已落地，后续消息设计和测试整理也已完成；实施步骤不是新增待办，最新进度与验证见[当前项目状态](../../docs/project-status.md)。

日期：2026-11-11。

状态：业务方案已认可，五项实现任务和整体验收已完成。本文保留设计基线；实现进度见[任务地图](map.md)，场景定义见[场景清单](scenarios.md)，实际验证见[场景证据](verification/scenario-evidence.md)。

## 1. 目标与范围

将新预约窗口从 D+1～D+7 改为 D～D+2，支持今天、明天、后天；保持每日四个预约场次。每个场次在开始前 15 分钟停止创建新订单；支付截止为订单创建时间加现有支付时限与场次开始时间的较早者，现有配置为 15 分钟。允许跨零点下单和支付。

本设计围绕正常浏览、选择、下单、支付、查询、当天签到展开，并覆盖截止与日期滚动这些正常业务边界。退款、渠道异常、消息故障和恢复机制不展开设计。

验收主线：查询得到 12 项有明确可预约状态的场次；今天符合资格的场次可以下单；预约单和支付单共享创建时间及支付截止；停止接新订单后，原订单仍按自己的期限支付；既有预约确认、当天签到与医生接诊继续可用。

## 2. 已认可的业务口径

| 事项 | 目标行为 |
| --- | --- |
| 时区与精度 | 继续注入 `appointmentClock`，使用上海业务时间，按现有秒精度处理 |
| 日期范围 | `today <= appointmentDate <= today + 2 days`，包含两个端点 |
| 日期入口 | 今天、明天、后天；请求仍传具体日期，不传“今天”等相对标签 |
| 日期滚动 | 每天 00:00 自然滚动，新后天进入展示窗口，无需预生成场次的午夜任务 |
| 场次 | 08:00–10:00、10:30–12:00、14:00–16:00、16:30–18:00 |
| 下单截止 | `bookingClosesAt = appointmentDate.atTime(startTime).minusMinutes(15)` |
| 下单资格 | 日期合法、有可用名额，且实际占号准入时间严格早于下单截止 |
| 支付截止 | `min(createdAt + paymentTimeout, appointmentDate.atTime(startTime))`；`paymentTimeout` 继续配置为 `15m` |
| 支付资格 | 使用支付单已经保存的截止；到达截止即失去资格，不随刷新或重试重算 |
| 停止新下单后 | 已创建订单仍可以在本单期限内支付 |
| 查询行为 | 查询不生成场次、不占用库存；首次创建仍按需生成场次并复制设置快照 |
| 已有订单 | 不按新窗口筛掉历史订单，不重算已有支付截止，不取消已有超出三天入口的预约 |
| 付款后流程 | 继续通过既有消息处理确认预约、分配号码；沿用现有当天签到与接诊 |

四场当天新下单截止依次为 07:45、10:15、13:45、16:15。时间相等时已截止。

当支付时限恰为 15 分钟、下单必须提前超过 15 分钟时，正常新订单的 `createdAt + 15m` 已早于场次开始；`min` 同时明确支付上限，不能因此允许任意订单一直付到开诊。

## 3. 改造前基线与本轮实现

| 代码位置 | 改造前行为 | 已落实行为 |
| --- | --- | --- |
| `catalog.controller.CatalogController.schedules` | 医生场次 GET 入口 | 路径、权限与签名保持，扩展响应说明 |
| `catalog.service.CatalogService.listSchedules` | 返回场次数组 | 保持 interface，更新契约为三天十二项 |
| `catalog.service.impl.CatalogServiceImpl.listSchedules` | D+1～D+7；`available > 0` 决定 `bookable` | 一次采样上海时间，生成 D～D+2，结合时间与库存计算可预约状态 |
| `catalog.dto.ScheduleResponse` | 日期、时段、库存、费用、`bookable` | 追加下单截止、可预约状态、统一服务端时间 |
| `appointment.application.AppointmentHoldServiceImpl` | 只准未来七天；旧创建时间计算支付截止 | 共用纯时间规则；锁后准入采样；计算并保存新截止 |
| `catalog.mapper.ScheduleMapper.holdOneFutureSession` | SQL 硬性要求 `appointment_date > today` | 替换为支持三天与开始前 15 分钟的条件占号方法 |
| `coordination.application.BookingCreationTransaction` | 事务开头采样时间，传给暂占命令 | 请求登记时间与预约准入时间分开，支付只复制暂占结果 |
| `appointment.application.CreateAppointmentHoldCommand` | 含外层 `createdAt` | 去掉外层时间参数，由暂占实现负责采样预约创建时间 |
| `appointment.application.AppointmentHold` | 返回创建时间和截止 | 保持结构，明确时间来源改为实际暂占准入时刻 |
| `payment.application.PaymentOrderCreationServiceImpl` | 复制命令中的金额、时间与截止 | 继续复用，不自行计算场次截止 |
| `payment.application.PaymentAttemptTransaction.create` | 锁支付单后按持久化 `expiresAt` 校验 | 继续复用，不再检查新订单窗口或下单截止 |
| `bookingquery.application.AppointmentResponseAssembler` | 根据已存截止和状态计算 `canPay` | 继续复用；不把新下单截止混入原订单支付资格 |

已新增 [ADR 0007](../../docs/adr/0007-three-day-booking-and-session-cutoffs.md)，明确替代 ADR 0005 的 D+1～D+7、禁止当天创建和支付截止决定；保留按需生成、四场、快照等既有决定。ADR 0006 的既有支付确认语义继续沿用。

## 4. 包结构与新增类型

保持模块化单体，不拆 Maven 模块，不新增预约时间微服务。

```text
com.qsx.appoint
├── catalog
│   ├── controller/CatalogController.java                 复用
│   ├── service/CatalogService.java                      契约注释更新
│   ├── service/impl/CatalogServiceImpl.java              修改
│   ├── dto/ScheduleResponse.java                        修改
│   ├── model/AppointmentSession.java                    复用
│   ├── model/BookingTimeRules.java                      新增，纯时间规则
│   ├── model/ScheduleAvailability.java                  新增，查询展示枚举
│   └── mapper/ScheduleMapper.java                       修改条件占号
├── appointment
│   ├── controller/AppointmentController.java            复用
│   ├── dto/CreateAppointmentRequest.java                更新日期契约注释
│   ├── dto/CreateAppointmentResponse.java               字段保持，更新时间语义注释
│   ├── application/CreateAppointmentHoldCommand.java    修改，移除外层创建时间
│   ├── application/AppointmentHoldService.java          更新契约注释
│   ├── application/AppointmentHoldServiceImpl.java      修改
│   ├── application/AppointmentHold.java                 更新契约注释
│   └── config/AppointmentProperties.java                复用现有支付时限
├── coordination
│   └── application/BookingCreationTransaction.java      修改时间传递
├── payment                                               复用持久化截止与支付尝试链路
├── bookingquery                                          复用我的预约与详情查询
└── visit                                                 复用当天签到与医生接诊
```

将 `BookingTimeRules` 放在现有 `catalog.model`，与 `AppointmentSession` 一起表达预约场次的可选择时间。当前 catalog 和 appointment 已共享场次知识；该位置可被查询与暂占复用，无需让 catalog 反向依赖 appointment，也无需让 payment 引用预约规则。

### 4.1 BookingTimeRules

一个无 Spring 注解、无数据库和 Clock 依赖的 `public final` 类，直接接受调用者采样的时间。只提供实际被复用的方法，不再加 interface、工厂或多实现策略。

已实现的方法：

```java
public static List<LocalDate> selectableDates(LocalDateTime serverTime);

public static LocalDateTime bookingClosesAt(
        LocalDate appointmentDate, LocalTime startTime);

public static boolean canCreate(
        LocalDate appointmentDate, LocalTime startTime,
        LocalDateTime acceptanceTime);

public static LocalDateTime paymentExpiresAt(
        LocalDateTime createdAt, LocalDateTime sessionStartsAt,
        Duration paymentTimeout);
```

窗口长度 3 天、提前量 15 分钟集中定义。为 SQL 构造最早场次开始阈值时，也从这个类的提前量常量 `MINIMUM_BOOKING_ADVANCE` 取得 `Duration`，不在 Mapper、Service 重复硬编码 15。

所有 `LocalDateTime` 输入遵循上海业务时间，规则不自行转换宿主默认时区。支付时限继续使用现有 `AppointmentProperties.paymentTimeout()`，配置保持 `15m`；不新增管理员时间配置 REST 操作或设置表字段。

### 4.2 ScheduleAvailability

```java
public enum ScheduleAvailability {
    AVAILABLE,
    FULL,
    CUTOFF_REACHED,
    STARTED
}
```

只表示 GET 返回时的可预约状态，不写入 `schedules.status`，也不增加预约生命周期状态。

在查询内部按下面顺序计算：

1. 当前时间已到场次开始：`STARTED`。
2. 还未开诊但已到下单截止：`CUTOFF_REACHED`。
3. 尚未截止但可用名额为零：`FULL`。
4. 其余：`AVAILABLE`。

`bookable` 唯一由 `availability == AVAILABLE` 得出，避免布尔值与展示枚举矛盾。已过时段可统一显示“已开诊，不再接受预约”，本轮不为它增加独立结束状态。

## 5. REST 契约

本轮不新增 REST 路径。现有资源操作可以表达全部正常流程；不增加“今天下单”“后天下单”“校验是否可约”等平行接口。

| 操作 | 方法与路径 | 本轮变化 |
| --- | --- | --- |
| 查医院 | `GET /api/v1/hospitals` | 复用 |
| 查科室 | `GET /api/v1/hospitals/{hospitalId}/departments` | 复用 |
| 查医生 | `GET /api/v1/departments/{departmentId}/doctors` | 复用 |
| 查就诊人 | `GET /api/v1/patients` | 复用 |
| 查场次 | `GET /api/v1/doctors/{doctorId}/schedules` | 由 28 项改为 12 项，增加三个响应字段 |
| 创建预约 | `POST /api/v1/appointments` | 请求与成功响应字段保持，采用新时间规则 |
| 发起支付 | `POST /api/v1/payments/{id}/attempts` | `{id}` 是支付单 ID，保持请求和响应 |
| 查详情 | `GET /api/v1/appointments/{id}` | 保持，继续展示实际支付截止与流程状态 |
| 查我的预约 | `GET /api/v1/appointments/my?order=DESC` | 保持全部历史查询，不套用三天新预约窗口 |
| 当天签到 | `POST /api/v1/appointments/{id}/check-in` | 复用 |

既有登录、医生四场概览、名单、开始和完成接诊接口继续复用。本轮不增加新的角色或安全过滤规则；上表继续要求已登录和现有账号归属约束。

### 5.1 场次查询响应

保持 `Result<List<ScheduleResponse>>` 和 `data` 数组，避免将它改为新的分页或分组结构。前端按 `appointmentDate` 分组为三个日期入口，十二条数据无需增加按日期筛选请求。

保留原字段，追加：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `bookingClosesAt` | 日期时间字符串 | 该场次创建新订单的截止 |
| `availability` | 枚举字符串 | 当前展示状态 |
| `serverTime` | 日期时间字符串 | 本次查询统一采样时间，全部十二项一致 |

为保持原数组结构，`serverTime` 在各项重复返回；仅十二项，优先保证当前契约的连续性。它表示查询快照时间，不保证用户提交时仍有资格。

示例：上海时间 2026-11-11 10:05 查询上午第二段。以下 `data` 仅截取一项，完整响应为十二项。

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "doctorId": "1",
      "doctorNo": "DOC001",
      "appointmentDate": "2026-11-11",
      "sessionType": "MORNING_SECOND",
      "startTime": "10:30:00",
      "endTime": "12:00:00",
      "totalCapacity": 20,
      "availableCapacity": 20,
      "registrationFee": "10.00",
      "feeCurrency": "CNY",
      "bookable": true,
      "bookingClosesAt": "2026-11-11T10:15:00",
      "availability": "AVAILABLE",
      "serverTime": "2026-11-11T10:05:00"
    }
  ]
}
```

医生编号、容量和金额均为示例，实际使用目录和设置快照。内部场次 ID 继续不作为创建预约请求的输入。

### 5.2 创建预约

```http
POST /api/v1/appointments
Authorization: Bearer <accessToken>
Idempotency-Key: <UUID>
Content-Type: application/json

{
  "doctorId": "1",
  "appointmentDate": "2026-11-11",
  "sessionType": "AFTERNOON_FIRST",
  "patientId": "101"
}
```

不接收客户端 `userId`、价格、创建时间、下单截止或支付截止。时间、资格与金额在服务端决定。

首次保持 HTTP 201，以下为服务端 13:40 创建的示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "appointmentOrderId": "1001",
    "paymentOrderId": "2001",
    "appointmentStatus": "PENDING_PAYMENT",
    "paymentStatus": "UNPAID",
    "amount": "10.00",
    "currency": "CNY",
    "paymentExpiresAt": "2026-11-11T13:55:00",
    "serverTime": "2026-11-11T13:40:00"
  }
}
```

字段结构不变，`serverTime` 改为创建结果组装时重新采样的时间，不继续拿事务开始时刻冒充响应时间。前端倒计时以返回的绝对截止和服务端时间估计，不固定从页面展示开始倒数 15 分钟。

同键同内容的既有 HTTP 200 重放契约保留。它不是创建新订单，不因滚动后的三天窗口重新判定原订单是否能创建。

### 5.3 支付与查询

```http
POST /api/v1/payments/2001/attempts
Content-Type: application/json

{"channel":"MOCK"}
```

继续返回 HTTP 202；`PaymentAttemptResponse.expiresAt` 复制支付单中的截止。不得在发起支付时调用 `BookingTimeRules.canCreate`，否则停止新下单后原订单也会被错误禁止支付。

用户通过原预约详情 GET 查询：既有 `paymentExpiresAt`、`serverTime`、`paymentExpired`、`canPay`、`paymentStatus`、`workflowStatus`、`visitNumber` 足够支撑支付、确认与凭证展示。本轮不新建一个平行的预约支付状态接口，不加实时推送。

## 6. 查询实现流程

`CatalogServiceImpl.listSchedules(doctorId)`：

1. 检查医生是否存在，沿用现有返回契约。
2. 一次采样秒精度 `serverTime`。
3. 调用 `BookingTimeRules.selectableDates(serverTime)` 得到三天。
4. 查询该医生三天内已存在的四类场次。
5. 读取统一设置，用于尚未生成的场次展示容量与费用。
6. 按日期、`AppointmentSession.values()` 生成十二项。
7. 计算每项 `bookingClosesAt` 和 `availability`，进而生成 `bookable`。
8. 返回数组；全程不创建场次、不调整库存。

同一次查询只使用一个时间采样，避免十二行在零点或截止点分属不同判断时刻。

## 7. 下单事务与时间采样

保留外层 `DefaultBookingCoordinator` 与独立事务 Bean `BookingCreationTransaction`，不将原事务拆开，也不让 Controller 直接操作 Mapper。

```text
AppointmentController.create
→ BookingCoordinator.create
→ BookingCreationTransaction.create                  外层同库事务
  → 登记账号内幂等请求（requestRegisteredAt）
  → AppointmentHoldService.createHold
    → 解析场次、快速检查日期与截止
    → 检查医生
    → 锁统一设置，生成或锁定实际场次
    → 锁本人就诊人，读取完整目录与就诊人资料
    → 采样 acceptedAt，最终检查三天窗口与下单截止
    → 条件占号 available - 1 / held + 1
    → 保存预约、凭证、费用快照与有效占用
    → 返回 AppointmentHold（createdAt = acceptedAt）
  → PaymentOrderCreationService.createOrder
    → 原样复制金额、createdAt、paymentExpiresAt
  → 组装创建结果，完成幂等回执
→ HTTP 201
```

### 7.1 预约创建时间由暂占实现负责

改造前，`BookingCreationTransaction` 在获取场次和就诊人锁之前采样 `now`，并通过 `CreateAppointmentHoldCommand.createdAt` 传给暂占实现，导致锁等待提前消耗支付期限。该时间参数现已移除。

本轮已调整为：

- 外层只保留请求登记用 `requestRegisteredAt`。
- `CreateAppointmentHoldCommand` 移除 `createdAt`，保留用户、医生、具体日期、场次和就诊人。
- `AppointmentHoldServiceImpl` 在完成锁定与必要读取后、实际条件占号前采样 `acceptedAt`。
- 用同一个 `acceptedAt` 判断准入、保存预约 `createdAt`/`updatedAt`、保存占用创建时间，并计算支付截止。
- `AppointmentHold` 返回这个时间，支付单直接复制。
- `CreateAppointmentResponse.serverTime` 在结果组装时另行采样。

准入时间不是 HTTP 点击时间、不是客户端收到响应时间，也不定义为事务提交完成时间。锁后采样避免前面锁等待提前消耗订单期限；网络传输和之后事务执行仍会消耗一部分时间，因此实际剩余时长以绝对截止为准。

### 7.2 SQL 占号需要同步改造

原 `holdOneFutureSession` 的 `appointment_date > today` 会直接拒绝今天；现已替换为 `holdOneBookableSession`，与 Java 校验共同约束日期和截止。

已使用的方法：

```java
int holdOneBookableSession(
        @Param("scheduleId") Long scheduleId,
        @Param("firstBookableDate") LocalDate firstBookableDate,
        @Param("lastBookableDate") LocalDate lastBookableDate,
        @Param("earliestStartDate") LocalDate earliestStartDate,
        @Param("earliestStartTime") LocalTime earliestStartTime);
```

由同一个 `acceptedAt` 推导参数：

```text
firstBookableDate = acceptedAt.toLocalDate()
lastBookableDate  = firstBookableDate + 2 days
earliestStartAt  = acceptedAt + MINIMUM_BOOKING_ADVANCE
earliestStartDate / earliestStartTime = earliestStartAt 的日期 / 时间
```

首尾日期从共享规则返回的三天中取得。提前量只来自 `BookingTimeRules`，不复制常量到调用方。

保留四类场次、OPEN、可用库存等原条件，资格部分改为：

```sql
AND appointment_date BETWEEN #{firstBookableDate} AND #{lastBookableDate}
AND (
    appointment_date > #{earliestStartDate}
    OR (
        appointment_date = #{earliestStartDate}
        AND start_time > #{earliestStartTime}
    )
)
```

它等价于 `sessionStartsAt > acceptedAt + 15m`，即 `acceptedAt < bookingClosesAt`。日期与时间拆分比较，便于沿用 H2/MySQL 测试，不新增数据库方言时间函数；也不使用数据库 `CURRENT_DATE` 或 `NOW()` 判定业务资格。原 `updated_at` 的记账赋值不在这里扩展修改。

条件占号继续在已持有的场次锁内执行；Java 与 SQL 使用同一次准入采样。窗口滚动后的日期合法性也以该次采样为准。

## 8. 支付与预约详情如何接续

新的支付截止只在创建阶段计算一次：

```text
AppointmentHoldServiceImpl
  计算 paymentExpiresAt
    ↓
AppointmentHold
    ↓
CreatePaymentOrderCommand
    ↓
payment_orders.expires_at
```

预约单 `payment_expires_at` 与支付单 `expires_at` 必须完全一致。

支付尝试创建继续锁支付单并检查持久化截止；向 MockPay 传递该截止。详情继续读取已保存的截止计算 `canPay`；不因现在已经不能新下单、日期入口已经滚动或订单不在三天入口内而重算。

停止新下单后原订单支付，是本轮必须明确验证的正常场景。示例：10:14 为 10:30 场创建的订单，10:15 后还可在本单 10:29 截止前支付。

沿用现有及时收款的异步预约确认；正常流程最终仍得到 `CONFIRMED / PAID` 与就诊序号。既有 `PaymentSucceeded` 事件与存储状态无需增加新版本或新枚举。

## 9. 数据库与配置

不增加业务表、不新增 Flyway 迁移，不回填旧订单，也不增加新的手工场次维护接口。

- `schedules.appointment_date/start_time/end_time` 足够计算场次开始和新下单截止。
- `appointment_orders.created_at/payment_expires_at`、`payment_orders.created_at/expires_at` 足够保存订单时限。
- 继续使用既有医生/日期/场次唯一约束、库存列与有效占用表。
- `bookingClosesAt` 和查询 `availability` 为派生值，无需持久化。
- 保持 `appoint.appointment.payment-timeout: 15m` 与上海 `appointmentClock`。
- 所有历史订单继续保留其已有时间快照；三天限制只作用于创建新订单。

## 10. 正常场景与代码、验收对应

| 场景 | 用户结果 | 实现位置与检查点 |
| --- | --- | --- |
| 10:05 查询今天、明天、后天 | 十二项，今天上午第一场不可约，第二场可约 | `CatalogServiceImpl`，统一时间与新字段 |
| 今天 13:40 预约 14:00 场 | 创建成功，支付截止 13:55 | 纯规则、暂占、预约/支付双快照 |
| 今天 13:44 创建 14:00 场订单 | 13:45 后原订单仍可支付，截止 13:59 | 支付入口只读持久化截止 |
| 13:45 新建 14:00 场订单 | 已停止接受新订单 | 查询状态与最终 Java/SQL 判断一致 |
| 08:30 浏览 08:00 场 | 可见为已开诊，不提供新预约 | 查询派生状态 |
| 今天预约后天 | 可创建，仍在创建后十五分钟内支付 | 三天窗口包含 D+2，支付与就诊日期分离 |
| 23:55 预约次日 08:00 场 | 支付截止次日 00:10 | 创建时 min 计算，日期时间完整保存 |
| 请求处理跨零点，原明天变今天 | 尚未到该场截止时仍可创建 | 最终准入采样；旧测试预期必须改写 |
| 凌晨日期入口滚动 | 展示新今天至后天；原订单仍可查 | 查询与订单详情职责分开 |
| 已有订单现在不在新窗口内 | 详情仍可读，已有截止保持 | 我的预约不套用目录窗口 |
| 付款后查看进度与凭证 | 原字段显示确认进度与号码 | 复用详情查询和既有确认流程 |
| 当天预约确认后签到、接诊 | 使用现有动作推进就诊记录 | 复用 visit；不新增时间状态机 |

## 11. 验证计划

以下保留设计时的验证计划；实际执行已完成，详见任务地图、各任务 Comments 和场景证据，不把历史测试结果作为本轮结果。

进入实现后按以下层次验证；截止资格验证属于本需求本身，不扩展渠道故障、退款或消息恢复场景设计。

### 11.1 纯规则测试

新增 `catalog/model/BookingTimeRulesTest`，覆盖三天日期列表、四场截止、严格边界、支付 min 计算、跨零点。规则不依赖 Spring 或真实数据库。

### 11.2 现有 HTTP 测试调整

- `catalog/SessionCatalogHttpTest`：28 改 12，首日期由明天改今天、末日期为后天；固定时钟下分别检查当天已开诊/已截止/可预约，以及新字段和全行统一 `serverTime`；继续验证 GET 不生成场次。
- `catalog/CatalogManagementIntegrationTest`：同步新增医生立即查询场次的十二项预期，保留其目录管理测试目的。
- `appointment/OnDemandBookingHttpTest`：将“拒绝今天、接受七天内”改为“接受今天合法场次及后天、拒绝窗口外日期”；测试固定时钟为中午时，今天成功用下午场，不能机械选当天上午第一场。
- 同类幂等用例原固定时钟 11/05、目标 11/08 已变成窗口外，应换成 11/07 的合法场次，保留其原有断言目的。
- `appointment/BookingMidnightIntegrationTest`：原来“明天变今天必失败”的前提已经失效，改为验证跨零点后合法当天场次可创建；同时保留原成功键可重放。避免继续依赖固定第几次 Clock 调用跨日的脆弱断言，可使用受控时钟与明确的占号检查点。
- 增加正常下单→发起支付→MockPay 完成→查询确认的用例，证明预约与支付截止一致、停止新下单后旧单仍能支付。

### 11.3 MySQL 与整体链路

- 真实 MySQL 运行条件占号，验证同日场次的资格及库存变化；继续使用现有事务与唯一约束，不重建库存设计。
- 检查 `AppointmentLifecycleMySqlIT` 等新建预约夹具中 D+7 的目标，只有调用新创建流程的夹具才迁入三天窗口；直接用于历史查询或生命周期事实的记录不机械改写。
- `PatientJourneyHttpMySqlIT` 中场次数量 28 改 12；选择可预约场次，不固定使用当天数组第一项。
- `scripts/compose_acceptance.py` 当前使用上海日期加 3 天创建正常预约，必须改为加 2 天；这样全天执行时都能落入新窗口。原有专门验收内容不在本轮扩展重写。
- 秒级截止、当天及跨零点用受控业务 Clock 测试，不修改宿主系统时间。真实 Compose 普通主线可以使用后天，避免受执行时刻影响。
- 运行常规测试、MySQL profile、既有架构检查和正常 HTTP 链路；这属于后续实现验收，不在本文声称已通过。

## 12. 文档与实施顺序

五步实施已按以下顺序完成，实际结果见[场景证据](verification/scenario-evidence.md)：

1. **集中时间规则。** 新增 `BookingTimeRules` 和纯规则测试，四场截止与跨日计算明确。
2. **调整目录查询。** 修改查询 Service 和 DTO，验证三天十二项及状态；现有 GET 仍不写库。
3. **调整创建事务。** 更改暂占命令、锁后时间采样、支付截止计算、SQL 条件及协调层时间传递，验证预约与支付两份时间快照一致。
4. **串联正常流程。** 验证原支付尝试、详情、确认、当天签到及医生接诊；不在支付入口加新下单资格检查。
5. **同步文档与验收。** 更新 README、正式 API、业务流程、模块设计、HTTP 示例；新增 ADR 0007，更新相关领域术语。验收报告新增本次结果，不改写历史测试记录。

正式文档中需要同步的主要位置：`README.md`、`docs/api.md`、`docs/business-flow.md`、`docs/module-design.md`、`docs/visit/design.md` 中对公共预约目录的描述、`docs/runbooks/payment-module-cutover.md` 的正常冒烟场次数量、`http/appointment.http`。历史 ADR 0005 保留，并通过新 ADR 明确替代的范围；旧交付记录中的 28 项事实保持历史含义。

最终交付应能从同一套规则追踪到：用户场次状态、创建资格、占号 SQL、订单支付期限、详情按钮和对应正常场景测试。

## 13. 实现任务入口

以下任务已按依赖顺序完成；需求和技术契约以本文为基线，任务中的场景编号对应[场景清单](scenarios.md)。

| 任务 | 交付内容 | 依赖 |
| --- | --- | --- |
| [01 共享预约时间规则](issues/01-booking-time-rules.md) | `BookingTimeRules` 与规则测试 | 无 |
| [02 三天场次查询](issues/02-session-query.md) | 十二项场次、展示状态及三个新增响应字段 | 01 |
| [03 预约创建与占号](issues/03-appointment-creation.md) | 锁后准入时间、三天与截止 SQL、订单时间快照 | 01、02 |
| [04 正常支付与就诊链路](issues/04-payment-flow.md) | 原订单支付资格、正常确认、历史查询、签到接诊及验收入口适配 | 02、03 |
| [05 文档同步与整体验收](issues/05-docs-and-acceptance.md) | 现行文档、ADR、HTTP 示例与实际验证记录 | 01、02、03、04 |

以上五项已逐项实现并验收。具体状态、依赖、实际执行记录见[任务地图](map.md)，十八个场景的证据见[场景验收](verification/scenario-evidence.md)。
