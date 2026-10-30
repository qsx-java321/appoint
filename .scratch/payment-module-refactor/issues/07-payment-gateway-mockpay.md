# 07 PaymentGateway 与 MockPay 契约

Type: feature
Triage: ready-for-agent
Status: resolved

## 背景和目标

建立不泄露具体 SDK 类型的支付渠道 seam，并以 dev/test 的 MockPay Adapter 验证创建、关闭、查询、验签和全额退款契约。

## 范围

- `PaymentGateway`、registry、稳定请求/结果与错误映射。
- MockPay 内存/持久适配实现、测试密钥签名、通知验证、幂等键。
- dev/test profile 注册 Mock Adapter/Controller；生产 profile 不注册。
- Adapter 共享契约测试基础设施。

## 非范围

- 不接入 WeChat Pay/Alipay，不实现业务支付尝试编排。

## 依赖关系

Blocked by: 04、05。

## 需要修改的模块

- `payment.port`、`payment.adapter.mock`、配置与契约测试。

## 业务不变量

- 通用接口不出现渠道 SDK 类型；`paymentNo/attemptNo/refundNo` 是渠道幂等键。
- Mock 不能绕开验签、金额/币种或幂等规则；密钥不写日志。

## 可验证的验收条件

- 创建/重复创建、成功/失败/处理中、关闭、查询、验签、全额退款契约通过。
- 非 dev/test profile 中 Mock 路由和 Adapter 不存在。

## 应运行的测试

- `PaymentGateway` 共享契约测试。
- profile/MVC 安全测试；`./mvnw test`。

## 完成状态

- [x] port/registry
- [x] Mock Adapter/签名
- [x] profile 隔离
- [x] 共享契约测试

## Comments

- 2026-10-29：完成无渠道 SDK 泄漏的 Gateway DTO/port/registry，以及创建、关闭、查询、验签和全额退款契约。
- 2026-10-29：MockPay 使用 HMAC-SHA256 测试签名和支付/退款幂等键，仅在 dev/test profile 注册 Adapter 与 HTTP 202 操作入口。
- 2026-10-29：共享契约与生产隔离 8 项、完整 H2 66 项通过。

