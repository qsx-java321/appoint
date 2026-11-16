# 测试与验证

自动化测试包含单元/Mock、独立H2的MockMvc，以及按需运行的Testcontainers MySQL集成测试，覆盖业务规则、接口流程和真实数据库事务。

## 测试范围

| 类型 | 验证重点 | 位置 |
| --- | --- | --- |
| 后端单元/Mock | 时间和状态规则、查询组装、支付渠道与消息处理分支 | src/test/java中的普通JUnit与Mockito用例 |
| MockMvc | 注册登录、就诊人、目录管理、预约支付、取消和就诊流程 | 标注`@RegressionTest`及`@AutoConfigureMockMvc`的用例 |
| MySQL集成 | Flyway迁移、并发预约、真实提交与回滚、支付事件一致性 | src/test/java/com/qsx/appoint/mysql中的*MySqlIT |
| 前端单元/Mock | 会话、请求、预约状态与展示规则 | frontend/tests/unit |
| 环境脚本单元/Mock | 模板恢复、凭据保留、空库预检和异常拦截 | scripts/test_local_env.py |

MockMvc通过Spring和H2内存库验证接口协作，不启动真实HTTP监听端口。支付夹具直接驱动业务消费者，消息处理的单元测试使用Mock。

## 运行

后端在项目根目录执行：

```bash
./mvnw clean test
```

前端在frontend目录执行：

```bash
npm test
npm run typecheck
npm run build
```

默认测试无需Docker。MySQL集成测试需要可访问Docker，无需启动项目Compose。Maven依赖已缓存时可加`-o`，MySQL镜像需另行缓存。

环境脚本单元/Mock在项目根目录执行，仅使用Python标准库、临时目录和Mock：

```bash
python3 -m unittest discover -s scripts -p 'test_*.py'
```

## 隔离与边界

- MockMvc显式加载src/test/resources/regression/application.yml，使用H2内存库，屏蔽宿主连接覆盖，不读取三环境私密配置。
- Redis缓存、RabbitMQ连接和后台任务默认关闭；前端用例以Mock替代API请求。
- 真实提交的H2夹具按新增ID清理，事务用例回滚；业务Clock按需固定或在每例重置。
- MySQL集成测试覆盖选定业务场景的真实锁竞争与事务，不代表压力测试；Redis/RabbitMQ运行故障和浏览器端到端流程仍未覆盖。

## MySQL集成测试

### 运行条件与命令

使用项目已有的Java 21、Maven Wrapper，以及当前终端可访问的Docker。Windows/WSL2使用Docker Desktop时，需启用当前发行版集成；先用`docker info`确认连通性。首次运行需要获取Maven依赖、MySQL及Testcontainers辅助镜像。Maven的`-o`只控制Maven依赖下载，不控制Docker镜像拉取。

所有命令在项目根目录执行。测试自行创建数据库，无需执行`local_env.py init`、启动Compose或重置常驻test环境。

| 命令 | 执行范围 | Docker |
| --- | --- | --- |
| `./mvnw clean test` | 单元/Mock与H2/MockMvc | 不需要 |
| `./mvnw -Pmysql-it clean verify` | 默认测试加全部MySQL测试 | 需要 |
| `./mvnw -Pmysql-it -Dit.test=BookingConcurrencyMySqlIT verify` | 默认测试加指定MySQL测试类 | 需要 |
| `./mvnw -Pmysql-it test-compile failsafe:integration-test failsafe:verify` | 编译后仅执行MySQL组，适合定位失败 | 需要 |

`-Dit.test`只筛选Failsafe，不跳过Surefire。完整验收优先使用`clean verify`；排查时的仅MySQL命令不代表默认测试也已执行。不要只运行`integration-test`就认定成功，Failsafe的`verify`负责汇总并使失败影响构建结果。`-DskipTests`也会跳过集成测试，不用它实现“仅MySQL”。

`mysql-it`是Maven profile，激活Failsafe并只匹配`*MySqlIT`；既有`*IntegrationTest`继续由Surefire执行H2回归。普通`test`会编译所有测试源码并可能下载新增测试依赖，但不会启动容器。Failsafe配置`failIfNoTests=true`，显式运行MySQL组却未发现用例时构建失败。

