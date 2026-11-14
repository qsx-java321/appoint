# 03 常驻环境配置与启动校验交付

日期：2026-11-14。任务：[03](../../issues/03-runtime-config.md)。基线：`748eca3`。本报告随阶段代码提交；交付提交可用 `git log -1 -- .scratch/environment-isolation/evidence/stage03-policy/report.md` 追溯。

## 目标与实际行为

- dev/test/demo明确导入各自私密环境文件，默认dev；沿用appoint、appoint_test、appoint_demo以及原定端口、Redis namespace和RabbitMQ vhost。
- `LocalEnvironmentPolicy`通过spring.factories在ConfigData加载后、创建应用上下文前验证最终配置。生命周期复用同一校验，错误demo目标在连接及清理前拒绝。
- 复现并修复单属性查找漏掉索引列表/Shell覆盖的问题。采用Spring配置属性名称映射，拒绝Flyway独立目标、Hikari底层连接覆盖（含data-source-properties）、Rabbit addresses、Redis URL/cluster/sentinel及自动配置排除；缺凭据、混合profile、关闭保护/迁移也拒绝。
- 密钥比较只在本机私密文件间读取，不输出值；测试使用临时目录中的虚构配置。错误JDBC URL只输出固定错误，避免异常泄漏连接串。
- 校验从重置生命周期移到策略类，让03可以独立交付。时钟装配用例移到尚待交付的TestClockConfigTest，不在本阶段计为通过。

## 验证与证据

仅导出基线的pom.xml、mvnw、.mvn和src，叠加本阶段文件，在临时目录执行；未包含工作区未提交的生命周期、时钟、V11、回归托管者或pom回归入口。文件范围见[snapshot.json](snapshot.json)，完整源码摘要见[source-manifest.sha256](source-manifest.sha256)。

```bash
SPRING_DATASOURCE_URL=jdbc:forbidden:host-override SPRING_FLYWAY_SCHEMAS_0=forbidden \
./mvnw -o -Dtest=LocalEnvironmentPolicyTest,RegressionIsolationTest \
-DargLine=-javaagent:/home/qsx/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar test
```

9项通过，零失败/错误/跳过：策略8项、H2回归隔离1项。覆盖三份真实profile文件的映射与独立导入、正常配置、错误目标、底层覆盖、Shell和索引列表、混合profile、缺凭据、共享JWT、禁止关闭保护/迁移、真实SpringApplication启动时的提前拒绝，以及宿主注入不改变H2目标。见[测试摘要](test-summary.log)和[用例结果](results.json)。

初始新增负例4项中2项失败，证明漏检；修复后策略7项通过。首次组合执行的H2用例遇到Mockito沙箱自挂载限制，显式Java agent后单项通过。最后新增三profile文件核验并在独立提交快照上运行上述9项，全部通过。未启动常驻应用、Docker或容器，未访问共享业务或管理端口，未读取真实.env文件作为测试夹具。

文档检查及`git diff --check`通过，见[静态检查](static-checks.json)。分别核验整个在途工作区及仅含暂存内容的独立文档快照；两者均通过。不将其余功能计为业务验证通过。暂存的11个实现/配置文件摘要与被测快照一致。

## 范围限制与后续

本阶段关闭03，不代表整个环境改造交付。demo启动重建、同环境锁、test重置及文件JWT轮换仍属于04；调时、HTTP变量和独占容器回归分别属于05–07。保留已有生命周期与MockPay装配历史证据，本轮不重跑这些业务测试，也不宣称IDE实际操作已经验证。不增加跨环境互斥。

工作区生命周期/文件辅助类仅适配校验移动，仍留待04提交；其余在途实现保留。下一步优先完成04的test密钥文件轮换及重置边界。阶段仅本地提交，不推送。
