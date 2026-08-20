# AGENTS.md — 无纸化办案电子档案系统 开发工作守则

> 本文件是「无纸化办案电子档案系统」的长期开发工作守则，所有在本仓库进行的开发、评审与自动化任务必须遵守。本文件依据 `codeplan.md` 提炼；如技术实现与本文件冲突，必须先更新本文件，再修改代码。

## 项目概述

本项目是「无纸化办案电子档案系统」：无纸化办案体系的核心基础设施，定位为轻量化、高安全、全流程的电子档案管理平台，实现文件产生、中转暂存、一键归档、借阅利用的全生命周期闭环管理，满足电子档案"四性"（真实性、完整性、可用性、安全性）要求。

- **架构**：四层架构。接入层（Nginx：静态资源托管、API 反向代理、SSL 终止、限流、Gzip）；应用层（Spring Boot 3.x：Web 层 / Service 层 / Infrastructure 层）；数据层（H2 结构化数据 + MinIO 非结构化文件 + Elasticsearch 全文索引）；集成层（OpenAPI 对外接口，预留 SSO）。
- **部署基调**：单机 Docker Compose 4 服务（`archive-app` / `archive-nginx` / `archive-minio` / `archive-es`），推荐 4C8G 硬件即可流畅运行。
- **演进约束**：H2 可平滑切换 MySQL/PostgreSQL；单体可按模块边界拆分为微服务；MinIO 可扩展为分布式集群。任何实现不得阻塞这三条演进路径。

## 一、技术栈（锁定选型）

| 领域 | 技术 | 版本/模式 | 用途 |
| :--- | :--- | :--- | :--- |
| 前端框架 | Vue 3 + Vite + TypeScript | Latest | 响应式 UI，组件化开发 |
| UI 组件库 | Element Plus | Latest | 企业级组件库，表格/表单/树形控件 |
| HTTP 客户端 | Axios | Latest | 前端 HTTP 请求封装 |
| 后端框架 | Spring Boot | 3.x | 核心框架，自动配置 |
| 运行时 | JDK | 21 | 虚拟线程，高并发 IO 处理 |
| 持久层 | MyBatis-Plus | Latest | 轻量级 ORM，简化 CRUD |
| 数据库 | H2 | File Mode | 元数据、流程状态、审计日志 |
| 缓存 | Caffeine | Latest | 本地内存缓存（替代 Redis） |
| 认证授权 | Sa-Token | Latest | 轻量级权限、RBAC、本地会话 |
| 对象存储 | MinIO | 单机模式 | S3 兼容，海量文件存储 |
| 搜索引擎 | Elasticsearch | 8.x 单节点 | 全文检索、OCR 文本索引 |
| 文档转换 | LibreOffice | 命令行工具 | Word/Excel 转 PDF |
| 音视频处理 | FFmpeg | 命令行工具 | 音视频转码为 HLS 流 |
| 接口文档 | Knife4j | Latest | OpenAPI 3.0 自动生成文档 |
| 反向代理 | Nginx | Latest | 静态资源 + API 反向代理 |
| 部署 | Docker Compose | - | 4 组件编排，一键启动 |

**规则：**

- 必须使用上表锁定的技术栈，禁止擅自引入或替换技术（如用 Redis 替代 Caffeine、用 JPA 替代 MyBatis-Plus）。
- 新增依赖必须说明理由（解决什么问题、现有技术为何不满足）并经评审；必须保持 H2 兼容，禁止引入 H2 私有 SQL 或方言特性。
- 运行时必须为 JDK 21，IO 密集型任务（OCR、ES 写入、文件流转发）必须使用虚拟线程异步执行，禁止阻塞主请求线程。

## 二、模块划分（Maven Multi-Module）

父工程 `archive-system`，包含 7 个模块，职责必须与下表一致：

| 模块 | 职责 |
| :--- | :--- |
| `archive-common` | 通用模块：常量、枚举、异常定义、DTO/VO、工具类 |
| `archive-auth` | 安全模块：Sa-Token 配置、RBAC 权限、水印过滤器、审计切面 |
| `archive-core` | 核心业务：文件管理、两阶段存储、归档流程、案件管理 |
| `archive-search` | 检索模块：ES 客户端、OCR 调度、索引构建与查询 |
| `archive-api` | 开放接口：对外 REST API、Knife4j 文档、扫描矫正接入 |
| `archive-job` | 定时任务：权限回收、临时文件清理、索引健康检查 |
| `archive-starter` | 启动模块：Application 入口、配置聚合、多模块依赖注入 |

**依赖方向（禁止违反）：**

