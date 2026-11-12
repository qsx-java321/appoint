# 文档归档整理核验

日期：2026-11-12（Asia/Shanghai）。整理前完整检查点：`1e83c0c6bb4ddfcdfe22196d1309e7e0c04c5bef`。本次仅修改文档与文档检查工具；生产代码、迁移、业务测试、构建和运行配置未变。未执行Maven、容器或业务HTTP验收，也没有修改凭据或清理业务资源。原未跟踪scripts/__pycache__/保留。

## 文件去向

原148文件：105份任务正文合并，42份证据迁移，1份检查工具提升。六份主题总结保存76项issue编号、标题、resolved状态及Git关闭记录提交；历史设计和验收的完整原文可按清单中的sourceCommit/source读取。关闭记录提交不代表该任务所有实现所在提交。

保留的JSON、SHA256、SQL逐文件原摘要一致；Markdown证据只调整导航并标明历史角色。正式验证报告、就诊设计与变化说明的整理前全文额外保留为历史快照，避免丢失独有诊断和实施细节。新需求、测试类清单和场景映射进入docs，当前规则无需读取已删除的任务正文。

## 实际执行

在根目录运行 `python3 scripts/check_documentation.py`，结果见[统计](results.json)。检查包括所有Markdown本地链接/锚点/围栏、REST操作覆盖、响应字段/record、JSON与HTTP示例、148文件Git来源/去向/摘要、360被测代码/配置/脚本摘要、56个当前测试类、32消息和18时间场景及54处显式测试方法引用。

另运行 `git diff --check`，并检查src/main、src/test、pom.xml、compose.yaml、HTTP示例和业务验收脚本均无变更。原始证据中的340/38/16与curl92逐项汇总一致，这只是历史持久证据复核，不是本次重跑结果。

隔离临时副本中注入五类错误：失效链接、证据内容改变、无效测试方法、生产源码偏离摘要、归档清单漏项；均被工具拒绝，详见[负向核验](negative-checks.json)。临时目录退出后自动删除，未在实际工作区注入故障。

## 业务语义人工核对

以下逐项对照当前源码与现行文档，未发现需要改变业务结果的冲突。源码链接为实际阅读入口；仅静态/人工复核，不扩称新的运行验证。

| 核对内容 | 结论 | 源码 |
| --- | --- | --- |
| 窗口与四场 | 今天至后天十二项、四固定时间，查询无生成副作用 | [BookingTimeRules.java](../../../../../src/main/java/com/qsx/appoint/catalog/model/BookingTimeRules.java) |
| 下单严格截止 | 必须早于场次开始前15分钟，Java与SQL都约束 | [ScheduleMapper.java](../../../../../src/main/java/com/qsx/appoint/catalog/mapper/ScheduleMapper.java) |
| 锁后准入 | 锁定场次和就诊人后采样，订单与支付复用创建/期限事实 | [AppointmentHoldServiceImpl.java](../../../../../src/main/java/com/qsx/appoint/appointment/application/AppointmentHoldServiceImpl.java) |
| 支付期限 | min(创建时间+配置时限,开始时间)，持久化后不随入口滚动 | [BookingTimeRules.java](../../../../../src/main/java/com/qsx/appoint/catalog/model/BookingTimeRules.java) |
| 收款与迟到退款 | paidAt为锁后服务端时间，严格截止；迟到事件按策略关闭释放并补偿 | [PaymentNotificationServiceImpl.java](../../../../../src/main/java/com/qsx/appoint/payment/application/PaymentNotificationServiceImpl.java) |
| 24小时取消 | 已确认预约仅严格早于开始前24小时可取消 | [BookingLifecyclePolicy.java](../../../../../src/main/java/com/qsx/appoint/coordination/application/BookingLifecyclePolicy.java) |
| 库存与序号 | confirmed/held转换和序号由预约模块维护；就诊只写自己的记录 | [DefaultVisitService.java](../../../../../src/main/java/com/qsx/appoint/visit/application/DefaultVisitService.java) |
| 签到与医生资格 | 签到按当天；医生动作按归属/已确认/进度，不附加场次时间 | [VisitRules.java](../../../../../src/main/java/com/qsx/appoint/visit/domain/VisitRules.java) |
| 消息恢复边界 | Outbox持锁发布与至少一次投递；重试、DLQ和人工恢复保持原边界 | [module-design.md](../../../../../docs/module-design.md) |
| Mock与通知 | dev/test提供Mock，退款结果拓扑存在但没有对应监听器 | [RabbitEventTopology.java](../../../../../src/main/java/com/qsx/appoint/payment/config/RabbitEventTopology.java) |
| 历史与当前 | 七天、旧模拟路由和旧页均属历史；当前不包含前端/真实渠道/病历处方 | [project-status.md](../../../../../docs/project-status.md) |

## 阅读与维护

从[README](../../../../../README.md)选择学习或运行维护路线；[归档索引](../../../../README.md)提供六组交付总结。[去向清单](../../../../archive-manifest.json)提供每份原文的完整Git追溯方式。

当前剩余差异：无已发现而未解决的业务/契约或导航差异。语义人工核对和静态工具不证明所有可能行为；本次没有浏览器、压测、真实资金渠道或新业务运行验收。Git提交与推送尚未执行。
