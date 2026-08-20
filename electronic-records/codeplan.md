# 无纸化办案电子档案系统 - 技术规划文档 (codeplan.md)

## 一、项目概述

### 1.1 系统定位与建设目标

本系统是无纸化办案体系的核心基础设施，定位为**轻量化、高安全、全流程**的电子档案管理平台。旨在解决办案过程中非结构化文件分散、归档流程繁琐、检索困难及借阅权限失控等痛点。

**建设目标：**
- **全生命周期管理**：实现从文件产生、中转暂存、一键归档到借阅利用的闭环管理。
- **极致轻量化**：基于单机部署，利用 JDK 21 虚拟线程与本地缓存，在低资源消耗下支撑高并发读写。
- **数据资产化**：通过全文检索与结构化分类，将非结构化档案转化为可检索、可分析的数据资产。
- **安全合规**：满足电子档案"四性"要求，提供水印、审计、权限到期自动回收等安全机制。

### 1.2 设计原则

- **轻量化单体模块化**：采用 Modular Monolith 架构，物理隔离但逻辑统一，避免微服务带来的运维复杂度，同时保留模块边界。
- **本地优先**：优先使用 H2、Caffeine、MinIO 单机模式，减少外部中间件依赖，降低故障点。
- **异步非阻塞**：利用 JDK 21 虚拟线程处理 IO 密集型任务（OCR、ES 写入、文件流转发），提升吞吐。
- **安全左移**：水印、权限校验在网关或拦截器层统一处理，业务代码零侵入。
- **平滑演进**：H2 无缝切换 MySQL/PG，单体可拆分为微服务，保护前期投资。

---

## 二、整体架构设计

### 2.1 架构分层描述

系统采用经典四层架构，各层职责清晰：

| 层级 | 职责说明 |
| :--- | :--- |
| **接入层 (Nginx)** | 静态资源托管、API 反向代理、SSL 终止、基础限流、Gzip 压缩 |
| **应用层 (Spring Boot 3.x)** | Web 层（RESTful API、参数校验、全局异常处理）；Service 层（归档流程、借阅状态机、版本控制）；Infrastructure 层（封装 MinIO、ES、H2、Caffeine 操作） |
| **数据层** | 结构化数据（H2 File Mode，存储元数据、流程状态、审计日志）；非结构化数据（MinIO，存储原始文件及转换后的 PDF/图片）；索引数据（Elasticsearch，存储全文内容及高亮片段） |
| **集成层** | OpenAPI 供办案系统调用，预留 SSO 接口供统一认证对接 |

### 2.2 技术栈总览

| 层级 | 技术选型 | 版本/模式 | 核心用途 |
| :--- | :--- | :--- | :--- |
| **前端** | Vue 3 + Vite + TypeScript | Latest | 响应式 UI，组件化开发 |
| **UI 框架** | Element Plus | Latest | 企业级组件库，表格/表单/树形控件 |
| **HTTP 客户端** | Axios | Latest | 前端 HTTP 请求封装 |
| **后端** | Spring Boot | 3.x | 核心框架，自动配置 |
| **运行时** | JDK | 21 | 虚拟线程，高并发 IO 处理 |
| **持久层** | MyBatis-Plus | Latest | 轻量级 ORM，简化 CRUD |
| **数据库** | H2 Database | File Mode | 嵌入式文件持久化，零运维 |
| **缓存** | Caffeine | Latest | 本地内存缓存，替代 Redis |
| **认证授权** | Sa-Token | Latest | 轻量级权限，支持本地 Session |
| **对象存储** | MinIO | 单机模式 | S3 兼容，海量文件存储 |
| **搜索引擎** | Elasticsearch | 单节点 | 全文检索，OCR 文本索引 |
| **文档转换** | LibreOffice | 命令行工具 | Word/Excel 转 PDF |
| **音视频处理** | FFmpeg | 命令行工具 | 音视频转码为 HLS 流 |
| **接口文档** | Knife4j | Latest | OpenAPI 3.0 自动生成文档 |
| **部署** | Docker Compose | - | 4 组件编排，一键启动 |
| **反向代理** | Nginx | Latest | 静态资源 + API 代理 |

### 2.3 模块化设计 (Maven Multi-Module)

```
archive-system (父工程)
├── archive-common      # 通用模块：常量、枚举、异常定义、DTO/VO、工具类
├── archive-auth        # 安全模块：Sa-Token 配置、RBAC 权限、水印过滤器、审计切面
├── archive-core        # 核心业务：文件管理、两阶段存储、归档流程、案件管理
├── archive-search      # 检索模块：ES 客户端、OCR 调度、索引构建与查询
├── archive-api         # 开放接口：对外 REST API、Knife4j 文档、扫描矫正接入
├── archive-job         # 定时任务：权限回收、临时文件清理、索引健康检查
└── archive-starter     # 启动模块：Application 入口、配置聚合、多模块依赖注入
```

**模块依赖关系：**

