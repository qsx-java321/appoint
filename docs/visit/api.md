# 到院签到与医生看板 API

日期：2026-11-11。本文接口及响应扩展已实现，实际验收结果见 [验证报告](../verification-report.md)。

关联：[需求](../../.scratch/visit-workflow/spec.md)、[代码设计](design.md)、[改动影响](change-scope.md)。现有字段与规则继续参考 [现行 API](../api.md)。

Controller、Service、Mapper 的对应关系，以及响应 DTO 的 Java 定义，见设计文档第 8–14 节。本文负责 HTTP 契约，不另外定义一套业务规则。

## 1. 通用约定

- 所有接口需要 `Authorization: Bearer <accessToken>`。医生接口需要 `DOCTOR`，管理员绑定接口需要 `ADMIN`。
- 登录账号由服务端提取。用户不传自己的 `userId`，医生不传自己的 `doctorId`；管理员关联账号的请求体是例外，需要明确被关联的账号 ID。
- 响应使用 `Result<T>`：`{"code":0,"message":"success","data":...}`。本文所有正常成功响应为 HTTP 200。
- Java Long ID 输出为 JSON 字符串，前端按不透明标识处理；`visitNumber` 和人数是 JSON 数字。
- 日期为 `yyyy-MM-dd`，时间为 `HH:mm:ss`，日期时间为 `yyyy-MM-dd'T'HH:mm:ss`，按 `Asia/Shanghai` 解释。
- `null` 时间表示该动作尚未发生，页面显示“—”。`visitNumber=3` 可格式化显示为 003，不改变号码值。
- 示例使用虚构资料，独立展示不同操作时刻；涉及当天签到的示例假定服务端日期为 2026-11-13。
- 本文限定正常流程；身份、归属与状态资格仍在后端判断，不扩展异常状态与错误码矩阵。

## 2. 接口总览

新增 7 个接口、扩展 2 个既有查询接口：

| 类型 | 方法与路径 | 权限 | 响应 data |
| --- | --- | --- | --- |
| 扩展 | `GET /api/v1/appointments/my?order=DESC` | 登录用户，本人 | `AppointmentResponse[]`，增加就诊信息 |
| 扩展 | `GET /api/v1/appointments/{appointmentId}` | 登录用户，本人 | `AppointmentResponse`，增加就诊信息 |
| 新增 | `POST /api/v1/appointments/{appointmentId}/check-in` | 登录用户，本人 | 更新后的 `AppointmentResponse` |
| 新增 | `PUT /api/v1/admin/doctors/{doctorId}/account` | ADMIN | 医生账号关联 |
| 新增 | `GET /api/v1/doctor/sessions?date=2026-11-13` | DOCTOR，本人 | 四场概览数组 |
| 新增 | `GET /api/v1/doctor/appointments?date=2026-11-13&sessionType=MORNING_FIRST` | DOCTOR，本人 | 场次人员数组 |
| 新增 | `GET /api/v1/doctor/appointments/{appointmentId}` | DOCTOR，本人 | 医生只读详情 |
| 新增 | `POST /api/v1/doctor/appointments/{appointmentId}/start-visit` | DOCTOR，本人 | 更新后的医生详情 |
| 新增 | `POST /api/v1/doctor/appointments/{appointmentId}/complete-visit` | DOCTOR，本人 | 更新后的医生详情 |

沿用 `POST /api/v1/auth/register`、`POST /api/v1/auth/login`、`GET /api/v1/users/me`，不增加医生专用登录。管理员先为已存在的账号绑定医生，医生再登录，用户对象的现有 `roles` 数组包含 `DOCTOR`。

## 3. 用户：我的预约列表与详情

### 3.1 列表

```http
GET /api/v1/appointments/my?order=DESC
```

| 参数 | 必填 | 默认值 | 含义 |
| --- | --- | --- | --- |
| `order` | 否 | `DESC` | `DESC` 或 `ASC`，按就诊日期、开始时间、预约 ID 同向排序 |

返回当前账号全部预约，不限定为已确认。保持数组结构，首版不新增分页、按状态筛选或独立摘要接口。默认排序从原 ID 降序改为就诊日期、开始时间、ID 降序。

列表继续使用现有 `AppointmentResponse`，每项增加可空 `visit`，以及场次标签所需的可空 `sessionType`。既有字段保持原含义。

### 3.2 详情

```http
GET /api/v1/appointments/103
```

