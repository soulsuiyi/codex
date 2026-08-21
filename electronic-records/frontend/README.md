# archive-web（前端）

无纸化办案电子档案系统前端，基于 Vue 3 + Vite + TypeScript + Element Plus + Axios。

## 快速开始

```powershell
npm install
npm run dev
```

开发地址 <http://127.0.0.1:5173>，`/api` 请求由 Vite 代理到 `http://127.0.0.1:8080`。

## 常用命令

| 命令 | 说明 |
| :--- | :--- |
| `npm run dev` | 开发服务器（热更新） |
| `npm run build` | 类型检查 + 生产构建（产物 `dist/`） |
| `npm run preview` | 预览生产构建 |
| `npm run lint` | ESLint 检查并自动修复 |
| `npm run format` | Prettier 格式化 |

## 目录结构

```text
src/
├── api/          # 各业务模块 API 封装（与后端 /api/v1 一一对应）
├── layouts/      # 主布局（侧边栏 + 顶栏）
├── router/       # 路由与登录守卫
├── stores/       # Pinia 状态（用户会话）
├── types/        # 与后端 DTO/VO 对齐的类型定义
├── utils/        # Axios 封装、高亮工具
└── views/        # 页面
```

## 约定

- 所有请求经 `src/utils/request.ts`，自动携带 Sa-Token（`Authorization` 头），并解包统一响应体 `{ code, msg, data, timestamp }`，`code !== 200` 时统一弹错并处理 401 跳转登录。
- 分页参数统一使用 `page` / `size`。
- 页面默认按菜单与角色规划，具体接口权限由后端 RBAC 控制。
