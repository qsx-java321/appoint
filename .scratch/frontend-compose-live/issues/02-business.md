# 02 Compose真实浏览器流程

Type: verification
Triage: ready-for-agent
Status: resolved
Blocked by: 01

## 验收

独立真实MySQL/RabbitMQ/Redis环境；浏览器核心流程、支付退款消息与库存SQL核对；无API拦截。成功后清理独立资源，失败保留诊断标识。

## Comments

- 正常服务已就绪，开始建立隔离MySQL/RabbitMQ/Redis及真实浏览器验收。
- 首次浏览器142次真实请求通过，但Outbox检查因测试classpath缺发布确认配置失败；诊断证据保留。补齐correlated/returns后，c1d0197130a1整轮浏览器、SQL、Redis和队列检查通过，隔离资源已清理。首次失败资源诊断后另行定向清理。
