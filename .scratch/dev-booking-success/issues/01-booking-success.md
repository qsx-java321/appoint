# 01 dev 预约成功实测

Type: verification
Triage: ready-for-agent
Status: resolved
Blocked by: none

## 完成条件

正式dev环境真实页面从注册走到已付款、预约确认并取号，保存证据，保留已有数据和新成功记录，停止于待签到。

## Comments

- 用户明确本轮终点为预约成功；不延伸至到院接诊。
- 基线提交为 `6c1ffe57b709cd0a8a7c7d994f4b1863b31a17dc`；前轮test验收资料未提交，继续保留。
- test已停止，正式dev启动并保持运行。真实浏览器6组检查、36次HTTP通过；SQL确认成功预约、支付、库存和消息记录，且没有签到。
- 首轮URL断言误判造成脚本中断，证据和空账号保留；新账号完整复跑通过。原有预约与目录保持一致，未改设置、未调时。见[实测报告](../report.md)。
