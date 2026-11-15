# 01 demo预约成功实测

Type: verification
Triage: ready-for-agent
Status: resolved
Blocked by: none

## 完成条件

demo真实页面注册到已付款、预约确认取号，停止于待签到；保留服务及记录，dev/test数据不变。

## Comments

- 用户明确沿用前轮思路，终点为预约成功。
- 基线提交`6c1ffe57b709cd0a8a7c7d994f4b1863b31a17dc`，保留前两轮未提交验收资料。
- 已停dev并启动demo；预置三类账号及医生绑定通过。页面6组检查、35次HTTP一次通过，SQL确认支付、库存、消息与未签到事实；dev/test关键记录投影指纹不变。
- demo及成功记录保留运行，业务实现未变，详见[报告](../report.md)。
