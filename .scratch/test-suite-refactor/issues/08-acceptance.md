# 08 完整验收与文档收口

Type: test/refactor
Status: ready-for-agent
Blocked by: 07

## 工作内容

串行 clean test、mysql-it、reliability-it、隔离 Compose HTTP；核对数量差异、源码范围和生产 JAR；独立确认隔离资源清理。

## 验收

四类验收成功、零无解释跳过；原生产源码与配置未改，每阶段有提交，不推送。

## Comments

- 尚未验收，相关命令与结果写入 verification/commands.md。
