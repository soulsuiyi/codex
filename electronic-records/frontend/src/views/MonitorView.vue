<template>
  <div class="monitor">
    <div class="toolbar">
      <el-button type="primary" :loading="loading" @click="loadAll">刷新</el-button>
      <el-tag v-if="health" :type="health.status === 'UP' ? 'success' : 'danger'" size="large">
        服务状态：{{ health.status }}
      </el-tag>
    </div>

    <el-row :gutter="16">
      <el-col v-for="card in statCards" :key="card.key" :span="6">
        <el-card shadow="hover" style="margin-bottom: 16px">
          <div class="stat-card">
            <el-icon :size="30" :color="card.color">
              <component :is="card.icon" />
            </el-icon>
            <div>
              <div class="stat-value">{{ stats?.[card.key] ?? '-' }}</div>
              <div class="stat-label">{{ card.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card>
          <template #header>JVM 堆内存</template>
          <el-progress :percentage="memoryPercent" :stroke-width="18" :color="memoryColor" />
          <div class="memory-detail">{{ formatBytes(memoryUsed) }} / {{ formatBytes(memoryMax) }}</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>进程运行时长</template>
          <div class="uptime">{{ formatUptime(uptimeSeconds) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 16px">
      <template #header>健康检查组件</template>
      <el-table v-if="healthComponents.length" :data="healthComponents" border size="small">
        <el-table-column prop="name" label="组件" width="240" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="row.status === 'UP' ? 'success' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无组件信息" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import axios from 'axios'
import {
  Box,
  Collection,
  Files,
  FolderOpened,
  Key,
  Lock,
  Reading,
  User,
} from '@element-plus/icons-vue'
import { systemStats } from '@/api/system'
import type { StatsVO } from '@/types/api'

interface HealthResp {
  status: string
  components?: Record<string, { status: string }>
}

interface MetricResp {
  measurements: { statistic: string; value: number }[]
}

const loading = ref(false)
const stats = ref<StatsVO | null>(null)
const health = ref<HealthResp | null>(null)
const memoryUsed = ref(0)
const memoryMax = ref(1)
const uptimeSeconds = ref(0)

const statCards: { key: keyof StatsVO; label: string; icon: Component; color: string }[] = [
  { key: 'userCount', label: '用户数', icon: User, color: '#409eff' },
  { key: 'caseCount', label: '案件数', icon: FolderOpened, color: '#67c23a' },
  { key: 'fileCount', label: '文件数', icon: Files, color: '#e6a23c' },
  { key: 'archivedFileCount', label: '已归档文件', icon: Box, color: '#f56c6c' },
  { key: 'archiveRecordCount', label: '归档记录', icon: Collection, color: '#909399' },
  { key: 'borrowApplyCount', label: '借阅申请', icon: Reading, color: '#9254de' },
  { key: 'activeBorrowCount', label: '借阅中', icon: Lock, color: '#13c2c2' },
  { key: 'apiKeyCount', label: 'API 密钥', icon: Key, color: '#fa8c16' },
]

const memoryPercent = computed(() =>
  Math.min(100, Math.round((memoryUsed.value / memoryMax.value) * 100)),
)
const memoryColor = computed(() =>
  memoryPercent.value > 85 ? '#f56c6c' : memoryPercent.value > 60 ? '#e6a23c' : '#67c23a',
)
const healthComponents = computed(() =>
  Object.entries(health.value?.components || {}).map(([name, c]) => ({ name, status: c.status })),
)

async function loadStats() {
  stats.value = await systemStats()
}

async function loadHealth() {
  health.value = (await axios.get<HealthResp>('/actuator/health')).data
}

async function loadMetrics() {
  const [used, max, uptime] = await Promise.all([
    axios.get<MetricResp>('/actuator/metrics/jvm.memory.used'),
    axios.get<MetricResp>('/actuator/metrics/jvm.memory.max'),
    axios.get<MetricResp>('/actuator/metrics/process.uptime'),
  ])
  memoryUsed.value = used.data.measurements[0]?.value ?? 0
  memoryMax.value = max.data.measurements[0]?.value ?? 1
  uptimeSeconds.value = uptime.data.measurements[0]?.value ?? 0
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadStats(), loadHealth(), loadMetrics()])
  } finally {
    loading.value = false
  }
}

function formatBytes(bytes: number) {
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024).toFixed(1)} KB`
}

function formatUptime(seconds: number) {
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return `${d} 天 ${h} 小时 ${m} 分钟`
}

onMounted(() => {
  loadAll().catch(() => {
    // 后端未启动时页面保持可打开
  })
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
}

.stat-label {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.memory-detail {
  margin-top: 8px;
  color: #606266;
  font-size: 13px;
}

.uptime {
  font-size: 28px;
  font-weight: 600;
}
</style>