| 模块 | 依赖 |
| :--- | :--- |
| `archive-starter` | archive-core, archive-api, archive-job |
| `archive-core` | archive-common, archive-auth, archive-search |
| `archive-api` | archive-core, archive-search |
| `archive-search` | archive-common |
| `archive-auth` | archive-common |
| `archive-job` | archive-core |
| `archive-common` | 无 |

### 2.4 部署架构 (Docker Compose)

整个系统仅需 4 个核心服务，通过 `docker-compose.yml` 一键拉起：

| 服务名 | 镜像 | 端口 | 说明 |
| :--- | :--- | :--- | :--- |
| `archive-app` | openjdk:21-slim + jar | 8080 | Spring Boot 单体应用，挂载 H2 数据目录 |
| `archive-nginx` | nginx:latest | 80/443 | 前端静态资源 + 后端 API 反向代理 |
| `archive-minio` | minio/minio | 9000(API)/9001(Console) | 单机版对象存储，创建 transit-bucket 和 archive-bucket |
| `archive-es` | elasticsearch:8.x | 9200/9300 | Elasticsearch 单节点，JVM 内存限制 1G-2G |

**硬件建议**：单机 4C8G 即可流畅运行。

---

## 三、详细功能设计

### 3.1 文件管理模块

#### 3.1.1 文件上传（中转站）
- 支持普通上传与分片上传（大文件场景）
- 上传时自动计算 SHA-256 指纹，实现秒传判断（同 Hash 文件跳过重复存储）
- 文件存储至 MinIO 的 `transit-bucket`，记录 `sys_file` 表，`stage=STAGING`
- 上传后异步触发 OCR 文本提取，写入 Elasticsearch 索引

#### 3.1.2 文件预览
| 文件类型 | 预览方案 |
| :--- | :--- |
| PDF / 图片 | 前端 PDF.js 直接渲染，图片原生标签 |
| Word / Excel / PPT | 后端调用 LibreOffice 转 PDF → 缓存至 MinIO → 前端 PDF.js 预览 |
| 音频 (MP3/WAV) | 前端 Audio.js 播放 |
| 视频 (MP4/AVI) | 后端 FFmpeg 转码为 HLS (m3u8) → 前端 Video.js 流式播放 |
| 不支持预览的格式 | 隐藏预览按钮，仅展示"安全下载"入口 |

#### 3.1.3 文件下载
- 生成 MinIO Pre-signed URL（有效期 5 分钟），前端直接下载，减轻后端带宽压力
- 下载时服务端流式注入动态水印（用户 ID + 时间戳），不落盘

#### 3.1.4 文件分类管理
- 基于 `sys_case_category` 构建树形结构
- 前端使用 Element Plus 的 el-tree 组件展示案件分类树
- 支持拖拽排序、右键菜单（新建子分类、重命名、删除）

### 3.2 两阶段存储模块

#### 3.2.1 中转站（Staging）
- 状态标识：`stage = STAGING`
- 特性：支持多版本覆盖，保留历史版本；仅办案人员可见
- 水印：预览/下载强制注入"办案中 - 用户名 - 时间"水印
- 文件存储路径：`transit-bucket/{case_no}/{file_uuid}`

#### 3.2.2 归档区（Archive）
- 状态标识：`stage = ARCHIVED`
- 特性：只读，不可修改元数据；支持借阅流程
- 水印：预览/下载注入"已归档 - 借阅人 - 时间"水印
- 文件存储路径：`archive-bucket/{case_no}/{file_uuid}`

#### 3.2.3 一键归档流程
1. **完整性校验**：检查案件下所有必填材料是否齐全（通过 `sys_case` 配置的必填项校验）
2. **文件完整性校验**：对中转站文件进行 Hash 比对，确保未损坏
3. **批量状态更新**：开启 H2 事务，批量更新 `sys_file.status` 为 `ARCHIVED`，同时更新 `sys_case.status` 为 `ARCHIVED`
4. **结构化文书迁移**：将办案系统产生的结构化文书（如笔录、起诉书等）一并归档
5. **中转站清理**：逻辑删除中转站临时引用（标记 `is_deleted=TRUE`），物理文件保留 30 天防误删
6. **归档记录**：写入 `sys_archive` 表，记录操作人、归档时间、文件数量

### 3.3 全文检索模块

#### 3.3.1 索引构建
- 文件上传成功 → 通过 Spring ApplicationEvent 异步发布 `FileUploadedEvent`
- 事件消费者：
  - 文档类（PDF/Word/Excel）：调用 Apache Tika 提取文本
  - 图片类：调用 Tesseract OCR 识别文字
  - 音视频类：提取元数据（时长、格式）
- 提取的文本 + 元数据写入 Elasticsearch 索引

