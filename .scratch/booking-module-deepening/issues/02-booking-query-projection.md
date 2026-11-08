# 02 查询流程投影与响应组装分离

Type: refactor
Triage: ready-for-agent
Status: claimed
Blocked by: 01

设计依据：[spec](../spec.md)，执行位置：[map](../map.md)。初始 Status 使用 triage 标签表示待认领；开始实施改为 claimed，验收完成改为 resolved。Triage 不代表依赖已解除。

## 背景和目标

`DefaultBookingQueryService.toResponse` 混合支付完整性、workflow、权限、脱敏和嵌套 DTO。提取 query 内部 Projector/Assembler，让详情与列表主流程保持读取、归属校验和组装。该任务先验证拆分方式，不触及写事务。

## 范围

- 提取 package-private `BookingWorkflowProjector` 和 `AppointmentResponseAssembler`，不增加单实现 interface。
- 保持 `BookingQueryMapper` SQL、`BookingQueryRecord`、公开 `AppointmentService` 和全部响应结构。
- Projector 独立决策表测试，Assembler 保留权限和时间计算；初次提取沿用字符串输入，03 再统一类型化，避免临时重复 enum。
- 查询非法组合/关联错误仍返回 INTERNAL_ERROR 并保留关联 ID 日志。

## 非范围

- 不新增 query 写操作或写事务，不建异步读库。
- 不修正 canCancel 与写入口的既有差异，不按 refund.status 重新判定 workflow。
- 不修改分页/排序/最新尝试定义，不修改 Mapper SQL 或向无关 CRUD 推广 Assembler。

## 前置依赖

[01 基线行为、状态决策表与风险证据](01-baseline-state-matrix.md)。
只有全部前置任务 Status 为 resolved 才能开始；当前仅01可开始。推荐按map串行顺序执行，避免共享快照和调用点冲突。

## 需要修改的模块和主要文件

下列源码路径相对项目根目录；新增类型明确标注，implementation命名可沿用现有风格，不强制为改名搬文件。

- `src/main/java/com/qsx/appoint/bookingquery/application/DefaultBookingQueryService.java`
- 同包新增 `BookingWorkflowProjector.java`、`AppointmentResponseAssembler.java`。
- 参照 `bookingquery/model/WorkflowStatus.java`、`bookingquery/persistence/{BookingQueryMapper,BookingQueryRecord}.java`（SQL 不变）。
- `src/test/java/com/qsx/appoint/appointment/AppointmentQueryIntegrationTest.java`
- 新增同包 `src/test/java/com/qsx/appoint/bookingquery/application/BookingWorkflowProjectorTest.java`。

## 目标代码形态

`getOwned`：mapper.selectById → 原归属校验 → assembler.assemble(record, now)。`listMine`：一次 serverTime → 查询 → 同一 Assembler。Assembler 包含完整性校验和 DTO 构造，调用纯 Projector；内部类与服务同 package，保持 package-private 可访问，不为目录对称移动到不能访问的子包。

## 改造后的业务逻辑

PENDING_PAYMENT 搭配 UNPAID/PAYING/PAID 分别投影等待、处理中、确认中；CONFIRMED+PAID 投影确认；三个关闭预约状态搭配退款中/已退款均投影取消退款中/已退款；EXPIRED+CLOSED 投影过期；CANCELLED 或历史医院取消+CLOSED 返回空 workflow。其他组合仍错误。canPay 只在未过期的 pending+unpaid；canCancel 对 pending+UNPAID/PAYING 即使到期仍 true，对 pending+PAID 仍 false，confirmed+PAID 严格早于开诊前24小时才 true。

## 必须保持的不变量

- 先查询不存在/归属校验，再支付完整性；不能改变信息暴露和错误顺序。
- DTO 字段、Long ID 字符串、BigDecimal 格式、时间截断秒和证件脱敏保持一致。
- Legacy 空金额/旧终态可读；latest attempt 按最大 ID；无退款时摘要为空。
- query 不能被写模块反向依赖。

## 可验证验收条件

- 全部合法组合和非法组合有 Projector 表驱动断言，覆盖 EXPIRED+REFUND_PENDING/REFUNDED 与历史空 workflow。
- 详情/列表的状态、摘要、权限、脱敏、所有权错误与 01 基线相同；列表复用一个 serverTime。
- 缺支付单/归属不一致仍报错；缺失退款摘要不新增业务拒绝。
- SQL 无 diff；生产 application 没有新增事务；公开 HTTP 无 diff。

## 应运行的测试

- `./mvnw -Dtest=BookingWorkflowProjectorTest,AppointmentQueryIntegrationTest,ApiFlowIntegrationTest,ModuleDependencyTest test`（新增测试后执行）
- `./mvnw test`
- 此任务不改锁或 SQL，通常无需重复全部容器测试；若响应集成涉及 MySQL 专有行为，再运行 `./mvnw -Pmysql-it -Dit.test=PatientJourneyHttpMySqlIT verify`。

## 独立交付与回退

仅回退查询服务提取、内部类和新增测试；不撤销 01 基线，无数据回滚。03 尚未开始时可直接回退。
后续任务已使用本任务成果时，先逆序撤销依赖，或做兼容性调整；独立回退指本任务完成时可整体撤销，不保证任意抽掉已被下游使用的提交仍可构建。任何回退不得删除已提交资金、Outbox/Inbox或退款数据。

## 完成状态清单

- [ ] Projector 与全部组合测试完成
- [ ] Assembler 完成并保持 DTO/权限一致
- [ ] 查询主方法简化且 SQL 无改动
- [ ] 聚焦及常规测试通过
- [ ] Comments/地图更新

## Comments

- 2026-11-08：根据源码复核后的spec创建。仅完成任务定义，未认领、未实施；本轮基线结果见map，不等同于本issue验收结果。 依赖尚未resolved，暂不可开始实施。

- 2026-11-08：已拆分 Projector 和 Assembler，并添加状态决策表；尚待最终全量验收。
