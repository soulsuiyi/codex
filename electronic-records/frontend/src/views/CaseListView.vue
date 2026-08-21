<template>
  <el-card>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按案号/案件名称搜索"
        clearable
        style="width: 260px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button type="success" @click="openCreate">新建案件</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="caseNo" label="案号" min-width="140" />
      <el-table-column prop="caseName" label="案件名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="caseType" label="案件类型" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goFiles(row)">文件</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑案件' : '新建案件'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="案号" prop="caseNo">
          <el-input v-model="form.caseNo" :disabled="!!editing" placeholder="如 2026-001" />
        </el-form-item>
        <el-form-item label="案件名称" prop="caseName">
          <el-input v-model="form.caseName" placeholder="请输入案件名称" />
        </el-form-item>
        <el-form-item label="案件分类">
          <el-select v-model="form.categoryId" clearable placeholder="请选择分类" style="width: 100%">
            <el-option
              v-for="item in categoryOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="案件类型">
          <el-input v-model="form.caseType" placeholder="如 仲裁案件" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="备注信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { categoryTree, createCase, listCases, updateCase } from '@/api/case'
import type { CaseStatus, CaseVO, CategoryVO } from '@/types/api'

const router = useRouter()

const rows = ref<CaseVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref<CaseVO | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const categoryOptions = ref<CategoryVO[]>([])

const form = reactive({
  caseNo: '',
  caseName: '',
  categoryId: undefined as number | undefined,
  caseType: '',
  remark: '',
})

const rules: FormRules = {
  caseNo: [{ required: true, message: '请输入案号', trigger: 'blur' }],
  caseName: [{ required: true, message: '请输入案件名称', trigger: 'blur' }],
}

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

async function loadCategories() {
  categoryOptions.value = await categoryTree()
}

function statusLabel(status: CaseStatus) {
  return { ACTIVE: '办理中', CLOSED: '已结', ARCHIVED: '已归档' }[status] || status
}

function statusTag(status: CaseStatus) {
  return { ACTIVE: 'primary', CLOSED: 'info', ARCHIVED: 'success' }[status] || 'info'
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

function goFiles(row: CaseVO) {
  router.push(`/cases/${row.caseNo}/files`)
}

function openCreate() {
  editing.value = null
  Object.assign(form, { caseNo: '', caseName: '', categoryId: undefined, caseType: '', remark: '' })
  dialogVisible.value = true
}

function openEdit(row: CaseVO) {
  editing.value = row
  Object.assign(form, {
    caseNo: row.caseNo,
    caseName: row.caseName,
    categoryId: row.categoryId,
    caseType: row.caseType || '',
    remark: row.remark || '',
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editing.value) {
      await updateCase(editing.value.id, {
        caseName: form.caseName,
        categoryId: form.categoryId,
        caseType: form.caseType || undefined,
        remark: form.remark || undefined,
      })
      ElMessage.success('案件已更新')
    } else {
      await createCase({
        caseNo: form.caseNo,
        caseName: form.caseName,
        categoryId: form.categoryId,
        caseType: form.caseType || undefined,
        remark: form.remark || undefined,
      })
      ElMessage.success('案件已创建')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  load()
  loadCategories()
})
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
