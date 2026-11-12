# 09 正式文档同步与完整验收

Type: docs/test
Triage: ready-for-agent
Status: resolved
Blocked by: 08

依据：[spec 第 8～12 节](../spec.md)。重点责任：S29～S32；负责全场景与整体交付核对。

## 目标

让正式文档描述最终实现，串行完成用户确认的四类验收，记录真实结果、限制和资源状态。仅方案完成不等于本任务完成。

## 文档清单

- `docs/module-design.md`：目标包结构、接口契约、事件 record、事务、失败处理和代码阅读入口。
- `docs/runbooks/payment-module-cutover.md`：新的重放逻辑目标/结果和实际调用方式；保留 FAILED 人工边界、原文/ID 与不删 Inbox 的规则。
- `README.md`：仅更新实际已经实现的消息设计与源码入口，不把技术细节放进用户业务流程。
- `docs/verification-report.md`：追加日期、实际检查点、命令与结果，保留历史章节，不全局替换旧数字。
- `docs/api.md`、`docs/business-flow.md`：检查业务/HTTP 未变，必要时修正陈旧说明；无变化不为了文件数量修改。
- 本目录 spec/map/scenarios/issues 与 verification：由实施目标更新为真实结果，保留确认与历史 Comments。

本轮不强制新增 ADR。若实施发现需要推翻现有 ADR 或引入新架构机制，先说明与用户确认的范围冲突；按项目 domain 约定处理，不默默扩大方案。

## 最终验收

在项目根目录串行执行，实际离线选项如实记录：

```bash
./mvnw clean test
./mvnw -Pmysql-it verify
./mvnw -Preliability-it verify
docker compose --env-file compose.env up -d --wait
python3 scripts/compose_acceptance.py --http-client curl --http-report .scratch/messaging-design-refactor/verification/curl-report.json
git diff --check
```

08 基于相同最终代码刚完成的完整 profile 可以引用，但要明确被测代码一致并说明实际执行时间；新增代码/测试变更需重跑受影响门禁。不能以“不想重复执行”为理由省略尚未完成的最终覆盖。

核对 `curl-report.json` 所有 expectedStatus，正常预约确认、取消退款、迟到收款释放退款、双方就诊进度与库存/快照守恒。HTTP 链路不是消息故障专项的替代，MockPay 不是实际支付接入证明。

## 静态与证据检查

- 本地 Markdown 链接、代码围栏、JSON 样本、任务编号和依赖无环、map/issue 状态一致。
- S01～S32 每项有真实测试/命令映射，无用旧测试结果充数。
- 常规与两 profile 的重复常规测试不累计成新增数量。
- 保存相关代码摘要、实际命令与日志；临时日志易失的限制写清。
- 脱敏 curl 与样本，不纳入真实 .env、compose.env、Token、证件或密码。
- 成功隔离资源已按脚本清理，旧业务数据/卷/失败现场保留；验收进程退出，依赖服务终态记录。

## 完成条件

- [x] 正式文档与源码/接口/队列一致，能按三条代码阅读路径理解正常与故障行为。
- [x] 四类最终门禁全部通过；无意外跳过或未解决环境阻塞。
- [x] S01～S32 有证据，所有前序任务真实 resolved，资源/数据检查完成。
- [x] map/spec 及本任务同步 resolved，报告明确尚未实现的通知、真实渠道和租约优化。

若存在未完成项，保持 claimed 并具体记录；不能为了收口宣布成功。用户已授权每步验收后提交，本任务完成后同样单独提交，不推送。

## Comments

- 2026-11-12：任务已定义，依赖 08；正式文档与业务代码尚未改造，未执行最终验收。

- 2026-11-12：正式设计、README、恢复手册、逐场景证据同步；最终常规323、完整MySQL38、可靠性16、隔离curl92全部成功，零失败/错误/跳过。四条重放普通测试及序列化失败回滚断言在收口补齐并重验。命令、源摘要、资源清理及限制见verification/；九项完成，每步验收后单独提交，不推送。
