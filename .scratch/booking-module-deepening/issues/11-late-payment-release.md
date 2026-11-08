# 11 迟到支付释放暂占

Type: fix
Triage: ready-for-agent
Status: claimed
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

- [ ] 实现与必要测试
- [ ] 三层验证及文档记录

## Comments

- 2026-11-08：根据用户接受的方案创建，待完成依赖及完整验收。

- 2026-11-08：已实现迟到事件关闭预约并释放暂占，补齐占用 fixture；聚焦 H2 集成测试 2 项通过。独立事务回滚、真实并发、历史只读盘点及三层验收尚未完成；前置 01 基线矩阵尚待补齐。
