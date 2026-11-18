# dev、test、demo三环境使用说明

三套环境共用Docker Compose中的真实MySQL、Redis、RabbitMQ及Mailpit，前后端在宿主机启动。dev用于日常业务体验，test用于独立操作业务，demo用于首次体验和演示。三环境的业务代码和规则一致，通过独立配置、身份和数据生命周期区分用途。

## 环境对应关系

| 项目 | dev | test | demo |
| --- | --- | --- | --- |
| Spring profile | dev | test | demo（默认） |
| 前端→后端 | 5173→8080 | 5173→8080 | 5173→8080 |
| MySQL数据库 | appoint（原库） | appoint_test | appoint_demo |
| Redis namespace | dev | test | demo |
| RabbitMQ vhost | /appoint-dev | /appoint-test | /appoint-demo |
| 注册邮件 | 共用Mailpit，标题[dev] | 共用Mailpit，标题[test] | 共用Mailpit，标题[demo] |
| 私密配置 | .env.dev | .env.test | .env.demo |
| 数据生命周期 | 一直保留 | 默认保留，显式重置 | 每次后端启动恢复初始状态 |
| 业务时间 | 上海真实时间 | 默认实时，可运行中调时 | 上海真实时间 |

三个数据库使用同一套Flyway迁移与基础目录，不复制dev业务数据。数据库账号、RabbitMQ权限和JWT密钥独立，Redis按前缀隔离。启动时校验最终连接目标；项目级MySQL运行锁和共用端口检查先于重置，脚本、Maven、IDE使用同一生命周期。

前后端一次只能运行一套。切换前先停止当前前后端。脚本在启动前检查三环境的托管进程和两个共用端口，后端在初始化前取得项目级运行锁。

## Flyway初始化基线

当前以[src/main/resources/db/migration/V1__init_schema.sql](../src/main/resources/db/migration/V1__init_schema.sql)初始化完整结构。它合并原V1至V11的SQL和Java迁移结果，一次创建22张业务表、索引、约束和原有基础数据；V1保持不变。随后由[V2迁移](../src/main/java/db/migration/V2__expand_sample_catalog.java)追加医疗目录，使全新环境具有2家医院、6个科室和18名医生。环境账号由应用启动生命周期创建。历史示例场次保留，当前四场预约继续按首次下单生成。

这是面向重新部署的首版整理，不提供旧库原地升级。已经执行旧迁移链的数据库，其Flyway历史和V1校验和与当前代码不兼容；重复运行init或复制配置均不会转换这些历史。需要保留旧数据时应继续使用整理前的代码，并另行制定数据迁移方案；确认放弃旧数据后，使用空库重新初始化，不通过repair、删除历史记录或baseline跳过校验。

应用启动时由Flyway执行迁移，不需要手工导入SQL。空库依次执行V1和V2，现行V1数据库只追加V2，成功后版本为2；普通重启跳过已成功执行的迁移。V2保留原医院、科室、两名医生的ID、资料、绑定和预约引用；新增医生编号沿用分配表，避免与人工新增医生冲突。已有人工目录数据不会清理，升级后的总数可以超过样例规模；迁移后管理员增删改目录的结果也不会在普通启动时被还原。后续变更从V3继续追加。历史迁移可从Git提交`eb84890`追溯。

## 首次准备

以下脚本使用Linux进程信息与信号，支持Linux及WSL2。准备Git、JDK 21、Python 3、Docker和Compose v2；需要前端时准备Node 24（至少24.12）及npm。WSL中使用同一发行版内的Java、Python和Node，确保当前终端能执行`docker compose version`并访问Docker服务。

首次安装需要联网下载Docker镜像、Maven及应用依赖，无需预先安装Maven。以下从GitHub克隆开始，进入项目根目录后执行初始化：

```bash
git clone "<仓库地址>"
cd "<项目目录>"
python3 scripts/local_env.py init
```

init先从`compose.env.example`、`.env.dev.example`、`.env.test.example`和`.env.demo.example`复制缺失的实际配置，文件权限为600，已有文件保持原样。模板按本项目约定保存现有本地配置，初始化沿用模板中的账号、密码和密钥，不再随机生成新值；模板缺失且实际文件也不存在时，在启动共享服务前报错。

随后启动共享Compose并创建缺少的数据库、账号和vhost。重复执行保留已有密码和业务数据；卷内凭据或权限不匹配时停止，不擅自改密码或删除数据卷。init不创建业务表，业务结构在后端启动时由Flyway初始化。

完成初始化并安装前端依赖后，即可直接启动任一环境。首次使用 dev 执行 `python3 scripts/local_env.py start dev`，空库由 Flyway 在后端启动时初始化。

Compose操作始终使用`docker compose --env-file compose.env ...`。停止共享依赖用`docker compose --env-file compose.env stop`，保留卷。

## 启动和切换

