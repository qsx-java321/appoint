# 管理员医疗资源管理 MVP

Status: resolved

## 目标

在现有 `catalog` 模块中补齐医院、科室和医生档案的管理员写接口，复用已有排班管理与用户查询链，使管理员能够依次创建医院、科室、医生和排班，用户能够查询并预约。

## 业务规则

- 所有写接口仅允许 `ADMIN`；普通用户返回 `403 / 2002 FORBIDDEN`。
- 创建科室、医生和排班时，父级资源必须存在。
- 同一医院内，去除首尾空格后的科室名称不能重复；不同医院允许同名科室。
- 医生编号由系统自动生成，创建后不可修改。
- 医生档案本期只允许修改姓名、职称和简介，不允许调科室。
- 有科室的医院、有医生的科室、有任意排班的医生不能删除。
- 排班不物理删除，停诊继续使用关闭排班操作。
- 资源修改不回写已有预约凭证；之后创建的新预约使用最新资料。
- 新建资源立即进入现有公共查询，不增加上下架状态或管理员专用查询。

## HTTP 接口

- `POST /api/v1/admin/hospitals`
- `PATCH /api/v1/admin/hospitals/{hospitalId}`
- `DELETE /api/v1/admin/hospitals/{hospitalId}`
- `POST /api/v1/admin/hospitals/{hospitalId}/departments`
- `PATCH /api/v1/admin/departments/{departmentId}`
- `DELETE /api/v1/admin/departments/{departmentId}`
- `POST /api/v1/admin/departments/{departmentId}/doctors`
- `PATCH /api/v1/admin/doctors/{doctorId}`
- `DELETE /api/v1/admin/doctors/{doctorId}`

创建接口返回 HTTP 201，更新和删除返回 HTTP 200；删除成功统一返回 `data: null`。排班创建接口同时调整为 HTTP 201。

## 数据与错误契约

- 新增数据库唯一约束 `(hospital_id, name)`。
- `4401 DEPARTMENT_NAME_EXISTS`：同一医院已存在该科室，HTTP 409。
- `4402 CATALOG_RESOURCE_IN_USE`：资源仍被下级资源引用，HTTP 409。
- 父级或目标资源不存在继续使用 `4001 RESOURCE_NOT_FOUND`。

## 非目标

- 医生账号、医生登录、签到、叫号、接诊、处方、药品和取药。
- 医疗资源上下架、软删除、批量导入和操作审计。
- 医生调科室和排班物理删除。
