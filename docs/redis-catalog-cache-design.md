# Redis 目录缓存实施方案

状态：设计已确认，代码与完整验收已交付。确认日期：2026-11-13（Asia/Shanghai）。源码核对点：`4a4cc81980cfeab107543123847d7eb62b645785`。

本方案是第一阶段行为和验收标准的唯一来源。[需求与决定记录](../.scratch/redis-catalog-cache/spec.md)保存用户确认，[任务地图](../.scratch/redis-catalog-cache/map.md)安排实施顺序。2026-11-14用户授权实施并提交；下文为实现契约，实际通过结果见[交付档案](../.scratch/archive/redis-catalog-cache/summary.md)，不能仅凭方案声称测试通过。

## 1. 目标与范围

让 Redis 承担医院、科室和医生目录的共享查询缓存，减少多个用户反复浏览相同目录时的 MySQL 查询。采用本地单应用落地、两个应用实例可共享缓存的设计，接受分钟级最终一致性，Redis 故障时优先保持业务可用。

第一阶段覆盖三个查询和对应目录增改删后的缓存失效。管理员操作结果、预约创建资料和费用快照继续以 MySQL 为准。HTTP 路径、认证要求、DTO 字段、错误语义保持现行契约。

后续方向为入口限流；场次余量缓存和 JWT 撤销暂缓。首期不增加 Redis Cluster、多级缓存、分布式库存、分布式业务锁、Redis 消息队列或缓存失效 Outbox。不新增数据库表或 Flyway 迁移。

## 2. 代码事实与设计依据

| 当前入口 | 已核对事实 | 设计影响 |
| --- | --- | --- |
| [CatalogServiceImpl](../src/main/java/com/qsx/appoint/catalog/service/impl/CatalogServiceImpl.java) 的三个目录方法 | 直接 Mapper 查询并映射 DTO；无显式排序、分页或事务注解 | 在这三个方法加入旁路缓存，保持查询形状，不顺手改排序或分页 |
| 同类的 listSchedules | 在只读事务中组合医生、场次、统一设置和当前上海业务时间 | 不缓存整个场次响应，保持三天入口、截止、余量及 serverTime 的原行为 |
| [CatalogManagementServiceImpl](../src/main/java/com/qsx/appoint/catalog/service/impl/CatalogManagementServiceImpl.java) | 增改删都有事务；科室不换医院、医生不换科室；删除受关联约束限制 | 可以按固定父 ID 定向删除列表缓存 |
| [DoctorAccountServiceImpl](../src/main/java/com/qsx/appoint/catalog/service/impl/DoctorAccountServiceImpl.java) | 绑定账号更新的字段不出现在用户医生列表中 | 医生绑定不用新增目录缓存失效 |
| [AppointmentHoldServiceImpl](../src/main/java/com/qsx/appoint/appointment/application/AppointmentHoldServiceImpl.java) | 直接读目录与就诊人，按统一设置→场次→就诊人加锁并保存快照 | 不经过目录展示缓存，缓存旧资料不成为预约凭证来源 |
| [ApiJsonConfig](../src/main/java/com/qsx/appoint/common/ApiJsonConfig.java) | HTTP Long ID 序列化为文本 | 缓存往返必须验证大 Long，不改变 HTTP 序列化配置 |
| [目录管理测试](../src/test/java/com/qsx/appoint/catalog/CatalogManagementIntegrationTest.java) | 类级测试事务内创建后立即查询，通常回滚 | 既要保留事务内读己之写，也要新增真实提交测试证明失效 |
| [Compose 验收](../scripts/compose_acceptance.py) | Redis 目前只 PING；隔离 MySQL/RabbitMQ；同一轮会重启应用到测试 Clock | 必须增加 Redis 命名空间隔离，两次进程沿用同一轮空间 |

目录 GET 当前需要登录；缓存命中仍经过原安全链。不存在医院/科室的子列表查询当前返回空列表，接入缓存后继续返回空列表，不改为 404。

## 3. 缓存契约

键前缀统一为 `appoint:<namespace>:catalog:v1:`。namespace 标识同一套数据库和部署环境，而非用户或应用实例；v1 标识缓存值结构版本，与数据库 V10 无关。