| 模块 | 允许依赖 |
| :--- | :--- |
| `archive-starter` | `archive-core`、`archive-api`、`archive-job` |
| `archive-core` | `archive-common`、`archive-auth`、`archive-search` |
| `archive-api` | `archive-core`、`archive-search` |
| `archive-search` | `archive-common` |
| `archive-auth` | `archive-common` |
| `archive-job` | `archive-core` |
| `archive-common` | 无（禁止依赖任何其他模块） |

**规则：**

- 禁止模块间反向或跨层依赖（如 `archive-common` 依赖 `archive-core`、`archive-api` 依赖 `archive-job`）。
- `archive-common` 禁止包含任何业务逻辑，只允许放常量、枚举、异常、DTO/VO、工具类。
- 新增业务必须落入对应模块：通用能力→common；权限/水印/审计→auth；文件与案件→core；检索/OCR→search；对外接口→api；定时任务→job；启动装配→starter。禁止在 `archive-starter` 中堆积业务代码。
- 跨模块异步任务必须通过 Spring ApplicationEvent 解耦（如上传后发布 `FileUploadedEvent` 触发 OCR/索引），禁止同步调用跨模块方法完成 IO 密集型任务。

## 三、库表设计规范

### 3.1 命名与字段规则

- 表名：必须使用 `sys_` 前缀 + 小写下划线命名（如 `sys_file`、`sys_borrow_apply`），禁止驼峰或复数表名。
- 主键：每张表必须使用 `id BIGINT AUTO_INCREMENT`。
- 时间字段：统一使用 `created_at`、`updated_at`，类型 `TIMESTAMP DEFAULT CURRENT_TIMESTAMP`。
- 状态字段：使用 `VARCHAR(n)` + 大写英文枚举（取值见 3.2），禁止使用中文、数字裸值或魔法字符串。
- 布尔字段：使用 `BOOLEAN`（如 `is_latest`、`is_revoked`、`need_download`、`allow_download`）。
- 关联字段：使用 `BIGINT`，命名取被引用表的语义（`case_id`、`user_id`、`role_id`、`file_id`、`apply_id`）。
- 冗余字段：仅允许高频查询字段冗余（如 `sys_archive.case_no`、`sys_borrow_apply.case_no`），且必须在字段说明中注明"冗余，便于查询"。
- 密码字段：`password VARCHAR(128)`，必须 BCrypt 加密存储，禁止明文或 MD5/SHA 直存。

### 3.2 表清单（15 张，H2 兼容）

每张表必须包含下表列出的关键字段与枚举取值；新增字段不得改变既有枚举含义。

1. **sys_user** 用户表：`username`（唯一）、`password`（BCrypt）、`real_name`、`phone`、`email`、`dept_id`、`status`（0-禁用/1-启用）、`last_login_time`。
2. **sys_role** 角色表：`role_code`（`ADMIN` 管理员 / `SECRETARY` 仲裁秘书 / `ARCHIVIST` 档案管理员 / `CASE_HANDLER` 办案人员）、`role_name`、`description`。
3. **sys_user_role** 用户角色关联表：`user_id`、`role_id` 联合主键。
4. **sys_menu** 菜单权限表：`parent_id`（0 表示根）、`menu_name`、`menu_type`（`M`-目录/`C`-菜单/`B`-按钮）、`perms`、`path`、`component`、`sort_order`、`visible`。
5. **sys_case** 案件表：`case_no`（唯一索引）、`case_name`、`category_id`、`case_type`、`handler_id`、`status`（`ACTIVE`-办理中/`CLOSED`-已结/`ARCHIVED`-已归档）、`remark`。
6. **sys_case_category** 案件分类表（树形）：`parent_id`（0 表示根节点）、`name`、`sort_order`、`level`。
7. **sys_file** 文件表（中转站+归档区统一存储）：`case_id`、`file_name`、`storage_bucket`、`storage_path`、`file_hash`（SHA-256）、`file_size`、`mime_type`、`stage`（`STAGING`-中转站/`ARCHIVED`-归档区）、`version`、`is_latest`、`preview_path`、`created_by`。
8. **sys_file_version** 文件版本表：`file_id`、`version`、`storage_path`、`file_name`、`file_size`、`change_desc`、`created_by`。
9. **sys_archive** 归档记录表：`case_id`、`case_no`（冗余）、`operator_id`、`file_count`、`struct_doc_count`、`status`（`SUCCESS`-成功/`FAILED`-失败）、`remark`、`archived_at`。
10. **sys_borrow_apply** 借阅申请表：`case_id`、`case_no`（冗余）、`applicant_id`、`file_ids`（逗号分隔）、`reason`、`need_download`、`expire_time`、`status`（借阅状态机：`PENDING_SECRETARY` → `PENDING_ADMIN` → `ACTIVE`，驳回 `REJECTED`，到期 `EXPIRED`，归还 `RETURNED`）。
11. **sys_borrow_approval** 借阅审批记录表：`apply_id`、`approver_id`、`approval_step`（`SECRETARY`-仲裁秘书/`ADMIN`-档案管理员）、`result`（`APPROVED`-通过/`REJECTED`-驳回）、`comment`。
12. **sys_borrow_token** 借阅授权 Token 表：`apply_id`、`user_id`、`token_value`（唯一）、`file_ids`、`allow_download`（默认 FALSE）、`expire_time`、`is_revoked`（默认 FALSE）。
13. **sys_audit_log** 操作审计日志表：`user_id`、`username`、`module`（`FILE`/`BORROW`/`ARCHIVE`/`SYSTEM`）、`action`（`UPLOAD`/`DOWNLOAD`/`PREVIEW`/`DELETE`/`ARCHIVE`/`APPROVE`/`APPLY`）、`target_type`、`target_id`、`ip`、`user_agent`、`detail`、`result`（`SUCCESS`/`FAILED`）。
14. **sys_dict** 系统字典表：`dict_type`、`dict_code`、`dict_label`、`dict_value`、`sort_order`、`remark`。
15. **sys_api_key** 开放 API 密钥表：`app_name`、`api_key`（唯一）、`api_secret`、`ip_whitelist`（逗号分隔）、`status`（0-禁用/1-启用）、`expire_time`。

