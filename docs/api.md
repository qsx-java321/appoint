# HTTP API：预约、独立支付与可靠事件

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
| `POST /api/v1/appointments/{id}/cancel` | 取消预约；需要退款时异步受理 | 登录 | 200/202 |
| `POST /api/v1/payments/{id}/attempts` | 创建支付尝试 | 登录 | 202 |
| `POST /api/v1/payment-notifications/{channel}` | 支付渠道签名回调 | 渠道验签 | 200 |
| `POST /api/v1/mock-pay/attempts/{attemptNo}/complete` | MockPay 完成支付 | dev/test | 202 |
| `POST /api/v1/mock-pay/attempts/{attemptNo}/fail` | MockPay 支付失败 | dev/test | 202 |
| `POST /api/v1/admin/hospitals` | 创建医院 | `ADMIN` | 201 |
| `PATCH /api/v1/admin/hospitals/{hospitalId}` | 修改医院资料 | `ADMIN` | 200 |
| `DELETE /api/v1/admin/hospitals/{hospitalId}` | 删除空医院 | `ADMIN` | 200 |
| `POST /api/v1/admin/hospitals/{hospitalId}/departments` | 创建科室 | `ADMIN` | 201 |
| `PATCH /api/v1/admin/departments/{departmentId}` | 修改科室名称 | `ADMIN` | 200 |
| `DELETE /api/v1/admin/departments/{departmentId}` | 删除空科室 | `ADMIN` | 200 |
| `POST /api/v1/admin/departments/{departmentId}/doctors` | 创建医生档案 | `ADMIN` | 201 |
| `PATCH /api/v1/admin/doctors/{doctorId}` | 修改医生档案 | `ADMIN` | 200 |
| `DELETE /api/v1/admin/doctors/{doctorId}` | 删除无排班医生 | `ADMIN` | 200 |
| `POST /api/v1/admin/schedules` | 创建排班 | `ADMIN` | 201 |
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

## 管理员医疗资源接口

管理员可以按医院、科室、医生、排班的顺序建立可预约目录。新建资源立即进入已有公共查询，不存在草稿或上下架状态。所有管理写接口要求 `ADMIN`；普通用户调用返回 `403 / 2002 FORBIDDEN`。

医院、科室和医生请求中的字符串在写入前会去除首尾空格；提供纯空白字符串返回 `400 / 1001 VALIDATION_ERROR`。修改请求至少要提供一个允许修改的字段。

创建医院：

```http
POST /api/v1/admin/hospitals
```

```json
{ "name": "示例市第二人民医院", "address": "示例市健康路 20 号" }
```

医院可用 `PATCH /api/v1/admin/hospitals/{hospitalId}` 修改名称或地址。医院仍有科室时不能删除；空医院可用对应 `DELETE` 接口删除。

在医院下创建科室：

```http
POST /api/v1/admin/hospitals/{hospitalId}/departments
```

```json
{ "name": "心血管内科" }
```

同一医院内，去除首尾空格后的科室名称不能重复；不同医院允许同名科室。`PATCH /api/v1/admin/departments/{departmentId}` 只修改名称，科室仍有医生时不能删除。

在科室下创建医生档案：

```http
POST /api/v1/admin/departments/{departmentId}/doctors
```

```json
{
  "name": "王医生",
  "title": "副主任医师",
  "introduction": "擅长心血管常见疾病诊治。"
}
```

系统自动生成全局唯一且稳定的 `doctorNo`。医生修改接口只接受姓名、职称和简介，不接受 `doctorNo` 或 `departmentId`。医生存在任何过去、未来、关闭或 Legacy 排班时都不能删除；停诊应关闭排班而不是删除排班。

医院、科室和医生资料修改只影响目录及之后创建的预约，已有预约凭证继续显示创建时保存的快照。创建科室、医生或排班时父级不存在统一返回 `404 / 4001 RESOURCE_NOT_FOUND`。

## 管理员排班接口

创建排班请求：

```json
{
  "doctorId": "1",
  "appointmentDate": "2026-11-08",
  "sessionType": "MORNING",
  "totalCapacity": 20,
  "registrationFee": 50.00,
  "feeCurrency": "CNY"
}
```

`sessionType` 仅允许 `MORNING` 或 `AFTERNOON`，对应固定时段 `08:00–12:00` 和 `14:00–17:00`。同一医生、日期、时段只能有一场。初始容量和 `registrationFee` 必须大于 0，第一阶段币种只能是 `CNY`。只要尚未到预约截止，即使理论开放时间已过，也可补录并立即开放。

