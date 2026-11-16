# 医院预约前端

独立Vue 3工程，使用TypeScript、Vite、Vue Router、Pinia和Axios，调用同仓库Spring Boot REST。业务规则见[业务流程](../docs/business-flow.md)，字段与权限见[API](../docs/api.md)。

## 开发与构建

使用Node 24（至少24.12）和npm。WSL开发使用WSL内的Node/npm，避免混用依赖目录。在frontend目录执行：

```bash
npm ci
npm run dev
```

`dev`为5173→8080，`dev:test`为5174→8081，`dev:demo`为5175→8082。浏览器使用127.0.0.1；每个mode的端口与`/api`代理固定，冲突覆盖会拒绝启动。整体初始化、账号及环境切换见[环境说明](../docs/environments.md)。前端刷新或重启不重置demo数据。

```bash
npm run typecheck
npm test
npm run build
```

测试使用Vitest单元/Mock用例，API由Mock提供，不连接后端。已有结果见[测试报告](../docs/testing/latest-report.md)。

构建输出dist，前后端分别构建。静态服务器需将`/api`反向代理到后端，并为history路由提供index.html回退；后端凭据不进入VITE变量。三套本地模式显示MockPay操作，生产构建关闭模拟按钮，真实支付尚未接入。

## 代码与状态

- `api/`保存HTTP客户端、接口封装与DTO；`views/`为账号、预约、医生和管理页面。
- `stores/`管理会话、预约选择和同次提交身份，`composables/`管理请求与有限轮询，`lib/`处理展示和时间。
- 会话保存在当前标签页sessionStorage；过期或认证401回登录，退出只清理客户端会话，后端没有Token撤销与续签。角色变更后重新登录。
- 预约结果未知时保留原提交内容及幂等键用于核实；证件原文、密码和支付签名不进入持久化草稿。
- ID按字符串传递，金额和操作资格以服务端为准。异步付款后查询正式结果；医生动作后刷新名单和概览，用户手动刷新同一预约查看进度。
