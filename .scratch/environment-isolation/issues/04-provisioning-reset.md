# 04 资源初始化、账号与重置生命周期

Type: enhancement
Triage: ready-for-agent
Status: open
Blocked by: 03

## 工作与文件

新增 `scripts/local_env.py`及应用侧localenv生命周期支持，必要时调整compose.yaml但保留现有卷。实现幂等多库/账号/vhost准备、进程管理、test显式重置、demo所有启动方式的自动重置、实例锁和账号初始化。

按照[正式方案](../../../docs/environment-isolation-plan.md)第4节执行顺序：demo校验/排他/清理先于Flyway与后台工作，初始化成功后再对外可用。每次demo启动及test显式重置使旧JWT失效；dev数据和账号原样保留。

## 完成条件与验证

- E02–E05与E09破坏性操作边界在独占容器中通过。
- 测试首次与已有卷、重复init、配置错误、第二实例、端口占用、准备中途失败及清理失败。
- 所有支持的demo启动方式满足同一行为，不只验证脚本分支；进程退出释放锁。
- test重启不清数据；test重置只在停止状态执行并初始化联调管理员。

## Comments

- 尚未实施。旧dev消息切换只在08实际过渡时按维护步骤执行，不用真实dev测试重置。
