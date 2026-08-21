<template>
  <div class="dashboard">
    <el-row :gutter="16">
      <el-col v-for="item in cards" :key="item.title" :span="6">
        <el-card shadow="hover">
          <div class="card">
            <el-icon :size="36" :color="item.color">
              <component :is="item.icon" />
            </el-icon>
            <div class="card-body">
              <div class="card-title">{{ item.title }}</div>
              <div class="card-desc">{{ item.desc }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-card style="margin-top: 16px">
      <template #header>系统说明</template>
      <p>
        前端骨架已就绪：登录、案件管理、案件文件、归档管理、借阅管理、全文检索等模块的路由与 API
        封装已打通，可在现有页面上继续完善业务功能。
      </p>
      <p>接口规范：统一响应体 <code>{ code, msg, data, timestamp }</code>，分页参数 <code>page</code> / <code>size</code>。</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { Files, FolderOpened, Reading, Search } from '@element-plus/icons-vue'

const cards = [
  { title: '案件管理', desc: '案件建档与维护', icon: FolderOpened, color: '#409eff' },
  { title: '文件管理', desc: '中转站上传与版本', icon: Files, color: '#67c23a' },
  { title: '归档与借阅', desc: '一键归档与双重审批', icon: Reading, color: '#e6a23c' },
  { title: '全文检索', desc: 'H2 FULLTEXT 检索', icon: Search, color: '#f56c6c' },
]
</script>

<style scoped>
.card {
  display: flex;
  align-items: center;
  gap: 14px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.card-desc {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
</style>
