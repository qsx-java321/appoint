# 场景 B：单场次抢号正确性

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

## 2. 数据规模

| 项 | 值 |
| --- | --- |
| 加载账号 | 1（`test-admin@example.local`） |
| 就诊人 | 100 个，证件号唯一 |
| 目标场次 | 1 个：医生 1，2026-11-30，`MORNING_FIRST` |
| 每场容量 | 1 |
| 数据准备 | reset 后由 `set_capacity.py` 设容量 1，再由 `prepare_patients.py` 创建就诊人 |
| 测试前是否已有场次 | 否；首次下单才生成 `schedules` 行 |

## 3. 场景与流量模型

- 执行器：`per-vu-iterations`
- VU：100，每个 VU 1 次迭代
- 软 barrier：`setup()` 返回 `startAt = Date.now() + 5000`；每个 VU 先领令牌，再等到 `startAt` 同时创建
- 每次迭代：`POST /appointments/token` → `POST /appointments`
- 创建请求使用各自的令牌和患者，目标场次相同
- k6 内不支付；由验证脚本找到唯一胜者后支付

预期结果：

| 结果 | 数量 |
| --- | --- |
| 成功 | 1 |
| `409 / 4002` 号源约满 | 99 |
| 其他错误 | 0 |

## 4. k6 结果

| 指标 | 值 |
| --- | --- |
| 尝试数 | 100 |
| 成功数 | 1 |
| `4002` 号源约满 | 99 |
| unexpected | 0 |
| 预期结果率 | 100.000% |
| 5xx 率 | 0.000% |
| HTTP 请求数 | 201（100 token + 100 create + 1 setup 登录） |
| HTTP p95 | 246.76 ms |
| HTTP p99 | 252.93 ms |
| 最大 VU | 100 |
| 并发启动 lag p95 | 0 ms |
| 令牌接口 p95 | 31.57 ms |
| 创建接口 p95 | 251.53 ms（含 99 个失败请求等待单行锁） |
| 202 重试 / 5001 重试 / 202 卡死 | 0 / 0 / 0 |

> HTTP p95 和创建 p95 主要来自 99 个失败请求等待唯一的 `schedules` 行锁；这不是容量指标，而是单行锁串行化的直接体现。成功请求本身很快。

## 5. 创建后、支付前的数据库验证

| 检查项 | 实际值 |
| --- | --- |
| `schedules` 行数 | 1 |
| `total, available, held, confirmed, next` | `1, 0, 1, 0, 1` |
| 库存不变量 | `1 = 0 + 1 + 0` |
| `appointment_orders` | 1 行，`PENDING_PAYMENT`，`visit_number=NULL` |
| `payment_orders` | 1 行，`UNPAID` |
| 支付过期时间与预约一致 | 是 |
| `appointment_active_claims` | 1 行 |
| `appointment_create_requests` | 1 行，订单 ID 和回执 JSON 均非空 |
| 就诊人数量 / 证件去重 | 100 / 100 |
| k6 与 DB 对账 | success=1、full=99、unexpected=0 |

99 个失败请求没有留下预约单、支付单、占用或幂等请求行；事务整体回滚。

## 6. 支付胜者后的验证

| 检查项 | 实际值 |
| --- | --- |
| `schedules total, available, held, confirmed, next` | `1, 0, 0, 1, 2` |
| 预约状态 | `CONFIRMED` |
| 就诊序号 | 1 |
| `payment_orders.status` | `PAID` |
| `paid_at` | `2026-11-28T00:01:04` |
| `appointment_active_claims` | 1 行 |
| 预约详情 | `CONFIRMED`、`PAID`、`visitNumber=1`、`visit` 非空 |
| `canPay / canCancel / paymentExpired` | `false / false / false` |
| 重复支付 | 200，`paidAt` 和 `visitNumber` 不变 |
| 已确认预约取消 | `409 / 4003`，库存保持 `1, 0, 0, 1, 2` |

## 7. 资源快照

压测结束后采集，非峰值：

| 容器 | CPU | 内存 |
| --- | --- | --- |
| MySQL | 3.39% | 495.6 MiB |
| Redis | 0.34% | 5.8 MiB |
| RabbitMQ | 0.33% | 165.0 MiB |
| SeaweedFS | 9.11% | 138.5 MiB |
| Mailpit | 0.00% | 20.1 MiB |

MySQL 状态：

| 指标 | 值 |
| --- | --- |
| Threads_connected | 12 |
| Threads_running | 2 |
| Innodb_row_lock_current_waits | 0 |
| Innodb_row_lock_waits | 180 |
| Innodb_row_lock_time_avg | 6 ms |
| Innodb_row_lock_time_max | 62 ms |
| Slow_queries | 0 |

## 8. 分析

- **恰好 1 单成功，无超卖**：首次下单的懒生成只创建了 1 行 `schedules`；条件更新 `available_capacity > 0` 让 1 个请求拿到名额，其余 99 个看到 `available=0` 返回 `4002`。
- **无重复预约**：100 个患者、100 个唯一证件，`4204` 为 0，说明每个患者只尝试了一次且没有触发同患者/同证件重复约束。
- **无时间窗口错误**：目标场次在业务今天+2 天，`4202` 为 0。
- **无锁异常**：没有 `5001`、没有 5xx、没有 202 卡死；MySQL 没有慢查询。
- **单行锁代价**：99 个失败请求的创建 p95 约 251ms，主要是在等唯一场次行的事务锁；这验证了库存单行锁在热点场次下会串行化请求。
- **幂等与生命周期**：重复支付沿用原 `paidAt` 和 `visitNumber`；已确认预约取消返回 `4003`，库存不变。

## 9. 已知限制

- k6 与后端同机，客户端和服务端会争抢 CPU；本结果不代表独立压测机。
- 100 个患者同属一个加载账号，共享同一个 JWT；这是为了减少登录开销，不影响库存和唯一约束验证。
- barrier 是软 barrier，不是操作系统级同时唤醒；本次启动 lag p95 为 0ms，竞争已足够充分。
- 本次不支付并发中的胜者，支付在 k6 结束后单独执行；支付路径的并发竞争不在本场景范围内。
- 只跑了 1 轮；如需论文级稳定性，可重复 3 轮并比较结果。

## 10. 结论

场景 B 通过全部审查标准：

- 100 次尝试，恰好 1 单成功，99 个 `4002`，0 个 `4204/4202/410x/5xx/202 卡死`。
- 创建前后库存不变量成立，无超卖、无重复预约。
- 支付、重复支付幂等、已确认取消 `4003` 均符合契约。
- 单行锁串行化在热点场次下清晰可见，是场景 A 需要重点关注的写链路瓶颈。

下一轮：审查通过后重置 test，进入场景 A（下单主链路容量）。

## 11. 复现命令

```bash
./loadtest/scripts/reset_and_start.sh --yes
python3 loadtest/scripts/set_capacity.py --capacity 1
python3 loadtest/scripts/prepare_patients.py --scenario b --patients 100 --capacity 1 \
  --out loadtest/data/scenario-b.json

set -a; . ./.env.test; set +a
k6 run -e LOAD_PASSWORD="$LOCAL_SEED_PASSWORD" loadtest/k6/02-single-slot-race.js

./loadtest/scripts/collect_metrics.sh --scenario b --phase after
python3 loadtest/scripts/verify_inventory.py --scenario b \
  --data loadtest/data/scenario-b.json \
  --summary loadtest/results/scenario-b-summary.json --pay-winner

./loadtest/scripts/stop_and_reset.sh --yes
```
