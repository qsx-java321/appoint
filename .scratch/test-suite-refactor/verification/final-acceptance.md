# 全项目测试整理最终验收

日期：2026-11-12（Asia/Shanghai）。01～08已按顺序实施，每阶段验收后独立提交，不推送。原生产基线963051f，最终测试源码检查点9c5878b；08只补验收报告、正式文档和状态。360个被测代码/配置/脚本摘要见code-manifest.sha256。

## 新执行结果

| 验收 | 实际命令 | 结果 | 日志 |
| --- | --- | --- | --- |
| 干净常规 | `./mvnw -o clean test` | 43类、340项通过 | `/tmp/test-refactor-08-unit.log` |
| 完整MySQL | `./mvnw -o -Pmysql-it verify` | 常规340、11类MySQL38通过 | `/tmp/test-refactor-08-mysql.log` |
| 完整可靠性 | `./mvnw -o -Preliability-it verify` | 常规340、2类可靠性16通过 | `/tmp/test-refactor-08-reliability.log` |
| 隔离HTTP | `python3 scripts/compose_acceptance.py --http-client curl --http-report .scratch/test-suite-refactor/verification/curl-report.json` | 92请求全部符合expectedStatus | `/tmp/test-refactor-08-compose.log` |

全部零失败、零错误、零跳过；profile重复常规不累计。逐用例结果保存在final-unit-suites.json、final-mysql-suites.json和final-reliability-suites.json，不把旧XML当作本轮结果。

## 改造与数量

- 常规323→340：移除基线1项、两个Listener重复4项，新增实际mapper1项、历史样本8项，公共失败矩阵9→22（+13），净增17。MySQL38、可靠性16保留。
- 测试类57→56；SpringBootTest标注文件33→31。生产扫描改类内一次复用，MockPay契约每例直接新建真实对象；三个profile保护保留完整Spring。
- 原与最终各260个注解方法，8个旧名称的合并/改名均有实际接收位置。04简化正则漏两个全限定注解，07已校正；不以方法数替代覆盖。
- 事务、并发、Inbox/Outbox、原ID重发、提交后丢ACK、退款恢复、权限和就诊断言保留。原八份消息样本及架构负例共19文件摘要不变。
- 收紧数据库异常和失败后状态断言；历史迁移明确版本；时钟每例重置；真实提交按新增ID清理；线程/故障恢复有界；故障注入验证实际调用。没有生产接口、业务规则、schema或拓扑变化。

## HTTP与资源

运行编号`277f4dc1aa1f`，状态分布`{200: 63, 201: 12, 202: 13, 403: 1, 409: 3}`。注册→预约→MockPay→真实Rabbit异步确认→签到→医生接诊完成，以及取消退款和迟到支付通过。用户/医生时间一致、就诊号与预约/支付/库存快照守恒由脚本实际断言。

第一阶段正常Spring Boot/真实MySQL/RabbitMQ；第二阶段同一隔离库配合测试专用业务Clock验证就诊日，不修改宿主时间，不直接改就诊状态。没有真实支付渠道或前端验收。

报告敏感字段递归脱敏。成功后的数据库`accept_277f4dc1aa1f`、账号`a_277f4dc1aa1f`、vhost`accept-277f4dc1aa1f`由脚本清理并独立只读确认均为0，见resources.json。验收应用已退出；Compose三依赖健康保留运行，旧业务库/数据卷和历史诊断资源未清理。

## 静态门禁与限制

生产273文件摘要与起点一致；src/main、pom.xml、compose.yaml及验收脚本没有变更。生产JAR不含support、架构违规样例、JUnit/Mockito/Testcontainers/ArchUnit/H2测试依赖。当前场景、方法、类清单及链接一致；本机/tmp日志用于诊断，持久证据为本目录统计、逐用例摘要、脱敏HTTP和源码摘要。

全套耗时受CPU和缓存影响，不承诺固定加速比例。此轮整理增强可维护性与失败定位，不宣称覆盖所有可能错误或替代压测。原有scripts/__pycache__/未跟踪缓存保留，实际.env/compose.env及target未提交。