### 配置与资源生命周期

| 配置位置 | 职责 |
| --- | --- |
| [pom.xml](../pom.xml) | 测试依赖、mysql-it分组、镜像属性和Failsafe匹配规则；版本沿用Boot父POM管理 |
| [MySqlIntegrationTest](../src/test/java/com/qsx/appoint/support/MySqlIntegrationTest.java) | 固定独立配置根、regression profile、MOCK Web环境与上下文关闭策略 |
| [MySqlContainerConfiguration](../src/test/java/com/qsx/appoint/support/MySqlContainerConfiguration.java) | Spring管理MySQLContainer，ServiceConnection提供数据源及Flyway连接 |
| [MySQL配置](../src/test/resources/mysql-it/application.yml) | Flyway、连接池、业务超时，以及关闭缓存、消息和后台任务 |
| [RegressionEnvironment](../src/test/java/com/qsx/appoint/support/RegressionEnvironment.java) | 屏蔽宿主Spring及业务连接属性覆盖，避免误接常驻资源 |

Spring使用`regression` profile启用现有MockPay，但配置根是`classpath:/mysql-it/`，不读取H2根配置、`.env.*`或三环境连接。这个Spring profile和Maven的`mysql-it`分组用途不同；不要将其替换为常驻环境的`test`。

MySQL使用动态宿主端口、随机密码和`appoint_it`库，不挂载项目持久卷。业务基类核对实际数据库产品、URL和库名，阻止H2误接。容器、JDBC和业务Clock使用上海时区；测试连接池上限10，连接获取超时10秒，JDBC socket超时20秒，容器启动超时2分钟。

当前使用MySQL 8.4的固定镜像摘要，以POM和容器配置默认值为准。Testcontainers 1.x使用`mysql@sha256:…`形式。需要试验其他镜像时可通过`-Dit.mysql.image=<镜像引用>`传入；正式升级须同步两处默认值、完成默认及MySQL回归并记录实际镜像版本。这个参数只影响临时测试容器，不修改Compose服务。

业务类通过`@DirtiesContext(AFTER_CLASS)`关闭上下文，同一上下文中的方法顺序执行；Spring管理容器启动和停止，避免数据源还在使用时提前关库。Spy等上下文定制也可能触发上下文重新创建，不依赖固定容器总数。迁移类使用`AFTER_EACH_TEST_METHOD`，每例获得空库，并关闭自动迁移、显式调用正式Flyway迁移链。

业务测试加载MOCK Web环境以满足现有安全Bean配置，主要通过注入的业务接口调用，不启动HTTP监听端口。Redis目录缓存、RabbitMQ业务消息、过期扫描、对账、Outbox后台发布和本地环境初始化均关闭。

### 事务、夹具与并发规则

[MySqlBusinessTestSupport](../src/test/java/com/qsx/appoint/support/MySqlBusinessTestSupport.java)在每例固定业务Clock、创建独立医生和账号，并使用[CommittedFixtureScope](../src/test/java/com/qsx/appoint/support/CommittedFixtureScope.java)恢复本例提交的数据与目录快照。夹具中的邮箱、证件、联系方式等必须符合真实表约束；自增ID可以跳号，不依赖固定新ID或顺序执行结果。

测试类和方法不加外层测试事务，直接调用Spring业务代理。特别是`BookingCoordinator.create()`依赖创建事务回滚后再读取幂等回执，用测试事务包住它会改变待验证行为。

并发用例用2～4个工作线程、ready/start屏障统一启动，各线程运行独立业务事务。屏障等待10秒、Future取结果30秒、线程池关闭最多等待15秒；只将场景预期的业务异常计为竞争失败，SQL异常、死锁、超时及未知异常均使测试失败。工作线程完成后再查询已提交结果、清理夹具；异常退出仍有存活线程时跳过逐行清理，交给独立上下文最终关闭容器。不要通过自动重跑或改变隔离级别掩盖失败。

统一启动增加事务重叠机会，但不穷举所有执行顺序。因此确认与取消另外验证两种串行顺序，再验证并发结果。库存断言检查`available + held + confirmed = total`及非负性，同时核对预约、支付、有效占用和幂等回执的数量、归属和费用关联。

