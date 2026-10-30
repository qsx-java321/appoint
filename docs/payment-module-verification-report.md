# 支付模块独立改造验证报告

验证日期：2026-10-30（Asia/Shanghai）

## 自动化结果

| 层级 | 命令 | 结果 | 重点覆盖 |
| --- | --- | --- | --- |
| H2 快速回归 | `./mvnw test` | 83 项通过 | 模块依赖、费用快照、状态机、查询投影、Legacy 与 HTTP 契约 |
| MySQL 8.4 集成 | `./mvnw -Pmysql-it verify` | 24 项通过 | V1→V7、唯一约束、行锁、支付/取消/过期/停诊竞争、真实 HTTP |
| MySQL + RabbitMQ | `./mvnw -Preliability-it verify` | 9 项通过 | confirm/return、Broker 中断恢复、重复消息、ACK/发布崩溃窗口、异步退款 |

Testcontainers 套件使用隔离的临时 MySQL/RabbitMQ，不写入本地业务库。MySQL profile 会先执行同一套 H2 回归；表中数量按 Failsafe 集成测试计数记录。

## 核心验收

- 排班只接受正数 CNY 挂号费；开放后费用锁定。预约单与支付单得到相同不可变快照，Legacy 未知金额保持 `null`。
- 预约、支付分别拥有写模型；协调层只经 application interface 编排，跨表 SQL 仅存在于只读 `bookingquery`。
- 支付尝试采用 T1/事务外 Gateway/T2，同一支付单最多一个处理中尝试；失败后可在截止前重试。
- 回调验签并核对通知、渠道订单、金额和币种；资金事实、通知审计和唯一 Outbox 同事务提交。
- Broker 停机不影响回调落库，恢复后自动确认；发布或 ACK 崩溃、重复消息都只产生一次业务副作用。
- 已确认取消立即释放号源并异步退款；退款失败不恢复预约。迟到支付不恢复预约，只建立同一笔全额退款。
- 真实 MySQL 验证回调分别与取消、过期、停诊竞争，终态合法且库存恒等式成立。
- 查询覆盖等待支付、处理中、确认中、已确认、退款中、已退款和过期，并返回金额、尝试与退款摘要。
- prod profile 不注册 MockPay Adapter、Mock Controller 或 `simulate-success` 路由。

## 迁移与可靠性

V4 在 DDL 前阻断活跃 Legacy 支付；V5 增加费用快照；V6 创建 attempt、refund、notification、outbox、inbox；V7 增加状态/唯一约束和扫描索引。空库、旧终态、阻断场景和二次迁移均有 H2/MySQL 验证。新模型上线后只允许前向恢复；切换步骤见 [支付模块切换 runbook](runbooks/payment-module-cutover.md)。

Outbox 使用 Publisher Confirm、mandatory return 和退避；Inbox 与预约确认同事务。对账扫描长期确认中、陈旧 Outbox、迟到支付无退款、长期尝试、退款积压和关联不一致；无法安全自动修复的记录转人工项。DLQ 重放保留原 `eventId`。

## 当前边界与风险

- 第一阶段只支持 CNY、MockPay、原路全额退款；未接真实微信/支付宝，不支持部分退款。
- 已确认预约停诊后的自动退款不在本期，保持“保留预约并提示联系医院”的结果。
- 至少一次投递允许短暂 `PAID + PENDING_PAYMENT` 和 `REFUND_PENDING`，客户端应轮询查询投影。
- Flyway 提示当前版本声明验证到 MySQL 8.1；V1–V7 已在 MySQL 8.4 Testcontainers 实际通过。
