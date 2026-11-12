> 整理前正式文档的历史快照，检查点 `1e83c0c`。旧路径及代码步骤按当时理解；当前内容见[项目状态](../../../../docs/project-status.md)。

# 就诊功能改动范围与前后变化

日期：2026-11-11。状态：到院签到与门诊接诊已实现；本文保留该功能交付时的改造范围和验收记录。随后完成的三天预约改造及最新结果见 [验证报告](../../delivery-quality/evidence/project-verification-history.md#2026-11-11-三天预约窗口与截止规则交付)。

关联：[需求](../summary.md)、[设计](../../../../docs/visit/design.md)、[API](../../../../docs/visit/api.md)。

设计文档已进一步细化到具体类与方法：第 6 节为完整包布局，第 8 节为 API 调用映射，第 9–14 节为迁移 SQL、正常业务步骤、查询算法、DTO 和既有文件改造，第 15 节为分步交付与验证。

## 1. 本次实际改动

在先前需求与文档基础上，已新增 V10、医生账号关联与角色、visit 业务和 bookingquery 医生查询，扩展原用户预约响应与排序，并补充测试和 HTTP 示例。

已实现新增 7 个接口和原 2 个查询接口扩展；未创建前端工程。以下对照表比较到院功能改造前与该功能交付后的行为。

## 2. 用户和医生操作前后变化

| 业务位置 | 到院功能改造前行为 | 到院功能交付后行为 |
| --- | --- | --- |
| 普通用户入口 | “我的预约”查询预约快照和资金流程 | 入口保留，原详情中增加就诊进度和签到 |
| 我的预约排序 | 按预约 ID 降序 | 默认按就诊日期、开始时间、ID 降序，可切换升序 |
| 已确认预约详情 | 显示预约成功信息 | 显示“已预约，待签到”；就诊当天签到可用 |
| 用户到院后 | 没有签到动作 | 点击签到后显示候诊中并记录签到时间 |
| 医生身份 | 有医生档案，没有医生登录关联 | 管理员绑定账号并授予 DOCTOR，沿用原登录 |
| 医生工作入口 | 没有医生场次看板 | 默认今天，查看四场概况、全部已确认预约人员 |
| 医生名单 | 没有就诊操作 | 按原就诊序号固定排序，三个按钮按状态启用 |
| 用户查看就诊进度 | 没有签到和接诊进度 | 原预约随着医生操作显示就诊中、已就诊 |
| 本次就诊结束 | 没有接诊完成记录 | 医生完成后保存终态与时间，双方仍可查看 |

“已就诊”表示本次接诊完成。未签到、未完成的记录不被时间任务自动标记完成；本期没有新增这样的任务或状态。

## 3. 新增与修改范围

| 区域 | 实际变更 |
| --- | --- |
| 数据库 | 新增就诊记录表；医生新增可空唯一账号关联；新增 DOCTOR 角色；增加用户时间排序和医生日期查询索引 |
| `catalog` | 医生账号关联、按账号解析医生 ID；保留已有目录 CRUD |
| `user` | 增加内部医生角色授予操作；注册默认角色保持 USER |
| `visit` | 新增两个端的 HTTP 入口、VisitService、规则、状态、记录及 Mapper |
| `appointment.application` | 预约事实 `AppointmentLifecycleCase` 增加 doctorId，并更新映射和构造处 |
| `appointment.service` | 既有查询契约增加排序参数重载，保留旧签名 |
| `appointment.dto` | AppointmentResponse 增加 visit、sessionType；新增纯响应 DTO 与排序枚举 |
| `bookingquery` | 用户联查增加就诊字段与排序；新增医生概览、名单、详情只读查询 |
| 测试 | 预约事实、DTO 构造适配；新增正常就诊、查询和架构检查；保留原有回归 |
| HTTP 示例与现行文档 | 新增 http/visit.http，并将就诊接口同步到现行文档 |
| 前端接入（本仓库范围外） | 可使用本文接口实现进度、按钮、看板及账号绑定，无前端工程改动 |

前端部分是交互与接口接入要求。当前仓库定位为后端，不在本期文档中假定已经存在某套前端目录或技术栈。

## 4. 接口与客户端影响

- 新增 7 个接口：管理员绑定 1 个、用户签到 1 个、医生查询 3 个、医生动作 2 个。
- 扩展已有我的预约列表和详情两个查询接口，路径不变；列表增加可选 order 参数。
- 保留统一 Result、数组列表、字符串 Long ID、日期时间格式。
- AppointmentResponse 新增 visit 与 sessionType，其余字段原含义保留；已有取消接口也返回该 DTO，因此带上新增字段。创建接口使用独立的 AppointmentCreationResult，响应结构保持原样。
- 默认排序由 ID 降序改为就诊时间降序，这是可见行为变化，客户端不能再假定列表顺序等于创建顺序。
- workflowStatus 继续表达原预约与支付流程；客户端展示新增就诊状态时读取 visit.status，不能等待原 status 变成 COMPLETED。
- 角色授予后需重新登录，沿用当前 JWT 行为，不新增动态角色同步机制。

## 5. 保留的业务行为

本节记录到院签到交付时保持的业务基线。随后三天预约改造已将下列历史 D+1～D+7 窗口替换为今天至后天，并新增场次下单截止，见 [ADR 0007](../../../../docs/adr/0007-three-day-booking-and-session-cutoffs.md)；原就诊状态与库存语义继续保持。

本期对签到和接诊增加独立记录；不改写以下业务事实与已有职责：

- 预约创建、15 分钟支付窗口、支付确认、取消、超时、退款及原恢复流程。
- 四固定场次、D+1 至 D+7 新预约窗口、按需生成、统一容量与费用快照。
- 预约确认时分配 visitNumber；每个场次独立递增，已分配号码不因签到或完成变化。
- 总量 = 可用 + 暂占 + 已确认的库存恒等式。
- 原有账号、就诊人、医生编号及预约资料快照。

完成接诊后，预约仍 CONFIRMED、资金仍 PAID、就诊为 COMPLETED。就诊消耗了既有名额，不增加可用名额，不删除防重复占用。

## 6. 数据迁移与历史记录

使用新增 Flyway 迁移扩展，不修改旧迁移，不删除数据库或数据卷。新账号关联允许为空，就诊表初始为空，不批量插入历史签到或完成记录。

其他预约状态的 visit 为 null。已确认但没有就诊记录时仅按“系统尚未记录签到”展示，不据此推断历史上实际是否看过病。功能启用前的诊疗事实不补造；端到端验收从新建预约开始。

原始日期、场次起止时间使用预约快照。sessionType 从关联场次读取，历史旧类型或空值继续兼容显示，不改变既有历史凭证。

## 7. 实施顺序与每步验证

| 顺序 | 工作内容 | 验证结果 |
| --- | --- | --- |
| 1 | 迁移、就诊状态、医生账号关联与角色 | 旧数据可读；账号绑定后重新登录可识别 DOCTOR |
| 2 | 用户签到、就诊记录、共享日期规则 | 受控 Clock 下就诊日前按钮禁用、当天启用；签到落库为 WAITING |
| 3 | 用户查询联查和响应、排序 | 全部预约仍返回；ASC/DESC 正确；快照不变，详情包含进度 |
| 4 | 医生四场概览、名单、详情 | 无预约仍有四场；人数和分类一致；按号码排列；四状态均能查看 |
| 5 | 医生开始和完成接诊 | WAITING → IN_PROGRESS → COMPLETED，按钮矩阵与时间对应 |
| 6 | API 示例、文档同步与完整回归 | 双方正常 HTTP 链路贯通；原预约与资金流程通过验证 |

## 8. 端到端正常验收场景

1. 注册普通账号与医生待关联账号，管理员将后者关联医生并授予角色，医生重新登录。
2. 普通用户选择自己的就诊人，预约次日场次并经 MockPay 完成预约确认，取得就诊序号。
3. 查询“我的预约”列表和详情，快照、号码正确，显示待签到，当前不能签到。
4. 使用受控业务日期进入就诊日，详情显示可签到；用户签到后记录为候诊中。
5. 医生场次概览统计正确，名单同一预约显示候诊中，查看始终可用，开始接诊可用。
6. 医生开始接诊，双方查询显示就诊中，开始按钮禁用、完成按钮启用。
7. 医生完成接诊，双方查询显示已就诊，两个动作按钮禁用，查看可用，三个时间齐全。
8. 核对原预约快照、号码、库存与支付结果在签到接诊期间保持正确。

自动化时间规则测试使用可替换 Clock，不修改宿主系统时间。Compose HTTP 验收通过测试用启动配置提供受控业务 Clock，默认启动仍使用真实时钟；不对外暴露修改日期的业务 API。脚本通过测试 classpath 的 VisitAcceptanceApplication 在重启时注入固定 Clock；不改宿主时间，生产入口不提供这一配置。

## 9. 到院功能交付时的验证结果（历史记录）

以下为三天预约改造前、到院功能交付时的 2026-11-11 实测：常规测试 241 项、MySQL 集成 36 项、消息可靠性 15 项，均无失败或跳过；扩展后的 Compose 隔离 HTTP 验收通过，另有本轮 92 次 curl 业务请求结果可供复核。两个集成 profile 分别运行时均会重复执行常规测试，不重复累计数量。

上述记录只对应到院功能当时的检查点。之后已完成三天窗口、消息设计和测试整理；2026-11-12最新交付为常规340、MySQL38、可靠性16及隔离curl92请求通过，详见[当前状态](../../../../docs/project-status.md)和[最终验收](../../delivery-quality/evidence/test-suite-refactor/final-acceptance.md)。不把旧的241/263项结果当作当前规模。

## 10. 实施后的逐文件核对清单

完整类名和包位置以设计第 6 节为准。下面记录容易遗漏的改造关联，对应本次已实现代码与检查项。

| 改动对象 | 必须一起处理的内容 | 用户可见结果 |
| --- | --- | --- |
| 医生账号表字段、Doctor Entity、DoctorMapper | 同步字段映射、首次绑定 SQL、按账号查医生 | 登录账号能对应到自己的看板 |
| DoctorAccountService、UserRoleService | 同库事务、保留原角色、绑定 DTO 和管理员权限 | 管理员一次操作完成医生身份准备 |
| AppointmentLifecycleCase、view()、构造该 record 的测试 | 增加 doctorId 参数，原字段与锁契约保留 | 医生动作能确认预约归属 |
| VisitRecord、VisitRecordMapper | 显式自增 ID、一对一关联、两个条件更新 | 同一预约保存连续的就诊进度 |
| VisitRules、DefaultVisitService | Clock 注入、三个动作、三个时间、共享按钮规则 | 签到和接诊按既定流程变化 |
| AppointmentResponse、Assembler 的全参构造调用 | 同步追加 visit、sessionType，并更新响应断言 | 原详情能展示场次标签和就诊进度 |
| BookingQueryMapper、BookingQueryRecord | 原有 JOIN 保留，新增 LEFT JOIN 与六个字段 | 没签到的人仍能查看预约，原状态不丢失 |
| AppointmentService、Controller、DefaultBookingQueryService | 旧方法保留、新排序参数、固定 SQL 分支 | 全部预约可按就诊时间排序 |
| DoctorVisitQueryMapper、查询 Service、Assembler、DTO | 空场补零、号码顺序、状态转换、证件脱敏 | 医生能查看各场完整名单和详情 |
| 两个就诊 Controller、管理员 Controller | 角色要求、身份提取、动作后查询返回 | 前端可以直接按 API 文档接入 |
| ModuleDependencyTest 与查询约束 | 区分 Controller 和写业务层，覆盖新 Mapper | 新业务继续符合既有模块协作方式 |

修改后主要增加“就诊进度数据及其操作”，已有预约与支付代码只提供事实或扩展查询；不会把支付确认、库存释放搬进就诊 Service。无需为用户、医生各建一份就诊记录，也无需按每个按钮建立独立业务模块。

## 11. 验收接线与运行状态

HTTP 脚本的第二阶段使用 `./mvnw -o spring-boot:test-run -Dspring-boot.run.main-class=com.qsx.appoint.support.VisitAcceptanceApplication`，测试源码中的 @Primary Clock 接收验收进程参数；普通启动没有这个 Bean，也不提供调时 API。第一阶段资金及消息流程完成后才重启，第二阶段关闭消息与资金扫描，仅验证同步就诊业务及查询，避免 MockPay 重启丢失内存事实。

本次成功验收的临时数据库、账号和 vhost 已清理；应用子进程已退出。Compose 基础依赖保留运行，未改动原有数据库和数据卷。第一次验收因启动目标未包含测试类失败，修正后完整重跑通过；第一次失败资源与日志按项目约定保留，标识详见验证报告。

## 12. 本轮文件清单

以下列出本次就诊功能交付的源码、测试、文档与脱敏验收记录，包括已整理的 GLOSSARY.md。scripts/__pycache__/ 为本地生成缓存，不纳入交付；Git 提交信息以仓库历史为准。


- [.scratch/visit-workflow/spec.md](../summary.md)
- [.scratch/visit-workflow/verification/curl-2026-11-11.json](visit-workflow/curl-2026-11-11.json)
- [GLOSSARY.md](../../../../GLOSSARY.md)
- [README.md](../../../../README.md)
- [docs/api.md](../../../../docs/api.md)
- [docs/business-flow.md](../../../../docs/business-flow.md)
- [docs/module-design.md](../../../../docs/module-design.md)
- [docs/runbooks/payment-module-cutover.md](../../../../docs/runbooks/payment-module-cutover.md)
- [docs/verification-report.md](../../../../docs/verification-report.md)
- [docs/visit/api.md](../../../../docs/visit/api.md)
- [docs/visit/change-scope.md](../../../../docs/visit/change-scope.md)
- [docs/visit/design.md](../../../../docs/visit/design.md)
- [http/auth.http](../../../../http/auth.http)
- [http/visit.http](../../../../http/visit.http)
- [scripts/compose_acceptance.py](../../../../scripts/compose_acceptance.py)
- [src/main/java/com/qsx/appoint/appointment/application/AppointmentLifecycleCase.java](../../../../src/main/java/com/qsx/appoint/appointment/application/AppointmentLifecycleCase.java)
- [src/main/java/com/qsx/appoint/appointment/application/DefaultAppointmentLifecycle.java](../../../../src/main/java/com/qsx/appoint/appointment/application/DefaultAppointmentLifecycle.java)
- [src/main/java/com/qsx/appoint/appointment/controller/AppointmentController.java](../../../../src/main/java/com/qsx/appoint/appointment/controller/AppointmentController.java)
- [src/main/java/com/qsx/appoint/appointment/dto/AppointmentResponse.java](../../../../src/main/java/com/qsx/appoint/appointment/dto/AppointmentResponse.java)
- [src/main/java/com/qsx/appoint/appointment/dto/AppointmentTimeOrder.java](../../../../src/main/java/com/qsx/appoint/appointment/dto/AppointmentTimeOrder.java)
- [src/main/java/com/qsx/appoint/appointment/dto/AppointmentVisitResponse.java](../../../../src/main/java/com/qsx/appoint/appointment/dto/AppointmentVisitResponse.java)
- [src/main/java/com/qsx/appoint/appointment/service/AppointmentService.java](../../../../src/main/java/com/qsx/appoint/appointment/service/AppointmentService.java)
- [src/main/java/com/qsx/appoint/bookingquery/application/AppointmentResponseAssembler.java](../../../../src/main/java/com/qsx/appoint/bookingquery/application/AppointmentResponseAssembler.java)
- [src/main/java/com/qsx/appoint/bookingquery/application/DefaultBookingQueryService.java](../../../../src/main/java/com/qsx/appoint/bookingquery/application/DefaultBookingQueryService.java)
- [src/main/java/com/qsx/appoint/bookingquery/application/DefaultDoctorVisitQueryService.java](../../../../src/main/java/com/qsx/appoint/bookingquery/application/DefaultDoctorVisitQueryService.java)
- [src/main/java/com/qsx/appoint/bookingquery/application/DoctorVisitQueryService.java](../../../../src/main/java/com/qsx/appoint/bookingquery/application/DoctorVisitQueryService.java)
- [src/main/java/com/qsx/appoint/bookingquery/application/DoctorVisitResponseAssembler.java](../../../../src/main/java/com/qsx/appoint/bookingquery/application/DoctorVisitResponseAssembler.java)
- [src/main/java/com/qsx/appoint/bookingquery/dto/DoctorAppointmentDetailResponse.java](../../../../src/main/java/com/qsx/appoint/bookingquery/dto/DoctorAppointmentDetailResponse.java)
- [src/main/java/com/qsx/appoint/bookingquery/dto/DoctorAppointmentRowResponse.java](../../../../src/main/java/com/qsx/appoint/bookingquery/dto/DoctorAppointmentRowResponse.java)
- [src/main/java/com/qsx/appoint/bookingquery/dto/DoctorPatientResponse.java](../../../../src/main/java/com/qsx/appoint/bookingquery/dto/DoctorPatientResponse.java)
- [src/main/java/com/qsx/appoint/bookingquery/dto/DoctorSessionResponse.java](../../../../src/main/java/com/qsx/appoint/bookingquery/dto/DoctorSessionResponse.java)
- [src/main/java/com/qsx/appoint/bookingquery/dto/DoctorVisitProgressResponse.java](../../../../src/main/java/com/qsx/appoint/bookingquery/dto/DoctorVisitProgressResponse.java)
- [src/main/java/com/qsx/appoint/bookingquery/persistence/BookingQueryMapper.java](../../../../src/main/java/com/qsx/appoint/bookingquery/persistence/BookingQueryMapper.java)
- [src/main/java/com/qsx/appoint/bookingquery/persistence/BookingQueryRecord.java](../../../../src/main/java/com/qsx/appoint/bookingquery/persistence/BookingQueryRecord.java)
- [src/main/java/com/qsx/appoint/bookingquery/persistence/DoctorSessionCountRecord.java](../../../../src/main/java/com/qsx/appoint/bookingquery/persistence/DoctorSessionCountRecord.java)
- [src/main/java/com/qsx/appoint/bookingquery/persistence/DoctorVisitQueryMapper.java](../../../../src/main/java/com/qsx/appoint/bookingquery/persistence/DoctorVisitQueryMapper.java)
- [src/main/java/com/qsx/appoint/bookingquery/persistence/DoctorVisitQueryRecord.java](../../../../src/main/java/com/qsx/appoint/bookingquery/persistence/DoctorVisitQueryRecord.java)
- [src/main/java/com/qsx/appoint/catalog/controller/DoctorAccountController.java](../../../../src/main/java/com/qsx/appoint/catalog/controller/DoctorAccountController.java)
- [src/main/java/com/qsx/appoint/catalog/dto/BindDoctorAccountRequest.java](../../../../src/main/java/com/qsx/appoint/catalog/dto/BindDoctorAccountRequest.java)
- [src/main/java/com/qsx/appoint/catalog/dto/DoctorAccountResponse.java](../../../../src/main/java/com/qsx/appoint/catalog/dto/DoctorAccountResponse.java)
- [src/main/java/com/qsx/appoint/catalog/entity/Doctor.java](../../../../src/main/java/com/qsx/appoint/catalog/entity/Doctor.java)
- [src/main/java/com/qsx/appoint/catalog/mapper/DoctorMapper.java](../../../../src/main/java/com/qsx/appoint/catalog/mapper/DoctorMapper.java)
- [src/main/java/com/qsx/appoint/catalog/service/DoctorAccountService.java](../../../../src/main/java/com/qsx/appoint/catalog/service/DoctorAccountService.java)
- [src/main/java/com/qsx/appoint/catalog/service/impl/DoctorAccountServiceImpl.java](../../../../src/main/java/com/qsx/appoint/catalog/service/impl/DoctorAccountServiceImpl.java)
- [src/main/java/com/qsx/appoint/common/ErrorCode.java](../../../../src/main/java/com/qsx/appoint/common/ErrorCode.java)
- [src/main/java/com/qsx/appoint/common/GlobalExceptionHandler.java](../../../../src/main/java/com/qsx/appoint/common/GlobalExceptionHandler.java)
- [src/main/java/com/qsx/appoint/user/entity/Role.java](../../../../src/main/java/com/qsx/appoint/user/entity/Role.java)
- [src/main/java/com/qsx/appoint/user/mapper/UserMapper.java](../../../../src/main/java/com/qsx/appoint/user/mapper/UserMapper.java)
- [src/main/java/com/qsx/appoint/user/service/UserRoleService.java](../../../../src/main/java/com/qsx/appoint/user/service/UserRoleService.java)
- [src/main/java/com/qsx/appoint/user/service/impl/UserRoleServiceImpl.java](../../../../src/main/java/com/qsx/appoint/user/service/impl/UserRoleServiceImpl.java)
- [src/main/java/com/qsx/appoint/visit/application/DefaultVisitService.java](../../../../src/main/java/com/qsx/appoint/visit/application/DefaultVisitService.java)
- [src/main/java/com/qsx/appoint/visit/application/VisitService.java](../../../../src/main/java/com/qsx/appoint/visit/application/VisitService.java)
- [src/main/java/com/qsx/appoint/visit/controller/DoctorVisitController.java](../../../../src/main/java/com/qsx/appoint/visit/controller/DoctorVisitController.java)
- [src/main/java/com/qsx/appoint/visit/controller/UserVisitController.java](../../../../src/main/java/com/qsx/appoint/visit/controller/UserVisitController.java)
- [src/main/java/com/qsx/appoint/visit/domain/VisitRules.java](../../../../src/main/java/com/qsx/appoint/visit/domain/VisitRules.java)
- [src/main/java/com/qsx/appoint/visit/domain/VisitStatus.java](../../../../src/main/java/com/qsx/appoint/visit/domain/VisitStatus.java)
- [src/main/java/com/qsx/appoint/visit/entity/VisitRecord.java](../../../../src/main/java/com/qsx/appoint/visit/entity/VisitRecord.java)
- [src/main/java/com/qsx/appoint/visit/mapper/VisitRecordMapper.java](../../../../src/main/java/com/qsx/appoint/visit/mapper/VisitRecordMapper.java)
- [src/main/resources/db/migration/V10__visit_workflow.sql](../../../../src/main/resources/db/migration/V10__visit_workflow.sql)
- [src/test/java/com/qsx/appoint/architecture/ModuleDependencyTest.java](../../../../src/test/java/com/qsx/appoint/architecture/ModuleDependencyTest.java)
- [src/test/java/com/qsx/appoint/catalog/CatalogManagementMySqlIT.java](../../../../src/test/java/com/qsx/appoint/catalog/CatalogManagementMySqlIT.java)
- [src/test/java/com/qsx/appoint/catalog/InitialSchemaMigrationTest.java](../../../../src/test/java/com/qsx/appoint/catalog/InitialSchemaMigrationTest.java)
- [src/test/java/com/qsx/appoint/catalog/VisitSchemaMigrationTest.java](../../../../src/test/java/com/qsx/appoint/catalog/VisitSchemaMigrationTest.java)
- [src/test/java/com/qsx/appoint/catalog/VisitSchemaMySqlIT.java](../../../../src/test/java/com/qsx/appoint/catalog/VisitSchemaMySqlIT.java)
- [src/test/java/com/qsx/appoint/coordination/application/BookingLifecyclePolicyTest.java](../../../../src/test/java/com/qsx/appoint/coordination/application/BookingLifecyclePolicyTest.java)
- [src/test/java/com/qsx/appoint/payment/PaymentPersistenceMySqlIT.java](../../../../src/test/java/com/qsx/appoint/payment/PaymentPersistenceMySqlIT.java)
- [src/test/java/com/qsx/appoint/support/VisitAcceptanceApplication.java](../../../../src/test/java/com/qsx/appoint/support/VisitAcceptanceApplication.java)
- [src/test/java/com/qsx/appoint/visit/VisitHttpTest.java](../../../../src/test/java/com/qsx/appoint/visit/VisitHttpTest.java)
- [src/test/java/com/qsx/appoint/visit/VisitWorkflowMySqlIT.java](../../../../src/test/java/com/qsx/appoint/visit/VisitWorkflowMySqlIT.java)
