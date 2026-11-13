# 签到窗口交付记录

- 实现提交：`d75f73c65e01bf2e956686715a2c3400be526150`（feat: 实现预约场次签到窗口并同步测试与文档）。
- 实现基线：`4bfc1d5`；任务01～04全部resolved，已本地提交，未推送。
- 业务验收仍为聚焦106、常规381、MySQL38、可靠性16、隔离curl92；本次只同步提交状态与归档导航，未修改业务源码或重跑业务测试。
- 368文件验收摘要与当前工作区一致；对照实现提交时367份字节一致，mvnw.cmd仅由Git按core.autocrlf=input规范化CRLF为LF。业务及测试Java源码全部字节一致。验收时的JSON、命令、数字及未提交标记保留原事实。
- [验收档案](../archive/visit-workflow/evidence/visit-checkin-window/report.md)保存原内容证据；[原方案](spec.md)与[原任务](map.md)保留在实现提交中，不合并删除，因此不改写原148项去向清单。
- [提交后文档检查](delivery-documentation-check.json)记录当前文档/DTO/API/历史与新验收核验结果；`git diff --check`通过。
- 实际.env/compose.env、日志、target及原有scripts/__pycache__/均未纳入提交。
