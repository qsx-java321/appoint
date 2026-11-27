# 场景 A：下单主链路容量

## 1. 环境

| 项 | 值 |
| --- | --- |
| 日期 | 2026-11-28 |
| 分支 | `test/k6-loadtest` |
| 后端环境 | `test` |
| 机器 | WSL2，Linux 6.18.33.2 |
| 部署方式 | Docker Compose + 本地 Spring Boot |
| 数据库 | MySQL 8.4（Docker） |
| Java / Spring Boot | JDK 21 / Spring Boot 3.5.16 |
| k6 | v2.3.0 |
| 客户端与服务端是否同机 | 是 |
| 时间同步 | 本轮压测期间临时关闭 `systemd-timesyncd`，结束后恢复 |

## 2. 数据规模

| 项 | 值 |
| --- | --- |
| 加载账号 | 1（`test-admin@example.local`） |
| 就诊人 | 300 个，证件号唯一 |
| 场次 | 144 个（18 名医生 × 明天/后天 × 4 场） |
| 每场容量 | 1000 |
| 业务迭代 | 1 次 = 领令牌 → 下单 → 支付 → 查详情（4 个 HTTP 请求） |
| 唯一 pair 上限 | 300 × 144 = 43,200 |
| 数据准备 | reset → 设置容量 1000 → 批量创建就诊人 → 写 `scenario-a.json` |
| 时间窗口 | 只选明天/后天场次，避免当天截止和支付过期 |

### pair 映射

每个迭代使用唯一的 `(patient, schedule)`：

```text
pairIndex = OFFSET + exec.scenario.iterationInTest
permuted  = (pairIndex * 104729) % (300 * 144)
patient   = patients[permuted % 300]
schedule  = slots[floor(permuted / 300) % 144]
```

乘数 `104729` 与 `43,200` 互质，保证置换是双射；同时打散患者和场次，避免同一患者或同一场次在短时间内形成人为行锁热点。

## 3. 流量模型

7 个顺序执行的 `constant-arrival-rate` 场景，每档 30 秒，档间 5 秒：

| 档位 | 业务 TPS | 设计迭代数 | 预分配 VU / 最大 VU |
| --- | --- | --- | --- |
| r10 | 10 | 300 | 100 / 200 |
| r30 | 30 | 900 | 120 / 240 |
| r50 | 50 | 1,500 | 200 / 400 |
| r80 | 80 | 2,400 | 320 / 640 |
| r100 | 100 | 3,000 | 400 / 800 |
| r120 | 120 | 3,600 | 480 / 960 |
| r160 | 160 | 4,800 | 640 / 1,280 |

> 业务 TPS 与 HTTP QPS 的关系：QPS ≈ 4 × TPS。160 TPS ≈ 640 HTTP QPS。

## 4. 最终结果（A6）

| 档位 | 实际/设计迭代 | 成功率 | p95 | p99 | 5xx | 4204 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| r10 | 301/300 | 100.00% | 20.01 ms | 24.70 ms | 0 | 0 | PASS |
| r30 | 901/900 | 100.00% | 15.25 ms | 19.26 ms | 0 | 0 | PASS |
| r50 | 1,500/1,500 | 100.00% | 15.83 ms | 19.90 ms | 0 | 0 | PASS |
| r80 | 2,401/2,400 | 100.00% | 21.47 ms | 25.67 ms | 0 | 0 | PASS |
| r100 | 3,001/3,000 | 100.00% | 24.58 ms | 29.20 ms | 0 | 0 | PASS |
| r120 | 3,601/3,600 | 99.97% | 27.05 ms | 37.73 ms | 0 | 1 | PASS |
| r160 | 4,801/4,800 | 100.00% | **48.93 ms** | **98.98 ms** | 0 | 0 | **PASS** |

汇总：

| 指标 | 值 |
| --- | --- |
| 业务尝试 | 16,506 |
| 业务成功 | 16,505 |
| HTTP 请求 | 66,023 |
| 全周期平均 HTTP 请求率 | 274.95 req/s |
| 全周期 HTTP p95 / p99 | 31.60 ms / 60.56 ms |
| HTTP 失败率 | 0.000% |
| 5xx | 0 |
| 202 卡死 | 0 |
| 4002 / 4202 | 0 / 0 |
| 4204 | 1（r120，0.028%） |
| 最高通过档位 | **160 TPS（≈640 HTTP QPS）** |

> 实际迭代数略高于设计值，是到达率执行器在档位边界上的正常波动；没有出现 `dropped_iterations`，160 TPS 档完整打满。

## 5. 数据库验证

| 检查项 | 实际值 |
| --- | --- |
| 场次数 | 144 |
| 就诊人数 / 证件去重 | 300 / 300 |
| 预约单 / CONFIRMED | 16,505 / 16,505 |
| PENDING / CANCELLED / EXPIRED | 0 / 0 / 0 |
| 支付单 / PAID | 16,505 / 16,505 |
| 幂等请求数 | 16,505 |
| 有效占用数 | 16,505 |
| 库存不变量违规 | 0 |
| 场次与订单对账违规 | 0 |
| `visit_number` 连续性违规 | 0 |
| 重复 patient / 证件 claim | 0 / 0 |
| 支付一致性违规 | 0 |
| k6 与 DB 对账 | attempts=16,506，create/pay/detail=16,505，orders=16,505 |
| 4204 被拒绝的重复占用 | 1，未产生订单，未造成超卖 |

结论：库存不变量、订单/支付/占用/回执对账、就诊序号连续性和唯一约束全部成立。

