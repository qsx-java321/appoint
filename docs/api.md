# HTTP API：医生排班、统一库存与预约暂占

Base URL：`http://localhost:8080`。除注册与登录外，接口均要求 `Authorization: Bearer <accessToken>`。用户身份从 Token 获取，请求中不传 `userId`。时间均按 `Asia/Shanghai` 解释，精确到秒。

成功响应统一为 `{ "code": 0, "message": "success", "data": ... }`。错误响应的 `data` 为 `null`。所有 `Long` ID 在响应中编码为 JSON 字符串；路径 ID 及请求中的 ID 可使用字符串或数字。

## 接口总览

| 方法与路径 | 用途 | 权限 | 成功 HTTP |
| --- | --- | --- | ---: |
| `POST /api/v1/auth/register` | 注册账号 | 公开 | 201 |
| `POST /api/v1/auth/login` | 登录并获取 JWT | 公开 | 200 |
| `GET /api/v1/users/me` | 当前账号 | 登录 | 200 |
| `GET /api/v1/hospitals` | 医院列表 | 登录 | 200 |
| `GET /api/v1/hospitals/{hospitalId}/departments` | 医院科室 | 登录 | 200 |
| `GET /api/v1/departments/{departmentId}/doctors` | 科室医生，含 `doctorNo` | 登录 | 200 |
| `GET /api/v1/doctors/{doctorId}/schedules` | 医生未来排班及可预约状态 | 登录 | 200 |
| `POST /api/v1/patients` | 创建就诊人 | 登录 | 201 |
| `GET /api/v1/patients` | 本账号全部就诊人 | 登录 | 200 |
| `GET /api/v1/patients/{id}` | 本账号一位就诊人 | 登录 | 200 |
| `PATCH /api/v1/patients/{id}` | 修改就诊人 | 登录 | 200 |
| `PATCH /api/v1/patients/{id}/status` | 启用或停用就诊人 | 登录 | 200 |
| `POST /api/v1/appointments` | 暂占一个排班名额 | 登录 | 201 |
| `GET /api/v1/appointments/{id}` | 自己的一张预约凭证 | 登录 | 200 |
| `GET /api/v1/appointments/my` | 自己的预约列表 | 登录 | 200 |
| `POST /api/v1/appointments/{id}/cancel` | 取消、过期关闭或模拟退款 | 登录 | 200 |
| `POST /api/v1/payments/{id}/simulate-success` | 模拟支付成功 | 登录 | 200 |
| `POST /api/v1/admin/schedules` | 创建排班 | `ADMIN` | 200 |
| `PATCH /api/v1/admin/schedules/{id}` | 开放前修改排班身份 | `ADMIN` | 200 |
| `POST /api/v1/admin/schedules/{id}/inventory-adjustments` | 调整库存并记审计 | `ADMIN` | 200 |
| `POST /api/v1/admin/schedules/{id}/close` | 关闭排班 | `ADMIN` | 200 |

## 登录、角色与就诊人

注册和登录请求为 `{ "email": "student@example.com", "password": "password123" }`，密码长度 8–64。管理员角色由运维直接写入账号角色关系后授予；系统没有公开提权接口。角色变化后必须重新登录，取得包含 `ADMIN` 的新 Token。

初期只接受 `RESIDENT_ID_CARD`。居民身份证会校验结构、出生日期和校验位，末位 `x` 规范化为 `X`。同一账号内证件唯一；不同账号可以登记相同证件。就诊人停用后仍保留资料和账号内唯一性，只阻止新预约。

创建预约只能选择当前账号下已启用的就诊人。预约单保存创建时的就诊人、医院、科室、医生和排班快照；之后修改或停用就诊人不会改变历史凭证。

## 医生排班查询

`GET /api/v1/doctors/{doctorId}/schedules` 返回该医生尚未结束的全部新模型排班，包括尚未开放、已约满、已到截止时间和已关闭场次。V1 旧单号源不进入列表。

```json
{
  "id": "4",
  "doctorId": "1",
  "doctorNo": "DOC000001",
  "appointmentDate": "2026-11-08",
  "sessionType": "MORNING",
  "startTime": "08:00:00",
  "endTime": "12:00:00",
  "status": "OPEN",
  "totalCapacity": 20,
  "availableCapacity": 3,
  "availabilityStatus": "AVAILABLE",
  "bookable": true,
  "releaseAt": "2026-11-01T08:00:00",
  "bookingCutoffAt": "2026-11-08T07:30:00"
}
```

