> 历史验收材料：正文中的时间、命令、路径和“当前”均属于原检查点；当前入口见[项目状态](../../../../../docs/project-status.md)。导航链接已迁移。

# 文档一致性复核记录

日期：2026-11-12（Asia/Shanghai）。读取基线71e4ec8；仅修改文档、任务元数据及HTTP示例日期占位说明，附带只读核验脚本。实现和测试源码检查点仍为9c5878b。

## 核对方法

- 对照实际Controller和安全配置核对API、权限及已删除路由；对照公开DTO核对字段。
- 对照BookingTimeRules、AppointmentSession、VisitRules、支付通知、消息拓扑、发布/重放实现与默认配置核对时间、状态和恢复边界。
- 对照Git历史和已完成issue，修正总状态、已删除页面和已完成实现的描述；历史记录补角色与适用范围。
- 执行只读静态检查：`python3 .scratch/documentation-alignment/verification/check.py`，结果见[results.json](results.json)。
- 360个生产/测试/构建/验收脚本文件与上一轮实际被测摘要完全一致；本次没有重跑Maven、容器或HTTP业务验收。340/38/16/92是[71e4ec8交付实测](../test-suite-refactor/final-acceptance.md)，不是本次文档检查新增的测试结果。

## 静态结果与边界

检查所有仓库Markdown的本地链接、锚点和围栏，38个实际REST操作的文档覆盖，27个预约详情字段、14个场次字段、5个医生record及1个用户visit record契约，14段JSON示例、42条HTTP请求和18个JSON请求体。日期运行变量不再预置固定年月日；独立说明页删除已由Git历史确认。

静态检查不能证明所有文字语义或替代业务测试；关键规则另经源码人工核对。临时基础服务状态只按验收时刻记录，当前需要时重新读取。原凭据、数据卷、业务库、源码、测试及scripts/__pycache__/均未修改。

## 复核入口

[发现与处置](../../summary.md)记录实际改正，现行事实统一由[项目状态](../../../../../docs/project-status.md)导航。后续代码变化应同时更新对应文档和检查点，不把本记录当永久有效的未来验证。
