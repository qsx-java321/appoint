# 04 预约暂占创建内部深度化

Type: refactor
Triage: ready-for-agent
Status: claimed
Blocked by: 03

设计依据：[spec](../spec.md)，执行位置：[map](../map.md)。初始 Status 使用 triage 标签表示待认领；开始实施改为 claimed，验收完成改为 resolved。Triage 不代表依赖已解除。

## 背景和目标

`AppointmentHoldServiceImpl.createHold` 已具备正确原子创建和多次日期防护，但集中处理规则、场次生成、就诊人、目录快照和占用。通过有业务含义的内部方法隐藏细节，保留现有用例顺序及等待锁后的最终日期验收。

## 范围

- 优先命名 private 方法；有独立依赖或纯规则测试价值时才新增 package-private BookingPolicy、ScheduleProvisioning、AppointmentFactory/PatientAccess。
- 保留外部 AppointmentHoldService/CreateAppointmentHoldCommand/AppointmentHold 和 MANDATORY；不改 BookingCoordinator 的幂等协议。
- 复用设置/场次生成逻辑，不新增通用 Inventory 或 Repository interface。
- 移除本次提取直接导致的冗余变量/未用导入，限于本用例。

## 非范围

- 不减少最终日期检查，不缓存 createdAt 对应日期作为实际占号日期。
- 不改四场次、D+1～D+7、设置快照、费用、支付超时配置和错误码。
- 不重构 catalog/patient CRUD，不修改 SQL 条件、唯一索引或生成策略。

## 前置依赖

[03 类型化状态、关闭原因与退款原因](03-typed-lifecycle-language.md)。
只有全部前置任务 Status 为 resolved 才能开始；当前仅01可开始。推荐按map串行顺序执行，避免共享快照和调用点冲突。

## 需要修改的模块和主要文件

下列源码路径相对项目根目录；新增类型明确标注，implementation命名可沿用现有风格，不强制为改名搬文件。

- `src/main/java/com/qsx/appoint/appointment/application/AppointmentHoldServiceImpl.java` 及确有必要的同包内部类型。
- 只读参照 `catalog/mapper/{BookingSettingsMapper,ScheduleMapper}.java`、`patient/mapper/PatientMapper.java` 和 appointment Mapper。
- `src/test/java/com/qsx/appoint/appointment/{OnDemandBookingHttpTest,AppointmentIdempotencyIntegrationTest,ScheduleInventoryIntegrationTest,AppointmentLifecycleMySqlIT}.java` 及 01 日期边界测试。

## 目标代码形态

主方法读作：初验请求→确认医生存在→锁设置并按需生成/锁场次→校验场次→锁并校验就诊人→读取目录快照→最终读取日期并复验→条件暂占→Factory 保存预约快照→建立有效占用→返回 Hold。createdAt 仅服务业务快照和截止时间，不兼任最终验收日期。

## 改造后的业务逻辑

设置锁在前，生成时复制容量和挂号费；已存在场次仍使用原快照。就诊人锁保证修改前后获得完整身份快照。实际占号前以当前 Clock 日期重新校验 D+1/D+7，并把最终 today 传给 holdOneFutureSession。库存成功后创建预约与有效占用，外层再创建支付单。任何失败包含新场次和幂等登记一起回滚。

## 必须保持的不变量

- 锁/写顺序：幂等登记→设置→场次→就诊人→占号→预约→占用→支付→幂等完成。
- 相同场次/就诊人及场次/证件的有效预约唯一；唯一冲突仍 DUPLICATE_ACTIVE_APPOINTMENT。
- 总库存=可用+暂占+确认，快照不可被目录修改追写。
- paymentExpiresAt 仍为 createdAt + 配置超时（默认15分钟），允许跨零点，不取开诊时间最小值。
- 外层事务原子性及 MANDATORY 代理调用不变。

## 可验证验收条件

- 主方法表达业务顺序，新增类各有独立规则/依赖，无无意义 public interface。
- 两次不同设置只影响之后首次生成场次；同场次并发首建最终一行、不超卖。
- 01 的可控跨零点测试仍通过，等待锁后日期到当天时整个创建回滚；已成功请求跨零点可幂等重放。
- 重复就诊人/证件、停用/非本人、无医生、费用非法和余量不足保持原错误及失败回滚。
- 源码未改 Mapper 条件和事务边界。

## 应运行的测试

- `./mvnw -Dtest=OnDemandBookingHttpTest,AppointmentIdempotencyIntegrationTest,ScheduleInventoryIntegrationTest,PatientIntegrationTest,ModuleDependencyTest test`
- 加跑 01 新增 Clock/回滚测试；`./mvnw test`。
- `./mvnw -Pmysql-it -Dit.test=AppointmentLifecycleMySqlIT,PatientJourneyHttpMySqlIT verify`。

## 独立交付与回退

独立回退实现提取与本次新增纯规则测试；03 类型保留，无数据库迁移。后续 06 只改生命周期，不应依赖本 issue 的私有类。
后续任务已使用本任务成果时，先逆序撤销依赖，或做兼容性调整；独立回退指本任务完成时可整体撤销，不保证任意抽掉已被下游使用的提交仍可构建。任何回退不得删除已提交资金、Outbox/Inbox或退款数据。

## 完成状态清单

- [ ] 主方法及必要内部职责拆分
- [ ] 最终日期与锁序保留
- [ ] 快照/幂等/冲突/回滚回归
- [ ] H2 与 MySQL 竞争通过
- [ ] Comments/地图更新

## Comments

- 2026-11-08：根据源码复核后的spec创建。仅完成任务定义，未认领、未实施；本轮基线结果见map，不等同于本issue验收结果。 依赖尚未resolved，暂不可开始实施。

- 2026-11-08：已提取就诊人校验和预约快照构造；其余整理及最新代码回归尚待完成。
