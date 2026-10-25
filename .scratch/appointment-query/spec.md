# 阶段 2.1：预约查询补齐支付信息

Status: ready-for-agent

## 1. 背景

当前项目已经实现最小预约闭环：用户注册与登录、查询医院资源、提交预约、创建支付单，以及模拟支付成功。

创建预约时，`POST /api/v1/appointments` 会返回 `appointmentOrderId`、`paymentOrderId`、预约单状态和支付单状态。后续查询预约的两个现有接口为：

- `GET /api/v1/appointments/{id}`
- `GET /api/v1/appointments/my`

但是，这两个查询当前只返回预约单自身的 `id`、`appointmentNo`、`scheduleId`、`status` 和 `createdAt`，不返回关联支付单。客户端一旦没有保存创建预约时的首次响应，就无法重新取得 `paymentOrderId`，因而无法继续调用当前的模拟支付接口。

阶段 2.1 只补齐这个读取断点，不扩展预约状态机或支付能力。

## 2. 目标

用户创建预约后，可以随时通过预约详情或“我的预约”列表重新取得：

- 关联支付单 ID；
- 当前预约单状态；
- 当前支付单状态。

查询结果必须反映数据库中的最新状态。因此，模拟支付成功后再次查询，应从 `PENDING_PAYMENT + UNPAID` 变为 `CONFIRMED + PAID`。

## 3. 范围

### 3.1 本阶段包含

1. 扩展现有 `AppointmentResponse`，新增两个非空字段：
   - `paymentOrderId: Long`
   - `paymentStatus: String`
2. `GET /api/v1/appointments/{id}` 返回上述字段。
3. `GET /api/v1/appointments/my` 中的每一项返回相同的 `AppointmentResponse` 结构。
4. 查询时从数据库关联读取支付单，不保存冗余支付状态，不使用缓存。
5. 检查预约单与支付单的数据完整性和用户归属一致性。
6. 增加自动化测试、HTTP 调试示例和 README 接口说明。

### 3.2 本阶段不包含

- 医院、科室、医生名称和就诊时间等展示信息；这些内容留到阶段 2.2。
- 新增独立的支付单查询接口。
- 修改预约创建、模拟支付或其他写入流程。
- 新增或修改业务状态。
- 把字符串状态重构为 Java enum。
- 预约取消、支付超时、改签、退款或真实支付。
- 分页、筛选或可配置排序。
- 前端页面和交互设计。
- Redis、RabbitMQ 或其他基础设施接入。
- 数据库迁移或表结构修改。
- 在查询过程中自动修复异常数据。

## 4. 接口契约

### 4.1 兼容原则

- 保留现有接口路径。
- 保留 `AppointmentResponse` 的全部已有字段及含义。
- 现有 `status` 字段继续表示预约单状态，不重命名。
- 只在顶层新增 `paymentOrderId` 和 `paymentStatus`。
- 本阶段不返回 `paymentNo`。
- 详情与列表项使用完全相同的响应结构。

### 4.2 待支付预约详情

请求：

```http
GET /api/v1/appointments/1
Authorization: Bearer <accessToken>
```

成功响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "appointmentNo": "AP0123456789abcdef",
    "scheduleId": 1,
    "status": "PENDING_PAYMENT",
    "createdAt": "2026-10-25T20:00:00",
    "paymentOrderId": 1,
    "paymentStatus": "UNPAID"
  }
}
```

### 4.3 支付成功后的预约详情

对关联支付单完成模拟支付后，再次查询同一预约，成功响应中的状态必须为：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "appointmentNo": "AP0123456789abcdef",
    "scheduleId": 1,
    "status": "CONFIRMED",
    "createdAt": "2026-10-25T20:00:00",
    "paymentOrderId": 1,
    "paymentStatus": "PAID"
  }
}
```

### 4.4 “我的预约”列表

请求：

```http
GET /api/v1/appointments/my
Authorization: Bearer <accessToken>
```