| 查询 | Key 后缀 | 缓存字段 | 非空 TTL |
| --- | --- | --- | --- |
| 医院列表 | `hospitals` | id、name、address | 5 分钟 |
| 指定医院科室列表 | `departments:<hospitalId>` | id、name | 5 分钟 |
| 指定科室医生列表 | `doctors:<departmentId>` | id、doctorNo、name、title、introduction | 2 分钟 |

三类空列表统一缓存 30 秒。读取不续期。缓存成功查询的列表数据，不缓存 Result 包装、认证身份、异常、null 查询失败结果、Entity 或就诊人资料。每次回填通过一条带 TTL 的 SET 完成，避免写出永久键。

namespace 必须非空且只允许字母、数字、短横线和下划线，避免清理匹配跨范围。相同数据库的两个应用实例使用相同 namespace；不同数据库、开发环境和每轮验收使用不同 namespace。不能仅以 Spring profile 或数据库名自动认定环境唯一。

缓存值用明确类型的 JSON 数组。在 catalog 内定义少量内部 record 作为编解码结构，显式转换现有响应 DTO，避免依赖 Lombok DTO 的隐式构造器反序列化。只解析预期类型，不启用任意类名多态反序列化。JSON字面量null、null数组元素、缺少ID/名称等必需字段均按损坏缓存回源；医生introduction允许null，不能误判。必须覆盖 Long 大整数、中文、null 简介与空数组往返；HTTP ID 仍输出文本。

## 4. 查询、事务与失效

### 4.1 查询步骤

1. 缓存开关关闭，或当前线程已在 Spring 实际事务内：直接执行原 Mapper 查询，既不读也不回填 Redis。
2. 无事务且开启缓存：GET 指定键；成功解码则返回对应 DTO 列表。
3. 未命中：执行原 MySQL 查询，按数据类型或空列表选择 TTL，再尝试写入缓存，返回查询结果。
4. GET 超时/连接失败：直接回源，本次请求不再尝试回填，减少故障等待。
5. 缓存内容无法按预期解码：记为损坏缓存并回源，尝试用正确查询结果覆盖，失败仍返回数据库结果。
6. SET 或缓存编码失败：记录缓存错误，返回已经取得的数据库结果。

数据库查询置于 Redis 异常捕获范围之外。数据库查询异常按原错误处理传播，不能被吞成空列表，也不能因捕获范围过大重复查询数据库。

对所有已存在事务绕过缓存，包括只读事务，优先保证事务视图与读己之写。此规则还能避免测试事务或未来内部调用把未提交数据写入共享 Redis。普通三个目录 HTTP 查询本身无事务，正常使用缓存。

### 4.2 提交后失效

| 成功管理动作 | 删除键 |
| --- | --- |
| 医院新增、修改、删除 | hospitals |
| 科室新增、修改、删除 | departments:所属医院ID |
| 医生新增、修改、删除 | doctors:所属科室ID |

在写方法成功完成数据库操作后登记事务同步回调，只有 afterCommit 执行定向 DEL；回滚不执行。以原锁定实体取得父 ID，尤其删除科室/医生时保留锁定查询返回对象，不能删除后再查父 ID。

一次普通写操作只失效一个已知列表键，不扫描所有目录缓存。现有父子删除限制和不可换归属规则使上述映射成立；将来增加移动科室或医生时，需同时失效新旧父列表。

DEL 异常由缓存边界处理并记录；数据库已提交的管理操作仍返回成功。禁止把 Redis 异常抛到 HTTP 形成“数据库实际成功、客户端却收到失败”的结果。写路径只删缓存，不用管理响应直接重建整份列表。

缓存失效针对开启缓存的部署。临时关闭缓存期间可以不触达 Redis；恢复开启时必须采用第 7 节的命名空间切换或清理步骤，不能重新使用可能陈旧的旧缓存。

### 4.3 一致性承诺和竞态

正常顺序执行的“管理提交→删除成功→新查询”会回源并看到新资料；并发及故障窗口允许旧数据短暂存在。例如：

```text
查询 A 未命中并读到旧资料
管理 B 提交新资料并删除缓存
查询 A 把旧资料回填到缓存
后续查询暂时读取旧资料，直到再次失效或 TTL 到期
```

