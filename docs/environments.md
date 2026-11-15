# dev、test、demo三环境使用说明

三套环境共用Docker Compose中的真实MySQL、Redis、RabbitMQ，前后端在宿主机启动。dev用于日常业务体验，test用于独立操作业务，demo用于首次体验和演示。三环境的业务代码和规则一致，通过独立配置、身份和数据生命周期区分用途。

## 环境对应关系

| 项目 | dev | test | demo |
| --- | --- | --- | --- |
| Spring profile | dev（默认） | test | demo |
| 前端→后端 | 5173→8080 | 5174→8081 | 5175→8082 |
| MySQL数据库 | appoint（原库） | appoint_test | appoint_demo |
| Redis namespace | dev | test | demo |
| RabbitMQ vhost | /appoint-dev | /appoint-test | /appoint-demo |
| 私密配置 | .env.dev | .env.test | .env.demo |
| 数据生命周期 | 一直保留 | 默认保留，显式重置 | 每次后端启动恢复初始状态 |
| 业务时间 | 上海真实时间 | 默认实时，可运行中调时 | 上海真实时间 |

三个数据库使用同一套Flyway迁移与基础目录，不复制dev业务数据。数据库账号、RabbitMQ权限和JWT密钥独立，Redis按前缀隔离。启动时校验最终连接目标；同环境锁和端口检查先于重置，脚本、Maven、IDE使用同一生命周期。

电脑资源有限，使用时按需启动一套，切换前停止上一套；程序只保留同环境重复实例与重置保护，不增加跨环境互斥。

## Flyway初始化基线

当前仅保留[src/main/resources/db/migration/V1__init_schema.sql](../src/main/resources/db/migration/V1__init_schema.sql)，供全新空库初始化。它合并原V1至V11的SQL和Java迁移结果，一次创建22张业务表、索引、约束和原有基础数据；环境账号仍由应用启动生命周期创建。历史示例场次保留，当前四场预约继续按首次下单生成。

这是面向重新部署的首版整理，不提供旧库原地升级。已经执行旧迁移链的数据库，其Flyway历史和V1校验和与当前代码不兼容；重复运行init、执行cutover-dev或复制配置均不会转换这些历史。需要保留旧数据时应继续使用整理前的代码，并另行制定数据迁移方案；确认放弃旧数据后，使用空库重新初始化，不通过repair、删除历史记录或baseline跳过校验。

应用启动时由Flyway执行此脚本，不需要手工导入SQL。成功后数据库版本为1；再次启动由Flyway验证并跳过已执行的V1。后续结构变更新增V2及后续版本。历史迁移可从Git提交`eb84890`追溯。

## 首次准备

以下脚本使用Linux进程信息与信号，支持Linux及WSL2。准备Git、JDK 21、Python 3、Docker和Compose v2；需要前端时准备Node 24（至少24.12）及npm。WSL中使用同一发行版内的Java、Python和Node，确保当前终端能执行`docker compose version`并访问Docker服务。

首次安装需要联网下载Docker镜像、Maven及应用依赖，无需预先安装Maven。以下从GitHub克隆开始，进入项目根目录后执行初始化：

```bash
git clone <仓库地址>
cd <项目目录>
python3 scripts/local_env.py init
```

init先从`compose.env.example`、`.env.dev.example`、`.env.test.example`和`.env.demo.example`复制缺失的实际配置，文件权限为600，已有文件保持原样。模板按本项目约定保存现有本地配置，初始化沿用模板中的账号、密码和密钥，不再随机生成新值；模板缺失且实际文件也不存在时，在启动共享服务前报错。

随后启动共享Compose并创建缺少的数据库、账号和vhost。重复执行保留已有密码和业务数据；卷内凭据或权限不匹配时停止，不擅自改密码或删除数据卷。init不创建业务表，业务结构在后端启动时由Flyway初始化。

首次使用dev需要执行启动预检；切换已有dev时，先备份原库并停止旧应用：

```bash
python3 scripts/local_env.py cutover-dev
```

空库预检只检查后端端口和旧默认vhost中的消息，不查询尚未建立的业务表；非空库还检查未完成资金和Outbox工作，缺少必要业务表时中止。两种情况均在旧vhost仍有待处理或未确认消息时中止。通过后保存本机启动标记；保留原appoint库、旧默认vhost、旧缓存及数据卷。该标记不代替Flyway历史校验，旧迁移链数据库的限制仍按上述初始化基线执行。