#### 3.3.2 ES 索引结构设计
| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `file_id` | long | 文件 ID |
| `case_no` | keyword | 案号（用于过滤） |
| `file_name` | text + keyword | 文件名 |
| `content` | text | 全文内容（分词索引） |
| `category_path` | keyword | 分类路径 |
| `upload_time` | date | 上传时间 |
| `uploader` | keyword | 上传人 |

#### 3.3.3 检索能力
- 支持布尔查询、短语匹配、模糊查询
- 结果高亮：使用 ES 内置 highlight 功能，返回 `<em>keyword</em>` 标记
- 排序：相关度 (score) 降序 > 上传时间降序
- 分页：支持 ES 的 `from/size` 分页

### 3.4 借阅管理模块

#### 3.4.1 借阅申请流程
1. 用户选择归档区文件，填写借阅理由、期望归还时间、是否需要下载权限
2. 系统创建 `sys_borrow_apply` 记录，状态为 `PENDING_SECRETARY`
3. 通知案件仲裁秘书审批

#### 3.4.2 双重审批状态机

```
[待仲裁秘书审批] --(通过)--> [待档案管理员审批] --(通过)--> [借阅中(授权)]
     |                              |                          |
     --(驳回)--> [已驳回]           --(驳回)--> [已驳回]       --(到期)--> [已过期]
                                                              |
                                                              --(归还)--> [已归还]
```

| 状态 | 说明 |
| :--- | :--- |
| `PENDING_SECRETARY` | 待案件仲裁秘书审批 |
| `PENDING_ADMIN` | 待档案管理员审批 |
| `ACTIVE` | 借阅中，已授权 |
| `RETURNED` | 已归还 |
| `REJECTED` | 已驳回 |
| `EXPIRED` | 权限已过期 |

#### 3.4.3 权限到期自动回收
- 审批通过后，生成 `sys_borrow_token` 记录，同时写入 Caffeine 缓存
- Token 包含：`userId`、`fileIds`、`expireTime`、`allowDownload`
- `archive-job` 定时任务每分钟扫描过期 Token，更新借阅状态为 `EXPIRED`，清除缓存
- 前端请求时，Sa-Token 拦截器校验 Token 有效性，过期直接返回 403

#### 3.4.4 查阅室限时授权
- 授权时设置较短的 `expireTime`（如 2 小时）
- 下载权限默认关闭，如需下载需再次申请
- Token 过期后，前端自动跳转提示页

### 3.5 开放 API 模块

#### 3.5.1 RESTful API 设计规范
- 统一响应体：`{ code: 200, msg: "success", data: {...} }`
- 版本管理：URL 路径前缀 `/api/v1/`
- 分页参数：`page` (页码)、`size` (每页条数)
- 错误码规范：200 成功、400 参数错误、401 未认证、403 无权限、404 不存在、500 服务器错误

#### 3.5.2 扫描矫正软件接入接口
- 接口路径：`POST /api/v1/external/scan/upload`
- 鉴权方式：API Key + HMAC 签名（防重放攻击）
- 请求体包含：`case_no`（案号）、`file_name`、`file_data`（Base64 或 Multipart）、`category_path`
- 文件直接存入中转站，触发 OCR 索引

#### 3.5.3 Knife4j 接口文档
- 所有对外接口使用 `@Tag`、`@Operation` 注解标注
- 自动生成 Swagger UI 文档，供对接方查阅
- 支持在线调试

### 3.6 系统集成模块

#### 3.6.1 SSO 预留接口
- 定义 `IdentityProvider` 接口，当前实现 `LocalIdentityProvider`（本地认证）
- 预留 `OAuth2Provider`、`CasProvider` 实现，后期只需引入对应插件并修改配置
- 支持协议：OAuth2.0、CAS、OIDC

#### 3.6.2 用户同步接口
- 接口路径：`POST /api/v1/sync/user`
- 支持外部系统推送用户/组织变更
- 本地 Caffeine 缓存用户权限树，变更时自动刷新

### 3.7 安全管理模块

#### 3.7.1 动态水印
- 使用 Java `Graphics2D` 在内存中合成水印图片，不落盘
- 水印内容：用户名 + 操作时间 + "内部文件-禁止外传"
- 预览时流式输出带水印的图片/PDF，下载时同理

#### 3.7.2 操作审计日志
- AOP 切面拦截 `@AuditLog` 注解标注的方法
- 异步写入 `sys_audit_log` 表（JDK 21 虚拟线程异步）
- 记录内容：操作人、模块、动作、目标 ID、IP 地址、User-Agent、操作参数、操作结果、操作时间

#### 3.7.3 四性保障措施
| 性质 | 保障措施 |
| :--- | :--- |
| **真实性** | SHA-256 文件指纹 + 审计日志全链路记录 |
| **完整性** | H2 事务保证 + MinIO 版本控制 + 归档时 Hash 校验 |
| **可用性** | Docker 重启策略 (restart: unless-stopped) + Nginx 负载均衡预留 |
| **安全性** | Sa-Token 权限隔离 + 动态水印 + 借阅审批 + 操作审计 |

---