`availabilityStatus` 的值为：

| 值 | 含义 | `bookable` |
| --- | --- | --- |
| `NOT_OPEN` | 未到就诊日前第 7 个自然日 08:00 | `false` |
| `AVAILABLE` | 已开放、未截止、排班开放且有可用名额 | `true` |
| `FULL` | 可用名额为 0 | `false` |
| `CUTOFF_REACHED` | 已到开诊前 30 分钟截止边界 | `false` |
| `CLOSED` | 医院已关闭排班 | `false` |

查询结果只是即时快照。创建预约时会在事务内重新检查排班状态、时间窗口、重复预约和库存。

## 管理员排班接口

创建排班请求：

```json
{
  "doctorId": "1",
  "appointmentDate": "2026-11-08",
  "sessionType": "MORNING",
  "totalCapacity": 20
}
```

`sessionType` 仅允许 `MORNING` 或 `AFTERNOON`，对应固定时段 `08:00–12:00` 和 `14:00–17:00`。同一医生、日期、时段只能有一场。初始容量必须大于 0。只要尚未到预约截止，即使理论开放时间已过，也可补录并立即开放。

开放前可用 `PATCH` 修改 `doctorId`、`appointmentDate`、`sessionType` 中至少一项。到达理论开放时间后，这三个字段全部锁定。

库存调整请求：

```json
{ "delta": 5, "reason": "临时加号" }
```

`delta` 不能为 0。开诊前可增减；减少量不得超过当前可用名额，不会挤掉暂占或已确认预约。响应返回调整后的总量、可用、暂占、已确认量；系统另存操作者、原因、调整后数量和时间。

关闭仅允许在开诊前执行且不可重开。关闭后待支付预约立即释放：已经到支付截止的记为 `EXPIRED`，其余记为 `CANCELLED_BY_HOSPITAL`，支付单均为 `CLOSED`。已确认预约保留，详情显示停诊提示。

管理员排班响应示例：

```json
{
  "id": "101",
  "doctorId": "1",
  "appointmentDate": "2026-11-08",
  "sessionType": "MORNING",
  "startTime": "08:00:00",
  "endTime": "12:00:00",
  "status": "OPEN",
  "totalCapacity": 20,
  "availableCapacity": 18,
  "heldCapacity": 1,
  "confirmedCapacity": 1,
  "nextVisitNumber": 2,
  "closedAt": null,
  "closedBy": null
}
```

## 创建预约与幂等

`POST /api/v1/appointments` 要求 UUID 请求头 `Idempotency-Key`，请求体为：

```json
{ "scheduleId": "101", "patientId": "201" }
```

成功暂占一个名额并在同一事务中创建预约单、支付单、有效预约占用和快照：

```json
{
  "appointmentOrderId": "301",
  "paymentOrderId": "401",
  "appointmentStatus": "PENDING_PAYMENT",
  "paymentStatus": "UNPAID",
  "paymentExpiresAt": "2026-11-01T08:15:00",
  "serverTime": "2026-11-01T08:00:00"
}
```

支付截止为 `min(创建时间 + 15 分钟, 开诊时间)`。放号边界包含等号：到达就诊日前第 7 日 08:00 可预约；截止边界不包含等号：到达开诊前 30 分钟即拒绝新暂占。

同一排班中，相同 `patientId` 或相同“证件类型＋创建时规范化证件号”只能有一张有效预约。第二层约束跨账号生效，冲突只返回统一错误，不泄露其他账号信息。取消或过期释放占用后，可用新的幂等键重新预约。同一就诊人预约其他排班不受影响。

`Idempotency-Key` 在账号内唯一。同账号同键同内容重试返回 HTTP 200、原 `appointmentOrderId`、`appointmentNo` 和 `replayed=true`；同键不同内容返回 `IDEMPOTENCY_CONFLICT`。失败并回滚的请求不占用键。

## 模拟支付与就诊序号

`POST /api/v1/payments/{paymentOrderId}/simulate-success` 只推进持久化状态，不发生资金操作。截止前成功后：

