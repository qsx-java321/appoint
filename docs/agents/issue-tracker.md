# Issue管理与完成归档

## 创建和执行任务

新任务使用 `.scratch/<feature-slug>/spec.md` 保存需求，`map.md` 保存任务地图，`issues/<NN>-<slug>.md` 保存独立任务；编号从01开始。创建、读取或归档任务时使用本约定。

任务用 `Type:` 记录类型，`Triage:` 记录分流标签（见[标签约定](triage-labels.md)），`Status:` 记录执行状态。待实施为open，进行中为claimed，完成为resolved；只有Blocked by列出的前置任务均resolved时才开始。讨论与实际处理结果追加到 `## Comments`。

## 完成条件与归档

完成后先核对需求、issue和实际证据，更新状态并同步现行文档。每个已完成阶段至少保存目标、实际行为、关键取舍、任务编号及交付提交、执行命令、源码检查点、结果和验证限制。

阶段关闭后并入 `.scratch/archive/<topic>/summary.md`，证据放在对应 `evidence/<feature-slug>/`；当前有效的需求和维护说明进入docs。原始任务正文可在确认有信息去向和完整Git检查点后合并移除，逐文件记入 `.scratch/archive-manifest.json`，包含原路径、原摘要、处置、新路径/章节和追溯提交。未提交的原文先保留，直到有可追溯的Git检查点。

JSON、摘要和诊断SQL保持原内容；Markdown可更新导航并标明历史角色，历史命令和数字保留原检查点。更新[归档入口](../../.scratch/README.md)，运行 `python3 scripts/check_documentation.py`，报告已执行检查和未执行部分。

## 读取历史任务

先从归档索引找主题总结，再读证据；需要原spec、issue或讨论时，用清单中的sourceCommit与source执行 `git show <commit>:<path>`。新需求单独建任务，历史已关闭事项不因旧未来时态重新变为待办。
