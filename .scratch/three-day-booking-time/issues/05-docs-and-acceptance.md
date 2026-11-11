# 05 现行文档同步与整体验收

Type: docs
Triage: ready-for-agent
Status: resolved
Blocked by: 01, 02, 03, 04

设计依据：[spec 第 11、12 节](../spec.md)。执行位置：[map](../map.md)。覆盖要求：[scenarios](../scenarios.md)。

## 目标与前置条件

01～04 均 resolved 后，按最终实现同步正式文档、架构决策与 HTTP 示例，执行本轮整体验收，记录可追踪结果。不能在生产尚未修改时把正式 API 文档写成已经上线的新行为。

## 文档与文件清单

- `README.md`：三天十二项、当天创建和当前进度。
- `docs/api.md`：场次 GET 的三个新增字段、三天范围、创建准入、支付期限；逐项核对真实 DTO。
- `docs/business-flow.md`：查询→创建→支付→确认→就诊的现行时间语义和正常场景。
- `docs/module-design.md`：共享规则位置、请求登记/准入/响应时间及截止传递，保持现有模块职责。
- `docs/visit/design.md`：更新公共预约目录的旧 D+1～D+7 描述；医生当天查询仍使用原接口。
- `docs/visit/change-scope.md`：若其中“当前不变范围”与本次交付冲突，补充后续变更说明，保留原交付日期和历史语境。
- `docs/runbooks/payment-module-cutover.md`：更新正常冒烟预期为十二项；本轮没有新增数据库迁移，不改写旧迁移事实。
- 新增 `docs/adr/0007-three-day-booking-and-session-cutoffs.md`：记录已认可的新窗口、下单/支付截止、锁后准入时间和旧订单保留；明确替代 ADR 0005 的对应部分，保留其余决定与 ADR 0006。
- `GLOSSARY.md`：按项目 domain 文档及适用技能补充“下单截止时间”“预约准入时间”等实际采用的术语；不重定义账号用户与就诊人。
- `http/appointment.http`：日期和说明改为三天，新下单示例使用符合截止的场次，支付仍走原正式 attempt/MockPay 流程。
- `docs/verification-report.md`：追加本次命令、代码检查点、实际结果和限制，不把历史 28 项或测试数量全局替换。
- 本目录 `spec.md`、`map.md`、`scenarios.md` 和各 issue：以实际实现更新状态和覆盖证据。

正式 ADR/术语更新遵循项目 domain 文档与适用的 domain-modeling 技能。任务拆解阶段未预先声明实现完成；实现验收后已新增 ADR 0007，并更新现行文档。

## 业务与契约核对

- [x] 三天范围包含今天、明天、后天，接口十二项并有统一 serverTime。
- [x] 四场和截止时间准确，availability 与 bookable 一致。
- [x] 请求仍传医生、具体日期、场次类型和就诊人；无客户端截止或价格入参。
- [x] 首次 201、原幂等重放 200、支付尝试 202、详情 200 等现有成功契约保持。
- [x] 下单截止和原单支付期限分开；已有订单的截止不重算。
- [x] 预约/支付时间一致，跨零点可付，当天创建后可继续签到接诊。
- [x] 表结构、事件版本及历史记录未因本轮时间规则被重建或回填。
- [x] S01～S18 在对应验证层次有实际证据；不要求把受控时钟边界全部搬进真实时间的 Compose 执行。

## 整体验收命令

从项目根目录串行执行，实际使用的离线/在线选项如实记录：

```bash
./mvnw test
./mvnw -Pmysql-it verify
python3 scripts/compose_acceptance.py --http-client curl --http-report .scratch/three-day-booking-time/verification/curl-report.json
git diff --check
```

执行 Compose 验收前检查 Docker、镜像和脚本离线 Maven 依赖条件。需要启动本地依赖时，按项目约定使用 `docker compose --env-file compose.env up -d --wait`，不输出凭据，不删除既有卷。

保留原有全部测试，不为本轮添加新的退款或消息故障专项。若项目要求或实际改动需要追加既有 `reliability-it` profile，串行执行并单独记录，不以此前通过代替本轮结果。

另做 Markdown 本地链接、代码围栏、JSON 示例、任务依赖和 map/issue 状态一致性检查。业务命令的通过与文档静态检查分开报告。

## 完成记录与验收标准

- [x] 记录每条实际命令、退出结果、日志或脱敏报告路径、实现代码检查点。
- [x] 常规和 MySQL profile 的重复常规测试不累计成新增数量。
- [x] 真实 HTTP 正常链路成功，支付渠道仍明确为 MockPay；未验证内容单独写明。
- [x] 更新 map 完成数和全部任务状态，保留原 Comments 历史。
- [x] 正式文档与交付代码一致，S 编号证据可追踪，才将本任务标记 resolved。

记录实际实现检查点，区分方案文档、生产改造与已执行验收的交付状态。

## Comments

- 2026-11-11：完成任务定义；尚未认领，01～04 未 resolved，本轮未执行 Maven、Compose 或业务 HTTP 验收。

- 2026-11-11：01～04 已验收，开始现行文档同步与完整 MySQL、Compose 及静态校验。

- 2026-11-11：正式文档、ADR 0007、术语和 HTTP 示例已同步。最终 `./mvnw -o -Pmysql-it verify` 常规 263/MySQL 37 均零失败/错误/跳过；完整 Compose/curl 92 次请求符合预期，成功运行 e34a8b021efb。具体命令、日志、代码摘要和首次报告目录失败记录见验证报告；S01～S18 证据已落盘。未重跑 reliability-it 专用 profile，未验证真实渠道或前端。05 resolved。
