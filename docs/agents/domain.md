# Domain 文档

在探索或修改代码前，先阅读：

- 根目录的 `GLOSSARY.md`
- 与当前代码区域相关的 `docs/adr/` 中的 ADR（Architecture Decision Record）

如果这些文件或目录尚不存在，静默继续，不要提前要求创建。只有在领域术语或架构决策真正确定时，再按需创建。

## 文档布局

本项目采用 single-context 布局：

- `GLOSSARY.md`：领域术语表
- `docs/adr/`：架构决策记录
- `src/`：源代码

## 使用领域术语

在 issue 标题、重构提案、假设和测试名称中，优先使用 `GLOSSARY.md` 已定义的领域术语。

## 处理 ADR 冲突

如果新方案与已有 ADR 冲突，必须明确指出冲突，不要静默覆盖原有决策。