## 四、库表设计 (H2 Compatible)

### 4.1 sys_user - 用户表

| 字段名 | 类型 | 必填 | 主键 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGINT | Y | Y | AUTO_INCREMENT | 用户ID |
| username | VARCHAR(64) | Y | N | - | 登录账号（唯一） |
| password | VARCHAR(128) | Y | N | - | BCrypt加密密码 |
| real_name | VARCHAR(64) | Y | N | - | 真实姓名 |
| phone | VARCHAR(20) | N | N | NULL | 手机号 |
| email | VARCHAR(100) | N | N | NULL | 邮箱 |
| dept_id | BIGINT | N | N | NULL | 部门ID |
| status | TINYINT | Y | N | 1 | 状态: 0-禁用 1-启用 |
| last_login_time | TIMESTAMP | N | N | NULL | 最后登录时间 |
| created_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 更新时间 |

### 4.2 sys_role - 角色表

| 字段名 | 类型 | 必填 | 主键 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGINT | Y | Y | AUTO_INCREMENT | 角色ID |
| role_code | VARCHAR(64) | Y | N | - | 角色编码: ADMIN, SECRETARY, ARCHIVIST, CASE_HANDLER |
| role_name | VARCHAR(64) | Y | N | - | 角色名称 |
| description | VARCHAR(255) | N | N | NULL | 描述 |
| created_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 创建时间 |

### 4.3 sys_user_role - 用户角色关联表

| 字段名 | 类型 | 必填 | 主键 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| user_id | BIGINT | Y | Y | - | 用户ID |
| role_id | BIGINT | Y | Y | - | 角色ID |

### 4.4 sys_menu - 菜单权限表

| 字段名 | 类型 | 必填 | 主键 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGINT | Y | Y | AUTO_INCREMENT | 菜单ID |
| parent_id | BIGINT | Y | N | 0 | 父级ID |
| menu_name | VARCHAR(64) | Y | N | - | 菜单名称 |
| menu_type | VARCHAR(10) | Y | N | - | 类型: M-目录 C-菜单 B-按钮 |
| perms | VARCHAR(128) | N | N | NULL | 权限标识 |
| path | VARCHAR(255) | N | N | NULL | 路由路径 |
| component | VARCHAR(255) | N | N | NULL | 组件路径 |
| sort_order | INT | Y | N | 0 | 排序值 |
| visible | TINYINT | Y | N | 1 | 是否可见 |
| created_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 创建时间 |

### 4.5 sys_case - 案件表

| 字段名 | 类型 | 必填 | 主键 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGINT | Y | Y | AUTO_INCREMENT | 案件ID |
| case_no | VARCHAR(64) | Y | N | - | 案号（唯一索引） |
| case_name | VARCHAR(255) | Y | N | - | 案件名称 |
| category_id | BIGINT | Y | N | - | 分类ID |
| case_type | VARCHAR(32) | N | N | NULL | 案件类型 |
| handler_id | BIGINT | N | N | NULL | 承办人ID |
| status | VARCHAR(20) | Y | N | 'ACTIVE' | 状态: ACTIVE-办理中 CLOSED-已结 ARCHIVED-已归档 |
| remark | VARCHAR(500) | N | N | NULL | 备注 |
| created_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 立案时间 |
| updated_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 更新时间 |

### 4.6 sys_case_category - 案件分类表（树形）

| 字段名 | 类型 | 必填 | 主键 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGINT | Y | Y | AUTO_INCREMENT | 分类ID |
| parent_id | BIGINT | Y | N | 0 | 父级ID，0表示根节点 |
| name | VARCHAR(100) | Y | N | - | 分类名称 |
| sort_order | INT | Y | N | 0 | 排序值 |
| level | INT | Y | N | 1 | 层级深度 |
| created_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 创建时间 |

### 4.7 sys_file - 文件表（中转站+归档区统一存储）

| 字段名 | 类型 | 必填 | 主键 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGINT | Y | Y | AUTO_INCREMENT | 文件ID |
| case_id | BIGINT | Y | N | - | 案件ID |
| file_name | VARCHAR(255) | Y | N | - | 原始文件名 |
| storage_bucket | VARCHAR(50) | Y | N | - | MinIO Bucket名 |
| storage_path | VARCHAR(512) | Y | N | - | MinIO Object Key |
| file_hash | VARCHAR(64) | Y | N | - | SHA-256 指纹 |
| file_size | BIGINT | Y | N | 0 | 文件大小(bytes) |
| mime_type | VARCHAR(100) | Y | N | - | MIME类型 |
| stage | VARCHAR(20) | Y | N | 'STAGING' | 阶段: STAGING-中转站 ARCHIVED-归档区 |
| version | INT | Y | N | 1 | 版本号 |
| is_latest | BOOLEAN | Y | N | TRUE | 是否最新版 |
| preview_path | VARCHAR(512) | N | N | NULL | 预览文件路径(转码后) |
| created_by | BIGINT | Y | N | - | 上传人ID |
| created_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 上传时间 |
| updated_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 更新时间 |

