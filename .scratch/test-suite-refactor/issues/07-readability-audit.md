# 07 完善可读性、规范和场景审计

Type: test/refactor
Status: resolved
Blocked by: 06

## 工作内容

注释说明层次、事务、故障和隔离；重命名误导变量；大型类按职责组织，不硬拆；核对原方法和独有断言；建立 docs/testing.md。

## 验收

受影响测试通过、文档链接和场景映射完整、历史 fixture 不丢失。

## Comments

- 尚未验收，相关命令与结果写入 verification/commands.md。

- 聚焦常规111+MySQL10、确认单测5+Outbox可靠性9通过；260原方法全部有去向，19受保护样本/架构负例不变。补充类职责、去除重复注入/全限定噪音、统一架构正反例规则、验证故障实际调用，测试规范已落盘。临时清理脚本字段名检查中止后按实际jdbcTemplate修正，无生产修改。
