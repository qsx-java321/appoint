# REST API

所有响应使用统一结构：

```json
{ "code": 0, "message": "success", "data": {} }
```

除注册、登录和支付渠道通知入口外，所有接口都需要 Bearer Token；管理员接口还需要 `ADMIN`。

## 目录与场次

| 方法与路径 | 用途 | 权限 |
| --- | --- | --- |
| `GET /api/v1/hospitals` | 查询医院 | 登录 |
| `GET /api/v1/hospitals/{hospitalId}/departments` | 查询科室 | 登录 |
| `GET /api/v1/departments/{departmentId}/doctors` | 查询医生 | 登录 |
| `GET /api/v1/doctors/{doctorId}/schedules` | 查询未来 7 天的 28 个场次 | 登录 |

场次查询按 `Asia/Shanghai` 返回 D+1 至 D+7，每天依开始时间排序。未生成场次使用当前统一预约设置计算展示值，但 GET 不写数据库；已生成场次返回自身容量和费用快照。

```json
{
  "doctorId": "1",
  "doctorNo": "DOC000001",
  "appointmentDate": "2026-11-11",
  "sessionType": "MORNING_FIRST",
  "startTime": "08:00:00",
  "endTime": "10:00:00",
  "totalCapacity": 20,
  "availableCapacity": 20,
  "registrationFee": "10.00",
  "feeCurrency": "CNY",
  "bookable": true
}
```

四种 `sessionType`：

| 类型 | 固定时间 |
| --- | --- |
| `MORNING_FIRST` | 08:00–10:00 |
| `MORNING_SECOND` | 10:30–12:00 |
| `AFTERNOON_FIRST` | 14:00–16:00 |
| `AFTERNOON_SECOND` | 16:30–18:00 |

`bookable` 只由日期窗口和剩余量决定；约满为 `false`。新响应不包含供用户下单的 `scheduleId`、开放/截止/关闭状态或 `releaseAt`。

## 管理员统一预约设置

| 方法与路径 | 用途 |
| --- | --- |
| `GET /api/v1/admin/booking-settings` | 查询当前设置 |
| `PUT /api/v1/admin/booking-settings` | 完整更新设置 |

更新请求：

```json
{
  "capacityPerSession": 30,
  "registrationFee": 12.00
}
```

容量必须为正整数；费用必须大于零且最多两位小数；币种固定为 CNY。默认值为 `20 / 10.00 / CNY`。更新只影响之后首次生成的场次，不改变已生成场次和订单快照。

旧 `/api/v1/admin/schedules` 创建、修改、库存调整和关闭路由已经移除，调用返回 404。管理员仍可通过目录接口维护医院、科室和医生。

## 创建预约

`POST /api/v1/appointments`

请求必须携带 UUID `Idempotency-Key`：

```json
{
  "doctorId": "1",
  "appointmentDate": "2026-11-11",
  "sessionType": "MORNING_FIRST",
  "patientId": "201"
}
```

请求不接受 `scheduleId`。按服务端 `Asia/Shanghai` 日期，只允许为 D+1 至 D+7 创建新订单；就诊日 `00:00:00` 起当天新订单失败。系统在实际占号 UPDATE 中再次校验日期，随后原子暂占库存、创建预约单和支付单。

幂等摘要为规范化后的 `doctorId + appointmentDate + sessionType + patientId`。同键同内容返回原订单；同键不同内容返回冲突。已取消或过期后重新预约必须使用新键。

首次下单会按需生成场次并复制统一设置。并发首次下单依靠 `(doctor_id, appointment_date, session_type)` 唯一约束收敛到同一场次，条件更新防止超卖。失败事务不保留新场次、暂占或幂等键。

首次创建返回 **201**，data 字段为 `appointmentOrderId`、`paymentOrderId`、`appointmentStatus`、`paymentStatus`、`amount`、`currency`、`paymentExpiresAt`、`serverTime`。幂等重放返回 **200**，data 仅含 `appointmentOrderId`、`appointmentNo`、`replayed=true`；需查询详情获取最新支付状态，不能将重放回执当首次创建响应。详情仍可返回内部 `scheduleId` 以关联历史记录。

示例日期只用于说明；实际请求必须改为服务端明天至第 7 天。

## 支付时间边界

`paymentExpiresAt = orderCreatedAt + 15 分钟`，不再与开诊时间取较早值。订单在 23:59:59 创建时可跨零点支付到次日 00:14:59；恰好到截止时刻即不能发起有效新支付；已经发生的渠道成功回调仍记录收款事实并按迟到支付退款。

截止前渠道已经确认的支付，即使消息稍后消费，仍可确认预约。若预约仍待支付，截止时刻或之后才确认的实际收款会使预约 EXPIRED、释放暂占、删除有效占用并进入原路全额退款；已关闭预约保持终态，只确保退款。paidAt 使用本系统确认收款的服务端时间。过期扫描延迟只影响状态落库时间，不延长支付资格。

## 预约查询和生命周期

| 方法与路径 | 用途 |
| --- | --- |
| `GET /api/v1/appointments/my` | 查询当前账号预约列表 |
| `GET /api/v1/appointments/{id}` | 查询预约详情 |
| `POST /api/v1/appointments/{id}/cancel` | 主动取消 |
| `POST /api/v1/payments/{id}/attempts` | 发起支付尝试 |

