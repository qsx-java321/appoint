# 01 共享预约时间规则

Type: feature
Triage: ready-for-agent
Status: resolved
Blocked by: none

设计依据：[spec 第 2、4.1 节](../spec.md)。执行位置：[map](../map.md)。场景索引：[scenarios](../scenarios.md)。

## 目标与交付

新增纯规则类，集中计算三天日期窗口、场次下单截止、创建时间资格与支付截止，为查询和暂占提供同一套判断。

## 具体文件

以下路径均相对项目根目录：

- 新增 `src/main/java/com/qsx/appoint/catalog/model/BookingTimeRules.java`。
- 新增 `src/test/java/com/qsx/appoint/catalog/model/BookingTimeRulesTest.java`。
- 只读参照 `catalog/model/AppointmentSession.java`、`appointment/config/AppointmentProperties.java` 与 `AppointmentTimeConfig.java`；不移动这些类型。

## Interface 与规则

按 spec 实现以下静态方法：

```java
List<LocalDate> selectableDates(LocalDateTime serverTime);
LocalDateTime bookingClosesAt(LocalDate date, LocalTime startTime);
boolean canCreate(LocalDate date, LocalTime startTime, LocalDateTime acceptanceTime);
LocalDateTime paymentExpiresAt(LocalDateTime createdAt,
                              LocalDateTime sessionStartsAt,
                              Duration paymentTimeout);
```

类为 `public final`，方法公开静态；不注册 Spring Bean、不再定义同名 interface。提前量在本类提供 `MINIMUM_BOOKING_ADVANCE` 常量，供 03 生成条件 SQL 阈值；三天长度也集中定义。

1. `selectableDates` 返回当前上海业务日期及之后两天，顺序固定。
2. `canCreate` 同时检查日期窗口和 `acceptanceTime < bookingClosesAt`，相等不满足。
3. `paymentExpiresAt` 返回创建时间加给定支付时限与场次开始的较早者。
4. 时间全部由调用方传入，本类不读 Clock、数据库或宿主默认时区。
5. 不为本次需求增加日期、提前量的管理员配置；支付时限参数继续接收现有配置。

## 场景与验收条件

负责 S17，并为 S01、S03～S11、S15、S18 提供规则基础。

- [x] 11/11 的日期列表严格为 11/11、11/12、11/13，零点推进后滚动一天。
- [x] 四场截止严格为 07:45、10:15、13:45、16:15。
- [x] 每个截止前一秒可通过时间资格，恰好截止和之后不可通过。
- [x] 昨天、大后天不满足新创建日期资格；今天满足日期仍需满足时刻条件。
- [x] 创建 13:40、场次 14:00、时限 15m，结果为 13:55；23:55 创建次日 08:00 场，结果次日 00:10。
- [x] 验证 min 的两个分支，不只验证 `createdAt + timeout`；纯规则测试可传入不同 Duration，不改变项目实际 15m 配置。
- [x] 新规则独立可测，不依赖 Controller、Mapper 或 payment 包。

## 验证方式

```bash
./mvnw -Dtest=BookingTimeRulesTest test
```

记录实际结果；本任务不提前改场次 GET、占号 SQL 和生产下单行为。没有新增 REST 路径或数据库迁移。

## 完成记录

验收通过后更新本文和 map 状态为 resolved，记录新增方法、测试命令与覆盖的规则。未运行的检查明确注明，不借用历史测试结果。

## Comments

- 2026-11-11：完成任务定义；尚未认领、尚未实现，暂无测试执行结果。

- 2026-11-11：开始实施共享时间规则及纯规则测试，尚未验收。

- 2026-11-11：共享纯规则已实现；`./mvnw -o -Dtest=BookingTimeRulesTest test` 9 项通过，0 失败、0 错误、0 跳过，日志 `/tmp/three-day-01.log`。覆盖三天滚动、四场严格边界、日期资格与支付 min/跨零点，01 resolved。
