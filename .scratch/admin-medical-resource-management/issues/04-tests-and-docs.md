# 04 验收测试与文档

Type: verification
Status: resolved

覆盖权限、父级校验、名称唯一、编号稳定、受限删除和预约快照，并更新 API 文档与 HTTP 示例。

## Comments

- 已覆盖权限、父级不存在、科室唯一、并发、编号稳定、受限删除和预约快照，并更新 API 文档与 HTTP 示例。
- `./mvnw test` 与 `./mvnw -Pmysql-it verify` 均通过。