```json
{
  "id": "401",
  "paymentNo": "PAYexample",
  "paymentStatus": "PAID",
  "appointmentOrderId": "301",
  "appointmentStatus": "CONFIRMED",
  "visitNumber": 18
}
```

支付使库存从暂占转为已确认，并按同场确认顺序发放 `visitNumber`。序号单调递增且取消后不复用，因此可能大于当前总容量。重复支付已确认订单返回同一成功结果。到达支付截止后立即拒绝，后台扫描延迟不构成宽限。

## 预约详情、取消与退款

预约详情在原有快照字段外新增：

| 字段 | 含义 |
| --- | --- |
| `visitNumber` | 确认时取得的就诊序号；待支付时为 `null` |
| `paymentExpired` | 已过期，或待支付且当前时间已到截止 |
| `canPay` | 待支付、未过期且排班未关闭 |
| `canCancel` | 待支付；或已确认且当前时间严格早于开诊前 24 小时 |
| `scheduleClosed` | 排班是否已关闭 |
| `hospitalClosureNotice` | 已确认且排班关闭时为“医生已停诊，请联系医院” |

`POST /api/v1/appointments/{id}/cancel` 的状态流转：

| 当前状态和时间 | 预约单 | 支付单 | 库存 |
| --- | --- | --- | --- |
| 待支付且未到截止 | `CANCELLED` | `CLOSED` | 暂占转可用 |
| 待支付且已到截止 | `EXPIRED` | `CLOSED` | 暂占转可用 |
| 已确认，且当前时间 `< 开诊时间 - 24h` | `CANCELLED` | `REFUNDED` | 已确认转可用 |
| 已确认，且当前时间 `>= 开诊时间 - 24h` | 保持 `CONFIRMED` | 保持 `PAID` | 不变，返回 4205 |
| 已取消、已过期或医院停诊取消 | 返回当前详情 | 保持 | 不重复释放 |

已确认取消成功时接口消息为“模拟全额退款成功”。原 `visitNumber` 保留在历史预约中，不再发给其他预约。关闭排班后的已确认预约仍可按同一 24 小时规则自行取消。

## 主要错误契约

| 情况 | HTTP | `code` / 业务名 |
| --- | ---: | --- |
| 参数不合法 | 400 | `1001 VALIDATION_ERROR` |
| 未登录或 Token 无效 | 401 | `2001 UNAUTHORIZED` |
| 权限不足 | 403 | `2002 FORBIDDEN` |
| 资源不存在 | 404 | `4001 RESOURCE_NOT_FOUND` |
| 排班无可用名额或重复时段 | 409 | `4002 SCHEDULE_UNAVAILABLE` |
| 订单状态不允许操作 | 409 | `4003 ORDER_STATE_ERROR` |
| 支付截止时间已到 | 409 | `4004 PAYMENT_EXPIRED` |
| 排班已开诊 | 409 | `4005 SCHEDULE_STARTED` |
| 就诊人证件在账号内重复 | 409 | `4101 PATIENT_DOCUMENT_EXISTS` |
| 就诊人已停用 | 409 | `4102 PATIENT_DISABLED` |
| 幂等键用于不同内容 | 409 | `4103 IDEMPOTENCY_CONFLICT` |
| 排班尚未开放 | 409 | `4201 SCHEDULE_NOT_OPEN` |
| 新预约截止时间已到 | 409 | `4202 SCHEDULE_CUTOFF_REACHED` |
| 排班已关闭 | 409 | `4203 SCHEDULE_CLOSED` |
| 本场已有同就诊人或同证件有效预约 | 409 | `4204 DUPLICATE_ACTIVE_APPOINTMENT` |
| 已到开诊前 24 小时取消边界 | 409 | `4205 CANCELLATION_DEADLINE_REACHED` |
| 排班身份字段已锁定 | 409 | `4206 SCHEDULE_IDENTITY_LOCKED` |
| 减少量超过当前可用名额 | 409 | `4207 INVENTORY_REDUCTION_EXCEEDS_AVAILABLE` |
| 并发幂等请求尚未完成 | 503 | `5001 RETRYABLE_CONCURRENCY_ERROR` |

可运行示例见 [auth.http](../http/auth.http)、[admin-schedule.http](../http/admin-schedule.http) 和 [appointment.http](../http/appointment.http)。
