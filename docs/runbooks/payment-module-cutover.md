# 支付模块独立改造切换与前向恢复 Runbook

现行内容复核：2026-11-12。业务与恢复边界已对照源码；当前交付、验证检查点及历史记录说明见[项目状态](../project-status.md)。

本 runbook 用于已安装 V1–V9 的数据库升级至当前 V10，以及已到 V10 时的应用切换和消息恢复。必须先确认起始版本，不能一律从 V4 开始。当前仅 dev/test 提供 MockPay，本文不表示真实支付渠道已具备生产上线条件。

执行人应记录开始/结束时间、应用版本、数据库实例、备份位置、校验值和每一步结果。不得把数据库密码、JWT/RabbitMQ 密钥或完整敏感报文写入记录。

## 1. 发布前门禁

- 确认待发布构建的 H2、MySQL 8.4、RabbitMQ Testcontainers 和真实 HTTP 验收全部通过。
- 读取实际 Flyway 历史并确认起点；V2 在 V3 场次/库存结构变更前阻断有效旧预约，V4 在 V5–V7 支付扩展前阻断旧活跃支付。既有迁移文件不得修改、跳过或手工标记成功。
- 确认应用和 RabbitMQ 声明可使用同一版本配置启动，MockPay 仅在 dev/test profile 注册。
- 约定维护窗口、执行人、复核人和前向修复版本；不要安排会自动清表或执行 down migration 的工具。

先在目标数据库执行以下只读查询，检查全部历史行而非只看最高版本：

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

| 已成功安装的起点 | 预检与后续迁移 |
| --- | --- |
| V1 | 同时执行下文 V2、V4 预检；Flyway 顺序执行 V2–V10 |
| V2–V3 | V2 已成功，不重跑；V4 尚未执行，执行其预检，Flyway 从实际下一版本顺序推进至 V10 |
| V4–V8 | V2/V4 已成功，不重跑迁移；盘点现有订单、费用及消息状态，执行实际剩余版本至 V10 |
| V9 | 执行 V10：医生账号可空唯一关联、DOCTOR 角色、就诊表及查询索引；不回填历史就诊记录 |
| V10 | 校验迁移与构建匹配；正常情况下没有待执行迁移，只进行应用切换和运行状态检查 |

表格以此前版本连续成功为前提。历史有失败、缺口、校验不匹配或未知版本时先停止并调查；不能以最高成功版本推断实际结构完整。全新空库由 Flyway 从 V1 初始化；已有业务表却缺少历史表时，需单独核实来源，不直接 baseline 或套用上述起点。

## 2. 进入维护窗口

1. 隔离所有业务写流量，包括注册、就诊人/目录/设置维护、预约、支付尝试、渠道通知、MockPay、医生账号绑定、签到和医生开始/完成接诊；停用过期扫描、Outbox 发布、消息消费者和对账。查询仅在有兼容的只读维护版本时继续。
2. 停止所有应用实例或切换为只读维护版本，确认没有旧版本实例仍能接收写请求。
3. 等待正在执行的写事务结束，记录数据库活动事务检查结果。
4. 在同一目标数据库执行与尚未执行的门禁迁移对应的只读预检。

**V2 预检（V1 起点）：**

```sql
SELECT COUNT(*) AS active_legacy_appointments
FROM appointment_orders
WHERE status IN ('PENDING_PAYMENT', 'CONFIRMED');
```

待支付和已确认都必须计入；只检查未支付支付单会漏掉被 V2 阻断的已确认旧预约。

**V4 预检（V1–V3 起点）：**

```sql
SELECT COUNT(*) AS active_legacy_payments
FROM appointment_orders appointment
JOIN payment_orders payment ON payment.appointment_order_id = appointment.id
WHERE appointment.status = 'PENDING_PAYMENT'
  AND payment.status = 'UNPAID';
```

