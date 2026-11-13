# 02 共享规则、事实与接口实现

Type: feature
Triage: ready-for-agent
Status: resolved
Blocked by: 01

依照[方案第2、3节](../spec.md#2-当前实现与改造位置)，扩展AppointmentLifecycleCase的endTime及全部构造点；VisitRules集中计算窗口；写入口按归属→状态→开放→截止验证，采样时间复用于签到记录；查询同一serverTime计算canCheckIn并追加两字段；增加4503/4504，重复动作沿用4502。

完成条件：聚焦规则和HTTP测试通过，查询/写入窗口一致，失败不写入；医生接诊资格、预约及资金库存保持原规则。数据库、REST路径及医生DTO保持方案定义，不增加新框架。

## Comments

- 2026-11-12：待实施，无实现或验收结果。
- 2026-11-13：用户明确要求开始改造；已核实基线与已保存方案，认领实现。提交和推送仍未授权。

- 2026-11-13：共享窗口、快照endTime、用户DTO和4503/4504完成；聚焦106例通过，详见verification/final-focused-suites.json。初次测试快照的byte[]比较已修正；完整回归由03执行。
