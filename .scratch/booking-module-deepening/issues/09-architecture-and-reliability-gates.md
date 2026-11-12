# 09 架构、并发与消息可靠性最终门禁

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。12项已于2026-11-09交付。后续三天窗口、visit、消息设计及测试整理已完成；正文草图、旧类名及当时数量仅解释该检查点。 当前能力与进度见[项目状态](../../../docs/project-status.md)。

Type: test
Triage: ready-for-agent
Status: resolved
Blocked by: 02, 07, 08, 11, 12

设计依据：[spec](../spec.md)，执行位置：[map](../map.md)。初始 Status 使用 triage 标签表示待认领；开始实施改为 claimed，验收完成改为 resolved。Triage 不代表依赖已解除。

## 背景和目标

四模块实现和Legacy清理已完成，需要把新的深interface固定为测试表面，补齐现有架构规则的缺项和可靠性测试窗口。不能把只检查注解当作完整只读证明，也不能把预置Outbox的Broker测试当作真实回调停机验证。

## 范围

- 扩展ModuleDependencyTest：coordination不得依赖两个写模块implementation、公开application返回值不得泄漏Entity、Controller不得直接用Mapper、query Mapper只读。
- 保留合法HTTP Adapter→coordination及稳定enum/DTO依赖，限定写业务层规则，不一刀切禁止整个payment包引用coordination。
- 规则有故意违规fixture/反例验证；只读检查覆盖现有Mapper全部方法/注解/可继承写接口等，避免只检查方法名称。
- 保留MySQL真实竞争、条件更新、唯一约束、SKIP LOCKED，以及RabbitMQ confirm/return、崩溃重复、DLQ和退款重试。
- 补消费者提交前失败、提交后ACK前失败的可控故障窗口，验证重投单副作用；补05尚未覆盖的真实回调Broker停机链路。
- 清理本次改造造成的重复浅调用测试；没有此类旧测试就不为达成删除数量而删测试。

## 非范围

- 不用ArchUnit声称能分析所有字符串常量/SQL语义；本轮状态字面量通过聚焦rg、映射测试及review验证。
- 保留 issue 12 的有界重试与可靠转发，不新增其他重试产品机制。
- 不复活ADR0005取消的停诊写入口或停诊竞争用例。
- 不以H2替代MySQL锁/约束，也不以Mock替代RabbitMQ故障验证。

## 前置依赖

[02 查询流程投影与响应组装分离](02-booking-query-projection.md)；[07 跨模块生命周期决策与流程编排收口](07-coordination-process-manager.md)；[08 迁移支付测试并删除 dev/test 旧模拟路径](08-remove-legacy-payment-simulation.md)。
[11 迟到支付释放暂占](11-late-payment-release.md)；[12 消费有界重试与可靠转发](12-bounded-consumer-retries.md)。
只有全部前置任务 Status 为 resolved 才能开始；最终实施状态见本文 Status 和最新 Comments。推荐按map串行顺序执行，避免共享快照和调用点冲突。

## 需要修改的模块和主要文件

下列源码路径相对项目根目录；新增类型明确标注，implementation命名可沿用现有风格，不强制为改名搬文件。

- `src/test/java/com/qsx/appoint/architecture/ModuleDependencyTest.java`、架构违规fixture。
- `src/test/java/com/qsx/appoint/appointment/AppointmentLifecycleMySqlIT.java`
- `src/test/java/com/qsx/appoint/payment/{PaymentNotificationMySqlIT,PaymentAttemptMySqlIT,OutboxPublisherReliabilityIT}.java`
- `src/test/java/com/qsx/appoint/coordination/{PaymentConfirmationReliabilityIT,PaymentSucceededProcessorIntegrationTest}.java`
- 本轮新增Policy/深interface测试；`.scratch/booking-module-deepening/baseline.md`验证对照。

## 目标代码形态

行为测试跨公开module interface；Policy纯决策表；MySQL独立事务竞争；RabbitMQ真实发布/监听并以数据库最终事实断言。故障注入位于测试配置/传输seam，不为了测试把内部implementation设为public或在生产加入通用开关。新增规则对应明确禁止依赖，有真实反例触发失败。

## 改造后的业务逻辑

不改业务顺序。发布confirm后标记失败产生重复，消费提交前故障回滚Inbox与业务，提交后ACK前故障重投命中Inbox；退款失败仍保留预约终态和库存释放，三次既有重试后DLQ；重放保留eventId。普通成功消息的临时失败按 issue 12 有界重试，耗尽进入 DLQ。

## 必须保持的不变量

