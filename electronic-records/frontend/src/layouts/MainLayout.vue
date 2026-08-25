<template>
  <el-container class="layout">
    <el-aside width="220px" class="layout-aside">
      <div class="logo">无纸化办案<br />电子档案系统</div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#001529"
        text-color="#a6adb4"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-menu-item index="/cases">
          <el-icon><FolderOpened /></el-icon>
          <span>案件管理</span>
        </el-menu-item>
        <el-menu-item index="/cases/categories">
          <el-icon><Collection /></el-icon>
          <span>案件分类</span>
        </el-menu-item>
        <el-menu-item index="/archives">
          <el-icon><Files /></el-icon>
          <span>归档管理</span>
        </el-menu-item>
        <el-menu-item index="/borrows">
          <el-icon><Reading /></el-icon>
          <span>借阅管理</span>
        </el-menu-item>
        <el-menu-item index="/search">
          <el-icon><Search /></el-icon>
          <span>全文检索</span>
        </el-menu-item>
        <el-sub-menu index="/system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/system/users">用户管理</el-menu-item>
          <el-menu-item index="/system/roles">角色管理</el-menu-item>
          <el-menu-item index="/system/menus">菜单管理</el-menu-item>
          <el-menu-item index="/system/dicts">字典管理</el-menu-item>
        <el-menu-item index="/system/api-keys">API 密钥</el-menu-item>
          <el-menu-item index="/system/audit-logs">操作日志</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/monitor">
          <el-icon><Monitor /></el-icon>
          <span>监控中心</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="layout-header">
        <div class="header-title">{{ $route.meta.title || '' }}</div>
        <el-dropdown @command="handleCommand">
          <span class="user-name">
            <el-icon><User /></el-icon>
            {{ userStore.isLoggedIn ? '当前用户' : '未登录' }}
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Collection,
  Files,
  FolderOpened,
  HomeFilled,
  Monitor,
  Reading,
  Search,
  Setting,
  User,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => {
  if (route.path === '/cases/categories') return '/cases/categories'
  if (route.path.startsWith('/cases')) return '/cases'
  return route.path
})

async function handleCommand(command: string) {
  if (command === 'logout') {
    await userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}

.layout-aside {
  background: #001529;
}

.logo {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  padding: 18px 12px;
  text-align: center;
}

.layout-aside :deep(.el-menu) {
  border-right: none;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e6e6e6;
  background: #fff;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.user-name {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  outline: none;
}

.layout-main {
  background: #f5f7fa;
}
</style>
