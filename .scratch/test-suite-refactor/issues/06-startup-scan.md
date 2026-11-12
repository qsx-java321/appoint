# 06 减少测试启动和重复扫描

Type: test/refactor
Status: resolved
Blocked by: 05

## 工作内容

MockPay 契约直接构造新实例，架构生产扫描类内复用，保留 profile 完整上下文与架构负例；枚举不约束无意义顺序。

## 验收

完整常规通过；证明无 Spring 契约启动、只一次生产扫描，不承诺提速比例。

## Comments

- 尚未验收，相关命令与结果写入 verification/commands.md。

- 完整常规340项通过，架构20项、MockPay契约7项保留；契约不启动Spring，生产扫描static final复用，三个profile保护完整。实际耗时及首次编译修正见startup-evidence.md。
