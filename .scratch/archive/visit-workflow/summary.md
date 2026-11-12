# 到院签到与门诊接诊交付

## 阶段与交付

归档核对基线：`1e83c0c6bb4ddfcdfe22196d1309e7e0c04c5bef`，日期2026-11-12。以下记录阶段交付，当前行为由[项目状态](../../../docs/project-status.md)及其现行文档定义。

V10增加医生账号关联、DOCTOR及visit_records；用户当天签到，医生四场概览、名单、详情、开始与完成。共享VisitRules和同一就诊记录，查询不创建记录；所有动作不改变资金、预约、库存、号码和快照。交互需求已提升为正式需求文档，页面建议不表示已交付前端。

主要交付提交：5d49ff9（签到与医生接诊）。原issue的关闭记录提交列在下表，可能是验收文档提交，不等同于全部实现修改所在提交。

## 演进与边界

仅正常签到接诊主线：无爽约、回退、叫号、病历处方、推送；当天范围按日期，医生动作不另限当天或场次时间。旧设计里的新增/修改是实施历史。

## visit-workflow

历史文件提交范围：2026-11-11至2026-11-12（含后续文档复核，具体实现/验收时间以记录为准）。

原任务均已完成；下表保留编号、标题和状态关闭记录，原始全文按文末Git方法读取。

| 任务 | 标题 | 状态 | 关闭记录提交 |
| --- | --- | --- | --- |
| 阶段规格 | 已交付，见阶段背景与交付提交 | resolved | 以Git交付记录为准 |

### 验收与实施记录

下列记录属于该阶段；旧规则、旧类名和临时路径保持历史含义。

2026-11-11原交付常规241、MySQL36、可靠性15通过；后续专项curl92与三天、消息和测试整理分别拥有自己的检查点。详见证据索引。

## 证据与当前阅读

- [visit-workflow/curl-2026-11-11.json](evidence/visit-workflow/curl-2026-11-11.json).
- [business-flow.md](../../../docs/business-flow.md).
- [module-design.md](../../../docs/module-design.md).
- [scenario-coverage.md](../../../docs/testing/scenario-coverage.md).
- [verification-report.md](../../../docs/verification-report.md).

## 原文追溯

逐文件去向见[清单](../../archive-manifest.json)。原始路径与摘要由清单保存；例如：

```bash
git show 1e83c0c6bb4ddfcdfe22196d1309e7e0c04c5bef:.scratch/visit-workflow/spec.md
```

旧报告中的/tmp日志不承诺持久存在；历史通过不代表本次重跑。

历史详细说明：[visit-design-history](evidence/visit-design-history.md)。

历史详细说明：[visit-change-history](evidence/visit-change-history.md)。
