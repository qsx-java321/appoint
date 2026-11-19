# 医院预约前端

Vue 3、TypeScript、Vite、Vue Router、Pinia 和 Axios。整套应用的安装和启动见[项目 README](../README.md#快速启动)。

## 开发与构建

使用 Node.js `>=24.12.0 <25`。单独启动前端时，先启动对应环境的后端，然后在 `frontend` 目录安装依赖并运行对应命令。

```bash
npm ci
npm run dev:demo
```

`npm run dev` 对应 dev，`npm run dev:test` 对应 test，`npm run dev:demo` 对应 demo。Vite 在后端就绪且环境匹配时提供页面，并将 `/api` 代理到 `127.0.0.1:8080`。同一时间运行一套前后端。[环境指南](../docs/environments.md)

```bash
npm run typecheck
npm test
npm run build
```

Vitest 使用 Mock API。构建输出 `dist`。独立部署静态文件时，配置 `/api` 反向代理与页面路由的 `index.html` 回退。

## 代码入口

- `src/router/`、`src/views/` 负责路由和页面。
- `src/api/` 负责请求与类型，接口字段见 [REST API](../docs/api.md)。
- `src/stores/` 管理会话和预约选择。会话与预约草稿按环境保存在当前标签页的 `sessionStorage`，预约提交重试沿用原幂等键。