继续返回现有预约快照、资金摘要、状态及操作标记。以下是增量相关字段示例；未列出的原有字段仍返回，见现行 API：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "103",
    "status": "CONFIRMED",
    "paymentStatus": "PAID",
    "workflowStatus": "CONFIRMED",
    "appointmentDate": "2026-11-13",
    "sessionType": "MORNING_FIRST",
    "startTime": "08:00:00",
    "endTime": "10:00:00",
    "visitNumber": 3,
    "visit": {
      "status": "NOT_CHECKED_IN",
      "checkedInAt": null,
      "startedAt": null,
      "completedAt": null,
      "canCheckIn": true
    }
  }
}
```

| 新字段 | 含义 |
| --- | --- |
| `sessionType` | 场次类型，供前端映射“上午第一段”等标签；关联历史场次可能为空或为旧类型 |
| `visit` | 已确认预约的就诊进度；其他预约状态为 `null` |
| `visit.status` | `NOT_CHECKED_IN` / `WAITING` / `IN_PROGRESS` / `COMPLETED` |
| `visit.checkedInAt` | 签到时间 |
| `visit.startedAt` | 开始接诊时间 |
| `visit.completedAt` | 完成接诊时间 |
| `visit.canCheckIn` | 已确认、就诊当天、尚未签到时为 true |

`sessionType` 读取关联场次类型；日期和起止时间仍使用预约快照。待支付、取消等原记录继续按 `workflowStatus` 展示，不将没有就诊记录统一解释为待签到。

确认预约没有就诊记录时，GET 计算 `NOT_CHECKED_IN`，不创建记录。尚未到就诊日时 `canCheckIn=false`；就诊当天为 true。签到后以及后续阶段均为 false。

当天范围为上海时间 00:00（含）至次日 00:00（不含）；没有预约时段或提前分钟限制，下午场当天上午及场次结束后仍可签到，次日不能首次签到。医生开始/完成只校验归属、已确认预约及当前进度，没有额外的日期或场次时间限制；跨日未完成记录不自动变更。

### 3.3 签到

```http
POST /api/v1/appointments/103/check-in
```

无请求体，不提交状态、时间或就诊序号。正常前置条件：本人预约、已确认、当天、尚未签到。

创建就诊记录并进入 `WAITING`，签到时间由服务端生成。响应为更新后的完整 `AppointmentResponse`，其中 `visit` 为：

```json
{
  "status": "WAITING",
  "checkedInAt": "2026-11-13T07:50:00",
  "startedAt": null,
  "completedAt": null,
  "canCheckIn": false
}
```

用户后续通过原列表、详情查询或手动刷新获取进度，不提供用户开始/完成接诊接口。

## 4. 管理员：关联医生账号

```http
PUT /api/v1/admin/doctors/1/account
Content-Type: application/json

{"userId":"12"}
```

将已存在账号与医生档案关联，并在同一事务授予 `DOCTOR` 角色。账号与医生一对一，首版只覆盖首次绑定，不提供解绑、换绑接口。

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "doctorId": "1",
    "userId": "12"
  }
}
```

完成后用该账号重新登录取得含医生角色的 Token。

## 5. 医生：日期与四场概览

```http
GET /api/v1/doctor/sessions?date=2026-11-13
```

`date` 可省略，默认服务端今天。返回本人该日四个固定场次，按开始时间升序。无实际场次或无人预约仍返回四项，各人数为零，不写入场次。

每项字段如下：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `appointmentDate` | String | 就诊日期 |
| `sessionType` | String | 四场类型之一 |
| `startTime` / `endTime` | String | 固定场次起止时间 |
| `confirmedCount` | Number | 当前已确认预约总人数，含已就诊 |
| `notCheckedInCount` | Number | 待签到人数 |
| `waitingCount` | Number | 候诊人数 |
| `inProgressCount` | Number | 就诊中人数 |
| `completedCount` | Number | 已就诊人数 |

`data` 为数组。下面是上午第一段的一项示例，实际响应始终包含四项：

```json
{
  "appointmentDate": "2026-11-13",
  "sessionType": "MORNING_FIRST",
  "startTime": "08:00:00",
  "endTime": "10:00:00",
  "confirmedCount": 4,
  "notCheckedInCount": 1,
  "waitingCount": 1,
  "inProgressCount": 1,
  "completedCount": 1
}
```

四个分项人数之和等于 `confirmedCount`。使用现有四场类型和时间，不返回新的开放/关闭状态。前端点击一项时带其日期与场次类型查询名单，不依赖可能尚不存在的 `scheduleId`。

## 6. 医生：场次人员名单

```http
GET /api/v1/doctor/appointments?date=2026-11-13&sessionType=MORNING_FIRST
```