[FormalPaymentTestSupport](../src/test/java/com/qsx/appoint/support/FormalPaymentTestSupport.java)的`recordPaymentSuccess()`真实提交支付尝试、验签通知和收款Outbox，再返回成功事件；调用者控制消费者执行时机。`pay()`继续组合收款与确认，供既有H2测试使用。H2的JSON差异适配仅在数据库产品为H2时生效。

回滚用例通过`@MockitoSpyBean`和`AopTestUtils.getUltimateTargetObject()`在事务代理内部的Spy上设桩，先执行真实写入再抛错；实际被测调用仍经过Spring事务代理。重试前恢复Spy，验证同一键或事件能重新成功，数据库及Mapper均为真实实现。

### 用例与断言

| 测试类 | 项数 | 核心场景和预期 |
| --- | --- | --- |
| [MigrationMySqlIT](../src/test/java/com/qsx/appoint/mysql/MigrationMySqlIT.java) | 2 | 空库完成V1/V2、22张应用表及预置目录，重复迁移零变更；V1升级保留人工医生、姓名和账号绑定，并避让已有编号 |
| [BookingConcurrencyMySqlIT](../src/test/java/com/qsx/appoint/mysql/BookingConcurrencyMySqlIT.java) | 4 | 并发首次预约只生成一个场次；容量1时只成功一单；同键并发重放同一预约；跨账号同证件只保留一份有效占用；库存和关联数据一致 |
| [BookingRollbackMySqlIT](../src/test/java/com/qsx/appoint/mysql/BookingRollbackMySqlIT.java) | 1 | 支付INSERT成功后抛错，新场次、暂占、预约、支付、有效占用和幂等登记均回滚，同键重试可成功 |
| [PaymentConsistencyMySqlIT](../src/test/java/com/qsx/appoint/mysql/PaymentConsistencyMySqlIT.java) | 5 | 同事件重复处理只确认一次；取消优先、确认优先及并发均收敛到取消与单笔退款申请；确认失败时Inbox与库存一起回滚，已提交PAID/Outbox保留，重试成功 |

这12项检查MySQL持久化及选定并发场景。退款终态断言为本地`REFUND_PENDING`，未驱动真实RabbitMQ或退款渠道完成；支付仍使用内存MockPay。该组也不代表吞吐压测、网络故障恢复、浏览器端到端或真实资金验收。

### IDE、报告与故障排查

IDE导入Maven项目后可以直接运行`*MySqlIT`，组合注解和容器配置负责启动环境，无需激活Maven profile或提供常驻数据库连接。IDE使用Java 21，并保证其进程可访问Docker；镜像覆盖使用VM选项`-Dit.mysql.image=…`。

| 现象 | 核对方式 |
| --- | --- |
| Docker连接失败或permission denied | 在运行Maven/IDE的同一环境执行`docker info`，核对Docker服务、socket权限及WSL集成 |
| 镜像下载失败或启动超时 | 核对网络、镜像摘要、磁盘和Docker资源；Maven离线模式不会预先缓存镜像 |
| `verify`未执行MySQL用例 | 确认带`-Pmysql-it`，类名以`MySqlIT`结尾，未传`skipTests`；检查Failsafe报告 |
| Mockito初始化提示无法自附加 | 按下面方式显式加载当前项目依赖版本的Mockito agent |
| MySQL约束错误、事务或库存断言失败 | 查看具体用例异常和SQL，核对夹具必填字段、事务代理和业务状态；不退回H2或放宽约束 |

Surefire报告在`target/surefire-reports`，Failsafe报告在`target/failsafe-reports`。以测试数量、失败、错误、跳过及最终构建退出码判断结果，不仅检查日志是否出现ERROR（部分错误分支测试会主动记录ERROR）。

仅在JVM限制动态附加时使用显式agent，替换为当前Mockito依赖JAR的本机绝对路径：

```bash
./mvnw -Pmysql-it '-DargLine=-javaagent:/absolute/path/to/mockito-core-VERSION.jar' clean verify
```

