# 06 三前端入口

Type: enhancement
Triage: ready-for-agent
Status: resolved
Blocked by: 04

提供dev/test/demo三个Vite启动入口，端口分别为5173/5174/5175，代理对应8080/8081/8082。冲突端口或代理覆盖在启动时拒绝；浏览器会话随origin隔离。

入口：frontend/package.json、frontend/vite.config.ts。

## Comments

- 交付提交：`19bc004`。