适用预检结果必须为 `0`。非零时终止发布，不启动迁移实例、不手工删除或改写订单；先制定并按既有流程执行旧订单结算/关闭方案，再安排维护窗口。SQL 与 V2/V4 代码中的门禁条件一致，不保证其他数据问题也已经排除。

## 3. 备份与基线校验

- 按现有发布规范做 MySQL 逻辑备份，备份文件放在受控位置。
- 记录备份文件大小与 SHA-256；执行一次只读恢复演练或使用最近一次已验证的恢复记录。
- 记录迁移前 Flyway 历史、预约/支付各状态数量和适用预检计数；仅在 V3 库存字段已存在时检查库存恒等式，仅在 V5/V6 后检查费用/消息表，避免在旧 schema 上执行不存在的字段查询。
- 任一校验不明确时停止，不以猜测或补写历史金额继续发布。

## 4. 执行迁移和部署

1. 保持 HTTP 写流量隔离，启动单个应用迁移实例，由 Spring Boot 集成的 Flyway 自动按顺序执行所有待迁移版本。该实例先设置 `appoint.messaging.enabled=false`、`appoint.outbox.enabled=false`、`appoint.expiration.enabled=false`、`appoint.reconciliation.enabled=false`，避免启动完成后立即处理业务。
2. 按起点核对待迁移序列及 V2/V4 门禁结果；任一迁移失败立即保持维护状态、保存历史和结构证据。已成功版本不重跑；不通过单独挑选 V4 或跳过中间版本完成升级。
3. 核对应用配置和 RabbitMQ 的 durable exchange、queue、binding、retry、DLX、DLQ，以及 publisher confirm、mandatory return。`appoint.messaging.enabled=false` 只控制监听器注册，不等于禁止拓扑声明；RabbitMQ 声明冲突也必须排查。
4. 在等价环境冒烟通过、迁移及拓扑均核对后，以原计划配置恢复一个应用实例的消费者和定时任务，检查运行结果，再逐步恢复流量和扩容。

## 5. 切换后验证

- Flyway schema history 只包含成功迁移且版本顺序正确，当前发布终点为 V10；重复执行迁移为零项。
- Legacy 终态订单数量、状态和未知金额空值保持不变。
- 新表、四时段约束、场次唯一约束、扫描索引和金额/状态约束存在；首次执行 V9 时 `booking_settings` 单行初始值为 `20 / 10.00 / CNY`，已到 V9 的库保留管理员设置，不强制改回默认。适用库存恒等式异常数为零。
- V10 的 doctors.user_id 可空唯一关联及外键、DOCTOR 角色、visit_records 的预约一对一/外键/状态约束及两个查询索引存在；升级不改变历史订单、统一设置和就诊序号，不自动绑定医生或生成历史就诊记录。
- 查询医生场次得到今天至后天共 12 项，包含 `bookingClosesAt/availability/serverTime`，且查询本身不生成 `schedules` 记录。当天已截止项不可新下单；既有订单仍按自己的已存期限支付。
- 在 dev/test 等价环境使用 [隔离验收脚本](../../scripts/compose_acceptance.py) 或正式 HTTP 示例完成 MockPay 冒烟，核对预约、支付尝试、通知、Outbox、Inbox 与库存，并验证医生绑定、四场概览、当天签到、接诊完成和双方查询一致性。curl 复测可追加 `--http-client curl --http-report /tmp/appoint-curl-report.json`；跨日只通过测试源码的 Clock 接线，不在业务接口修改时间。脚本需要预先缓存 Maven 依赖，成功清理本次资源，失败保留资源和日志；不在目标业务库生成冒烟资金记录。
- 检查 Outbox 最老事件、DLQ、`PAID + PENDING_PAYMENT`、`REFUND_PENDING` 和长期 `CREATED/PROCESSING` 指标。
- 全部通过后恢复写流量；先小流量观察，再完全开放。

## 6. 失败处理与前向恢复

