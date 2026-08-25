<template>
  <el-card>
    <el-form inline class="filter-form">
      <el-form-item label="模块">
        <el-select v-model="query.module" clearable placeholder="全部" style="width: 130px">
          <el-option v-for="m in moduleOptions" :key="m" :label="m" :value="m" />
        </el-select>
      </el-form-item>
      <el-form-item label="动作">
        <el-select v-model="query.action" clearable placeholder="全部" style="width: 130px">
          <el-option v-for="a in actionOptions" :key="a" :label="a" :value="a" />
        </el-select>
      </el-form-item>
      <el-form-item label="操作人">
        <el-input v-model="query.username" placeholder="账号模糊匹配" clearable style="width: 160px" />
      </el-form-item>
      <el-form-item label="结果">
        <el-select v-model="query.result" clearable placeholder="全部" style="width: 120px">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          style="width: 360px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="操作人" width="110" />
      <el-table-column label="模块" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ row.module }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="动作" width="100">
        <template #default="{ row }">
          <el-tag size="small" type="warning">{{ row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="targetId" label="目标ID" width="110" />
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.result === 'SUCCESS' ? 'success' : 'danger'">
            {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="操作时间" width="170" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        :current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next, jumper"
        @current-change="onPageChange"
      />
    </div>

    <el-dialog v-model="detailDialog" title="审计详情" width="620px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="操作人">{{ detail.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模块 / 动作">{{ detail.module }} / {{ detail.action }}</el-descriptions-item>
        <el-descriptions-item label="目标">{{ detail.targetType || '-' }} / {{ detail.targetId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP / UA">{{ detail.ip || '-' }} / {{ detail.userAgent || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ detail.result }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ detail.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求参数">
          <pre class="detail-json">{{ detail.detail || '-' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { AuditLogVO } from '@/types/api'
import { listAuditLogs } from '@/api/system'

const moduleOptions = ['FILE', 'BORROW', 'ARCHIVE', 'SYSTEM', 'CASE', 'CATEGORY']
const actionOptions = ['CREATE', 'UPDATE', 'DELETE', 'UPLOAD', 'DOWNLOAD', 'PREVIEW', 'ARCHIVE', 'APPROVE', 'APPLY', 'RETURN']

const rows = ref<AuditLogVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const dateRange = ref<string[]>([])

const query = reactive<{
  module?: string
  action?: string
  username?: string
  result?: string
}>({})

const detailDialog = ref(false)
const detail = ref<AuditLogVO | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await listAuditLogs({
      page: page.value,
      size: size.value,
      module: query.module || undefined,
      action: query.action || undefined,
      username: query.username || undefined,
      result: query.result || undefined,
      startTime: dateRange.value?.[0] || undefined,
      endTime: dateRange.value?.[1] || undefined,
    })
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  load()
}

function reset() {
  Object.assign(query, { module: undefined, action: undefined, username: undefined, result: undefined })
  dateRange.value = []
  page.value = 1
  load()
}

function onPageChange(p: number) {
  page.value = p
  load()
}

function openDetail(row: AuditLogVO) {
  detail.value = row
  detailDialog.value = true
}

onMounted(load)
</script>

<style scoped>
.filter-form {
  margin-bottom: 4px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.detail-json {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
}
</style>
