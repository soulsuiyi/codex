# 后端视角的 Vue3 前端开发指南（含用户管理页实施计划）

> 面向熟悉 Spring Boot 的后端 Java 开发者。全文用 Java 概念类比前端术语，所有代码示例来自本仓库真实文件。
> 适用仓库：无纸化办案电子档案系统（`frontend/` 为 Vue 3 + Vite + TypeScript + Element Plus + Axios）。

## 前后端概念对照表

| 后端（Spring Boot） | 前端（本项目） | 职责 |
| :--- | :--- | :--- |
| `application.yml` 的 `server.port` + Nginx 反向代理 | `frontend/vite.config.ts` 的 `server.port` + `proxy` | 启动端口、接口转发 |
| `application-dev.yml` / Profile | `.env.development` + `import.meta.env` | 环境差异化配置 |
| Maven 包名（如 `com.archive.*`） | `@` 别名（`@/api/xxx`） | 路径引用根 |
| RestTemplate / WebClient 单例 | `frontend/src/utils/request.ts` 的 axios 实例 | HTTP 客户端 |
| HandlerInterceptor / Filter 注入 Token | request 拦截器 | 请求头注入 |
| `@RestControllerAdvice` 全局异常处理 | response 拦截器 | 统一解包、统一报错、401 处理 |
| DTO / VO 类 | `frontend/src/types/api.ts` 的 TypeScript interface | 前后端契约 |
| Feign 接口 / Service 方法 | `frontend/src/api/*.ts` 里的函数 | 业务接口封装 |
| Controller/Service 方法体 | `<script setup>` | 页面逻辑与状态 |
| JSP / Thymeleaf 模板 | `<template>` | 视图渲染 |
| private 字段（隔离） | `<style scoped>` | 样式隔离 |
| Caffeine 缓存 | Pinia store | 客户端内存状态 |
| `@RequestMapping` 路由表 | vue-router 的 routes | URL → 处理单元映射 |
| Sa-Token 登录拦截器 | `router.beforeEach` | 登录守卫 |

---

## 一、项目结构与配置：Vite 代理 ≈ 开发期的 Nginx

`frontend/vite.config.ts`：

```ts
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://127.0.0.1:8080',
      changeOrigin: true,
    },
  },
},
```

开发阶段 Vite dev server 一人分饰两角：

- `port: 5173` 相当于前端应用的 `server.port`，浏览器访问 `http://127.0.0.1:5173`。
- `proxy` 相当于 AGENTS.md 接入层 Nginx 的 `location /api { proxy_pass http://127.0.0.1:8080; }`。所有以 `/api` 开头的请求由 Vite 转发到后端 8080；`changeOrigin: true` 类似 Nginx 重写 Host 头。它同时解决跨域：浏览器只认识 5173 一个源，看不到 8080，没有 CORS 问题。

请求链路（URL 全程不变）：

```text
前端 get('/cases')
  → baseURL '/api/v1'（来自 .env.development 的 VITE_API_BASE_URL）
  → http://localhost:5173/api/v1/cases
  → Vite proxy 转发到 http://127.0.0.1:8080/api/v1/cases
  → CaseController 的 @RequestMapping("/api/v1/cases") + @GetMapping
```

前端写的 `/cases` 只是相对路径，拼上 `baseURL` 之后才与后端 `@RequestMapping` 对齐。`baseURL` 由 `.env.development` / `.env.production` 的 `VITE_API_BASE_URL=/api/v1` 注入，通过 `import.meta.env.VITE_API_BASE_URL` 读取——相当于 `@Value` 读配置，`.env.*` 相当于 Spring 的 profile。

`@` 别名（`vite.config.ts` 的 `resolve.alias`）：`@/api/case` 指向 `src/api/case.ts`，等价于把 `src/` 当作类路径根目录。

## 二、接口调用规范：request.ts ≈ RestTemplate + 拦截器 + 全局异常处理

调用链只有一条：`src/utils/request.ts` → `src/api/*.ts` → 页面。

### 底层：utils/request.ts

```ts
const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 30000,
})
```

相当于一个配好 baseURL 与超时时间的 RestTemplate 单例。

