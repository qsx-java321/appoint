# 前端真实Compose业务验收

日期：2026-11-14（Asia/Shanghai）。Git基线87ad984856dedfba64b449651979517ee41a699c，工作区未提交、未推送、未部署。此前设计、Vue首版代码和文档全部保留。源码检查点与实际汇总见[源码摘要](evidence/source-manifest.sha256)、[结果摘要](evidence/summary.json)。

## 结论与正常入口

使用本机已有nvm Node 24.18.1和npm 11.16.0，实际启动Docker Compose、Spring Boot和Vue前端。MySQL、Redis、RabbitMQ均健康；正常Spring Boot使用根目录.env和真实业务时钟，在8080运行，正常前端5173代理到8080并开启本地MockPay演示。

- 正常浏览器入口：http://localhost:5173/login。
- Windows侧通过本机curl再次确认localhost:5173/login返回200，localhost:8080/api/v1/hospitals未认证返回401；[Windows入口检查](evidence/windows-entrypoints.json)。
- 正常服务真实浏览器检查：登录页可渲染；从5173请求/api/v1/hospitals获得8080的401鉴权响应。[检查](evidence/dev-browser-smoke.json)、[截图](evidence/dev-login.png)。
- 启动日志核对：AppointApplication启动、MySQL连接池完成、RabbitMQ连接创建、dev profile生效。[启动检查](evidence/dev-startup.json)。
- 本轮结束保留正常8080/5173和三个Compose服务，测试服务18082/5174及回归5175均退出；原数据卷及原有账号凭据不变。[最终服务检查](evidence/services.json)。

之前将非交互终端中node/npm不可见判断为本机未安装，是环境识别遗漏。本机原有nvm安装可直接使用；通过加载nvm或在当前进程PATH加入其bin目录即可，不需要/tmp中的构建工具。本轮构建、测试、服务均使用已有安装。

## 真实业务范围与隔离

最终通过运行：c1d0197130a1，约19秒的浏览器流程，139次真实HTTP响应。前端、REST、MySQL、RabbitMQ和Redis均真实运行，没有Playwright接口拦截，也没有H2替代。测试端口5174→18082；正常5173→8080持续可用。

独立MySQL数据库/账号front_c1d0197130a1、f_c1d0197130a1，RabbitMQ vhost和Redis namespace均为front-c1d0197130a1；通过后已清理。管理员角色只授予此独立数据库中新建验收账号，不修改正常数据库用户或角色。[资源标识](evidence/c1d0197130a1/resources.json)、[清理记录](evidence/c1d0197130a1/cleanup.json)。

使用已有测试classpath入口VisitAcceptanceApplication，将业务时间固定在2026-11-14 13:40，便于当天14:00场同时满足新下单与签到窗口。不修改系统时间、不修改已有预约日期，不在生产入口增加调时能力。测试入口显式启用真实MySQL、消息发布/消费、Redis和dev MockPay，并配置与正常应用一致的RabbitMQ correlated确认及returns。

支付是开发模拟渠道，通知验签、支付事实、消息投递、预约确认、退款执行及持久化链路均为真实实现；不代表接入真实资金支付。

## 浏览器实际操作

| 流程 | 实际动作与结果 |
| --- | --- |
| 管理目录 | 新建医院、编辑地址、新建科室和医生；医院存在科室时删除返回409，前端显示错误 |
| 统一设置 | 保存每场4人/12.34元，之后再次保存6人/15元；已生成场次仍为4人/12.34元，未生成场次显示新设置 |
| 首次绑定 | 管理端为医生档案绑定新账号；医生重新登录后可访问工作台 |
| 普通用户与资料 | 浏览器注册、登录，新增就诊人、修改联系方式、停用再启用；证件格式由真实后端校验 |
| 预约与失败重试 | 选择后天场次，核对后创建201；首次模拟渠道失败后重新发起支付，支付尝试数为2 |
| 异步确认 | 模拟成功通知经后端正式链路确认预约，取得场次序号001；不是前端点击后直接伪造确认 |
| 取消与退款 | 距开诊超过24小时的预约取消返回202，继续查询到退款已完成；退款金额12.34元及完成时间可见 |
| 当天签到 | 创建当天14:00场，支付确认后无取消按钮；13:40处于签到窗口，点击后进入候诊 |
| 未支付取消 | 对已取消的后天场次再次创建预约，未支付取消，结果为CANCELLED/CLOSED，无重复退款 |
| 医生与双端一致 | 查看只读详情，开始、完成接诊；用户刷新为就诊中/已就诊；双方签到、开始、完成时间逐字段一致 |
| 预约列表和权限 | 全部3条预约可见，ASC排序正确；普通用户进入管理路由显示无权访问，真实管理员API返回403 |

HTTP状态分布：200共122次、201共8次、202共7次、预期的403与409各1次，没有其他失败。实际观察到WAITING_FOR_PAYMENT、PAYMENT_PROCESSING、CONFIRMED、CANCELLED_REFUNDING和CANCELLED_REFUNDED。确认中可能在快速处理后未被查询捕获，不声称本轮观察到该瞬时状态；原夹具回归另覆盖15种详情状态。

