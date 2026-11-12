# 01 基线与消息契约样本

Type: test
Triage: ready-for-agent
Status: resolved
Blocked by: none

依据：[spec 第 3、4、6、10 节](../spec.md)。执行顺序见 [map](../map.md)。责任场景：S01；建立其他场景的历史覆盖索引。

## 目标与前置条件

在生产改造前保存实际基线、合法 v1 样本、当前测试结果和已批准的校验收紧清单。用户已完成范围确认，不重新询问 Q1～Q4。

开始时检查 HEAD、工作区和项目 AGENTS、术语/ADR，确认 `.scratch` 中没有另一轮同名实施。保留原有缓存和用户工作。

## 文件与动作

1. 新建 `verification/baseline.md`，记录 HEAD、相关源码清单、实际配置前缀和测试入口；不要读取并输出凭据值。
2. 保存四种事件的脱敏 JSON 样本到 `verification/contracts/`，依据当前生产代码/真实生产 ObjectMapper 输出，明确 Long 文本、金额数值、时间和 null 字段。
3. 对 ID 构造数字/文本两组兼容样本；保留 Rabbit metadata 可缺的事实。旧样本固定保存，不随新 record 自动重写。
4. 建立 `verification/scenario-evidence.md`，S01～S32 初始待执行；当前测试映射只能标为旧基线，不能填目标已实现。
5. 在 `src/test/resources` 创建可用于自动化测试的脱敏契约 fixture；方案目录可索引同一内容，避免维护两份分歧样本。
6. 读取并记录三处生产者、两个 Codec/Listener、Outbox 和重放现有断言。明确非法数字转换等现状与预期变化，尚未修改前不要求旧代码通过新收紧断言。

## 应保护的既有测试

- `PaymentNotificationAtomicityTest`、`PaymentNotificationMySqlIT`。
- `PaymentSucceededProcessorIntegrationTest`、`LatePaymentMySqlIT`。
- `PaymentParentLockMySqlIT`。
- `PaymentSucceededEventCodecTest`、`PaymentSucceededListenerTest`。
- `ConfirmedMessagePublisherTest`。
- `OutboxPublisherReliabilityIT`、`PaymentConfirmationReliabilityIT`。
- `ModuleDependencyTest`。

记录特别注意项：生产 Long 文本；退款 IT 最小夹具不等于生产 payload；Qualifier 装饰器；H2 JSON 的测试侧适配；无 Rabbit 属性的真实消费夹具；发布专用不完整正文不应交给业务 Codec。

## 验证

先执行完整常规测试，再以真实依赖确认消息改造相关的旧保障：

```bash
./mvnw test
./mvnw -Pmysql-it -Dit.test=PaymentNotificationMySqlIT,PaymentParentLockMySqlIT,LatePaymentMySqlIT verify
./mvnw -Preliability-it -Dit.test=OutboxPublisherReliabilityIT,PaymentConfirmationReliabilityIT verify
```

命令串行；缓存齐备时可 `-o` 并如实记录。记录实际用例数和过滤后的范围，不把它表述为全部 MySQL 集成已执行。不修改 unrelated 源码修饰基线；遇故障先判断环境/既有问题，未解决时不声明该门禁通过。

## 完成条件

- [x] 基线记录、四类样本、兼容/收紧清单及测试映射完整。
- [x] 真实 ObjectMapper 的 Long/null/时间行为已有证据。
- [x] 上述基线门禁成功，或已解决实际失败后完整重跑；无意外跳过。
- [x] Comments 记录实际命令与结果，地图同步，才能 resolved。

## Comments

- 2026-11-12：任务已定义，尚未认领，未运行本任务测试。

- 2026-11-12：基线验收完成。常规263；聚焦MySQL16；可靠性profile重跑常规264（含新增契约测试）及可靠性15，全部零失败/错误/跳过。日志见 verification/commands.md。
