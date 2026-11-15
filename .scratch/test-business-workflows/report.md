# test 环境业务流程实测报告

- 日期：2026-11-15（Asia/Shanghai）。任务：01。
- 源码检查点：`6c1ffe57b709cd0a8a7c7d994f4b1863b31a17dc`；启动前 Git 工作区干净，未修改业务代码。验收执行时新增资料尚未提交；现与三环境验收结论一并提交，保留原任务和证据。
- 目标：正式 test profile 上完成注册到接诊完成及重要业务分支，整理人工体验路线。
- 范围：真实 MySQL `appoint_test`、Redis namespace `test`、RabbitMQ vhost `/appoint-test`，正式 Spring Boot 8081、Vue 5174；没有使用 H2、测试 classpath 应用或独立临时验收库替代。

## 执行与结论

1. 启动前脚本记录及监听端口没有前后端实例，Compose 没有运行容器。启动共享依赖和正式 test 服务；日志确认 profile、数据库与 vhost，Flyway V11。
2. 测试前只读确认：test 有 1 个用户、0 条预约、5 个场次。保留原有数据，新增带 `20261115-132151` 标记的账号和目录。
3. [接口脚本](verify.py)完成 20 组检查，250 次顺序请求状态均符合预期；另 3 次并发抢号返回 201、201、409，失败业务码 4002。
4. [浏览器适配脚本](browser.py)复用项目真实 Compose 页面测试，连接已经运行的正式 test 前后端。完整复跑通过 1 项流程，143 次真实页面响应，页面异常 0，耗时 23.6 秒（测试主体 22.9 秒）。
5. [审计脚本](audit.py)只读核对本轮所有订单和真实依赖；包括首轮页面脚本留下的业务记录。共 18 条预约，5 笔退款成功，22 条 Outbox 已发布（PaymentSucceeded 12、RefundRequested 5、RefundSucceeded 5）；12 条支付成功均有 Inbox。五项不变量违例均为 0，死信队列为空，Redis 中存在 test 目录缓存。
6. 已恢复统一设置为原来的容量 20、费用 10.00；恢复实时时钟。保留 test 前后端、Compose 和本轮测试数据供用户体验。未重启后端，MockPay 内存渠道状态随当前实例保留。

人工业务步骤、关键时间边界和保留记录见[体验清单](../../docs/test-business-workflows.md)。

## 验证覆盖

| 范围 | 实际结果 |
| --- | --- |
| 身份与归属 | 注册、邮箱规范化去重、错误凭据、匿名访问、就诊人/预约/支付归属、用户管理越权、医生归属均符合预期 |
| 就诊人和目录 | 新增、编辑、启停、证件格式及重复限制；医院科室医生管理、被引用删除保护、首次绑定及重复绑定拒绝；历史凭证不被资料变更改写 |
| 预约和库存 | 三天十二场、日期和下单截止、幂等重放及内容冲突、有效预约重复、三人并发抢两个名额、关闭释放占用、序号不复用 |
| 支付退款 | 支付失败重试、真实签名通知、重复通知、无效签名拒绝、Outbox/RabbitMQ/Inbox确认、提前取消退款、重复取消、24小时边界、超时与迟到退款 |
| 到院接诊 | 开诊前30分钟签到开放、场次结束首次签到拒绝、重复动作保护、接诊顺序、跨日接诊、四状态看板、双方时间一致、完成后库存保留 |
| 管理设置 | 容量/费用校验；修改只影响之后首次生成场次，已有场次和订单快照不变；结束还原原设置 |

## 首轮浏览器失败及修正

首轮[日志](browser-first-run.log)与[结果](evidence/browser-first/results.json)记录一次验收脚本失败：旧测试使用冻结 Clock，断言签到及详情时间为 `13:40:00`；正式 test 使用走秒的可调 Clock，实际签到时间为 `13:40:24`。第一次适配漏改详情处的第二个固定秒断言。

修正本轮临时复制脚本的两处断言，验证日期和 `13:40` 分钟，并沿用后续用户/医生完整时间字段相等断言。重新生成全新用户、医生和医院后，从注册到接诊完成整条复跑通过。正式测试文件与业务代码均未改动。首轮的三条订单保留，最终审计包含这些记录；首轮失败不隐藏，也不计入通过数量。

## 执行命令

从项目根目录、Node 24.18.1 环境执行：

```bash
docker compose --env-file compose.env up -d --wait
python3 scripts/local_env.py start test
python3 .scratch/test-business-workflows/verify.py
python3 .scratch/test-business-workflows/browser.py
python3 .scratch/test-business-workflows/audit.py
python3 scripts/local_env.py clock test get
python3 scripts/check_documentation.py
```

浏览器入口使用本机缓存的 Chrome 156，通过 Playwright 驱动；没有启动第二套后端和前端。脚本中的运行私密文件位于被 Git 忽略的 `.local-env/workflow-browser/`，账号凭据位于 `.local-env/workflow-actors.json`；公开证据不保存 Token、密码、完整证件和通知签名。

## 证据

- [20组检查](evidence/checks.json)、[顺序HTTP响应](evidence/http.json)、[记录与并发结果](evidence/records.json)、[接口执行日志](evidence-run.log)。
- [最终浏览器结果](evidence/browser/results.json)、[页面HTTP摘要](evidence/browser/http-summary.json)、[页面异常](evidence/browser/page-errors.json)、[新增记录ID](evidence/browser/created-records.json)、[执行日志](browser-run.log)。
- [医生完成截图](evidence/browser/doctor-completed.png)、[退款完成截图](evidence/browser/refund-completed.png)。
- [数据库与Redis审计](evidence/database-audit.json)、[RabbitMQ队列](evidence/rabbit-queues.txt)、[源码文件摘要](evidence/source-manifest.sha256)。
- [文档检查结果](evidence/documentation-check.log)。

## 限制

没有发现本轮覆盖范围内的业务阻断缺陷；一次浏览器失败来自验收脚本固定时钟假设，已修正并整链复验。

没有执行真实支付渠道、Broker故障/ACK窗口/跨重启恢复注入、长期稳定性和完整移动端布局回归，也没有重跑 Maven 全套测试；这些能力不能根据本轮日常业务验收宣布通过。就诊完成不涉及尚未实现的病历、处方与自动叫号。

本轮调时可能令后天预约记录先进入接诊完成再恢复当日时钟，符合 test 调时保留业务事实的规则；不应将这些合成时间记录解读为真实临床操作时间。
