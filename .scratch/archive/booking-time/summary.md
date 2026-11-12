# 四场次与预约时间交付

## 阶段与交付

归档核对基线：`1e83c0c6bb4ddfcdfe22196d1309e7e0c04c5bef`，日期2026-11-12。以下记录阶段交付，当前行为由[项目状态](../../../docs/project-status.md)及其现行文档定义。

每天四固定场次，查询不写库，首次下单按需生成并复制统一设置。后续改为上海日期今天至后天十二项，严格提前超过十五分钟创建；锁后采样准入，Java与SQL复验；双方保存相同创建时间与支付截止。下单截止与原单支付资格分别判断。

主要交付提交：9d20e30（四场次）、cf02768（三天窗口）。原issue的关闭记录提交列在下表，可能是验收文档提交，不等同于全部实现修改所在提交。

## 演进与边界

ADR0007替代ADR0005的七天/禁止当天/支付截止规则；四场、快照与按需生成保留。历史场次不拆分，历史预约与原期限不随入口滚动重算。

## four-session-booking

历史文件提交范围：2026-11-05至2026-11-12（含后续文档复核，具体实现/验收时间以记录为准）。

原任务均已完成；下表保留编号、标题和状态关闭记录，原始全文按文末Git方法读取。

| 任务 | 标题 | 状态 | 关闭记录提交 |
| --- | --- | --- | --- |
| 01 | 01 统一设置与数据库迁移 | resolved | `9d20e30` |
| 02 | 02 未来七天场次目录 | resolved | `9d20e30` |
| 03 | 03 按需生成与预约创建 | resolved | `9d20e30` |
| 04 | 04 支付窗口与生命周期简化 | resolved | `9d20e30` |
| 05 | 05 验证与文档收口 | resolved | `9d20e30` |

### 验收与实施记录

下列记录属于该阶段；旧规则、旧类名和临时路径保持历史含义。

**任务01**

- 已新增 V9 迁移、默认单行设置及管理员 GET/PUT；H2 迁移测试通过，MySQL 验证由最终 profile 执行。

**任务02**

- 已集中定义四个固定时段，返回 D+1 至 D+7 共 28 项；未生成场次查询不写库。

**任务03**

- 已切换创建请求与幂等摘要，实现按需生成、最终写入日期校验和原子暂占；新增 MySQL 21 人竞争测试。

**任务04**

- 支付截止固定为创建后 15 分钟，等于截止时刻拒绝确认并退款；手工场次 REST 和新停诊写路径已退出。

**任务05**

- `./mvnw -Pmysql-it verify` 已通过：98 个常规测试、24 个 MySQL 集成测试全部成功；覆盖 V9 迁移、真实数据库约束、21 人并发竞争 20 个号源及完整 HTTP 流程。
- README、API、业务流程、HTTP 示例、术语和 ADR 已同步。

## three-day-booking-time

历史文件提交范围：2026-11-11至2026-11-12（含后续文档复核，具体实现/验收时间以记录为准）。

原任务均已完成；下表保留编号、标题和状态关闭记录，原始全文按文末Git方法读取。

| 任务 | 标题 | 状态 | 关闭记录提交 |
| --- | --- | --- | --- |
| 01 | 01 共享预约时间规则 | resolved | `cf02768` |
| 02 | 02 三天场次查询与响应 | resolved | `cf02768` |
| 03 | 03 预约创建、准入时间与条件占号 | resolved | `cf02768` |
| 04 | 04 正常支付、查询与就诊链路 | resolved | `cf02768` |
| 05 | 05 现行文档同步与整体验收 | resolved | `cf02768` |

### 验收与实施记录

下列记录属于该阶段；旧规则、旧类名和临时路径保持历史含义。

**任务01**

- 2026-11-11：共享纯规则已实现；`./mvnw -o -Dtest=BookingTimeRulesTest test` 9 项通过，0 失败、0 错误、0 跳过，日志 `/tmp/three-day-01.log`。覆盖三天滚动、四场严格边界、日期资格与支付 min/跨零点，01 resolved。

**任务02**

- 2026-11-11：十二项查询与四状态已实现；聚焦命令在允许 JVM 附加的环境执行，32 项通过、零失败/错误/跳过，日志 `/tmp/three-day-02.log`。首次沙箱内 Mockito 附加受限的运行不计为验收。02 resolved。

**任务03**

- 2026-11-11：01、02 已验收，开始创建事务、准入时间和条件占号改造。

- 2026-11-11：锁后准入、三天/十五分钟 SQL 和双时间快照已实现；常规 `./mvnw -o test` 258 项通过（`/tmp/three-day-03-all.log`）；真实 MySQL 聚焦命令 `./mvnw -o -Pmysql-it -Dtest=BookingTimeRulesTest -Dit.test=AppointmentLifecycleMySqlIT verify` 中规则 9 项、MySQL 13 项通过，均零失败/错误/跳过（`/tmp/three-day-03-mysql.log`）。新增同日 SQL 截止实测；旧夹具改用窗口内日期及独立医生。03 resolved。

**任务04**

- 2026-11-11：02、03 已验收，开始正常支付、历史查询与当天就诊联调测试，并适配 Compose 日期。

- 2026-11-11：正常链路聚焦 30 项通过（`/tmp/three-day-04.log`）；真实 MySQL/TCP HTTP 的 PatientJourneyHttpMySqlIT 2 项通过（`/tmp/three-day-04-mysql.log`），均零失败/错误/跳过。已覆盖截止后原单支付、跨零点、历史查询、三处期限一致及当天签到接诊；脚本改为后天，语法检查通过。04 resolved，完整 Compose 在 05 执行。

**任务05**

- 2026-11-11：01～04 已验收，开始现行文档同步与完整 MySQL、Compose 及静态校验。

- 2026-11-11：正式文档、ADR 0007、术语和 HTTP 示例已同步。最终 `./mvnw -o -Pmysql-it verify` 常规 263/MySQL 37 均零失败/错误/跳过；完整 Compose/curl 92 次请求符合预期，成功运行 e34a8b021efb。具体命令、日志、代码摘要和首次报告目录失败记录见验证报告；S01～S18 证据已落盘。未重跑 reliability-it 专用 profile，未验证真实渠道或前端。05 resolved。

## 证据与当前阅读

- [three-day-booking-time/code-manifest.sha256](evidence/three-day-booking-time/code-manifest.sha256).
- [three-day-booking-time/curl-report.json](evidence/three-day-booking-time/curl-report.json).
- [three-day-booking-time/scenario-evidence.md](evidence/three-day-booking-time/scenario-evidence.md).
- [business-flow.md](../../../docs/business-flow.md).
- [module-design.md](../../../docs/module-design.md).
- [scenario-coverage.md](../../../docs/testing/scenario-coverage.md).
- [verification-report.md](../../../docs/verification-report.md).

## 原文追溯

逐文件去向见[清单](../../archive-manifest.json)。原始路径与摘要由清单保存；例如：

```bash
git show 1e83c0c6bb4ddfcdfe22196d1309e7e0c04c5bef:.scratch/four-session-booking/spec.md
```

旧报告中的/tmp日志不承诺持久存在；历史通过不代表本次重跑。
