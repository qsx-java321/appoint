# 11 迟到支付释放暂占

Type: fix
Triage: ready-for-agent
Status: resolved
Blocked by: 01

## 背景和目标

依据用户确认的业务决定修复当前缺口。

## 范围

在 Inbox 事务内按场次→预约锁序，迟到时关闭预约、释放暂占、删除有效占用并确保退款；历史记录仅只读盘点。

## 非范围

不修改数据库 schema、事件版本或正式 HTTP 契约；不自动修复历史数据。

## 前置依赖

01 基线与状态矩阵。

## 需要修改的模块和主要文件

`coordination/application/DefaultPaymentSucceededProcessor.java`、`coordination/PaymentSucceededProcessorIntegrationTest.java`，以及 MySQL 竞争测试和只读历史候选 SQL。

## 目标代码形态

复用现有 application interface、事务和消息拓扑，业务分支清晰，传输确认逻辑集中。

## 改造后的业务逻辑

在 Inbox 事务内按场次→预约锁序，迟到时关闭预约、释放暂占、删除有效占用并确保退款；历史记录仅只读盘点。

## 必须保持的不变量

事务原子性、库存守恒、Inbox 幂等、退款唯一性、条件更新及 eventId 不变。

## 可验证验收条件

截止前确认、截止时/截止后释放并退款；重复 eventId 和不同 eventId 不重复释放或退款；退款建立失败回滚 Inbox、预约、库存和占用；MySQL 真实竞争不超卖。历史候选查询仅只读，无法连接业务库时记录未执行。

## 应运行的测试

`./mvnw test`、`./mvnw -Pmysql-it verify`、`./mvnw -Preliability-it verify`。

## 独立交付与回退

单独回退事件处理修复及其测试；已提交资金、退款和消息记录不得删除。下游深 interface 已依赖此行为时先按依赖顺序回退。

## 完成状态清单

- [x] 实现与必要测试
- [x] 三层验证及文档记录

## Comments

- 2026-11-08：根据用户接受的方案创建，待完成依赖及完整验收。

- 2026-11-08：已实现迟到事件关闭预约并释放暂占，补齐占用 fixture；聚焦 H2 集成测试 2 项通过。独立事务回滚、真实并发、历史只读盘点及三层验收尚未完成；前置 01 基线矩阵尚待补齐。

- 2026-11-09：前置01已在 c4f1dc0 resolved。首轮生产修复已随 ea5bbeb 提交，本阶段补充退款创建失败的独立事务回滚、截止后成功、三种关闭状态及不同eventId幂等、MySQL四路竞争和Compose真实迟到支付。快照 `/tmp/booking-11-snapshot-nbgv8xyc` 验收中，尚不标记resolved。默认业务库只读盘点为V9、预约/支付0条，完整历史候选查询0条；未修复历史数据。

- 2026-11-09：独立快照聚焦28项、常规159项、MySQL25项、可靠性10项全部通过且零失败/错误/跳过；Maven串行执行。扩展Compose验收同时通过正常取消退款和真实迟到事件释放/退款，成功清理本次隔离资源。代码修复起点ea5bbeb，前置01提交c4f1dc0，本阶段提交SHA见Git日志。