### 4.8 sys_file_version - 文件版本表

| 字段名 | 类型 | 必填 | 主键 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGINT | Y | Y | AUTO_INCREMENT | 版本记录ID |
| file_id | BIGINT | Y | N | - | 关联文件ID |
| version | INT | Y | N | - | 版本号 |
| storage_path | VARCHAR(512) | Y | N | - | 历史版本存储路径 |
| file_name | VARCHAR(255) | Y | N | - | 当时文件名 |
| file_size | BIGINT | Y | N | 0 | 文件大小 |
| change_desc | VARCHAR(255) | N | N | NULL | 变更说明 |
| created_by | BIGINT | Y | N | - | 操作人ID |
| created_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 创建时间 |

### 4.9 sys_archive - 归档记录表

| 字段名 | 类型 | 必填 | 主键 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGINT | Y | Y | AUTO_INCREMENT | 归档ID |
| case_id | BIGINT | Y | N | - | 案件ID |
| case_no | VARCHAR(64) | Y | N | - | 案号（冗余，便于查询） |
| operator_id | BIGINT | Y | N | - | 操作人ID（档案管理员） |
| file_count | INT | Y | N | 0 | 归档文件数 |
| struct_doc_count | INT | Y | N | 0 | 结构化文书数 |
| status | VARCHAR(20) | Y | N | 'SUCCESS' | 状态: SUCCESS-成功 FAILED-失败 |
| remark | VARCHAR(500) | N | N | NULL | 备注 |
| archived_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 归档时间 |

### 4.10 sys_borrow_apply - 借阅申请表

| 字段名 | 类型 | 必填 | 主键 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGINT | Y | Y | AUTO_INCREMENT | 申请ID |
| case_id | BIGINT | Y | N | - | 案件ID |
| case_no | VARCHAR(64) | Y | N | - | 案号（冗余） |
| applicant_id | BIGINT | Y | N | - | 申请人ID |
| file_ids | VARCHAR(1000) | Y | N | - | 借阅文件ID列表(逗号分隔) |
| reason | VARCHAR(500) | Y | N | - | 借阅理由 |
| need_download | BOOLEAN | Y | N | FALSE | 是否需要下载权限 |
| expire_time | TIMESTAMP | Y | N | - | 期望到期时间 |
| status | VARCHAR(30) | Y | N | 'PENDING_SECRETARY' | 流程状态(见3.4.2) |
| created_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 申请时间 |
| updated_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 更新时间 |

### 4.11 sys_borrow_approval - 借阅审批记录表

| 字段名 | 类型 | 必填 | 主键 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGINT | Y | Y | AUTO_INCREMENT | 审批ID |
| apply_id | BIGINT | Y | N | - | 申请ID |
| approver_id | BIGINT | Y | N | - | 审批人ID |
| approval_step | VARCHAR(30) | Y | N | - | 审批环节: SECRETARY-仲裁秘书 ADMIN-档案管理员 |
| result | VARCHAR(20) | Y | N | - | 结果: APPROVED-通过 REJECTED-驳回 |
| comment | VARCHAR(500) | N | N | NULL | 审批意见 |
| created_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 审批时间 |

### 4.12 sys_borrow_token - 借阅授权Token表

| 字段名 | 类型 | 必填 | 主键 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGINT | Y | Y | AUTO_INCREMENT | Token ID |
| apply_id | BIGINT | Y | N | - | 关联申请ID |
| user_id | BIGINT | Y | N | - | 被授权人ID |
| token_value | VARCHAR(64) | Y | N | - | 唯一Token值 |
| file_ids | VARCHAR(1000) | Y | N | - | 授权文件ID列表 |
| allow_download | BOOLEAN | Y | N | FALSE | 是否允许下载 |
| expire_time | TIMESTAMP | Y | N | - | 过期时间 |
| is_revoked | BOOLEAN | Y | N | FALSE | 是否已撤销 |
| created_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 创建时间 |

### 4.13 sys_audit_log - 操作审计日志表

| 字段名 | 类型 | 必填 | 主键 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGINT | Y | Y | AUTO_INCREMENT | 日志ID |
| user_id | BIGINT | N | N | NULL | 操作人ID |
| username | VARCHAR(64) | N | N | NULL | 操作人账号 |
| module | VARCHAR(50) | Y | N | - | 模块: FILE-文件 BORROW-借阅 ARCHIVE-归档 SYSTEM-系统 |
| action | VARCHAR(50) | Y | N | - | 动作: UPLOAD-DOWNLOAD-PREVIEW-DELETE-ARCHIVE-APPROVE-APPLY |
| target_type | VARCHAR(50) | N | N | NULL | 目标对象类型 |
| target_id | VARCHAR(64) | N | N | NULL | 目标对象ID |
| ip | VARCHAR(50) | Y | N | - | IP地址 |
| user_agent | VARCHAR(500) | N | N | NULL | 浏览器UA |
| detail | TEXT | N | N | NULL | 详细参数/结果 |
| result | VARCHAR(20) | N | N | NULL | 结果: SUCCESS/FAILED |
| created_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 操作时间 |

