# demo预约成功验收报告

日期：2026-11-15（Asia/Shanghai）。任务01。业务源码检查点：`6c1ffe57b709cd0a8a7c7d994f4b1863b31a17dc`。没有修改业务实现；验收资料与三环境验收结论一并提交，保留原任务和证据。

## 目标与实际结果

按用户要求停止dev，启动正式demo，业务流程只到预约成功。沿用demo正常启动时的自身重置与预置账号生命周期；没有手工删库、删卷或修改凭据。启动日志确认demo profile、`appoint_demo`、RabbitMQ vhost `/appoint-demo`及端口8082/5175，Flyway恢复至V11。dev/test均已停止。使用上海真实时间，不调时。

[预置检查](seed_check.py)确认3类账号登录200且角色正确；预置医生可查询当天四个空场次；管理员读取默认20人/10.00元设置。demo基线为3用户、0就诊人、0预约、0支付。

[浏览器脚本](browser.mjs)复用前轮已修正的登录时序与严格URL检查，仅替换demo目标、文件位置和测试标记，一次通过6组检查、35次HTTP（200/201/202），页面异常0。完成注册、登录、新增就诊人、查看目录及三天十二场次、选择明天场次、下单、模拟付款、消息异步确认和取号；我的预约及重新打开详情均保持成功。

成功预约`2121841712157466626`、支付单`2121841712186826753`，2026-11-16 08:00–10:00，示例市第一人民医院／内科／张医生，10.00元，序号001；最终CONFIRMED / PAID / NOT_CHECKED_IN。

[只读审计](audit.py)确认库存20=19+0+1、有效占用1、支付Outbox发布1、Inbox处理1、就诊记录0、退款0；demo死信为空，存在3个真实Redis目录缓存键。最终4用户、1就诊人、1预约、1支付，目录未修改。

在demo启动前后，对dev/test的用户角色、就诊人ID/归属/启用状态、订单/支付状态、库存、占用/就诊及预约设置投影进行指纹比较，结果一致；这是对保存投影的核对，不是所有表与全部列的逐字节比较。

## 执行命令

在项目根目录加载本机Node24后执行：

```bash
python3 scripts/local_env.py stop dev
python3 scripts/local_env.py start demo
python3 .scratch/demo-booking-success/seed_check.py
/home/qsx/.nvm/versions/node/v24.18.1/bin/node .scratch/demo-booking-success/browser.mjs
python3 .scratch/demo-booking-success/audit.py
python3 scripts/check_documentation.py
```

隔离比较使用[前置文件](evidence/isolation-before.json)内保存的查询投影，对`appoint`和`appoint_test`分别只读查询并计算SHA256，再与[后置指纹](evidence/isolation-after.json)比较。浏览器使用本机缓存Chrome156，没有另启测试实例或临时业务库。

## 证据与最终状态

- [初始数据与账号检查](evidence/baseline.json)、[六组页面检查](evidence/checks.json)、[成功记录](evidence/result.json)。
- [页面HTTP](evidence/http-summary.json)、[页面异常](evidence/page-errors.json)、[浏览器日志](evidence/browser.log)、[成功截图](evidence/booking-success.png)。
- [SQL及缓存审计](evidence/audit.json)、[RabbitMQ队列](evidence/rabbit-queues.txt)、[源码摘要](evidence/source-manifest.sha256)。
- [文档检查](evidence/documentation-check.log)、[脱敏扫描](evidence/redaction-check.json)。

demo保持5175/8082运行，dev/test无实例，共享Compose运行。随机账号凭据只存于忽略文件`.local-env/demo-booking-actor.json`，预置密码只读取`.env.demo`；成功预约保留至demo下次后端启动重置。公开证据不保存密码、Token、完整证件和支付通知签名。

本轮未发现主流程阻断问题。没有执行退款、签到接诊、异常分支、真实资金渠道、故障注入或Maven全套回归。当前查看方式见[demo操作说明](../../docs/demo-booking-success.md)。