该参数会传给Surefire和Failsafe；IDE在VM选项中配置同样的`-javaagent:…`。路径包含空格时还需要在argLine内部引用路径。不要把开发者个人缓存路径写入POM。失败时保留测试报告及必要的容器错误日志，避免记录密码、签名密钥或Token；测试容器按生命周期清理。

## MySQL集成接入验收

2026-11-16完成方案复核与接入。现有单元/Mock与H2/MockMvc 324项通过；新增MySQL测试4个类、12项通过，失败、错误和跳过均为0。确认/取消场景分别执行取消优先、确认优先和并发三种情况。

验证使用固定摘要的MySQL 8.4临时容器和正式V1/V2迁移；故障注入作用于事务代理内部的Spy，被测调用仍通过真实Spring事务代理。先执行完整`clean verify`，修正测试夹具与Spy设桩后通过`test-compile failsafe:integration-test failsafe:verify`复验MySQL组。此环境通过`argLine`显式加载本地Mockito agent。另在无Docker访问权限的沙箱中执行默认`./mvnw -o test`（同样加载Mockito agent），324项通过且没有启动容器。验收后Testcontainers容器清单为空，未连接三环境常驻资源。文档与差异格式检查通过。

## 预置目录与账号验证

2026-11-15扩充样例目录并统一三环境账号预置后，按test、dev、demo的顺序逐套启动，使用真实MySQL、Redis和RabbitMQ完成以下核验。dev/test保留原数据并追加V2，demo按启动生命周期重建自身数据；未执行test显式重置。

| 核对项 | dev | test | demo |
| --- | --- | --- | --- |
| Flyway成功版本 | V1、V2 | V1、V2 | V1、V2 |
| 医院／科室／医生档案 | 2／6／18 | 2／6／18 | 2／6／18 |
| 预置账号数 | 19 | 19 | 20 |
| 管理员登录、角色、当前用户及管理接口 | 通过 | 通过 | 通过 |
| 18个医生登录、角色、当前用户及四场概览 | 全部通过 | 全部通过 | 全部通过 |
| 普通演示用户登录、角色及目录查询 | 不预置 | 不预置 | 通过 |
| 医生档案必填资料及账号绑定 | 完整，一一绑定 | 完整，一一绑定 | 完整，一一绑定 |
| 医生访问管理接口 | 403 | 403 | 403 |
| 普通演示用户访问管理／医生接口 | 不适用 | 不适用 | 均为403 |
| 前端HTTP | 200 | 200 | 200 |

