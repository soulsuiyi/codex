<template>
  <el-card>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="输入检索关键词（如 仲裁裁决书）"
        clearable
        style="width: 360px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" :loading="loading" @click="handleSearch">搜索</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="file.fileName" label="文件名" min-width="220" show-overflow-tooltip />
      <el-table-column prop="file.caseNo" label="案号" width="150" />
      <el-table-column label="命中片段" min-width="360">
        <template #default="{ row }">
          <span class="snippet" v-html="highlightSnippet(row.snippet)" />
        </template>
      </el-table-column>
      <el-table-column prop="file.updatedAt" label="更新时间" width="170" />
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
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { searchFiles } from '@/api/search'
import { highlightSnippet } from '@/utils/highlight'
import type { SearchResultVO } from '@/types/api'

const keyword = ref('')
const rows = ref<SearchResultVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

async function load() {
  if (!keyword.value.trim()) {
    return
  }
  loading.value = true
  try {
    const res = await searchFiles(keyword.value.trim(), page.value, size.value)
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

.snippet :deep(em) {
  color: #f56c6c;
  font-style: normal;
}
</style>
