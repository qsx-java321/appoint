# 实际验证记录与证据入口

## 最新业务验收

2026-11-13签到窗口改造，实现交付`d75f73c`（基线`4bfc1d5`，验收摘要与该提交一致）：聚焦106、干净常规381、MySQL38、可靠性16通过，零失败/错误/跳过；隔离curl92请求状态全部符合预期。聚焦属于常规子集，集成profile重复常规不累计。

[本任务验收](../.scratch/visit-checkin-window/verification/report.md)保存命令、源码摘要、逐用例、HTTP和资源复核。服务已恢复停止、原卷保留；实现已本地提交为`d75f73c`，未推送。前次测试源码9c5878b的340/38/16/92结果独立保留在[历史验收](../.scratch/archive/delivery-quality/evidence/test-suite-refactor/final-acceptance.md)。归档见[签到窗口验收档案](../.scratch/archive/visit-workflow/evidence/visit-checkin-window/report.md)。当前边界见[项目状态](project-status.md)。

## 按日期追溯

| 日期/阶段 | 当时实际结果与限制 | 详细证据 |
| --- | --- | --- |
| 2026-11-09 四模块 | 最终常规233、MySQL34、可靠性15及Compose通过；不含后续就诊 | [基线及验收](../.scratch/archive/payment-lifecycle/evidence/booking-module-deepening/baseline.md) |
| 2026-11-10 HTTP及文档同步 | 实际注册到异步确认HTTP和文档核对；临时服务状态仅属当时 | [历史完整报告](../.scratch/archive/delivery-quality/evidence/project-verification-history.md) |
| 2026-11-11 到院功能 | 原交付241/36/15；另有专项curl92，具体运行和限制见原记录 | [就诊档案](../.scratch/archive/visit-workflow/summary.md) |
| 2026-11-11 三天窗口 | 常规263、MySQL37、curl92；本阶段未重跑reliability-it | [场景证据](../.scratch/archive/booking-time/evidence/three-day-booking-time/scenario-evidence.md) |
| 2026-11-12 消息设计 | 常规323、MySQL38、可靠性16、curl92；保持合法v1，收紧非法输入 | [消息验收](../.scratch/archive/messaging-design/evidence/messaging-design-refactor/scenario-evidence.md) |
| 2026-11-12 测试整理 | 常规340、MySQL38、可靠性16、curl92 | [最终验收](../.scratch/archive/delivery-quality/evidence/test-suite-refactor/final-acceptance.md) |
| 2026-11-12 文档对齐 | REST/DTO、链接、JSON和示例静态检查；未重跑业务测试 | [原复核记录](../.scratch/archive/delivery-quality/evidence/documentation-alignment/report.md) |
| 2026-11-12 文档归档整理 | 文件去向、证据摘要、当前契约与导航检查；未重跑业务测试 | [整理报告](../.scratch/archive/delivery-quality/evidence/documentation-consolidation/report.md) |
| 2026-11-13 签到窗口 | 聚焦106、常规381、MySQL38、可靠性16、curl92；实现交付d75f73c | [本任务验收](../.scratch/visit-checkin-window/verification/report.md) |

## 证据的适用范围

早期交付的全部独有执行记录、临时资源标识、失败修复和限制保存在[整理前报告快照](../.scratch/archive/delivery-quality/evidence/project-verification-history.md)。历史数字维持原检查点，不统一替换成最新数字。/tmp日志不承诺长期存在，持久证据以归档报告和摘要为准。

本地dev/test资金链路使用MockPay，实际HTTP不是前端验收，也不是接入真实支付。历史服务状态不能证明现在仍在运行；历史通过不是未来代码继续通过的保证。