### 4.14 sys_dict - 系统字典表

| 字段名 | 类型 | 必填 | 主键 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGINT | Y | Y | AUTO_INCREMENT | 字典ID |
| dict_type | VARCHAR(50) | Y | N | - | 字典类型 |
| dict_code | VARCHAR(50) | Y | N | - | 字典编码 |
| dict_label | VARCHAR(100) | Y | N | - | 显示标签 |
| dict_value | VARCHAR(100) | N | N | NULL | 字典值 |
| sort_order | INT | Y | N | 0 | 排序 |
| remark | VARCHAR(255) | N | N | NULL | 备注 |

### 4.15 sys_api_key - 开放API密钥表

| 字段名 | 类型 | 必填 | 主键 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGINT | Y | Y | AUTO_INCREMENT | 密钥ID |
| app_name | VARCHAR(100) | Y | N | - | 应用名称 |
| api_key | VARCHAR(64) | Y | N | - | Access Key（唯一） |
| api_secret | VARCHAR(128) | Y | N | - | Secret Key |
| ip_whitelist | VARCHAR(500) | N | N | NULL | IP白名单(逗号分隔) |
| status | TINYINT | Y | N | 1 | 状态: 0-禁用 1-启用 |
| expire_time | TIMESTAMP | N | N | NULL | 过期时间 |
| created_at | TIMESTAMP | Y | N | CURRENT_TIMESTAMP | 创建时间 |

---

## 五、核心接口设计

### 5.1 文件管理接口

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| POST | `/api/v1/files/upload` | 文件上传至中转站 | 办案人员 |
| POST | `/api/v1/files/upload/chunk` | 分片上传（大文件） | 办案人员 |
| GET | `/api/v1/files/{id}/preview` | 文件在线预览 | 办案人员/借阅人 |
| GET | `/api/v1/files/{id}/download` | 文件下载（需Token） | 借阅人 |
| GET | `/api/v1/files/{id}/versions` | 获取文件版本列表 | 办案人员 |
| GET | `/api/v1/cases/{caseNo}/files` | 获取案件文件树 | 办案人员 |
| DELETE | `/api/v1/files/{id}` | 删除中转站文件 | 办案人员 |

### 5.2 案件管理接口

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| GET | `/api/v1/cases` | 案件列表（分页） | 办案人员 |
| GET | `/api/v1/cases/{id}` | 案件详情 | 办案人员 |
| POST | `/api/v1/cases` | 新建案件 | 办案人员 |
| PUT | `/api/v1/cases/{id}` | 更新案件 | 办案人员 |
| GET | `/api/v1/cases/categories` | 获取分类树 | 办案人员 |
| POST | `/api/v1/cases/categories` | 新建分类 | 办案人员 |

### 5.3 归档管理接口

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| POST | `/api/v1/archives/archive` | 一键归档 | 档案管理员 |
| GET | `/api/v1/archives` | 归档记录列表 | 档案管理员 |
| GET | `/api/v1/archives/{id}` | 归档详情 | 档案管理员 |

### 5.4 借阅管理接口

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| POST | `/api/v1/borrows/apply` | 提交借阅申请 | 所有用户 |
| GET | `/api/v1/borrows/my` | 我的借阅申请列表 | 所有用户 |
| POST | `/api/v1/borrows/{id}/approve` | 审批借阅申请 | 仲裁秘书/档案管理员 |
| GET | `/api/v1/borrows/{id}/token` | 获取借阅授权Token | 所有用户 |
| POST | `/api/v1/borrows/{id}/download` | 下载借阅文件（需Token） | 借阅人 |

### 5.5 全文检索接口

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| GET | `/api/v1/search/files` | 全文检索文件 | 办案人员 |
| GET | `/api/v1/search/files/{id}` | 获取文件详情（含高亮） | 办案人员 |

### 5.6 开放 API 接口

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| POST | `/api/v1/external/scan/upload` | 扫描矫正软件上传文件 | API Key |
| GET | `/api/v1/external/cases` | 获取案件列表 | API Key |
| GET | `/api/v1/external/files` | 获取案件文件列表 | API Key |

### 5.7 系统集成接口

| 方法 | 路径 | 说明 | 权限 |
| :--- | :--- | :--- | :--- |
| POST | `/api/v1/sso/login` | SSO 回调登录 | SSO 协议 |
| POST | `/api/v1/sync/user` | 用户/组织同步 | 内部接口 |

---

## 六、关键流程设计

### 6.1 文件上传与 OCR 索引流程

