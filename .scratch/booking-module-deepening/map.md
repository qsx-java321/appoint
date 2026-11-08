# 四模块深度化改造任务地图

Status: active

设计依据：[复核后的 spec](spec.md)。源码复核日期：2026-11-08（Asia/Shanghai）；Git 基线：`9d20e301db5896c08896730a91a09f4a1b8cb058`。本轮正在实施改造；issue 状态及验证记录见各 issue 和本地图 Comments。

## 当前实现与目标差距

| 领域 | 当前源码事实 | 目标及约束 | Issue |
| --- | --- | --- | --- |
| 预约暂占创建 | MANDATORY参加外层事务，方法内混合设置锁、按需场次、就诊人、库存和快照；实际占号前多次日期复验 | 保持设置→场次→就诊人→最终日期验收的顺序，提取必要内部职责；禁止只用请求开始日期 | 01、04 |
| 预约生命周期 | 暴露lockSchedule/lockAppointment、confirmInventory/confirmAppointment和分步释放；coordination传nextVisitNumber | appointment完整操作内封装锁序、库存、序号和占用；锁和写操作MANDATORY，不跨事务传Case | 03、06 |
| 支付回调 | attempt→notification复验→payment；状态/审计/Outbox/退款混合，资金事实原子提交 | payment先锁、attempt和通知锁下复验；纯Policy + 原子Executor，事件v1不变 | 01、05 |
| 支付其他锁路径 | T1为payment→attempt；T2失败attempt→payment；退款请求payment→refund→attempt；worker完成refund→payment | 05覆盖全部相关支付写路径，父payment锁串行化其子记录，不扩大成支付重写 | 05 |
| 跨模块协调 | 已有同库创建、取消/过期/对账和Inbox消费；复杂分支与细粒度库存操作交织 | 深interface后提取跨模块纯Policy，保留各入口校验/短路/布尔返回/退款原因 | 06、07 |
| 查询 | Mapper只读联查；toResponse承担workflow、权限、脱敏和组装 | Projector/Assembler；不改SQL/DTO和既有canCancel差异；refund状态只做摘要 | 02、03 |
| 类型语言 | 已有支付状态enum，application仍大量字符串；appointment无统一enum | Java层显式映射，边缘仍原字符串，无schema/TypeHandler迁移 | 03 |
| 旧模拟路径 | 仅dev/test；HTTP包装正式attempt/回调、直接消费并调用同步兜底；测试另直接调用同步协调 | 迁移两组测试调用再删路径。用户已授权08删除dev/test端点，正式HTTP不变 | 08 |
| 架构门禁 | 已阻止写模块互依赖和协调访问Mapper/Entity，query Mapper无写注解 | 补implementation/返回Entity/Controller访问Mapper的规则及违规反例；保留合法入口依赖 | 09 |
| 已确认业务修复 | 原迟到事件保留暂占，原消费者对多数错误无限重投 | 11 同事务释放并退款；12 三次处理后进入 DLQ，确认可靠转发 | 11、12 |

旧 [支付改造 map](../payment-module-refactor/map.md) 的“当前实现”表是历史基线；其16项已resolved，不是本轮待做事项。ADR0005现已覆盖旧半日场次、开诊截止最小值及停诊写流程；本轮保留历史只读，不恢复停诊接口。

## 实施原则与测试表面

- 业务主方法保留步骤、事务和简单分支；复杂组合下沉到拥有规则的Policy/值对象/内部implementation。
- 优先private方法和package-private具体类；不为单实现Factory/Assembler/Policy增加interface，也不为对称搬目录。
- 写模块互不引用对方Mapper/Entity/implementation；coordination经application interface/稳定事实和事件协作；bookingquery只读。
- 创建预约和支付仍单库原子事务；资金回调审计/Outbox原子提交；消费Inbox和确认/退款原子提交。
- 条件更新、唯一约束、FOR UPDATE、固定锁序、迟到退款、过期保护、对账和DLQ保留。潜在锁序偏差须核验并收敛，不能写成已经不存在风险。
- interface测试覆盖外部可观察结果；Policy决策表纯测试；锁/约束用MySQL，传输/故障窗口用真实RabbitMQ。快速测试可驱动公开Processor，但不冒充传输验收。
- 无数据库迁移，正式HTTP/错误码/事件v1不变；唯一已授权例外为08删除dev/test旧端点。

## Issue与依赖

Type表示工作性质，Triage表示任务清晰可交给agent；初始Status=ready-for-agent（未认领），实施时claimed，验收后resolved。只有全部Blocked by任务为resolved才unblocked；依赖状态和实际进度以各 issue 的 Status 与 Comments 为准。后续Comments必须填实际命令、结果和遗留事项，不能把本次基线当作改造验收。

