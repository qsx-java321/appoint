# 测试报告

更新日期：2026-11-17，邮箱验证码与注册票据改造。被测实现基于 `6a321a5`，在开发分支 `feat/email-signup-verification` 完成；实现与本报告同次提交，文档在验收后复核整理。

| 检查 | 数量 | 通过 | 失败/错误/跳过 |
| --- | --- | --- | --- |
| 后端单元/Mock与H2/MockMvc | 337（38类） | 337 | 0/0/0 |
| Testcontainers MySQL集成 | 12（4类） | 12 | 0/0/0 |
| 前端Vitest | 30（7文件） | 30 | 0/0/0 |

`./mvnw clean test`、最终 `./mvnw -Pmysql-it verify`、前端 `npm test`、`npm run build` 均退出0，前端改动文件Prettier检查通过。新增2项后端测试方法、5项前端用例；原有注册夹具完整经过发码、验证和票据注册接口。

独立自动化使用H2、临时MySQL和测试替身，不访问dev/test/demo常驻数据库、Redis或SMTP。Redis/邮件边界在MockMvc中用测试替身，验证码真实五分钟TTL、SMTP和浏览器流程在三环境分别验收，见[邮箱注册报告](email-signup-report.md)。

一次MySQL组命令在前置普通回归阶段因宿主时钟回拨约2.1秒触发雪花ID生成错误，未进入MySQL测试；未修改业务实现，重新完整执行后337+12项通过。最初沙箱内Mockito附加受限，最终测试在具备JVM附加权限的执行环境中完成。

本轮常驻验收仅针对注册登录和约定错误场景，未重新执行完整预约支付就诊链路。历史主流程见[API报告](api-flow-report.md)，医生账号查询历史验收见[医生账号报告](2026-11-17-doctor-accounts-report.md)。历史结果不能视为本轮业务全流程回归。原三环境历史记录可通过Git追溯。

逐类统计见[JSON报告](latest-report.json)。未新增限流、安全加固、并发专项、故障补偿或真实外部邮件投递测试。

## 三环境人工验收

本轮邮箱注册验收按test→dev→demo串行执行，结果见[邮箱注册报告](email-signup-report.md)。历史目录、预置账号及权限验收见[2026-11-17医生账号报告](2026-11-17-doctor-accounts-report.md)，历史完整预约就诊主流程见[API报告](api-flow-report.md)。各轮验证范围和代码版本分别记录。
