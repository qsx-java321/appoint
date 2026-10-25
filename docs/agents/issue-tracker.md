# Issue 管理

本项目使用本地 Markdown 文件作为 issue tracker，不依赖 GitHub、GitLab 或其他外部服务。

## 文件约定

- 每个 feature 使用一个目录：`.scratch/<feature-slug>/`
- feature 的需求说明文件为 `.scratch/<feature-slug>/spec.md`
- 实现任务拆分为独立文件，放在 `.scratch/<feature-slug>/issues/`
- 任务文件命名格式为 `.scratch/<feature-slug>/issues/<NN>-<slug>.md`，编号从 `01` 开始
- 每个 issue 文件顶部附近使用 `Status:` 记录 triage 状态
- 讨论和处理记录追加到文件末尾的 `## Comments` 标题下

## 发布 issue

当技能要求发布 issue 时，在对应的 `.scratch/<feature-slug>/` 目录下创建文件；目录不存在时一并创建。

## 获取 issue

当技能要求获取指定 issue 时，读取用户提供的文件路径或 issue 编号对应的文件。

## Wayfinding

- 任务地图：`.scratch/<effort>/map.md`
- 子任务：`.scratch/<effort>/issues/NN-<slug>.md`
- 子任务使用 `Type:` 记录类型，使用 `Status:` 记录 `claimed` 或 `resolved`
- 子任务可以通过 `Blocked by:` 声明依赖
- 只有所有依赖都为 `resolved` 的任务才视为 unblocked
