# 测试整理任务地图

Type: refactor
Triage: ready-for-agent
Status: resolved

[详细方案](spec.md) · [场景](scenarios.md) · [清单](verification/inventory.md) · [命令与结果](verification/commands.md)

| 序号 | 任务 | Status | Blocked by |
| --- | --- | --- | --- |
| 01 | [建立测试清单、场景映射和执行基线](issues/01-baseline.md) | resolved | none |
| 02 | [提高断言准确性并固定历史迁移边界](issues/02-assertions-migrations.md) | resolved | 01 |
| 03 | [整理时钟与业务夹具](issues/03-clocks-fixtures.md) | resolved | 02 |
| 04 | [完善隔离、故障恢复和有界等待](issues/04-isolation-concurrency.md) | resolved | 03 |
| 05 | [合并消息测试重复职责](issues/05-messaging-deduplication.md) | resolved | 04 |
| 06 | [减少测试启动和重复扫描](issues/06-startup-scan.md) | resolved | 05 |
| 07 | [完善可读性、规范和场景审计](issues/07-readability-audit.md) | resolved | 06 |
| 08 | [完整验收与文档收口](issues/08-acceptance.md) | resolved | 07 |

## 执行约定

按顺序逐项验收后提交，不推送。保留用户原有未跟踪缓存。生产缺陷单列，不通过弱化测试掩盖。

## 提交记录


- 01：新执行常规323、MySQL38、可靠性16全部通过，零失败/错误/跳过；文档链接、8项任务及57类清单通过。起点963051f。

- 02：聚焦常规10、真实MySQL4全部通过。首次CHECK断言发现实际包装为UncategorizedSQLException，改为精确验证3819/HY000/约束名后重跑成功。临时编辑脚本语法错误未改源码；此前预执行不计改造验收。

- 03：聚焦常规41、就诊MySQL1通过；可变钟统一为support配置，所有使用者每例重置，原UTC时点转换为等价上海时间。保留FormalPaymentTestSupport等已有夹具，不机械再抽象一次性SQL。

- 04：聚焦73通过；顺序A常规323+完整MySQL38通过，顺序B常规323+受影响MySQL32通过；完整可靠性两轮各16通过，A另验收19项收尾常规，B最终源码额外5项确认测试。全部零失败/错误/跳过。用户要求本步提交后暂停，05～08不启动。

## 当前进度与历史暂停

已完成 **8 / 8**，最终常规340、MySQL38、可靠性16及隔离curl92请求通过。用户曾要求04后休息，04验收提交后已停止；随后明确要求继续，现依序完成05～07。不把历史暂停描述当作当前状态。

## 已提交阶段

- 01：`7e72e36 docs: 建立测试整理任务与场景基线`
- 02：`bbbb6d0 test: 收紧异常断言并明确迁移测试边界`
- 03：`f46e49b refactor: 整理测试时钟与业务夹具`
- 04：`c1b7fb3 test: 完善测试隔离与并发资源恢复`
- 05：`048c9bf refactor: 合并重复消息测试并明确验证职责`
- 06：`861bb64 perf: 减少测试上下文与架构重复扫描`
- 07：`9c5878b docs: 完善测试规范与场景追踪`

第04步细节见 [隔离说明](verification/step-04-isolation.md) 与 [方法核对](verification/step-04-method-audit.md)。原有未跟踪 scripts/__pycache__/ 保留，未提交。

- 2026-11-12：用户要求继续，撤销第04步后暂停，依序实施05～08；已有验收不重复作为本轮最终结果。

- 05：聚焦85、完整可靠性16通过，零失败/错误/跳过。删除独立基线类和两个Listener各2项重复分支；独有断言迁移、新增I/O确认矩阵及S01～S32入口映射已登记。

- 06：完整常规340项通过，架构20项、MockPay契约7项保留；契约不启动Spring，生产扫描static final复用，三个profile保护完整。实际耗时及首次编译修正见startup-evidence.md。

- 07：聚焦常规111+MySQL10、确认单测5+Outbox可靠性9通过；260原方法全部有去向，19受保护样本/架构负例不变。补充类职责、去除重复注入/全限定噪音、统一架构正反例规则、验证故障实际调用，测试规范已落盘。临时清理脚本字段名检查中止后按实际jdbcTemplate修正，无生产修改。

- 08：本次最终提交 `docs: 完成测试整理与全流程验收`，具体哈希见Git日志。

[最终验收](verification/final-acceptance.md) · [逐方法审计](verification/method-audit.md) · [当前清单](verification/current-inventory.md) · [测试约定](../../docs/testing.md)。全部任务resolved，无后续必做项。

- 08：干净常规340、完整MySQL38、完整可靠性16、隔离curl92全部通过，零失败/错误/跳过；按日志筛选报告避免旧XML累计。数据库/账号/vhost精确只读复核均清理，应用端口关闭，Compose健康；原生产代码及360被测摘要一致。八项全部完成。
