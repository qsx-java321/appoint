# 支付模块独立改造任务地图

> 文档角色：已交付阶段的历史设计/实施记录（2026-11-12复核）。16/16已于a293afa交付；同步兼容入口后来删除、接口继续深化、消息设计与测试整理也已完成。下文目标/差距及83等数量均按原实施检查点理解，不是当前待办。 当前能力与进度见[项目状态](../../docs/project-status.md)。

设计基线：`4d43a97`（仅作为目标设计基线；实施以当前分支增量推进）
Status: resolved

## 实施前基线与目标差距（2026-10-29历史）

| 领域 | 当时实现 | 当时目标 | 对应 issue |
| --- | --- | --- | --- |
| 模块依赖 | `payment → AppointmentLifecycleService`，预约创建和生命周期直接使用 `PaymentOrderMapper`/`PaymentOrder` | 写模块只经 application interface/事件协作，查询跨表读取独立到 `bookingquery` | 01、05、06、15 |
| 支付模型 | 支付单只有简单字符串状态；没有金额、币种、截止、Legacy 标记 | 费用快照和完整支付单状态机 | 01、03、04 |
| 支付尝试与渠道 | `simulate-success` 同步推进预约；没有渠道 seam、失败或重试 | `PaymentGateway`、MockPay、每单多尝试且单活跃尝试 | 07、08、09 |
| 可靠事件 | 应用未接入 RabbitMQ；没有 Outbox、Inbox、重试、DLQ | 支付回调事务落库，至少一次投递，Inbox 幂等消费 | 09、10、11 |
| 退款补偿 | 已确认取消同步把支付标记 `REFUNDED`；无真实退款记录与失败态 | 预约先取消释放号源，异步原路全额退款；迟到支付退款 | 12 |
| 过期与对账 | 扫描只接受 `PENDING_PAYMENT + UNPAID`，无法保护已支付未确认组合 | 检查支付本地真相，保护 `PAID` 暂占并恢复处理；异常对账与 DLQ 重放 | 13 |
| 数据库 | V1–V3；无费用、attempt/refund/notification/outbox/inbox 表 | V4–V7 增量迁移、Legacy 阻断/保留、状态与金额约束 | 02、03、04 |
| API/查询 | 支付同步 HTTP 200；无 `workflowStatus`、金额、尝试、退款摘要 | 发起尝试 HTTP 202；轮询显示确认中/退款中；Legacy 可读 | 11、15、16 |
| 验证 | 设计基线为 H2 43 项、MySQL 11 项；完成 01–02 后为 H2 53 项、MySQL 12 项；支付浅层转发仍待拆，无 RabbitMQ Testcontainers | 行为契约、模块依赖、真实 MySQL/RabbitMQ 故障与竞争、真实 HTTP 全链路 | 01、10、14、16 |

## 实施原则与测试 seam

- 行为 seam：现有预约、支付、取消和查询 HTTP/application interface；characterization tests 只断言调用者可观察的状态、错误、库存与幂等结果。
- 架构 seam：`com.qsx.appoint.appointment`、`payment`、`coordination`、`bookingquery` 包依赖；依赖测试阻止跨写模块 Mapper、实体和实现类引用。
- 外部系统 seam：`PaymentGateway` 与 RabbitMQ；渠道使用共享契约测试，消息可靠性使用真实 RabbitMQ Testcontainers。
- 数据 seam：涉及锁、唯一约束、并发、Outbox/Inbox 的行为必须在 MySQL 8.4 Testcontainers 上验证；H2 只承担快速回归。

## 关键路径

```text
01 基线契约
  └─ 02 迁移预检与 runbook
      ├─ 03 费用快照
      └─ 04 支付持久化模型
          └─ 05 创建协调与写模块拆分
              └─ 06 生命周期协调
                  ├─ 07 PaymentGateway/MockPay
                  │   └─ 08 支付尝试
                  │       └─ 09 回调/通知/Outbox
                  │           └─ 10 RabbitMQ 发布可靠性
                  │               └─ 11 Inbox 异步确认与 202 API
                  │                   └─ 12 异步退款与迟到支付
                  │                       └─ 13 过期协调与对账
                  │                           └─ 14 MySQL/RabbitMQ 竞争验收
                  └──────────────────────────────┘
                              ├─ 15 查询投影
                              └─ 16 兼容清理、文档与端到端交付
```

