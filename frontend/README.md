# 医院预约前端

独立Vue 3工程，使用TypeScript、Vite、Vue Router、Pinia和Axios，调用同仓库Spring Boot REST。业务规则见[业务流程](../docs/business-flow.md)，字段与权限见[API](../docs/api.md)。

## 开发与构建

使用 Node 24（`>=24.12.0 <25`）和 npm。WSL 开发使用同一发行版内的 Node 和 npm。整套前后端按[项目快速启动](../README.md#快速启动)运行。单独启动 Vite 时，先启动对应的后端。以下以已就绪的 demo 后端为例，在 `frontend` 目录执行。

```bash
npm ci
npm run dev:demo
```

| 前端命令 | 对应的后端 profile |
| --- | --- |
| `npm run dev` | `dev` |
| `npm run dev:test` | `test` |
| `npm run dev:demo` | `demo` |

三条命令使用同一对[本地端口](../docs/environments.md#环境对应关系)。Vite 确认后端就绪且 profile 匹配后才提供页面。首次启动无需复制 [`.env.example`](.env.example)。如果旧环境配置或 Shell 仍设置了 `API_PROXY_TARGET`，可移除该设置或改为 `http://127.0.0.1:8080`。

```bash
npm run typecheck
npm test
npm run build
```

测试使用Vitest单元/Mock用例，API由Mock提供，不连接后端。已有结果见[测试报告](../docs/testing/latest-report.md)。

构建输出dist，前后端分别构建。静态服务器需将`/api`反向代理到后端，并为history路由提供index.html回退；后端凭据不进入VITE变量。预约支付页通过后端同步模拟支付接口取得最终结果。

## 代码与状态

- `api/`保存HTTP客户端、接口封装与DTO；`views/`为账号、预约、医生和管理页面。
- `stores/`管理会话、预约选择和同次提交身份，`composables/`管理请求与有限轮询，`lib/`处理展示和时间。
- 会话和预约草稿按环境保存在当前标签页的 sessionStorage；过期或认证401回登录，退出只清理客户端会话，后端没有Token撤销与续签。角色变更后重新登录。
- 预约结果未知时保留原提交内容及幂等键用于核实；证件原文、密码和支付签名不进入持久化草稿。
- ID按字符串传递，金额和操作资格以服务端为准。异步付款后查询正式结果；医生动作后刷新名单和概览，用户手动刷新同一预约查看进度。

## 邮箱注册

`/register`先填写邮箱并发送验证码，在[Mailpit](http://127.0.0.1:8025)查看邮件后输入六位验证码，进入设置密码和确认密码步骤。验证返回的signupTicket只在页面内存保存，完成注册后跳转登录页；刷新或返回修改邮箱需重新验证。验证码和票据均有效五分钟。
