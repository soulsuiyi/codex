# 无纸化办案电子档案系统

> 轻量化、高安全、全流程的电子档案管理平台，覆盖文件产生、中转暂存、一键归档、借阅利用的全生命周期闭环管理。

## 项目简介

无纸化办案电子档案系统是一套面向办案场景的轻量化电子档案管理平台，以“单机低资源部署”为设计基调，实现从文件产生、中转暂存、一键归档到借阅利用的全流程数字化管理，满足电子档案“真实性、完整性、可用性、安全性”四项要求。

核心价值：**全生命周期闭环管理、极简部署运维、安全合规内建**。

## 核心功能

- **两阶段存储**：文件先入中转站，归档后转入只读归档区，禁止跳级写入
- **文件管理**：SHA-256 秒传、分片上传、多版本覆盖与历史版本保留、逻辑删除
- **在线预览 / 下载**：图片、PDF 服务端动态水印注入，MinIO 预签名 URL 下载
- **案件管理**：案件 CRUD、案件分类树、案件文件树
- **一键归档**：完整性校验 → Hash 比对 → 事务内批量归档，任一环节失败整体回滚
- **借阅管理**：双重审批状态机、授权 Token、到期自动回收
- **全文检索**：H2 内置 FULLTEXT 全文索引，相关度排序 + 命中高亮
- **安全体系**：RBAC 权限（Sa-Token）、操作审计日志、开放 API（API Key + HMAC 签名 + IP 白名单）
- **监控运维**：Actuator 健康检查 + Prometheus 指标采集

## 技术栈

| 领域 | 技术 | 版本 / 模式 |
| :--- | :--- | :--- |
| 后端框架 | Spring Boot | 3.5.x（JDK 21，虚拟线程） |
| 持久层 | MyBatis-Plus | 3.5.x |
| 数据库 | H2 | File Mode（内置 FULLTEXT 全文检索） |
| 缓存 | Caffeine | 本地内存缓存（替代 Redis） |
| 认证授权 | Sa-Token | RBAC、本地会话 |
| 对象存储 | MinIO | 单机模式，S3 兼容 |
| 文本提取 / OCR | Apache Tika / Tesseract | 文档文本提取 / 图片 OCR |
| 接口文档 | Knife4j（Springdoc OpenAPI 3.0） | — |
| 前端 | Vue 3 + Vite + TypeScript | Element Plus + Pinia + Axios |
| 部署 | Nginx + Docker Compose | 单机 3 服务：archive-app / archive-nginx / archive-minio |

## 环境要求

**硬件**：单机 4C8G 即可流畅运行（推荐）。

**软件环境**：

| 软件 | 版本要求 | 说明 |
| :--- | :--- | :--- |
| JDK | 21+ | 后端运行时 |
| Maven | 3.8+ | 后端构建 |
| Node.js | 18+（推荐 20 LTS） | 前端构建 |
| MinIO | 最新版 | 对象存储，默认 `http://127.0.0.1:9000` |
| Tesseract OCR | 5.x（可选） | 图片 OCR；未安装时图片文本提取返回空 |
| FFmpeg / LibreOffice | — | 音视频 / 文档预览转换，规划中，尚未接入代码 |

## 快速启动

### 1. 启动 MinIO

方式一（Docker）：

```bash
docker run -d --name archive-minio -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data --console-address ":9001"
```

方式二（原生进程）：下载 MinIO 后执行 `minio server <数据目录> --console-address ":9001"`。

### 2. 配置后端 `application.yml`

MinIO 连接配置位于 `backend/archive-starter/src/main/resources/application.yml`，默认连接本机，可通过环境变量覆盖：

```yaml
minio:
  endpoint: ${MINIO_ENDPOINT:http://127.0.0.1:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  buckets:
    transit: ${MINIO_TRANSIT_BUCKET:transit-bucket}   # 中转站桶
    archive: ${MINIO_ARCHIVE_BUCKET:archive-bucket}   # 归档区桶
```

其他可选配置：

```yaml
archive:
  ocr:
    command: ${ARCHIVE_OCR_COMMAND:tesseract}   # Tesseract 可执行文件路径
  watermark:
    font-path: "${ARCHIVE_WATERMARK_FONT_PATH:C:/Windows/Fonts/simhei.ttf}"  # 水印字体，Linux/macOS 需改为本机字体路径
```

应用启动时自动创建 `transit-bucket` / `archive-bucket`，并自动建表（15 张）与写入种子数据；MinIO 不可用时仅告警、不阻塞启动。

### 3. 启动后端

```powershell
cd backend
mvn -pl archive-starter -am spring-boot:run
```

- 服务地址：<http://localhost:8080>
- 默认账号：`admin / admin123`（系统管理员，首次登录后请修改）
- H2 控制台：<http://localhost:8080/h2-console>（连接信息见 `application.yml`）
- 运行全部测试：`mvn -pl archive-starter -am test`

### 4. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

- 开发地址：<http://localhost:5173>（Vite 已将 `/api`、`/h2-console` 代理至 `http://127.0.0.1:8080`）
- 生产构建：`npm run build`，产物输出至 `frontend/dist/`，由 Nginx 托管并反向代理 `/api`

## 外部组件说明

| 组件 | 用途 | 集成状态 | 安装 / 配置 |
| :--- | :--- | :--- | :--- |
| Tesseract OCR | 图片文字识别，提取文本进入全文检索 | ✅ 已集成 | 安装 Tesseract 5.x 并确保命令在 PATH；通过 `application.yml` 的 `archive.ocr.command` 指定路径（默认 `tesseract`）。Windows 可下载 UB-Mannheim 安装包，Ubuntu 可执行 `apt install tesseract-ocr tesseract-ocr-chi-sim` |
| Apache Tika | Word / PDF 等文档文本提取 | ✅ 已集成（Maven 内嵌，无需外部安装） | 无需配置 |
| LibreOffice | Word / Excel / PPT 转 PDF 在线预览 | ⏳ 规划中（尚未接入代码） | 安装 LibreOffice，后续阶段接入转换服务 |
| FFmpeg | 音视频转码 HLS 流式预览 | ⏳ 规划中（尚未接入代码） | 安装 FFmpeg，后续阶段接入转码服务 |

## 接口文档

- Knife4j 接口文档：<http://localhost:8080/doc.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>
- 所有接口统一以 `/api/v1/` 为前缀，响应体统一为 `{ code, msg, data, timestamp }`（成功时 `code=200`）
- 可使用 `admin / admin123` 登录后携带 Token 调试接口

## 项目结构

```text
electronic-records/
├── backend/                  # Spring Boot 后端（Maven 多模块）
│   ├── archive-common/       # 通用：常量、枚举、异常、DTO/VO、工具类
│   ├── archive-auth/         # 安全：Sa-Token、RBAC、审计切面
│   ├── archive-core/         # 核心业务：文件、案件、归档、借阅
│   ├── archive-search/       # 检索：H2 全文检索、文本提取 / OCR
│   ├── archive-api/          # 对外接口：REST API、Knife4j 文档
│   ├── archive-job/          # 定时任务：权限回收、临时文件清理
│   ├── archive-starter/      # 启动模块：Application 入口、配置聚合
│   └── prometheus/           # Prometheus 采集与告警规则
├── frontend/                 # Vue 3 前端
├── AGENTS.md                 # 开发工作守则
└── codeplan.md               # 技术规划文档
```

> 注：详细技术规划见 `codeplan.md`，开发协作守则见 `AGENTS.md`。
