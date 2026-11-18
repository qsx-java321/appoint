# REST API

本文定义当前接口。业务规则见[业务流程](business-flow.md)。

业务 API 使用统一响应结构。独立的状态探测格式见[本地环境识别](#本地环境识别)。

```json
{ "code": 0, "message": "success", "data": {} }
```

注册、登录、支付渠道通知和本地环境探测可免 Bearer Token。业务接口使用 Bearer Token，管理员接口同时要求 `ADMIN` 角色。

响应中的 Java `Long` 类型 ID 统一序列化为 JSON 字符串，例如 `"id":"201"`，避免浏览器整数精度丢失。客户端应按不透明标识保存；容量、就诊序号及 `expiresIn` 等数值字段仍为数字。金额的字符串形式以对应 DTO 为准，例如创建预约的 `amount` 和设置中的 `registrationFee` 为字符串，退款摘要中的 `amount` 为 JSON 数值。

日期使用 `yyyy-MM-dd`，时间使用 `HH:mm:ss`；业务时间按 `Asia/Shanghai` 解释，`LocalDateTime` 响应不带时区偏移。以下响应字段均指统一包装内的 `data`；错误时 `data=null`，`code` 和 HTTP 状态见“主要错误”。

## 账号注册、登录与当前用户

匿名注册分三阶段；先验证邮箱，最后创建账号。登录沿用邮箱密码。

| 方法与路径 | 参数 | 成功结果 |
| --- | --- | --- |
| `POST /api/v1/auth/register/code` | `email` | 200，`{"expiresIn":300}`，验证码发往 Mailpit |
| `POST /api/v1/auth/register/verify` | `email`、`code` | 200，`{"signupTicket":"<UUID>","expiresIn":300}` |
| `POST /api/v1/auth/register` | `email`、`password`、`signupTicket` | 201，用户对象 |
| `POST /api/v1/auth/login` | `email`、`password` | 200，Token 与用户对象 |
| `GET /api/v1/users/me` | 无请求体，需要登录 | 200，当前用户对象 |

上述 data 均包含在统一 Result 响应中。验证码是保留前导零的六位数字字符串，有效期五分钟；重发覆盖旧码。验证成功删除验证码并签发五分钟票据；验证步骤不创建用户。注册必须消费与邮箱匹配的票据，之后检查邮箱重复并创建账号。注册不会自动登录。

```json
{"email":"reader@example.com","password":"example-password","signupTicket":"<verify返回的票据>"}
```

邮箱要求非空且符合格式，业务处理前统一去除首尾空白并转小写。注册密码为8–64字符，登录密码要求非空。用户对象为 `id`、`email`、`roles`，不含密码；注册只授予 `USER`，管理员授权见[环境说明](environments.md#dev管理员授权)。

| 条件 | HTTP / code |
| --- | --- |
| 验证码错误 | 400 / 3003 |
| 验证码不存在或过期 | 400 / 3004 |
| 票据无效、过期、已消费或邮箱不匹配 | 400 / 3005 |
| 重复邮箱 | 409 / 3001 |
| 邮箱或密码错误 | 401 / 2003 |

登录响应为 `accessToken`、`tokenType`（Bearer）、`expiresIn`（7200秒）和 `user`。后续携带 `Authorization: Bearer <accessToken>`；无效/过期Token访问受保护接口返回401/2001。`users/me` 查询数据库角色，但权限来自JWT角色，角色变更应重新登录。当前用户不存在返回404/3002。

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

医生编号由系统生成，修改资料不改变编号。父级或目标资源不存在返回 `404 / 4001`；同医院科室名称重复返回 `409 / 4401`。医院仍有科室、科室仍有医生、医生仍有关联场次（包括历史场次）或数据库引用阻止删除时，返回 `409 / 4402`，不级联删除业务数据。

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

支付截止保存为`paymentExpiresAt`，等于截止即拒绝新支付；它与目录的`bookingClosesAt`分别表示支付和下单资格。收款时间判断、迟到退款及跨零点示例见[业务流程](business-flow.md#支付截止与异步确认)。

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
| `visit` | `status/checkedInAt/startedAt/completedAt/canCheckIn/checkInOpensAt/checkInClosesAt`；仅已确认预约非 null，未签到时计算 NOT_CHECKED_IN |

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

dev/test/demo 注册以下 MockPay 接口，均需要登录，无请求体：

| 方法与路径 | 用途 | 成功响应 |
| --- | --- | --- |
| `POST /api/v1/mock-pay/attempts/{attemptNo}/complete` | 模拟渠道成功 | 202，签名通知对象 |
| `POST /api/v1/mock-pay/attempts/{attemptNo}/fail` | 模拟渠道失败 | 202，签名通知对象 |

响应对象字段与下文渠道通知 body 一致，通知通过异步分发器进入正式验签链路；202 不代表通知处理或预约确认已经完成。使用尝试响应中的 checkoutUrl 加 `/complete` 或 `/fail`；不直接构造同步成功请求。

渠道通知 `POST /api/v1/payment-notifications/{channel}` 不要求 Bearer Token，依靠渠道验签；body 为 `notificationId`、`providerOrderNo`、`providerTransactionNo`、`status`、`amount`、`currency`、`occurredAt`、`signature`。现有 MockPay 签名由 Adapter 生成，真实支付渠道尚未接入。响应 **200** 的 data 为 `notificationId`、`processingResult`、`paymentOrderId`、`paymentStatus`、`duplicate`、`eventId`；重复通知部分字段可为 null。未知订单、金额币种或状态拒绝会记录审计并通过 processingResult 表达，不能仅凭HTTP200认为已收款。

客户端轮询预约详情：支付成功可先见 CONFIRMING_APPOINTMENT，再见 CONFIRMED；取消或迟到支付可先见 CANCELLED_REFUNDING，再见 CANCELLED_REFUNDED。轮询不延长支付截止，业务状态和库存以数据库事务为准。

新响应不包含 `scheduleClosed` 或 `hospitalClosureNotice`，新流程也不会产生停诊取消。历史 `CLOSED`、`CANCELLED_BY_HOSPITAL` 数据仍可查询。

## 主要错误

| 场景 | HTTP / code |
| --- | --- |
| 参数、容量、金额或时段非法 | `400 / 1001` |
| 本地请求的环境标识与当前 profile 冲突 | `409 / 1003` |
| 未登录 | `401 / 2001` |
| 账号或密码错误 | `401 / 2003` |
| 注册邮箱重复 | `409 / 3001` |
| 当前账号记录不存在 | `404 / 3002` |
| 注册验证码错误 | `400 / 3003` |
| 注册验证码不存在或过期 | `400 / 3004` |
| 注册票据无效、过期、已消费或邮箱不匹配 | `400 / 3005` |
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
| 预约、就诊状态或重复动作不满足资格（优先于时间） | `409 / 4502` |
| 尚未到签到开放时间 | `409 / 4503` |
| 已超过预约场次签到截止时间 | `409 / 4504` |
| 查询状态组合或关联完整性异常 | `500 / 1002` |

## 管理员医生账号查询

`GET /api/v1/admin/doctor-accounts`仅允许`ADMIN`访问，无请求参数，成功返回200。查询当前后端所连接环境的全部医生，按医院ID、科室ID、医生编号、医生ID排序；读取实际目录和账号绑定，不使用目录缓存，未绑定账号的医生也返回。无医生时`items=[]`。

data为`environment`（当前环境名称）和`items`数组。每项字段如下，ID沿用字符串约定：

| 字段 | 含义 |
| --- | --- |
| hospitalId、hospitalName | 所属医院ID及当前名称 |
| departmentId、departmentName | 所属科室ID及当前名称 |
| doctorId、doctorNo、doctorName | 医生ID、编号及当前姓名 |
| userId、email | 绑定的账号ID和邮箱，未绑定时均为null |
| bindingStatus | BOUND（已绑定）或UNBOUND（未绑定） |
| passwordHint | 密码说明，不含密码或密码哈希 |

例如test环境的一个列表项：

```json
{
  "hospitalId": "1",
  "hospitalName": "示例市第一人民医院",
  "departmentId": "1",
  "departmentName": "内科",
  "doctorId": "1",
  "doctorNo": "DOC000001",
  "doctorName": "张医生",
  "userId": "2",
  "email": "test-doctor-001@example.local",
  "bindingStatus": "BOUND",
  "passwordHint": "初始密码见 .env.test 的 LOCAL_SEED_PASSWORD"
}
```

预置医生账号通过`local_environment_seed_accounts`归属记录识别，不根据邮箱格式推断。dev/test/demo的提示分别指向`.env.dev`、`.env.test`、`.env.demo`；自行注册后绑定的账号提示“使用注册时设置的密码”，未绑定时提示“未绑定账号”。配置中的初始密码只用于首次创建，修改配置不会更新已有账号密码。该接口不读取密码配置，也不提供修改密码、解绑或换绑操作。未登录返回`401 / 2001`，已登录但没有ADMIN返回`403 / 2002`。

## 到院签到与门诊接诊

所有成功响应为200。用户动作限定本人预约，医生动作限定本人已确认预约；写入口重新校验状态，成功后查询并返回最新详情。

| 方法与路径 | 权限及输入 | 返回data |
| --- | --- | --- |
| `PUT /api/v1/admin/doctors/{doctorId}/account` | ADMIN；`{"userId":"12"}` | doctorId、userId |
| `POST /api/v1/appointments/{appointmentId}/check-in` | 本人；无body | 完整用户预约详情 |
| `GET /api/v1/doctor/sessions` | DOCTOR；可选date，默认业务今天 | 四场概览数组 |
| `GET /api/v1/doctor/appointments` | DOCTOR；必填date、sessionType | 场次名单数组 |
| `GET /api/v1/doctor/appointments/{appointmentId}` | DOCTOR | 医生只读详情 |
| `POST /api/v1/doctor/appointments/{appointmentId}/start-visit` | DOCTOR；无body | 更新后的医生详情 |
| `POST /api/v1/doctor/appointments/{appointmentId}/complete-visit` | DOCTOR；无body | 更新后的医生详情 |

日期、场次和ID格式沿用通用约定。医生或账号已绑定返回4501，不提供重复绑定成功、解绑或换绑；绑定并授权后须重新登录。

| 响应对象 | 字段 |
| --- | --- |
| 用户就诊进度 | `status`、`checkedInAt`、`startedAt`、`completedAt`、`canCheckIn`、`checkInOpensAt`、`checkInClosesAt` |
| 医生场次概览 | `appointmentDate`、`sessionType`、`startTime`、`endTime`、`confirmedCount`、`notCheckedInCount`、`waitingCount`、`inProgressCount`、`completedCount` |
| 医生人员名单项 | `appointmentId`、`visitNumber`、`patientName`、`visitStatus`、`checkedInAt`、`canStartVisit`、`canCompleteVisit` |
| 医生只读详情 | `appointmentId`、`appointmentNo`、`visitNumber`、`appointmentDate`、`sessionType`、`startTime`、`endTime`、`patient`、`doctor`、`visit` |
| 医生详情就诊人 | `id`、`name`、`documentType`、`documentNoMasked` |
| 医生详情就诊进度 | `status`、`checkedInAt`、`startedAt`、`completedAt` |

用户详情的`visit`仅已确认预约非null；无记录时推导NOT_CHECKED_IN，签到后WAITING，开始后IN_PROGRESS，完成后COMPLETED。未发生的时间为null。

`visit.canCheckIn`为当前查询时的展示资格；`visit.checkInOpensAt`和`visit.checkInClosesAt`由预约快照计算，已签到或完成仍返回窗口，首次签到要求开放时间<=当前时间<截止。具体时间规则见[签到与接诊](business-flow.md#到院签到与接诊)。

签到先检查不存在/归属，再检查预约与进度，最后检查开放和截止。重复动作4502优先于时间错误4503/4504，保留首次成功时间。开始仅允许WAITING，完成仅允许IN_PROGRESS，医生动作无额外日期限制。

医生四场概览始终按开始时间返回四项，无预约时补零；confirmedCount为四种就诊进度人数之和。名单按visitNumber升序，查看始终可用，另外两个操作由对应can字段提示。医生详情只含脱敏凭证和就诊进度；doctor结构沿用预约快照，不提供支付渠道信息。

普通用户调用医生接口返回403；其他医生的详情查询返回404，写操作归属不符返回403。非法日期、场次、排序或缺必填参数返回400/1001。页面在医生操作成功后重查名单与概览，用户刷新同一预约查看结果。

## 历史兼容

旧半日场次、历史关闭终态及空场次类型继续可读，不用于新预约。历史订单按自身快照和持久化截止处理，不补写就诊事实。

## 本地环境识别

`GET /api/v1/local-environment` 在常驻环境提供无需登录的探测。后端接受流量时返回 HTTP 200，已能响应请求且尚未就绪时返回 HTTP 503。响应直接返回状态对象。由 Python 脚本启动时，`launchId` 为当次启动标识。单独启动后端时通常为 `null`。

```json
{ "profile": "demo", "ready": true, "launchId": null }
```

本地前端给 API 请求附带 `X-Appoint-Environment`。标识与正在运行的 profile 不匹配时返回 `409 / 1003`，提示刷新页面。直接调用 API 的客户端可省略此标识。

## test环境业务调时

常驻 `test` 环境注册调时接口，调用需要 `ADMIN` 角色。对 `/api/v1/test-control/clock` 发起 `GET` 查询，使用 `PUT` 设置业务时间，使用 `DELETE` 恢复实时。设置时传入带时区的 ISO 时间字符串 `instant`。响应包含 `businessTime` 和 `mode`（`realtime` 或 `adjusted`）。设置后时间继续走秒，重启后恢复真实时间。调时只作用于业务时钟，已发生的业务事实继续保留。