| 顺序 | Issue | Type | 初始Status/Triage | Blocked by | 当前可执行性 | 独立验收重点 |
| ---: | --- | --- | --- | --- | --- | --- |
| 01 | [基线行为、状态决策表与风险证据](issues/01-baseline-state-matrix.md) | test | claimed | 无 | 实施中 | 状态/错误/副作用矩阵、最终日期复验与风险证据 |
| 11 | [迟到支付释放暂占](issues/11-late-payment-release.md) | fix | claimed | 01 | 验收中 | 过期、释放、退款与 Inbox 原子提交 |
| 12 | [消费有界重试与可靠转发](issues/12-bounded-consumer-retries.md) | fix | claimed | 01 | 验收中 | 三次处理、确认后 ACK、受控重放 |
| 02 | [查询流程投影与响应组装分离](issues/02-booking-query-projection.md) | refactor | claimed | 01 | 实施中 | Projector/Assembler；SQL、JSON、权限完全兼容 |
| 03 | [类型化状态、关闭原因与退款原因](issues/03-typed-lifecycle-language.md) | refactor | ready-for-agent | 01 | 依赖未完成 | 状态/原因类型化；持久化与事件稳定字符串不变 |
| 04 | [预约暂占创建内部深度化](issues/04-appointment-hold-deepening.md) | refactor | claimed | 03 | 实施中 | 设置/场次/就诊人/占号/快照顺序及跨零点回滚 |
| 05 | [支付回调决策分离与支付锁序收敛](issues/05-payment-notification-policy.md) | refactor/fix | claimed | 03 | 实施中 | 回调纯决策；payment父锁先行；原子Outbox/审计/退款 |
| 06 | [预约与支付生命周期 interface 深度化](issues/06-lifecycle-interfaces.md) | refactor | ready-for-agent | 03、04、05 | 依赖未完成 | 完整预约操作封装库存/序号/占用；MANDATORY |
| 07 | [跨模块生命周期决策与流程编排收口](issues/07-coordination-process-manager.md) | refactor | ready-for-agent | 05、06 | 依赖未完成 | 取消/过期/确认/对账纯决策；保留入口差异 |
| 08 | [迁移支付测试并删除 dev/test 旧模拟路径](issues/08-remove-legacy-payment-simulation.md) | cleanup | ready-for-agent | 07 | 依赖未完成 | 正式支付测试迁移；已授权删除dev/test旧端点 |
| 09 | [架构、并发与消息可靠性最终门禁](issues/09-architecture-and-reliability-gates.md) | test | ready-for-agent | 02、07、08 | 依赖未完成 | 架构违规反例；MySQL竞争与真实消息故障窗口 |
| 10 | [最终 interface、流程与验证文档交付](issues/10-documentation-delivery.md) | docs | ready-for-agent | 09 | 依赖未完成 | 当前契约/流程/ADR/验证记录一致，已知限制交付 |

## 推荐执行顺序

默认串行：**01 → 11 → 12 → 02 → 03 → 04 → 05 → 06 → 07 → 08 → 09 → 10**。

建议的实施顺序如下；具体前置依赖以各 issue 的 Blocked by 为准：

```text
01 → 11 → 12 → 02 ────────────────────┐
 └→ 03 → 04 ────────┐                 │
       └→ 05 ───────┴→ 06 → 07 → 08 → 09 → 10
                         └───────→ 09
```

- 02依赖01；03依赖01。推荐02先做，在03统一类型；若03先做，02直接复用类型，避免临时重复enum。
- 04、05仅在03完成后可分别实施；04的创建与05的支付逻辑相对独立，默认串行降低调用点冲突。
- 06显式依赖03、04、05，先稳定支付锁图，再将完整预约操作接入全部调用者；一个可构建issue内完成整个interface家族切换。
- 07依赖05、06，抽取既有决策而非补写业务。
- 08依赖07，迁移测试后删除Legacy；09依赖02、07、08，查询验收不可遗漏；10依赖09。

第一项可开始实施的是 [01 基线行为、状态决策表与风险证据](issues/01-baseline-state-matrix.md)。实现已经开始；01 基线矩阵、已开始的 11/12 和结构改造仍需完成各自验收。

## 各阶段测试门禁

| Issue | 聚焦验证 | 阶段门禁 |
| --- | --- | --- |
| 01 | 状态/副作用矩阵、日期/截止边界、已有锁图与故障证据 | 常规 + mysql-it + reliability-it |
| 02 | Projector表、AppointmentQueryIntegrationTest、API/ArchUnit | 常规；SQL不变通常不需重复全部容器 |
| 03 | enum/映射/原因契约、HTTP/事件兼容 | 常规 + mysql-it |
| 04 | OnDemandBookingHttpTest、幂等、跨零点、库存/就诊人竞争 | 常规 + 相关MySQL IT |
| 05 | 回调决策、T2/退款父锁竞争、资金/审计/Outbox回滚、真实回调Broker停机 | 常规 + mysql-it + reliability-it |
| 06 | module interface、MANDATORY/回滚、序号/占用、全调用者 | 常规 + mysql-it + reliability-it |
| 07 | lifecycle Policy、取消/过期/对账、Inbox原子性 | 常规 + mysql-it + reliability-it |
| 08 | 两组旧调用迁移、dev/test/prod路由映射、正式端到端 | 常规 + mysql-it + reliability-it |
| 09 | 架构负例、原覆盖对照、消息提交/ACK故障窗口 | 三层全量，零跳过 |
| 10 | 当前契约与实现核对、链接与任务字段/依赖 | 复用09同代码基线报告；文档变化不重复全部测试 |

