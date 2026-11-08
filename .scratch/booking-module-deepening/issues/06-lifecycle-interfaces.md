# 06 预约与支付生命周期 interface 深度化

Type: refactor
Triage: ready-for-agent
Status: ready-for-agent
Blocked by: 03, 04, 05

设计依据：[spec](../spec.md)，执行位置：[map](../map.md)。初始 Status 使用 triage 标签表示待认领；开始实施改为 claimed，验收完成改为 resolved。Triage 不代表依赖已解除。

## 背景和目标

当前 coordination 掌握场次→预约锁、库存确认、nextVisitNumber、有效占用删除和分步关闭。将这些知识收回 appointment，在保持外层本地事务的前提下让调用者只执行完整业务操作，并类型化 payment 生命周期入口。

## 范围

- 引入 AppointmentLifecycle/AppointmentLifecycleCase/AppointmentConfirmation；实现 find、lock、confirm、closePending、cancelConfirmed。
- lock 封装只读定位→场次锁→预约锁→关联复验；confirm 内部取得序号并完成库存/预约更新；关闭内部释放对应库存和删除有效占用。
- PaymentLifecycle/PaymentLifecycleCase 类型化 find/lockByAppointment/closeUnpaid；删除无调用 refundPaid。
- 一次迁移全部生产调用者（包括 Legacy 预检与同步兜底），删除旧锁/库存分步 interface 与 ScheduleLifecycleView 的对外泄漏；暂留一个 Legacy markPaid 能力至 08，不新增第二套正式生命周期服务。
- 加 MANDATORY 事务契约和可验证回滚；05 已负责支付锁序，不在此再次改支付算法。

## 非范围

- 不在本任务抽取跨模块 Policy 或改变取消/过期/事件/对账分支。
- 不允许 confirm 自己开启 REQUIRES_NEW 或将库存与预约分事务。
- 不删除 Legacy 端点（由08负责），不改变条件更新或有效占用语义。
- 不让写模块互相引用 Entity/Mapper。

## 前置依赖

[03 类型化状态、关闭原因与退款原因](03-typed-lifecycle-language.md)；[04 预约暂占创建内部深度化](04-appointment-hold-deepening.md)；[05 支付回调决策分离与支付锁序收敛](05-payment-notification-policy.md)。
只有全部前置任务 Status 为 resolved 才能开始；当前仅01可开始。推荐按map串行顺序执行，避免共享快照和调用点冲突。

## 需要修改的模块和主要文件

下列源码路径相对项目根目录；新增类型明确标注，implementation命名可沿用现有风格，不强制为改名搬文件。

- `src/main/java/com/qsx/appoint/appointment/application/{AppointmentLifecycleOperations,AppointmentLifecycleOperationsImpl,AppointmentLifecycleView,ScheduleLifecycleView}.java` 替换为目标 interface/Case/implementation/Confirmation。
- `payment/application/{PaymentLifecycleOperations,PaymentLifecycleOperationsImpl,PaymentLifecycleView}.java` 类型化及清理 refundPaid；必要时清理仅剩无调用的对应 Mapper 方法。
- `coordination/application/{DefaultBookingLifecycleCoordinator,DefaultPaymentSucceededProcessor,DefaultLegacyPaymentSimulationPrecheck}.java`
- `payment/controller/LegacySimulatePaymentController.java` 的 interface 调用适配。
- 新增 AppointmentLifecycle interface 集成测试，更新现有调用这些 interface 的测试；保留 MySQL/RabbitMQ 用例。

## 目标代码形态

coordination 调用 appointments.lock(id) 后再 payments.lockByAppointment(id)。Appointment Case 不包含库存列或 nextVisitNumber。confirm(case) 返回确认结果而不是要求传序号；closePending(case, closeReason, closedAt) 和 cancelConfirmed(case, cancelledAt) 包含库存与有效占用处理。所有锁/写方法以 MANDATORY 经代理加入调用者事务；find 是非加锁定位快照，不能用于写操作。

## 改造后的业务逻辑

lock 无预约时返回 null，场次缺失/历史空 session 按现有错误处理。确认在已锁场次内读取下一序号→confirmOne 条件更新→confirmIfPending；关闭待支付为释放held→closeIfPending→删占用；取消确认为释放confirmed→cancelIfConfirmed→删占用。外层退款请求与这些完整操作一起提交，调整内部语句封装顺序不能改变最终原子结果。Legacy 同步路径仍保留同事务 markPaid 并创建兼容尝试，08再删。

## 必须保持的不变量

- 锁定 Case 只在获得它的外层事务中使用，禁止将 find 结果或跨事务 Case 传入写操作；实现保留关联/状态复验与条件 UPDATE。
- 场次→预约→payment→支付子记录；封装不能隐式提前锁 payment。
- 序号单调不复用；一次确认仅推进一次库存/序号；关闭只删除对应有效占用。
- 每步 requireOne、异常码与事务回滚保持；无事务调用必须失败且不写库。
- 暂留 Legacy 能力明确截止08，其余旧分步方法在本 issue 完成时删除。

## 可验证验收条件

- 所有生产调用者均不再读/传 nextVisitNumber，不再分别确认库存/预约或释放库存/关闭预约。
- MANDATORY 的无事务调用拒绝；外层事务内成功，预约更新/删占用故障后库存及序号整体回滚。
- 确认、取消、过期、对账、Legacy 同步调用行为与01相同；锁定缺失/历史场次错误不变。
- 同场并发确认/取消/过期保持库存恒等式、唯一占用和序号不重复。
- 旧 interface/方法无残留调用、公开方法不返回 persistence Entity；构建和全部适用测试通过。

## 应运行的测试

- 新增 AppointmentLifecycle interface 集成测试（包含代理/传播和回滚）。
- `./mvnw -Dtest=ScheduleInventoryIntegrationTest,AppointmentLifecycleIntegrationTest,AppointmentQueryIntegrationTest,PaymentSucceededProcessorIntegrationTest,ModuleDependencyTest test`
- `./mvnw test`
- `./mvnw -Pmysql-it verify`
- `./mvnw -Preliability-it verify`（Inbox/退款事务与原链路一致）。

## 独立交付与回退

作为完整 interface 家族迁移提交，回退时连同所有调用者和新增目标 interface 测试一起回退；不回退03–05，不修改数据。不要只恢复一侧 interface 导致编译失败。
后续任务已使用本任务成果时，先逆序撤销依赖，或做兼容性调整；独立回退指本任务完成时可整体撤销，不保证任意抽掉已被下游使用的提交仍可构建。任何回退不得删除已提交资金、Outbox/Inbox或退款数据。

## 完成状态清单

- [ ] 完整生命周期 interface/Case 与事务契约
- [ ] 全部调用者迁移并删除低层泄漏
- [ ] Legacy markPaid 暂留范围标记
- [ ] 原子回滚/库存/序号/锁竞争验证
- [ ] 三层测试与 Comments 更新

## Comments

- 2026-11-08：根据源码复核后的spec创建。仅完成任务定义，未认领、未实施；本轮基线结果见map，不等同于本issue验收结果。 依赖尚未resolved，暂不可开始实施。