`date` 与 `sessionType` 均必填，场次类型为现有四种类型之一。返回本人该场次全部已确认预约，按 `visitNumber ASC`，无人员返回 `[]`。

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "appointmentId": "101",
      "visitNumber": 1,
      "patientName": "张某",
      "visitStatus": "COMPLETED",
      "checkedInAt": "2026-11-13T07:40:00",
      "canStartVisit": false,
      "canCompleteVisit": false
    },
    {
      "appointmentId": "102",
      "visitNumber": 2,
      "patientName": "李某",
      "visitStatus": "IN_PROGRESS",
      "checkedInAt": "2026-11-13T07:45:00",
      "canStartVisit": false,
      "canCompleteVisit": true
    },
    {
      "appointmentId": "103",
      "visitNumber": 3,
      "patientName": "王小明",
      "visitStatus": "WAITING",
      "checkedInAt": "2026-11-13T07:50:00",
      "canStartVisit": true,
      "canCompleteVisit": false
    },
    {
      "appointmentId": "104",
      "visitNumber": 4,
      "patientName": "赵某",
      "visitStatus": "NOT_CHECKED_IN",
      "checkedInAt": null,
      "canStartVisit": false,
      "canCompleteVisit": false
    }
  ]
}
```

前端固定显示查看、开始接诊、完成接诊三个按钮。查看始终启用，无需 `canView`；另外两个分别使用布尔字段。签到、接诊后不改变排序。

## 7. 医生：查看详情

```http
GET /api/v1/doctor/appointments/103
```

待签到、候诊中、就诊中、已就诊均可查看本人预约。详情使用预约快照，区别于仅限预约所属账号调用的用户详情接口。

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "appointmentId": "103",
    "appointmentNo": "EXAMPLE-APPOINTMENT-103",
    "visitNumber": 3,
    "appointmentDate": "2026-11-13",
    "sessionType": "MORNING_FIRST",
    "startTime": "08:00:00",
    "endTime": "10:00:00",
    "patient": {
      "id": "21",
      "name": "王小明",
      "documentType": "RESIDENT_ID_CARD",
      "documentNoMasked": "110105********1234"
    },
    "doctor": {
      "id": "1",
      "doctorNo": "DOC000001",
      "name": "李医生",
      "title": "主任医师",
      "department": {"id":"2","name":"心内科"},
      "hospital": {"id":"1","name":"市第一医院","address":"示例医院地址"}
    },
    "visit": {
      "status": "WAITING",
      "checkedInAt": "2026-11-13T07:50:00",
      "startedAt": null,
      "completedAt": null
    }
  }
}
```

`appointmentNo` 示例仅表达字段用途，不规定现有单号生成格式。医生详情的 `visit` 只返回状态和时间，不带用户签到资格。详情只读，开始和完成操作保留在名单。

## 8. 医生：开始与完成接诊

```http
POST /api/v1/doctor/appointments/103/start-visit
```

无请求体。登录医生为该预约所属医生，当前进度为 `WAITING`。保存 `IN_PROGRESS` 和服务端开始时间，返回第 7 节同结构的更新后详情，其 `visit` 示例：

```json
{
  "status": "IN_PROGRESS",
  "checkedInAt": "2026-11-13T07:50:00",
  "startedAt": "2026-11-13T08:15:00",
  "completedAt": null
}
```

```http
POST /api/v1/doctor/appointments/103/complete-visit
```

无请求体。登录医生为该预约所属医生，当前进度为 `IN_PROGRESS`。保存 `COMPLETED` 和服务端完成时间，返回更新后详情，其 `visit` 示例：

```json
{
  "status": "COMPLETED",
  "checkedInAt": "2026-11-13T07:50:00",
  "startedAt": "2026-11-13T08:15:00",
  "completedAt": "2026-11-13T08:28:00"
}
```

医生操作成功后，前端重新查询当前名单和场次概览，更新按钮与统计；用户刷新原预约详情即可看到相同状态。完成接诊不改变库存、号码、快照和支付结果。

## 9. HTTP 参数与代码类型对照

| HTTP 输入 | Controller 中的类型或处理 | 传入业务方法 |
| --- | --- | --- |
| 当前登录身份 | `(Long) authentication.getPrincipal()` | userId 或 doctorUserId，不接受请求替代 |
| `{appointmentId}`、`{doctorId}` | Long | 预约或医生标识 |
| 用户列表 `order` | AppointmentTimeOrder，默认 DESC | listMine(userId, order) |
| 医生概览 `date` | 可空 LocalDate | listSessions 内按业务 Clock 补今天 |
| 医生名单 `date` | 必填 LocalDate | listAppointments 的 date |
| 医生名单 `sessionType` | 必填 String，经 AppointmentSession.require 转换 | listAppointments 的 AppointmentSession |
| 绑定请求 `userId` | BindDoctorAccountRequest 中的 @NotNull Long | bindAccount(doctorId, userId) |

三个动作请求均无 body，时间和状态由服务端产生。HTTP 层先执行写 Service，事务完成后再调用查询 Service 返回最新详情；不由前端拼出一份“已签到”或“已就诊”的替代响应。

人数 DTO 使用 int、号码使用 Integer，保持数值输出；标识使用 Long，沿用字符串输出。用户进度 DTO 带 canCheckIn，医生进度 DTO 只含状态与时间；医生名单另带 canStartVisit 和 canCompleteVisit。

前端按以下顺序接入：列表 GET → 详情 GET → 签到 POST 后替换当前详情；医生概览 GET → 场次名单 GET → 查看详情 GET 或动作 POST → 重查名单与概览。无需新增卡片摘要、用户接诊动作或修改状态的接口。

## 10. 必要错误与重复操作

未登录返回 401；角色或写操作归属不符返回 403；预约、账号不存在返回 404。医生详情查询范围内找不到已确认预约也返回 404，不返回其他医生的数据。缺失必填参数、非法日期、排序或场次返回 400/1001。

医生或账号已经绑定返回 409/4501，首版不提供同绑定重复成功、换绑和解绑。签到日期、预约或就诊状态不满足资格，以及重复签到、开始、完成返回 409/4502；首次成功时间保留，不用新的请求时间覆盖。
