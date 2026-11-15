# demo 环境预约成功实测

Type: verification
Triage: ready-for-agent
Status: resolved

## 目标与完成条件

停止dev，启动正式demo，在真实页面完成注册、登录、新增就诊人、目录与场次选择、下单和MockPay支付，终点为CONFIRMED / PAID / NOT_CHECKED_IN并取得就诊序号。

沿用demo启动恢复初始状态的既定规则，dev/test数据保留。使用上海真实时间，不修改业务代码、预约设置或目录，不执行签到接诊。结束保留demo进程和本轮成功预约。

## 步骤与验证

1. 停dev并启动demo，核对端口、profile、数据库和vhost；只读保存dev/test数据指纹，确认demo预置账号。
2. 复用前轮已经修正登录时序的浏览器脚本，仅适配demo目标；断言注册到预约成功并保存脱敏证据。
3. SQL核对支付、库存、占用和消息处理，确认未签到；复核dev/test指纹未变，运行文档检查并整理查看入口。