请求拦截器（`request.ts` 第 33 行起）等价于后端从请求头还原会话的 Filter：从 localStorage 取 token，塞进 `Authorization` 头。后端 `application.yml` 中 `sa-token.token-name: Authorization` 与前端约定同一个请求头名。

响应拦截器（第 41 行起）是"前端版 @RestControllerAdvice"：

```ts
if (body && typeof body.code === 'number') {
  if (body.code === 200) {
    return body.data as unknown as typeof response   // 解包：只把 data 还给你
  }
  if (body.code === 401) {
    redirectToLogin()
  } else {
    ElMessage.error(body.msg || '请求失败')
  }
  return Promise.reject(new Error(body.msg || '请求失败'))
}
```

后端 `Result.success(data)` 包一层 `{ code, msg, data, timestamp }`，前端拦截器解一层。业务代码拿到的永远是 `data` 本身；`code !== 200` 统一弹错并 reject，401 清 token 跳登录。页面代码不需要 catch 业务错误——与 AGENTS.md"Controller 禁止 try-catch 拼响应"的纪律一致。

TypeScript 泛型 `get<T>` 相当于 `RestTemplate.exchange(url, ..., ParameterizedTypeReference<T>)`。`types/api.ts` 的 `Result<T>`、`PageResult<T>` 与后端 `Result.java`、`PageResult.java` 字段一一对应：`{ records, total, page, size }`。

### 业务层：api/*.ts（相当于 Service / Feign 接口）

```ts
export function listCases(page = 1, size = 10, keyword?: string): Promise<PageResult<CaseVO>> {
  return get<PageResult<CaseVO>>('/cases', { page, size, keyword })
}
```

`Promise<T>` ≈ `CompletableFuture<T>`；`async/await` ≈ 协程语法糖，`await` 相当于 `.join()`。

### 页面调用标准写法

```ts
async function load() {
  loading.value = true
  try {
    const res = await listCases(page.value, size.value, keyword.value || undefined)
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
```

要点：`try/finally` 只负责开关 loading；业务错误由拦截器统一处理；`keyword.value || undefined` 相当于可选参数，无输入不传，避免后端收到空字符串。

## 三、SFC 拆解：一个 .vue 文件 = 自带视图的 Controller 方法

以 `frontend/src/views/CaseListView.vue` 为例（项目暂无用户管理页，案件列表页结构完全相同，可直接当模板）。

### `<template>` = JSP / Thymeleaf 视图

```html
<el-table v-loading="loading" :data="rows" border stripe>
  <el-table-column prop="caseNo" label="案号" min-width="140" />
  <el-table-column label="状态" width="100">
    <template #default="{ row }">
      <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
    </template>
  </el-table-column>
</el-table>
```

- `:data="rows"` 的 `:` 是绑定，相当于 `th:each` 数据源；`prop="caseNo"` ≈ 取 JavaBean 的 `getCaseNo()`。
- `{{ ... }}` 就是 `${...}` 表达式；`#default="{ row }"` 是插槽，可理解为表格把当前行对象当参数回调你（类似 lambda）。

### `<script setup lang="ts">` = Controller + Service + 状态

```ts
const rows = ref<CaseVO[]>([])   // 当前页数据
const total = ref(0)             // 总条数
const page = ref(1)              // 页码
const size = ref(10)             // 每页条数
const keyword = ref('')          // 搜索关键词
const loading = ref(false)       // loading 状态位
```

`ref` ≈ "可被观察的变量"（JavaFX `SimpleObjectProperty` + 监听器）：赋值后模板自动重渲染。`load()`、`handleSearch()`、`onPageChange()` 就是 Service 方法，触发者是页面事件而非 HTTP 请求。`onMounted(load)` ≈ `@PostConstruct` / `ApplicationRunner`：组件挂载后执行首次查询。

### `<style scoped>` = private 样式

`scoped` 使 CSS 只作用于当前组件，就像 `private` 字段；穿透子组件用 `:deep(...)`，相当于显式声明允许"子类"访问。

## 四、状态与路由：Pinia ≈ 缓存镜像，Router ≈ URL 映射 + 过滤器

