# 测试与文档收尾交付

## 阶段与交付

归档核对基线：`1e83c0c6bb4ddfcdfe22196d1309e7e0c04c5bef`，日期2026-11-12。以下记录阶段交付，当前行为由[项目状态](../../../docs/project-status.md)及其现行文档定义。

测试八步完成：断言、历史迁移边界、业务钟与夹具、隔离与故障恢复、重复职责合并、启动和扫描、可读性与审计、完整验收。保留真实提交/并发/消息故障窗口和原样本，测试数量不作为整理目标。文档对齐阶段修正完成状态、旧页面与规则替代，保持历史验收事实。

主要交付提交：71e4ec8（测试收口，源码9c5878b）、1e83c0c（文档对齐）。原issue的关闭记录提交列在下表，可能是验收文档提交，不等同于全部实现修改所在提交。

## 演进与边界

常规323→340、测试类57→56；MySQL38、可靠性16保留。第04步初报258方法因简化脚本漏注解，最终校正260；不是执行用例数。曾暂停04后已恢复并完成08。340/38/16及curl92为历史实测，本轮仅重验文档。

## test-suite-refactor

历史文件提交范围：2026-11-12至2026-11-12（含后续文档复核，具体实现/验收时间以记录为准）。

原任务均已完成；下表保留编号、标题和状态关闭记录，原始全文按文末Git方法读取。

| 任务 | 标题 | 状态 | 关闭记录提交 |
| --- | --- | --- | --- |
| 01 | 01 建立测试清单、场景映射和执行基线 | resolved | `7e72e36` |
| 02 | 02 提高断言准确性并固定历史迁移边界 | resolved | `bbbb6d0` |
| 03 | 03 整理时钟与业务夹具 | resolved | `f46e49b` |
| 04 | 04 完善隔离、故障恢复和有界等待 | resolved | `c1b7fb3` |
| 05 | 05 合并消息测试重复职责 | resolved | `048c9bf` |
| 06 | 06 减少测试启动和重复扫描 | resolved | `861bb64` |
| 07 | 07 完善可读性、规范和场景审计 | resolved | `9c5878b` |
| 08 | 08 完整验收与文档收口 | resolved | `71e4ec8` |

### 验收与实施记录

下列记录属于该阶段；旧规则、旧类名和临时路径保持历史含义。

**任务01**

- 新执行常规323、MySQL38、可靠性16全部通过，零失败/错误/跳过；文档链接、8项任务及57类清单通过。起点963051f。

**任务02**

- 聚焦常规10、真实MySQL4全部通过。首次CHECK断言发现实际包装为UncategorizedSQLException，改为精确验证3819/HY000/约束名后重跑成功。临时编辑脚本语法错误未改源码；此前预执行不计改造验收。

**任务03**

- 聚焦常规41、就诊MySQL1通过；可变钟统一为support配置，所有使用者每例重置，原UTC时点转换为等价上海时间。保留FormalPaymentTestSupport等已有夹具，不机械再抽象一次性SQL。

**任务04**

- 聚焦73通过；顺序A常规323+完整MySQL38通过，顺序B常规323+受影响MySQL32通过；完整可靠性两轮各16通过，A另验收19项收尾常规，B最终源码额外5项确认测试。全部零失败/错误/跳过。用户要求本步提交后暂停，05～08不启动。

**任务05**

- 聚焦85、完整可靠性16通过，零失败/错误/跳过。删除独立基线类和两个Listener各2项重复分支；独有断言迁移、新增I/O确认矩阵及S01～S32入口映射已登记。

**任务06**

- 完整常规340项通过，架构20项、MockPay契约7项保留；契约不启动Spring，生产扫描static final复用，三个profile保护完整。实际耗时及首次编译修正见startup-evidence.md。

**任务07**

- 聚焦常规111+MySQL10、确认单测5+Outbox可靠性9通过；260原方法全部有去向，19受保护样本/架构负例不变。补充类职责、去除重复注入/全限定噪音、统一架构正反例规则、验证故障实际调用，测试规范已落盘。临时清理脚本字段名检查中止后按实际jdbcTemplate修正，无生产修改。

**任务08**

- 干净常规340、完整MySQL38、完整可靠性16、隔离curl92全部通过，零失败/错误/跳过；按日志筛选报告避免旧XML累计。数据库/账号/vhost精确只读复核均清理，应用端口关闭，Compose健康；原生产代码及360被测摘要一致。八项全部完成。

## documentation-alignment

历史文件提交范围：2026-11-12至2026-11-12（含后续文档复核，具体实现/验收时间以记录为准）。

原任务均已完成；下表保留编号、标题和状态关闭记录，原始全文按文末Git方法读取。

| 任务 | 标题 | 状态 | 关闭记录提交 |
| --- | --- | --- | --- |
| 阶段规格 | 已交付，见阶段背景与交付提交 | resolved | 以Git交付记录为准 |

### 验收与实施记录

下列记录属于该阶段；旧规则、旧类名和临时路径保持历史含义。

### 文档错位与处置

核对基线：71e4ec8。当前事实来自源码、Git和该交付实际验收，不把旧规格的目标当作未完成任务。

