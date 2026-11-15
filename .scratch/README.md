# 临时工作目录

本目录用于确有需要的临时任务记录和验收输出，完成后将长期说明归入docs；普通任务无需固定的计划、问题和归档文件。

已完成的开发过程、三环境验收脚本及原始证据保存在Git提交`7b8a370`。按原路径查看，例如：

```bash
git ls-tree -r --name-only 7b8a370 -- .scratch
git show 7b8a370:.scratch/demo-booking-success/report.md
```

当前能力与运行说明从[项目README](../README.md)进入。本目录的临时内容默认不纳入Git，保留本说明作为入口。
