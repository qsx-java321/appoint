# 前端实施方案

日期：2026-11-14。状态：实施基线。用户已认可[页面设计](research/frontend-page-scope-and-navigation.md)，授权在同仓库的独立`frontend/`工程中实现。本文先于业务前端代码建立，实际进度与证据见[任务地图](../.scratch/frontend-v1/map.md)。

实施与真实Compose联调已交付，本地代码检查点为`e91d870`；[交付档案](../.scratch/archive/frontend/summary.md)保存阶段、原始证据和限制，未推送。

## 1. 目标与完成条件

实现一个可独立开发、构建的Vue 3应用，覆盖已认可的14个页面模板，接入既有Spring Boot REST。用户预约、模拟支付、签到、医生接诊和管理员维护使用真实接口；测试夹具只存在于测试目录，业务页面不内置虚构账号或回退假数据。

完成条件：类型检查、单元/组件测试、生产构建、浏览器流程检查通过；真实联调与接口拦截测试分别记录，未运行的验收不得写成已通过。Maven与前端构建独立；不改数据库、既有业务时间、权限或资金规则，不自动提交、推送或部署。

## 2. 工程与依赖

采用Vue 3单文件组件、Composition API和`script setup`，TypeScript严格模式，Vite构建，Vue Router路由，Pinia保存会话和临时预约选择，Axios处理HTTP。界面先以原生表单、少量共用组件和统一CSS实现已认可草图，避免同时引入另一套视觉体系。测试采用Vitest和Playwright。

