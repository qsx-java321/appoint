# 测试与验证

自动化测试仅保留单元/Mock和MockMvc两层，覆盖业务规则、权限和主要接口流程。

## 测试范围

| 类型 | 验证重点 | 位置 |
| --- | --- | --- |
| 后端单元/Mock | 时间和状态规则、查询组装、支付渠道与消息处理分支 | src/test/java中的普通JUnit与Mockito用例 |
| MockMvc | 注册登录、就诊人、目录管理、预约支付、取消和就诊流程 | 标注`@RegressionTest`及`@AutoConfigureMockMvc`的用例 |
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

测试无需Docker Compose。Maven依赖已缓存时可加`-o`。

环境脚本单元/Mock在项目根目录执行，仅使用Python标准库、临时目录和Mock：

```bash
python3 -m unittest discover -s scripts -p 'test_*.py'
```

## 隔离与边界

- MockMvc显式加载src/test/resources/regression/application.yml，使用H2内存库，屏蔽宿主连接覆盖，不读取三环境私密配置。
- Redis缓存、RabbitMQ连接和后台任务默认关闭；前端用例以Mock替代API请求。
- 真实提交的H2夹具按新增ID清理，事务用例回滚；业务Clock按需固定或在每例重置。
- 当前测试不覆盖真实MySQL锁行为、Redis/RabbitMQ运行故障和浏览器端到端流程。

## 最近验证

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

2026-11-15环境脚本单元/Mock共15项通过，覆盖缺失配置从模板恢复、600权限、重复初始化保留配置、凭据验证失败不改密码、空库与非空库预检、残缺结构及资金/消息未完成时阻断。Maven启动和重置命令不再强制离线。测试使用临时配置并Mock所有Docker、数据库和消息调用，不读取本机凭据，不连接常驻资源。

此结果不代表已执行真实Docker空机部署，也未验证首次联网下载耗时。恢复步骤见[首次准备](environments.md#首次准备)。

## 三环境历史验收

2026-11-15已完成test注册至接诊完成及异常分支、dev/demo注册至付款预约成功的真实环境验收。该结果适用于当时的代码版本，使用MockPay，与本轮自动化测试结果分别记录。

## 文档检查

```bash
python3 scripts/check_documentation.py
```

只读检查本地链接、代码围栏、JSON示例及REST/DTO文档契约，不连接数据库或启动服务。
