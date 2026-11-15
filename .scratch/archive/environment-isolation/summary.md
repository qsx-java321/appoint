# dev、test、demo环境改造

目标：三环境共用Compose中的真实MySQL、Redis、RabbitMQ，配置和数据独立；dev保留数据，test支持显式重置与业务调时，demo每次后端启动恢复初始状态。

是否完成：已完成，dev、test、demo三环境业务验收于2026-11-15通过。

test验证注册到完成接诊及关键业务分支；dev、demo按约定验证注册到支付成功、预约确认取号。均使用正式profile及真实Compose依赖，核对支付、库存和消息处理；dev/test数据保留，demo沿用启动恢复初始状态的规则。

验收范围与证据：[test](../test-business-workflows/summary.md)、[dev](../dev-booking-success/summary.md)、[demo](../demo-booking-success/summary.md)。本轮使用MockPay，不包含真实资金渠道、故障注入及Maven全套回归。
