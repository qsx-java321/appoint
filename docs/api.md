# REST API

> 认证、用户、头像、就诊人和医疗目录接口已提供 [OpenAPI 契约](openapi/README.md)。对应章节的字段表与示例用于过渡，后续以 OpenAPI 为准；预约、就诊、通知等模块仍在本文维护。

本文定义当前接口。业务规则见[业务流程](business-flow.md)。

## 通用约定

业务 API 使用统一响应结构。独立的状态探测格式见[本地环境识别](#本地环境识别)。

```json
{ "code": 0, "message": "success", "data": {} }
```

注册、登录、用户头像读取和本地环境探测可免 Bearer Token。业务接口使用 Bearer Token，管理员接口同时要求 `ADMIN` 角色。

ID 在 JSON 中为字符串，容量、序号和 `expiresIn` 为数字。创建预约的 `amount` 和预约设置、场次的 `registrationFee` 为字符串，预约详情的 `amount` 为 JSON 数值。

日期使用 `yyyy-MM-dd`，时间使用 `HH:mm:ss`。业务时间按 `Asia/Shanghai` 解释，`LocalDateTime` 响应不带时区偏移。各节列出的业务响应字段位于统一包装的 `data` 中。错误时 `data=null`，详见[主要错误](#主要错误)。

## 账号注册、登录与当前用户

> OpenAPI：`Auth`、`User` 两个 Tag。字段、Schema 和请求/响应示例以 [OpenAPI](openapi/README.md) 为准。

| 方法与路径 | 参数 | 成功结果 |
| --- | --- | --- |
| `POST /api/v1/auth/register/code` | `email` | 200，`{"expiresIn":300}`，验证码发往 Mailpit |
| `POST /api/v1/auth/register/verify` | `email`、`code` | 200，`{"signupTicket":"<UUID>","expiresIn":300}` |
| `POST /api/v1/auth/register` | `email`、`password`、`signupTicket` | 201，用户对象 |
| `POST /api/v1/auth/login` | `email`、`password` | 200，Token 与用户对象 |
| `GET /api/v1/users/me` | 无请求体，需要登录 | 200，当前用户对象 |

验证码是六位数字字符串，保留前导零。验证码与注册票据默认各有效五分钟，重发覆盖旧码，成功验码后签发票据，提交注册时消费票据。

邮箱按去除首尾空白、转小写处理。注册密码为 8–64 字符。用户对象为 `id`、`email`、`roles`、`avatarUrl`，注册授予 `USER`，未设置头像时 `avatarUrl=null`。

登录返回 `accessToken`、`tokenType`（`Bearer`）、`expiresIn`（7200 秒）和 `user`。后续请求携带 `Authorization: Bearer <accessToken>`。角色变更后重新登录以取得新的权限。

## 用户头像

> OpenAPI：`User` Tag。字段、Schema、状态码和示例以 [OpenAPI](openapi/README.md) 为准。

头像只操作当前登录账号，不需要客户端指定用户 ID。读取接口无需登录，替换和删除需要 Bearer Token。

| 方法与路径 | 请求 | 成功结果 |
| --- | --- | --- |
| `PUT /api/v1/users/me/avatar` | `multipart/form-data`，文件字段 `file` | 200，更新后的用户对象 |
| `DELETE /api/v1/users/me/avatar` | 无请求体 | 200，`avatarUrl=null` 的用户对象；重复删除仍为 200 |
| `GET /api/v1/users/{id}/avatar?v={version}` | 无请求体，可免登录 | 200，图片字节；没有头像时 `404 / 4001` |

`PUT` 只接受 2 MB 以内的 JPEG、PNG 或 WebP。服务端按文件头判定格式，并校验 JPEG/PNG 的宽高不超过 4096 像素；SVG、GIF 和伪装文件返回 `400 / 3101`。缺少 `file` 字段返回 `400 / 1001`。multipart 请求体上限为 4 MB，超过时返回 `400 / 3101`。存储暂时不可用返回 `503 / 3102`。

设置头像后，用户对象的 `avatarUrl` 形如 `/api/v1/users/{id}/avatar?v={对象文件名}`，替换头像会改变 `v`。读取响应带长期 `Cache-Control` 和 `ETag`；请求携带匹配的 `If-None-Match` 时返回 `304`。头像读取只按用户 ID 返回当前图片，不判断角色；知道用户 ID 即可查看，不建议上传敏感图片。

## 就诊人管理

> OpenAPI：`Patient` Tag。字段、Schema、状态码和示例以 [OpenAPI](openapi/README.md) 为准。

所有接口只操作当前账号的就诊人，不接受客户端指定归属账号。就诊人 ID 与账号用户 ID 不同。

| 方法与路径 | 请求 | 成功响应 |
| --- | --- | --- |
| `POST /api/v1/patients` | 完整就诊人资料 | 201，就诊人对象，初始 `enabled=true` |
| `GET /api/v1/patients` | 无请求体 | 200，本人就诊人数组；无数据为 `[]` |
| `GET /api/v1/patients/{id}` | 无请求体 | 200，就诊人对象 |
| `PATCH /api/v1/patients/{id}` | 部分就诊人资料 | 200，更新后的就诊人对象 |
| `PATCH /api/v1/patients/{id}/status` | `{"enabled":false}` 或 `{"enabled":true}` | 200，就诊人对象 |

创建请求示例

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

资料 PATCH 中缺省或 `null` 字段保留原值，已填写字段须通过校验。状态 PATCH 需要布尔值 `enabled`。停用影响后续下单，已创建预约保留原有快照。

就诊人对象为 `id`、`name`、`documentType`、`documentNoMasked`、`contactPhone`、`contactEmail`、`enabled`、`createdAt`、`updatedAt`。

## 目录与场次

> OpenAPI：`Catalog` Tag。字段、Schema 和示例以 [OpenAPI](openapi/README.md) 为准。

| 方法与路径 | 用途 | 权限 |
| --- | --- | --- |
| `GET /api/v1/hospitals` | 查询医院 | 登录 |
| `GET /api/v1/hospitals/{hospitalId}/departments` | 查询科室 | 登录 |
| `GET /api/v1/departments/{departmentId}/doctors` | 查询医生 | 登录 |
| `GET /api/v1/doctors/{doctorId}/schedules` | 查询今天、明天、后天的 12 个场次 | 登录 |

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

场次类型及其时间见[场次设置与库存](business-flow.md#场次设置与库存)。

`availability` 按 `STARTED`、`CUTOFF_REACHED`、`FULL`、`AVAILABLE` 的顺序判定。`bookable` 仅在 `AVAILABLE` 时为真。同一响应十二个场次共享 `serverTime`。

## 创建预约

已登录用户调用 `POST /api/v1/appointments/token` 领取令牌，无请求体。成功返回 `200`，`data` 为 `{"token":"<UUID>","expiresIn":600}`。调用 `POST /api/v1/appointments` 时，将领取到的令牌放入 `Idempotency-Key` 请求头。

请求示例

```json
{
  "doctorId": "1",
  "appointmentDate": "2026-11-11",
  "sessionType": "MORNING_FIRST",
  "patientId": "201"
}
```

将示例日期替换为可预约日期。日期超出窗口或达到下单截止返回 `409 / 4202`。服务端确定金额、创建时间和支付截止。

令牌作用于当前账号，比较内容为规范化后的 `doctorId + appointmentDate + sessionType + patientId`。同令牌同内容且原请求已完成时返回原回执。令牌已绑定的内容与本次提交不同时返回 `409 / 4103`，无效或过期且未创建预约的令牌返回 `409 / 4104`。已取消或过期后重新预约须领取新令牌。

| HTTP 状态 | `data` |
| --- | --- |
| `201` 首次创建 | `appointmentOrderId`、`appointmentNo`、`appointmentStatus`、`paymentOrderId`、`paymentNo`、`paymentStatus`、`amount`、`currency`、`createdAt`、`paymentExpiresAt`、`serverTime` |
| `200` 重放 | 与首次创建相同的回执，当前状态通过详情查询 |
| `202` 原请求正在提交 | `{"pending":true}`，再次提交时沿用原令牌和请求体 |

网络超时或 `503 / 5001` 时也沿用原令牌和请求体。[下单幂等设计](booking-idempotency.md)

## 预约查询和生命周期

以下接口均要求登录，仅允许本人操作，无请求体。

| 方法与路径 | 用途 | 成功结果 |
| --- | --- | --- |
| `GET /api/v1/appointments/{id}` | 查询预约详情 | 200，详情对象 |
| `GET /api/v1/appointments/my` | 查询本人预约 | 200，详情数组，依次按就诊日期、场次开始时间和预约 ID 排序。`order` 可为 `ASC` 或 `DESC`，默认降序 |
| `POST /api/v1/appointments/{id}/pay` | 同步模拟付款 | 200，已确认的预约详情，包含支付时间和就诊序号 |
| `POST /api/v1/appointments/{id}/cancel` | 关闭未支付预约 | 200，关闭后的详情。重复取消返回当前详情 |

支付和取消均使用预约单 ID。待支付预约超时后付款，或尝试付款给已过期预约，返回 `409 / 4004`。取消已确认预约返回 `409 / 4003`。如果待支付预约已超时但尚未被后台关单，取消接口会按过期关闭并释放名额。重复支付已确认的订单返回原支付时间和就诊序号。[时间规则](business-flow.md#关键时间)

### 详情字段

| 类别 | 字段 |
| --- | --- |
| 预约 | `id`、`appointmentNo`、`scheduleId`、`appointmentDate`、`startTime`、`endTime`、`sessionType`、`status`、`visitNumber` |
| 支付 | `paymentOrderId`、`paymentStatus`、`amount`、`currency`、`paidAt`。未付款时 `paidAt` 为 `null` |
| 时间 | `createdAt`、`paymentExpiresAt`、`closedAt`、`serverTime`。最晚支付时间可为 `null` |
| 操作与提示 | `workflowStatus`、`paymentExpired`、`canPay`、`canCancel` |
| 凭证与就诊 | `patient`（证件号脱敏）、`doctor`（包含科室与医院快照）、`visit`（已确认预约的就诊进度） |

`paymentExpired` 在预约已过期，或者仍待支付且超过最晚支付时间时为真。后一种情况在后台关单前即可显示 `workflowStatus=EXPIRED`，而持久化的 `status` 仍为 `PENDING_PAYMENT`。`canPay` 和 `canCancel` 只在预约仍待支付、支付单未付款且处于支付期限内时为真。`workflowStatus` 可以为 `null`，例如用户已取消的预约。

支付单关联缺失或不一致返回 `500 / 1002`。

## 到院签到与门诊接诊

所有成功响应为200。用户动作限定本人预约，医生动作限定本人已确认预约；写入口重新校验状态，成功后查询并返回最新详情。

| 方法与路径 | 权限及输入 | 返回data |
| --- | --- | --- |
| `POST /api/v1/appointments/{appointmentId}/check-in` | 本人；无body | 完整用户预约详情 |
| `GET /api/v1/doctor/sessions` | DOCTOR；可选date，默认业务今天 | 四场概览数组 |
| `GET /api/v1/doctor/appointments` | DOCTOR；必填date、sessionType | 场次名单数组 |
| `GET /api/v1/doctor/appointments/{appointmentId}` | DOCTOR | 医生只读详情 |
| `POST /api/v1/doctor/appointments/{appointmentId}/start-visit` | DOCTOR；无body | 更新后的医生详情 |
| `POST /api/v1/doctor/appointments/{appointmentId}/complete-visit` | DOCTOR；无body | 更新后的医生详情 |

日期、场次和 ID 格式沿用通用约定。医生账号权限由[首次绑定](#首次绑定)授予。

| 响应对象 | 字段 |
| --- | --- |
| 用户就诊进度 | `status`、`checkedInAt`、`startedAt`、`completedAt`、`canCheckIn`、`checkInOpensAt`、`checkInClosesAt` |
| 医生场次概览 | `appointmentDate`、`sessionType`、`startTime`、`endTime`、`confirmedCount`、`notCheckedInCount`、`waitingCount`、`inProgressCount`、`completedCount` |
| 医生人员名单项 | `appointmentId`、`visitNumber`、`patientName`、`visitStatus`、`checkedInAt`、`canStartVisit`、`canCompleteVisit` |
| 医生只读详情 | `appointmentId`、`appointmentNo`、`visitNumber`、`appointmentDate`、`sessionType`、`startTime`、`endTime`、`patient`、`doctor`、`visit` |
| 医生详情就诊人 | `id`、`name`、`documentType`、`documentNoMasked` |
| 医生详情就诊进度 | `status`、`checkedInAt`、`startedAt`、`completedAt` |

用户详情的`visit`仅已确认预约非null；无记录时推导NOT_CHECKED_IN，签到后WAITING，开始后IN_PROGRESS，完成后COMPLETED。未发生的时间为null。

`visit.canCheckIn` 表示查询时的签到资格。`checkInOpensAt`、`checkInClosesAt` 取自预约快照。[签到时间规则](business-flow.md#关键时间)

签到重复操作返回 `409 / 4502`，保留首次成功时间。未到窗口返回 `409 / 4503`，首次签到达到或超过截止返回 `409 / 4504`。

医生概览按开始时间返回四项，空场次补零。名单按 `visitNumber` 升序。`canStartVisit`、`canCompleteVisit` 表示各动作的当前资格。

普通用户访问医生接口返回 403，医生读取其他医生的预约详情返回 404，越权写入返回 403。

## 消息中心

以下接口要求登录，只读取当前账号自己的通知，不判断 `USER`、`DOCTOR` 或 `ADMIN` 角色。

| 方法与路径 | 用途 | 成功结果 |
| --- | --- | --- |
| `GET /api/v1/notifications` | 查询本人通知列表 | 200，数组；无数据为 `[]` |
| `GET /api/v1/notifications/{id}` | 查询本人通知详情 | 200，详情对象 |

列表按 `occurredAt` 降序、`id` 降序排列，首版不分页。列表项字段为 `id`、`type`、`title`、`patientName`、`occurredAt`。详情在上述字段外增加 `detail` 和 `appointmentId`。

通知不存在或不属于当前账号时返回 `404 / 4001`，不区分两种情况。`appointmentId` 可用于跳转现有预约详情接口，预约归属校验继续生效。

`type` 为 `APPOINTMENT_CONFIRMED`（预约成功）、`CHECKED_IN`（签到成功）、`VISIT_STARTED`（开始接诊）或 `VISIT_COMPLETED`（完成接诊）。`detail` 是事件生成时保存的完整中文文案，后续修改就诊人或医疗目录不会改变。通知异步投递，刚完成业务操作后稍等或刷新列表即可看到。

## 管理员医院、科室与医生目录

> OpenAPI：`AdminCatalog` Tag。字段、Schema 和示例以 [OpenAPI](openapi/README.md) 为准。

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

> OpenAPI：`BookingSettings` Tag。字段、Schema 和示例以 [OpenAPI](openapi/README.md) 为准。

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

两个接口均要求 `ADMIN`，返回 **200**。响应为 `capacityPerSession`、`registrationFee`（字符串）、`currency`、`updatedAt`。PUT 的两个字段必填，容量为正整数，费用最低为 0.01 元，最多八位整数和两位小数。非法输入返回 `400 / 1001`。[设置生效范围](business-flow.md#场次设置与库存)

## 管理员医生账号

> OpenAPI：`DoctorAccount` Tag。字段、Schema 和示例以 [OpenAPI](openapi/README.md) 为准。

### 管理员医生账号查询

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

预置医生的 `passwordHint` 指向对应环境的 `LOCAL_SEED_PASSWORD`。[预置账号](environments.md#预置目录和账号)

### 首次绑定

| 方法与路径 | 权限及请求 | 成功响应 |
| --- | --- | --- |
| `PUT /api/v1/admin/doctors/{doctorId}/account` | `ADMIN`，请求体为 `{"userId":"12"}` | 200，`data` 为 `doctorId` 和 `userId`，均为字符串 |

首次绑定同时授予账号 `DOCTOR` 角色，账号重新登录后即可使用医生接口。医生或账号已有绑定时返回 `409 / 4501`，包括对同一关系的重复绑定。当前接口只支持首次绑定。

## 本地环境识别

`GET /api/v1/local-environment` 在常驻环境提供无需登录的探测。后端接受流量时返回 HTTP 200，已能响应请求且尚未就绪时返回 HTTP 503。响应直接返回状态对象。由 Python 脚本启动时，`launchId` 为当次启动标识。单独启动后端时通常为 `null`。

```json
{ "profile": "demo", "ready": true, "launchId": null }
```

本地前端给 API 请求附带 `X-Appoint-Environment`。标识与正在运行的 profile 不匹配时返回 `409 / 1003`，提示刷新页面。直接调用 API 的客户端可省略此标识。

## test环境业务调时

调时接口在常驻 `test` 环境提供，需要 `ADMIN` 角色。成功响应包含 `businessTime` 和 `mode`（`realtime` 或 `adjusted`）。[调时操作和影响](environments.md#test-业务调时)

| 方法与路径 | 用途 |
| --- | --- |
| `GET /api/v1/test-control/clock` | 查询业务时间 |
| `PUT /api/v1/test-control/clock` | 设置业务时间，请求体中的 `instant` 为带时区偏移的 ISO 时间字符串 |
| `DELETE /api/v1/test-control/clock` | 恢复实时 |

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
| 头像格式、大小或尺寸不合法 | `400 / 3101` |
| 头像对象存储暂时不可用 | `503 / 3102` |
| 普通用户访问管理员接口、访问他人就诊人或预约/支付 | `403 / 2002` |
| 资源不存在 | `404 / 4001` |
| 本账号重复登记就诊人证件 | `409 / 4101` |
| 使用停用就诊人创建预约 | `409 / 4102` |
| 同医院科室名称重复 | `409 / 4401` |
| 医疗目录资源仍被引用，无法删除 | `409 / 4402` |
| 号源约满或场次不可用 | `409 / 4002` |
| 同场有效预约重复 | `409 / 4204` |
| 日期不在今天至后天，或已到场次下单截止 | `409 / 4202` |
| 幂等键同键不同内容 | `409 / 4103` |
| 预约令牌无效或已过期，且无已提交回执 | `409 / 4104` |
| 已超过最晚支付时刻 | `409 / 4004` |
| 当前订单状态不允许操作 | `409 / 4003` |
| 需要沿原键重试的并发冲突 | `503 / 5001` |
| 医生档案或账号已绑定，不支持换绑 | `409 / 4501` |
| 预约、就诊状态或重复动作不满足资格（优先于时间） | `409 / 4502` |
| 尚未到签到开放时间 | `409 / 4503` |
| 首次签到时已到达或超过场次签到截止时间 | `409 / 4504` |
| 查询状态组合或关联完整性异常 | `500 / 1002` |