## Issue 清单与推荐顺序

| 顺序 | 阶段 | Issue | 状态 | 前置依赖 | 独立验收重点 |
| ---: | ---: | --- | --- | --- | --- |
| 1 | 1 | [01 基线行为与模块契约](issues/01-baseline-module-contracts.md) | resolved | 无 | 现有行为锁定；反向 Mapper 依赖会失败 |
| 2 | 2 | [02 Legacy 预检与切换 runbook](issues/02-legacy-preflight-runbook.md) | resolved | 01 | 活跃旧支付阻断且无 DDL；切换/前向恢复可演练 |
| 3 | 2 | [03 挂号费与快照](issues/03-fee-snapshots.md) | resolved | 02 | 金额规则、排班锁定、双份不可变快照 |
| 4 | 2 | [04 支付可靠性持久化模型](issues/04-payment-persistence-models.md) | resolved | 02 | 新表、约束、Legacy 兼容与二次迁移 |
| 5 | 3 | [05 创建预约协调与写模块拆分](issues/05-booking-coordinator.md) | resolved | 03、04 | 原子创建且写模块不跨 Mapper |
| 6 | 3 | [06 取消、过期与停诊协调](issues/06-lifecycle-coordinator.md) | resolved | 05 | 固定锁序、库存守恒、同步兼容行为 |
| 7 | 4 | [07 PaymentGateway 与 MockPay 契约](issues/07-payment-gateway-mockpay.md) | resolved | 04、05 | 渠道 registry、签名、profile 隔离 |
| 8 | 4 | [08 支付尝试状态机](issues/08-payment-attempts.md) | resolved | 06、07 | 外部调用不占长事务；单活跃尝试 |
| 9 | 4–5 | [09 回调幂等与支付 Outbox](issues/09-notification-outbox.md) | resolved | 08 | 验签、金额校验、资金事实与 Outbox 同事务 |
| 10 | 5 | [10 RabbitMQ 拓扑与 Outbox 发布](issues/10-rabbit-outbox-publisher.md) | resolved | 09 | confirm、退避、重复、Broker 故障与 DLQ |
| 11 | 5 | [11 Inbox 异步确认与支付 202 API](issues/11-inbox-async-confirmation.md) | resolved | 06、10 | 幂等确认、确认中投影、HTTP 202 |
| 12 | 6 | [12 异步全额退款与迟到支付](issues/12-refund-compensation.md) | resolved | 08、10、11 | 立即释放号源；失败不恢复；每单一笔退款 |
| 13 | 7 | [13 过期协调、对账与 DLQ 重放](issues/13-expiration-reconciliation.md) | resolved | 11、12 | 保护已支付暂占、故障自愈或明确转人工 |
| 14 | 7 | [14 真实并发与可靠性验收矩阵](issues/14-concurrency-reliability-acceptance.md) | resolved | 13 | MySQL/RabbitMQ Testcontainers 全竞争矩阵 |
| 15 | 8 | [15 bookingquery 查询投影](issues/15-booking-query-projection.md) | resolved | 11、12 | 跨表只读 ownership、workflowStatus、Legacy |
| 16 | 8 | [16 兼容清理、文档与端到端交付](issues/16-compatibility-docs-delivery.md) | resolved | 14、15 | HTTP 流程、文档一致、生产无旧模拟入口 |

## 阶段门禁

每个 issue 完成后运行其文件所列测试；进入下一阶段前至少运行 `./mvnw test`。阶段 2、3、4、6、7 涉及真实约束或并发时运行 `./mvnw -Pmysql-it verify`；阶段 5 起增加 RabbitMQ Testcontainers profile/测试集。只有前置 issue 为 `resolved`，后续 issue 才视为 unblocked。

## Comments

