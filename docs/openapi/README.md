# OpenAPI 契约

认证、用户与头像接口的机器可读契约试点。业务规则、通用约定和错误码仍以 [REST API](../api.md) 为准。

## 当前覆盖

| Tag | 接口 |
| --- | --- |
| `Auth` | `POST /auth/register/code`、`POST /auth/register/verify`、`POST /auth/register`、`POST /auth/login` |
| `User` | `GET /users/me`、`PUT /users/me/avatar`、`DELETE /users/me/avatar`、`GET /users/{id}/avatar` |

其余模块暂时保留在 `docs/api.md`，后续按模块迁移。

## 目录

```text
docs/openapi/
├── openapi.yaml              # 入口：servers、tags、security、paths
├── paths/
│   ├── auth.yaml             # 注册与登录
│   └── users.yaml            # 当前用户与头像
├── components/
│   ├── schemas.yaml          # User、Result、请求/响应模型
│   └── responses.yaml        # 通用错误响应
├── preview.html              # Scalar 本地预览页
└── dist/openapi.yaml         # bundle 产物，供 Apifox/Postman 导入
```

`openapi.yaml`、`paths/`、`components/` 是维护源文件；`dist/openapi.yaml` 是生成文件，不要手工修改。

## 校验与生成

在 `docs/openapi` 目录执行：

```bash
npx -y @redocly/cli@latest lint openapi.yaml
npx -y @redocly/cli@latest bundle openapi.yaml -o dist/openapi.yaml
```

`lint` 校验规范、引用和示例；`bundle` 把多文件合并成单文件，方便 Apifox/Postman 导入。

## 本地预览

```bash
cd docs
python3 -m http.server 8090
```

打开 <http://127.0.0.1:8090/openapi/preview.html>。预览页通过 CDN 加载 Scalar，需要网络。
从 `docs` 目录启动是为了让契约里的 `../api.md` 链接也能正确打开。

如果 CDN 不可用，可以使用 Redocly 的本地预览：

```bash
npx -y @redocly/cli@latest preview-docs dist/openapi.yaml
```

## 导入 Apifox / Postman

使用生成后的 `dist/openapi.yaml`：

- Apifox：导入 → OpenAPI → 选择文件。
- Postman：Import → File → 选择 OpenAPI 3.0 文件。

建议环境变量：

| 变量 | 值 |
| --- | --- |
| `baseUrl` | `http://127.0.0.1:8080/api/v1` |
| `accessToken` | 登录接口返回的 `data.accessToken` |
| `environment` | `demo`、`dev` 或 `test`，用于 `X-Appoint-Environment` |

登录后把 `accessToken` 写入环境，并在请求头设置：

```http
Authorization: Bearer {{accessToken}}
X-Appoint-Environment: {{environment}}
```

直接调用后端时 `X-Appoint-Environment` 可以省略；通过本地前端联调时建议带上。

测试账号初始密码取自对应环境的 `.env.<环境>` 中的 `LOCAL_SEED_PASSWORD`，不要提交真实 Token 或密码。

## 编写约定

- 使用 OpenAPI 3.0.3，优先兼容 Apifox、Postman、Swagger UI 和 Redoc。
- 路径、字段名使用英文；`summary`、`description` 使用中文。
- 每个操作必须有唯一 `operationId` 和 `tags`。
- 公开接口显式写 `security: []`。
- 请求/响应示例不要写真实密码、Token 或用户隐私数据，使用 `<PASSWORD>`、`<JWT>` 等占位符。
- 通用错误响应放 `components/responses.yaml`，模型放 `components/schemas.yaml`。
- 业务规则较长时放在 `description`；跨接口规则放 `docs/api.md`，避免重复维护。
