# 测试与验证

本页保留测试职责、入口和实际验收范围。测试实现位于src/test及frontend/tests，HTTP操作示例位于[http目录](../http/)。固定数量与逐方法清单由源码和运行报告提供，不在文档中重复维护。

## 测试分层

| 层次 | 验证重点 | 位置或命名 |
| --- | --- | --- |
| 单元与Mock | 时间、状态策略、组装、传输分支 | 普通`*Test`及Mockito用例 |
| Spring与MockMvc | 装配、权限、真实事务和Mapper协作 | `@RegressionTest`，默认H2 |
| MySQL Testcontainers | 迁移、唯一约束、行锁和并发 | `*MySqlIT` |
| RabbitMQ可靠性 | 发布确认、重投、重试、DLQ及提交/ACK窗口 | `*ReliabilityIT` |
| Redis Testcontainers | 命中、事务绕过、失效和故障回源 | `*RedisIT` |
| 真实HTTP | 注册、预约、支付退款和就诊闭环 | `scripts/compose_acceptance.py` |
| 前端 | 规则、会话、请求和浏览器页面流程 | `frontend/tests/unit`、`frontend/tests/e2e` |
| 架构 | 模块依赖、只读查询和Entity泄漏 | architecture测试及违规fixture |

H2不替代MySQL锁行为；手工调用消费者不能证明Broker投递，渠道受理或发布确认不能证明最终业务完成。验收应核对订单、资金、库存及消息事实。

## 运行入口

在项目根目录按需串行执行；前端命令在frontend目录执行：

```bash
./mvnw clean test
./mvnw -Pmysql-it verify
./mvnw -Preliability-it verify
./mvnw -Predis-it verify
python3 scripts/compose_acceptance.py --http-client curl --http-report /tmp/appoint-http-report.json
```

容器测试需要Docker和对应镜像；Maven依赖已缓存时可加`-o`。集成profile会重复运行常规测试，统计时分开记录。前端基础检查为`npm run typecheck`、`npm test`、`npm run build`；浏览器流程和隔离启动入口见frontend/tests/e2e及scripts/frontend_compose_acceptance.py，运行配置须与目标环境映射一致。

## 数据与时间隔离

- 回归配置显式加载src/test/resources/regression/application.yml，容器连接由测试注入；业务测试不覆盖日常数据库连接，不假定所有夹具都回滚。
- MockMvc事务可验证同步协作；跨线程、真实HTTP和传播边界保留实际提交。已提交夹具按新增ID清理，不重置共享序列。
- 每例重置业务Clock；需要到院时间时使用测试时钟，手动体验使用test的[调时命令](environments.md#test业务调时)，不修改系统时钟。
- 资金测试沿用正式attempt、签名通知、Outbox/Inbox和原refundNo；先完成异步工作再停止实例，MockPay内存状态不跨重启保留。
- 故障桩、连接、队列及Broker操作结束后恢复。关键断言验证业务事实及明确错误，异步使用条件等待。

## 已完成的验收

2026-11-15基于正式profile和真实Compose完成三环境约定范围的业务验收：

| 环境 | 范围 | 结果 |
| --- | --- | --- |
| test | 注册至完成接诊，支付失败重试、取消退款、超时、迟到支付和并发抢号等分支 | 20组接口检查、253次接口请求及1项完整浏览器流程通过 |
| dev | 注册至付款、预约确认取号，停止于待签到 | 6组页面检查通过 |
| demo | 预置账号、医生绑定及注册至付款、预约确认取号 | 预置检查及6组页面检查通过 |

均核对库存、支付、消息和归属；最终页面异常为0。demo验收前后dev/test关键业务记录投影指纹一致。首次test固定时钟断言和dev登录衔接脚本失败均已修正并完整复验，原证据位于Git提交`7b8a370`。

验收结果仅适用于记录时的代码版本；上述三环境验收使用MockPay，不包含真实资金渠道、故障注入与完整移动端覆盖。完整历史结果按原提交读取，新增执行另行记录。

## 文档检查

```bash
python3 scripts/check_documentation.py
```

该命令只读检查本地链接、代码围栏、JSON示例及当前REST/DTO文档契约，不连接数据库或启动服务。历史计划、测试数量和原始报告通过Git追溯，不作为当前文档检查依赖。