## 6. 资源与瓶颈

压测后快照（非全过程峰值）：

| 容器 | CPU | 内存 |
| --- | --- | --- |
| MySQL | 8.75% | 628.9 MiB |
| Redis | 0.32% | 10.5 MiB |
| RabbitMQ | 4.87% | 165.9 MiB |
| SeaweedFS | 5.68% | 139.4 MiB |
| Mailpit | 2.32% | 20.5 MiB |

MySQL：

| 指标 | 值 |
| --- | --- |
| Threads_connected / running | 12 / 2 |
| Slow_queries | 0 |
| Innodb_row_lock_waits（累计） | 32,532 |
| Innodb_row_lock_time_avg / max | 62 ms / 270 ms |

关键发现：

1. **测试数据映射会制造假瓶颈**  
   A5 使用“场次优先”的 pair 映射，同一患者连续占用 144 个场次。创建事务会 `SELECT ... FOR UPDATE` 锁患者行，导致 100/120 TPS 的 p95 分别达到 1.8s/2.2s，累计行锁等待 32,521 次。  
   A6 改用互质乘数置换后，患者和场次被同时打散，行锁等待只新增 11 次，160 TPS 的 p95 降到 48.93ms。之前的 80 TPS 结论是测试数据造成的低估。

2. **160 TPS 尚未触达拐点**  
   7 个档位全部满足 p95 < 300ms、p99 < 800ms、成功率 ≥ 99%、5xx < 1%、无超卖。160 TPS 的 p95 仍低于 50ms，系统还有余量；本次没有找到真正的容量上限，只能说“至少 160 TPS”。

3. **通知 Outbox 是下一个观察点**  
   压测后 `notification_outbox_events` 为 `PENDING=7,208`、`PUBLISHED=9,297`，`user_notifications=9,297`。发布器每秒最多 50 条、消费者单并发，通知投递出现明显积压。它没有影响下单/支付事务的成功率，但会延迟消息中心可见时间。

4. **WSL 时钟回拨会触发雪花 ID 异常**  
   A2b/A3 期间 `systemd-timesyncd` 频繁回拨系统时钟，MyBatis-Plus 雪花 ID 抛出 `Clock moved backwards`，导致插入返回 500，并进一步产生 202 卡死。A6 临时关闭 NTP 后 5xx 为 0，测试结束后已恢复 NTP。这是环境/时钟同步问题，不是库存逻辑问题；生产环境应保证时钟稳定或使用对回拨更宽容的 ID 生成策略。

5. **k6 高基数 URL**  
   A2 首轮详情和支付 URL 带有唯一预约 ID，k6 产生超过 10 万条时间序列。已通过 `name` 标签归组，A6 不再出现高基数警告。

## 7. 容量结论

- **最高通过档位：160 业务 TPS，约 640 HTTP QPS。**
- 160 TPS 稳定 30 秒：p95 48.93ms、p99 98.98ms、成功率 100%、5xx 0、无超卖。
- r120 出现 1 次 `4204`（0.028%），是唯一约束拒绝重复有效占用，未产生订单、未超卖；r160 为 0。
- 本次没有找到拐点；系统容量大于 160 TPS。下一步可继续测 240/320 TPS，或先优化通知 Outbox 再回归。

## 8. 已知限制

- k6 与后端同机，客户端和服务端会争抢 CPU；结论是本地环境下限。
- 只有 1 个加载账号、300 个就诊人、144 个场次、每场容量 1000；不是生产规模。
- 每档只持续 30 秒；长稳、内存泄漏和连接池长时间行为未覆盖。
- 没有 Actuator，Hikari 活跃/等待连接只能通过 MySQL 连接数和日志间接判断。
- 通知 Outbox 有意保留积压，未在本轮优化。
- 1 次 4204 未完全定位到具体 pair；它被唯一约束正确拒绝，未造成数据错误。

## 9. 下一步

1. 在干净环境继续测 240/320 TPS，找到真正的 p95 拐点。
2. 观察通知 Outbox 积压对消息中心延迟的影响，必要时提高发布器批量/并发或消费者并发。
3. 补 Actuator 或 Micrometer，采集 Hikari 活跃连接、等待连接和 MySQL 锁等待的实时曲线。
4. 优化后做同场景回归，形成“优化前/优化后”的对比数据。

## 10. 复现命令

```bash
# WSL 时钟回拨会触发雪花 ID 异常；本轮临时关闭时间同步，结束后恢复
sudo timedatectl set-ntp false

./loadtest/scripts/reset_and_start.sh --yes
python3 loadtest/scripts/set_capacity.py --capacity 1000
python3 loadtest/scripts/prepare_patients.py --scenario a --patients 300 --capacity 1000 \
  --min-slots 144 --out loadtest/data/scenario-a.json

./loadtest/scripts/collect_metrics.sh --scenario a6 --phase before

set -a; . ./.env.test; set +a
k6 run -e TIERS=10,30,50,80,100,120,160 -e RUN_LABEL=a6 \
  -e LOAD_PASSWORD="$LOCAL_SEED_PASSWORD" loadtest/k6/03-booking-capacity.js

./loadtest/scripts/collect_metrics.sh --scenario a6 --phase after
python3 loadtest/scripts/verify_inventory.py --scenario a \
  --data loadtest/data/scenario-a.json \
  --summary loadtest/results/scenario-a6-summary.json

./loadtest/scripts/stop_and_reset.sh --yes
sudo timedatectl set-ntp true
```
