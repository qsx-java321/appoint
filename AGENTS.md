# Agent 协作说明

本项目是医院预约系统。默认使用中文沟通、编写文档和描述业务概念；技术框架、类名、接口名保持原文。

## 工作方式

- 实现前说明目标、关键假设和完成条件。会改变业务结果的歧义先澄清，常规实现细节沿用项目约定。
- 采用满足本次需求的简单方案，控制改动范围，清理本次产生的无用代码；无关问题单独说明。
- 按变更运行有意义的检查，报告实际结果与未验证范围；文档检查不代表业务测试已执行。
- 自动化测试维护单元/Mock、独立H2的MockMvc及按需运行的Testcontainers MySQL集成测试；均不连接dev/test/demo常驻资源。命令见[README](README.md#自动化测试)；`docs/testing/`仅存放测试报告，测试后按实际执行范围覆盖更新结果和日期。
- 多步骤任务给出简短计划。过程记录按需放在`.scratch/`，普通任务无需固定的spec/map/issues或归档清单。

## 文档维护

- 业务术语查阅[GLOSSARY.md](GLOSSARY.md)，相关设计原因查阅[docs/adr/](docs/adr/)；规则冲突要明确说明。
- 业务行为、API和实现边界分别维护在[业务流程](docs/business-flow.md)、[API](docs/api.md)、[模块设计](docs/module-design.md)，避免重复描述。
- 运行配置维护在[环境说明](docs/environments.md)，测试命令维护在[README](README.md#自动化测试)。
- README作为阅读入口，文档结构或使用方式变化时同步更新。每项业务规则优先维护一个主要说明位置。
- 完成的过程资料通过Git追溯，`.scratch/`临时内容默认不纳入Git。当前文档说明现行行为，整理后核对链接、JSON示例及API契约。

## 本地环境

- Python脚本仅保留`scripts/local_env.py`，用于环境初始化、启停及管理。

- dev/test/demo共用Compose中的MySQL、Redis、RabbitMQ和Mailpit；数据库、Redis namespace、RabbitMQ vhost及凭据独立，测试邮件按标题环境和收件人区分。一次按需启动一套，切换前停止上一套。
- dev保留原appoint数据；test默认保留，显式重置；demo每次后端启动恢复初始状态。同环境锁与重置保护保留，不增加跨环境代码互斥。
- Compose操作显式使用`--env-file compose.env`；对应profile加载`.env.dev/.env.test/.env.demo`。私密配置不提交、不输出；保留密码和数据卷。
- IDE/Maven选择单个profile，工作目录为项目根目录，默认dev。test可调业务Clock，dev/demo使用上海真实时间；MockPay渠道状态仅存内存。