- V2/V4 阻断：保持维护状态、不执行后续迁移，处理门禁指出的旧订单。重新读取 schema history 和实际表结构；从 V1 开始时，V4 失败前可能已经成功执行 V2/V3，不能声称仍是发布前 schema。
- V3、V5–V10 或应用启动失败：停止实例，保存成功/失败迁移记录及实际结构。MySQL DDL 不能假定整份迁移或整个升级已回滚，V9/V10 均包含多条 DDL；不得盲目重跑、删表或直接执行 Flyway repair。先确定部分成功范围，再制定受控前向恢复方案。
- 应用版本回退必须单独证明旧构建兼容实际 schema 和已有业务数据；没有新模型写入也不自动代表可以安全回退。不删除已成功添加的表/列来迁就旧构建。
- 已产生 attempt、notification、Outbox、Inbox、refund、医生绑定或 visit_records 数据后：关闭新写入口，保留所有资金和事件记录，部署修复版本向前恢复。
- RabbitMQ 不可用：不回退已提交的支付结果，恢复 Broker、绑定及发布配置后，尚处于 PENDING 的到期 Outbox 可继续发布；已 PUBLISHED 只说明发布获确认，不证明消费完成。
- Outbox FAILED：当前发布器不会自动领取，对账也只计数报告。先核实 last_error、发布确认的不确定窗口、原事件及业务结果，再制定单独受控修复方案；当前无通用 Outbox FAILED 恢复接口。它不是 RabbitMQ DLQ，不能调用 DLQ 重放服务恢复，也不能重新生成 eventId 或改写 payload。
- 消费或退款异常：定位对应队列、eventId/refundNo、业务结果及重试计数，必要时停用消费者。支付成功 Inbox 与业务同事务；退款 worker 为分段事务，渠道可能已退款，即使本地保存或 ACK 失败仍须按原 refundNo 幂等重试，不能推断整次操作已经回滚。
- DLQ：修复根因后可由受控运维调用 `DeadLetterReplayService.replayOne(ReplayTarget)`，应用接口由 RabbitDeadLetterReplayService 实现，仅允许登记逻辑目标；当前没有 REST 重放接口。它保留原正文/eventId、清除重试计数/TTL，确认转发成功后才 ACK 原 DLQ 消息，失败则保留。退款结果队列目前没有监听器，重放这类结果消息不会自动通知用户。
- 对账：当前每 5 分钟执行，按 5 分钟滞留阈值恢复已付待确认（按 paidAt 确认或关闭退款）、为已关闭已付缺退款的预约补申请。长期 Outbox、退款、支付尝试及关联异常仅报告人工项，不自动重置 FAILED、重放 DLQ 或查询渠道资金结果。
- 任何无法证明安全的资金/库存不一致转人工处理，不擅自修改支付成功事实或重新占用号源。

### 6.1 受控重放 Java 契约

通过已注入的 DeadLetterReplayService 调用，不手动 new 接口，不向接口传任意物理队列：

```java
ReplayResult result = replayService.replayOne(ReplayTarget.PAYMENT_SUCCEEDED);
```

目标 PAYMENT_SUCCEEDED、REFUND_REQUESTED、REFUND_RESULT 分别映射原支付成功、退款执行、退款结果 DLQ。EMPTY 表示未领取消息；REPLAYED 表示转发已获确认，尚不证明消费者完成。REPLAYED 的 eventId 也可能为空，因为原文缺ID时不伪造；应按原文/队列定位，不能误报成空队列。

转发失败保留原消息并传播异常，修复根因后再调用。退款结果仍无监听器。OutboxPublicationException 的超时/连接失败不表示 Broker 一定未收到；数据库发布标记失败回滚整批可能产生重发，应核对原事件与 Inbox，不能创建新ID或清除幂等凭证。

## 7. 收尾记录

记录最终应用版本、Flyway 版本、备份校验值、迁移耗时、冒烟订单标识、关键指标和遗留人工项。确认维护模式已解除、定时任务和消费者按计划恢复，并由复核人签字确认。
