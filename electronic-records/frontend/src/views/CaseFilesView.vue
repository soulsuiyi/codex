<template>
  <el-card>
    <div class="toolbar">
      <el-tag size="large" type="primary">案件 {{ caseNo }}</el-tag>
      <el-upload :auto-upload="false" :show-file-list="false" :on-change="onFileChange">
        <el-button type="primary">选择文件</el-button>
      </el-upload>
      <el-button type="success" :disabled="!selectedFile" :loading="uploading" @click="doUpload">
        上传
      </el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="fileName" label="文件名" min-width="240" show-overflow-tooltip />
      <el-table-column label="大小" width="110">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column label="阶段" width="100">
        <template #default="{ row }">
          <el-tag :type="row.stage === 'ARCHIVED' ? 'success' : 'warning'">
            {{ row.stage === 'ARCHIVED' ? '归档区' : '中转站' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="上传时间" width="170" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="preview(row)">预览</el-button>
          <el-button link type="primary" @click="download(row)">下载</el-button>
          <el-button link type="primary" @click="showVersions(row)">版本</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="versionDialog" title="版本列表" width="680px">
      <template v-if="versionInfo">
        <el-descriptions :column="2" border style="margin-bottom: 12px">
          <el-descriptions-item label="当前文件">{{ versionInfo.current.fileName }}</el-descriptions-item>
          <el-descriptions-item label="版本号">v{{ versionInfo.current.version }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="versionInfo.history" border size="small">
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
          <el-table-column label="大小" width="110">
            <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column prop="changeDesc" label="变更说明" min-width="160" />
          <el-table-column prop="createdAt" label="变更时间" width="170" />
        </el-table>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { deleteFile, downloadFile, fileVersions, listCaseFiles, previewFile, uploadFile } from '@/api/file'
import type { FileVO, VersionListVO } from '@/types/api'

const route = useRoute()
const caseNo = route.params.caseNo as string

const rows = ref<FileVO[]>([])
const loading = ref(false)
const selectedFile = ref<File | null>(null)
const uploading = ref(false)

const versionDialog = ref(false)
const versionInfo = ref<VersionListVO | null>(null)

function onFileChange(file: UploadFile) {
  selectedFile.value = file.raw ?? null
}

async function load() {
  loading.value = true
  try {
    rows.value = await listCaseFiles(caseNo)
  } finally {
    loading.value = false
  }
}

async function doUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const res = await uploadFile(caseNo, selectedFile.value)
    ElMessage.success(`上传成功：${res.fileName}`)
    selectedFile.value = null
    load()
  } finally {
    uploading.value = false
  }
}

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

async function preview(row: FileVO) {
  const blob = await previewFile(row.id)
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank')
}

async function download(row: FileVO) {
  const res = await downloadFile(row.id)
  if (typeof res === 'string') {
    window.open(res, '_blank')
    return
  }
  if (res.type.includes('json')) {
    const parsed = JSON.parse(await res.text()) as { data?: string }
    if (parsed.data) window.open(parsed.data, '_blank')
    return
  }
  const url = URL.createObjectURL(res)
  const a = document.createElement('a')
  a.href = url
  a.download = row.fileName
  a.click()
  URL.revokeObjectURL(url)
}

async function showVersions(row: FileVO) {
  versionInfo.value = await fileVersions(row.id)
  versionDialog.value = true
}

async function remove(row: FileVO) {
  await ElMessageBox.confirm(`确认删除文件「${row.fileName}」？删除后进入逻辑删除状态。`, '提示', {
    type: 'warning',
  })
  await deleteFile(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
