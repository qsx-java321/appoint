# dev 预约成功验收报告

日期：2026-11-15（Asia/Shanghai）。任务01。业务源码检查点：`6c1ffe57b709cd0a8a7c7d994f4b1863b31a17dc`。本轮没有修改业务实现；验收资料与三环境验收结论一并提交，保留原任务和证据。

## 目标与实际执行

用户要求从test切到dev，业务流程只到预约成功。按照项目单环境运行约定，先停止test前后端，再用正式入口启动dev，Compose保留运行。实际启动日志确认profile为dev，数据库`appoint`、RabbitMQ vhost `/appoint-dev`，前端5173代理后端8080。沿用已有 `.env.dev` 和dev切换预检标记，没有初始化、重置或重新迁移已有业务数据。

使用[真实浏览器脚本](browser.mjs)注册新账号、登录、维护虚构就诊人、从现有目录选择医院/科室/医生，读取服务端真实上海日期后选择明天可预约场次，提交预约、发起MockPay并模拟成功，经正式支付通知与消息链路完成确认。终点为已确认、已付款、待签到，序号001。

页面通过6组检查，36次HTTP均为200/201/202，页面异常0。预约ID为`2121839749319684098`，日期为2026-11-16 08:00–10:00，医生张医生，金额10.00元。我的预约及重新打开详情均保持成功。

[只读审计](audit.py)确认预约和支付归属、库存20=19+0+1、有效占用1、Outbox发布1、Inbox处理1、就诊记录0、退款0。dev死信队列为空，观察到3个dev目录缓存键。原有目录与原有预约状态、支付状态和场次关联保持一致。

## 首轮脚本中断

首轮只注册了一个新账号。自动化URL断言 `/booking$` 会匹配 `/login?redirect=/booking`，在登录请求真正完成前继续跳转，随后被登录保护重定向，等待“新增就诊人”超时。未产生订单。完整[首轮证据](evidence/first-attempt/result.json)及[日志](evidence/first-attempt/browser.log)保留。

修正为显式打开登录页、等待登录HTTP200、断言sessionStorage已保存Token后再继续；使用新账号整条流程复跑成功。最后把URL断言收紧为pathname严格等于`/booking`，并用独立URL例子验证旧匹配误判和新匹配拒绝。未修改应用代码，也没有删除首轮账号。

## 命令

从根目录加载本机Node24后执行：

```bash
python3 scripts/local_env.py stop test
python3 scripts/local_env.py start dev
/home/qsx/.nvm/versions/node/v24.18.1/bin/node .scratch/dev-booking-success/browser.mjs
python3 .scratch/dev-booking-success/audit.py
python3 scripts/check_documentation.py
```

浏览器使用本机缓存Chrome156，由Playwright驱动。没有新增第二套实例，也没有使用H2、临时业务库或可调Clock。

## 证据与最终状态

- [原库基线](evidence/baseline.json)、[结果和成功预约](evidence/result.json)、[六组检查](evidence/checks.json)。
- [页面HTTP记录](evidence/http-summary.json)、[页面异常](evidence/page-errors.json)、[浏览器日志](evidence/browser.log)、[成功截图](evidence/booking-success.png)。
- [SQL与缓存审计](evidence/audit.json)、[RabbitMQ队列](evidence/rabbit-queues.txt)、[源码摘要](evidence/source-manifest.sha256)。
- [文档检查](evidence/documentation-check.log)、[脱敏扫描](evidence/redaction-check.json)。

dev保持5173/8080运行，test及demo无实例。未重置或改写原业务数据；原库从1账号/1就诊人/1预约变为3账号/2就诊人/2预约，新增的两账号分别来自首轮注册和最终成功复跑。目录和预约设置未修改，Clock持续使用上海真实时间。

本轮不覆盖取消退款、签到接诊、异常分支、真实资金渠道、故障注入或全套Maven回归。当前用户体验入口和记录说明见[dev操作说明](../../docs/dev-booking-success.md)。