这是已接受的最终一致性取舍。TTL 从缓存写入时开始计算，不能承诺“管理员提交后严格最多 5 分钟全部可见”，因为在途数据库读取和回填也有耗时。不会以读取续期或过期后继续服务旧值无限延长缓存寿命。

本阶段不增加双删延迟任务、代际版本键或分布式回填锁。并发冷启动允许有限的重复数据库查询；用测试测出同键并发回源情况，不能宣称已经解决缓存击穿。若后续负载证明需要请求合并，再单独设计。

删除医生后，旧目录中可能仍短暂展示该医生；后续场次查询、预约创建按现有数据库规则处理，不因缓存存在而放行。目录展示变化不改写已有预约快照。

## 5. Redis 故障与资源控制

| 场景 | 对外结果 | 内部处理 |
| --- | --- | --- |
| Redis 读失败 | 返回 MySQL 查询结果 | read_error，跳过本次回填 |
| 缓存损坏 | 返回 MySQL 查询结果 | decode_error，尝试覆盖 |
| Redis 写失败或拒绝写入 | 返回 MySQL 查询结果 | write_error |
| 数据库已提交、失效失败 | 管理操作成功 | evict_error，旧值由 TTL 淘汰 |
| MySQL 查询失败 | 原数据库错误响应 | 不缓存，不伪装成空列表 |
| 应用启动时 Redis 不可达 | 应用可启动，目录可回源 | 缓存不作为启动成功或业务正确性的强依赖 |

Redis 连接超时初值 500ms、命令超时 200ms，可配置；同一请求不自行循环重试。超时是客户端操作配置，不是整个 HTTP 请求的绝对 SLA。首次建立连接、线程调度和数据库回源仍计入总耗时。

首期使用短超时与回源，不另外引入熔断框架。持续故障会增加 MySQL 流量和接口耗时；可以关闭缓存开关减少额外等待，观测告警后再处理 Redis。Redis 恢复不意味着所有旧键已刷新，仍遵循 TTL 或定向清理。

当前项目 Redis 没有 maxmemory/淘汰策略。实施时为该项目的纯缓存用途设置可调整的内存上限，本地初值 256mb、allkeys-lru；AOF 和已有数据卷保留。所有目录键必须有 TTL；空列表短 TTL 减少不存在父 ID 的键长期占用。此限制不是防滥用限流，不能替代后续入口保护。

若未来该实例承载不可随意淘汰的 JWT 撤销、配额或其他状态，应重新评估隔离与淘汰策略，不直接沿用纯缓存配置。

## 6. 最小代码与配置方案

### 6.1 组件职责

采用 Spring Data Redis 的 StringRedisTemplate 和 catalog 内的小型专用缓存组件，显式处理 TTL、类型、事务绕过和错误降级。第一阶段不用给 listSchedules 或整个 CatalogService 添加统一缓存注解。

代码位置：

- `catalog/cache/CatalogDirectoryCache`：提供三种类型明确的读取及三种失效方法；内部接收数据库加载回调，封装键、JSON、TTL 和 Redis 错误处理。
- `catalog/cache/CatalogCacheProperties`：开关、namespace 和三类/空列表 TTL。
- `catalog/config/CatalogCacheConfig`：缓存专用编解码器、必要配置装配和指标注册。

不为每个 DTO 建一套缓存接口/实现，不引入通用 RedisUtil。CatalogServiceImpl 保留查询意图和原 Mapper 映射；管理 Service 保留事务和父 ID，委托缓存组件安排提交后失效。事务登记逻辑只保留一处。

### 6.2 配置

| 配置 | 初值或要求 |
| --- | --- |
| spring.data.redis.host / port | 本地 localhost / 6379 |
| spring.data.redis.connect-timeout | 500ms |
| spring.data.redis.timeout | 200ms |
| appoint.catalog-cache.enabled | 基础配置 false；dev/test/demo profile为true；常规测试 false；缓存测试显式 true |
| appoint.catalog-cache.namespace | 开启时必填；三环境分别为dev/test/demo；验收使用独立标识 |
| appoint.catalog-cache.hospital-ttl | 5m |
| appoint.catalog-cache.department-ttl | 5m |
| appoint.catalog-cache.doctor-ttl | 2m |
| appoint.catalog-cache.empty-ttl | 30s |

