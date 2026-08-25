<template>
  <el-card>
    <div class="toolbar">
      <el-tag size="large" type="primary">案件 {{ caseNo }}</el-tag>
      <el-upload :auto-upload="false" :show-file-list="false" :on-change="onFileChange">
        <el-button type="primary">选择文件</el-button>
      </el-upload>
      <el-button type="success" :disabled="!selectedFile" :loading="uploading" @click="doUpload">
        {{ uploading ? '上传中…' : '上传' }}
      </el-button>
      <el-progress
        v-if="uploading && uploadProgress > 0"
        :percentage="uploadProgress"
        :stroke-width="14"
        style="width: 220px"
      />
      <el-tag v-if="selectedFile" type="info">{{ formatSize(selectedFile.size) }}</el-tag>
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

    <el-dialog
      v-model="previewDialog"
      :title="previewTitle"
      width="78%"
      top="4vh"
      destroy-on-close
      @closed="closePreview"
    >
      <div class="pdf-toolbar">
        <el-button :disabled="previewPage <= 1" @click="previewPage--; renderPdfPage()">上一页</el-button>
        <span class="pdf-page-info">{{ previewPage }} / {{ previewTotal }}</span>
        <el-button :disabled="previewPage >= previewTotal" @click="previewPage++; renderPdfPage()">
          下一页
        </el-button>
      </div>
      <div class="pdf-canvas-wrap">
        <canvas ref="previewCanvas" class="pdf-canvas" />
      </div>
    </el-dialog>

    <el-dialog
      v-model="videoDialog"
      :title="videoTitle"
      width="70%"
      top="6vh"
      destroy-on-close
      @closed="closeVideoPreview"
    >
      <video ref="videoEl" controls class="video-player" />
    </el-dialog>

    <el-dialog v-model="audioDialog" :title="audioTitle" width="50%" @closed="closeAudioPreview">
      <audio ref="audioEl" controls autoplay class="audio-player" :src="audioUrl" />
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import Hls from 'hls.js'
import {
  deleteFile,
  downloadFile,
  fileVersions,
  listCaseFiles,
  mergeChunks,
  previewFile,
  uploadChunk,
  uploadFile,
} from '@/api/file'
import * as pdfjsLib from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import type { FileVO, VersionListVO } from '@/types/api'
import type { PDFDocumentProxy } from 'pdfjs-dist'
import { getToken } from '@/utils/request'

pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl

const route = useRoute()
const caseNo = route.params.caseNo as string

const rows = ref<FileVO[]>([])
const loading = ref(false)
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const uploadProgress = ref(0)

const CHUNK_SIZE = 5 * 1024 * 1024
const CHUNK_THRESHOLD = 20 * 1024 * 1024

const versionDialog = ref(false)
const versionInfo = ref<VersionListVO | null>(null)

const previewDialog = ref(false)
const previewTitle = ref('')
const previewCanvas = ref<HTMLCanvasElement>()
const previewPage = ref(1)
const previewTotal = ref(0)
let pdfDoc: PDFDocumentProxy | null = null

const videoDialog = ref(false)
const videoTitle = ref('')
const videoEl = ref<HTMLVideoElement>()
let hls: Hls | null = null

const audioDialog = ref(false)
const audioTitle = ref('')
const audioEl = ref<HTMLAudioElement>()
const audioUrl = ref('')

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
  uploadProgress.value = 0
  try {
    if (selectedFile.value.size > CHUNK_THRESHOLD) {
      await doChunkUpload(selectedFile.value)
    } else {
      const res = await uploadFile(caseNo, selectedFile.value)
      ElMessage.success(`上传成功：${res.fileName}`)
    }
    selectedFile.value = null
    load()
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

async function doChunkUpload(file: File) {
  const identifier = `${file.name}-${file.size}-${file.lastModified}`
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE)
  for (let i = 0; i < totalChunks; i++) {
    const start = i * CHUNK_SIZE
    const chunk = file.slice(start, Math.min(start + CHUNK_SIZE, file.size))
    await uploadChunk({
      file: chunk,
      caseNo,
      identifier,
      chunkIndex: i + 1,
      totalChunks,
    })
    uploadProgress.value = Math.round(((i + 1) / totalChunks) * 100)
  }
  const res = await mergeChunks({ caseNo, identifier, fileName: file.name, totalChunks })
  ElMessage.success(`分片上传完成：${res.fileName}`)
}

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

async function preview(row: FileVO) {
  if (row.mimeType.startsWith('video/')) {
    await openVideoPreview(row)
    return
  }
  const blob = await previewFile(row.id)
  if (row.mimeType.startsWith('audio/') || blob.type.startsWith('audio/')) {
    await openAudioPreview(blob, row.fileName)
    return
  }
  if (blob.type === 'application/pdf') {
    await openPdfPreview(blob, row.fileName)
    return
  }
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank')
}

async function openVideoPreview(row: FileVO) {
  videoTitle.value = row.fileName
  videoDialog.value = true
  await nextTick()
  if (!videoEl.value) return
  const base = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  const playlistUrl = `${base}/files/${row.id}/hls/playlist.m3u8`
  if (Hls.isSupported()) {
    hls = new Hls({
      xhrSetup: (xhr) => {
        const token = getToken()
        if (token) {
          xhr.setRequestHeader('Authorization', token)
        }
      },
    })
    hls.loadSource(playlistUrl)
    hls.attachMedia(videoEl.value)
  } else if (videoEl.value.canPlayType('application/vnd.apple.mpegurl')) {
    videoEl.value.src = playlistUrl
  }
}

function closeVideoPreview() {
  hls?.destroy()
  hls = null
}

async function openAudioPreview(blob: Blob, title: string) {
  audioTitle.value = title
  audioUrl.value = URL.createObjectURL(blob)
  audioDialog.value = true
  await nextTick()
  audioEl.value?.play().catch(() => {
    // 浏览器自动播放策略可能阻止，用户可手动点击播放
  })
}

function closeAudioPreview() {
  if (audioUrl.value) {
    URL.revokeObjectURL(audioUrl.value)
    audioUrl.value = ''
  }
}

async function openPdfPreview(blob: Blob, title: string) {
  const url = URL.createObjectURL(blob)
  const loadingTask = pdfjsLib.getDocument(url)
  pdfDoc = await loadingTask.promise
  previewTitle.value = title
  previewTotal.value = pdfDoc.numPages
  previewPage.value = 1
  previewDialog.value = true
  await renderPdfPage()
}

async function renderPdfPage() {
  if (!pdfDoc || !previewCanvas.value) return
  const page = await pdfDoc.getPage(previewPage.value)
  const viewport = page.getViewport({ scale: 1.2 })
  const canvas = previewCanvas.value
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  canvas.width = viewport.width
  canvas.height = viewport.height
  await page.render({ canvasContext: ctx, viewport }).promise
}

function closePreview() {
  pdfDoc?.destroy()
  pdfDoc = null
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

.pdf-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}

.pdf-page-info {
  color: #606266;
}

.pdf-canvas-wrap {
  display: flex;
  justify-content: center;
  max-height: 72vh;
  overflow: auto;
  background: #525659;
  padding: 12px;
  border-radius: 4px;
}

.pdf-canvas {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.4);
}

.video-player {
  width: 100%;
  max-height: 70vh;
  background: #000;
}

.audio-player {
  width: 100%;
}
</style>
