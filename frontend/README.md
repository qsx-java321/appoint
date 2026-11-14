# 医院预约前端

独立Vue 3工程，与Spring Boot后端保存在同一仓库。使用TypeScript、Vite、Vue Router、Pinia和Axios；页面接真实REST，业务代码没有假数据回退。技术与业务取舍见[完整实施方案](../docs/frontend-implementation-plan.md)，实际检查见[验收记录](../.scratch/frontend-v1/verification.md)。

## 环境与启动

在运行前端的同一环境安装Node.js 24 LTS（至少24.12，随附npm）。WSL开发使用WSL中的Node/npm，避免混用Windows与Linux依赖目录。

本机已核实通过nvm安装了Node 24.18.1和npm 11.16.0。非交互终端不一定加载.bashrc中的nvm；若命令暂时不可见，先加载已有安装，无需重新安装：

```bash
source "$HOME/.nvm/nvm.sh"
cd frontend
nvm use
node --version
npm --version
```

```bash
cd frontend
npm ci
cp .env.example .env.local
npm run dev
```

访问http://127.0.0.1:5173。开发时/api默认代理到http://127.0.0.1:8080；修改.env.local中的API_PROXY_TARGET可指定另一后端地址。浏览器代码统一使用相对/api/v1，无需放宽后端CORS。

正常本地后端使用根目录README和AGENTS规定的配置与启动命令。前端启动不会自动启动Docker、Spring Boot或创建账号；没有后端时显示连接失败与重试入口。

## 开发模拟支付

默认关闭模拟支付。只有在后端dev/test且明确用于演示时，在.env.local设置VITE_ENABLE_MOCK_PAY=true并重启Vite。先发起支付尝试，再使用返回的模拟入口提交成功/失败通知，最后查询预约正式结果。

不提供真实资金渠道。生产构建应关闭此开关；不能把模拟按钮当作真实支付能力。已有支付处理中尝试不会重复发起，刷新页面可以用最近尝试信息恢复模拟入口。

## 校验

```bash
npm run format:check
npm run typecheck
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

单元测试覆盖身份、客户端、时间与流程状态、创建幂等和异步查询。默认Playwright测试由Vite临时服务器及API拦截夹具运行，覆盖真实页面交互，不等于后端联调。可通过PLAYWRIGHT_CHROMIUM_EXECUTABLE指定已有Chrome，不需要再次下载浏览器。

默认浏览器测试使用5175端口，真实Compose测试使用5174，正常开发页面继续使用5173，三者互不抢占端口。

额外真实API测试仅允许指定隔离测试环境，不能指向生产或包含他人数据的服务：

```bash
REAL_API_TEST=1 REAL_API_TARGET=http://127.0.0.1:18081 npm run test:e2e
```

真实测试创建独立测试账号、就诊人和未支付订单，并取消该订单。调用方负责启动、隔离及清理后端；此项首版交付证据使用H2内存测试入口，应用关闭即清除数据，没有启动Docker或连接既有数据库。这项测试不验证异步支付消息链路。

## 真实Compose完整业务验收

在根目录启动正常Compose依赖及Spring Boot之后，可执行：

```bash
python3 scripts/frontend_compose_acceptance.py
```

此命令在仓库根目录运行，不是在frontend/目录。运行前在该终端加载Node/npm；需要Docker可访问、Maven依赖已缓存及Playwright浏览器可用。已有Chrome时可设置PLAYWRIGHT_CHROMIUM_EXECUTABLE。业务日期默认上海当天，也可通过--business-date指定验收日期。

脚本自动创建独立MySQL数据库/账号、RabbitMQ vhost和Redis namespace，启动18082测试入口和5174真实前端；浏览器不拦截API。管理员、用户和医生通过真实页面执行目录维护、设置、首次绑定、就诊人、预约、支付失败重试、异步确认、取消退款、签到与开始/完成接诊，再以SQL核对库存、支付退款及Outbox/Inbox。

隔离入口使用测试classpath和固定业务时间13:40，因此必须显式提供真实MySQL驱动、消息开关、RabbitMQ correlated发布确认及returns配置，不能沿用H2测试资源的缺省值。固定时间便于当天场次下单后立即签到，不修改系统时钟；本验收不覆盖推进时间后的发布退避重试。正常8080应用仍用真实时钟。

成功后清理本轮资源，正常8080/5173及Compose保持运行；失败保留隔离资源标识和诊断证据。日志及临时测试凭据位于被Git忽略的任务runtime/中，成功后删除测试凭据。实际结果与问题修正见[真实Compose验收](../.scratch/frontend-compose-live/verification.md)。

## 会话与权限

会话保存在当前标签页sessionStorage。关闭标签页或退出后清除；过期与401返回登录。角色以登录响应快照为准，users/me中的新角色不会扩大旧JWT权限。管理员绑定医生后必须重新登录。退出只清除客户端会话，后端没有Token撤销和续签接口。

就诊人原始证件、密码和支付签名不进入持久化草稿。未改证件时编辑请求省略documentNo。预约结果未知时保存同次请求的ID和幂等键供核实，不能改成另一人或另一场的新提交。

## 生产静态文件

npm run build输出dist/。用静态服务器提供该目录，以同一域名反向代理/api到Spring Boot，并为前端路由配置index.html回退。不要将Vite开发服务器用作生产服务，不把后端凭据放进VITE_变量。

示例反向代理规则（需要按实际环境配置上游和TLS）：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080;
}
location / {
    try_files $uri $uri/ /index.html;
}
```

本轮没有部署、提交或推送。前后端分别构建；前端上线/回退不需要修改数据库V10。
