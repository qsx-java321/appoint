# Agent 协作说明

本项目是一个医院预约系统，默认使用中文沟通、编写文档和描述业务概念；技术框架、类名、接口名和必要的英文专用名词保持原文。

## Agent skills

### Issue tracker

项目使用本地 Markdown 文件管理 issue 和需求说明，文件放在 `.scratch/<feature-slug>/` 下。详见 `docs/agents/issue-tracker.md`。

### Triage labels

使用默认的 `triage` 标签：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human` 和 `wontfix`。详见 `docs/agents/triage-labels.md`。

### Domain docs

项目采用 single-context 布局，使用根目录下的 `GLOSSARY.md` 和 `docs/adr/` 保存领域术语与架构决策。详见 `docs/agents/domain.md`。
