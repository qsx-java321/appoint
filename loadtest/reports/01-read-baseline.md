# 场景 C：读接口基线

## 1. 环境

| 项 | 值 |
| --- | --- |
| 日期 | 2026-11-27 |
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
| 账号数 | 1（`test-admin@example.local`） |
| 就诊人数 | 1 |
| 预约数 | 1（已支付确认） |
| 通知数 | 1（预约成功） |
| 医生数 | 18 |
| 每场容量 | 20 |
| 数据准备方式 | 重置 test 后由 `scripts/prepare_data.py` 通过 API 创建 |
| 本场景是否消耗号源 | 否；只读压测，仅准备阶段创建 1 张预约 |

## 3. 场景与流量模型

- 执行器：`ramping-arrival-rate`
- 预分配 VU：100
- 时间单位：1 秒
- 阶梯：

| 阶段 | 目标 | 时长 |
| --- | --- | --- |
| 预热 | 50 req/s | 10s |
| 爬坡 | 50 → 500 req/s | 10s |
| 稳定 | 500 req/s | 40s |
| 降坡 | 500 → 0 req/s | 10s |

- 接口权重：

| 接口 | 权重 |
| --- | --- |
| `GET /doctors/{id}/schedules` | 30% |
| `GET /hospitals` | 20% |
| `GET /hospitals/{id}/departments` | 15% |
| `GET /departments/{id}/doctors` | 13% |
| `GET /appointments/{id}` | 10% |
| `GET /appointments/my` | 8% |
| `GET /notifications` | 4% |

- 阈值：整体 p95 < 100ms、p99 < 300ms；场次查询 p95 < 150ms；HTTP 失败率 < 1%；业务校验通过率 > 99%。

## 4. 结果

| 指标 | 值 |
| --- | --- |
| 峰值请求率 | 500 req/s（稳定 40 秒） |
| 全周期平均请求率 | 388.7 req/s |
| HTTP 请求数 | 25,750 |
| p95 | 4.40 ms |
| p99 | 6.59 ms |
| HTTP 失败率 | 0.000% |
| 业务校验通过率 | 100.000% |
| 业务校验失败数 | 0 |
| dropped_iterations | 未出现 |
| 最大 VU | 100 |

### 分接口结果

| 接口 | p95 | p99 | 平均 |
| --- | --- | --- | --- |
| 场次查询 `schedules` | 5.88 ms | 8.56 ms | 4.01 ms |
| 预约详情 `appointment` | 2.93 ms | 4.82 ms | 2.12 ms |
| 我的预约 `my_appointments` | 2.87 ms | 4.88 ms | 2.14 ms |
| 通知列表 `notifications` | 2.18 ms | 3.78 ms | 1.55 ms |
| 科室列表 `departments` | 1.97 ms | 3.88 ms | 1.32 ms |
| 医生列表 `doctors` | 1.92 ms | 3.69 ms | 1.32 ms |
| 医院列表 `hospitals` | 1.92 ms | 3.87 ms | 1.25 ms |

## 5. 业务结果

| 结果 | 数量 |
| --- | --- |
| HTTP 请求总数 | 25,750 |
| HTTP 4xx/5xx | 0 |
| checks 通过 | 51,498 |
| checks 失败 | 0 |

> checks 用于校验 HTTP 200 和业务 `code=0`；本次 checks 全部通过。

## 6. 资源快照

压测结束后采集，非全过程峰值：

| 容器 | CPU | 内存 |
| --- | --- | --- |
| MySQL | 0.51% | 480.9 MiB |
| Redis | 0.33% | 5.4 MiB |
| RabbitMQ | 0.66% | 164.9 MiB |
| SeaweedFS | 7.02% | 138.5 MiB |
| Mailpit | 0.00% | 20.3 MiB |

MySQL 状态：

| 指标 | 值 |
| --- | --- |
| Threads_connected | 12 |
| Threads_running | 2 |
| Innodb_row_lock_current_waits | 0 |
| Innodb_row_lock_waits | 0 |
| Com_select | 69,741 |

## 7. 瓶颈分析

- **场次查询最重**：p95 5.88ms，约为目录接口的 3 倍。它每次请求要查医生、场次和统一设置，且没有走目录缓存，符合预期。
- **目录接口最快**：医院、科室、医生 p95 约 1.9–2.0ms，`test` 环境目录缓存开启，Redis 命中效果明显。
- **预约详情/列表**：p95 约 2.9ms，均是一次联查，符合单表组合查询的成本。
- **通知列表**：p95 2.18ms，数据量只有 1 条，不能代表大数据量下的表现。
- **未观察到饱和**：500 req/s 稳定阶段零 HTTP 失败、零业务校验失败，没有 dropped_iterations，MySQL 行锁等待为 0。
- **客户端不是瓶颈**：100 个预分配 VU 足以支撑 500 req/s，未出现 VU 不足导致的丢迭代。
- **结论**：读路径在 500 req/s 基线下没有成为瓶颈；真正的风险仍在写链路和库存行锁。

## 8. 已知限制

- 只有 1 个就诊人、1 张预约、1 条通知，`/appointments/my` 和 `/notifications` 的结果集很小；本场景用于验证流程和读基线，不代表大结果集下的查询上限。
- 资源指标是压测后的 Docker/MySQL 快照，不是全过程峰值；Hikari 没有 Actuator 指标，只能通过连接数和日志间接判断。
- k6 与后端在同一台 WSL 上，客户端和服务端会争抢 CPU；该结果不能直接等价为独立压测机上的容量。
- 本场景峰值是 500 req/s，不是读路径容量上限；后续如需找读拐点，应单独做更高阶梯。

## 9. 结论

- 场景 C 的脚本、数据准备、报告流程已经跑通。
- 基线结果：500 req/s 稳定 40 秒，p95 4.40ms、p99 6.59ms、HTTP 失败率 0%、业务校验失败 0。
- 下一轮建议：审查本报告后，进入场景 B（单场次抢号正确性），再进入场景 A（下单主链路容量）。

## 10. 复现命令

```bash
# 重置并启动 test
./loadtest/scripts/reset_and_start.sh --yes

# 准备数据
python3 loadtest/scripts/prepare_data.py

# 执行压测
set -a; . ./.env.test; set +a
k6 run -e LOAD_PASSWORD="$LOCAL_SEED_PASSWORD" loadtest/k6/01-read-baseline.js

# 采集资源快照
./loadtest/scripts/collect_metrics.sh
```
