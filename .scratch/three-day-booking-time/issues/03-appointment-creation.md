# 03 预约创建、准入时间与条件占号

Type: feature
Triage: ready-for-agent
Status: resolved
Blocked by: 01, 02

设计依据：[spec 第 5.2、7、8、9 节](../spec.md)。执行位置：[map](../map.md)。场景索引：[scenarios](../scenarios.md)。

## 目标与前置条件

01、02 均 resolved 后，将真实创建能力同步到三天窗口和场次开始前十五分钟；锁后最终采样准入时间，创建预约及支付两份一致的时间快照。

## 具体文件

- `src/main/java/com/qsx/appoint/appointment/application/AppointmentHoldServiceImpl.java`：最终时间判断、条件占号、快照时间和支付截止计算。
- `src/main/java/com/qsx/appoint/appointment/application/CreateAppointmentHoldCommand.java`：移除外层 createdAt 参数。
- `src/main/java/com/qsx/appoint/appointment/application/AppointmentHold.java`、`AppointmentHoldService.java`：更新 Interface 说明，返回结构保持。
- `src/main/java/com/qsx/appoint/coordination/application/BookingCreationTransaction.java`：分开请求登记与预约准入时间，支付创建复制 Hold。
- `src/main/java/com/qsx/appoint/catalog/mapper/ScheduleMapper.java`：替换 holdOneFutureSession。
- `src/main/java/com/qsx/appoint/appointment/dto/CreateAppointmentRequest.java`、`CreateAppointmentResponse.java`：更新日期、时间语义注释，HTTP 字段结构保持。
- 更新 `src/test/java/com/qsx/appoint/appointment/OnDemandBookingHttpTest.java`、`BookingMidnightIntegrationTest.java`、`AppointmentLifecycleMySqlIT.java`。
- 复核所有真实调用新创建流程的测试夹具，特别是 `ApiFlowIntegrationTest`、`AppointmentIdempotencyIntegrationTest`、`AppointmentLifecycleIntegrationTest`、`ScheduleInventoryIntegrationTest`；只调整受三天窗口影响的创建数据，不机械修改直接保存的历史记录。

## 创建流程与时间职责

保持 `AppointmentController → BookingCoordinator → BookingCreationTransaction`：幂等登记、预约暂占、支付建单与创建回执仍在同一事务中完成，两个写 Interface 继续 MANDATORY。

外层先以 requestRegisteredAt 登记创建请求；暂占命令只传账号、医生、日期、场次和就诊人。暂占实现先做资格初验，随后按原顺序锁统一设置、生成/锁定场次、锁就诊人并读取资料。在条件占号前采样 acceptedAt：

1. 使用 01 再检查三天日期与下单截止。
2. 使用同一次采样推导 SQL 的日期范围和最早场次开始阈值。
3. 占号后用 acceptedAt 保存预约 createdAt/updatedAt 和有效占用创建时间。
4. 计算 `min(acceptedAt + paymentTimeout, sessionStartsAt)` 保存到预约。
5. 返回 Hold，其 createdAt 和 paymentExpiresAt 原样传给支付建单。
6. 创建结果组装时重新采样 serverTime，不能继续返回外层登记时刻。

这里的准入时间不是客户端点击或事务提交完成时间。后续响应倒计时仍以返回的绝对截止为准。

## 条件占号 SQL

按 spec 将方法替换为 `holdOneBookableSession`，用 `@Param` 明确传入 scheduleId、firstBookableDate、lastBookableDate、earliestStartDate、earliestStartTime。

保留四场次类型、OPEN 和可用库存条件，时间条件为：

```sql
AND appointment_date BETWEEN #{firstBookableDate} AND #{lastBookableDate}
AND (
    appointment_date > #{earliestStartDate}
    OR (appointment_date = #{earliestStartDate}
        AND start_time > #{earliestStartTime})
)
```

earliestStartAt 由 acceptedAt 加共享的十五分钟提前量产生，拆成日期和时间绑定。不要保留旧 `appointment_date > today` 条件，也不要用宿主或数据库当前日期代替业务 Clock。

不增加下单截止数据库列或新迁移；截止由场次日期、开始时间和集中规则计算。

## 场景与验收条件

主责 S03、S04、S05、S06、S10、S15；为 04 提供 S07、S09、S13 的创建前提。

- [x] 今天、明天、后天满足场次资格的新订单都可创建。
- [x] 13:40 创建今天 14:00 场，支付截止 13:55，预约与支付创建时间及截止完全相同。
- [x] 恰好下单截止不能创建；最终 Java 资格和 SQL 条件一致。
- [x] 锁前与锁后时钟不同时，创建时间使用最终 acceptedAt，不复用请求登记时间。
- [x] 请求跨零点后，原明天变今天，仍在截止前时可以创建。
- [x] 原已成功幂等请求继续返回原预约；创建请求字段和首次 201/重放 200 契约保持。
- [x] 当天正常创建库存由 available 转 held，费用/就诊人快照继续按现有规则保存。
- [x] 当前夹具固定 11/05 却创建 11/08 的用例调整为窗口内日期；D+7 夹具仅在调用新创建流程时调整。
- [x] 没有引用 payment 的 Mapper/Entity，协调层仍仅通过稳定 Interface 协作。

## 验证方式

```bash
./mvnw -Dtest=BookingTimeRulesTest,OnDemandBookingHttpTest,BookingMidnightIntegrationTest,AppointmentIdempotencyIntegrationTest,ScheduleInventoryIntegrationTest,ModuleDependencyTest test
./mvnw test
./mvnw -Pmysql-it -Dit.test=AppointmentLifecycleMySqlIT verify
```

命令串行执行。常规测试发现窗口前提不匹配时，在本任务追踪修正实际创建夹具，不能删除原有测试目的。MySQL 验证同日条件占号与正常库存变化；没有 Docker/镜像等执行条件时如实记录未验证，不宣称通过。

## Comments

- 2026-11-11：完成任务定义；尚未认领，依赖 01、02 未 resolved，暂无实现与测试结果。

- 2026-11-11：01、02 已验收，开始创建事务、准入时间和条件占号改造。

- 2026-11-11：锁后准入、三天/十五分钟 SQL 和双时间快照已实现；常规 `./mvnw -o test` 258 项通过（`/tmp/three-day-03-all.log`）；真实 MySQL 聚焦命令 `./mvnw -o -Pmysql-it -Dtest=BookingTimeRulesTest -Dit.test=AppointmentLifecycleMySqlIT verify` 中规则 9 项、MySQL 13 项通过，均零失败/错误/跳过（`/tmp/three-day-03-mysql.log`）。新增同日 SQL 截止实测；旧夹具改用窗口内日期及独立医生。03 resolved。
