# 任务入口与历史交付档案

## 在途任务

暂无环境改造在途任务。

## 最近交付

- [demo预约成功主流程实测](archive/demo-booking-success/summary.md)：预置账号及页面6组检查、35次HTTP通过；demo保持运行，dev/test停止。[操作说明](../docs/demo-booking-success.md)。

- [dev预约成功主流程实测](archive/dev-booking-success/summary.md)：真实页面6组检查、36次HTTP通过；保留已付款、已确认、待签到的成功预约，dev继续运行，test已停止。[操作说明](../docs/dev-booking-success.md)。

- [正式test环境业务流程实测](archive/test-business-workflows/summary.md)：20组接口检查、253次接口请求（含3次并发）、1项真实浏览器完整流程通过；[人工体验清单](../docs/test-business-workflows.md)。

- [dev/test/demo环境改造](archive/environment-isolation/summary.md)：三环境配置与数据隔离，已完成。

- [业务前端与真实Compose联调](archive/frontend/summary.md)：代码交付`e91d870`，14类页面、22项单元、10项浏览器回归及139次真实HTTP/SQL核对通过；原始任务保留，验收证据已归档，未推送。

- [Redis目录缓存交付](archive/redis-catalog-cache/summary.md)：医院、科室、医生共享缓存、提交后失效、故障回源与隔离验收已完成；常规389、Redis7、MySQL38、可靠性16及HTTP97通过。

- [签到时间窗口改造](visit-checkin-window/spec.md)：[开诊前30分钟,场次结束)规则已实现；聚焦106、常规381、MySQL38、可靠性16及curl92通过，文档与工具同步。[任务地图](visit-checkin-window/map.md) · [实际验收](visit-checkin-window/verification/report.md)。实现交付`d75f73c`，未推送。[交付记录](visit-checkin-window/delivery.md)；证据已归入到院接诊档案，原方案/任务保留。

## 已交付档案

当前开发阶段已交付，当前能力、运行和学习入口见[项目状态](../docs/project-status.md)。这里保存阶段总结和验收证据；历史命令、数字、临时资源状态按原检查点理解。未来任务继续在 `.scratch/<feature-slug>/` 建立，完成后按[issue约定](../docs/agents/issue-tracker.md)归档。

| 主题 | 交付档案 |
| --- | --- |
| demo预约成功主流程 | [总结](archive/demo-booking-success/summary.md) |
| dev预约成功主流程 | [总结](archive/dev-booking-success/summary.md) |
| 正式test业务流程实测 | [总结](archive/test-business-workflows/summary.md) |
| dev/test/demo三环境 | [总结](archive/environment-isolation/summary.md) |
| 业务前端与真实Compose联调 | [总结](archive/frontend/summary.md) |
| Redis目录缓存交付 | [总结](archive/redis-catalog-cache/summary.md) |
| 到院签到与门诊接诊交付 | [总结](archive/visit-workflow/summary.md) |
| 消息代码设计交付 | [总结](archive/messaging-design/summary.md) |
| 四场次与预约时间交付 | [总结](archive/booking-time/summary.md) |
| 预约基础与目录交付 | [总结](archive/booking-foundation/summary.md) |
| 支付与预约生命周期交付 | [总结](archive/payment-lifecycle/summary.md) |
| 测试与文档收尾交付 | [总结](archive/delivery-quality/summary.md) |

## 原文追溯

[逐文件清单](archive-manifest.json)记录原文件的摘要、去向及Git检查点（后续合并记录位于additionalEntries）。查询例子：

```bash
git show 1e83c0c6bb4ddfcdfe22196d1309e7e0c04c5bef:.scratch/three-day-booking-time/spec.md
```

## 核验

在项目根目录运行 `python3 scripts/check_documentation.py`。新执行的文档整理核验见[整理报告](archive/delivery-quality/evidence/documentation-consolidation/report.md)。历史业务结果见[验收入口](../docs/verification-report.md)。