### 3.3 行为约束

- **唯一约束**：`case_no`、`username`、`token_value`、`api_key` 必须建立唯一索引。
- **两阶段存储**：文件必须先进入中转站（`stage=STAGING`，存储于 `transit-bucket/{case_no}/{file_uuid}`），归档后才可进入归档区（`stage=ARCHIVED`，存储于 `archive-bucket/{case_no}/{file_uuid}`）；禁止跳级直接写入归档区。
- **版本管理**：中转站允许多版本覆盖，覆盖时必须写 `sys_file_version` 保留历史版本，并维护 `is_latest` 标识；归档区只读，元数据禁止修改。
- **秒传与删除**：上传必须计算 SHA-256 指纹（`file_hash`）实现秒传判断；删除中转站文件必须为逻辑删除（`is_deleted=TRUE`），物理文件保留 30 天，禁止直接物理删除。
- **一键归档**：必须按 完整性校验 → Hash 比对 → H2 事务内批量更新 `sys_file.stage='ARCHIVED'` 与 `sys_case.status='ARCHIVED'` → 结构化文书归档 → 逻辑删除中转站引用 → 写入 `sys_archive` 的顺序执行，任一环节失败必须整体回滚。
- **借阅状态机**：必须遵循 `PENDING_SECRETARY` → `PENDING_ADMIN` → `ACTIVE` 的顺序流转，只允许跳转到 `REJECTED`/`EXPIRED`/`RETURNED`，禁止绕过双重审批直接置为 `ACTIVE`。
- **权限回收**：`archive-job` 必须每分钟扫描 `expire_time < NOW()` 且 `is_revoked=FALSE` 的借阅 Token，将 `sys_borrow_apply.status` 置为 `EXPIRED`、`sys_borrow_token.is_revoked` 置为 TRUE，并清除 Caffeine 缓存。
- **动态水印**：中转站预览/下载必须注入"办案中 - {用户名} - {时间}"，归档区注入"已归档 - {借阅人} - {时间}"；水印必须由 Java Graphics2D 在内存中合成后流式输出，禁止落盘。
- **审计日志**：敏感操作必须通过 `@AuditLog` 注解记录到 `sys_audit_log`；日志保留 365 天，超期自动归档至冷存储。
- **下载**：必须生成 MinIO Pre-signed URL（有效期 5 分钟）供前端直接下载，禁止后端透传大文件；借阅文件下载必须校验 Token，过期返回 403。

## 四、接口规范

### 4.1 硬性约定

