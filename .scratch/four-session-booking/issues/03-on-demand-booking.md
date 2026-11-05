# 03 按需生成与预约创建

Type: feature
Status: resolved
Blocked by: 01, 02

切换预约请求契约，实现并发安全的场次创建、窗口校验、暂占和幂等。

## Comments

- 已切换创建请求与幂等摘要，实现按需生成、最终写入日期校验和原子暂占；新增 MySQL 21 人竞争测试。

