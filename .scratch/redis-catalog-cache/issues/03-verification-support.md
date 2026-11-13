# 03 补齐真实 Redis 测试和隔离验收

Type: test
Triage: ready-for-agent
Status: resolved
Blocked by: 02

按[主方案](../../../docs/redis-catalog-cache-design.md)第7～8节增加 redis-it profile 与真实 Redis 测试；需要数据库竞争时组合 MySQL。重点验证真实提交/回滚、旧值回填竞态、故障回源、双实例、编解码和指标，不仅重复单元实现断言。

扩展 compose_acceptance.py：显式启用缓存，每轮唯一 namespace，重启阶段沿用 namespace；证明缓存命中及目录修改失效；应用退出后定向清理并核对清理成功。开发键与其他环境必须保留；故障注入使用独占测试容器。

完成条件：R01～R15全部有明确测试或验收责任入口；真实 Redis 用例实际运行通过；常规测试仍无需开发 Redis。保存脱敏结果、资源标识及清理证据；不在共享 Redis 上清空整库。

## Comments

- 2026-11-13：仅建立任务，未实施；原HTTP验收只检查Redis健康，不能作为缓存测试证据。

- 2026-11-14：代码/验证入口已完成；聚焦对象测试8项及真实MySQL+Redis测试7项通过。完整回归由04继续执行，结果另存verification/results.json。