开启时校验 namespace 及 TTL 为正；关闭时不因缓存 namespace 缺失阻止应用启动。所有应用实例共享相同缓存结构版本及配置；滚动发布若改变值结构，提升键中的 v1 版本，旧键按 TTL 自行淘汰。

pom 新增 spring-boot-starter-data-redis，依赖版本由当前 Spring Boot 3.5.16 管理。指标使用 Micrometer core，依赖版本同样交给 Boot 管理；提供可注入、可测试的 MeterRegistry，不为本阶段暴露新的管理 HTTP 端点。将来已有 registry 时复用，避免重复注册。

Redis地址由公共application配置提供，三个profile分别指定缓存开关和namespace；compose.yaml配置内存限制与淘汰参数。保留现有 MySQL/RabbitMQ 配置和数据卷，不把实际凭据抄入方案。当前无 Redis 密码时不编造密码；未来启用认证再同步服务端与客户端配置。

### 6.3 可观测结果

记录 `catalog.cache.requests`，低基数标签限定为目录类型和 outcome；outcome 包含 hit、miss、read_error、decode_error、bypass_disabled、bypass_transaction。另以 `catalog.cache.operations` 记录 put/evict 的 success/error，记录数据库加载及 Redis 操作耗时。

指标不得以账号、医院/科室 ID、完整 Key 或测试 namespace 作标签，避免无限维度。错误日志仅记录动作、目录类型和异常类别，不输出列表正文、令牌或凭据。持续故障日志应节流。通过测试读取 registry 和验收采集结果证明命中与回源，不凭 Redis PING 声称缓存生效。

## 7. 环境隔离、启用与回退

1. 常规 H2 测试默认关闭缓存，继续无需开发 Redis。真实 Redis 测试用类独占 Testcontainers 容器及专用 namespace。
2. 增加 Maven `redis-it` profile，Failsafe 明确匹配 `*RedisIT.java`；不能只新增测试类却未纳入执行。该 profile 也会执行常规测试，统计不得重复累计。
3. Compose 验收在已有 MySQL 库和 RabbitMQ vhost 隔离之外，增加本轮 `accept-<runId>` namespace。验收显式启用缓存；两个先后启动的应用进程使用同一 namespace；应用重启到受控业务时间不改变 Redis TTL 的真实经过时间。
4. 成功后先停止验收进程，防止清理时再次回填，再以 SCAN MATCH 指定完整前缀分批删除 Redis 键；验证没有剩余本轮键。清理失败使验收失败并报告资源标识。失败保留 namespace 和必要证据供排查，沿用原数据库/vhost保留规则。
5. 开发 Redis 上不执行 FLUSHDB、FLUSHALL 或全库 KEYS。共享 Redis 的故障注入放在独占测试容器中，不为测试停止开发服务。
6. 初次上线使用空 namespace。紧急回退将所有应用实例的缓存开关关闭并按现有方式重启，读写立即恢复直接数据库路径；再次开启前使用新 namespace，或停用期间清理旧前缀后再启用。
7. 多实例配置开关、namespace 和结构版本需协调一致，避免某实例关闭失效而其他实例继续读旧缓存。变更期间的残留旧值仍遵循最终一致性，不能承诺无延迟。

两个实例共享缓存的验证必须让两个独立 Spring 上下文或进程连接同一数据库、同一 Redis、同一 namespace；不能以同一个 Service 调用两次代替。不同数据库即便业务 ID 相同，也应通过不同 namespace 完全隔离。

## 8. 验收矩阵

