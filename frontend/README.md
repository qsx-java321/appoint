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
npm run dev       # 5173 -> 8080，dev
# 停止当前环境后，可改选 npm run dev:test 或 npm run dev:demo
```

为节省本机资源，实际操作时选择一个入口，切换前停止上一套环境；不要求程序强制跨环境互斥。使用`127.0.0.1`保持origin一致；`/api`代理固定到对应后端，旧`API_PROXY_TARGET`或命令行端口与mode冲突时失败。后端资源准备、账号与生命周期见[环境总览](../docs/environments.md)。前端启动不会创建数据库或重置demo；每次demo后端启动才恢复基线。

三套本地模式均开启MockPay界面；默认生产构建关闭模拟按钮，真实支付尚未接入。MockPay状态保存在后端内存，重启后不能保证旧支付和退款续接。

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

实现与真实联调已本地提交为e91d870，任务及原始证据见[前端交付档案](../.scratch/archive/frontend/summary.md)，未推送或部署。前后端分别构建；历史首版证据仍对应V10，当前数据库版本以Flyway记录为准。
