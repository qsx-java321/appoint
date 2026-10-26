# 实现预约过期扫描

Type: feature
Status: resolved

按截止时间游标分批扫描，每张预约独立事务关闭，单笔失败不阻塞后续记录。

## Comments

默认每 30 秒扫描 100 条，测试环境默认禁用自动调度。