- **路径前缀**：所有接口必须以 `/api/v1/` 为前缀（如 `POST /api/v1/files/upload`）。
- **统一响应体**：必须为 `{ code, msg, data, timestamp }`，成功时 `code=200`、`msg="success"`；禁止返回裸数据或自定义包装结构。
- **错误码**：`200` 成功、`400` 参数错误、`401` 未认证、`403` 无权限（含借阅 Token 过期）、`404` 不存在、`500` 服务器错误。
- **分页参数**：统一使用 `page`（页码，从 1 开始）与 `size`（每页条数），禁止使用 `pageSize`、`current` 等其他命名。
- **异常处理**：必须由全局 `@RestControllerAdvice` 统一捕获并返回标准错误格式，禁止在 Controller 内 try-catch 后自行拼装响应。
- **接口文档**：所有对外接口必须使用 Knife4j 的 `@Tag`、`@Operation` 注解标注，禁止无文档接口上线。
- **外部系统鉴权**：开放 API 必须使用 API Key + HMAC 签名（防重放攻击），支持 IP 白名单，禁止裸 API Key 明文调用。
- **权限标注**：每个接口必须按下表标注角色权限（办案人员/仲裁秘书/档案管理员/借阅人/API Key），禁止默认放行。
- **全文检索**：检索接口必须支持布尔查询、短语匹配、模糊查询；高亮使用 ES highlight 返回 `<em>` 标记；排序为相关度降序 > 上传时间降序；分页使用 `from/size`。

### 4.2 接口清单（既有基准，新增接口必须保持同样风格）

**文件管理：**

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| POST | `/api/v1/files/upload` | 文件上传至中转站 | 办案人员 |
| POST | `/api/v1/files/upload/chunk` | 分片上传（大文件） | 办案人员 |
| GET | `/api/v1/files/{id}/preview` | 文件在线预览 | 办案人员/借阅人 |
| GET | `/api/v1/files/{id}/download` | 文件下载（需 Token） | 借阅人 |
| GET | `/api/v1/files/{id}/versions` | 获取文件版本列表 | 办案人员 |
| GET | `/api/v1/cases/{caseNo}/files` | 获取案件文件树 | 办案人员 |
| DELETE | `/api/v1/files/{id}` | 删除中转站文件 | 办案人员 |

**案件管理：**

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| GET | `/api/v1/cases` | 案件列表（分页） | 办案人员 |
| GET | `/api/v1/cases/{id}` | 案件详情 | 办案人员 |
| POST | `/api/v1/cases` | 新建案件 | 办案人员 |
| PUT | `/api/v1/cases/{id}` | 更新案件 | 办案人员 |
| GET | `/api/v1/cases/categories` | 获取分类树 | 办案人员 |
| POST | `/api/v1/cases/categories` | 新建分类 | 办案人员 |

**归档管理：**

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| POST | `/api/v1/archives/archive` | 一键归档 | 档案管理员 |
| GET | `/api/v1/archives` | 归档记录列表 | 档案管理员 |
| GET | `/api/v1/archives/{id}` | 归档详情 | 档案管理员 |

**借阅管理：**

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| POST | `/api/v1/borrows/apply` | 提交借阅申请 | 所有用户 |
| GET | `/api/v1/borrows/my` | 我的借阅申请列表 | 所有用户 |
| POST | `/api/v1/borrows/{id}/approve` | 审批借阅申请 | 仲裁秘书/档案管理员 |
| GET | `/api/v1/borrows/{id}/token` | 获取借阅授权 Token | 所有用户 |
| POST | `/api/v1/borrows/{id}/download` | 下载借阅文件（需 Token） | 借阅人 |

**全文检索：**

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| GET | `/api/v1/search/files` | 全文检索文件 | 办案人员 |
| GET | `/api/v1/search/files/{id}` | 获取文件详情（含高亮） | 办案人员 |

**开放 API（外部系统）：**

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| POST | `/api/v1/external/scan/upload` | 扫描矫正软件上传文件 | API Key |
| GET | `/api/v1/external/cases` | 获取案件列表 | API Key |
| GET | `/api/v1/external/files` | 获取案件文件列表 | API Key |

**系统集成：**

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| POST | `/api/v1/sso/login` | SSO 回调登录 | SSO 协议 |
| POST | `/api/v1/sync/user` | 用户/组织同步 | 内部接口 |

## 五、开发协作规范

- **提交信息**：必须使用 Conventional Commits，type 限定为 `feat`/`fix`/`docs`/`style`/`refactor`/`test`/`chore`，禁止无 type 或自造 type。
- **后端代码**：必须遵循《阿里巴巴 Java 开发手册》，提交前通过 SonarQube 扫描，禁止携带 Critical/Blocker 级别问题提交。
- **前端代码**：必须通过 ESLint + Prettier 检查，遵循 Vue 官方风格指南，禁止跳过 lint 提交。
- **安全底线**：密码必须 BCrypt；SQL 必须使用 MyBatis-Plus 参数化查询，禁止字符串拼接 SQL；前端输出必须转义且后端配置 `Content-Security-Policy` 响应头防 XSS；必须启用 Sa-Token 的 CSRF 防护。
