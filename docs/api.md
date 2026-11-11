# REST API

本文记录当前已实现的接口。到院签到、医生看板及接诊的 7 个新增接口与原预约查询扩展见 [就诊 API 契约](visit/api.md)。

所有响应使用统一结构：

```json
{ "code": 0, "message": "success", "data": {} }
```

除注册、登录和支付渠道通知入口外，所有接口都需要 Bearer Token；管理员接口还需要 `ADMIN`。

响应中的 Java `Long` 类型 ID 统一序列化为 JSON 字符串，例如 `"id":"201"`，避免浏览器整数精度丢失。客户端应按不透明标识保存；容量、就诊序号及 `expiresIn` 等数值字段仍为数字。金额的字符串形式以对应 DTO 为准，例如创建预约的 `amount` 和设置中的 `registrationFee` 为字符串，退款摘要中的 `amount` 为 JSON 数值。

日期使用 `yyyy-MM-dd`；业务时间按 `Asia/Shanghai` 解释，`LocalDateTime` 响应不带时区偏移。以下响应字段均指统一包装内的 `data`；错误时 `data=null`，`code` 和 HTTP 状态见“主要错误”。

## 账号注册、登录与当前用户

| 方法与路径 | 权限 | 请求 | 成功响应 |
| --- | --- | --- | --- |
| `POST /api/v1/auth/register` | 匿名 | `email`、`password` | 201，用户对象 |
| `POST /api/v1/auth/login` | 匿名 | `email`、`password` | 200，Token 与用户对象 |
| `GET /api/v1/users/me` | 登录 | 无请求体 | 200，当前用户对象 |

注册、登录请求示例：

```json
{
  "email": "student@example.com",
  "password": "password123"
}
```

邮箱必须非空并符合邮箱格式，保存和登录查找前会去除首尾空白、转为小写。注册密码必须为 8–64 个字符；登录密码要求非空。