```
[前端] --(1) 上传文件--> [Spring Boot]
                              |
                              v
                         (2) 计算 SHA-256 指纹
                              |
                              v
                         (3) 写入 MinIO (transit-bucket)
                              |
                              v
                         (4) 插入 sys_file 记录 (stage=STAGING)
                              |
                              v
                         (5) 发布 FileUploadedEvent (异步)
                              |
                    +----------+----------+
                    |                     |
                    v                     v
              (6a) 文档类:           (6b) 图片类:
              Apache Tika           Tesseract OCR
              提取文本                识别文字
                    |                     |
                    +----------+----------+
                               |
                               v
                          (7) 写入 Elasticsearch 索引
                               |
                               v
                          (8) 返回上传成功响应
```

### 6.2 一键归档流程

```
[档案管理员] --(1) 点击"一键归档"--> [Spring Boot]
                                         |
                                         v
                                    (2) 校验案件必填材料
                                         |  完整性
                                    (3) Hash 比对文件
                                         |
                    +-------------------+-------------------+
                    | (通过)                                  | (失败)
                    v                                        v
               (4) 开启 H2 事务                          返回失败提示
                    |
                    v
               (5) 批量更新 sys_file.stage = 'ARCHIVED'
                    |
                    v
               (6) 更新 sys_case.status = 'ARCHIVED'
                    |
                    v
               (7) 将结构化文书一并归档
                    |
                    v
               (8) 逻辑删除中转站临时引用
                    |
                    v
               (9) 写入 sys_archive 归档记录
                    |
                    v
               (10) 提交事务
                    |
                    v
               [11] 返回归档成功
```

### 6.3 借阅审批流程

```
[申请人] --(1) 提交借阅申请--> [Spring Boot]
                                   |
                                   v
                              (2) 创建 sys_borrow_apply
                                   |  status=PENDING_SECRETARY
                                   v
                              (3) 通知案件仲裁秘书
                                   |
                                   v
                    [仲裁秘书] --(4) 审批(通过/驳回)
                                   |
                    +----------------+----------------+
                    | (通过)                          | (驳回)
                    v                                 v
               (5a) 创建审批记录                   状态=REJECTED
                    |                               | 通知申请人
                    v
               (5b) 更新 status=PENDING_ADMIN
                    |
                    v
               (5c) 通知档案管理员
                    |
                    v
        [档案管理员] --(6) 审批(通过/驳回)
                    |
        +-----------+-----------+
        | (通过)                 | (驳回)
        v                        v
   (7a) 生成 sys_borrow_      状态=REJECTED
        token + Caffeine缓存       | 通知申请人
        |
        v
   (7b) 更新 status=ACTIVE
        |
        v
   (7c) 通知申请人借阅成功
        |
        v
   [申请人] --(8) 查阅/下载文件(需校验Token)
```

### 6.4 权限到期回收流程

```
[定时任务 archive-job] --(每分钟触发)
        |
        v
(1) 查询 sys_borrow_token 中 expire_time < NOW() 且 is_revoked=FALSE 的记录
        |
        v
(2) 更新对应 sys_borrow_apply.status = 'EXPIRED'
        |
        v
(3) 更新 sys_borrow_token.is_revoked = TRUE
        |
        v
(4) 清除 Caffeine 缓存中对应的 Token
        |
        v
(5) 写入审计日志
        |
        v
(6) 通知申请人权限已过期
```

---

## 七、安全设计

### 7.1 认证与授权方案

- **认证框架**：Sa-Token，轻量级，内置防重发、会话过期自动踢人
- **授权模型**：RBAC（基于角色的访问控制）
  - 用户 → 角色 → 菜单/按钮权限
  - 角色预置：ADMIN（管理员）、SECRETARY（仲裁秘书）、ARCHIVIST（档案管理员）、CASE_HANDLER（办案人员）
- **会话管理**：JWT Token + Caffeine 本地缓存，无状态会话
- **Token 有效期**：默认 2 小时，支持"记住我"延长至 7 天
- **并发控制**：同一账号不允许多点同时登录（可配置）

### 7.2 水印安全方案

| 场景 | 水印内容 | 实现方式 |
| :--- | :--- | :--- |
| 中转站预览 | "办案中 - {用户名} - {时间}" | Graphics2D 内存合成，流式输出 |
| 归档区预览 | "已归档 - {借阅人} - {时间}" | 同上 |
| 下载文件 | 覆盖全文的水印文本 | 对 PDF 逐页合成水印后流式输出 |
| 敏感操作下载 | 额外添加"禁止外传"标识 | 水印内容动态拼接 |

### 7.3 审计日志方案

- **采集方式**：AOP 切面 + `@AuditLog` 注解，业务方法无侵入
- **记录内容**：操作人、模块、动作、目标对象、IP、UA、参数、结果、时间
- **存储方式**：异步写入 H2 `sys_audit_log` 表（JDK 21 虚拟线程）
- **查询能力**：支持按时间范围、操作人、模块、动作筛选
- **保留策略**：日志保留 365 天，超期自动归档至冷存储