- 深interface不泄漏库存和Entity，query只读，写模块互不依赖。
- 库存、序号、退款与Inbox单副作用，资金/Outbox原子提交。
- 不跳过容器测试、不改弱断言，不用测试数量相同代替覆盖等价。
- 测试环境隔离，不访问开发/生产业务库。

## 可验证验收条件

- 每条新增架构规则至少有明确反例会失败；实际生产类全部通过。
- 01每项覆盖均保留或有新的行为测试替代，02查询和03映射兼容门禁不遗漏。
- 05父锁路径的MySQL竞争全通过，故障后可重试收敛，无永久等待或部分提交。
- 消费两个故障窗口证明Inbox/库存/序号只有一次副作用；Broker停机时真实回调提交，恢复后完成有效确认或已关闭迟到退款。
- 三层全量命令均通过、零跳过；报告区分已知业务缺口、缺少的保证和新引入回归。

## 应运行的测试

- `./mvnw -Dtest=ModuleDependencyTest test` 及架构负例。
- `./mvnw test`
- `./mvnw -Pmysql-it verify`
- `./mvnw -Preliability-it verify`
- MySQL/reliability profile分别执行，检查Failsafe报告实际用例及跳过数。

## 独立交付与回退

独立回退新增架构fixture/规则与测试调整即可，生产代码和数据不变；发现生产问题转对应issue修复，不能在门禁任务悄悄改业务。
后续任务已使用本任务成果时，先逆序撤销依赖，或做兼容性调整；独立回退指本任务完成时可整体撤销，不保证任意抽掉已被下游使用的提交仍可构建。任何回退不得删除已提交资金、Outbox/Inbox或退款数据。

## 完成状态清单

- [x] 架构规则及违规反例完成
- [x] 01覆盖对照和浅测试整理
- [x] MySQL全部锁竞争通过
- [x] 真实消息故障窗口/重试/DLQ通过
- [x] 三层结果及已知限制记录

## Comments

- 2026-11-08：根据源码复核后的spec创建。仅完成任务定义，未认领、未实施；本轮基线结果见map，不等同于本issue验收结果。 依赖尚未resolved，暂不可开始实施。

- 2026-11-09：08已在b71c89f验收，依赖全部resolved。新增架构规则及故意违规fixture：写implementation（含未注解公开具体类型）、公开返回Entity及泛型、Controller→Mapper、query继承写接口/写注解/伪装SELECT写语句/锁定读；生产规则和反例聚焦16项通过。补真实消费者业务写后提交前故障窗口（测试配置装饰公开Processor，回滚后暂停以核实Inbox/库存/序号/占用，再放行实际Broker重试）；保留12提交后ACK前故障窗口与05Broker停机真实回调。RabbitMQ两份IT镜像对齐Compose的4.3.2。完整快照尚待验收。

- 2026-11-09：最终暂存树 `990dc71f121d17c7441621ebce9490727d3d4753` 导出 `/tmp/booking-09-snapshot-8eo2fl37`，串行执行 `./mvnw -o -Dtest=ModuleDependencyTest,BookingQueryServiceTest test`（18）、`./mvnw -o test`（233）、`./mvnw -o -Pmysql-it verify`（常规233+MySQL34）、`./mvnw -o -Preliability-it verify`（常规233+可靠性15）及 `python3 scripts/compose_acceptance.py`，全部通过、零失败/错误/跳过。按profile筛选XML核对，未把保留的MySQL报告累计为可靠性数量。RabbitMQ统一4.3.2；初次聚焦遇到非持久非独占测试队列被Broker拒绝，改为持久测试队列后通过，生产拓扑无改动。查询Clock测试已改为真实Assembler响应断言，重复只读浅检查由完整规则替代；01覆盖对照见baseline。源码/构建/验收281个文件与被测快照逐字节相同。日志 `/tmp/booking-09-final-{focused,test,mysql,reliability,compose}.log`；09 resolved，未改变生产行为或业务库数据。只读复核默认库V9、预约0、支付0、历史候选0。已知限制：真实支付未接入；保留查询与写入口权限差异及PROCESSING+CLOSED迟到补偿契约；测试不宣称证明任意负载/动态SQL。

- 2026-11-09：10文档收口核对：本issue原阶段独立验收证据保留；最终集成代码a61b808的常规233/MySQL34/可靠性15及Compose全部通过，零失败/错误/跳过，详见 [最终验证报告](../../../docs/verification-report.md)。09覆盖对照未遗漏本issue保证；此处不以最终数量改写原阶段结果。
