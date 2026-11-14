# 01 本机Node和项目启动

Type: verification
Triage: ready-for-agent
Status: resolved
Blocked by: 无

## 验收

使用现有nvm Node/npm；Compose健康，正常Spring Boot及Vite可访问，凭据不输出。

## Comments

- 已确认Node 24.18.1、npm 11.16.0和现有容器状态。
- Compose三个服务健康；8080未认证目录返回401，5173首页返回200，已使用本机nvm启动。正常开发服务保留运行。
