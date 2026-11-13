# Redis目录缓存交付

2026-11-13完成方案确认，2026-11-14按用户授权实施并完成实现与验证，按用户要求提交Git。源码基线为`4a4cc81980cfeab107543123847d7eb62b645785`。本轮新增Redis目录缓存，没有新增数据库迁移，MySQL仍为V10。

医院、科室和医生列表采用固定TTL缓存；事务内绕过共享缓存，目录管理提交后定向删除。Redis异常回源，已提交的管理操作不因失效失败返回失败。详细行为见[实施契约](../../../docs/redis-catalog-cache-design.md)，[任务地图](../../redis-catalog-cache/map.md)保留原文和状态。

## 验收证据

- [逐类测试及命令结果](evidence/redis-catalog-cache/results.json)：干净常规389、完整Redis7、MySQL38、消息可靠性16通过，profile重复常规不累计。
- [97次真实HTTP记录](evidence/redis-catalog-cache/http-report.json)：全部状态符合预期，覆盖缓存和原预约、支付、退款、签到接诊主线。
- [被测源码摘要](evidence/redis-catalog-cache/source-manifest.sha256)：记录源码、配置示例、构建和验证脚本，不包含本地凭据。
- 启动前Redis不可达的补强测试复验成功：第二个应用可启动并回源，恢复后命中共享缓存；已记录在results.json。
- 最终文档检查通过：76份Markdown、550个本地链接/锚点、60个测试类清单匹配；源码摘要和git空白检查通过。HTTP私密字段逐项确认已脱敏。

本次HTTP验收成功退出，先停止隔离应用，再定向删除本轮Redis键；其他namespace守卫键保留验证成功，随后清理隔离数据库、账号与RabbitMQ vhost。保留现有开发服务数据卷，未清理历史其他验收库。业务时间控制只使用测试启动入口。

## 取舍与回退

分钟级最终一致性是用户已确认的边界；提交后删键仍允许并发旧值回填，Redis故障可能增加数据库压力。不承诺未测量的性能百分比。缓存可以关闭，重新开启使用新namespace或在停用期间清理旧前缀，避免复用旧值。

入口限流、场次库存缓存、JWT撤销和Redis集群未纳入本次范围。完整任务材料在本次提交中保留，未删除原方案或改写历史验收记录。

## 开发环境交付状态

2026-11-14 00:21（Asia/Shanghai）已正常停止会话此前启动的旧应用，并用现有.env启动当前版本；8080未登录目录请求返回401，MySQL版本V10，MySQL/Redis/RabbitMQ均healthy。开发应用和基础服务保留运行，实际本地.env已补缓存开关与namespace但不纳入Git。