目录API确认每院3个科室、每科室3名医生，医生编号、姓名、职称和介绍完整；数据库确认无空医生绑定、重复绑定或必填资料缺失。账号命名、医生对应关系及密码配置统一见[账号及重置](environments.md#账号及重置)，本次未记录密码、密码哈希或Token。

提交前另行完成独立自动化检查，未连接三环境常驻资源：

- 后端全量单元/Mock与MockMvc共324项通过，失败、错误及跳过均为0。新增用例覆盖三环境预置目录与账号、角色及绑定、重复初始化保留密码和人工资料、账号或绑定冲突回滚，以及V1升级V2保留原档案并避让人工医生编号。
- 环境脚本单元/Mock共13项通过；文档链接、JSON及REST/DTO契约检查通过。
- 后端使用`./mvnw -o clean test`，通过JVM参数显式加载本地Mockito agent；MockMvc使用独立H2，Redis、RabbitMQ及后台任务关闭。本次未修改前端源码，未重跑前端单元、类型检查或生产构建。

本轮覆盖真实环境启动、目录查询及预置账号登录权限，未重新执行完整预约、支付、签到和接诊流程，也未进行浏览器端到端或中间件故障测试。核验结束后已停止三环境前后端及共享Compose容器，保留配置和数据卷。临时核验脚本与原始输出不作为长期自动化测试提交。

## 测试精简历史验证

2026-11-15测试精简后完成以下验证，未连接dev/test/demo常驻数据库：

| 检查 | 结果 |
| --- | --- |
| 后端单元/Mock与MockMvc | 315项通过，失败、错误和跳过均为0 |
| 前端单元/Mock | 5个文件、22项通过 |
| 前端类型检查与生产构建 | 通过 |
| 文档链接、JSON及REST/DTO契约检查 | 通过 |

后端本次在Codex沙箱中通过JVM启动参数显式加载Mockito agent，绕过动态自附加限制；上述普通开发环境命令保持不变。结果适用于本次测试精简后的代码，后续改动按影响范围重新验证。

## Flyway首版合并验证

2026-11-15将原V1至V11的SQL/Java迁移合并为单一V1后，完成独立H2空库校验与后端全量测试：

- 原迁移链和新V1分别初始化空库，22张业务表的373项结构及基础数据快照一致，覆盖列类型、默认值、可空性、外键、索引和检查约束；数据比较排除自动生成的时间戳。
- 新V1只执行一次迁移，再次调用Flyway不执行任何迁移；清理后重新编译的迁移目录仅包含V1 SQL，没有旧Java迁移残留。
- 后端单元/Mock与MockMvc共315项通过，失败、错误和跳过均为0；文档链接与契约检查通过。

本轮不连接dev/test/demo资源，未验证真实MySQL初始化或旧库升级，未重跑前端测试。当前V1仅用于空库，使用限制见[初始化基线](environments.md#flyway初始化基线)。

## 首次初始化脚本验证

2026-11-15简化dev启动预检后，环境脚本单元/Mock共13项通过，覆盖缺失配置从模板恢复、600权限、重复初始化保留配置、凭据验证失败不改密码，以及空库和必要表齐全的非空库通过预检、端口占用/缺表/数据库查询失败时阻断。预检用例确认只查询表清单，不查询业务状态或访问RabbitMQ。Maven启动和重置命令允许联网下载依赖。测试使用临时配置并Mock所有Docker、数据库和消息调用，不读取本机凭据，不连接常驻资源。

此结果不代表已执行真实Docker空机部署，也未验证首次联网下载耗时。恢复步骤见[首次准备](environments.md#首次准备)。

## GitHub从零重建验收

2026-11-15以源码提交`53bb0e8`完成私有GitHub仓库的真实重建验收，按用户要求不保留旧库业务记录：

- 仅含Git文件的临时检出中，后端315项、前端22项和环境脚本15项测试通过，前端类型检查及生产构建通过。
- 推送后从GitHub独立克隆，核对提交及四份真实配置模板一致；随后停止项目进程，删除原项目目录、三个Compose容器、三个数据卷和项目网络。
- 从GitHub重新克隆回项目目录，确认没有旧配置、运行记录、node_modules或target。按README执行init、npm ci、dev预检和逐环境启动；四份配置从模板恢复为600权限，新数据卷创建时间均晚于旧卷。

| 核对项 | dev | test | demo |
| --- | --- | --- | --- |
| 应用表＋Flyway历史表 | 22＋1 | 22＋1 | 22＋1 |
| Flyway版本及成功记录 | V1，1条成功记录 | V1，1条成功记录 | V1，1条成功记录 |
| 初始账号数 | 0 | 1 | 3 |
| 初始账号登录及目录查询 | 无初始账号 | 管理员通过 | 普通用户、医生、管理员均通过 |
| 前端HTTP | 200 | 200 | 200 |
| 未认证API权限拦截 | 401 | 401 | 401 |

三环境逐套运行，核对22张应用表的初始记录数、默认20人/10元/CNY设置及demo医生账号绑定；预约、支付、退款和就诊记录均为空。最终恢复dev运行，test/demo停止。宿主JDK、Node、Docker及通用依赖缓存作为运行工具保留，未复用旧项目运行文件或旧数据卷。

本轮覆盖真实MySQL初始化、配置恢复、三环境启动及初始账号登录，没有重新执行完整预约支付签到接诊流程或中间件故障测试。临时验证脚本不作为长期自动化测试维护。

## 三环境历史验收

2026-11-15已完成test注册至接诊完成及异常分支、dev/demo注册至付款预约成功的真实环境验收。该结果适用于当时的代码版本，使用MockPay，与本轮自动化测试结果分别记录。

## 文档检查

```bash
python3 scripts/check_documentation.py
```

只读检查本地链接、代码围栏、JSON示例及REST/DTO文档契约，不连接数据库或启动服务。
