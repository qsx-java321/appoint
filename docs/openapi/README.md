# OpenAPI 契约

医院预约系统全部 REST API 的机器可读契约。接口字段、Schema、状态码和示例以本目录为准；跨接口业务规则、通用约定和错误码索引见 [REST API](../api.md)。

## 当前覆盖

| Tag | 接口 |
| --- | --- |
| `Auth` | 注册验证码、验码、注册、登录 |
| `User` | 当前用户、上传/删除/读取头像 |
| `Patient` | 就诊人列表、详情、新增、修改、启停 |
| `Catalog` | 医院、科室、医生、场次查询 |
| `AdminCatalog` | 医院、科室、医生档案管理 |
| `BookingSettings` | 统一预约设置查询与更新 |
| `DoctorAccount` | 医生账号查询与首次绑定 |
| `Appointment` | 预约令牌、创建、详情、列表、支付、取消 |
| `Visit` | 到院签到、医生场次、开始/完成接诊 |
| `Notification` | 消息中心列表与详情 |
| `LocalEnvironment` | 本地环境状态探测 |
| `TestClock` | test 环境业务时间查询、设置和恢复 |

当前 REST API 已全部迁移。`docs/api.md` 继续保留跨接口约定、业务规则和错误码索引。

## 目录

```text
docs/openapi/
├── openapi.yaml               # 入口：servers、tags、security、paths
├── paths/
│   ├── auth.yaml              # 注册与登录
│   ├── users.yaml             # 当前用户与头像
│   ├── patients.yaml          # 就诊人管理
│   ├── catalog.yaml           # 医院、科室、医生、场次查询
│   ├── admin-catalog.yaml     # 管理员目录管理
│   ├── booking-settings.yaml  # 统一预约设置
│   ├── doctor-accounts.yaml   # 医生账号查询与绑定
│   ├── appointments.yaml      # 预约创建、查询、支付、取消
│   ├── visits.yaml            # 签到与医生接诊
│   ├── notifications.yaml     # 消息中心
│   ├── local-environment.yaml # 本地环境识别
│   └── test-clock.yaml        # test 业务调时
├── components/
│   ├── schemas.yaml           # 全部请求/响应模型
│   ├── responses.yaml         # 通用错误响应
│   └── parameters.yaml        # 路径、查询和请求头参数
├── preview.html               # Scalar 本地预览页
└── dist/openapi.yaml          # bundle 产物，供 Apifox/Postman 导入
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
