# 04 正常支付、查询与就诊链路

Type: test
Triage: ready-for-agent
Status: resolved
Blocked by: 02, 03

设计依据：[spec 第 5.3、8、10、11 节](../spec.md)。执行位置：[map](../map.md)。场景索引：[scenarios](../scenarios.md)。

## 目标与前置条件

02、03 均 resolved 后，串联新时间快照与原支付、预约查询和就诊能力。明确停止新下单不影响原单支付；更新真实验收脚本，使它创建合法日期的订单。

该任务以测试、验收入口适配为主。已有生产逻辑满足目标时直接复用，不为了任务编号新增 Service 或 REST 路径。

## 涉及文件与读取入口

- 复用并核对 `src/main/java/com/qsx/appoint/payment/application/PaymentOrderCreationServiceImpl.java`、`PaymentAttemptServiceImpl.java`、`PaymentAttemptTransaction.java`。
- 复用并核对 `src/main/java/com/qsx/appoint/bookingquery/application/DefaultBookingQueryService.java`、`AppointmentResponseAssembler.java`。
- 复用并核对 `src/main/java/com/qsx/appoint/visit/application/DefaultVisitService.java`、`visit/domain/VisitRules.java`。
- 在现有 `src/test/java/com/qsx/appoint/appointment/OnDemandBookingHttpTest.java` 扩充支付时间链路；较独立的端到端用例可新增 `ThreeDayBookingFlowHttpTest.java`，沿用已有正式 MockPay 支付辅助工具。
- 复用 `src/test/java/com/qsx/appoint/payment/PaymentAttemptIntegrationTest.java`、`bookingquery/application/AppointmentResponseAssemblerTest.java`、`visit/VisitHttpTest.java`，按新增覆盖实际需要修改。
- `src/test/java/com/qsx/appoint/PatientJourneyHttpMySqlIT.java`：验证整体正常链路，与 02 的数量断言衔接。
- `scripts/compose_acceptance.py`：将正常创建使用的上海日期 `+3 days` 改为 `+2 days`，其余改动限于本轮契约所需的断言与日期选择。

## REST 与 Service 接续约定

1. `POST /api/v1/payments/{id}/attempts` 的 ID 是支付单 ID，HTTP 202 和原响应字段保持，expiresAt 读取支付单。
2. 支付入口只校验已保存的截止与支付状态；不得新增 `BookingTimeRules.canCreate` 或新三天窗口判断。
3. `GET /api/v1/appointments/{id}` 和 `/appointments/my` 按原契约查询全部记录，使用已存 paymentExpiresAt；已有超出新创建窗口的订单仍可查。
4. 创建、支付尝试和详情三处展示的截止必须完全一致；查询、刷新不延长期限。
5. 继续使用 `POST /api/v1/appointments/{id}/check-in` 和医生原开始、完成接诊接口。当天合法预约确认后可沿用现有签到资格，不额外增加提前到院分钟限制。

## 正常流程验证

主责 S07、S08、S09、S12、S13、S14、S18。

- [x] 10:14 创建 10:30 场订单，10:20 发起支付仍可用；同时新建同场订单已过下单时间资格。
- [x] 跨零点原订单付款成功，沿用已保存的次日 00:10 截止。
- [x] 首次创建 201、支付尝试 202、预约详情中的截止一致；正常收款后最终 CONFIRMED/PAID 且取得号码。
- [x] 使用受控时间验证原单达到支付截止时 canPay=false，刷新不重置期限；不增加消息故障或迟到收款专项。
- [x] 已存的历史/窗口外本人预约仍可查询，原截止不被新规则覆盖。
- [x] 当天确认预约经用户签到、医生开始、完成后，双方进度一致，预约/支付/号码及场次库存保持。
- [x] 验收脚本不再创建大后天预约，不通过临时放宽生产三天规则让脚本通过。
- [x] 若复用现有 Compose 脚本，其已有测试内容保留，不将旧流程删除当作“只关心正常流程”；本轮不扩展那些专项。

## 验证方式

```bash
./mvnw -Dtest=OnDemandBookingHttpTest,PaymentAttemptIntegrationTest,AppointmentResponseAssemblerTest,VisitHttpTest test
```

若新增 `ThreeDayBookingFlowHttpTest`，将实际类名补入命令，不在类尚不存在时声称已执行。

```bash
./mvnw -Pmysql-it -Dit.test=PatientJourneyHttpMySqlIT verify
python3 -m py_compile scripts/compose_acceptance.py
```

真实 Compose 执行和完整结果在 05 收口。时间边界通过受控 Clock 验证，不依赖现实恰好处于某一时刻；正常真实 HTTP 主线默认后天。无需新增生产调时接口。

## Comments

- 2026-11-11：完成任务定义；尚未认领，依赖 02、03 未 resolved，暂无链路验证结果。

- 2026-11-11：02、03 已验收，开始正常支付、历史查询与当天就诊联调测试，并适配 Compose 日期。

- 2026-11-11：正常链路聚焦 30 项通过（`/tmp/three-day-04.log`）；真实 MySQL/TCP HTTP 的 PatientJourneyHttpMySqlIT 2 项通过（`/tmp/three-day-04-mysql.log`），均零失败/错误/跳过。已覆盖截止后原单支付、跨零点、历史查询、三处期限一致及当天签到接诊；脚本改为后天，语法检查通过。04 resolved，完整 Compose 在 05 执行。
