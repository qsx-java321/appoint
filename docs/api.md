# HTTP API：当前预约业务

Base URL：`http://localhost:8080`。除注册与登录外，接口均要求 `Authorization: Bearer <accessToken>`；用户身份从 Token 获取，请求中不传 `userId`。成功响应统一为：

```json
{ "code": 0, "message": "success", "data": {} }
```

错误响应的 `data` 为 `null`，HTTP 状态码和业务 `code` 同时用于判定结果。所有 `Long` 类型 ID 在响应中统一编码为 JSON 字符串，避免浏览器丢失 64 位整数精度；路径 ID 以及请求体中的 `scheduleId`、`patientId` 可使用字符串或数字。`code` 和 `expiresIn` 等非 ID 数值保持数字。示例中的日期时间均按 `Asia/Shanghai` 理解，JSON 时间值不带时区偏移，业务精度为秒。可运行的请求见 [http/auth.http](../http/auth.http) 和 [http/appointment.http](../http/appointment.http)。

## 接口总览

| 方法与路径 | 用途 | 成功 HTTP |
|---|---|---:|
| `POST /api/v1/auth/register` | 注册用户 | 201 |
| `POST /api/v1/auth/login` | 登录并获取 JWT | 200 |
| `GET /api/v1/users/me` | 当前用户资料 | 200 |
| `GET /api/v1/hospitals` | 医院列表 | 200 |
| `GET /api/v1/hospitals/{hospitalId}/departments` | 医院科室 | 200 |
| `GET /api/v1/departments/{departmentId}/doctors` | 科室医生，包含 `doctorNo` | 200 |
| `GET /api/v1/doctors/{doctorId}/schedules` | 该医生未来且 `AVAILABLE` 的号源 | 200 |
| `POST /api/v1/patients` | 创建就诊人 | 201 |
| `GET /api/v1/patients` | 本人全部就诊人，含停用资料 | 200 |
| `GET /api/v1/patients/{id}` | 本人的一位就诊人 | 200 |
| `PATCH /api/v1/patients/{id}` | 局部修改就诊人 | 200 |
| `PATCH /api/v1/patients/{id}/status` | 启用或停用就诊人 | 200 |
| `POST /api/v1/appointments` | 创建预约并暂占号源 | 201 |
| `GET /api/v1/appointments/{id}` | 自己的一张预约凭证 | 200 |
| `GET /api/v1/appointments/my` | 自己的预约列表 | 200 |
| `POST /api/v1/appointments/{id}/cancel` | 取消自己的待支付预约 | 200 |
| `POST /api/v1/payments/{id}/simulate-success` | 模拟支付成功 | 200 |

## 登录与查询资源

注册请求：`{ "email": "student@example.com", "password": "password123" }`，密码长度为 8–64。登录使用相同字段，响应 `data.accessToken` 用于后续 Bearer 请求，`data.expiresIn` 由当前配置决定。首次迁移只写入角色，用户账号需要自行注册。

先获取医院 ID，再获取科室 ID、医生 ID，最后查询医生号源。号源响应包含 `id`、`appointmentDate`、`startTime`、`endTime`、`status`；只返回查询时仍未开始、且状态为 `AVAILABLE` 的号源。用户提交时服务端再次校验，不能仅凭查询结果认定号源仍可用。

## 就诊人管理

创建预约前先创建就诊人：

```json
{
  "name": "张三",
  "documentType": "RESIDENT_ID_CARD",
  "documentNo": "11010519491231002X",
  "contactPhone": "13800138000",
  "contactEmail": "patient@example.com"
}
```

初期证件类型只接受 `RESIDENT_ID_CARD`。证件号码必须是合法的 18 位居民身份证号码，末位 `x` 保存为 `X`；系统校验结构、出生日期和校验位，不核验证件归属。姓名、电话和邮箱去除首尾空白，邮箱统一为小写。响应不返回完整证件号，而返回形如 `110105********002X` 的 `documentNoMasked`。

同一账号内 `(documentType, documentNo)` 唯一，资料停用后仍不能重复登记；不同账号可以登记相同证件。列表包含启停状态并按 ID 倒序。`PATCH /api/v1/patients/{id}` 只修改已提交字段，必填字段不能清空；调用方不需要回传脱敏证件号。状态接口请求体为 `{ "enabled": false }` 或 `{ "enabled": true }`，重复设置返回当前结果。