Compose操作始终使用`docker compose --env-file compose.env ...`。停止共享依赖用`docker compose --env-file compose.env stop`，保留卷。

## 启动和切换

先加载Node 24，在frontend目录安装依赖：

```bash
npm ci
```

回到项目根目录，选择需要的环境：

```bash
python3 scripts/local_env.py start dev
python3 scripts/local_env.py stop dev
python3 scripts/local_env.py start test
```

浏览器使用http://127.0.0.1:5173（dev）、5174（test）、5175（demo）。`status <环境>`查看脚本托管的进程；日志及进程记录位于被忽略的.local-env目录。脚本会同时启动对应前后端，stop只停止该环境的托管进程，保持Compose运行。

start和reset的Maven命令允许联网下载依赖。start为后端下载与启动预留10分钟；等待期间可查看`.local-env/<环境>-backend.log`。首次执行前必须完成npm ci，避免后端启动后因缺少前端依赖而退出。

电脑重启或共享容器已停止时，先在项目根目录启动共享依赖：

```bash
docker compose --env-file compose.env up -d --wait
```

IDE启动时，可在src/main/resources/application.yml中将`spring.profiles.default`从`dev`改为`test`或`demo`，然后运行`AppointApplication.main`；工作目录设为项目根目录，并留空IDE的Active profiles、`--spring.profiles.active`参数和`SPRING_PROFILES_ACTIVE`环境变量，让文件中的默认值生效。Spring自动叠加对应application-dev/test/demo.yml并导入对应私密配置。

只启动Spring Boot时不需要Node或npm，在完成init和dev预检后，也可用Maven显式选择环境：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

将dev替换为test或demo即可；这两套环境无需dev预检标记。显式选择的profile优先于文件中的默认值。两种启动方式使用同一套环境配置，每次只选一个profile。dev后端监听127.0.0.1:8080，根路径没有网页；需要页面时，前端在frontend目录分别使用`npm run dev`、`npm run dev:test`、`npm run dev:demo`。

前端代理固定到同环境后端；API_PROXY_TARGET或命令行端口与环境冲突时拒绝启动。后端在连接和重置前校验最终数据库、消息、缓存与端口目标，拒绝独立Flyway/Hikari连接覆盖等会绕开目标配置的方式。

## 账号及重置

- dev不自动新增或修改已有账号。
- test补充管理员test-admin@example.local，普通重启保留业务数据和用户已修改的密码。
- demo启动后补充demo-user@example.local、demo-doctor@example.local、demo-admin@example.local，并将示例医生1绑定到医生账号。

初始化密码读取对应环境文件的LOCAL_SEED_PASSWORD；从模板首次创建配置时沿用模板值。密码具体值不在本文重复展示，已有用户修改的密码不会因重复init而恢复为模板值。

demo清理仅针对自身数据库、缓存和消息；脚本、Maven、IDE入口共用Spring启动生命周期。同环境第二实例或后端端口占用时，在清理前拒绝。刷新页面或重启前端不会重置数据。每次后端启动使用新的JWT密钥，旧Token失效。

test只有显式重置才清数据。停止test后执行：

```bash
python3 scripts/local_env.py reset test
```

reset和正常启动共用同环境锁，重置后重新初始化管理员，原子轮换.env.test中的JWT密钥；旧Token失效，普通重启继续使用轮换后的密钥。IDE启动的实例也持有锁，不能只凭脚本无PID就认为可以重置。

## dev管理员授权

注册只授予USER。dev可由有权限的本地维护者核对目标库及账号后执行以下SQL，再重新登录取得ADMIN角色；已有账号和角色不被自动改写。替换示例邮箱，不在共享环境随意授权：

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

设置后时间继续走秒；偏移仅存当前进程，重启恢复实时。调时影响使用业务Clock的预约、支付截止、签到及相关任务，不修改系统、数据库、浏览器或JWT时间。回拨不撤销已完成事实。dev/demo不注册调时接口。

MockPay继续保存在内存，应用重启后旧渠道支付查询和退款续接存在限制。demo是本地演示环境，不代表生产部署。
