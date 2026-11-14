# 测试分层与编写约定

本项目用自动化测试保护预约、支付、消息和接诊业务。测试文件数量不代表保障强度；整理时优先确保断言准确、失败能定位、用例可独立运行，再减少重复和启动成本。

当前类与隔离见[测试清单](testing/inventory.md)，场景与入口见[覆盖映射](testing/scenario-coverage.md)，历史执行结果见[验收记录](verification-report.md)。

## 测试分层与入口

| 层次 | 主要责任 | 典型位置 |
| --- | --- | --- |
| 普通对象测试 | 时间边界、策略、投影、编解码和传输分支 | VisitRulesTest、VisitServiceTest、BookingTimeRulesTest、BookingLifecyclePolicyTest、MessageConsumerContractTest |
| Spring＋H2 | 实际装配、MyBatis读写、事务传播与回滚、MockMvc权限与接口 | PaymentOutboxTransactionTest、AppointmentLifecycleInterfaceTest、VisitHttpTest |
| MySQL容器 | 实际迁移、唯一键、外键、行锁及竞争 | AppointmentLifecycleMySqlIT、PaymentParentLockMySqlIT |
| MySQL＋RabbitMQ容器 | Broker故障、confirm、重试、DLQ、提交与ACK窗口 | OutboxPublisherReliabilityIT、PaymentConfirmationReliabilityIT |
| 真实HTTP | 实际启动配置及注册到支付、退款、接诊闭环 | PatientJourneyHttpMySqlIT、compose_acceptance.py |
| 架构 | 模块边界、Entity泄漏、只读查询规则及规则负例 | ModuleDependencyTest、archfixture |

`IntegrationTest`并不自动表示真实MySQL。H2可以验证Spring事务协作，不能替代MySQL的锁、CHECK错误分类和SQL行为。相同业务在不同层验证不同风险时应保留：对象测试验证规则，Spring测试验证事务，Rabbit测试验证实际重投。

普通测试保持`*Test.java`，MySQL测试保持`*MySqlIT.java`，可靠性测试保持`*ReliabilityIT.java`。不新增全局并行或共享容器机制。

## 运行与验收

在项目根目录串行执行：

```bash
./mvnw test
./mvnw -Pmysql-it verify
./mvnw -Preliability-it verify
python3 scripts/compose_acceptance.py --http-client curl --http-report /tmp/appoint-http-report.json
```

两个集成profile都会再次运行常规测试，统计时不能重复相加。常规测试使用H2或对象替身；容器测试需要Docker与对应镜像。完整验收先执行`./mvnw clean test`，避免旧编译产物造成误判。依赖已缓存时可以加`-o`；HTTP验收脚本内部使用Maven离线模式。

开发时只运行相关测试，例如：

```bash
./mvnw -Dtest=MessageConsumerContractTest,RabbitConsumerFailureHandlerTest test
./mvnw -Pmysql-it -Dtest=PaymentOutboxTransactionTest -Dit.test=PaymentParentLockMySqlIT verify
```

不能用`-DskipTests`完成门禁；它可能把待验收的集成测试一并跳过。报告明确区分未执行、失败和跳过，环境不满足时不能宣布成功。

## 数据、事务与时钟

- `src/test/resources/regression/application.yml`由`@RegressionTest`显式加载，Maven和IDE使用相同配置根；默认使用H2内存数据库，并关闭后台扫描和消息业务监听。真实容器连接由`DynamicPropertySource`注入；数据库名字相同也不代表连接同一台服务。
- 不要通过环境覆盖把测试数据源指向日常业务数据库。部分测试会真实提交和删除夹具，并非所有用例都依靠回滚。
- 同步MockMvc普通写入测试可以使用测试事务回滚。跨线程、真实HTTP、事务传播或提交窗口测试必须保留被测事务边界，不能为了方便清理套一层外部事务。
- `CommittedFixtureScope`仅供方法串行、类独占数据库且每例新建业务账号的测试使用。它按新增ID清理业务、事件和审计行，恢复医生、场次和设置快照；不重置序列，不是任意业务表的通用备份还原器。
- 修改已有业务账号的测试应显式还原相应字段；不能假定夹具清理器会恢复所有业务数据。普通精确ID清理继续保留。
- 类级容器配合Spring上下文时，类结束关闭对应上下文，避免缓存继续访问已停止的容器。迁移用例使用自己的空库或固定历史版本库。
- 可变业务钟统一使用`MutableAppointmentClock`和`AppointmentClockTestConfiguration`。每例在`BeforeEach`设置上海本地时间，例如`2026-11-12T08:00:00`；不要继承上个用例的时间。

测试类和辅助配置放在`src/test`，不会进入正常生产JAR，也不会在普通应用启动时自行执行。手动向日常运行中的应用发送HTTP请求，则会使用该应用真实配置的数据源。

## 测试支持类的边界

| 支持类 | 负责 | 使用限制 |
| --- | --- | --- |
| FormalPaymentTestSupport | 真实尝试、Mock验签、Outbox与业务Processor | 直接调用消费者，不证明Rabbit传输正常 |
| BookingTestDoctors | 创建独立医生隔离测试场次 | 不是生产医生编号分配策略 |
| CommittedFixtureScope | 清理本例提交的数据并恢复指定目录快照 | 仅类独占测试数据库，禁止并行方法 |
| MutableAppointmentClock | 控制上海业务时间 | 测试侧调时能力，不加入生产接口 |
| TestExecutor | 有界关闭测试线程池 | 不改变原屏障、持锁与结果断言 |
| RabbitReliabilityFixtures | 停监听后清理数据库夹具和固定队列 | 仅独占Rabbit/MySQL容器 |
| RabbitTestBroker | 有界Broker故障注入及退出码检查 | 恢复放在finally |
| MySqlConstraintAssertions | 精确识别当前驱动CHECK错误3819 | 不接受任意数据库异常 |