读取、修改、启停或预约使用其他账号的就诊人返回 403；不存在返回 404。停用只阻止新的预约，不影响历史预约的查询、支付、取消和过期处理。

## 创建预约

`POST /api/v1/appointments` 要求 Bearer Token 和 UUID 请求头：

```http
Idempotency-Key: 11d18b50-9c6c-4fa8-bf01-dfb79f6e2ff2
```

请求体同时指定号源和就诊人：

```json
{ "scheduleId": "1", "patientId": "101" }
```

成功返回 HTTP 201。`appointmentOrderId` 用于查凭证或取消，`paymentOrderId` 用于模拟支付：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "appointmentOrderId": "101",
    "paymentOrderId": "201",
    "appointmentStatus": "PENDING_PAYMENT",
    "paymentStatus": "UNPAID",
    "paymentExpiresAt": "2026-10-26T14:15:00",
    "serverTime": "2026-10-26T14:00:00"
  }
}
```

只能选择本人已启用的就诊人。截止时间为“创建时间 + 配置的保留时长”和“就诊开始时间”的较早值，默认保留 15 分钟；该值创建后保存在订单中。创建已开诊号源返回 409/`SCHEDULE_STARTED`，提交已占用号源返回 409/`SCHEDULE_UNAVAILABLE`。

幂等键在当前账号内生效并规范化为小写。同账号使用同一键和相同 `scheduleId + patientId` 重试，不再占号，返回 HTTP 200 和原预约的简短回执：

```json
{
  "code": 0,
  "message": "该请求已创建预约，请查询详情获取当前状态",
  "data": {
    "appointmentOrderId": "101",
    "appointmentNo": "APexample",
    "replayed": true
  }
}
```

回执不包含支付单 ID 或预约、支付状态；调用方应使用 `appointmentOrderId` 查询详情。同一键配合不同号源或就诊人返回 409/`IDEMPOTENCY_CONFLICT`，不同账号可以使用相同键。首次请求若失败并回滚，不会保留幂等记录，修复失败原因后可用原键重试。成功键永久关联原预约，即使预约已经支付、取消或过期，或就诊人已修改、停用，旧键仍返回同一简短回执。用户明确再次预约时必须生成新键。

## 预约详情和“我的预约”

`GET /api/v1/appointments/{id}` 返回自己的完整凭证。以下以创建后、尚未支付的订单为例，示例字段与当前响应一致：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "101",
    "appointmentNo": "APexample",
    "scheduleId": "1",
    "appointmentDate": "2099-01-01",
    "startTime": "09:00:00",
    "endTime": "09:30:00",
    "status": "PENDING_PAYMENT",
    "createdAt": "2026-10-26T14:00:00",
    "paymentOrderId": "201",
    "paymentStatus": "UNPAID",
    "paymentExpiresAt": "2026-10-26T14:15:00",
    "closedAt": null,
    "serverTime": "2026-10-26T14:00:00",
    "paymentExpired": false,
    "canPay": true,
    "canCancel": true,
    "patient": {
      "id": "101",
      "name": "张三",
      "documentType": "RESIDENT_ID_CARD",
      "documentNoMasked": "110105********002X",
      "contactPhone": "13800138000",
      "contactEmail": "patient@example.com"
    },
    "doctor": {
      "id": "1",
      "doctorNo": "DOC000001",
      "name": "张医生",
      "title": "主任医师",
      "department": { "id": "1", "name": "内科" },
      "hospital": {
        "id": "1",
        "name": "示例市第一人民医院",
        "address": "示例市健康路 1 号"
      }
    }
  }
}
```

`GET /api/v1/appointments/my` 的 `data` 是上述详情结构组成的数组，按预约 ID 倒序；没有预约时为 `[]`。列表一次请求中的各项使用相同 `serverTime`。`createdAt` 是下单时间，不是就诊时间。凭证中的就诊人、联系方式、医院、科室、医生、日期和时段都是创建时的快照；四种预约状态都可查询。就诊人之后修改或停用不会改变历史凭证。

