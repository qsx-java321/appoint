# 签到窗口实际验收

执行日期：2026-11-13（Asia/Shanghai）。代码基线为`4bfc1d513bbe7a5649e1e073a5fe60d7efe809d4`，本次交付为未提交工作区，没有新提交或推送。任务01固定规则、02实现、03完整验证、04文档与工具交付。

## 行为与关键取舍

首次签到按预约日期、开始和结束时间快照计算[开诊前30分钟,场次结束)，四场统一公式。查询与写入共享VisitRules；写入按不存在/归属→状态→开放→截止复验，同一次秒精度采样决定资格并保存签到时间。用户列表和详情追加窗口，已签到/完成仍返回窗口；医生DTO和REST不变。

新旧未签到预约统一应用新规则，已签到事实保留；超时未签到继续已确认、NOT_CHECKED_IN，无自动取消、退款、释放库存、删占用或爽约。医生可截止后开始、跨日完成。资金、库存、快照、号码及24小时取消语义保持。

## 实际执行

以下按表序串行执行。完整命令、结束时间和退出码见[final-counts.json](final-counts.json)，逐用例名称与结果保存在各阶段摘要。

| 阶段与命令 | 本次实际结果 | 证据 |
| --- | --- | --- |
| 聚焦规则/HTTP及受影响接口测试（7类，完整-Dtest见命令记录） | 106例通过，零失败/错误/跳过 | [聚焦摘要](final-focused-suites.json) |
| `./mvnw -o clean test` | 381例/45类通过，零失败/错误/跳过；36.569秒 | [常规摘要](final-unit-suites.json) |
| `./mvnw -o -Pmysql-it verify` | 常规381例重复通过；MySQL38例/11类通过，零失败/错误/跳过；2分49秒 | [MySQL摘要](final-mysql-suites.json) |
| `./mvnw -o -Preliability-it verify` | 常规381例重复通过；可靠性16例/2类通过，零失败/错误/跳过；1分46秒 | [可靠性摘要](final-reliability-suites.json) |
| `python3 scripts/compose_acceptance.py --http-client curl --http-report .scratch/visit-checkin-window/verification/curl-report.json` | 隔离curl92请求状态全部符合预期，窗口/进度/库存/快照断言通过 | [脱敏HTTP](curl-report.json) |

聚焦属于完整常规的子集，两个集成profile重复执行常规，不重复累计。Failsafe目录可能保留前个profile XML，摘要分别按MySqlIT/ReliabilityIT后缀提取，不混入旧报告。历史340/38/16/92属于旧检查点，未用作本任务通过证据。

W01～W12逐项入口见[覆盖映射](../../../docs/testing/scenario-coverage.md#签到窗口)。覆盖四场开放前一秒/开放时刻、开诊后、截止前一秒/截止时刻/后一秒，上海跨日，查询到提交跨截止，权限和状态优先级，拒绝请求的九张业务表完整行比较，真实MySQL并发唯一记录/首次时间稳定，以及截止后开始、次日完成。

## 源码检查点与文档工具

[源码摘要](code-manifest.sha256)记录368份源码、测试、配置和脚本，SHA-256为`7a112306e7275a60293a6c16c990fef5b374fa720420a3461f61a808ce335f16`。实际.env/compose.env、缓存和日志不在清单中；Java源码与Maven配置在成功聚焦及完整回归期间未再改动，HTTP脚本由本次curl验证，文档工具由独立静态与反例检查验证。最终检查需与当前树一致。

文档工具保留148份归档来源和原非Markdown证据摘要检查；旧360源码摘要对照Git检查点9c5878b。当前REST/DTO、4503/4504、测试类/方法及W01～W12和本任务新验收独立核验。后续源码漂移显示sourceMatchesWorkingTree=false，不冻结工作区至旧验收，也不把旧通过当作新代码通过。

[工具反例检查](checker-regression-checks.json)验证正常摘要匹配、模拟当前源码漂移能报告、模拟历史源码损坏会拒绝、模拟新验收计数损坏会拒绝。模拟仅替换进程内读取结果，未修改工作区源码或证据。[运行契约检查](runtime-contract-checks.json)确认生产JAR不包含测试时钟/验收启动类，生产没有acceptance.instant入口，无既有迁移修改。

## 失败修正与限制

首次沙箱聚焦无法创建本地socket，Mockito自附加未完成，未作为通过证据；改为本机权限运行隔离测试。初次本机聚焦106例中17例快照断言失败：H2的Outbox JSON为byte[]，嵌套Map.equals比较数组身份。改用递归内容比较，保留完整表/字段，随后106例全部通过，完整回归也通过。

旧规则零点签到的兼容由对象替身/组装器夹具验证，历史起止快照映射由真实H2验证；真实MySQL和HTTP均经正常业务创建新预约并签到，不直接写库伪造就诊状态。没有真实支付渠道或业务前端验收，不修改宿主时钟。

## 资源与交付状态

[资源复核](resource-audit.json)：本轮隔离数据库、MySQL账号与RabbitMQ vhost剩余均为0；三个Compose服务恢复为停止状态，容器和原数据卷保留；项目Java进程和8080监听均为0。原始运行日志在该JSON记录的/tmp目录中暂存，不纳入交付，持久证据为本目录摘要与脱敏HTTP。

正式规则/API/HTTP、业务及模块设计、测试清单/覆盖映射已同步，新增ADR0008；历史档案未改写。原方案、地图和任务全文尚无本任务Git检查点，按issue约定继续保留，待后续明确提交后再处理归档。原有scripts/__pycache__/保留且不纳入提交。

最终执行`python3 scripts/check_documentation.py`与`git diff --check`，均通过；文档静态结果见[documentation-check.json](documentation-check.json)，当前源码与368文件验收摘要一致。任务01～04全部resolved，未提交、未推送。
