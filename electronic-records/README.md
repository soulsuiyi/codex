# 无纸化办案电子档案系统

> 轻量化、高安全、全流程的电子档案管理平台，实现文件产生、中转暂存、一键归档、借阅利用的全生命周期闭环管理。

## 目录结构

| 目录 | 说明 |
| :--- | :--- |
| `backend/` | Spring Boot 3.x 后端（Maven 多模块 `archive-system`，7 个模块） |
| `frontend/` | Vue 3 + Vite + TypeScript 前端（Element Plus + Axios） |

后端模块：`archive-common` / `archive-auth` / `archive-core` / `archive-search` / `archive-api` / `archive-job` / `archive-starter`，职责与依赖约束见 `AGENTS.md`。

## 快速开始

### 后端（JDK 21）

```powershell
cd backend
mvn -pl archive-starter -am spring-boot:run
```

- 默认端口 8080；接口文档（Knife4j）：<http://127.0.0.1:8080/doc.html>
- 依赖 MinIO（默认 `http://127.0.0.1:9000`），后端启动时自动初始化 `transit-bucket` / `archive-bucket`
- H2 数据库文件位于 `backend/data/h2/`，启动时自动建表并写入种子数据

### 前端（Node.js 18+）

```powershell
cd frontend
npm install
npm run dev
```

- 默认地址 <http://127.0.0.1:5173>；开发环境通过 Vite 代理将 `/api` 转发到 `http://127.0.0.1:8080`
- 生产构建：`npm run build`，产物在 `frontend/dist/`，由 Nginx 托管并反向代理 `/api`

## 说明

- Git 仓库根目录位于本目录的上级目录，本目录是仓库中的一个子目录；目录迁移依赖 Git 重命名检测保留历史。
- 详细技术规划见 `codeplan.md`，开发守则见 `AGENTS.md`。