- 2026-10-29：确认工作区干净且位于 `4d43a97`；未修改或重置 Git 历史。基线 `./mvnw test` 为 43 项通过。
- 2026-10-29：开始 issue 01；不改变现有外部流程，也不提前删除 `simulate-success`。
- 2026-10-29：issue 01 resolved。H2 51 项、MySQL 11 项通过；issue 02 成为关键路径上第一个 unblocked 的 `ready-for-agent` 任务。
- 2026-10-29：issue 02 resolved。V4 与切换 runbook 完成，H2 53 项、MySQL 12 项通过；issue 03、04 同时解除阻塞，推荐先做 03 再做 04。
- 2026-10-29：issue 03 resolved。V5、正数 CNY 排班费、开放后锁定与双份金额快照完成；H2 57 项、MySQL 12 项通过。issue 04 是下一项关键路径任务。
- 2026-10-29：issue 04 resolved。V6/V7、可靠性持久化类型和 MySQL 真实约束完成；H2 58 项、MySQL 13 项通过。issue 05 解除阻塞。
- 2026-10-29：issue 05 resolved。预约暂占与支付单创建分别归属各自 application interface，`BookingCoordinator` 保持本地事务原子性；H2 59 项、MySQL 13 项通过。issue 06 成为下一项关键路径任务。
- 2026-10-29：issue 06 resolved。取消、过期、停诊和同步模拟支付迁入生命周期协调器，移除全部跨写模块架构豁免；H2 58 项、MySQL 13 项通过。issue 07 成为下一项关键路径任务。
- 2026-10-29：issue 07 resolved。稳定 `PaymentGateway`/registry、MockPay HMAC 签名与 dev/test profile 隔离完成；共享契约/隔离 8 项、完整 H2 66 项通过。issue 08 解除阻塞。
- 2026-10-29：issue 08 resolved。支付尝试 T1/事务外 Gateway/T2、失败恢复和 HTTP 202 完成；H2 70 项、MySQL 14 项通过。issue 09 成为下一项关键路径任务。
- 2026-10-29：issue 09 resolved。统一通知入口、验签、审计、资金事实与 `PaymentSucceeded` Outbox 原子提交完成；并发重复通知收敛为一次副作用。H2 70 项、MySQL 15 项通过，issue 10 成为下一项关键路径任务。
- 2026-10-29：issue 10 resolved。durable RabbitMQ/retry/DLX/DLQ、Outbox confirm/mandatory return、指数退避和 `FAILED` 告警完成；H2 70 项、MySQL+RabbitMQ reliability 6 项通过。issue 11 成为下一项关键路径任务。
- 2026-10-30：issue 11 resolved。版本化 `PaymentSucceeded`、Inbox 同事务幂等确认、提交后 ACK、坏消息 DLQ、MockPay 异步回调、dev/test 兼容入口 202 和 `CONFIRMING_APPOINTMENT` 投影完成；H2 73 项、MySQL 15 项、reliability 8 项通过。issue 12 成为下一项关键路径任务。
- 2026-10-30：issue 12 resolved。已确认取消异步全额退款、迟到支付双侧补偿、事务外 Gateway worker、三次退避与 DLQ 完成；H2 74 项、MySQL 15 项、reliability 9 项通过。issue 13 成为下一项关键路径任务。
- 2026-10-30：issue 13 resolved。过期任务保护已支付事实、对账分类自愈/转人工、受控 DLQ 原 eventId 重放完成；H2 76 项、reliability 双容器 9 项通过。issue 14 成为下一项关键路径任务。
- 2026-10-30：issue 14 resolved。真实回调与取消/过期/停诊竞争、唯一退款、MockPay 回调矩阵和 RabbitMQ 崩溃窗口门禁完成；MySQL 24 项、双容器 9 项通过。issue 15、16 依次进入交付阶段。
- 2026-10-30：issue 15 resolved。跨表读取集中到只读 `bookingquery`，金额、尝试、退款摘要和完整 workflow 投影完成，Legacy 空金额终态保持可读；H2 83 项、MySQL 24 项通过。issue 16 成为最终交付任务。
- 2026-10-30：issue 16 resolved。生产 profile 无 Mock/旧模拟路由，真实 HTTP 已切换至异步支付尝试与轮询，交付文档/示例/验证报告完成；最终 H2 83 项、MySQL 24 项、双容器可靠性 9 项全部通过。支付模块独立改造任务图全部完成。