### Pinia 不是 Session，是"令牌的本地缓存"

- `localStorage`（`utils/request.ts`）是持久层，存 token——类似 Cookie / 浏览器端"文件存储"，刷新不丢。
- `stores/user.ts` 的 Pinia store 是内存缓存：`state: () => ({ token: getToken() })` 从"持久层"回填，类似 Caffeine 启动时从 DB 回填；`login()` 成功后将 token 双写内存与 localStorage。
- 真正的会话在后端 Sa-Token；前端只持有令牌。localStorage 不具备响应式，需要 Pinia 把 token 变成可观察状态，登录/登出时界面才能即时感知。

登录链路：`login` → Sa-Token 签发 `tokenValue` → 存 localStorage → 每次请求拦截器塞 `Authorization` → 后端 `StpInterfaceImpl` 按用户查角色 → `@SaCheckRole` 判权限。**权限判断完全在后端**。

### Vue Router ≈ @RequestMapping 路由表 + 登录过滤器

`router/index.ts` 的 routes 数组即路由表：`path: 'cases'` + 组件 ≈ `@RequestMapping` + Controller 方法；`component: () => import(...)` 为懒加载。`router.beforeEach` ≈ Sa-Token 拦截器：未登录访问非公开页 → 重定向 `/login`；`meta: { public: true }` 即白名单。

### 动态菜单权限：当前是静态，对接方案如下

现状：

- 路由写死在 `router/index.ts`，侧边栏菜单写死在 `MainLayout.vue`；
- 数据库有 `sys_menu` 表（`path`、`component`、`perms` 字段齐全），但后端没有"按角色返回菜单树"的接口；
- `StpInterfaceImpl.getPermissionList` 返回空集合，角色-菜单关联未定义。

落地三步：

1. 后端按当前用户角色查询 `sys_menu`，返回层级菜单树（权限清单）。
2. 前端登录后取菜单树，用 `router.addRoute()` 动态注册路由，侧边栏 `el-menu` 改为 `v-for` 渲染。
3. 按钮级权限用 `v-permission` 指令控制。

对应后端 RBAC：Sa-Token 管"接口能否调"，动态菜单管"界面显示什么"，同源数据为 `sys_menu.perms`。

---

# 用户管理页面实施计划

## 0. 前提：后端暂无用户列表接口

当前后端没有 `UserController`，`AuthController` 仅有 `/auth/login`、`/auth/logout`、`/auth/me`。按"契约先行"设计，前端按下列约定编写，后端接口作为前置任务按同一风格补齐（照抄 `CaseController.list()` 风格）：

```text
GET /api/v1/users?page=1&size=10&keyword=xxx
→ Result<PageResult<UserVO>>，@SaCheckRole("ADMIN")
UserVO 字段：id, username, realName, phone, email, deptId, status, lastLoginTime, createdAt, updatedAt
```

关键约束：UserVO **绝不能包含 `password`**（对照 `SysUser.java` 实体，密码只进不出）。

## 1. 文件清单

| 动作 | 文件 | 内容 |
| :--- | :--- | :--- |
| 新建 | `frontend/src/views/UserListView.vue` | 页面本体：搜索框 + 表格 + 分页 |
| 新建 | `frontend/src/api/user.ts` | 用户接口封装，仿 `case.ts` |
| 修改 | `frontend/src/types/api.ts` | 追加 `UserVO` 接口 |
| 修改 | `frontend/src/router/index.ts` | 注册 `/users` 路由 |
| 修改 | `frontend/src/layouts/MainLayout.vue` | 侧边栏增加"用户管理"菜单项 |
| 前置（后端） | `backend/archive-api/.../UserController.java` 等 | `GET /api/v1/users` |

## 2. 实施步骤

### 步骤 1：定义契约（types/api.ts 追加）

```ts
export interface UserVO {
  id: number
  username: string
  realName: string
  phone?: string
  email?: string
  deptId?: number
  status: number // 0-禁用 1-启用
  lastLoginTime?: string
  createdAt?: string
  updatedAt?: string
}
```

可选字段用 `?`，对应后端 VO 可能为 null 的字段。

