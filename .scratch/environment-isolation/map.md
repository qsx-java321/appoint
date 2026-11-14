# 三环境任务地图

最新范围见[需求](spec.md)，使用方式见[环境说明](../../docs/environments.md)，交付记录见[总结](../archive/environment-isolation/summary.md)。现有业务按正确处理，本轮只交付环境，不做测试或验收。

| 编号 | 任务 | Blocked by | 状态 | 当前结论 |
| --- | --- | --- | --- | --- |
| 01 | [需求与方案](issues/01-plan.md) | 无 | resolved | 原方案259e368；最新范围覆盖原测试要求 |
| 02 | [早期回归配置](issues/02-regression-config.md) | 01 | resolved | 已提交748eca3，保留历史，不继续处理 |
| 03 | [常驻配置与目标校验](issues/03-runtime-config.md) | 02 | resolved | 已提交0ec8470，保留历史结果 |
| 04 | [初始化与生命周期](issues/04-provisioning-reset.md) | 03 | resolved | 环境实现交付，不补测试证据 |
| 05 | [test业务时间](issues/05-test-clock.md) | 04 | resolved | 环境功能交付，不做业务验证 |
| 06 | [前端入口](issues/06-frontend-http.md) | 04 | resolved | 交付三个mode；HTTP/Playwright移出范围 |
| 07 | [自动化回归](issues/07-container-acceptance.md) | 02、06 | resolved | 用户撤销，wontfix；不继续实现或运行 |
| 08 | [使用文档与提交](issues/08-verification-delivery.md) | 05、06、07 | resolved | 仅完成环境实现与文档交付 |

保留旧issue正文用于追溯，各issue末尾的最新范围说明取代旧测试完成条件。原始文件保留，未删除或归档原文；自动化在途代码没有纳入本次环境提交。
