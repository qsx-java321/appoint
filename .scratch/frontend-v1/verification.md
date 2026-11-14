# 前端首版实际验收

日期：2026-11-14（Asia/Shanghai）。基线：87ad984856dedfba64b449651979517ee41a699c；本轮全部改动未提交、未推送、未部署。最终前端源码摘要见[源码清单](evidence/source-manifest.sha256)，实际计数见[结果摘要](evidence/summary.json)。

## 交付内容

- 先建立[完整实施方案](../../docs/frontend-implementation-plan.md)，再实现同仓库独立frontend/。14个页面模板覆盖三类角色，业务页面接现有API，不在产品中混入测试夹具。
- 统一HTTP、角色与会话、路由、请求乱序保护、同次预约幂等身份、上海时间、有限轮询。已收款待确认期间遵守canCancel=false，不放宽后端资格。
- 原三张及完整流程草图均保留；原有docs/research/及scripts/__pycache__/未跟踪内容保留。后端源码、REST、数据库迁移及本地凭据不变。
- 运行与部署要求见[前端README](../../frontend/README.md)。不包含真实支付、绑定查询、改约、手工场次、停诊或病历处方等新增能力。

## 执行环境与命令

前次非交互终端的PATH未加载nvm，曾使用缓存Node 24.18.1和/tmp中的npm 12.2.2完成本节记录。后续核对已确认本机原有nvm Node 24.18.1、npm 11.16.0，之前将“命令暂不可见”判断为未安装并不准确。本节保留原执行版本；后续已直接使用本机安装完成[真实Compose验收](../frontend-compose-live/verification.md)。依赖版本固定在package.json与package-lock.json。浏览器采用本地缓存Chrome 156，通过PLAYWRIGHT_CHROMIUM_EXECUTABLE指定。

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 锁文件复现 | frontend/中npm ci --offline | 安装145个依赖包，通过 |
| 格式 | npm run format:check | 通过；[记录](evidence/format-check.log) |
| 类型和生产构建 | npm run build | vue-tsc严格检查及Vite构建通过；[记录](evidence/build.log) |
| 单元 | npm test | 5个文件22项通过，零失败；[原始JSON](evidence/unit-results.json) |
| 浏览器接口拦截 | npm run test:e2e | 10项通过，零失败/跳过；[JSON](evidence/browser-results.json) |
| 真实隔离API | REAL_API_TEST=1 REAL_API_TARGET=http://127.0.0.1:18081 npm run test:e2e | 1项通过，未拦截API；[JSON](evidence/real-browser-results.json) |
| 文档与空白 | python3 scripts/check_documentation.py；git diff --check | 通过，详见[文档检查](evidence/documentation-check.json) |

## 前端检查覆盖

- 会话过期、账号错误、受保护路由、角色不足、旧JWT角色、新旧请求401隔离、安全返回路径。
- 当前目录筛选、十二场、选择与核对、提交前规则确认、新增就诊人回到原选择、资料PATCH省略未改证件、启停。
- 创建未知结果沿原键原体核实；200幂等重放只要求预约ID；明确库存冲突清理提交身份；处理中阻止重复创建。
- 15种详情状态：待支付、支付中、待预约确认、签到前/可签到/截止、候诊/就诊中/已就诊、用户取消和过期、两类退款中/已完成。支付截止和严格24小时取消边界检查通过。
- 有限轮询30次停止，离页后未完成查询不重启，旧查询不覆盖新选择。
- 医生三按钮矩阵、其他人正在接诊时仍可按本行资格接诊、号码顺序、只读详情与返回名单；管理目录引用错误、统一设置核对、首次绑定回执。
- 14个页面模板对应15条测试路径（登录/注册分开），各检查320、390、736、1024px；另实际选场次进入有内容的核对页、完整就诊人表单及医生详情，逐宽度检查，无整体横向溢出或页面脚本错误。手机核对页截图已人工查看，见[截图](evidence/confirmation-mobile.png)。

首次浏览器检查发现表单辅助文字被合并为输入的可访问名称，随后为密码和证件输入补充稳定名称并复验通过；最终报告不包含首次失败。检查不能等同于完整无障碍或真实用户可用性验收。

## 真实API范围与资源清理

使用已有测试入口VisitAcceptanceApplication，spring-boot:test-run以test profile及H2内存库启动，明确禁用外部配置导入、消息和目录缓存；业务时钟固定为2026-11-14 09:40（上海），端口18081。应用实际执行V1–V10迁移；测试通过Vite代理访问真实Spring Boot控制器，没有Playwright接口拦截。

实际浏览器依次完成注册、登录、医院→科室医生→十二场、新增就诊人、核对提交、读取待支付详情、取消未支付预约。浏览器记录了真实HTTP响应，测试断言创建为201且没有业务HTTP错误。账号、就诊人和订单仅在本轮H2内存库中，应用正常停止后数据消失。

联调完成后正常停止本轮Maven/Java进程组，并确认18081关闭；Playwright管理的Vite服务随测试退出。本轮未启动Docker Desktop、MySQL、Redis或RabbitMQ，未变更已有卷或数据库账号，未读取/输出实际.env或compose.env凭据。

## 未验证与维护边界

以下范围对应首版检查点。后续MySQL/RabbitMQ/Redis真实浏览器闭环已执行，结果独立记录于[真实Compose验收](../frontend-compose-live/verification.md)，不改写或累计本节原计数。

- 未重跑Maven常规/集成测试：后端源码和迁移未改，本轮H2联调只覆盖上述用户链路，不冒充此前389/7/38/16等后端验收。
- 支付、退款、医生与管理的完整交互主要使用可重复的接口夹具验证；本轮未使用真实MySQL/RabbitMQ跑前端异步资金闭环，真实支付资金渠道仍未接入。
- 没有生产部署、真实设备/浏览器矩阵、性能压力、完整无障碍、网络恢复所有排列或人工真实用户验收。当前为可继续体验和联调的首版，不宣告生产上线就绪。
- 原型检查285条断言属于设计阶段，和本轮Vue的22项单元、10项浏览器及1项真实API检查分别计数，不累计成同一指标。

## 任务与交付检查点

任务01–06分别对应方案、工程、预约、详情、医生管理和验收。前端新文件和现行文档已落盘，证据保存于当前任务目录；因用户未授权提交，交付提交为空，保留原任务正文和证据，不删除或合并到要求Git追溯检查点的归档清单。
