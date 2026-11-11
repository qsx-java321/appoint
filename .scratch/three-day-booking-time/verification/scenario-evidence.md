# 三天预约窗口场景验收证据

日期：2026-11-11。状态：已完成实现与验收。

对应[场景清单](../scenarios.md)和[任务地图](../map.md)。测试采用固定的 11/05 等日期验证与讲解例子相同的日期关系和时间边界；不修改宿主系统时间。

## 执行结果

- 最终 `./mvnw -o -Pmysql-it verify`：常规 263 项、MySQL 37 项，均零失败、零错误、零跳过。日志 `/tmp/three-day-final-mysql.log`。
- 完整 Compose/curl：运行 `e34a8b021efb`，92 次请求，200×63、201×12、202×13、403×1、409×3，全部符合预期；结果见[curl 报告](curl-report.json)，日志 `/tmp/three-day-final-compose.log`。
- 实现/测试文件摘要见[代码清单](code-manifest.sha256)。源码基线 `5d49ff95d294ddfa0aef259f64d2907d93c50889`；以上验收在提交前工作区执行，最终交付提交以 Git 历史为准。
- 受控 Clock 测试承担精确截止、午夜和当天场次；Compose 使用后天创建，完成真实 MySQL、RabbitMQ、MockPay、签到接诊正常链路。

## 用例入口

- [BookingTimeRulesTest](../../../src/test/java/com/qsx/appoint/catalog/model/BookingTimeRulesTest.java)：共享纯规则。
- [SessionCatalogHttpTest](../../../src/test/java/com/qsx/appoint/catalog/SessionCatalogHttpTest.java)：十二项目录、状态和查询时间。
- [OnDemandBookingHttpTest](../../../src/test/java/com/qsx/appoint/appointment/OnDemandBookingHttpTest.java)：创建、准入、支付、截止和历史查询。
- [BookingMidnightIntegrationTest](../../../src/test/java/com/qsx/appoint/appointment/BookingMidnightIntegrationTest.java)：原请求在新日期窗口之外仍可重放。
- [AppointmentLifecycleMySqlIT](../../../src/test/java/com/qsx/appoint/appointment/AppointmentLifecycleMySqlIT.java)：真实 MySQL 条件占号与既有库存约束。
- [VisitHttpTest](../../../src/test/java/com/qsx/appoint/visit/VisitHttpTest.java)：当天新预约支付、签到和医生接诊。

## 场景覆盖

下表列出的用例均在最终套件通过；组合用例用于分别验证场景的日期资格、期限计算或存储/查询链路，不将纯规则测试当作真实 RabbitMQ 传输验证。

| 场景 | 实际用例或请求证据 | 结果 |
| --- | --- | --- |
| S01 | SessionCatalogHttpTest.doctorCatalogReturnsTwelveSessionsWithOneTimeSnapshotWithoutWritingSchedules；真实 curl 场次 GET | 三天十二项，排序正确 |
| S02 | SessionCatalogHttpTest.doctorCatalogReturnsTwelveSessionsWithOneTimeSnapshotWithoutWritingSchedules | 10:05 已开诊/可预约状态与统一时间正确 |
| S03 | OnDemandBookingHttpTest.allFourSessionsUseStrictNewOrderCutoffs；SessionCatalogHttpTest.timeStateTakesPrecedenceOverFullInventory | 截止前一秒可创建，恰好截止不可创建，查询一致 |
| S04 | OnDemandBookingHttpTest.todayBookingPersistsIdenticalAppointmentAndPaymentTimes | 当天创建成功，双方创建时间和截止一致 |
| S05 | OnDemandBookingHttpTest.firstBookingCreatesSessionFromSettingsAndGetsFullPaymentWindow | 明天场次正常创建，支付截止从今天的创建时间计算 |
| S06 | OnDemandBookingHttpTest.settingChangesOnlyAffectSessionsCreatedAfterTheChange；BookingTimeRulesTest.paymentDeadlineIsPerOrderAndCanCrossMidnight | 后天合法且本单支付期限仍从创建时间计算 |
| S07 | OnDemandBookingHttpTest.existingOrderCanPayAfterNewBookingCutoffWithOneDeadlineAcrossAllResponses | 停止新下单后，原单仍可在自己的期限内支付 |
| S08 | 同上正常支付用例；真实 curl attempt/complete/详情轮询 | 正常确认至 CONFIRMED/PAID 并取得号码 |
| S09 | OnDemandBookingHttpTest.paymentAcrossMidnightUsesPersistedDeadline | 次日 00:05 可付，沿用次日 00:10 截止 |
| S10 | OnDemandBookingHttpTest.midnightWhileWaitingStillAcceptsOriginalTomorrowAsToday | 原明天变今天，仍按锁后时间合法创建 |
| S11 | SessionCatalogHttpTest.midnightRollsAllThreeDatesWithoutPersistingSessions | 零点窗口整体滚动，单次响应不混用日期 |
| S12 | OnDemandBookingHttpTest.historyRemainsReadableAfterLeavingTheThreeDayWindowWithoutRecalculatingDeadline；BookingMidnightIntegrationTest.previouslyCompletedKeyReplaysEvenAfterOriginalDateLeavesCreationWindow | 详情、我的预约和原请求标识保留，期限不重算 |
| S13 | OnDemandBookingHttpTest.existingOrderCanPayAfterNewBookingCutoffWithOneDeadlineAcrossAllResponses | 创建、支付尝试、详情的期限完全一致 |
| S14 | VisitHttpTest.sameDayBookingCanBePaidCheckedInAndCompletedWithoutChangingInventory；真实 curl 就诊阶段 | 当天确认后可签到接诊，用户/医生一致且库存不变 |
| S15 | OnDemandBookingHttpTest.createdAtIsSampledAfterPatientLockRatherThanRequestRegistration | 锁后 13:40 准入，未沿用 13:30 登记时间 |
| S16 | SessionCatalogHttpTest.timeStateTakesPrecedenceOverFullInventory | 满额、截止、开诊优先级正确，其他场次独立 |
| S17 | BookingTimeRulesTest.creationChecksBothDateWindowAndTodaySessionTime；OnDemandBookingHttpTest.newOrdersRejectPastDateCutoffBeyondThreeDatesAndUnknownSession | 今天至后天日期合法，仍须满足场次时刻 |
| S18 | OnDemandBookingHttpTest.reachingPaymentDeadlineDisablesPaymentWithoutWaitingForExpirationScan | 达到期限立即不可支付，详情不依赖扫描完成 |

真实 MySQL 额外通过 `sameDaySqlAdmissionUsesStrictApplicationTimeThreshold`，直接验证今天的 SQL 日期范围、截止相等及前一秒，库存转换保持一致。

## 资源与验证限制

成功的隔离资源已由脚本清理，验收应用进程退出；共享 Compose 依赖保持运行，原业务库与数据卷保留。首次报告目录缺失的失败资源及日志保留，详见[验证报告](../../../docs/verification-report.md)。

支付为 MockPay，本轮没有新增前端、退款或消息故障专项，也未重跑 reliability-it 专用 profile。原有常规/MySQL/Compose 场景均保留，未用历史测试数量代替本轮结果。
