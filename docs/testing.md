# 测试与验证

自动化测试仅保留单元/Mock和MockMvc两层，覆盖业务规则、权限和主要接口流程。

## 测试范围

| 类型 | 验证重点 | 位置 |
| --- | --- | --- |
| 后端单元/Mock | 时间和状态规则、查询组装、支付渠道与消息处理分支 | src/test/java中的普通JUnit与Mockito用例 |
| MockMvc | 注册登录、就诊人、目录管理、预约支付、取消和就诊流程 | 标注`@RegressionTest`及`@AutoConfigureMockMvc`的用例 |
| 前端单元/Mock | 会话、请求、预约状态与展示规则 | frontend/tests/unit |

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

## 三环境历史验收

2026-11-15已完成test注册至接诊完成及异常分支、dev/demo注册至付款预约成功的真实环境验收。该结果适用于当时的代码版本，使用MockPay，与本轮自动化测试结果分别记录。

## 文档检查

```bash
python3 scripts/check_documentation.py
```

只读检查本地链接、代码围栏、JSON示例及REST/DTO文档契约，不连接数据库或启动服务。
