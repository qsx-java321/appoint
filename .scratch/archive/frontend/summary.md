# 业务前端与真实Compose联调交付

交付日期：2026-11-14（Asia/Shanghai）。代码、设计、测试及原始验收证据的交付检查点为`e91d870`（`feat: 新增Vue预约前端并完成真实Compose联调`），基线`87ad984`；本档案作为后续文档提交记录该检查点。仅本地提交，未推送、未部署。

## 目标与实际行为

在同仓库独立frontend/实现已认可的14类页面：邮箱账号、医院科室医生、三天十二场、就诊人、预约核对、支付退款与签到、医生接诊、管理目录/设置/首次绑定。Vue 3配套TypeScript、Vite、Router、Pinia和Axios，接既有REST；不修改Java业务规则、数据库迁移或引入真实资金渠道。

正常8080/5173和三个Compose依赖已启动，并通过Windows入口检查。完整真实验收使用独立MySQL数据库、RabbitMQ vhost及Redis namespace，测试入口固定业务时间以执行当天签到；正常入口仍使用真实时钟。成功和诊断结束的隔离资源已清理，正常开发服务在验收结束时保留运行。

## 阶段、任务与验证

| 阶段 | 任务 | 实际结果 | 原始说明 |
| --- | --- | --- | --- |
| Vue首版 | 01方案、02基础、03预约、04详情、05医生管理、06验收 | 22项单元、10项夹具浏览器、1项H2真实用户链路及类型/构建通过 | [需求](../../frontend-v1/spec.md)、[地图](../../frontend-v1/map.md)、[验收](../../frontend-v1/verification.md) |
| 真实Compose | 01启动、02真实业务、03回归与文档 | 139次真实HTTP、SQL库存及消息核对通过；后续22项单元与10项浏览器回归通过 | [需求](../../frontend-compose-live/spec.md)、[地图](../../frontend-compose-live/map.md)、[验收](../../frontend-compose-live/verification.md) |

首版与后续真实联调分别计数；设计草图的285条断言也不累计为业务验收。具体命令、执行版本、源码清单和限制保存在各阶段验收记录及以下原始证据中。

- [首版结果摘要](evidence/frontend-v1/summary.json)、[源码清单](evidence/frontend-v1/source-manifest.sha256)、[H2真实浏览器报告](evidence/frontend-v1/real-browser-results.json)。
- [Compose结果摘要](evidence/frontend-compose-live/summary.json)、[源码清单](evidence/frontend-compose-live/source-manifest.sha256)、[139次HTTP摘要](evidence/frontend-compose-live/c1d0197130a1/http-summary.json)、[SQL核对](evidence/frontend-compose-live/c1d0197130a1/database-audit.json)、[资源清理](evidence/frontend-compose-live/services.json)。

## 关键取舍与实测修正

- 保留登录后目录访问、JWT角色快照和后端资格复验；已收款待确认时不绕过canCancel=false。就诊人证件不回填原文，未知创建结果沿原键原体核实。
- 下单截止、订单支付截止和签到窗口分别展示；支付受理、预约确认、退款受理和完成分别表达。医生按钮按本行资格，不新增跨行忙碌或日期限制。
- 本机已有nvm Node 24.18.1/npm 11.16.0。此前非交互PATH未加载nvm的识别遗漏已纠正，后续直接使用已有安装。
- 首轮真实浏览器142次请求通过，但测试classpath缺正常应用的RabbitMQ发布确认配置，4条Outbox仍PENDING；未将该轮标为整体通过。验收脚本显式补齐correlated/returns后，整轮浏览器和SQL检查通过。[失败诊断](evidence/frontend-compose-live/b6b45f0e98f5/outbox-diagnosis.json)与[随后清理](evidence/frontend-compose-live/b6b45f0e98f5/subsequent-cleanup.json)分别保留。

## 证据与追溯

证据从.scratch/frontend-v1/evidence及.scratch/frontend-compose-live/evidence移入本目录的evidence/，JSON、日志、摘要、诊断SQL结果和图片保持原字节。报告中的绝对路径、uncommitted标记和旧命令均按`e91d870`之前的验收时点理解，不改写历史结果。

原spec、map、issues和verification正文保留原路径，仅追加提交检查点并修正证据链接；没有合并移除任务正文，因此不新增原文删除的archive-manifest条目。需要原布局时可执行`git show e91d870:<原路径>`；源码清单对应其记录时点，不能将后续运行覆盖的文件误当原证据。

现行维护入口：[实施方案](../../../docs/frontend-implementation-plan.md)、[前端说明](../../../frontend/README.md)、[项目状态](../../../docs/project-status.md)。

[归档检查](archive-check.json)记录交付完整提交号、53个原始证据的字节一致性和文档链接校验结果。

## 限制

MockPay只用于开发/测试，尚无真实资金渠道。固定测试时钟不覆盖推进时间后的发布退避恢复；退款结果通知队列无消费者为既有边界。未做生产部署、压力测试、真实设备矩阵或完整无障碍验收；本轮没有重跑或累计历史Maven/MySQL/可靠性测试数字。