Node.js采用24 LTS且至少24.12，npm管理依赖，提交package-lock.json并用npm ci复现。安装时核对实际可用版本与peerDependencies，锁定版本；Node是构建/开发工具，业务服务仍是Spring Boot。[Vue官方快速开始](https://vuejs.org/guide/quick-start.html)、[Vite官方指南](https://vite.dev/guide/)。

```text
frontend/
  package.json、package-lock.json、tsconfig*.json、vite.config.ts
  index.html、.env.example、README.md
  src/
    main.ts、App.vue、styles.css
    api/              HTTP客户端、REST包装及DTO
    stores/           登录会话、预约选择与同次提交身份
    router/           懒加载页面、登录和角色守卫
    components/       页面反馈、预约凭证、就诊人表单等确有复用的组件
    composables/      请求状态、有限轮询
    lib/              时间、展示状态及安全返回路径
    views/            账号、预约、医生、管理页面
  tests/              规则、会话、客户端和浏览器测试
```

单次使用逻辑留在页面内，不另建通用业务引擎、动态菜单平台或低代码表单。API DTO与提交类型分开，不把整个响应对象直接PATCH回服务端。ID始终是字符串；金额按DTO接受字符串或数值，显示两位小数，不在浏览器计算应付金额。

## 3. 前后端连接与配置

- 开发服务器默认localhost:5173，`/api`代理到`API_PROXY_TARGET`，默认http://127.0.0.1:8080；代理配置只供Node读取，不把后端凭据打包给浏览器。
- 浏览器统一请求同源`/api/v1`。不修改Spring Security来开放匿名目录，不设置通配CORS。
- 正式构建输出frontend/dist，部署说明要求反向代理/api并为前端history路由提供index.html回退；本轮提供说明，不创建真实部署或改后端静态资源映射。
- `VITE_ENABLE_MOCK_PAY=true`只供本地dev/test演示显式开启。默认关闭，即使开启仍须后端提供Mock接口；生产构建也能关闭。没有真实渠道时说明当前无法支付，不伪装扫码或资金服务已上线。
- `.env.local`等本地覆盖文件、node_modules、dist、浏览器报告不提交；根目录现有.env与compose.env不读取到前端、不改写、不输出值。

## 4. 登录、权限与错误

登录成功使用响应user.roles保存本次授权快照；sessionStorage只存accessToken、expiresAt、角色和账号基本资料，刷新后校验users/me；Pinia负责响应状态。users/me可展示数据库当前资料，但不得用新数据库角色擅自扩大旧JWT权限。无refresh-token接口，不添加自动续签。退出或401清理会话、个人数据及预约提交身份。

所有业务路由要求登录；医生和管理员另校验角色。菜单和路由守卫共用角色来源，服务端继续最终鉴权。[Vue Router导航守卫](https://router.vuejs.org/guide/advanced/navigation-guards.html)、[Pinia组件外使用](https://pinia.vuejs.org/core-concepts/outside-component-usage.html)。

返回地址只接受本站允许的相对业务路径，排除协议相对地址、外部URL和登录循环。401保留不含证件、密码的路由上下文；登录错误401/2003留在表单，不触发循环重定向。403显示无权访问，404显示记录不可用，网络失败提供重试；不把错误展示为零记录。旧请求的401不能清除之后新建立的会话。

Axios统一解析Result包装，错误保留HTTP状态和业务code供页面决定后续动作。取消请求不弹业务错误；不记录密码、Token、完整证件号或支付签名；不对POST自动重试。每个表单设置提交中状态，成功重新读取事实，失败保留必要输入。

## 5. 路由与页面拆分

| 区域 | 路由 | 页面与验收 |
| --- | --- | --- |
| 公开账号 | /login、/register | 注册后登录、密码显示、字段反馈；不自选角色 |
| 用户首页 | /booking | 医院名称地址、当前目录名称筛选、加载/空/失败 |
| 科室医生 | /booking/hospitals/:hospitalId | 医院上下文、科室切换与医生简介；切上级清空下级 |
| 日期场次 | /booking/doctors/:doctorId | 服务端三天十二场、状态、费用、下单截止；电脑三列，手机日期切换 |
| 核对预约 | /booking/confirm | 启用就诊人、资料、费用和取消规则；明确提交后才创建 |
| 支付结果 | /appointments/:id/payment | 实际订单金额截止、模拟尝试、处理和确认分开 |
| 预约记录 | /appointments、/appointments/:id | 全状态列表DESC/ASC、快照、退款及签到就诊进度 |
| 个人资料 | /patients、/account | 就诊人增改启停、只读账号信息 |
| 医生 | /doctor | 日期四场、序号升序、始终可见的三个行按钮、只读详情 |
| 管理 | /admin/catalog | 医院/科室/医生逐级增改删，处理引用冲突 |
| 管理设置 | /admin/booking-settings | 容量费用、保存影响核对、已有快照保留 |
| 医生绑定 | /admin/doctor-accounts | 首次绑定、确认医生及账号ID、重新登录提示、本次回执 |
| 系统反馈 | /forbidden、未知路由 | 权限不足或找不到页面，有可返回入口 |

用户共用顶栏，医生页面保留返回用户端，管理页面共用左侧菜单；手机导航改为顶部换行。主内容最大约1200px，用户正文16px，辅助14px；主色蓝、白色表面、8–12px圆角。状态必须配文字，键盘焦点清楚，手机操作目标约44px。管理表格在手机重排为有字段标签的记录。

## 6. 状态与数据边界

### 预约选择与创建

目录选择通过路由参数和当前查询重建，跨页选择仅保留医生ID、医院科室上下文、日期和场次，不存完整就诊人资料。确认页重新查询场次与就诊人；直接访问缺失选择时引导重新选择。新增就诊人后可返回确认页并重选有效记录。

一次明确提交创建UUID Idempotency-Key并保留规范化请求体。网络失败、超时或503/5001等未知结果沿原键原体重试；更改内容前提醒先核实原提交。200重放只取appointmentOrderId并GET详情，不要求首次201字段。400/明确409失败可刷新场次并建立新的选择；成功后清理草稿。不能因双击或重试创建新键。

### 三组时间

后端LocalDateTime统一按Asia/Shanghai解析，不能按浏览器本地时区解释；有serverTime时计算客户端偏差并随新响应更新。下单截止用bookingClosesAt；支付用持久化paymentExpiresAt；签到用visit.checkInOpensAt/checkInClosesAt。前端到点只禁用和查询，不本地制造EXPIRED、退款或爽约状态。

### 预约、支付与就诊

展示优先使用workflowStatus、支付事实和visit，不把CONFIRMING_APPOINTMENT显示为已确认。CANCELLED/CANCELLED_BY_HOSPITAL/EXPIRED分别保留关闭原因，退款进度另列。历史旧场次或null类型使用快照起止时间，不崩溃。

canPay/canCancel/canCheckIn用于展示，提交仍由后端复验。已确认取消严格超过24小时；已收款待确认canCancel=false，首版不在前端绕过标记开放取消，也不修改后端既有写入口兼容规则。未支付取消可能按服务端时间返回EXPIRED，必须用响应更新文案。取消进入202退款受理后不能写退款已完成。

支付尝试只使用响应checkoutUrl中的同源Mock路径，不请求任意外站URL，不手工猜attemptNo。已有处理中尝试不重发；刷新后可从latestPaymentAttempt.attemptNo恢复开发模拟动作。模拟通知受理后仍查询详情。

异步支付、确认、退款期间每2秒查询，最多30次；离页、终态、请求失败或切后台停止，回到前台刷新后可继续新一轮有限查询。轮询不重叠；用户可手动刷新，超出上限提示稍后查看。签到与接诊不轮询、不推送。

### 资料与管理

就诊人编辑不回填证件原文，不改证件时省略documentNo，停用不改变历史凭证。医生详情只读、行按钮按canStartVisit/canCompleteVisit，不新增队首、跨行忙碌或当天限制。日期/场次切换的旧响应不得覆盖新名单；动作成功刷新名单与四场统计。

管理目录不级联删除、不移动医生科室或编辑编号；不能将空医生简介作为后端支持清空。统一设置只影响之后首次生成场次。绑定无查询API：只展示本次回执，不虚构账号搜索、邮箱核验、已绑定列表、解绑换绑。

## 7. 开发顺序与验收

| 阶段 | 内容 | 验证 |
| --- | --- | --- |
| 01 | 完整方案、边界与任务地图 | 对照现有API、源码与认可页面逐项检查 |
| 02 | 工程、HTTP、会话、路由、统一布局与登录注册 | 类型/构建；会话失效、旧401、角色守卫测试 |
| 03 | 医院科室医生、场次、就诊人、核对与创建 | 组件/浏览器；三天十二场、上级切换、证件省略、原键重试 |
| 04 | 预约列表详情、模拟支付、取消退款、签到与有限轮询 | 状态与时间边界测试；支付受理≠确认、退款受理≠完成 |
| 05 | 医生工作台、管理三级目录/设置/绑定 | 行按钮矩阵、号码稳定、旧响应防覆盖、管理操作反馈 |
| 06 | 响应式、构建、完整浏览器检查和维护文档 | 320/390/736/1024px、键盘与错误、文档链接检查；实际联调单列 |

测试优先覆盖会改变业务结果的规则与跨页流程，避免只检查类名或镜像实现的测试。浏览器使用接口拦截验证可重复的边界；若启用真实服务验收，使用隔离账号和资源，不改既有业务数据。真实联调可能需要恢复Docker Desktop与三个依赖，完成后恢复原停止状态。

## 8. 风险、部署和维护

后续已按此方案启动真实Compose及正常开发入口，并在独立MySQL/RabbitMQ/Redis环境完成浏览器核心闭环，结果见[真实Compose验收](../.scratch/frontend-compose-live/verification.md)。默认浏览器回归使用5175，完整Compose验收使用5174，正常开发保留5173。Node/npm直接使用本机nvm安装；非交互终端需先加载nvm。

当前没有真实支付、JWT撤销/续签、绑定关系查询、服务端目录分页/搜索，界面按现有边界设计。用户数据量增大后再讨论分页；生产资金能力另行接入。sessionStorage使会话限定当前标签页，但仍须通过同源部署、避免HTML注入、限制依赖与不持久化敏感表单减少暴露。

部署必须由反向代理同源提供API与页面，不能直接把Vite开发服务器当生产服务。前端配置不包含秘密；构建前确认Mock开关。后端保持Maven原结构与数据库V10，回退前端可移除独立静态发布物而无需回退数据库。