操作标记的计算：

| 字段 | 含义 |
|---|---|
| `paymentExpired` | 已处于 `EXPIRED`；或仍为 `PENDING_PAYMENT`，但 `serverTime >= paymentExpiresAt` |
| `canPay` | `PENDING_PAYMENT + UNPAID` 且尚未到截止时间 |
| `canCancel` | `PENDING_PAYMENT + UNPAID`；到期未扫描时仍可请求取消，结果会是 `EXPIRED` |
| `closedAt` | 实际首次关闭时间；未关闭时为 `null` |

操作标记只反映查询当时的状态，写接口始终重新读取和校验。查询不会提前关闭到期订单。已确认预约超过原支付截止时间后，`paymentExpired` 仍为 `false`。他人的预约返回 403，预约不存在返回 404。

## 取消预约

`POST /api/v1/appointments/{id}/cancel` 不需要请求体，成功 HTTP 200 的 `data` 与预约详情相同：

| 当前状态 | 结果 |
|---|---|
| 自己的待支付，未到截止时间 | `CANCELLED + CLOSED`，释放号源 |
| 自己的待支付，已到截止时间 | `EXPIRED + CLOSED`，释放号源 |
| 已取消或已过期 | 返回当前详情，不重复释放，`closedAt` 不变 |
| 已确认 | 409/`ORDER_STATE_ERROR` |
| 他人的预约或不存在 | 分别为 403、404 |

取消和过期只关闭订单，不删除凭证。释放后的号源可以产生新的预约；旧预约的重复请求不会改变新预约。

## 模拟支付

`POST /api/v1/payments/{id}/simulate-success` 中的 `id` 是**支付单 ID**。仍可支付时返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "201",
    "paymentNo": "PAYexample",
    "paymentStatus": "PAID",
    "appointmentOrderId": "101",
    "appointmentStatus": "CONFIRMED"
  }
}
```

状态同时推进为 `CONFIRMED + PAID + BOOKED`。已经成功支付的重复请求返回同一成功结果，即使此时已超过原截止时间。已取消预约支付返回 409/`ORDER_STATE_ERROR`；已过期或待支付但达到截止时间，返回 409/`PAYMENT_EXPIRED`。到期支付请求只拒绝付款，不在该请求中关闭订单；释放由取消操作或后台扫描完成。

## 错误契约

| 情况 | HTTP | `code` / 业务名 |
|---|---:|---|
| 请求参数不合法 | 400 | `1001 VALIDATION_ERROR` |
| 未登录或 Token 无效 | 401 | `2001 UNAUTHORIZED` |
| 操作他人订单 | 403 | `2002 FORBIDDEN` |
| 资源不存在 | 404 | `4001 RESOURCE_NOT_FOUND` |
| 号源已被占用 | 409 | `4002 SCHEDULE_UNAVAILABLE` |
| 已取消预约支付、已确认预约取消 | 409 | `4003 ORDER_STATE_ERROR` |
| 支付期限已过 | 409 | `4004 PAYMENT_EXPIRED` |
| 号源已开始就诊 | 409 | `4005 SCHEDULE_STARTED` |
| 同账号重复登记同一证件 | 409 | `4101 PATIENT_DOCUMENT_EXISTS` |
| 新预约使用已停用就诊人 | 409 | `4102 PATIENT_DISABLED` |
| 同一幂等键用于不同请求内容 | 409 | `4103 IDEMPOTENCY_CONFLICT` |
| 锁等待超时或数据库死锁，可使用原键重试 | 503 | `5001 RETRYABLE_CONCURRENCY_ERROR` |
| 关联支付单缺失、归属不一致等数据异常 | 500 | `1002 INTERNAL_ERROR` |

已有的登录失败 `2003 LOGIN_FAILED`、邮箱已注册 `3001 EMAIL_EXISTS` 和用户不存在 `3002 USER_NOT_FOUND` 由各自入口使用。缺失请求头、非法 UUID、非法 ID 和字段校验失败均使用 400/`VALIDATION_ERROR`。未识别的数据库约束或异常数据会记录服务端错误并安全失败，不冒充邮箱或证件重复，也不会输出完整证件号码。
