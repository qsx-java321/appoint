# 第04步隔离与等待设计

## 适用边界

- `CommittedFixtureScope` 仅用于类独占的 H2 或 MySQL 容器，方法串行。每例开始记录既有账号、消息及医生/场次/预约设置快照；结束按新增账号、事件ID和Inbox主键清理，并恢复目录快照。不会包裹、回滚或替代被测业务事务，不重置自增序列。
- 普通HTTP就诊人测试改为回滚；预约幂等、生命周期、支付尝试及原子性测试继续观察真实提交。
- `OnDemandBookingHttpTest` 的 NOT_SUPPORTED 用例按本例patientId/userId清理；普通用例仍回滚。管理并发用例保留接口删除断言，finally按本例随机名称清理。
- 原目录快照不依赖seed ID、容量和迁移总数。辅助类不是共享环境清库工具，禁止用于开发数据库或并行测试。

## 容器和消息

- Spring类级容器使用AFTER_CLASS销毁上下文；原PatientJourneyHttpMySqlIT已有此约定，保持。
- 两个可靠性类各自拥有MySQL/RabbitMQ容器。`RabbitReliabilityFixtures` 停止监听并等待完成，再清重试队列、业务队列和DLQ；清理本例数据库行后恢复监听。
- 原用例中途全删Outbox迁到每例统一清理，避免批量发布读取其他夹具。
- Broker命令使用容器内30秒timeout并检查退出码；stop_app与binding移除放在try内，恢复放finally。
- 退款故障桩移除前先停监听；清理与恢复失败直接使测试失败，不吞异常。

## 线程和等待

- 原无界屏障改10秒，Future结果改15秒；保留已有更短且明确的持锁检查。
- `TestExecutor.close`取消未完成工作并等候15秒，超时失败，避免Java21默认close无限等待。
- 普通MySQL并发容器加innodb-lock-wait-timeout=10，为JDBC不可中断的行锁等待提供数据库侧上限；仍保留原SELECT FOR UPDATE/NOWAIT和父锁先于子锁的验证。
- 异步消息一般最多30秒；重试TTL检查原10秒窗口和900ms下界保留。

## 验证约定

顺序A：类alphabetical、方法MethodName。顺序B：类reversealphabetical、方法Random、随机种子20261112。两类顺序的实际命令和结果写入commands.md。完整可靠性分别使用两种顺序执行，不通过意外跳过获得通过。

## 第04步历史暂停要求（后续已恢复并完成）

以下仅记录当时要求；当前八项已完成，见[任务地图](../map.md)。用户在第04步实施期间要求：第04步验收并提交后停止。05～08不实施；本次不宣称全八步完成，也不执行08的Compose全流程验收。提交后的状态以map.md为准。
