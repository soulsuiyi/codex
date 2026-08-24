<template>
  <el-card>
    <div class="toolbar">
      <el-input
        v-model="dictType"
        placeholder="按字典类型过滤"
        clearable
        style="width: 200px"
        @keyup.enter="handleSearch"
      />
      <el-input
        v-model="keyword"
        placeholder="按编码/标签/值搜索"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button type="success" @click="openCreate">新建字典项</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="dictType" label="字典类型" width="180" />
      <el-table-column prop="dictCode" label="字典编码" width="140" />
      <el-table-column prop="dictLabel" label="显示标签" width="140" />
      <el-table-column prop="dictValue" label="字典值" width="140" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑字典项' : '新建字典项'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="字典类型" prop="dictType">
          <el-input v-model="form.dictType" placeholder="如 case_status" />
        </el-form-item>
        <el-form-item label="字典编码" prop="dictCode">
          <el-input v-model="form.dictCode" placeholder="如 ACTIVE" />
        </el-form-item>
        <el-form-item label="显示标签" prop="dictLabel">
          <el-input v-model="form.dictLabel" placeholder="如 办理中" />
        </el-form-item>
        <el-form-item label="字典值" prop="dictValue">
          <el-input v-model="form.dictValue" placeholder="如 ACTIVE" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
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
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createDict, deleteDict, listDicts, updateDict } from '@/api/system'
import type { DictVO } from '@/types/api'

const rows = ref<DictVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const dictType = ref('')
const keyword = ref('')
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref<DictVO | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  dictType: '',
  dictCode: '',
  dictLabel: '',
  dictValue: '',
  sortOrder: 0,
  remark: '',
})

const rules: FormRules = {
  dictType: [{ required: true, message: '请输入字典类型', trigger: 'blur' }],
  dictCode: [{ required: true, message: '请输入字典编码', trigger: 'blur' }],
  dictLabel: [{ required: true, message: '请输入显示标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典值', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const res = await listDicts(page.value, size.value, dictType.value || undefined, keyword.value || undefined)
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

function openCreate() {
  editing.value = null
  Object.assign(form, {
    dictType: dictType.value || '',
    dictCode: '',
    dictLabel: '',
    dictValue: '',
    sortOrder: 0,
    remark: '',
  })
  dialogVisible.value = true
}

function openEdit(row: DictVO) {
  editing.value = row
  Object.assign(form, {
    dictType: row.dictType,
    dictCode: row.dictCode,
    dictLabel: row.dictLabel,
    dictValue: row.dictValue,
    sortOrder: row.sortOrder ?? 0,
    remark: row.remark || '',
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = {
      dictType: form.dictType,
      dictCode: form.dictCode,
      dictLabel: form.dictLabel,
      dictValue: form.dictValue,
      sortOrder: form.sortOrder,
      remark: form.remark || undefined,
    }
    if (editing.value) {
      await updateDict(editing.value.id, payload)
      ElMessage.success('字典项已更新')
    } else {
      await createDict(payload)
      ElMessage.success('字典项已创建')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row: DictVO) {
  await ElMessageBox.confirm(
    `确认删除字典项「${row.dictLabel}（${row.dictType}）」？`,
    '提示',
    { type: 'warning' },
  )
  await deleteDict(row.id)
  ElMessage.success('已删除')
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