开放前可用 `PATCH` 修改排班身份与费用。到达理论开放时间后，医生、日期、时段、金额和币种全部锁定。

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
  "amount": 50.00,
  "currency": "CNY",
  "paymentExpiresAt": "2026-11-01T08:15:00",
  "serverTime": "2026-11-01T08:00:00"
}
```

支付截止为 `min(创建时间 + 15 分钟, 开诊时间)`。放号边界包含等号：到达就诊日前第 7 日 08:00 可预约；截止边界不包含等号：到达开诊前 30 分钟即拒绝新暂占。

同一排班中，相同 `patientId` 或相同“证件类型＋创建时规范化证件号”只能有一张有效预约。第二层约束跨账号生效，冲突只返回统一错误，不泄露其他账号信息。取消或过期释放占用后，可用新的幂等键重新预约。同一就诊人预约其他排班不受影响。

`Idempotency-Key` 在账号内唯一。同账号同键同内容重试返回 HTTP 200、原 `appointmentOrderId`、`appointmentNo` 和 `replayed=true`；同键不同内容返回 `IDEMPOTENCY_CONFLICT`。失败并回滚的请求不占用键。

## 支付尝试、MockPay 与异步确认

`POST /api/v1/payments/{paymentOrderId}/attempts` 请求体为 `{ "channel": "MOCK" }`。服务在短事务中创建尝试，事务外调用渠道，再以短事务保存渠道结果；成功返回 HTTP 202：

```json
{
  "paymentOrderId": "401",
  "paymentAttemptId": "501",
  "channel": "MOCK",
  "paymentStatus": "PAYING",
  "attemptStatus": "PROCESSING",
  "providerOrderNo": "mock-order-example",
  "checkoutUrl": "mockpay://checkout/mock-order-example",
  "expiresAt": "2026-11-01T08:15:00"
}
```

dev/test 可调用 `POST /api/v1/mock-pay/attempts/{attemptNo}/complete` 或 `/fail` 模拟渠道异步通知；`attemptNo` 可从 `checkoutUrl` 的末段取得。通知入口不使用 JWT，由 Adapter 验签并校验通知 ID、渠道订单、金额和币种。完成接口返回 202 后应轮询预约详情：可能短暂显示 `CONFIRMING_APPOINTMENT`，消费者确认后变为 `CONFIRMED` 并取得 `visitNumber`。支付失败可在截止前重新发起尝试，同一支付单最多一个处理中尝试。

旧 `POST /api/v1/payments/{id}/simulate-success` 仅是 dev/test 废弃兼容包装，生产 profile 不注册。新集成不得依赖它。

## 预约详情、取消与退款

预约详情在原有快照字段外新增：

| 字段 | 含义 |
| --- | --- |
| `visitNumber` | 确认时取得的就诊序号；待支付时为 `null` |
| `paymentExpired` | 已过期，或待支付且当前时间已到截止 |
| `canPay` | 待支付、未过期且排班未关闭 |
| `canCancel` | 待支付；或已确认且当前时间严格早于开诊前 24 小时 |
| `workflowStatus` | 用户流程状态：等待支付、处理中、确认中、已确认、退款中、已退款或已过期 |
| `amount` / `currency` | 创建预约时复制的不可变费用快照；Legacy 可为 `null` |
| `paymentAttemptCount` / `latestPaymentAttempt` | 尝试次数与最近一次尝试摘要 |
| `refund` | 全额退款摘要；未退款时为 `null` |
| `scheduleClosed` | 排班是否已关闭 |
| `hospitalClosureNotice` | 已确认且排班关闭时为“医生已停诊，请联系医院” |

`POST /api/v1/appointments/{id}/cancel` 的状态流转：

| 当前状态和时间 | 预约单 | 支付单 | 库存 |
| --- | --- | --- | --- |
| 待支付且未到截止 | `CANCELLED` | `CLOSED` | 暂占转可用 |
| 待支付且已到截止 | `EXPIRED` | `CLOSED` | 暂占转可用 |
| 已确认，且当前时间 `< 开诊时间 - 24h` | `CANCELLED` | `REFUND_PENDING`，异步后 `REFUNDED` | 已确认立即转可用 |
| 已确认，且当前时间 `>= 开诊时间 - 24h` | 保持 `CONFIRMED` | 保持 `PAID` | 不变，返回 4205 |
| 已取消、已过期或医院停诊取消 | 返回当前详情 | 保持 | 不重复释放 |

已确认取消需要退款时返回 HTTP 202 和“退款申请已受理”；号源立即释放，退款异步执行，失败不恢复预约或库存。待支付取消没有资金，仍返回 HTTP 200。关闭后收到迟到支付不会恢复预约，而会幂等建立唯一的原路全额退款。原 `visitNumber` 保留且不复用。

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
| 同一医院科室名称重复 | 409 | `4401 DEPARTMENT_NAME_EXISTS` |
| 医疗资源仍被下级资源引用 | 409 | `4402 CATALOG_RESOURCE_IN_USE` |
| 并发幂等请求尚未完成 | 503 | `5001 RETRYABLE_CONCURRENCY_ERROR` |

可运行示例见 [auth.http](../http/auth.http)、[admin-catalog.http](../http/admin-catalog.http)、[admin-schedule.http](../http/admin-schedule.http) 和 [appointment.http](../http/appointment.http)。
