# 01 基线行为、状态决策表与风险证据

Type: test
Triage: ready-for-agent
Status: claimed
Blocked by: none

设计依据：[spec](../spec.md)，执行位置：[map](../map.md)。初始 Status 使用 triage 标签表示待认领；开始实施改为 claimed，验收完成改为 resolved。Triage 不代表依赖已解除。

## 背景和目标

深度化方案已与 `9d20e301db5896c08896730a91a09f4a1b8cb058` 源码核对。当前快速测试 98 项、MySQL 24 项；旧支付任务地图中的 83 项是历史结果。本任务建立可重跑的行为基线，区分已有保证、测试缺口和已知业务缺口，供后续逐步替换实现。

## 范围

- 按 spec 2.4/2.5 整理查询、通知、取消、过期、对账和事件消费的输入/输出决策表，记录错误优先级、返回布尔值、退款原因和完整数据库副作用。
- 补齐当前缺失的截止前/恰好截止/截止后、paidAt=null、重复不同 eventId、非法状态组合与查询权限 characterization tests。
- 增加可控 Clock 日期跨零点的创建最终验收与失败回滚测试；区分“请求开始时间”和“实际占号日期”。
- 记录回调 attempt→payment、T2 失败 attempt→payment、worker refund→payment 的静态锁图；用 MySQL 可控并发复现或记录未复现，不要求概率测试每次撞出死锁。
- 在本目录增加 `baseline.md`（实施时创建），写执行时间、命令、用例数、失败/跳过和风险复现证据；不改写历史验证报告。

## 非范围

- 不创建新业务 Policy 或修改生产代码、SQL、表结构。
- 不把“迟到事件残留暂占”改成自动过期，不改变普通消费者重试策略。
- 不要求现有架构一定已经满足目标锁序，不写必须失败的目标实现测试阻断本阶段。

## 前置依赖

无。
只有全部前置任务 Status 为 resolved 才能开始；当前仅01可开始。推荐按map串行顺序执行，避免共享快照和调用点冲突。

## 需要修改的模块和主要文件

下列源码路径相对项目根目录；新增类型明确标注，implementation命名可沿用现有风格，不强制为改名搬文件。

- `src/test/java/com/qsx/appoint/appointment/{AppointmentLifecycleIntegrationTest,AppointmentQueryIntegrationTest,OnDemandBookingHttpTest,AppointmentIdempotencyIntegrationTest,AppointmentLifecycleMySqlIT}.java`
- `src/test/java/com/qsx/appoint/payment/{PaymentNotificationMySqlIT,PaymentAttemptMySqlIT,OutboxPublisherReliabilityIT}.java`
- `src/test/java/com/qsx/appoint/coordination/{PaymentSucceededProcessorIntegrationTest,PaymentConfirmationReliabilityIT}.java`
- `src/test/java/com/qsx/appoint/architecture/ModuleDependencyTest.java`
- `.scratch/booking-module-deepening/baseline.md`、本 issue 与 map Comments。花括号表示同目录多文件。

## 目标代码形态

测试从现有 HTTP/application interface 驱动，断言响应、状态、库存、有效占用、序号、审计、Outbox/Inbox 和退款数量。决策表先以测试数据或 Markdown 表表达，不预先引入目标生产 Policy。并发用 latch/独立事务控制交错，有超时，避免以 sleep 和无限重试碰运气。

## 改造后的业务逻辑

创建仍原子登记幂等、生成场次、占号、建预约/支付和完成回执；回调只提交资金事实与事件；消费者随后确认或退款；取消/过期/对账保留各自的短路、状态选择及退款原因。迟到且仍待支付的事件当前只退款并保留 held，必须单独标注为已知缺口。

## 必须保持的不变量

- 此 issue 对生产业务零变更；现有测试不被删除或改弱。
- 状态/HTTP JSON/事件 v1/错误码、库存恒等式、有效占用唯一约束均不变。
- 已知缺口的回归断言只是迁移护栏，不代表认可该业务结果。

## 可验证验收条件

- 决策表至少覆盖 spec 9.2、2.5 的全部分支及两种取消布尔返回；每行关联到现有或新增用例。
- 最终占号前跨零点拒绝后，场次新插入、库存、预约、支付、有效占用和幂等登记均回滚；成功旧单跨零点同键重放保持可读。
- 相同通知 ID/hash 与 ID 冲突、同交易重复/乱序、不完整关联分别有断言。
- 每条风险注明静态证据/已复现/未复现，不能将现有测试通过解释为已证明无死锁或所有消费者故障窗口已覆盖。
- 三层基线通过且无跳过；环境阻塞时明确记录，不以历史结果替代本次结果。

## 应运行的测试

- `./mvnw test`
- `./mvnw -Pmysql-it verify`
- `./mvnw -Preliability-it verify`
- 三个命令分开执行；Docker 失败和测试失败分别记录。若依赖已缓存，可加 `-o`；不得为了离线通过跳过测试。

## 独立交付与回退

独立提交仅包含测试和基线文档；回退该提交不改变生产行为。新增测试不得要求尚未实施的 interface。
后续任务已使用本任务成果时，先逆序撤销依赖，或做兼容性调整；独立回退指本任务完成时可整体撤销，不保证任意抽掉已被下游使用的提交仍可构建。任何回退不得删除已提交资金、Outbox/Inbox或退款数据。

## 完成状态清单

- [ ] 状态/错误/副作用矩阵落盘并链接用例
- [ ] 跨零点、截止边界和关联错误补测
- [ ] 支付锁图和已知缺口证据记录
- [ ] 三层测试实际结果记录
- [ ] map 和 issue Comments 更新

## Comments

- 2026-11-08：根据源码复核后的spec创建。仅完成任务定义，未认领、未实施；本轮基线结果见map，不等同于本issue验收结果。 无前置依赖，是首个可开始实施的任务。

- 2026-11-08：已建立 Compose 隔离验收入口；阶段 1 快照常规 98、MySQL 24、双容器 9 项通过，真实预约支付取消退款链路通过。跨零点最终占号测试和完整决策矩阵仍待补齐，暂不标记 resolved。

- 2026-11-08：新增真实事务跨零点最终占号回滚与跨日幂等重放测试；在当前工作区及原生产代码暂存快照均通过。状态矩阵及其余验证仍需逐项核对。
