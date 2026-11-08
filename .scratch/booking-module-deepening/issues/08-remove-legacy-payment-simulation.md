# 08 迁移支付测试并删除 dev/test 旧模拟路径

Type: cleanup
Triage: ready-for-agent
Status: ready-for-agent
Blocked by: 07

设计依据：[spec](../spec.md)，执行位置：[map](../map.md)。初始 Status 使用 triage 标签表示待认领；开始实施改为 claimed，验收完成改为 resolved。Triage 不代表依赖已解除。

## 背景和目标

用户已于2026-11-08明确允许删除 dev/test `/simulate-success`；正式HTTP保持兼容。当前旧Controller是“正式回调+直接消费+同步兜底”，另有测试直接调用同步协调方法。两组调用都迁移后再删兼容代码，防止仍依赖兼容成功尝试和同步语义。

## 范围

- 清点并迁移所有旧HTTP和simulatePaymentSuccess调用，测试helper使用正式attempt/Gateway签名/通知入口，并按测试层级处理Outbox。
- 删除 LegacySimulatePaymentController、coordination 的 LegacyPaymentSimulationPrecheck/DefaultLegacyPaymentSimulationPrecheck、BookingLifecycleCoordinator.simulatePaymentSuccess及实现。
- 删除PaymentLifecycle.markPaid、compat-*构造，以及确认无调用后的 payIfUnpaid/兼容DTO等；refundPaid已由06清理。
- 新增dev/test旧路由不存在的断言，保留生产Mock/旧路由隔离测试；同步更新当前API/示例，最终文档整理在10。
- 替换旧并发测试中的同步成功和终态断言，保留原库存/幂等/重复退款覆盖，并与真实回调矩阵合并去重。

## 非范围

- 不删除正式 MockPay attempt/checkout/complete/fail 与统一渠道回调。
- 不改正式API状态码/JSON/错误；只删除已授权旧端点及其内部能力。
- 不以helper直接写PAID、构造兼容attempt或绕过签名取代业务流程。
- 不把所有H2测试强制变成RabbitMQ测试。

## 前置依赖

[07 跨模块生命周期决策与流程编排收口](07-coordination-process-manager.md)。
只有全部前置任务 Status 为 resolved 才能开始；当前仅01可开始。推荐按map串行顺序执行，避免共享快照和调用点冲突。

## 需要修改的模块和主要文件

下列源码路径相对项目根目录；新增类型明确标注，implementation命名可沿用现有风格，不强制为改名搬文件。

- `src/main/java/com/qsx/appoint/payment/controller/LegacySimulatePaymentController.java`
- `coordination/application/{LegacyPaymentSimulationPrecheck,DefaultLegacyPaymentSimulationPrecheck,BookingLifecycleCoordinator,DefaultBookingLifecycleCoordinator}.java`
- `payment/application/PaymentLifecycle` 的Legacy方法及implementation，`payment/mapper/PaymentOrderMapper.java` 无调用方法，`payment/dto/PaymentResponse.java`（仅确认全局无调用后删除）。
- `src/test/java/com/qsx/appoint/{ApiFlowIntegrationTest.java,appointment/AppointmentIdempotencyIntegrationTest.java,appointment/AppointmentLifecycleIntegrationTest.java,appointment/AppointmentQueryIntegrationTest.java,appointment/ScheduleInventoryIntegrationTest.java,appointment/AppointmentLifecycleMySqlIT.java,payment/MockPayProductionProfileTest.java}`
- 新增测试support helper和路由不存在契约测试；`docs/api.md`、`docs/business-flow.md`、README/http 下实际存在的旧用法。

## 目标代码形态

生产成功资金状态仅由验签回调推进；预约确认由正式PaymentSucceededProcessor推进。H2/application测试可经公开interface创建尝试、签名通知→Ingress→读取真实生成Outbox并用Codec解码→Processor；这是确定性测试驱动，不能声称验证RabbitMQ传输。HTTP/MySQL/RabbitMQ全链路测试必须用真实发布/消费者并轮询最终状态，避免直接Processor代替端到端传输。

## 改造后的业务逻辑

用户发起attempt返回202，完成MockPay后通知落库PAID+Outbox；发布后Inbox确认预约或退款，查询最终结果。旧同步支付错误场景迁移到正式attempt/通知入口的既有错误语义，不要求已删除端点错误码继续存在。并发取消允许合法中间态/终态REFUND_PENDING，不能保留旧测试只接受CLOSED/REFUNDED的假设。

## 必须保持的不变量

- 唯一HTTP变化是dev/test旧simulate-success不再注册；生产原本无该路由，正式端点兼容。
- 测试不伪造资金状态/越过验签，不用sleep代替可观察状态等待。
- 原幂等确认、库存释放、序号单调、迟到退款、每单一笔退款覆盖不缩水。
- 消息仍v1、Outbox/Inbox及DLQ保留；Legacy历史数据只读兼容不因删除模拟入口被删除。

## 可验证验收条件

- 通过请求映射检查dev/test/production均无旧路由；不要把未认证返回401当作证明路由不存在。
- rg检索生产源码无Legacy类/同步方法/markPaid/compat构造残留；历史文档提及可保留，不要求删除历史记录。
- 所有原支付成功相关测试完成迁移；正式完整链路至少有真实HTTP+MySQL/RabbitMQ验证，快速测试明确不承担消息传输保证。
- 原库存与竞争断言不被弱化；新合法异步中间态有显式断言，最终状态有有界等待。
- 无无调用类/方法/导入；当前文档不再建议调用旧端点。

## 应运行的测试

- 全部受影响H2测试和新增路由契约测试；`./mvnw test`。
- `./mvnw -Pmysql-it verify`，重点AppointmentLifecycleMySqlIT/PatientJourneyHttpMySqlIT。
- `./mvnw -Preliability-it verify`。
- `rg -n 'simulatePaymentSuccess|LegacySimulatePaymentController|LegacyPaymentSimulationPrecheck|compat-' src/main src/test` 应无业务残留；路由不存在测试可保留旧URL作为断言输入。

## 独立交付与回退

本issue的测试迁移、代码删除及当前用法文档作为完整提交回退；恢复的也仅是dev/test路径，不影响已产生数据。回退不需要恢复数据库或事件；若后续09/10依赖删除结果，先逆序回退对应依赖。
后续任务已使用本任务成果时，先逆序撤销依赖，或做兼容性调整；独立回退指本任务完成时可整体撤销，不保证任意抽掉已被下游使用的提交仍可构建。任何回退不得删除已提交资金、Outbox/Inbox或退款数据。

## 完成状态清单

- [ ] 两组旧调用清单和正式测试helper完成
- [ ] 测试迁移且端到端传输不被helper替代
- [ ] Controller/预检/同步能力与死代码删除
- [ ] 三profile旧路由不存在及正式链路验证
- [ ] 当前用法文档与Comments更新

## Comments

- 2026-11-08：根据源码复核后的spec创建。仅完成任务定义，未认领、未实施；本轮基线结果见map，不等同于本issue验收结果。 依赖尚未resolved，暂不可开始实施。
