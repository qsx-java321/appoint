# API主流程测试报告

> 历史验收，执行于 2026-11-16，早于邮箱验证码注册和共用端口改造。当前注册请求见[REST API](../api.md#账号注册登录与当前用户)，启动方式见[环境说明](../environments.md#启动和切换)。

## 测试概况

本轮在test环境通过真实HTTP请求完成用户注册到就诊完成的主流程。请求和响应来自实际执行，JSON已格式化；密码、Token和支付签名使用占位符脱敏，ID、业务时间与其他业务字段保留实际值。

- 执行时间：2026-11-16 20:19:25至2026-11-16 20:19:27，Asia/Shanghai。
- 代码版本：`fb653b5cdf777178aaeb60904680fe60c45d69e1`；存在前序文档整理改动；本轮未修改生产代码。
- 环境：Spring `test` profile，后端端口8081；真实MySQL `appoint_test`、Redis `test`命名空间、RabbitMQ `/appoint-test`。Flyway V1、V2成功。
- 结果：主流程通过；34次HTTP请求全部符合预期，其中业务请求29次，调时及环境辅助请求5次，检查点共84项。
- 支付：MockPay模拟渠道付款，经正式验签、Outbox和消息消费链路确认预约。
- 预约场次：2026-11-17，MORNING_FIRST，08:00–10:00。
- 调时前确认：无待支付预约、待支付/退款资金单、未发布Outbox、未完成退款，以及已确认但未就诊完成的存量预约。
- 测试范围：注册、登录、就诊人、目录查询、预约、支付、签到、医生接诊和最终结果查询。本轮未执行异常分支。

## 本轮业务数据

| 数据 | 实际值 |
| --- | --- |
| 用户邮箱 | api-mainflow-20261116-201925@example.com |
| 用户ID | 2122303525106008065 |
| 就诊人ID | 2122303525575770113 |
| 医生 | 张医生，ID=1；账号test-doctor-001@example.local |
| 预约ID | 2122303527282851841 |
| 支付单ID | 2122303527312211969 |
| 就诊序号 | 1 |
| 挂号费 | 10.00 CNY |

## 主流程结果

预约与支付从待支付状态，经实际2次查询观察到`PENDING_PAYMENT / PAYING`，随后进入`CONFIRMED / PAID`。就诊进度依次为`NOT_CHECKED_IN → WAITING → IN_PROGRESS → COMPLETED`，用户与医生最终看到相同的就诊时间。

| 最终核对项 | 实际结果 |
| --- | --- |
| 预约状态 | CONFIRMED |
| 支付状态 | PAID |
| 就诊状态 | COMPLETED |
| 签到时间 | 2026-11-17T07:35:00 |
| 开始接诊时间 | 2026-11-17T08:05:00 |
| 完成接诊时间 | 2026-11-17T08:05:00 |
| 用户与医生详情、名单、场次统计 | 一致 |
| 数据库最终状态 | CONFIRMED / PAID / COMPLETED，与接口一致 |
| 场次库存 | 总量20 = 可用19 + 暂占0 + 已确认1 |
| 支付Outbox | 1条PUBLISHED |
| 协调Inbox | 1条消费记录 |

实际请求发生在2026-11-16晚间；通过test调时接口推进至预约日验证签到和接诊，因此就诊业务时间为2026-11-17。接诊开始和完成连续调用，业务时间按秒保存，两者落在同一秒。

## 请求记录索引

记录按实际调用顺序排列。点击序号跳转到对应步骤，再展开请求、响应和检查结果。

| 序号 | 业务 | 接口 | HTTP响应码 | 结果 |
| --- | --- | --- | --- | --- |
| [01](#01-test管理员登录) | test管理员登录 | `POST /api/v1/auth/login` | 200 | 通过 |
| [02](#02-查询测试前业务时间) | 查询测试前业务时间 | `GET /api/v1/test-control/clock` | 200 | 通过 |
| [03](#03-注册用户) | 注册用户 | `POST /api/v1/auth/register` | 201 | 通过 |
| [04](#04-用户登录) | 用户登录 | `POST /api/v1/auth/login` | 200 | 通过 |
| [05](#05-查询当前用户) | 查询当前用户 | `GET /api/v1/users/me` | 200 | 通过 |
| [06](#06-新增就诊人) | 新增就诊人 | `POST /api/v1/patients` | 201 | 通过 |
| [07](#07-查询就诊人列表) | 查询就诊人列表 | `GET /api/v1/patients` | 200 | 通过 |
| [08](#08-查询就诊人详情) | 查询就诊人详情 | `GET /api/v1/patients/2122303525575770113` | 200 | 通过 |
| [09](#09-查询医院) | 查询医院 | `GET /api/v1/hospitals` | 200 | 通过 |
| [10](#10-查询科室) | 查询科室 | `GET /api/v1/hospitals/1/departments` | 200 | 通过 |
| [11](#11-查询医生) | 查询医生 | `GET /api/v1/departments/1/doctors` | 200 | 通过 |
| [12](#12-查询可预约场次) | 查询可预约场次 | `GET /api/v1/doctors/1/schedules` | 200 | 通过 |
| [13](#13-创建预约) | 创建预约 | `POST /api/v1/appointments` | 201 | 通过 |
| [14](#14-查询待支付预约) | 查询待支付预约 | `GET /api/v1/appointments/2122303527282851841` | 200 | 通过 |
| [15](#15-发起支付尝试) | 发起支付尝试 | `POST /api/v1/payments/2122303527312211969/attempts` | 202 | 通过 |
| [16](#16-mockpay模拟支付成功) | MockPay模拟支付成功 | `POST /api/v1/mock-pay/attempts/1722ad35-dd92-4129-a7ab-0548e3724506/complete` | 202 | 通过 |
| [17](#17-轮询预约确认第1次) | 轮询预约确认（第1次） | `GET /api/v1/appointments/2122303527282851841` | 200 | 通过 |
| [18](#18-轮询预约确认第2次) | 轮询预约确认（第2次） | `GET /api/v1/appointments/2122303527282851841` | 200 | 通过 |
| [19](#19-调时至预约日0735进入签到窗口) | 调时至预约日07:35，进入签到窗口 | `PUT /api/v1/test-control/clock` | 200 | 通过 |
| [20](#20-用户到院签到) | 用户到院签到 | `POST /api/v1/appointments/2122303527282851841/check-in` | 200 | 通过 |
| [21](#21-医生登录) | 医生登录 | `POST /api/v1/auth/login` | 200 | 通过 |
| [22](#22-医生查询候诊场次概览) | 医生查询候诊场次概览 | `GET /api/v1/doctor/sessions?date=2026-11-17` | 200 | 通过 |
| [23](#23-医生查询候诊名单) | 医生查询候诊名单 | `GET /api/v1/doctor/appointments?date=2026-11-17&sessionType=MORNING_FIRST` | 200 | 通过 |
| [24](#24-医生查询预约详情) | 医生查询预约详情 | `GET /api/v1/doctor/appointments/2122303527282851841` | 200 | 通过 |
| [25](#25-调时至预约日0805开始门诊接诊) | 调时至预约日08:05，开始门诊接诊 | `PUT /api/v1/test-control/clock` | 200 | 通过 |
| [26](#26-医生开始接诊) | 医生开始接诊 | `POST /api/v1/doctor/appointments/2122303527282851841/start-visit` | 200 | 通过 |
| [27](#27-用户查询接诊中进度) | 用户查询接诊中进度 | `GET /api/v1/appointments/2122303527282851841` | 200 | 通过 |
| [28](#28-医生完成接诊) | 医生完成接诊 | `POST /api/v1/doctor/appointments/2122303527282851841/complete-visit` | 200 | 通过 |
| [29](#29-用户查询就诊完成结果) | 用户查询就诊完成结果 | `GET /api/v1/appointments/2122303527282851841` | 200 | 通过 |
| [30](#30-用户查询最终预约列表) | 用户查询最终预约列表 | `GET /api/v1/appointments/my?order=DESC` | 200 | 通过 |
| [31](#31-医生查询最终预约详情) | 医生查询最终预约详情 | `GET /api/v1/doctor/appointments/2122303527282851841` | 200 | 通过 |
| [32](#32-医生查询最终名单) | 医生查询最终名单 | `GET /api/v1/doctor/appointments?date=2026-11-17&sessionType=MORNING_FIRST` | 200 | 通过 |
| [33](#33-医生查询最终场次统计) | 医生查询最终场次统计 | `GET /api/v1/doctor/sessions?date=2026-11-17` | 200 | 通过 |
| [34](#34-恢复test原有实时业务时钟) | 恢复test原有实时业务时钟 | `DELETE /api/v1/test-control/clock` | 200 | 通过 |

## 接口请求与响应明细

### 01. test管理员登录

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：test管理员登录。执行身份：管理员；分类：环境辅助。

接口：`POST /api/v1/auth/login`

请求时间：2026-11-16T20:19:25.053+08:00；耗时：356.9 ms。

请求头：

```http
Accept: application/json
Content-Type: application/json
```

请求 JSON：

```json
{
  "email": "test-admin@example.local",
  "password": "<测试密码，已脱敏>"
}
```

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "<管理员Token>",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "user": {
      "id": "1",
      "email": "test-admin@example.local",
      "roles": [
        "USER",
        "ADMIN"
      ]
    }
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 管理员账号具有ADMIN角色：通过。

本步结论：**通过**。

</details>

### 02. 查询测试前业务时间

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：查询测试前业务时间。执行身份：管理员；分类：环境辅助。

接口：`GET /api/v1/test-control/clock`

请求时间：2026-11-16T20:19:25.411+08:00；耗时：47.19 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <管理员Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "businessTime": "2026-11-16T20:19:25.454794211+08:00",
    "mode": "realtime"
  }
}
```

检查结果：

- HTTP 200，业务码code=0。

本步结论：**通过**。

</details>

### 03. 注册用户

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：注册用户。执行身份：用户；分类：主流程。

接口：`POST /api/v1/auth/register`

请求时间：2026-11-16T20:19:25.459+08:00；耗时：89.96 ms。

请求头：

```http
Accept: application/json
Content-Type: application/json
```

请求 JSON：

```json
{
  "email": "api-mainflow-20261116-201925@example.com",
  "password": "<测试密码，已脱敏>"
}
```

HTTP 响应码：`201 Created`；预期：`201`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "2122303525106008065",
    "email": "api-mainflow-20261116-201925@example.com",
    "roles": [
      "USER"
    ]
  }
}
```

检查结果：

- HTTP 201，业务码code=0。
- 邮箱与注册邮箱一致且角色为USER：通过。

本步结论：**通过**。

</details>

### 04. 用户登录

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：用户登录。执行身份：用户；分类：主流程。

接口：`POST /api/v1/auth/login`

请求时间：2026-11-16T20:19:25.550+08:00；耗时：76.52 ms。

请求头：

```http
Accept: application/json
Content-Type: application/json
```

请求 JSON：

```json
{
  "email": "api-mainflow-20261116-201925@example.com",
  "password": "<测试密码，已脱敏>"
}
```

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "<用户Token>",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "user": {
      "id": "2122303525106008065",
      "email": "api-mainflow-20261116-201925@example.com",
      "roles": [
        "USER"
      ]
    }
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 返回Bearer Token且登录用户与注册一致：通过。

本步结论：**通过**。

</details>

### 05. 查询当前用户

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：查询当前用户。执行身份：用户；分类：主流程。

接口：`GET /api/v1/users/me`

请求时间：2026-11-16T20:19:25.628+08:00；耗时：7.92 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <用户Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "2122303525106008065",
    "email": "api-mainflow-20261116-201925@example.com",
    "roles": [
      "USER"
    ]
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 当前用户身份一致：通过。

本步结论：**通过**。

</details>

### 06. 新增就诊人

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：新增就诊人。执行身份：用户；分类：主流程。

接口：`POST /api/v1/patients`

请求时间：2026-11-16T20:19:25.637+08:00；耗时：16.28 ms。

请求头：

```http
Accept: application/json
Content-Type: application/json
Authorization: Bearer <用户Token>
```

请求 JSON：

```json
{
  "name": "接口主流程测试就诊人",
  "documentType": "RESIDENT_ID_CARD",
  "documentNo": "11010519491231002X",
  "contactPhone": "13800138000",
  "contactEmail": "api-patient@example.com"
}
```

HTTP 响应码：`201 Created`；预期：`201`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "2122303525575770113",
    "name": "接口主流程测试就诊人",
    "documentType": "RESIDENT_ID_CARD",
    "documentNoMasked": "110105********002X",
    "contactPhone": "13800138000",
    "contactEmail": "api-patient@example.com",
    "enabled": true,
    "createdAt": "2026-11-16T20:19:25",
    "updatedAt": "2026-11-16T20:19:25"
  }
}
```

检查结果：

- HTTP 201，业务码code=0。
- 就诊人已启用且身份证号脱敏：通过。

本步结论：**通过**。

</details>

### 07. 查询就诊人列表

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：查询就诊人列表。执行身份：用户；分类：主流程。

接口：`GET /api/v1/patients`

请求时间：2026-11-16T20:19:25.655+08:00；耗时：5.93 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <用户Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": "2122303525575770113",
      "name": "接口主流程测试就诊人",
      "documentType": "RESIDENT_ID_CARD",
      "documentNoMasked": "110105********002X",
      "contactPhone": "13800138000",
      "contactEmail": "api-patient@example.com",
      "enabled": true,
      "createdAt": "2026-11-16T20:19:25",
      "updatedAt": "2026-11-16T20:19:25"
    }
  ]
}
```

检查结果：

- HTTP 200，业务码code=0。
- 列表包含新增就诊人：通过。

本步结论：**通过**。

</details>

### 08. 查询就诊人详情

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：查询就诊人详情。执行身份：用户；分类：主流程。

接口：`GET /api/v1/patients/2122303525575770113`

请求时间：2026-11-16T20:19:25.662+08:00；耗时：5.95 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <用户Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "2122303525575770113",
    "name": "接口主流程测试就诊人",
    "documentType": "RESIDENT_ID_CARD",
    "documentNoMasked": "110105********002X",
    "contactPhone": "13800138000",
    "contactEmail": "api-patient@example.com",
    "enabled": true,
    "createdAt": "2026-11-16T20:19:25",
    "updatedAt": "2026-11-16T20:19:25"
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 详情与新增资料一致：通过。

本步结论：**通过**。

</details>

### 09. 查询医院

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：查询医院。执行身份：用户；分类：主流程。

接口：`GET /api/v1/hospitals`

请求时间：2026-11-16T20:19:25.669+08:00；耗时：307.64 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <用户Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": "1",
      "name": "示例市第一人民医院",
      "address": "示例市健康路 1 号"
    },
    {
      "id": "2",
      "name": "示例市第二人民医院",
      "address": "示例市健康路 20 号"
    }
  ]
}
```

检查结果：

- HTTP 200，业务码code=0。
- 存在医院目录：通过。

本步结论：**通过**。

</details>

### 10. 查询科室

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：查询科室。执行身份：用户；分类：主流程。

接口：`GET /api/v1/hospitals/1/departments`

请求时间：2026-11-16T20:19:25.978+08:00；耗时：10.54 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <用户Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": "1",
      "name": "内科"
    },
    {
      "id": "3",
      "name": "皮肤科"
    },
    {
      "id": "2",
      "name": "骨科"
    }
  ]
}
```

检查结果：

- HTTP 200，业务码code=0。
- 所选医院存在科室：通过。

本步结论：**通过**。

</details>

### 11. 查询医生

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：查询医生。执行身份：用户；分类：主流程。

接口：`GET /api/v1/departments/1/doctors`

请求时间：2026-11-16T20:19:25.991+08:00；耗时：10.26 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <用户Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": "1",
      "doctorNo": "DOC000001",
      "name": "张医生",
      "title": "主任医师",
      "introduction": "擅长常见内科疾病诊治。"
    },
    {
      "id": "3",
      "doctorNo": "DOC000003",
      "name": "王医生",
      "title": "副主任医师",
      "introduction": "擅长消化系统常见疾病诊治。"
    },
    {
      "id": "4",
      "doctorNo": "DOC000004",
      "name": "赵医生",
      "title": "主治医师",
      "introduction": "擅长呼吸系统常见疾病诊治。"
    }
  ]
}
```

检查结果：

- HTTP 200，业务码code=0。
- 目录包含test-doctor-001对应的医生：通过。

本步结论：**通过**。

</details>

### 12. 查询可预约场次

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：查询可预约场次。执行身份：用户；分类：主流程。

接口：`GET /api/v1/doctors/1/schedules`

请求时间：2026-11-16T20:19:26.003+08:00；耗时：21.26 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <用户Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "doctorId": "1",
      "doctorNo": "DOC000001",
      "appointmentDate": "2026-11-16",
      "sessionType": "MORNING_FIRST",
      "startTime": "08:00:00",
      "endTime": "10:00:00",
      "totalCapacity": 20,
      "availableCapacity": 20,
      "registrationFee": "10.00",
      "feeCurrency": "CNY",
      "bookable": false,
      "bookingClosesAt": "2026-11-16T07:45:00",
      "availability": "STARTED",
      "serverTime": "2026-11-16T20:19:26"
    },
    {
      "doctorId": "1",
      "doctorNo": "DOC000001",
      "appointmentDate": "2026-11-16",
      "sessionType": "MORNING_SECOND",
      "startTime": "10:30:00",
      "endTime": "12:00:00",
      "totalCapacity": 20,
      "availableCapacity": 20,
      "registrationFee": "10.00",
      "feeCurrency": "CNY",
      "bookable": false,
      "bookingClosesAt": "2026-11-16T10:15:00",
      "availability": "STARTED",
      "serverTime": "2026-11-16T20:19:26"
    },
    {
      "doctorId": "1",
      "doctorNo": "DOC000001",
      "appointmentDate": "2026-11-16",
      "sessionType": "AFTERNOON_FIRST",
      "startTime": "14:00:00",
      "endTime": "16:00:00",
      "totalCapacity": 20,
      "availableCapacity": 20,
      "registrationFee": "10.00",
      "feeCurrency": "CNY",
      "bookable": false,
      "bookingClosesAt": "2026-11-16T13:45:00",
      "availability": "STARTED",
      "serverTime": "2026-11-16T20:19:26"
    },
    {
      "doctorId": "1",
      "doctorNo": "DOC000001",
      "appointmentDate": "2026-11-16",
      "sessionType": "AFTERNOON_SECOND",
      "startTime": "16:30:00",
      "endTime": "18:00:00",
      "totalCapacity": 20,
      "availableCapacity": 20,
      "registrationFee": "10.00",
      "feeCurrency": "CNY",
      "bookable": false,
      "bookingClosesAt": "2026-11-16T16:15:00",
      "availability": "STARTED",
      "serverTime": "2026-11-16T20:19:26"
    },
    {
      "doctorId": "1",
      "doctorNo": "DOC000001",
      "appointmentDate": "2026-11-17",
      "sessionType": "MORNING_FIRST",
      "startTime": "08:00:00",
      "endTime": "10:00:00",
      "totalCapacity": 20,
      "availableCapacity": 20,
      "registrationFee": "10.00",
      "feeCurrency": "CNY",
      "bookable": true,
      "bookingClosesAt": "2026-11-17T07:45:00",
      "availability": "AVAILABLE",
      "serverTime": "2026-11-16T20:19:26"
    },
    {
      "doctorId": "1",
      "doctorNo": "DOC000001",
      "appointmentDate": "2026-11-17",
      "sessionType": "MORNING_SECOND",
      "startTime": "10:30:00",
      "endTime": "12:00:00",
      "totalCapacity": 20,
      "availableCapacity": 20,
      "registrationFee": "10.00",
      "feeCurrency": "CNY",
      "bookable": true,
      "bookingClosesAt": "2026-11-17T10:15:00",
      "availability": "AVAILABLE",
      "serverTime": "2026-11-16T20:19:26"
    },
    {
      "doctorId": "1",
      "doctorNo": "DOC000001",
      "appointmentDate": "2026-11-17",
      "sessionType": "AFTERNOON_FIRST",
      "startTime": "14:00:00",
      "endTime": "16:00:00",
      "totalCapacity": 20,
      "availableCapacity": 20,
      "registrationFee": "10.00",
      "feeCurrency": "CNY",
      "bookable": true,
      "bookingClosesAt": "2026-11-17T13:45:00",
      "availability": "AVAILABLE",
      "serverTime": "2026-11-16T20:19:26"
    },
    {
      "doctorId": "1",
      "doctorNo": "DOC000001",
      "appointmentDate": "2026-11-17",
      "sessionType": "AFTERNOON_SECOND",
      "startTime": "16:30:00",
      "endTime": "18:00:00",
      "totalCapacity": 20,
      "availableCapacity": 20,
      "registrationFee": "10.00",
      "feeCurrency": "CNY",
      "bookable": true,
      "bookingClosesAt": "2026-11-17T16:15:00",
      "availability": "AVAILABLE",
      "serverTime": "2026-11-16T20:19:26"
    },
    {
      "doctorId": "1",
      "doctorNo": "DOC000001",
      "appointmentDate": "2026-11-18",
      "sessionType": "MORNING_FIRST",
      "startTime": "08:00:00",
      "endTime": "10:00:00",
      "totalCapacity": 20,
      "availableCapacity": 20,
      "registrationFee": "10.00",
      "feeCurrency": "CNY",
      "bookable": true,
      "bookingClosesAt": "2026-11-18T07:45:00",
      "availability": "AVAILABLE",
      "serverTime": "2026-11-16T20:19:26"
    },
    {
      "doctorId": "1",
      "doctorNo": "DOC000001",
      "appointmentDate": "2026-11-18",
      "sessionType": "MORNING_SECOND",
      "startTime": "10:30:00",
      "endTime": "12:00:00",
      "totalCapacity": 20,
      "availableCapacity": 20,
      "registrationFee": "10.00",
      "feeCurrency": "CNY",
      "bookable": true,
      "bookingClosesAt": "2026-11-18T10:15:00",
      "availability": "AVAILABLE",
      "serverTime": "2026-11-16T20:19:26"
    },
    {
      "doctorId": "1",
      "doctorNo": "DOC000001",
      "appointmentDate": "2026-11-18",
      "sessionType": "AFTERNOON_FIRST",
      "startTime": "14:00:00",
      "endTime": "16:00:00",
      "totalCapacity": 20,
      "availableCapacity": 20,
      "registrationFee": "10.00",
      "feeCurrency": "CNY",
      "bookable": true,
      "bookingClosesAt": "2026-11-18T13:45:00",
      "availability": "AVAILABLE",
      "serverTime": "2026-11-16T20:19:26"
    },
    {
      "doctorId": "1",
      "doctorNo": "DOC000001",
      "appointmentDate": "2026-11-18",
      "sessionType": "AFTERNOON_SECOND",
      "startTime": "16:30:00",
      "endTime": "18:00:00",
      "totalCapacity": 20,
      "availableCapacity": 20,
      "registrationFee": "10.00",
      "feeCurrency": "CNY",
      "bookable": true,
      "bookingClosesAt": "2026-11-18T16:15:00",
      "availability": "AVAILABLE",
      "serverTime": "2026-11-16T20:19:26"
    }
  ]
}
```

检查结果：

- HTTP 200，业务码code=0。
- 返回今天至后天共12场：通过。
- 明天上午首场可预约且有余量：通过。

本步结论：**通过**。

</details>

### 13. 创建预约

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：创建预约。执行身份：用户；分类：主流程。

接口：`POST /api/v1/appointments`

请求时间：2026-11-16T20:19:26.027+08:00；耗时：47.85 ms。

请求头：

```http
Accept: application/json
Content-Type: application/json
Authorization: Bearer <用户Token>
Idempotency-Key: 124a1f9d-b73b-40ac-9e7d-0b47e4d15433
```

请求 JSON：

```json
{
  "doctorId": "1",
  "appointmentDate": "2026-11-17",
  "sessionType": "MORNING_FIRST",
  "patientId": "2122303525575770113"
}
```

HTTP 响应码：`201 Created`；预期：`201`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "appointmentOrderId": "2122303527282851841",
    "paymentOrderId": "2122303527312211969",
    "appointmentStatus": "PENDING_PAYMENT",
    "paymentStatus": "UNPAID",
    "amount": "10.00",
    "currency": "CNY",
    "paymentExpiresAt": "2026-11-16T20:34:26",
    "serverTime": "2026-11-16T20:19:26"
  }
}
```

检查结果：

- HTTP 201，业务码code=0。
- 新预约待支付且费用与场次一致：通过。

本步结论：**通过**。

</details>

### 14. 查询待支付预约

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：查询待支付预约。执行身份：用户；分类：主流程。

接口：`GET /api/v1/appointments/2122303527282851841`

请求时间：2026-11-16T20:19:26.077+08:00；耗时：14.14 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <用户Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "2122303527282851841",
    "appointmentNo": "AP8fa5e6e1729a4d149c77a5e560aefbe0",
    "scheduleId": "6",
    "appointmentDate": "2026-11-17",
    "startTime": "08:00:00",
    "endTime": "10:00:00",
    "status": "PENDING_PAYMENT",
    "visitNumber": null,
    "createdAt": "2026-11-16T20:19:26",
    "paymentOrderId": "2122303527312211969",
    "paymentStatus": "UNPAID",
    "workflowStatus": "WAITING_FOR_PAYMENT",
    "amount": 10.0,
    "currency": "CNY",
    "paymentAttemptCount": 0,
    "latestPaymentAttempt": null,
    "refund": null,
    "paymentExpiresAt": "2026-11-16T20:34:26",
    "closedAt": null,
    "serverTime": "2026-11-16T20:19:26",
    "paymentExpired": false,
    "canPay": true,
    "canCancel": true,
    "patient": {
      "id": "2122303525575770113",
      "name": "接口主流程测试就诊人",
      "documentType": "RESIDENT_ID_CARD",
      "documentNoMasked": "110105********002X",
      "contactPhone": "13800138000",
      "contactEmail": "api-patient@example.com"
    },
    "doctor": {
      "id": "1",
      "doctorNo": "DOC000001",
      "name": "张医生",
      "title": "主任医师",
      "department": {
        "id": "1",
        "name": "内科"
      },
      "hospital": {
        "id": "1",
        "name": "示例市第一人民医院",
        "address": "示例市健康路 1 号"
      }
    },
    "sessionType": "MORNING_FIRST",
    "visit": null
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 预约归属、医生、就诊人和场次一致：通过。
- 待支付且允许支付：通过。

本步结论：**通过**。

</details>

### 15. 发起支付尝试

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：发起支付尝试。执行身份：用户；分类：主流程。

接口：`POST /api/v1/payments/2122303527312211969/attempts`

请求时间：2026-11-16T20:19:26.095+08:00；耗时：28.73 ms。

请求头：

```http
Accept: application/json
Content-Type: application/json
Authorization: Bearer <用户Token>
```

请求 JSON：

```json
{
  "channel": "MOCK"
}
```

HTTP 响应码：`202 Accepted`；预期：`202`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "paymentOrderId": "2122303527312211969",
    "paymentAttemptId": "2122303527505149954",
    "channel": "MOCK",
    "paymentStatus": "PAYING",
    "attemptStatus": "PROCESSING",
    "providerOrderNo": "mock-pay-1722ad35-dd92-4129-a7ab-0548e3724506",
    "checkoutUrl": "/api/v1/mock-pay/attempts/1722ad35-dd92-4129-a7ab-0548e3724506",
    "expiresAt": "2026-11-16T20:34:26"
  }
}
```

检查结果：

- HTTP 202，业务码code=0。
- 支付处理中并返回MockPay入口：通过。

本步结论：**通过**。

</details>

### 16. MockPay模拟支付成功

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：MockPay模拟支付成功。执行身份：用户；分类：主流程。

接口：`POST /api/v1/mock-pay/attempts/1722ad35-dd92-4129-a7ab-0548e3724506/complete`

请求时间：2026-11-16T20:19:26.126+08:00；耗时：7.31 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <用户Token>
```

请求体：无。

HTTP 响应码：`202 Accepted`；预期：`202`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "notificationId": "mock-notify-1722ad35-dd92-4129-a7ab-0548e3724506",
    "providerOrderNo": "mock-pay-1722ad35-dd92-4129-a7ab-0548e3724506",
    "providerTransactionNo": "mock-txn-1722ad35-dd92-4129-a7ab-0548e3724506",
    "status": "SUCCEEDED",
    "amount": 10.0,
    "currency": "CNY",
    "occurredAt": "2026-11-16T20:19:26.129181114",
    "signature": "<支付签名，已脱敏>"
  }
}
```

检查结果：

- HTTP 202，业务码code=0。
- 成功通知为SUCCEEDED且金额币种一致：通过。

本步结论：**通过**。

</details>

### 17. 轮询预约确认（第1次）

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：轮询预约确认（第1次）。执行身份：用户；分类：主流程。

接口：`GET /api/v1/appointments/2122303527282851841`

请求时间：2026-11-16T20:19:26.136+08:00；耗时：8.44 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <用户Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "2122303527282851841",
    "appointmentNo": "AP8fa5e6e1729a4d149c77a5e560aefbe0",
    "scheduleId": "6",
    "appointmentDate": "2026-11-17",
    "startTime": "08:00:00",
    "endTime": "10:00:00",
    "status": "PENDING_PAYMENT",
    "visitNumber": null,
    "createdAt": "2026-11-16T20:19:26",
    "paymentOrderId": "2122303527312211969",
    "paymentStatus": "PAYING",
    "workflowStatus": "PAYMENT_PROCESSING",
    "amount": 10.0,
    "currency": "CNY",
    "paymentAttemptCount": 1,
    "latestPaymentAttempt": {
      "id": "2122303527505149954",
      "attemptNo": "1722ad35-dd92-4129-a7ab-0548e3724506",
      "channel": "MOCK",
      "status": "PROCESSING",
      "providerOrderNo": "mock-pay-1722ad35-dd92-4129-a7ab-0548e3724506",
      "failureCode": null,
      "createdAt": "2026-11-16T20:19:26",
      "completedAt": null
    },
    "refund": null,
    "paymentExpiresAt": "2026-11-16T20:34:26",
    "closedAt": null,
    "serverTime": "2026-11-16T20:19:26",
    "paymentExpired": false,
    "canPay": false,
    "canCancel": true,
    "patient": {
      "id": "2122303525575770113",
      "name": "接口主流程测试就诊人",
      "documentType": "RESIDENT_ID_CARD",
      "documentNoMasked": "110105********002X",
      "contactPhone": "13800138000",
      "contactEmail": "api-patient@example.com"
    },
    "doctor": {
      "id": "1",
      "doctorNo": "DOC000001",
      "name": "张医生",
      "title": "主任医师",
      "department": {
        "id": "1",
        "name": "内科"
      },
      "hospital": {
        "id": "1",
        "name": "示例市第一人民医院",
        "address": "示例市健康路 1 号"
      }
    },
    "sessionType": "MORNING_FIRST",
    "visit": null
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 保持同一预约和支付单：通过。
- 确认过程未进入关闭或退款终态：通过。

本步结论：**通过**。

</details>

### 18. 轮询预约确认（第2次）

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：轮询预约确认（第2次）。执行身份：用户；分类：主流程。

接口：`GET /api/v1/appointments/2122303527282851841`

请求时间：2026-11-16T20:19:27.149+08:00；耗时：11.36 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <用户Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "2122303527282851841",
    "appointmentNo": "AP8fa5e6e1729a4d149c77a5e560aefbe0",
    "scheduleId": "6",
    "appointmentDate": "2026-11-17",
    "startTime": "08:00:00",
    "endTime": "10:00:00",
    "status": "CONFIRMED",
    "visitNumber": 1,
    "createdAt": "2026-11-16T20:19:26",
    "paymentOrderId": "2122303527312211969",
    "paymentStatus": "PAID",
    "workflowStatus": "CONFIRMED",
    "amount": 10.0,
    "currency": "CNY",
    "paymentAttemptCount": 1,
    "latestPaymentAttempt": {
      "id": "2122303527505149954",
      "attemptNo": "1722ad35-dd92-4129-a7ab-0548e3724506",
      "channel": "MOCK",
      "status": "SUCCEEDED",
      "providerOrderNo": "mock-pay-1722ad35-dd92-4129-a7ab-0548e3724506",
      "failureCode": null,
      "createdAt": "2026-11-16T20:19:26",
      "completedAt": "2026-11-16T20:19:26"
    },
    "refund": null,
    "paymentExpiresAt": "2026-11-16T20:34:26",
    "closedAt": null,
    "serverTime": "2026-11-16T20:19:27",
    "paymentExpired": false,
    "canPay": false,
    "canCancel": false,
    "patient": {
      "id": "2122303525575770113",
      "name": "接口主流程测试就诊人",
      "documentType": "RESIDENT_ID_CARD",
      "documentNoMasked": "110105********002X",
      "contactPhone": "13800138000",
      "contactEmail": "api-patient@example.com"
    },
    "doctor": {
      "id": "1",
      "doctorNo": "DOC000001",
      "name": "张医生",
      "title": "主任医师",
      "department": {
        "id": "1",
        "name": "内科"
      },
      "hospital": {
        "id": "1",
        "name": "示例市第一人民医院",
        "address": "示例市健康路 1 号"
      }
    },
    "sessionType": "MORNING_FIRST",
    "visit": {
      "status": "NOT_CHECKED_IN",
      "checkedInAt": null,
      "startedAt": null,
      "completedAt": null,
      "canCheckIn": false,
      "checkInOpensAt": "2026-11-17T07:30:00",
      "checkInClosesAt": "2026-11-17T10:00:00"
    }
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 保持同一预约和支付单：通过。
- 预约为CONFIRMED且支付为PAID：通过。
- 就诊进度为NOT_CHECKED_IN：通过。
- 已分配就诊序号：通过。

本步结论：**通过**。

</details>

### 19. 调时至预约日07:35，进入签到窗口

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：调时至预约日07:35，进入签到窗口。执行身份：管理员；分类：环境辅助。

接口：`PUT /api/v1/test-control/clock`

请求时间：2026-11-16T20:19:27.168+08:00；耗时：14.13 ms。

请求头：

```http
Accept: application/json
Content-Type: application/json
Authorization: Bearer <管理员Token>
```

请求 JSON：

```json
{
  "instant": "2026-11-17T07:35:00+08:00"
}
```

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "businessTime": "2026-11-17T07:35:00.000010529+08:00",
    "mode": "adjusted"
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 业务时钟为adjusted：通过。

本步结论：**通过**。

</details>

### 20. 用户到院签到

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：用户到院签到。执行身份：用户；分类：主流程。

接口：`POST /api/v1/appointments/2122303527282851841/check-in`

请求时间：2026-11-16T20:19:27.187+08:00；耗时：13.53 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <用户Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "2122303527282851841",
    "appointmentNo": "AP8fa5e6e1729a4d149c77a5e560aefbe0",
    "scheduleId": "6",
    "appointmentDate": "2026-11-17",
    "startTime": "08:00:00",
    "endTime": "10:00:00",
    "status": "CONFIRMED",
    "visitNumber": 1,
    "createdAt": "2026-11-16T20:19:26",
    "paymentOrderId": "2122303527312211969",
    "paymentStatus": "PAID",
    "workflowStatus": "CONFIRMED",
    "amount": 10.0,
    "currency": "CNY",
    "paymentAttemptCount": 1,
    "latestPaymentAttempt": {
      "id": "2122303527505149954",
      "attemptNo": "1722ad35-dd92-4129-a7ab-0548e3724506",
      "channel": "MOCK",
      "status": "SUCCEEDED",
      "providerOrderNo": "mock-pay-1722ad35-dd92-4129-a7ab-0548e3724506",
      "failureCode": null,
      "createdAt": "2026-11-16T20:19:26",
      "completedAt": "2026-11-16T20:19:26"
    },
    "refund": null,
    "paymentExpiresAt": "2026-11-16T20:34:26",
    "closedAt": null,
    "serverTime": "2026-11-17T07:35:00",
    "paymentExpired": false,
    "canPay": false,
    "canCancel": false,
    "patient": {
      "id": "2122303525575770113",
      "name": "接口主流程测试就诊人",
      "documentType": "RESIDENT_ID_CARD",
      "documentNoMasked": "110105********002X",
      "contactPhone": "13800138000",
      "contactEmail": "api-patient@example.com"
    },
    "doctor": {
      "id": "1",
      "doctorNo": "DOC000001",
      "name": "张医生",
      "title": "主任医师",
      "department": {
        "id": "1",
        "name": "内科"
      },
      "hospital": {
        "id": "1",
        "name": "示例市第一人民医院",
        "address": "示例市健康路 1 号"
      }
    },
    "sessionType": "MORNING_FIRST",
    "visit": {
      "status": "WAITING",
      "checkedInAt": "2026-11-17T07:35:00",
      "startedAt": null,
      "completedAt": null,
      "canCheckIn": false,
      "checkInOpensAt": "2026-11-17T07:30:00",
      "checkInClosesAt": "2026-11-17T10:00:00"
    }
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 预约为CONFIRMED且支付为PAID：通过。
- 就诊进度为WAITING：通过。
- 记录签到时间且禁止重复签到：通过。

本步结论：**通过**。

</details>

### 21. 医生登录

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：医生登录。执行身份：医生；分类：主流程。

接口：`POST /api/v1/auth/login`

请求时间：2026-11-16T20:19:27.206+08:00；耗时：71.78 ms。

请求头：

```http
Accept: application/json
Content-Type: application/json
```

请求 JSON：

```json
{
  "email": "test-doctor-001@example.local",
  "password": "<测试密码，已脱敏>"
}
```

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "<医生Token>",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "user": {
      "id": "2",
      "email": "test-doctor-001@example.local",
      "roles": [
        "USER",
        "DOCTOR"
      ]
    }
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 医生账号具备DOCTOR角色：通过。

本步结论：**通过**。

</details>

### 22. 医生查询候诊场次概览

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：医生查询候诊场次概览。执行身份：医生；分类：主流程。

接口：`GET /api/v1/doctor/sessions?date=2026-11-17`

请求时间：2026-11-16T20:19:27.283+08:00；耗时：10.19 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <医生Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "appointmentDate": "2026-11-17",
      "sessionType": "MORNING_FIRST",
      "startTime": "08:00:00",
      "endTime": "10:00:00",
      "confirmedCount": 1,
      "notCheckedInCount": 0,
      "waitingCount": 1,
      "inProgressCount": 0,
      "completedCount": 0
    },
    {
      "appointmentDate": "2026-11-17",
      "sessionType": "MORNING_SECOND",
      "startTime": "10:30:00",
      "endTime": "12:00:00",
      "confirmedCount": 0,
      "notCheckedInCount": 0,
      "waitingCount": 0,
      "inProgressCount": 0,
      "completedCount": 0
    },
    {
      "appointmentDate": "2026-11-17",
      "sessionType": "AFTERNOON_FIRST",
      "startTime": "14:00:00",
      "endTime": "16:00:00",
      "confirmedCount": 0,
      "notCheckedInCount": 0,
      "waitingCount": 0,
      "inProgressCount": 0,
      "completedCount": 0
    },
    {
      "appointmentDate": "2026-11-17",
      "sessionType": "AFTERNOON_SECOND",
      "startTime": "16:30:00",
      "endTime": "18:00:00",
      "confirmedCount": 0,
      "notCheckedInCount": 0,
      "waitingCount": 0,
      "inProgressCount": 0,
      "completedCount": 0
    }
  ]
}
```

检查结果：

- HTTP 200，业务码code=0。
- 医生概览返回当天四场：通过。
- 概览人数分项之和等于已确认人数：通过。
- 本轮预约计入waitingCount：通过。

本步结论：**通过**。

</details>

### 23. 医生查询候诊名单

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：医生查询候诊名单。执行身份：医生；分类：主流程。

接口：`GET /api/v1/doctor/appointments?date=2026-11-17&sessionType=MORNING_FIRST`

请求时间：2026-11-16T20:19:27.298+08:00；耗时：8.29 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <医生Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "appointmentId": "2122303527282851841",
      "visitNumber": 1,
      "patientName": "接口主流程测试就诊人",
      "visitStatus": "WAITING",
      "checkedInAt": "2026-11-17T07:35:00",
      "canStartVisit": true,
      "canCompleteVisit": false
    }
  ]
}
```

检查结果：

- HTTP 200，业务码code=0。
- 名单中本轮预约候诊且允许开始接诊：通过。

本步结论：**通过**。

</details>

### 24. 医生查询预约详情

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：医生查询预约详情。执行身份：医生；分类：主流程。

接口：`GET /api/v1/doctor/appointments/2122303527282851841`

请求时间：2026-11-16T20:19:27.310+08:00；耗时：7.01 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <医生Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "appointmentId": "2122303527282851841",
    "appointmentNo": "AP8fa5e6e1729a4d149c77a5e560aefbe0",
    "visitNumber": 1,
    "appointmentDate": "2026-11-17",
    "sessionType": "MORNING_FIRST",
    "startTime": "08:00:00",
    "endTime": "10:00:00",
    "patient": {
      "id": "2122303525575770113",
      "name": "接口主流程测试就诊人",
      "documentType": "RESIDENT_ID_CARD",
      "documentNoMasked": "110105********002X"
    },
    "doctor": {
      "id": "1",
      "doctorNo": "DOC000001",
      "name": "张医生",
      "title": "主任医师",
      "department": {
        "id": "1",
        "name": "内科"
      },
      "hospital": {
        "id": "1",
        "name": "示例市第一人民医院",
        "address": "示例市健康路 1 号"
      }
    },
    "visit": {
      "status": "WAITING",
      "checkedInAt": "2026-11-17T07:35:00",
      "startedAt": null,
      "completedAt": null
    }
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 医生查看同一预约及候诊状态：通过。

本步结论：**通过**。

</details>

### 25. 调时至预约日08:05，开始门诊接诊

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：调时至预约日08:05，开始门诊接诊。执行身份：管理员；分类：环境辅助。

接口：`PUT /api/v1/test-control/clock`

请求时间：2026-11-16T20:19:27.321+08:00；耗时：3.62 ms。

请求头：

```http
Accept: application/json
Content-Type: application/json
Authorization: Bearer <管理员Token>
```

请求 JSON：

```json
{
  "instant": "2026-11-17T08:05:00+08:00"
}
```

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "businessTime": "2026-11-17T08:05:00.000003168+08:00",
    "mode": "adjusted"
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 业务时钟为adjusted：通过。

本步结论：**通过**。

</details>

### 26. 医生开始接诊

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：医生开始接诊。执行身份：医生；分类：主流程。

接口：`POST /api/v1/doctor/appointments/2122303527282851841/start-visit`

请求时间：2026-11-16T20:19:27.328+08:00；耗时：14.43 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <医生Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "appointmentId": "2122303527282851841",
    "appointmentNo": "AP8fa5e6e1729a4d149c77a5e560aefbe0",
    "visitNumber": 1,
    "appointmentDate": "2026-11-17",
    "sessionType": "MORNING_FIRST",
    "startTime": "08:00:00",
    "endTime": "10:00:00",
    "patient": {
      "id": "2122303525575770113",
      "name": "接口主流程测试就诊人",
      "documentType": "RESIDENT_ID_CARD",
      "documentNoMasked": "110105********002X"
    },
    "doctor": {
      "id": "1",
      "doctorNo": "DOC000001",
      "name": "张医生",
      "title": "主任医师",
      "department": {
        "id": "1",
        "name": "内科"
      },
      "hospital": {
        "id": "1",
        "name": "示例市第一人民医院",
        "address": "示例市健康路 1 号"
      }
    },
    "visit": {
      "status": "IN_PROGRESS",
      "checkedInAt": "2026-11-17T07:35:00",
      "startedAt": "2026-11-17T08:05:00",
      "completedAt": null
    }
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 进入IN_PROGRESS且保留签到时间：通过。

本步结论：**通过**。

</details>

### 27. 用户查询接诊中进度

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：用户查询接诊中进度。执行身份：用户；分类：主流程。

接口：`GET /api/v1/appointments/2122303527282851841`

请求时间：2026-11-16T20:19:27.346+08:00；耗时：5.86 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <用户Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "2122303527282851841",
    "appointmentNo": "AP8fa5e6e1729a4d149c77a5e560aefbe0",
    "scheduleId": "6",
    "appointmentDate": "2026-11-17",
    "startTime": "08:00:00",
    "endTime": "10:00:00",
    "status": "CONFIRMED",
    "visitNumber": 1,
    "createdAt": "2026-11-16T20:19:26",
    "paymentOrderId": "2122303527312211969",
    "paymentStatus": "PAID",
    "workflowStatus": "CONFIRMED",
    "amount": 10.0,
    "currency": "CNY",
    "paymentAttemptCount": 1,
    "latestPaymentAttempt": {
      "id": "2122303527505149954",
      "attemptNo": "1722ad35-dd92-4129-a7ab-0548e3724506",
      "channel": "MOCK",
      "status": "SUCCEEDED",
      "providerOrderNo": "mock-pay-1722ad35-dd92-4129-a7ab-0548e3724506",
      "failureCode": null,
      "createdAt": "2026-11-16T20:19:26",
      "completedAt": "2026-11-16T20:19:26"
    },
    "refund": null,
    "paymentExpiresAt": "2026-11-16T20:34:26",
    "closedAt": null,
    "serverTime": "2026-11-17T08:05:00",
    "paymentExpired": false,
    "canPay": false,
    "canCancel": false,
    "patient": {
      "id": "2122303525575770113",
      "name": "接口主流程测试就诊人",
      "documentType": "RESIDENT_ID_CARD",
      "documentNoMasked": "110105********002X",
      "contactPhone": "13800138000",
      "contactEmail": "api-patient@example.com"
    },
    "doctor": {
      "id": "1",
      "doctorNo": "DOC000001",
      "name": "张医生",
      "title": "主任医师",
      "department": {
        "id": "1",
        "name": "内科"
      },
      "hospital": {
        "id": "1",
        "name": "示例市第一人民医院",
        "address": "示例市健康路 1 号"
      }
    },
    "sessionType": "MORNING_FIRST",
    "visit": {
      "status": "IN_PROGRESS",
      "checkedInAt": "2026-11-17T07:35:00",
      "startedAt": "2026-11-17T08:05:00",
      "completedAt": null,
      "canCheckIn": false,
      "checkInOpensAt": "2026-11-17T07:30:00",
      "checkInClosesAt": "2026-11-17T10:00:00"
    }
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 预约为CONFIRMED且支付为PAID：通过。
- 就诊进度为IN_PROGRESS：通过。
- 用户与医生看到相同开始时间：通过。

本步结论：**通过**。

</details>

### 28. 医生完成接诊

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：医生完成接诊。执行身份：医生；分类：主流程。

接口：`POST /api/v1/doctor/appointments/2122303527282851841/complete-visit`

请求时间：2026-11-16T20:19:27.359+08:00；耗时：12.85 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <医生Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "appointmentId": "2122303527282851841",
    "appointmentNo": "AP8fa5e6e1729a4d149c77a5e560aefbe0",
    "visitNumber": 1,
    "appointmentDate": "2026-11-17",
    "sessionType": "MORNING_FIRST",
    "startTime": "08:00:00",
    "endTime": "10:00:00",
    "patient": {
      "id": "2122303525575770113",
      "name": "接口主流程测试就诊人",
      "documentType": "RESIDENT_ID_CARD",
      "documentNoMasked": "110105********002X"
    },
    "doctor": {
      "id": "1",
      "doctorNo": "DOC000001",
      "name": "张医生",
      "title": "主任医师",
      "department": {
        "id": "1",
        "name": "内科"
      },
      "hospital": {
        "id": "1",
        "name": "示例市第一人民医院",
        "address": "示例市健康路 1 号"
      }
    },
    "visit": {
      "status": "COMPLETED",
      "checkedInAt": "2026-11-17T07:35:00",
      "startedAt": "2026-11-17T08:05:00",
      "completedAt": "2026-11-17T08:05:00"
    }
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 进入COMPLETED且记录完成时间：通过。

本步结论：**通过**。

</details>

### 29. 用户查询就诊完成结果

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：用户查询就诊完成结果。执行身份：用户；分类：主流程。

接口：`GET /api/v1/appointments/2122303527282851841`

请求时间：2026-11-16T20:19:27.376+08:00；耗时：5.83 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <用户Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "2122303527282851841",
    "appointmentNo": "AP8fa5e6e1729a4d149c77a5e560aefbe0",
    "scheduleId": "6",
    "appointmentDate": "2026-11-17",
    "startTime": "08:00:00",
    "endTime": "10:00:00",
    "status": "CONFIRMED",
    "visitNumber": 1,
    "createdAt": "2026-11-16T20:19:26",
    "paymentOrderId": "2122303527312211969",
    "paymentStatus": "PAID",
    "workflowStatus": "CONFIRMED",
    "amount": 10.0,
    "currency": "CNY",
    "paymentAttemptCount": 1,
    "latestPaymentAttempt": {
      "id": "2122303527505149954",
      "attemptNo": "1722ad35-dd92-4129-a7ab-0548e3724506",
      "channel": "MOCK",
      "status": "SUCCEEDED",
      "providerOrderNo": "mock-pay-1722ad35-dd92-4129-a7ab-0548e3724506",
      "failureCode": null,
      "createdAt": "2026-11-16T20:19:26",
      "completedAt": "2026-11-16T20:19:26"
    },
    "refund": null,
    "paymentExpiresAt": "2026-11-16T20:34:26",
    "closedAt": null,
    "serverTime": "2026-11-17T08:05:00",
    "paymentExpired": false,
    "canPay": false,
    "canCancel": false,
    "patient": {
      "id": "2122303525575770113",
      "name": "接口主流程测试就诊人",
      "documentType": "RESIDENT_ID_CARD",
      "documentNoMasked": "110105********002X",
      "contactPhone": "13800138000",
      "contactEmail": "api-patient@example.com"
    },
    "doctor": {
      "id": "1",
      "doctorNo": "DOC000001",
      "name": "张医生",
      "title": "主任医师",
      "department": {
        "id": "1",
        "name": "内科"
      },
      "hospital": {
        "id": "1",
        "name": "示例市第一人民医院",
        "address": "示例市健康路 1 号"
      }
    },
    "sessionType": "MORNING_FIRST",
    "visit": {
      "status": "COMPLETED",
      "checkedInAt": "2026-11-17T07:35:00",
      "startedAt": "2026-11-17T08:05:00",
      "completedAt": "2026-11-17T08:05:00",
      "canCheckIn": false,
      "checkInOpensAt": "2026-11-17T07:30:00",
      "checkInClosesAt": "2026-11-17T10:00:00"
    }
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 预约为CONFIRMED且支付为PAID：通过。
- 就诊进度为COMPLETED：通过。
- 用户与医生看到相同就诊时间：通过。
- 费用、就诊序号及就诊人快照保持一致：通过。

本步结论：**通过**。

</details>

### 30. 用户查询最终预约列表

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：用户查询最终预约列表。执行身份：用户；分类：主流程。

接口：`GET /api/v1/appointments/my?order=DESC`

请求时间：2026-11-16T20:19:27.391+08:00；耗时：6.83 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <用户Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": "2122303527282851841",
      "appointmentNo": "AP8fa5e6e1729a4d149c77a5e560aefbe0",
      "scheduleId": "6",
      "appointmentDate": "2026-11-17",
      "startTime": "08:00:00",
      "endTime": "10:00:00",
      "status": "CONFIRMED",
      "visitNumber": 1,
      "createdAt": "2026-11-16T20:19:26",
      "paymentOrderId": "2122303527312211969",
      "paymentStatus": "PAID",
      "workflowStatus": "CONFIRMED",
      "amount": 10.0,
      "currency": "CNY",
      "paymentAttemptCount": 1,
      "latestPaymentAttempt": {
        "id": "2122303527505149954",
        "attemptNo": "1722ad35-dd92-4129-a7ab-0548e3724506",
        "channel": "MOCK",
        "status": "SUCCEEDED",
        "providerOrderNo": "mock-pay-1722ad35-dd92-4129-a7ab-0548e3724506",
        "failureCode": null,
        "createdAt": "2026-11-16T20:19:26",
        "completedAt": "2026-11-16T20:19:26"
      },
      "refund": null,
      "paymentExpiresAt": "2026-11-16T20:34:26",
      "closedAt": null,
      "serverTime": "2026-11-17T08:05:00",
      "paymentExpired": false,
      "canPay": false,
      "canCancel": false,
      "patient": {
        "id": "2122303525575770113",
        "name": "接口主流程测试就诊人",
        "documentType": "RESIDENT_ID_CARD",
        "documentNoMasked": "110105********002X",
        "contactPhone": "13800138000",
        "contactEmail": "api-patient@example.com"
      },
      "doctor": {
        "id": "1",
        "doctorNo": "DOC000001",
        "name": "张医生",
        "title": "主任医师",
        "department": {
          "id": "1",
          "name": "内科"
        },
        "hospital": {
          "id": "1",
          "name": "示例市第一人民医院",
          "address": "示例市健康路 1 号"
        }
      },
      "sessionType": "MORNING_FIRST",
      "visit": {
        "status": "COMPLETED",
        "checkedInAt": "2026-11-17T07:35:00",
        "startedAt": "2026-11-17T08:05:00",
        "completedAt": "2026-11-17T08:05:00",
        "canCheckIn": false,
        "checkInOpensAt": "2026-11-17T07:30:00",
        "checkInClosesAt": "2026-11-17T10:00:00"
      }
    }
  ]
}
```

检查结果：

- HTTP 200，业务码code=0。
- 用户列表与详情状态及就诊时间一致：通过。

本步结论：**通过**。

</details>

### 31. 医生查询最终预约详情

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：医生查询最终预约详情。执行身份：医生；分类：主流程。

接口：`GET /api/v1/doctor/appointments/2122303527282851841`

请求时间：2026-11-16T20:19:27.402+08:00；耗时：4.97 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <医生Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "appointmentId": "2122303527282851841",
    "appointmentNo": "AP8fa5e6e1729a4d149c77a5e560aefbe0",
    "visitNumber": 1,
    "appointmentDate": "2026-11-17",
    "sessionType": "MORNING_FIRST",
    "startTime": "08:00:00",
    "endTime": "10:00:00",
    "patient": {
      "id": "2122303525575770113",
      "name": "接口主流程测试就诊人",
      "documentType": "RESIDENT_ID_CARD",
      "documentNoMasked": "110105********002X"
    },
    "doctor": {
      "id": "1",
      "doctorNo": "DOC000001",
      "name": "张医生",
      "title": "主任医师",
      "department": {
        "id": "1",
        "name": "内科"
      },
      "hospital": {
        "id": "1",
        "name": "示例市第一人民医院",
        "address": "示例市健康路 1 号"
      }
    },
    "visit": {
      "status": "COMPLETED",
      "checkedInAt": "2026-11-17T07:35:00",
      "startedAt": "2026-11-17T08:05:00",
      "completedAt": "2026-11-17T08:05:00"
    }
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 医生最终详情与完成响应一致：通过。

本步结论：**通过**。

</details>

### 32. 医生查询最终名单

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：医生查询最终名单。执行身份：医生；分类：主流程。

接口：`GET /api/v1/doctor/appointments?date=2026-11-17&sessionType=MORNING_FIRST`

请求时间：2026-11-16T20:19:27.411+08:00；耗时：5.06 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <医生Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "appointmentId": "2122303527282851841",
      "visitNumber": 1,
      "patientName": "接口主流程测试就诊人",
      "visitStatus": "COMPLETED",
      "checkedInAt": "2026-11-17T07:35:00",
      "canStartVisit": false,
      "canCompleteVisit": false
    }
  ]
}
```

检查结果：

- HTTP 200，业务码code=0。
- 医生名单标记COMPLETED且关闭操作按钮：通过。

本步结论：**通过**。

</details>

### 33. 医生查询最终场次统计

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：医生查询最终场次统计。执行身份：医生；分类：主流程。

接口：`GET /api/v1/doctor/sessions?date=2026-11-17`

请求时间：2026-11-16T20:19:27.422+08:00；耗时：5.38 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <医生Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "appointmentDate": "2026-11-17",
      "sessionType": "MORNING_FIRST",
      "startTime": "08:00:00",
      "endTime": "10:00:00",
      "confirmedCount": 1,
      "notCheckedInCount": 0,
      "waitingCount": 0,
      "inProgressCount": 0,
      "completedCount": 1
    },
    {
      "appointmentDate": "2026-11-17",
      "sessionType": "MORNING_SECOND",
      "startTime": "10:30:00",
      "endTime": "12:00:00",
      "confirmedCount": 0,
      "notCheckedInCount": 0,
      "waitingCount": 0,
      "inProgressCount": 0,
      "completedCount": 0
    },
    {
      "appointmentDate": "2026-11-17",
      "sessionType": "AFTERNOON_FIRST",
      "startTime": "14:00:00",
      "endTime": "16:00:00",
      "confirmedCount": 0,
      "notCheckedInCount": 0,
      "waitingCount": 0,
      "inProgressCount": 0,
      "completedCount": 0
    },
    {
      "appointmentDate": "2026-11-17",
      "sessionType": "AFTERNOON_SECOND",
      "startTime": "16:30:00",
      "endTime": "18:00:00",
      "confirmedCount": 0,
      "notCheckedInCount": 0,
      "waitingCount": 0,
      "inProgressCount": 0,
      "completedCount": 0
    }
  ]
}
```

检查结果：

- HTTP 200，业务码code=0。
- 医生概览返回当天四场：通过。
- 概览人数分项之和等于已确认人数：通过。
- 本轮预约计入completedCount：通过。

本步结论：**通过**。

</details>

### 34. 恢复test原有实时业务时钟

<details>
<summary>查看该步的请求、响应与断言</summary>

业务：恢复test原有实时业务时钟。执行身份：管理员；分类：环境恢复。

接口：`DELETE /api/v1/test-control/clock`

请求时间：2026-11-16T20:19:27.435+08:00；耗时：3.42 ms。

请求头：

```http
Accept: application/json
Authorization: Bearer <管理员Token>
```

请求体：无。

HTTP 响应码：`200 OK`；预期：`200`。

响应 Content-Type：`application/json`。

响应 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "businessTime": "2026-11-16T20:19:27.438095508+08:00",
    "mode": "realtime"
  }
}
```

检查结果：

- HTTP 200，业务码code=0。
- 业务时钟恢复realtime：通过。

本步结论：**通过**。

</details>

## 环境恢复与数据保留

- test业务时钟已恢复测试前的`realtime`模式，恢复请求及响应见第34步。
- 本轮启动的test后端及MySQL、Redis、RabbitMQ容器均已停止，恢复测试前的关闭状态；数据卷和已有配置保留。
- 本轮新建用户、就诊人和已完成预约保留在test数据库中，可按上表ID追溯；未重置数据库，未修改dev/demo业务数据。
- 测试只使用模拟支付渠道，本报告不代表真实资金支付、异常分支或浏览器端到端测试通过。
