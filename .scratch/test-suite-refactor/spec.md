# 全项目测试代码整理与改造方案

Type: refactor
Triage: ready-for-agent
Status: paused

## 已确认目标和边界

依据用户 2026-11-12 确认的八阶段方案执行。目标是断言准确、职责清晰、隔离可靠、减少重复和无用启动；不以测试数量或行数下降作为成功标准。仅改测试源码、测试配置与文档，不改生产业务、迁移、消息契约、队列与生产配置。发现生产缺陷保留复现并单建任务，受影响阶段不得宣告成功。Maven 串行；每阶段验收后单独 Git 提交，不推送。

## 分层与保留

普通对象测试负责策略、时间、投影、Codec 和传输分支；Spring/H2 负责真实装配、事务、服务和 MockMvc；MySQL Testcontainers 负责实际 SQL/约束/锁；MySQL+RabbitMQ 负责真实发布、重投、DLQ 和提交/ACK；隔离 Compose 负责实际启动及全流程；ArchUnit 负责依赖约束及违规负例。保留 *Test.java、*MySqlIT.java、*ReliabilityIT.java 和现有 profile，不增加子模块、依赖、通用测试框架或全局并行。

必须保留：幂等重放与冲突；账户与权限；并发防超卖和库存守恒；序号单调；三天窗口/截止/跨午夜/支付快照；验签/金额/币种/重复回调；Inbox/Outbox 原子性；Broker接收后回滚重发；提交后丢ACK；同退款号重试及渠道成功本地失败；重试/DLQ/受控重放；历史订单；医生绑定和接诊；prod禁Mock；架构规则正反例。同场景不同层验证不同风险，不能因同名删除。

删除先记录原行为、目标测试、profile/真实组件/事务等价与实际执行证据，无接收位置默认保留。历史 JSON 不能由新 DTO 重新生成期望。

## 实现约束

- 数据库异常具体到数据完整性/重复键，必要时根 SQLState/错误码/约束名，配合失败后数据断言；不绑定完整报错文本。
- 最新初始化验证最终结构与再次迁移为零，不硬编码迁移总数；旧阻断固定 V2/V4、legacy V5、就诊 V9→V10。
- 可变业务钟每例重置、上海时间；解除 VisitWorkflowMySqlIT 对 VisitHttpTest 内部类依赖。
- support 仅提取真实重复准备操作，用明确ID/record，保留 FormalPaymentTestSupport 的真实支付流程；不造巨型基类，历史版本夹具独立。
- 回滚不代替提交/并发测试。专属 H2、类级容器、精确清理；容器对应 Spring 上下文类后关闭。
- 消息扫描全库/固定队列的类在独占容器内统一清理；停监听等待在途完成后清理再恢复。Broker、binding、spy故障均finally恢复，失败不能吞。
- 屏障10秒、结果15秒，Broker/异步一般30秒；保留有依据的特殊窗口，超时释放连接/持锁/线程，不削弱锁顺序断言。
- Listener 保留业务接线、成功ACK不读重试头、拒绝、各自路由、ACK异常；公共处理器承接完整传输矩阵。
- MockPay 契约改对象测试，profile 禁用/路由仍真实 Spring；架构扫描复用且不包含负例。

## 执行与完成条件

见 [任务地图](map.md) 和八项 issue；所有步骤顺序执行。每步实现→聚焦测试→场景核对→证据→提交。最终完整串行 ./mvnw clean test、./mvnw -Pmysql-it verify、./mvnw -Preliability-it verify、python3 scripts/compose_acceptance.py。缓存可用时 -o。报告重复常规不重复加总，零未经解释跳过；未执行不算通过。核对生产JAR不含测试注入、src/main与生产配置无改动，隔离验收资源清理。不提交凭据、target、运行日志或原 scripts/__pycache__/。


## 当前执行状态

用户要求第04步验收提交后暂停；01～04已完成，05～08未开始。当前结果见map.md与verification/commands.md，不代表08最终四类验收完成。