确切命令：`./mvnw test`、`./mvnw -Pmysql-it verify`、`./mvnw -Preliability-it verify`。两个集成profile分别执行，均同时运行常规测试；不能把总数重复相加。缓存可用时可加`-o`；新增依赖需要按环境重新获取，不以跳过测试应付离线失败。

## 本轮只读复核的实际测试

执行日期2026-11-08，以下针对未修改的生产代码基线；测试只产生构建产物和隔离容器数据，不修改本地业务库。本轮没有为尚未实施的目标类型编写或运行新测试。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `./mvnw -o test` | 98通过，0失败，0错误，0跳过 | H2/application/HTTP/契约与ArchUnit常规测试，不能全部称作H2测试 |
| `./mvnw -o -Pmysql-it verify` | 常规98 + MySQL24通过，均无失败/跳过 | 包括真实回调/取消/过期、库存和迁移约束；部分用例仍驱动旧同步路径 |
| `./mvnw -o -Preliability-it verify` | 常规98 + 双容器9通过，均无失败/跳过 | 6项Outbox + 3项确认/退款可靠性；不是所有故障窗口的证明 |

98项与当前四场次验证报告一致；旧支付交付报告83项为历史结果。日志含Broker停机注入产生的预期重连错误，最终构建均成功。潜在支付锁环仅有静态证据，本轮没有添加确定性死锁复现；迟到残留暂占由原 H2 测试记录为历史基线，现已按已确认决定修改对应断言。

## 独立交付与回退

- 每项issue作为一个可构建、可验收的提交单元；可包含同一interface家族所需的全部调用者，禁止留下半迁移或永久双轨。
- 05锁序修正和回调提取一起验收；不能只回退一个锁方法使其余路径采用不同父子次序。
- 06完成时清除旧低层锁/库存interface；Legacy专用markPaid明确限期保留至08，不算第二套正式生命周期入口。
- 本轮任务均无schema变化；回退代码不删除资金、Outbox/Inbox或退款数据。下游已依赖时按逆拓扑顺序回退或做编译兼容调整，不承诺任意抽掉上游仍能构建。
- 已有业务缺口与重构回归分开登记，不通过改弱测试/增加隐藏兼容分支掩盖失败。

## 已确认的业务修订

迟到成功事件须同事务过期、释放暂占并确保退款；成功事件消费采用三次处理、每次重试间隔一秒及 DLQ，重试转发确认成功后 ACK。历史数据只读盘点，另行决定修复。新增 issue 11、12；推荐顺序为 01→11→12→02→03→04→05→06→07→08→09→10。任务尚需逐项验收，旧表中“待决”描述以本节为准。

## 完成状态

- [x] 完整读取指定规范、spec、ADR与支付历史资料
- [x] 核对重点源码、SQL、H2/MySQL/RabbitMQ和ArchUnit测试
- [x] 修订spec中的不准确描述及可实施性/事务/锁序风险
- [x] 创建map及01–10完整issue，核对依赖与回退
- [x] 运行现有三层基线并记录真实结果
- [ ] 实施01（本轮未授权生产改造，尚未开始）
- [ ] 后续实施02–10及各自验收

## Comments

- 2026-11-08：完成方案复核与本地issue拆分；全部issue未认领。明确ADR0005现行规则、支付反向锁风险、最终日期复验、入口短路/退款原因/查询权限，以及迟到事件残留暂占和普通消费重试限制；删除旧端点范围已获用户授权。
- 2026-11-08：本轮常规98、MySQL24、双容器可靠性9项全部通过，零跳过；这是当前实现基线，不是目标改造已验收。生产代码、测试源码、schema和历史ADR未改。

- 2026-11-08：文档结构校验通过：10项issue必填字段、连续编号、初始状态、依赖无环、地图收录、本目录Markdown本地链接和代码围栏均通过；Git状态仅本feature目录未跟踪，无生产/测试源码改动。

- 2026-11-08：开始 issue 11，迟到事件处理和聚焦 H2 测试已改动；该 issue 仍为 claimed，基线矩阵、独立事务回滚、MySQL/RabbitMQ 验收和历史只读盘点未完成。issue 12 和四模块结构改造尚未实施。

- 2026-11-08：继续推进 02、04、05、11、12，均处于 claimed；完成迟到支付和有界重试首轮实现、查询拆分、预约创建局部整理和支付父锁调整。三层门禁须基于最终代码重新执行，尚不标记 resolved。

- 2026-11-08：最新生产代码串行验证：常规 124 项、MySQL 24 项、双容器可靠性 10 项通过；随后补充不同 eventId 重复迟到支付的断言，并单独运行对应 H2 测试通过。历史业务数据库不可用，候选 SQL 尚未执行。完整 01–12 任务尚未完成。

- 2026-11-08：Compose 三服务健康，独立数据库和 vhost 的真实业务验收通过；阶段 1 暂存快照常规 98、MySQL 24、双容器 9 项通过。跨零点占号测试及完整决策矩阵仍未完成，01 保持 claimed。