脚本托管的完整流程见[README](../README.md#首次克隆并启动)。`start` 默认选择 demo，`start dev`、`start test` 和 `start demo` 显式选择环境。两个进程均通过身份和就绪检查后，脚本才报告启动成功。切换环境时，先停止当前前后端，再启动目标环境。脚本的 `stop <环境>` 和 `status <环境>` 都需要环境参数，作用于脚本启动的进程。通过 IDE、JAR 或 npm 手动启动的进程从原入口停止。由旧版端口布局升级时，先停止旧版前后端，启动新版后刷新所有项目标签页。

### 单独启动后端或前端

在项目根目录启动后端。直接运行 IDE 中的 `AppointApplication.main`、`./mvnw spring-boot:run` 或打包后的 JAR 时，默认加载 demo 和 `.env.demo`。需要切换时在 IDE、`SPRING_PROFILES_ACTIVE` 环境变量或命令行显式设置 profile。

可以用 Maven 启动 test。

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=test
```

也可以先打包，再直接启动默认的 demo。改用 test 时，在 JAR 启动命令后加 `--spring.profiles.active=test`。

```bash
./mvnw -DskipTests package
java -jar target/appoint-0.0.1-SNAPSHOT.jar
```

后端监听 `127.0.0.1:8080`。在对应后端就绪后，从 `frontend` 目录分别使用 `npm run dev`、`npm run dev:test` 或 `npm run dev:demo` 启动前端。前端验证后端的实际 profile，并从 `127.0.0.1:5173` 代理请求到 `8080`。切换后刷新旧标签页，浏览器会按环境读取登录和预约草稿。

## 账号及重置

### 预置目录和账号

三套环境使用相同的样例目录：示例市第一人民医院、示例市第二人民医院，每院设内科、骨科、皮肤科，每科室3名医生，共18名。它是首次初始化的数据规模，管理员仍可增删改目录，不是业务数量限制。

每套环境有1个管理员、18个独立医生账号，医生档案与账号一一绑定；demo另有1个普通演示用户。新建环境的初始用户总数分别为19、19、20。

| 环境 | 管理员邮箱 | 医生邮箱范围 | 普通演示用户 |
| --- | --- | --- | --- |
| dev | dev-admin@example.local | dev-doctor-001@example.local至dev-doctor-018@example.local | 无，从页面注册 |
| test | test-admin@example.local | test-doctor-001@example.local至test-doctor-018@example.local | 无，从页面注册 |
| demo | demo-admin@example.local | demo-doctor-001@example.local至demo-doctor-018@example.local | demo-user@example.local |

医生邮箱中的三位序号是稳定的样例序号，三环境对应关系相同：

| 医院 | 科室 | 医生姓名及邮箱序号 |
| --- | --- | --- |
| 示例市第一人民医院 | 内科 | 张医生001、王医生003、赵医生004 |
| 示例市第一人民医院 | 骨科 | 李医生002、刘医生005、陈医生006 |
| 示例市第一人民医院 | 皮肤科 | 杨医生007、周医生008、吴医生009 |
| 示例市第二人民医院 | 内科 | 徐医生010、孙医生011、胡医生012 |
| 示例市第二人民医院 | 骨科 | 朱医生013、高医生014、林医生015 |
| 示例市第二人民医院 | 皮肤科 | 何医生016、郭医生017、马医生018 |

例如dev第二医院皮肤科马医生使用`dev-doctor-018@example.local`登录。样例序号通过编号分配记录关联医生，不假定数据库自增ID或医生编号的数值恰好等于序号；已有人工新增目录时，这些数值可能不同。

登录当前环境管理员账号后，通过右上角“管理工作台”进入“医生账号查询”（`/admin/doctor-accounts`），可查看全部医院、科室下医生的实际登录邮箱、账号ID、绑定状态及密码说明，点击“刷新列表”读取最新关系。新增医生尚未绑定时仍显示；“首次绑定账号”进入原绑定功能（`/admin/doctor-accounts/bind`）。列表按当前数据库关系展示，管理员修改目录后可能与上方初始对应表不同。

### 初始密码与重复启动

对应环境`.env.dev`、`.env.test`或`.env.demo`中的`LOCAL_SEED_PASSWORD`，用于该环境管理员、全部预置医生及普通演示用户的首次创建。三环境均要求至少12位；配置由模板首次复制时沿用模板值，不在文档中展示密码。

dev/test启动时只补充缺失的预置账号及空医生绑定，保留已有账号密码、角色和业务数据。修改`LOCAL_SEED_PASSWORD`不会直接更新已创建账号的密码，重复`init`也不会恢复旧密码。遇到同邮箱非预置账号，或样例医生已绑定其他账号时，初始化事务回滚并停止启动，不覆盖账号或抢占绑定；管理员已删除的样例医生不会在普通启动时重新创建。医生账号自带USER和DOCTOR角色，管理员自带USER和ADMIN角色。

这些行为由Java后端执行，脚本、Maven和IDE启动方式一致；只需选择对应profile。跨环境账号和密码保存在各自数据库中，不会因为切换profile而同步。

### demo自动重置与test显式重置

demo清理仅针对自身数据库、缓存和消息；脚本、Maven、IDE入口共用Spring启动生命周期。已有环境在启动、运行或重置，或任一共用端口被占用时，在清理前拒绝。刷新页面或重启前端不会重置数据。每次后端启动使用新的JWT密钥，旧Token失效。

demo每次后端启动重新执行V1和V2，再按当前`.env.demo`的`LOCAL_SEED_PASSWORD`创建上述20个用户及医生绑定。

test普通重启保留数据，只有显式重置才清空自己的业务数据（包括自行注册的用户和预约），重建表和样例目录、清理目录缓存、注册验证码/票据及应用消息队列。保持共享容器运行，先停止当前整套应用，再重置并启动test。以下以当前运行test为例。

```bash
docker compose --env-file compose.env up -d --wait
python3 scripts/local_env.py stop test
python3 scripts/local_env.py reset test
python3 scripts/local_env.py start test
```

reset和正常启动共用项目级运行锁，重置后按当前`.env.test`的`LOCAL_SEED_PASSWORD`重新初始化管理员和18名医生账号，原子轮换.env.test中的JWT密钥；旧Token失效，普通重启继续使用轮换后的密钥。reset完成后退出，最后的start才会持续运行前后端。IDE启动的实例也持有锁，需在IDE中先停止，不能只凭脚本无PID就认为可以重置。dev没有自动重置入口，保留其数据。

## dev管理员授权

dev已预置`dev-admin@example.local`。注册只授予USER；如果还需要给其他已注册用户授予管理员权限，可由有权限的本地维护者核对目标库及账号后执行以下SQL，再重新登录取得ADMIN角色。替换示例邮箱，不在共享环境随意授权：

```sql
SELECT u.id, u.email, r.code FROM users u
LEFT JOIN user_roles ur ON ur.user_id=u.id
LEFT JOIN roles r ON r.id=ur.role_id WHERE u.email='student@example.com';
INSERT IGNORE INTO user_roles(user_id,role_id)
SELECT u.id,r.id FROM users u JOIN roles r ON r.code='ADMIN'
WHERE u.email='student@example.com';
```

操作后重查确认ADMIN已存在。JWT角色不会随数据库变更即时更新，医生账号首次绑定后也需要重新登录。

## test业务调时

启动test后，管理员可查询、设置或恢复业务时间：

```bash
python3 scripts/local_env.py clock test get
python3 scripts/local_env.py clock test set --instant 2030-01-01T00:00:00Z
python3 scripts/local_env.py clock test reset
```

命令用LOCAL_SEED_PASSWORD登录专用管理员；若已修改管理员密码，应使用有效凭据调用[调时API](api.md#test环境业务调时)。

设置后时间继续走秒；偏移仅存当前进程，重启恢复实时。调时影响使用业务Clock的预约、支付截止、签到及相关任务，不修改系统、数据库、浏览器、JWT或注册验证码/票据的有效时间。回拨不撤销已完成事实。dev/demo不注册调时接口。

MockPay继续保存在内存，应用重启后旧渠道支付查询和退款续接存在限制。demo是本地演示环境，不代表生产部署。

## 自动化测试与常驻环境

常驻`test`是可手动体验和调整业务时间的运行环境；Maven的`mysql-it`用于启动Testcontainers临时MySQL执行自动化测试，二者互不依赖。自动化测试不读取三环境私密配置，不要求运行Compose初始化或环境重置。测试运行命令见[README](../README.md#自动化测试)，已有结果见[测试报告](testing/latest-report.md)。

## 本地注册邮件

四个共享服务由同一Compose统一管理。Mailpit使用已有 `axllent/mailpit:v1.31` 镜像，容器名 `appoint-mailpit`，复用镜像健康检查。宿主后端通过 `127.0.0.1:1025` SMTP发信，浏览器打开[Mailpit](http://127.0.0.1:8025)查看验证码。默认无认证/TLS、无外部投递；邮件仅为本地测试使用，临时存储不保证退出后保留。

已完成初始化时，使用 `docker compose --env-file compose.env up -d --wait` 启动四个共享服务。脚本的 `start` 和 `stop` 管理前后端，Compose 的 `stop` 管理共享服务。

三环境共用收件页面，邮件标题通过 `[dev]`、`[test]`、`[demo]` 区分。SMTP配置位于application.yml；注册namespace位于对应profile配置。验证码和票据在Redis按环境前缀隔离，demo每次后端启动与test显式重置会清理自身auth键；普通dev/test重启保留并自然过期。已有账号及预置账号仍直接邮箱密码登录，新公开注册必须验证邮箱。