| 问题 | 证据与处置 |
| --- | --- |
| README当前进度混排263、323和340，容易误读 | 当前摘要统一340/38/16/92与71e4ec8；旧数字保留在带日期的历史报告 |
| 就诊改动说明仍称最新263/37、可靠性未重跑 | 更新指向2026-11-12测试整理最终验收，不改写当时241/36/15历史执行记录 |
| 就诊设计称Case缺少doctorId、需要增加 | 实际record和构造点已具备，改为已实现，实施单元标为已完成 |
| 查询规格ready-for-agent但功能已交付 | e785933已补查询字段，现由bookingquery实现；总状态resolved，原背景标为实施前历史 |
| 支付总图active、spec ready，与16项resolved冲突 | a293afa已交付，统一resolved；差距表标明2026-10-29实施前状态 |
| 半日排班需求仍ready-for-human | 1d2f4c5已实现库存，后被四场及三天窗口部分取代；归档resolved，不补造人工验收结论 |
| UI规格ready，旧页称当前可打开 | 六项历史任务完成；Git确认独立页在9d20e30删除，所有相关记录标明当前无该页面/业务前端 |
| V1清库、同步模拟支付、七天28项、手工停诊等旧步骤混用 | 对相关历史spec/map/issue添加阶段角色、替代关系及现行入口，不执行旧调用/清库说明；ADR0005已有替代标记，保留原决策 |
| 消息历史验收仍引用已合并基线类 | 历史文件保留963051f语境，补当前S01～S32映射入口；不改写旧源码摘要和旧执行数字 |
| 测试地图第08步提交哈希未补全、暂停语句仍止于07 | 填入71e4ec8、明确05～08完成，默认导航改为当前清单并保留基线入口 |
| 第04步方法清单初报258 | 正确值260；当时简化脚本漏两项全限定注解。保留修正说明，不影响实际执行用例323 |
| HTTP固定年月日可能超出窗口或不满足24小时取消 | 预约/就诊日期变量留空标为运行前必填，从服务端目录/真实预约选择；不引入未验证的日期计算脚本 |
| API公式把默认15分钟写成不可配置常量 | 对照BookingTimeRules/配置，写明paymentTimeout默认15分钟及min计算 |


## 证据与当前阅读

- [documentation-alignment/report.md](evidence/documentation-alignment/report.md).
- [documentation-alignment/results.json](evidence/documentation-alignment/results.json).
- [test-suite-refactor/code-manifest.sha256](evidence/test-suite-refactor/code-manifest.sha256).
- [test-suite-refactor/commands.md](evidence/test-suite-refactor/commands.md).
- [test-suite-refactor/curl-report.json](evidence/test-suite-refactor/curl-report.json).
- [test-suite-refactor/current-inventory.md](evidence/test-suite-refactor/current-inventory.md).
- [test-suite-refactor/current-test-methods.json](evidence/test-suite-refactor/current-test-methods.json).
- [test-suite-refactor/final-acceptance.md](evidence/test-suite-refactor/final-acceptance.md).
- [test-suite-refactor/final-counts.json](evidence/test-suite-refactor/final-counts.json).
- [test-suite-refactor/final-mysql-suites.json](evidence/test-suite-refactor/final-mysql-suites.json).
- [test-suite-refactor/final-reliability-suites.json](evidence/test-suite-refactor/final-reliability-suites.json).
- [test-suite-refactor/final-unit-suites.json](evidence/test-suite-refactor/final-unit-suites.json).
- [test-suite-refactor/inventory.md](evidence/test-suite-refactor/inventory.md).
- [test-suite-refactor/message-scenarios.md](evidence/test-suite-refactor/message-scenarios.md).
- [test-suite-refactor/method-audit.md](evidence/test-suite-refactor/method-audit.md).
- [test-suite-refactor/original-environments.json](evidence/test-suite-refactor/original-environments.json).
- [test-suite-refactor/original-junit-methods.json](evidence/test-suite-refactor/original-junit-methods.json).
- [test-suite-refactor/original-methods.json](evidence/test-suite-refactor/original-methods.json).
- [test-suite-refactor/original-test-methods.json](evidence/test-suite-refactor/original-test-methods.json).
- [test-suite-refactor/production-baseline.sha256](evidence/test-suite-refactor/production-baseline.sha256).
- [test-suite-refactor/resources.json](evidence/test-suite-refactor/resources.json).
- [test-suite-refactor/startup-evidence.md](evidence/test-suite-refactor/startup-evidence.md).
- [test-suite-refactor/static-checks.md](evidence/test-suite-refactor/static-checks.md).
- [test-suite-refactor/step-04-isolation.md](evidence/test-suite-refactor/step-04-isolation.md).
- [test-suite-refactor/step-04-method-audit.md](evidence/test-suite-refactor/step-04-method-audit.md).
- [test-suite-refactor/test-baseline.sha256](evidence/test-suite-refactor/test-baseline.sha256).
- [test-suite-refactor/test-step-04.sha256](evidence/test-suite-refactor/test-step-04.sha256).
- [business-flow.md](../../../docs/business-flow.md).
- [module-design.md](../../../docs/module-design.md).
- [scenario-coverage.md](../../../docs/testing/scenario-coverage.md).
- [verification-report.md](../../../docs/verification-report.md).

## 原文追溯

逐文件去向见[清单](../../archive-manifest.json)。原始路径与摘要由清单保存；例如：

```bash
git show 1e83c0c6bb4ddfcdfe22196d1309e7e0c04c5bef:.scratch/test-suite-refactor/spec.md
```

旧报告中的/tmp日志不承诺持久存在；历史通过不代表本次重跑。

历史详细说明：[project-verification-history](evidence/project-verification-history.md)。
