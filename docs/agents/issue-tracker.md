# Issue 跟踪

Issue 和规格使用本地 Markdown。每个功能放在 `.scratch/<功能>/`，规格写入 `spec.md`，每个 issue 单独写入 `issues/NN-<名称>.md`，从 `01` 编号。

- 在 issue 顶部用 `Status:` 记录[分类状态](triage-labels.md)。
- 在 issue 末尾的 `## Comments` 记录讨论。
- 用 `Blocked by: NN, NN` 记录同一功能内的前置 issue。
- 发布或读取 issue 时，创建或打开对应的本地文件。

`.scratch/` 下的工作文件默认被 Git 忽略。
