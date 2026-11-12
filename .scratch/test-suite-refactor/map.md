# 测试整理任务地图

Type: refactor
Triage: ready-for-agent
Status: claimed

[详细方案](spec.md) · [场景](scenarios.md) · [清单](verification/inventory.md) · [命令与结果](verification/commands.md)

| 序号 | 任务 | Status | Blocked by |
| --- | --- | --- | --- |
| 01 | [建立测试清单、场景映射和执行基线](issues/01-baseline.md) | resolved | none |
| 02 | [提高断言准确性并固定历史迁移边界](issues/02-assertions-migrations.md) | resolved | 01 |
| 03 | [整理时钟与业务夹具](issues/03-clocks-fixtures.md) | ready-for-agent | 02 |
| 04 | [完善隔离、故障恢复和有界等待](issues/04-isolation-concurrency.md) | ready-for-agent | 03 |
| 05 | [合并消息测试重复职责](issues/05-messaging-deduplication.md) | ready-for-agent | 04 |
| 06 | [减少测试启动和重复扫描](issues/06-startup-scan.md) | ready-for-agent | 05 |
| 07 | [完善可读性、规范和场景审计](issues/07-readability-audit.md) | ready-for-agent | 06 |
| 08 | [完整验收与文档收口](issues/08-acceptance.md) | ready-for-agent | 07 |

## 执行约定

按顺序逐项验收后提交，不推送。保留用户原有未跟踪缓存。生产缺陷单列，不通过弱化测试掩盖。

## 提交记录


- 01：新执行常规323、MySQL38、可靠性16全部通过，零失败/错误/跳过；文档链接、8项任务及57类清单通过。起点963051f。

- 02：聚焦常规10、真实MySQL4全部通过。首次CHECK断言发现实际包装为UncategorizedSQLException，改为精确验证3819/HY000/约束名后重跑成功。临时编辑脚本语法错误未改源码；此前预执行不计改造验收。
