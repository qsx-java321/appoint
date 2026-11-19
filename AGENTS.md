# Agent 协作说明

项目是医院预约系统。默认使用中文沟通和撰写文档，技术框架、类名和接口名沿用原名。

## 开发与验证

- 先说明多步骤任务的目标、关键假设和完成条件。涉及业务结果的歧义先澄清，常规实现沿用项目约定。
- 按变更范围运行相关检查，并报告执行结果和未验证范围。[测试命令](README.md#自动化测试)包含单元、MockMvc、Testcontainers MySQL、前端和脚本测试，均使用独立测试资源。手动联调用 test 环境。

## 文档

- 改业务行为时查[术语表](GLOSSARY.md)和[业务流程](docs/business-flow.md)。
- 改接口时查 [REST API](docs/api.md)，改模块职责或事务时查[模块设计](docs/module-design.md)。
- 改启动、配置或数据保留规则时更新[环境指南](docs/environments.md)。对外入口或快速启动变化时更新 [README](README.md)。
- 以源码和配置核对现行行为。每条规则在主要文档中讲清，其他位置使用链接。检查受影响的链接、接口契约和示例。

## 本地环境

- 使用 `scripts/local_env.py` 初始化、启停、重置和调时。IDE、Maven 或 JAR 启动时，工作目录为项目根目录。
- 三套环境共用前后端端口，切换前先停止当前应用。操作 Compose 时指定 `--env-file compose.env`。
- 默认启动 demo，启动会重建 demo 数据。dev 保留数据，test 通过显式重置清理。重置或调时前查[环境指南](docs/environments.md)，私密配置保存在忽略文件中。