成功响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "appointmentNo": "AP0123456789abcdef",
      "scheduleId": 1,
      "status": "PENDING_PAYMENT",
      "createdAt": "2026-10-25T20:00:00",
      "paymentOrderId": 1,
      "paymentStatus": "UNPAID"
    }
  ]
}
```

列表继续按预约单 ID 倒序排列。

用户没有预约时，接口返回 HTTP 200 和空数组：

```json
{
  "code": 0,
  "message": "success",
  "data": []
}
```

## 5. 权限与错误语义

### 5.1 预约不存在

- 行为保持不变。
- 返回 HTTP 404。
- 错误码为 `RESOURCE_NOT_FOUND`（`4001`）。

### 5.2 查询他人的预约

- 行为保持不变。
- 返回 HTTP 403。
- 错误码为 `FORBIDDEN`（`2002`）。

详情查询不能只在 SQL 中使用当前用户 ID 过滤预约，否则会把“预约不存在”和“预约存在但属于其他用户”合并成同一种结果。

### 5.3 关联支付单缺失

正常创建预约的事务会同时创建一张支付单，因此“预约存在但支付单不存在”属于数据完整性异常。

遇到该情况时必须：

1. 记录包含预约单 ID 的错误日志，但不得记录 Token、密码等敏感信息；
2. 返回 HTTP 500；
3. 返回现有 `INTERNAL_ERROR`（`1002`）；
4. 不得返回 `paymentOrderId: null`；
5. 不得自动补建或修改支付单。

### 5.4 用户归属不一致

支付单的 `user_id` 必须与预约单的 `user_id` 一致。当前数据库没有跨表约束直接保证这一点，但正常创建流程会同时写入相同用户 ID。

如果查询发现二者不一致，必须按数据完整性异常处理：记录错误日志并返回 HTTP 500 与 `INTERNAL_ERROR`，不得向用户返回该支付单的信息，也不得自动修复数据。

### 5.5 列表中的异常数据

如果“我的预约”列表中任意一条预约存在支付单缺失或用户归属不一致，整个请求返回 `INTERNAL_ERROR`。不得跳过异常预约，也不得返回部分列表。

## 6. 推荐实现设计

本节约束重要的数据访问特性，但内部类名和方法名可以采用等价且更简单的设计。

### 6.1 查询投影

建议增加预约查询专用的内部投影，至少承载：

- 预约单 ID、预约编号、用户 ID、号源 ID、预约状态、创建时间；
- 支付单 ID、支付单用户 ID、支付状态。

查询投影只服务于预约读取，不修改 `AppointmentOrder` 或 `PaymentOrder` 实体，也不作为 HTTP 响应直接暴露。

### 6.2 数据访问

建议提供两个查询方法：

1. 按预约单 ID 查询一条预约及关联支付单；
2. 按当前用户 ID 查询其全部预约及关联支付单，并按预约单 ID 倒序。

两个查询都应使用 `appointment_orders` 到 `payment_orders` 的单次 `LEFT JOIN`：

- 单次关联查询避免列表产生 N+1 查询；
- `LEFT JOIN` 能保留支付单缺失的预约，使业务层可以识别并报告数据完整性异常；
- 不应使用会静默丢弃异常预约的 `INNER JOIN`。

`payment_orders.appointment_order_id` 已有唯一约束，因此正常数据下每张预约单至多得到一条支付单记录。

### 6.3 业务层校验顺序

预约详情建议按以下顺序处理：

1. 查询预约及其关联支付单；
2. 预约不存在时返回 `RESOURCE_NOT_FOUND`；
3. 校验预约是否属于当前用户，不属于时返回 `FORBIDDEN`；
4. 校验支付单存在；
5. 校验支付单用户 ID 与预约单用户 ID 一致；
6. 转换为 `AppointmentResponse`。

“我的预约”列表建议对每条查询结果执行步骤 4、5、6；任一条校验失败，整个列表请求失败。

### 6.4 保持写入模块不变

以下实现不应因本需求发生行为变化：

- `AppointmentServiceImpl#create`；
- `PaymentServiceImpl#simulateSuccess`；
- 号源 `AVAILABLE -> HELD -> BOOKED` 的状态推进；
- 预约单 `PENDING_PAYMENT -> CONFIRMED` 的状态推进；
- 支付单 `UNPAID -> PAID` 的状态推进。

## 7. 自动化验收

自动化测试是完成本阶段的必过门槛，手工请求不能替代自动化测试。

异常数据只能在独立的回滚事务中构造。每个预约查询测试必须创建自己的可用号源和用户，不得依赖或污染共享演示数据，以避免测试执行顺序影响结果。

至少覆盖以下场景：

### 7.1 待支付查询

1. 注册并登录用户；
2. 创建预约；
3. 查询预约详情；
4. 查询“我的预约”；
5. 断言两处均返回创建时生成的 `paymentOrderId`；
6. 断言预约状态为 `PENDING_PAYMENT`；
7. 断言支付状态为 `UNPAID`。

### 7.2 支付后的实时状态

