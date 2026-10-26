# 统一预约生命周期事务

Type: refactor
Status: resolved

支付、取消和过期统一使用预约单、支付单、号源的锁顺序。

## Comments

真实 MySQL 测试已覆盖支付与取消竞争及并发重复支付。
