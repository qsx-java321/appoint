# 02 提高断言准确性并固定历史迁移边界

Type: test/refactor
Status: resolved
Blocked by: 01

## 工作内容

收紧数据库及业务异常类型、错误码与失败后的数据断言；最新初始化不依赖迁移总数，历史升级固定 V2/V4/V5/V10。

## 验收

迁移与持久化常规测试及对应 MySQL 测试通过。

## Comments

- 尚未验收，相关命令与结果写入 verification/commands.md。

- 聚焦常规10、真实MySQL4全部通过。首次CHECK断言发现实际包装为UncategorizedSQLException，改为精确验证3819/HY000/约束名后重跑成功。临时编辑脚本语法错误未改源码；此前预执行不计改造验收。
