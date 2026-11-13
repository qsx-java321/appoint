# 当前项目状态与文档入口

核对日期：2026-11-13（Asia/Shanghai）。签到窗口改造实现已本地提交为`d75f73c`（基线`4bfc1d5`），代码、完整回归、正式文档与核验工具已完成；未推送。前次实现/测试交付为`71e4ec8`，测试源码检查点为`9c5878b`；其结果独立保留。

## 当前已完成

| 领域 | 当前实现 | 权威说明 |
| --- | --- | --- |
| 身份与目录 | 注册/JWT/角色、就诊人管理、医院科室医生目录、管理员预约设置和医生账号首次绑定 | [API](api.md)、[就诊API](visit/api.md) |
| 创建与库存 | 今天/明天/后天十二场展示，四固定场次，开始前15分钟停止新单；按需生成、幂等重放、防超卖、有效占用和快照 | [业务流程](business-flow.md)、[ADR0007](adr/0007-three-day-booking-and-session-cutoffs.md) |
| 支付与退款 | 正式支付尝试、dev/test MockPay验签通知、Outbox/RabbitMQ/Inbox、异步确认、迟到支付退款、取消与对账 | [模块设计](module-design.md)、[恢复手册](runbooks/payment-module-cutover.md) |
| 到院接诊 | [开诊前30分钟,场次结束)签到→候诊→开始→完成，医生四场概览/名单/详情，双方查询一致；完成不释放库存 | [就诊设计](visit/design.md)、[就诊API](visit/api.md) |
| 工程验证 | 消息设计九步和测试整理八步均完成，接口契约、架构正反例、真实事务/并发/故障窗口及隔离HTTP均已验证 | [测试约定](testing.md)、[最新验收](../.scratch/visit-checkin-window/verification/report.md) |

数据库最新迁移为V10；单Spring Boot、单Maven和共享MySQL的模块化单体保持。Redis已接入医院、科室、医生目录缓存，本轮完整验收已通过。创建、付款资格以持久化截止及服务端业务时间为准，不能把停止新单与原订单失去支付资格混为一谈。

## 当前边界

- Redis首期仅缓存目录；限流、场次缓存和JWT撤销未实施。范围及步骤见[方案](redis-catalog-cache-design.md)和[任务地图](../.scratch/redis-catalog-cache/map.md)。

- 未接入真实支付资金渠道；MockPay只在dev/test注册，prod不注册Mock渠道/入口。
- 没有业务前端。旧独立讲解页`docs/business-flow-demo.html`已在四场次改造中删除，不是当前交付物。
- 不提供医院关门、医生停诊、管理员手工维护场次的REST；历史旧场次及终态继续可读。
- 尚无退款结果监听器、短信/邮件通知、运维重放HTTP、Outbox租约领取、病历处方、自动叫号、爽约、回退或实时推送。
- Outbox FAILED和DLQ可能需要受控人工恢复；至少一次投递不等于物理恰好一次。就诊完成表示接诊状态完成，不表示已实现病历和处方系统。

## 最新实际验收

2026-11-14 Redis目录缓存：干净常规389、Redis7、MySQL38、可靠性16及curl97请求通过；Redis启动前不可达补强复验通过，零失败/错误/跳过，profile重复常规不累计。[交付与证据](../.scratch/archive/redis-catalog-cache/summary.md)。数据库仍为V10。

2026-11-13签到窗口改造（实现交付`d75f73c`，验收源码摘要与该提交一致）：聚焦106、干净常规381、完整MySQL38、可靠性16通过，零失败/错误/跳过；隔离curl92请求符合预期。聚焦和集成profile重复常规不累计。[实际验收](../.scratch/visit-checkin-window/verification/report.md)保存命令、源码摘要、逐用例、HTTP及资源证据。

前次2026-11-12测试整理的340/38/16及curl92保留在[历史验收](../.scratch/archive/delivery-quality/evidence/test-suite-refactor/final-acceptance.md)，对应源码9c5878b，不作为本次验证。该历史验收结束时基础服务已恢复停止，容器和卷保留；需要体验时按启动约定重新启动。

## 交付档案与阅读

业务和工程任务已交付；原14个阶段保存在[六组交付档案](../.scratch/README.md)，签到窗口增量已追加到[到院接诊档案](../.scratch/archive/visit-workflow/summary.md#2026-11-13-签到窗口增量)。历史任务全文可按[去向清单](../.scratch/archive-manifest.json)中的提交和原路径读取。档案里的“当前”“新增”和旧命令按历史检查点理解。

当前就诊要求见[正式需求](visit/requirements.md)，测试类与职责见[清单](testing/inventory.md)和[场景覆盖](testing/scenario-coverage.md)。旧七天入口由ADR0007替代；历史测试结果分别保留。

## 维护约定

已完成增量：[签到时间窗口方案](../.scratch/visit-checkin-window/spec.md)，规则为[预约开始前30分钟,预约结束)，历史已签保留，医生可截止后/跨日接诊，超时未签不自动取消或退款。详见[ADR0008](adr/0008-visit-checkin-window.md)、[任务地图](../.scratch/visit-checkin-window/map.md)和[本任务验收](../.scratch/visit-checkin-window/verification/report.md)。实现交付为`d75f73c`，未推送；原任务资料保留，验收证据已归入到院接诊档案。

修改REST或DTO时同步API和HTTP示例；修改时间/状态/事务规则时同步业务流程、模块/就诊设计及相关ADR；修改测试职责时同步测试约定和场景映射。完成任务后按issue约定同步状态、归档交付总结，保存实际执行命令与源码检查点。文档链接和静态契约检查通过不替代业务测试。