查询返回 **200**。取消在无需退款时返回 **200**；进入或仍处于异步退款流程时返回 **202**，message 为“退款申请已受理”，data 仍是预约详情。202 不代表渠道已经退款。重复已关闭订单的取消不重复释放，退款已经完成时返回200。

详情字段保持原契约：`id`、`appointmentNo`、`scheduleId`、`appointmentDate`、`startTime`、`endTime`、`status`、`visitNumber`、`createdAt`、`paymentOrderId`、`paymentStatus`、`workflowStatus`、`amount`、`currency`、`paymentAttemptCount`、`latestPaymentAttempt`、`refund`、`paymentExpiresAt`、`closedAt`、`serverTime`、`paymentExpired`、`canPay`、`canCancel`、`patient`、`doctor`。尝试/退款摘要可为 null；退款状态用于摘要，不决定 workflow。列表各项共享截断到秒的 serverTime。

| status + paymentStatus | workflowStatus |
| --- | --- |
| PENDING_PAYMENT + UNPAID | WAITING_FOR_PAYMENT |
| PENDING_PAYMENT + PAYING | PAYMENT_PROCESSING |
| PENDING_PAYMENT + PAID | CONFIRMING_APPOINTMENT |
| CONFIRMED + PAID | CONFIRMED |
| CANCELLED / CANCELLED_BY_HOSPITAL / EXPIRED + REFUND_PENDING | CANCELLED_REFUNDING |
| 同上 + REFUNDED | CANCELLED_REFUNDED |
| EXPIRED + CLOSED | EXPIRED |
| CANCELLED / CANCELLED_BY_HOSPITAL + CLOSED | null（历史兼容） |

其他组合或缺失/归属不符的支付单按 INTERNAL_ERROR 处理；详情先判断预约不存在/非本人，再检查支付完整性。canPay 只在未到期 PENDING_PAYMENT+UNPAID 为 true。保留既有差异：待支付 UNPAID/PAYING 即使到期仍 canCancel=true，取消入口按时刻关闭为 EXPIRED；待确认已付的 canCancel=false，但取消写入口仍可申请退款。已确认预约必须严格早于开诊前24小时才可取消。

## 支付尝试与通知

`POST /api/v1/payments/{id}/attempts` 需要本人 Token，body 为 `{"channel":"MOCK"}`，成功返回 **202**。data 为 `paymentOrderId`、`paymentAttemptId`、`channel`、`paymentStatus`、`attemptStatus`、`providerOrderNo`、`checkoutUrl`、`expiresAt`。此时一般为 PAYING/PROCESSING；调用 checkoutUrl 并不等于预约已经确认。

仅 dev/test 注册 MockPay：`POST /api/v1/mock-pay/attempts/{attemptNo}/complete` 或 `/fail`，需要登录，返回 **202** 并异步分发签名通知。使用尝试响应中的 checkoutUrl 加 `/complete`；不直接构造同步成功请求。旧 `/api/v1/payments/{id}/simulate-success` 已删除，dev/test/prod 均无该请求映射。

渠道通知 `POST /api/v1/payment-notifications/{channel}` 不要求 Bearer Token，依靠渠道验签；body 为 `notificationId`、`providerOrderNo`、`providerTransactionNo`、`status`、`amount`、`currency`、`occurredAt`、`signature`。现有 MockPay 签名由 Adapter 生成，真实支付渠道尚未接入。响应 **200** 的 data 为 `notificationId`、`processingResult`、`paymentOrderId`、`paymentStatus`、`duplicate`、`eventId`；重复通知部分字段可为 null。未知订单、金额币种或状态拒绝会记录审计并通过 processingResult 表达，不能仅凭HTTP200认为已收款。

客户端轮询预约详情：支付成功可先见 CONFIRMING_APPOINTMENT，再见 CONFIRMED；取消或迟到支付可先见 CANCELLED_REFUNDING，再见 CANCELLED_REFUNDED。轮询不延长支付截止，业务状态和库存以数据库事务为准。

新响应不包含 `scheduleClosed` 或 `hospitalClosureNotice`，新流程也不会产生停诊取消。历史 `CLOSED`、`CANCELLED_BY_HOSPITAL` 数据仍可查询。

## 主要错误

| 场景 | HTTP / code |
| --- | --- |
| 参数、容量、金额或时段非法 | `400 / 1001` |
| 未登录 | `401 / 2001` |
| 普通用户写管理员设置 | `403 / 2002` |
| 资源不存在或旧管理员场次路由 | `404 / 4001` |
| 号源约满或场次不可用 | `409 / 4002` |
| 同场有效预约重复 | `409 / 4204` |
| 日期不在 D+1 至 D+7 | `409 / 4202` |
| 幂等键同键不同内容 | `409 / 4103` |
| 支付已到截止时刻 | `409 / 4004` |
| 支付通知签名无效 | `400 / 4302` |
| 当前订单状态不允许操作 | `409 / 4003` |
| 已确认取消已到24小时边界 | `409 / 4205` |
| 支付渠道不可用 | `503 / 4301` |
| 需要沿原键重试的并发冲突 | `503 / 5001` |
| 查询状态组合或关联完整性异常 | `500 / 1002` |

## 历史兼容

V9 保留旧 `MORNING`、`AFTERNOON`、空时段及历史关闭状态供读取。旧半日场次不会拆分，也不接受新预约；旧活跃订单继续按自身费用和时间快照完成支付、过期或取消。
