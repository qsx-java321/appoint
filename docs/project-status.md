# 当前项目状态与文档入口

核对日期：2026-11-12（Asia/Shanghai）。最近一次实现/测试交付为`71e4ec8`，测试源码检查点为`9c5878b`；本次文档核对未修改源码。后续代码变化须同时更新对应现行文档和本页检查点。

## 当前已完成

| 领域 | 当前实现 | 权威说明 |
| --- | --- | --- |
| 身份与目录 | 注册/JWT/角色、就诊人管理、医院科室医生目录、管理员预约设置和医生账号首次绑定 | [API](api.md)、[就诊API](visit/api.md) |
| 创建与库存 | 今天/明天/后天十二场展示，四固定场次，开始前15分钟停止新单；按需生成、幂等重放、防超卖、有效占用和快照 | [业务流程](business-flow.md)、[ADR0007](adr/0007-three-day-booking-and-session-cutoffs.md) |
| 支付与退款 | 正式支付尝试、dev/test MockPay验签通知、Outbox/RabbitMQ/Inbox、异步确认、迟到支付退款、取消与对账 | [模块设计](module-design.md)、[恢复手册](runbooks/payment-module-cutover.md) |
| 到院接诊 | 当天签到→候诊→开始→完成，医生四场概览/名单/详情，双方查询一致；完成不释放库存 | [就诊设计](visit/design.md)、[就诊API](visit/api.md) |
| 工程验证 | 消息设计九步和测试整理八步均完成，接口契约、架构正反例、真实事务/并发/故障窗口及隔离HTTP均已验证 | [测试约定](testing.md)、[最新验收](../.scratch/test-suite-refactor/verification/final-acceptance.md) |

数据库最新迁移为V10；单Spring Boot、单Maven和共享MySQL的模块化单体保持。Redis不参与当前业务。创建、付款资格以持久化截止及服务端业务时间为准，不能把停止新单与原订单失去支付资格混为一谈。

## 当前边界

- 未接入真实支付资金渠道；MockPay只在dev/test注册，prod不注册Mock渠道/入口。
- 没有业务前端。旧独立讲解页`docs/business-flow-demo.html`已在四场次改造中删除，不是当前交付物。
- 不提供医院关门、医生停诊、管理员手工维护场次的REST；历史旧场次及终态继续可读。
- 尚无退款结果监听器、短信/邮件通知、运维重放HTTP、Outbox租约领取、病历处方、自动叫号、爽约、回退或实时推送。
- Outbox FAILED和DLQ可能需要受控人工恢复；至少一次投递不等于物理恰好一次。就诊完成表示接诊状态完成，不表示已实现病历和处方系统。

## 最新实际验收

2026-11-12测试整理交付：干净常规340、完整MySQL38、完整可靠性16通过，零失败/错误/跳过；隔离curl92个请求全部符合预期。两个集成profile重复运行的常规测试不重复累计。报告与逐用例摘要见[最终验收](../.scratch/test-suite-refactor/verification/final-acceptance.md)，原方法迁移见[审计](../.scratch/test-suite-refactor/verification/method-audit.md)。

这是上述检查点的实际结果，不是每次改文档都重新执行业务测试的承诺。临时服务和资源状态属于验收当时的记录；需要当前环境状态时重新检查，不据历史文字推断。

## 任务记录的正确读法

- [测试整理地图](../.scratch/test-suite-refactor/map.md)：8/8完成；[消息设计地图](../.scratch/messaging-design-refactor/map.md)：9/9完成。
- [三天窗口](../.scratch/three-day-booking-time/map.md)、[到院需求](../.scratch/visit-workflow/spec.md)的业务已落地，原设计草图不是额外待开发任务。
- `.scratch`保存阶段目标、已完成issue及当时证据；正文里的“当前/新增/下一步”按其历史检查点理解。已完成任务不因旧目标语气重新变为待办。
- 旧同步模拟支付、单号源、半日场次、D+1～D+7、V1清库重建、独立说明页等记录仅用于解释演进。当前运行只遵循本页链接的现行文档；不执行旧规格中的迁移或调用示例。
- ADR按接受/替代关系阅读，尤其ADR0005的七天日期规则已被ADR0007替代。历史测试数量不统一替换成340，以免篡改原验收事实。

## 维护约定

修改REST或DTO时同步API和HTTP示例；修改时间/状态/事务规则时同步业务流程、模块/就诊设计及相关ADR；修改测试职责时同步测试约定和场景映射。完成任务后同步spec/map/issue状态，保存实际执行命令与源码检查点。文档链接和静态契约检查通过不替代业务测试。
