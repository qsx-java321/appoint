# 02 实现目录缓存及提交后失效

Type: feature
Triage: ready-for-agent
Status: resolved
Blocked by: 01

阅读[主方案](../../../docs/redis-catalog-cache-design.md)第3～7节后实施。增加 Redis 依赖及配置、小型 catalog 缓存组件、类型明确的 JSON 编解码、三类目录缓存、事务内绕过与管理提交后定向失效，补指标和故障回源。

删除科室/医生时复用锁定实体取得父 ID；缓存异常边界不得吞数据库错误或把已提交管理操作伪装成失败。本地配置开关和 namespace 明确，常规测试默认关闭；Compose 保留数据卷并补纯缓存内存策略。

完成条件：聚焦验证命中/回源、TTL选择、编解码、开关、事务绕过和九种写入失效映射；原目录 HTTP 契约不变。记录实际改动与结果，不以本步骤代替 R01～R15 完整验收。

## Comments

- 2026-11-13：用户已授权实施；开始改造。

- 2026-11-14：代码/验证入口已完成；聚焦对象测试8项及真实MySQL+Redis测试7项通过。完整回归由04继续执行，结果另存[归档验收结果](../../archive/redis-catalog-cache/evidence/redis-catalog-cache/results.json)。