用户对象为 `id`、`email`、`roles`，不含密码。注册只赋予 `USER`；本地开发的管理员授权见 [README](../README.md#本地开发管理员初始化)。重复邮箱返回 `409 / 3001`，账号或密码错误返回 `401 / 2003`。

登录响应为 `accessToken`、`tokenType`（`Bearer`）、`expiresIn`（秒，当前配置 7200）和 `user`。后续请求携带 `Authorization: Bearer <accessToken>`；未提供或无效/过期 Token 访问受保护接口返回 `401 / 2001`。`users/me` 查询当前数据库角色，但请求权限来自 JWT 中的角色，授权后应重新登录。当前用户记录不存在返回 `404 / 3002`。

## 就诊人管理

所有接口只操作当前账号的就诊人，不接受客户端指定归属账号。就诊人 ID 与账号用户 ID 不同。

| 方法与路径 | 请求 | 成功响应 |
| --- | --- | --- |
| `POST /api/v1/patients` | 完整就诊人资料 | 201，就诊人对象，初始 `enabled=true` |
| `GET /api/v1/patients` | 无请求体 | 200，本人就诊人数组；无数据为 `[]` |
| `GET /api/v1/patients/{id}` | 无请求体 | 200，就诊人对象 |
| `PATCH /api/v1/patients/{id}` | 部分就诊人资料 | 200，更新后的就诊人对象 |
| `PATCH /api/v1/patients/{id}/status` | `{"enabled":false}` 或 `{"enabled":true}` | 200，就诊人对象 |

创建请求：

```json
{
  "name": "示例就诊人",
  "documentType": "RESIDENT_ID_CARD",
  "documentNo": "11010519491231002X",
  "contactPhone": "13800138000",
  "contactEmail": "patient@example.com"
}
```

创建时五个字段都必填且不能为纯空白；长度上限依次为 `64 / 32 / 32 / 32 / 120`。目前证件类型只支持 `RESIDENT_ID_CARD`，证件号码去除首尾空白并转大写，校验 18 位格式、出生日期、部分编码及校验位；不进行外部实名认证。联系电话要求非空和长度合法，联系邮箱另校验格式并转小写。

资料 PATCH 可提交上述任意字段，缺省或 `null` 字段保留原值，传入空白值不能清空必填资料；当前实现接受空对象并返回更新后的记录。状态 PATCH 必须提供非 null 的布尔值 `enabled`。停用只限制后续创建预约，不自动取消已有预约或修改凭证快照。当前没有删除就诊人的接口。

就诊人对象为 `id`、`name`、`documentType`、`documentNoMasked`、`contactPhone`、`contactEmail`、`enabled`、`createdAt`、`updatedAt`，不返回完整证件号。资料修改不改写历史预约快照。

记录不存在返回 `404 / 4001`，非本人返回 `403 / 2002`，本账号重复证件返回 `409 / 4101`；不同账号可以登记相同证件，但创建同一场次预约时仍受有效占用唯一约束限制。停用就诊人创建预约返回 `409 / 4102`。

## 目录与场次

| 方法与路径 | 用途 | 权限 |
| --- | --- | --- |
| `GET /api/v1/hospitals` | 查询医院 | 登录 |
| `GET /api/v1/hospitals/{hospitalId}/departments` | 查询科室 | 登录 |
| `GET /api/v1/departments/{departmentId}/doctors` | 查询医生 | 登录 |
| `GET /api/v1/doctors/{doctorId}/schedules` | 查询今天、明天、后天的 12 个场次 | 登录 |

场次查询按 `Asia/Shanghai` 返回 D 至 D+2，每天依开始时间排序；每次查询统一采样秒精度 `serverTime`，十二项完全相同。未生成场次使用当前统一预约设置计算展示值，但 GET 不写数据库；已生成场次返回自身容量和费用快照。

以上 GET 均返回 **200** 和数组。医院项为 `id/name/address`，科室项为 `id/name`，医生项为 `id/doctorNo/name/title/introduction`。父级 ID 不存在或没有下级时，科室、医生数组为空；医生不存在时场次数组为空。场次项字段如下：

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
  "bookable": true,
  "bookingClosesAt": "2026-11-11T07:45:00",
  "availability": "AVAILABLE",
  "serverTime": "2026-11-11T07:30:00"
}
```

四种 `sessionType`：

| 类型 | 固定时间 |
| --- | --- |
| `MORNING_FIRST` | 08:00–10:00 |
| `MORNING_SECOND` | 10:30–12:00 |
| `AFTERNOON_FIRST` | 14:00–16:00 |
| `AFTERNOON_SECOND` | 16:30–18:00 |

`bookingClosesAt` 为场次开始前 15 分钟。`availability` 按时间、库存顺序计算：已到场次开始为 `STARTED`，未开始但已到下单截止为 `CUTOFF_REACHED`，未截止且无余量为 `FULL`，其余为 `AVAILABLE`。`bookable` 仅在 `AVAILABLE` 时为 true。当天已截止场次继续返回，不因为不可预约从数组移除。响应不提供供用户下单的内部 `scheduleId` 或 `releaseAt`；查询结果是时间快照，创建仍须复验资格。

## 管理员医院、科室与医生目录

以下九个接口都要求登录且具有 `ADMIN` 角色；普通用户返回 `403 / 2002`。

| 方法与路径 | 请求字段 | 成功响应 |
| --- | --- | --- |
| `POST /api/v1/admin/hospitals` | 必填 `name/address` | 201，医院对象 |
| `PATCH /api/v1/admin/hospitals/{hospitalId}` | `name/address` 至少一个非 null | 200，医院对象 |
| `DELETE /api/v1/admin/hospitals/{hospitalId}` | 无请求体 | 200，`data=null` |
| `POST /api/v1/admin/hospitals/{hospitalId}/departments` | 必填 `name` | 201，管理用科室对象 |
| `PATCH /api/v1/admin/departments/{departmentId}` | 必填 `name` | 200，管理用科室对象 |
| `DELETE /api/v1/admin/departments/{departmentId}` | 无请求体 | 200，`data=null` |
| `POST /api/v1/admin/departments/{departmentId}/doctors` | 必填 `name/title`，可选 `introduction` | 201，管理用医生对象 |
| `PATCH /api/v1/admin/doctors/{doctorId}` | `name/title/introduction` 至少一个非 null | 200，管理用医生对象 |
| `DELETE /api/v1/admin/doctors/{doctorId}` | 无请求体 | 200，`data=null` |

医院名称、地址上限为 100、200 字符；科室名称上限为 100；医生姓名、职称、介绍上限为 64、64、500。提供的文本会去除首尾空白，非 null 的纯空白值被拒绝。创建医生可省略或传 null 的介绍；医生 PATCH 的 null 表示保留，不提供清空介绍或调整所属科室、医生编号的能力。

响应对象：

| 对象 | 字段 |
| --- | --- |
| 医院 | `id/name/address` |
| 管理用科室 | `id/hospitalId/name` |
| 管理用医生 | `id/doctorNo/departmentId/name/title/introduction` |

医生编号由系统生成，修改资料不改变编号。父级或目标资源不存在返回 `404 / 4001`；同医院科室名称重复返回 `409 / 4401`。医院仍有科室、科室仍有医生、医生仍有关联场次（包括历史场次）或数据库引用阻止删除时，返回 `409 / 4402`，不级联删除业务数据。HTTP 示例见 [admin-catalog.http](../http/admin-catalog.http)。

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

两个接口均要求 `ADMIN`，成功返回 **200**；响应为 `capacityPerSession`、`registrationFee`（字符串）、`currency`、`updatedAt`。PUT 的两个请求字段都必填，费用最多八位整数、两位小数，非法输入返回 `400 / 1001`。既有场次后续创建的订单仍使用该场次费用，不会重新套用更新后的设置。

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

请求不接受 `scheduleId` 或客户端指定的价格、创建时间、截止。按服务端 `Asia/Shanghai` 日期，只允许为今天至后天（D 至 D+2）创建新订单，且必须严格早于场次开始前 15 分钟；恰好截止已不可创建。系统在场次、就诊人锁定后，实际占号前采样准入时间，Java 与条件 UPDATE 使用同一采样校验日期和时刻，并原子暂占库存、创建预约单和支付单。

幂等摘要为规范化后的 `doctorId + appointmentDate + sessionType + patientId`。同键同内容返回原订单；同键不同内容返回冲突。已取消或过期后重新预约必须使用新键。

首次下单会按需生成场次并复制统一设置。并发首次下单依靠 `(doctor_id, appointment_date, session_type)` 唯一约束收敛到同一场次，条件更新防止超卖。失败事务不保留新场次、暂占或幂等键。

首次创建返回 **201**，data 字段为 `appointmentOrderId`、`paymentOrderId`、`appointmentStatus`、`paymentStatus`、`amount`、`currency`、`paymentExpiresAt`、`serverTime`。幂等重放返回 **200**，data 仅含 `appointmentOrderId`、`appointmentNo`、`replayed=true`；需查询详情获取最新支付状态，不能将重放回执当首次创建响应。详情仍可返回内部 `scheduleId` 以关联历史记录。

示例日期只用于说明；实际请求必须改为服务端今天、明天或后天，今天还须选择尚未到下单截止的场次。创建响应 `serverTime` 在结果组装时采样；预约、支付创建时间均为锁后准入时刻。

## 支付时间边界

`paymentExpiresAt = min(orderCreatedAt + 15 分钟, sessionStartsAt)`，预约与支付单保存相同的创建时间和支付截止，后续不重算。订单在 23:59:59 创建次日场次时可跨零点支付，截止为次日 00:14:59，须严格早于截止。停止创建新订单不禁止原单支付：10:14 创建的 10:30 场订单可在 10:29 前支付，即使该场 10:15 已停止接新单。支付入口只检查本单持久化截止和状态，不再检查新下单窗口或截止。已经发生的渠道成功回调仍记录收款事实并按既有规则处理。

截止前本系统已经确认收款的支付，即使消息稍后消费，预约仍待支付时可继续确认。若预约仍待支付，截止时刻或之后本系统才确认的收款会使预约 EXPIRED、释放暂占、删除有效占用并进入原路全额退款；已关闭预约保持终态，只确保退款。paidAt 使用本系统处理可信通知时确认收款的服务端时间，不使用渠道上报的 occurredAt 判断截止资格。过期扫描延迟只影响状态落库时间，不延长支付资格。

## 预约查询和生命周期

| 方法与路径 | 用途 |
| --- | --- |
| `GET /api/v1/appointments/my?order=DESC` | 查询当前账号全部预约，支持 DESC/ASC 就诊时间排序 |
| `GET /api/v1/appointments/{id}` | 查询预约详情 |
| `POST /api/v1/appointments/{id}/cancel` | 主动取消 |
| `POST /api/v1/payments/{id}/attempts` | 发起支付尝试 |

查询返回 **200**。取消在无需退款时返回 **200**；进入或仍处于异步退款流程时返回 **202**，message 为“退款申请已受理”，data 仍是预约详情。202 不代表渠道已经退款。重复已关闭订单的取消不重复释放，退款已经完成时返回200。

详情和列表元素字段为：`id`、`appointmentNo`、`scheduleId`、`appointmentDate`、`startTime`、`endTime`、`status`、`visitNumber`、`createdAt`、`paymentOrderId`、`paymentStatus`、`workflowStatus`、`amount`、`currency`、`paymentAttemptCount`、`latestPaymentAttempt`、`refund`、`paymentExpiresAt`、`closedAt`、`serverTime`、`paymentExpired`、`canPay`、`canCancel`、`patient`、`doctor`、`sessionType`、`visit`。尝试/退款摘要可为 null；退款状态用于摘要，不决定 workflow。列表按就诊日期、场次开始时间、预约 ID 同向排序，默认 DESC，可指定 ASC；列表各项共享截断到秒的 serverTime。

嵌套对象字段：

| 对象 | 字段 |
| --- | --- |
| `patient`（创建快照） | `id/name/documentType/documentNoMasked/contactPhone/contactEmail` |
| `doctor`（创建快照） | `id/doctorNo/name/title/department/hospital`；`department` 为 `id/name`，`hospital` 为 `id/name/address` |
| `latestPaymentAttempt` | `id/attemptNo/channel/status/providerOrderNo/failureCode/createdAt/completedAt` |
| `refund` | `id/refundNo/amount/currency/channel/status/reason/attemptCount/lastFailureCode/completedAt` |
| `visit` | `status/checkedInAt/startedAt/completedAt/canCheckIn`；仅已确认预约非 null，未签到时计算 NOT_CHECKED_IN |

`visitNumber` 在确认前可为 null，按同场次确认顺序分配、取消后不复用；尝试处理中的 `completedAt`、尚未成功退款的 `completedAt` 和无失败时的失败代码可为 null。详情顶层及退款摘要的 `amount` 为 JSON 数值，区别于首次创建响应的字符串金额；单位均为元。`canPay/canCancel` 是展示标记，不代替操作入口的权限、时间和状态复验。

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

仅 dev/test 注册以下 MockPay 接口，均需要登录，无请求体：

| 方法与路径 | 用途 | 成功响应 |
| --- | --- | --- |
| `POST /api/v1/mock-pay/attempts/{attemptNo}/complete` | 模拟渠道成功 | 202，签名通知对象 |
| `POST /api/v1/mock-pay/attempts/{attemptNo}/fail` | 模拟渠道失败 | 202，签名通知对象 |

响应对象字段与下文渠道通知 body 一致，通知通过异步分发器进入正式验签链路；202 不代表通知处理或预约确认已经完成。使用尝试响应中的 checkoutUrl 加 `/complete` 或 `/fail`；不直接构造同步成功请求。旧 `/api/v1/payments/{id}/simulate-success` 已删除，dev/test/prod 均无该请求映射。

渠道通知 `POST /api/v1/payment-notifications/{channel}` 不要求 Bearer Token，依靠渠道验签；body 为 `notificationId`、`providerOrderNo`、`providerTransactionNo`、`status`、`amount`、`currency`、`occurredAt`、`signature`。现有 MockPay 签名由 Adapter 生成，真实支付渠道尚未接入。响应 **200** 的 data 为 `notificationId`、`processingResult`、`paymentOrderId`、`paymentStatus`、`duplicate`、`eventId`；重复通知部分字段可为 null。未知订单、金额币种或状态拒绝会记录审计并通过 processingResult 表达，不能仅凭HTTP200认为已收款。

客户端轮询预约详情：支付成功可先见 CONFIRMING_APPOINTMENT，再见 CONFIRMED；取消或迟到支付可先见 CANCELLED_REFUNDING，再见 CANCELLED_REFUNDED。轮询不延长支付截止，业务状态和库存以数据库事务为准。

新响应不包含 `scheduleClosed` 或 `hospitalClosureNotice`，新流程也不会产生停诊取消。历史 `CLOSED`、`CANCELLED_BY_HOSPITAL` 数据仍可查询。

## 主要错误

| 场景 | HTTP / code |
| --- | --- |
| 参数、容量、金额或时段非法 | `400 / 1001` |
| 未登录 | `401 / 2001` |
| 账号或密码错误 | `401 / 2003` |
| 注册邮箱重复 | `409 / 3001` |
| 当前账号记录不存在 | `404 / 3002` |
| 普通用户访问管理员接口、访问他人就诊人或预约/支付 | `403 / 2002` |
| 资源不存在或旧管理员场次路由 | `404 / 4001` |
| 本账号重复登记就诊人证件 | `409 / 4101` |
| 使用停用就诊人创建预约 | `409 / 4102` |
| 同医院科室名称重复 | `409 / 4401` |
| 医疗目录资源仍被引用，无法删除 | `409 / 4402` |
| 号源约满或场次不可用 | `409 / 4002` |
| 同场有效预约重复 | `409 / 4204` |
| 日期不在今天至后天，或已到场次下单截止 | `409 / 4202` |
| 幂等键同键不同内容 | `409 / 4103` |
| 支付已到截止时刻 | `409 / 4004` |
| 支付通知签名无效 | `400 / 4302` |
| 当前订单状态不允许操作 | `409 / 4003` |
| 已确认取消已到24小时边界 | `409 / 4205` |
| 支付渠道不可用 | `503 / 4301` |
| 需要沿原键重试的并发冲突 | `503 / 5001` |
| 医生档案或账号已绑定，不支持换绑 | `409 / 4501` |
| 签到日期、就诊状态或重复动作不满足资格 | `409 / 4502` |
| 查询状态组合或关联完整性异常 | `500 / 1002` |

## 历史兼容

V9 保留旧 `MORNING`、`AFTERNOON`、空时段及历史关闭状态供读取。旧半日场次不会拆分，也不接受新预约；旧活跃订单继续按自身费用和时间快照完成支付、过期或取消。V10 只扩展医生账号关联、角色、就诊记录和查询索引，不重写历史预约或回填就诊事实。

## 到院签到与门诊接诊

`GET /api/v1/appointments/my` 支持可选 `order=DESC|ASC`，按就诊日期、开始时间、ID 同向排序，默认 DESC，仍返回全部预约数组。原详情、列表及取消响应追加 `sessionType` 和 `visit`；创建响应不变。已确认且无记录时计算 NOT_CHECKED_IN，其他预约的 visit 为 null。

新增管理员医生绑定、用户签到及五个医生接口的路径、字段和权限见 [就诊 API](visit/api.md#2-接口总览)。医生详情仅包含预约快照、脱敏证件及就诊进度。正常动作均返回 200 和最新详情，重复动作返回 409，不覆盖原时间。