证据：[脱敏HTTP摘要](evidence/c1d0197130a1/http-summary.json)、[浏览器原始报告](evidence/c1d0197130a1/browser-results.json)、[创建记录ID](evidence/c1d0197130a1/created-records.json)、[页面错误列表](evidence/c1d0197130a1/page-errors.json)。截图已人工查看：[退款完成](evidence/c1d0197130a1/refund-completed.png)、[医生完成](evidence/c1d0197130a1/doctor-completed.png)。请求摘要不保存Token、密码、签名或原始证件。

## 数据库、消息与缓存核对

[SQL结果](evidence/c1d0197130a1/database-audit.json)独立于页面断言核对：

- 3条新预约分别为CANCELLED/REFUNDED、CONFIRMED/PAID/COMPLETED、CANCELLED/CLOSED。
- 后天已取消场次容量4、可用4、暂占0、已确认0、下一序号2；取消后已确认序号不复用。
- 当天已就诊场次容量4、可用3、暂占0、已确认1、下一序号2；完成接诊不释放库存。
- 新场次库存均满足总量=可用+暂占+已确认；保留的历史种子场次空容量不参与四场库存规则。
- 有效占用只剩已就诊预约的1条；关闭订单不再占用。
- 4条Outbox均PUBLISHED：2条PaymentSucceeded、1条RefundRequested、1条RefundSucceeded；2条支付成功Inbox记录；退款单SUCCEEDED。
- Redis真实目录缓存观察到5个本轮namespace键，完成后定向清除。[缓存检查](evidence/c1d0197130a1/redis-check.json)。

[RabbitMQ队列检查](evidence/c1d0197130a1/rabbit-queues.txt)：支付成功和退款执行队列均有消费者且待处理/未确认数为0，相关DLQ为0。退款结果通知队列有1条消息、没有消费者，符合现有“尚无退款结果监听器”的已知边界；该队列不决定订单退款状态，不伪称已发送通知。

## 首次失败、原因与修正

首次运行b6b45f0e98f5的浏览器流程已通过142次请求，但最终Outbox检查失败，未把该轮标为整体通过。4条事件均PENDING、publish_attempts=1，错误为RabbitMQ publish confirmation failed，next_attempt_at为13:40:01。固定业务时间停在13:40，不能推进退避重试。[失败诊断](evidence/b6b45f0e98f5/outbox-diagnosis.json)、[失败检查点](evidence/b6b45f0e98f5/failure-checkpoint.json)。

根因是隔离入口使用spring-boot:test-run，其src/test/resources/application.yml没有正常应用已有的publisher-confirm-type/returns配置：消息实际到达消费者，发送器却不能得到所需确认。修正验收脚本，显式配置SPRING_RABBITMQ_PUBLISHER_CONFIRM_TYPE=correlated和SPRING_RABBITMQ_PUBLISHER_RETURNS=true，再执行同一完整验收，浏览器与Outbox/Inbox/库存全部通过。

正常业务代码和正常应用配置没有修改；固定时钟仍只供测试入口使用。该用例验证配置完整时的消息闭环，不覆盖依赖业务时间推进的发布退避恢复。首次失败的原脚本和诊断证据保留；诊断完成后只清除其独立资源，另存[后续清理记录](evidence/b6b45f0e98f5/subsequent-cleanup.json)，未改写原失败记录。

## 执行命令和回归

启动：docker compose --env-file compose.env up -d --wait；正常应用./mvnw -o spring-boot:run；frontend/中使用已有npm run dev，进程环境显式开启VITE_ENABLE_MOCK_PAY=true。

真实验收：在根目录、已加载nvm的环境运行python3 scripts/frontend_compose_acceptance.py --business-date 2026-11-14。浏览器通过PLAYWRIGHT_CHROMIUM_EXECUTABLE指定本机缓存Chrome 156；可按前端README改用已安装Playwright浏览器。

| 后续检查 | 证据 |
| --- | --- |
| 22项单元测试 | [报告](evidence/unit-results.json) |
| 10项原有浏览器回归，默认5175独立端口 | [报告](evidence/regression-browser-results.json) |
| 严格类型和生产构建 | [构建记录](evidence/build.log) |
| 前端格式 | [格式检查](evidence/format-check.log) |
| 文档与空白 | [文档检查](evidence/documentation-check.json)；git diff --check |

## 保留状态与限制

正常开发应用、前端及Compose保持运行，方便用户继续体验；所有成功/失败诊断已结束的隔离数据库、账号、vhost及缓存均清理。本轮没有删除数据卷、变更已有密码、覆盖已有业务记录或对外发送消息。

未接真实资金渠道；没有执行生产部署、压力测试、真实设备矩阵或故障注入全部排列。本轮没有修改Java业务代码，不重复累计历史Maven/MySQL/可靠性测试数字。受控时钟用于跨越实际时间限制，普通开发入口仍遵守真实时间；当前正常页面可能已有场次开诊/截止，这与测试固定时刻不同。

任务01–03的原文、日志与检查点保留，交付提交为空；后续获得可追溯Git检查点后再按归档约定处理。