| 编号 | 场景 | 必须证明的结果 | 层次 |
| --- | --- | --- | --- |
| R01 | 三类目录首次/重复查询 | 首次回源，重复命中；精确观察对应 Mapper 查询次数减少，响应内容一致 | Spring + 真实 Redis |
| R02 | JSON 往返 | 大 Long ID、中文、null 简介、空列表及 HTTP 字段正确 | 编解码 + HTTP |
| R03 | TTL | 三类非空 TTL 与空列表 TTL 正确；读不续期，到期重新回源 | 真实 Redis，测试缩短 TTL |
| R04 | 九种目录增改删 | 真实提交后按父 ID 失效；无并发顺序场景中再次读取新值；无关列表仍命中 | MySQL + Redis |
| R05 | 回滚与事务内查询 | 回滚不失效；事务内绕过缓存并读己之写；未提交数据不出现在 Redis | Spring 事务 |
| R06 | 旧值并发回填 | 用屏障重现读旧值→提交删除→旧值回填；允许暂旧，到期回源得到新值 | 并发集成 |
| R07 | Redis 启动不可达/运行中断开 | 应用可启动或继续运行；目录回源成功，缓存错误计数变化 | 独占 Redis 故障测试 |
| R08 | SET/DEL 失败与损坏值 | 查询结果保留；提交后失效失败不使管理员误收失败；语法错误、JSON null、null元素及缺必需字段均回源恢复 | 故障注入 + 真实 Redis |
| R09 | 数据库失败 | 不被转成空列表、不写缓存，不在同请求重复执行加载 | Spring 集成 |
| R10 | 双实例共享 | A 回填后 B 命中；A 管理提交失效后 B 重新读取；接受 R06 竞态边界 | 两上下文/进程 + 共享 MySQL/Redis |
| R11 | 命名空间与清理 | 两环境相同 ID 不串数据；清理验收前缀不影响开发键；两次验收应用复用同一前缀 | Redis + Compose HTTP |
| R12 | 认证和空目录 | 未登录仍 401，非管理员写入仍被拒绝；不存在父资源仍返回原空列表 | MockMvc / HTTP |
| R13 | 场次及预约主线 | 时间窗口、设置更新、快照、库存、幂等、支付确认、取消退款、签到接诊继续通过 | 原分层回归 + Compose HTTP |
| R14 | 开关和配置 | 关闭时无 Redis 业务操作；开启时无效 namespace/TTL 被拒绝；新 namespace 重启可恢复缓存 | 配置 + 集成 |
| R15 | 指标与收益证据 | 命中/未命中/错误/绕过及耗时可核验；重复请求 SQL 数减少；记录结果，不虚构吞吐提升 | 指标断言 + 验收记录 |

TTL 测试采用独立短 TTL 配置和有界条件等待；不让常规测试等待 5 分钟。并发、提交和故障测试使用真实边界，不能套测试外层回滚事务掩盖 afterCommit。数据库竞争使用 MySQL；H2 只承担其适合的事务与装配验证。

完整验证顺序（已纳入执行入口，实际通过结果另存任务证据）：

```bash
./mvnw clean test
./mvnw -Predis-it verify
./mvnw -Pmysql-it verify
./mvnw -Preliability-it verify
python3 scripts/compose_acceptance.py --http-client curl --http-report /tmp/appoint-redis-http-report.json
python3 scripts/check_documentation.py
```

Maven 串行执行。依赖首次增加时先完成下载，不假定当前离线缓存已有 Redis 客户端和指标依赖。记录源码检查点、命令退出码、用例层次、HTTP 记录和清理结果；不将各 profile 重复运行的常规测试累加，也不强行维持原 92 次 HTTP 数量。

## 9. 分期与完成定义

2026-11-13交付方案与任务；2026-11-14获授权实施。实现阶段按[任务地图](../.scratch/redis-catalog-cache/map.md)推进，只有 R01～R15 对应证据齐备且原业务回归通过，才可把项目状态改成“Redis 已参与业务”。

入口限流下一阶段另行确定账号/IP维度、请求配额、幂等重放计数、HTTP 429、故障策略和代理 IP 信任边界；本方案不预先承诺其阈值。场次缓存应以查询负载和锁等待测量为依据；JWT 撤销应以退出登录或强制下线需求为依据。

## 10. 官方参考

- [Redis Cache-Aside](https://redis.io/docs/latest/develop/use-cases/cache-aside/)：数据库回源、TTL 与写后失效模式；本方案明确保留最终一致性限制。
- [Spring Boot 3.5 Redis 支持](https://docs.spring.io/spring-boot/3.5/how-to/nosql.html)：使用 Boot 管理的 Redis starter；实施以项目实际解析的依赖版本验证。
- [Spring Data Redis 缓存](https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html)：若后续改用 Spring Cache，需显式配置 TTL、序列化和事务语义；本阶段选择专用组件以清楚表达事务绕过与失败回源。
- [Redis 内存淘汰](https://redis.io/docs/latest/reference/eviction/)：maxmemory 和缓存淘汰配置依据。