### 7.4 四性保障措施

| 性质 | 技术实现 |
| :--- | :--- |
| **真实性** | 文件上传时计算 SHA-256 指纹并固化；归档时再次 Hash 校验；全链路审计日志记录 |
| **完整性** | H2 事务保证原子性；MinIO 版本控制保留历史版本；归档时批量 Hash 比对 |
| **可用性** | Docker restart: unless-stopped 策略；Nginx 健康检查；预留负载均衡接口 |
| **安全性** | Sa-Token 权限隔离；动态水印防截图泄露；借阅审批控制访问；操作审计可追溯 |

---

## 八、部署方案

### 8.1 Docker Compose 配置说明

**docker-compose.yml 服务定义：**

| 服务 | 镜像 | 端口映射 | 数据卷 | 环境变量 |
| :--- | :--- | :--- | :--- | :--- |
| `archive-app` | openjdk:21-slim + jar | 8080:8080 | ./data/h2:/data/h2, ./config:/config | JAVA_OPTS, MINIO_URL, ES_URL |
| `archive-nginx` | nginx:latest | 80:80, 443:443 | ./nginx/conf.d:/etc/nginx/conf.d | - |
| `archive-minio` | minio/minio | 9000:9000, 9001:9001 | ./data/minio:/data | MINIO_ROOT_USER, MINIO_ROOT_PASSWORD |
| `archive-es` | elasticsearch:8.x | 9200:9200, 9300:9300 | ./data/es:/usr/share/elasticsearch/data | discovery.type=single-node, ES_JAVA_OPTS=-Xms1g -Xmx1g |

**启动命令**：`docker-compose up -d`

### 8.2 Nginx 反向代理配置要点

```
server {
    listen 80;
    server_name archive.example.com;

    # 前端静态资源
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 代理
    location /api/ {
        proxy_pass http://archive-app:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        # 大文件上传支持
        client_max_body_size 500M;
        proxy_read_timeout 300s;
    }

    # MinIO Console (可选，仅内网)
    location /minio/ {
        proxy_pass http://archive-minio:9000/;
    }

    # Elasticsearch (可选，仅内网)
    location /es/ {
        proxy_pass http://archive-es:9200/;
    }
}
```

### 8.3 备份策略

| 备份对象 | 备份方式 | 频率 | 保留策略 |
| :--- | :--- | :--- | :--- |
| **H2 数据文件** | 复制 .h2.db 文件 | 每日一次 | 保留 30 天 |
| **MinIO 文件** | rsync 至远程 NAS | 每日一次 | 保留 90 天 |
| **ES 索引** | snapshot 至远程存储 | 每周一次 | 保留 4 周 |
| **配置文件** | Git 版本控制 | 变更时提交 | 永久 |

**恢复演练**：每季度执行一次全量恢复演练，确保备份可用。

### 8.4 监控与告警

- **应用监控**：Spring Boot Actuator + Prometheus + Grafana
- **日志收集**：ELK（Elasticsearch + Logstash + Kibana），复用现有 ES 集群
- **健康检查**：Nginx upstream health check + Docker healthcheck
- **告警通知**：关键指标（CPU > 80%、内存 > 85%、磁盘 > 90%）通过企业微信/钉钉通知

---

## 九、开发规范

### 9.1 代码规范
- 后端：阿里巴巴 Java 开发手册 + SonarQube 代码扫描
- 前端：ESLint + Prettier + Vue 官方风格指南
- 提交规范：Conventional Commits（feat/fix/docs/style/refactor/test/chore）

### 9.2 接口规范
- 统一响应体：`{ code, msg, data, timestamp }`
- 分页统一参数：`page`（页码，从1开始）、`size`（每页条数）
- 异常统一处理：全局 `@RestControllerAdvice` 捕获，返回标准错误格式

### 9.3 安全规范
- 密码：BCrypt 加密存储
- SQL 注入防护：MyBatis-Plus 参数化查询
- XSS 防护：前端输出转义 + 后端响应头 `Content-Security-Policy`
- CSRF 防护：Sa-Token 内置 CSRF 防护

---

## 十、演进路线

| 阶段 | 目标 | 时间估算 |
| :--- | :--- | :--- |
| **Phase 1** | 核心功能：文件上传/预览/下载、案件分类、中转站管理 | 4-6 周 |
| **Phase 2** | 归档与检索：一键归档、全文检索、开放 API | 3-4 周 |
| **Phase 3** | 借阅管理：双重审批、权限回收、水印安全 | 3-4 周 |
| **Phase 4** | 集成与优化：SSO 对接、监控告警、性能调优 | 2-3 周 |

**平滑演进路径**：
- 数据量增长 → H2 切换 MySQL/PostgreSQL（仅需改配置，MyBatis-Plus 兼容）
- 并发量增长 → 单体拆分为微服务（按模块边界拆分，代码无需大改）
- 存储量增长 → MinIO 扩展为分布式集群（仅需改配置）
