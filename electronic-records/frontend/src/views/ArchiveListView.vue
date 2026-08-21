<template>
  <el-card>
    <div class="toolbar">
      <el-input
        v-model="caseNo"
        placeholder="按案号过滤"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button type="success" @click="openArchive">一键归档</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="caseNo" label="案号" min-width="140" />
      <el-table-column prop="fileCount" label="文件数" width="90" />
      <el-table-column prop="structDocCount" label="结构化文书" width="110" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">
            {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="archivedAt" label="归档时间" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">详情</el-button>
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

    <el-dialog v-model="archiveDialog" title="一键归档" width="440px">
      <el-form label-width="90px">
        <el-form-item label="案件案号">
          <el-input v-model="archiveCaseNo" placeholder="请输入案件案号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="archiveDialog = false">取消</el-button>
        <el-button type="primary" :loading="archiving" @click="doArchive">开始归档</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialog" title="归档详情" width="520px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="案号">{{ detail.caseNo }}</el-descriptions-item>
        <el-descriptions-item label="文件数">{{ detail.fileCount }}</el-descriptions-item>
        <el-descriptions-item label="结构化文书">{{ detail.structDocCount }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ detail.status === 'SUCCESS' ? '成功' : '失败' }}
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="归档时间">{{ detail.archivedAt || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { archiveCase, listArchives } from '@/api/archive'
import type { ArchiveVO } from '@/types/api'

const rows = ref<ArchiveVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const caseNo = ref('')
const loading = ref(false)

const archiveDialog = ref(false)
const archiveCaseNo = ref('')
const archiving = ref(false)

const detailDialog = ref(false)
const detail = ref<ArchiveVO | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await listArchives(page.value, size.value, caseNo.value || undefined)
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

function onPageChange(p: number) {
  page.value = p
  load()
}

function openArchive() {
  archiveCaseNo.value = ''
  archiveDialog.value = true
}

async function doArchive() {
  if (!archiveCaseNo.value) {
    ElMessage.warning('请输入案件案号')
    return
  }
  archiving.value = true
  try {
    const res = await archiveCase(archiveCaseNo.value)
    ElMessage.success(`归档完成，共 ${res.fileCount} 个文件`)
    archiveDialog.value = false
    load()
  } finally {
    archiving.value = false
  }
}

function showDetail(row: ArchiveVO) {
  detail.value = row
  detailDialog.value = true
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
