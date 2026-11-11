# 02 三天场次查询与响应

Type: feature
Triage: ready-for-agent
Status: resolved
Blocked by: 01

设计依据：[spec 第 4.2、5.1、6 节](../spec.md)。执行位置：[map](../map.md)。场景索引：[scenarios](../scenarios.md)。

## 目标与前置条件

01 已 resolved 后，使用共享规则将场次目录改为今天至后天的十二项，明确每项的下单截止和可预约状态。查询仍不生成场次或占库存。

## 具体文件

- 新增 `src/main/java/com/qsx/appoint/catalog/model/ScheduleAvailability.java`。
- 修改 `src/main/java/com/qsx/appoint/catalog/service/impl/CatalogServiceImpl.java`。
- 修改 `src/main/java/com/qsx/appoint/catalog/dto/ScheduleResponse.java`。
- 更新 `src/main/java/com/qsx/appoint/catalog/service/CatalogService.java` 的查询契约说明。
- 复用 `src/main/java/com/qsx/appoint/catalog/controller/CatalogController.java`，只有确有必要时更新注释，不增加新方法。
- 更新 `src/test/java/com/qsx/appoint/catalog/SessionCatalogHttpTest.java`、`CatalogManagementIntegrationTest.java` 的查询断言。
- 更新 `src/test/java/com/qsx/appoint/PatientJourneyHttpMySqlIT.java` 中场次数量 28 的断言；保留其原来选择后天创建的主线。

## API 契约

`GET /api/v1/doctors/{doctorId}/schedules` 保持登录要求、`Result<List<ScheduleResponse>>` 和 data 数组。保留原字段，追加：

| 字段 | Java 类型 | 语义 |
| --- | --- | --- |
| bookingClosesAt | LocalDateTime | 该场次停止创建新订单的时刻 |
| availability | ScheduleAvailability | AVAILABLE / FULL / CUTOFF_REACHED / STARTED |
| serverTime | LocalDateTime | 一次查询的统一时间，十二项完全一致 |

`bookable` 严格等于 `availability == AVAILABLE`。不增加独立日期 GET、日期专用下单接口、分页或分组包装；由调用方按 appointmentDate 分组三个入口。

## 查询流程

1. 沿用医生存在性查询和原返回契约。
2. 采样一次上海秒精度 `serverTime`，通过 01 获取三天日期。
3. 查询医生三天内已经存在的四类场次，按现有逻辑读取统一设置。
4. 按日期和 `AppointmentSession.values()` 组装十二项：已存在场次读自身容量/费用快照，未生成场次只计算展示值。
5. 共享规则计算下单截止；依次判断已开始、已截止、已约满、可预约。
6. 返回查询快照。不能逐行读取时钟，也不能通过定时任务预生成十二项。

## 场景与验收条件

主责 S01、S02、S11、S16；与 03 联合覆盖 S03 的查询部分。

- [x] 三天十二项，起止日期、四场次、排序正确。
- [x] 10:05 今天第一场 STARTED，其余有余号的场次 AVAILABLE。
- [x] 10:15 今天第二场 CUTOFF_REACHED；不因它仍有库存显示可预约。
- [x] 未到截止但无余号为 FULL；已开始/已截止优先于满额展示。
- [x] 同次所有 serverTime 一致，零点前后查询各自内部日期不混杂。
- [x] 原 id 隐藏、Long ID 字符串、费用序列化等契约继续保持。
- [x] GET 前后 schedules 行数及库存不变；统一设置与已有场次快照行为继续成立。
- [x] DTO 构造调用点、所有相关 28 项查询断言已同步，不把旧未来第一场的 bookable=true 直接套给今天第一场。

## 验证方式

```bash
./mvnw -Dtest=SessionCatalogHttpTest,CatalogManagementIntegrationTest,ModuleDependencyTest test
```

PatientJourneyHttpMySqlIT 的真实链路在 04/05 统一执行；此处数量断言随查询契约一起修改并在 Comments 留下交接说明。

本任务不改变实际占号或支付入口；查询支持当天是目录能力完成，不代表整个功能已经交付。03 必须继续同步写入资格。

## Comments

- 2026-11-11：完成任务定义；尚未认领，依赖 01 未 resolved，暂无实现与验证结果。

- 2026-11-11：01 已验收，开始实施十二项目录、展示状态与查询字段，尚未验收。

- 2026-11-11：十二项查询与四状态已实现；聚焦命令在允许 JVM 附加的环境执行，32 项通过、零失败/错误/跳过，日志 `/tmp/three-day-02.log`。首次沙箱内 Mockito 附加受限的运行不计为验收。02 resolved。