1. 对创建的支付单执行模拟支付成功；
2. 再次查询预约详情和“我的预约”；
3. 断言预约状态为 `CONFIRMED`；
4. 断言支付状态为 `PAID`；
5. 断言支付单 ID 未发生变化。

### 7.3 归属权限

1. 用户 A 创建预约；
2. 用户 B 查询该预约详情；
3. 断言返回 HTTP 403 和错误码 `2002`。

### 7.4 支付单缺失

1. 在测试数据中构造“预约存在但支付单缺失”；
2. 分别调用预约详情和“我的预约”；
3. 断言返回 HTTP 500 和错误码 `1002`；
4. 断言没有返回带空支付字段的成功响应；
5. 断言查询没有自动创建支付单。

### 7.5 用户归属不一致

1. 在测试数据中构造支付单与预约单 `user_id` 不一致；
2. 分别调用预约详情和“我的预约”；
3. 断言返回 HTTP 500 和错误码 `1002`；
4. 断言响应不泄露异常支付单信息；
5. 断言查询没有修改任何业务数据。

### 7.6 空列表

1. 使用没有预约的已登录用户调用“我的预约”；
2. 断言返回 HTTP 200、业务码 `0` 和 `data: []`。

### 7.7 回归测试

运行：

```bash
./mvnw test
```

现有注册、登录、认证、预约创建、支付幂等和号源防重复占用测试必须继续通过。

## 8. 手工验收

实现完成后，在 `http/appointment.http` 中按以下顺序复核：

1. 注册并登录用户；
2. 查询可用号源；
3. 创建预约并记录 `appointmentOrderId`；
4. 查询该预约详情，确认存在 `paymentOrderId` 和 `paymentStatus: UNPAID`；
5. 查询“我的预约”，确认相同预约包含一致的支付信息；
6. 使用返回的 `paymentOrderId` 模拟支付成功；
7. 再次查询详情和列表，确认状态变为 `CONFIRMED + PAID`。

`http/appointment.http` 是人工复核入口，不替代自动化测试。

## 9. 预计影响文件

实现阶段预计涉及以下文件；具体内部类名可以调整：

- `src/main/java/com/qsx/appoint/appointment/dto/AppointmentResponse.java`
- `src/main/java/com/qsx/appoint/appointment/mapper/AppointmentOrderMapper.java`，或新增预约查询专用 Mapper
- 预约查询专用内部投影文件
- `src/main/java/com/qsx/appoint/appointment/service/impl/AppointmentServiceImpl.java`
- `src/test/java/com/qsx/appoint/ApiFlowIntegrationTest.java`，或新增预约查询集成测试
- `http/appointment.http`
- `README.md`

预期不涉及：

- Flyway 迁移文件；
- `AppointmentOrder` 和 `PaymentOrder` 表结构；
- `AppointmentService` 对外方法；
- `PaymentServiceImpl`；
- Spring Security 配置；
- Redis 或 RabbitMQ 配置。

## 10. 风险与控制

| 风险 | 控制方式 |
| --- | --- |
| 列表逐条查询支付单导致 N+1 | 使用单次 `LEFT JOIN` 查询 |
| `INNER JOIN` 隐藏缺失支付单的预约 | 明确要求 `LEFT JOIN` 并在业务层校验 |
| SQL 直接按当前用户过滤详情，改变 403/404 语义 | 详情投影保留预约所属用户，由业务层判断 |
| 支付单异常归属导致信息泄露 | 返回响应前校验两个 `user_id` 一致 |
| 新字段来自旧快照，支付后状态不更新 | 每次请求直接读取当前数据库状态 |
| 查询接口意外产生业务写入 | 异常只记录和返回，不自动修复 |
| 顺手扩展状态机导致改动扩大 | 严格遵守非目标列表 |

## 11. Definition of Done

只有同时满足以下条件，阶段 2.1 才算完成：

- 两个现有预约查询接口均返回非空的 `paymentOrderId` 和 `paymentStatus`；
- 已有响应字段、接口路径、权限语义、列表顺序和空列表语义保持兼容；
- 查询反映模拟支付前后的最新状态；
- 支付单缺失或用户归属不一致时安全失败，不返回部分或伪造成功结果；
- 查询路径不存在 N+1 支付单查询；
- 不修改表结构、状态机或预约与支付写入逻辑；
- 本规格要求的自动化场景均有测试覆盖；
- `./mvnw test` 全部通过；
- `http/appointment.http` 和 `README.md` 已同步更新；
- 没有引入与本阶段无关的重构或基础设施。
