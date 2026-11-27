# 压测目录

本目录保存医院预约系统的 k6 压测脚本、数据准备脚本和压测报告。所有压测默认针对 `test` 环境，避免影响 `demo` 和 `dev`。

## 场景顺序

按实现和运行成本从低到高：

| 顺序 | 场景 | 目标 | 状态 |
| --- | --- | --- | --- |
| 1 | C. 读接口基线 | 验证压测脚本和数据准备流程，建立读路径基线 | 已实现 |
| 2 | B. 单场次抢号 | 验证并发下不超卖、不重复预约 | 待实现 |
| 3 | A. 下单主链路容量 | 测最大可持续业务 TPS 和写链路瓶颈 | 待实现 |

每个场景独立重置 test、独立准备数据、独立写报告；审查通过后再进入下一个场景。

## 目录结构

```text
loadtest/
├── README.md
├── .env.example
├── .gitignore
├── k6/
│   ├── lib/
│   │   ├── api.js               # 登录、请求、业务码校验
│   │   ├── metrics.js           # 自定义 Trend 和错误计数
│   │   └── summary.js           # handleSummary 与控制台摘要
│   └── 01-read-baseline.js      # 场景 C
├── scripts/
│   ├── prepare_data.py          # 创建读基线所需的就诊人和已支付预约
│   ├── reset_and_start.sh       # stop → reset → start test
│   └── collect_metrics.sh       # Docker/MySQL 状态快照
├── data/                        # 批量数据，默认不提交
├── results/                     # k6 原始 summary JSON，默认不提交
└── reports/                     # 人工整理的 Markdown 报告，提交
```

## 前置条件

- Docker Compose 已启动共享容器。
- k6 已安装，本项目使用 `k6 v2.3.0`。
- Python 3。
- 已执行 `python3 scripts/local_env.py init`，存在 `.env.test`。
- 所有命令都在项目根目录执行。

## 场景 C：读接口基线

### 1. 重置并启动 test

```bash
./loadtest/scripts/reset_and_start.sh --yes
```

脚本会停止 test、重置 test 数据、重新启动前后端，并检查 `/api/v1/local-environment` 是否就绪。

### 2. 准备数据

```bash
python3 loadtest/scripts/prepare_data.py
```

脚本会：

- 使用 `test-admin@example.local` 登录；
- 创建 1 个就诊人；
- 创建并支付 1 张预约；
- 等待“预约成功”通知异步到达；
- 写出 `loadtest/data/scenario-c.json`。

该文件包含接口 ID 和预约 ID，不包含密码或 JWT。

### 3. 执行 k6

```bash
set -a; . ./.env.test; set +a
k6 run -e LOAD_PASSWORD="$LOCAL_SEED_PASSWORD" loadtest/k6/01-read-baseline.js
```

流量模型：

| 阶段 | 目标 |
| --- | --- |
| 预热 | 50 req/s，10 秒 |
| 爬坡 | 50 → 500 req/s，10 秒 |
| 稳定 | 500 req/s，40 秒 |
| 降坡 | 500 → 0 req/s，10 秒 |

请求权重：

| 接口 | 权重 |
| --- | --- |
| `GET /doctors/{id}/schedules` | 30% |
| `GET /hospitals` | 20% |
| `GET /hospitals/{id}/departments` | 15% |
| `GET /departments/{id}/doctors` | 13% |
| `GET /appointments/{id}` | 10% |
| `GET /appointments/my` | 8% |
| `GET /notifications` | 4% |

阈值：

- 整体 p95 < 100ms，p99 < 300ms；
- 场次查询 p95 < 150ms；
- HTTP 失败率 < 1%；
- 业务校验通过率 > 99%。

### 4. 采集资源快照

```bash
./loadtest/scripts/collect_metrics.sh
```

结果写入 `loadtest/results/docker-stats.txt` 和 `mysql-status.txt`。这是压测后的快照，不是全过程峰值。

### 5. 写报告

复制 `reports/TEMPLATE.md` 为 `reports/01-read-baseline.md`，填入真实结果。报告是提交物，原始 JSON 不提交。

## 数据、密钥与结果

- `loadtest/.env`、`loadtest/data/*.json`、`loadtest/results/*` 已被 `.gitignore` 忽略。
- 不要把真实密码、JWT、批量就诊人数据或原始压测结果提交到 Git。
- 只提交脚本、文档、示例文件和最终 Markdown 报告。

## 指标口径

- **QPS**：HTTP 请求数 / 秒。
- **TPS**：业务事务数 / 秒。场景 C 每次迭代只发 1 个读请求，所以 QPS ≈ 迭代率。
- **业务错误**：HTTP 200 但 `code != 0`，单独计入 `read_business_error`。
- **系统错误**：HTTP 4xx/5xx，用 `http_req_failed` 统计。
- **p95/p99**：分别表示 95%/99% 的请求快于该值。

## 已知限制

- k6 和后端在同一台 WSL 上运行，客户端与服务端会互相争抢 CPU。报告必须注明这一点。
- 场景 C 只有 1 个就诊人和 1 张预约，`/appointments/my` 和 `/notifications` 的返回集很小；它用于验证流程和读路径基线，不代表大数据量下的查询上限。
- 当前没有 Actuator，Hikari 连接池指标只能通过 MySQL 连接数、日志和压测结果间接判断。后续可单独补监控。