只提取真实重复的准备操作。特殊输入、关键调用、故障点和业务断言保留在用例中；不建立巨型BaseIntegrationTest，不给所有测试工具增加interface。历史版本SQL夹具不能被最新模型的构造器替换。

## 消息测试职责

- `PaymentOutboxTransactionTest`使用Spring真实ObjectMapper与事务，验证四类生产事件、Long/null契约及写入回滚。
- `MessageConsumerContractTest`读取原八份v1样本，覆盖两种ID表示、额外字段和非法输入。期望样本不能用新DTO反向生成。
- 两个Listener测试各自保留正文解码、Processor/Worker调用、成功ACK、拒绝、固定路由与计数头、ACK异常传播。对象测试只能证明成功返回，不能声称真实数据库已经提交。
- `RabbitConsumerFailureHandlerTest`集中验证重试次数、原正文/ID、TTL、confirm先于ACK、转发失败NACK和确认I/O异常。真实TTL、DLQ和提交窗口继续由容器验证。
- `DeadLetterReplayContractTest`验证逻辑目标、原消息保留和确认顺序。退款结果没有消费者，不能把转发成功说成通知业务完成。

当前S01～S32责任入口见[覆盖映射](testing/scenario-coverage.md)。

## 断言与故障注入

数据库约束测试不能只断言`Exception`。唯一键用重复键异常；外键等用实际数据完整性异常；MySQL CHECK在当前驱动下是未分类SQL异常，应检查3819及具体约束名。失败后同时核对金额、状态或行数没有改变，不绑定完整错误文本。

故障测试要证明到达目标位置。例如：

- 审计/Outbox写入失败：验证对应Mapper确实被调用，再检查支付、尝试和事件一起回滚。
- 渠道成功、本地退款落库失败：验证真实Mock渠道返回成功，再验证markSucceeded故障点及同一refundNo重试。
- Broker接收、Outbox标记失败：先接收真实消息，再证明整批数据库标记回滚，重发原ID只产生一次确认。
- 提交后丢ACK：保留真实channel关闭与redelivery，不用“手工调用Processor两次”替代真实重投。

MyBatis Mapper是接口代理，故障恢复应撤销stub并恢复其原委托；不能用`callRealMethod()`调用抽象Mapper方法。普通具体类spy的真实方法调用与此不同。

故障桩、持锁连接、binding、Broker及临时队列在finally恢复。监听器未结束时不要清理其业务行。清理失败使测试失败，不吞异常。数据库并发屏障通常10秒、结果15秒；异步Broker恢复通常30秒。保留有依据的特殊窗口，并使用条件等待而非固定睡眠猜测完成时间。

## 可读性、架构和删减规则

类注释说明验证层次及隔离方式；方法名表达业务结果。只对事务、时间边界、并发和故障点添加必要解释，不机械给每行代码写注释，也不按行数硬拆文件。

架构正例扫描仅复用生产类集合。违规fixture单独导入，正反例使用同一条规则；不能为了让扫描更快缩小生产范围或删除负例。dev、test、prod的装配保障分别保留。

删除或合并前记录原行为、接收测试、真实组件/profile/事务条件及实际结果。稳定存储名称属于契约，枚举声明顺序通常不属于。数量减少不是验收目标；无接收位置的独有断言默认保留。

## 文档维护检查

在项目根目录运行 `python3 scripts/check_documentation.py`。检查覆盖REST/DTO、测试入口、链接和归档证据，不替代业务测试。

签到窗口W01～W12见[场景覆盖](testing/scenario-coverage.md#签到窗口)。聚焦测试可执行`./mvnw -o -Dtest=VisitRulesTest,VisitServiceTest,VisitHttpTest,AppointmentResponseAssemblerTest,BookingQueryServiceTest,AppointmentLifecycleInterfaceTest,BookingLifecyclePolicyTest test`。文档工具以Git检查点`9c5878b`验证旧360文件摘要；本任务逐用例摘要、HTTP报告及源码清单独立核验，源码漂移会显示`sourceMatchesWorkingTree=false`，不将旧通过结果认作当前代码验证。

## Redis目录缓存验证

`./mvnw -Predis-it verify` 执行 `*RedisIT.java`，使用独占MySQL和Redis容器验证缓存及事务，不连接开发库。该profile也重复运行常规测试，不重复累计数量。常规H2测试默认关闭目录缓存。

`CatalogDirectoryCacheTest` 验证数据库异常不被吞掉、缓存故障回源、损坏值、开关与事务绕过；`CatalogDirectoryCacheRedisIT` 验证实际TTL、九种目录写入、提交/回滚、并发旧值回填、两个独立应用共享缓存和Redis暂停故障。完整标准见[缓存方案](redis-catalog-cache-design.md)。

Compose验收显式开启缓存，每轮使用独立namespace，并在重启到测试Clock时复用该namespace。成功后停止应用，再按前缀SCAN并删除本轮键；其他namespace的守卫键必须保留。故障注入在独占测试容器执行，避免影响开发Redis。