### 步骤 2：API 封装（新建 api/user.ts）

```ts
import { get } from '@/utils/request'
import type { PageResult, UserVO } from '@/types/api'

export function listUsers(page = 1, size = 10, keyword?: string): Promise<PageResult<UserVO>> {
  return get<PageResult<UserVO>>('/users', { page, size, keyword })
}
```

### 步骤 3：页面本体（新建 UserListView.vue）

```vue
<template>
  <el-card>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按用户名/姓名搜索"
        clearable
        style="width: 260px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" min-width="140" />
      <el-table-column prop="realName" label="姓名" min-width="120" />
      <el-table-column prop="phone" label="手机号" min-width="140" />
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginTime" label="最后登录" width="170" />
      <el-table-column prop="createdAt" label="创建时间" width="170" />
    </el-table>

    <div class="pagination">
      <el-pagination
        :current-page="page"
        :page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listUsers } from '@/api/user'
import type { UserVO } from '@/types/api'

const rows = ref<UserVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await listUsers(page.value, size.value, keyword.value || undefined)
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function statusLabel(status: number) {
  return status === 1 ? '启用' : '禁用'
}

function statusTag(status: number) {
  return status === 1 ? 'success' : 'info'
}

function handleSearch() {
  page.value = 1
  load()
}

function onPageChange(p: number) {
  page.value = p
  load()
}

function onSizeChange(s: number) {
  size.value = s
  page.value = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
```

### 步骤 4：注册路由（router/index.ts 的 children 中追加）

```ts
{
  path: 'users',
  name: 'users',
  component: () => import('@/views/UserListView.vue'),
  meta: { title: '用户管理' },
},
```

### 步骤 5：加菜单项（MainLayout.vue 的 el-menu 中追加）

```html
<el-menu-item index="/users">
  <el-icon><User /></el-icon>
  <span>用户管理</span>
</el-menu-item>
```

注意：目前菜单为静态，对所有登录用户可见；非 ADMIN 访问会被后端 `@SaCheckRole("ADMIN")` 以 403 拦截并统一弹"无权限访问"。动态菜单落地后按角色隐藏。

## 3. 分页数据处理逻辑（Java 视角）

| 前端 ref | 等价后端概念 |
| :--- | :--- |
| `page` / `size` | `@RequestParam page / size` |
| `total` | `COUNT(*)` 结果 |
| `rows` | `LIMIT/OFFSET` 查出的 `records` 列表 |

三条黄金规则：

1. 搜索（`handleSearch`）：先 `page = 1` 再 `load()`——换 WHERE 必须重置 OFFSET。
2. 翻页（`onPageChange`）：只更新 `page` 再 `load()`。
3. 改每页条数（`onSizeChange`）：更新 `size` 且 `page = 1` 再 `load()`——OFFSET 必须重算。

`el-pagination` 的 `layout="total, sizes, prev, pager, next, jumper"` 是展示布局串；`:page-sizes="[10, 20, 50]"` 是每页条数选项；`v-loading` 配 `loading` ref 在 `finally` 中关闭。

## 4. 规范检查（AGENTS.md 合规）

```powershell
npm run format   # Prettier：无分号、单引号、printWidth 100、尾逗号 all
npm run lint     # ESLint（vue3-recommended + TS），脚本自带 --fix
npm run build    # vue-tsc 严格模式类型检查 + 产物构建
```

`tsconfig.json` 开启 `strict`、`noUnusedLocals`、`noUnusedParameters`：未使用的 import / 变量会使 `npm run build` 失败。提交信息使用 Conventional Commits，如 `feat: 用户管理列表页（搜索/分页）`。

## 5. 范围与后续

- **阶段一（本次）**：列表 + 搜索 + 分页 + 状态标签。
- **阶段二（可选）**：新建/编辑用户弹窗、启用/禁用开关——复用 `CaseListView` 的 `el-dialog` + `el-form` 模式，需后端补 `POST/PUT /api/v1/users` 等接口。
- **按钮级权限**：动态菜单/权限接口落地后加 `v-permission`；现阶段不做角色过滤，由后端 403 兜底。
