# 实际执行记录

每条记录包括阶段、命令、日志、数量及失败/错误/跳过；初次失败和修复后的运行分别记录。/tmp日志为本机临时证据，统计和结论在本文保存。


## 01 常规基线

- 命令：`./mvnw -o clean test`
- 日志：`/tmp/test-refactor-01-unit.log`
- Maven汇总（普通/集成分别计）：[('323', '0', '0', '0')]；耗时：['43.906 s']; BUILD SUCCESS=True。

## 01 MySQL基线

- 命令：`./mvnw -o -Pmysql-it verify`
- 日志：`/tmp/test-refactor-01-mysql.log`
- Maven汇总（普通/集成分别计）：[('323', '0', '0', '0'), ('38', '0', '0', '0')]；耗时：['03:02 min']; BUILD SUCCESS=True。

## 01 可靠性基线

- 命令：`./mvnw -o -Preliability-it verify`
- 日志：`/tmp/test-refactor-01-reliability.log`
- Maven汇总（普通/集成分别计）：[('323', '0', '0', '0'), ('16', '0', '0', '0')]；耗时：['01:43 min']; BUILD SUCCESS=True。
