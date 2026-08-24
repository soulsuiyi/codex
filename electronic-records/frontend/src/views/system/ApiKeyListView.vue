<template>
  <el-card>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按应用名/Access Key 搜索"
        clearable
        style="width: 260px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button type="success" @click="openCreate">新建密钥</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="appName" label="应用名称" min-width="140" />
      <el-table-column prop="apiKey" label="Access Key" min-width="220" />
      <el-table-column prop="ipWhitelist" label="IP 白名单" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status"
            :active-value="1"
            :inactive-value="0"
            @change="(val: number) => toggleStatus(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="expireTime" label="过期时间" width="170" />
      <el-table-column prop="createdAt" label="创建时间" width="170" />
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑密钥' : '新建密钥'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="应用名称" prop="appName">
          <el-input v-model="form.appName" placeholder="如 扫描矫正软件" />
        </el-form-item>
        <el-form-item label="IP 白名单">
          <el-input v-model="form.ipWhitelist" placeholder="逗号分隔，留空表示不限制" />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="form.expireTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空表示长期有效"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createdDialog" title="密钥创建成功" width="560px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px">
        Secret 仅在本次显示，请立即保存，关闭后将无法再次查看。
      </el-alert>
      <el-descriptions v-if="createdKey" :column="1" border>
        <el-descriptions-item label="应用名称">{{ createdKey.appName }}</el-descriptions-item>
        <el-descriptions-item label="Access Key">
          <el-text type="primary" copyable>{{ createdKey.apiKey }}</el-text>
        </el-descriptions-item>
        <el-descriptions-item label="Secret Key">
          <el-text type="danger" copyable>{{ createdKey.apiSecret }}</el-text>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="createdDialog = false">我已保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createApiKey, deleteApiKey, listApiKeys, updateApiKey } from '@/api/system'
import type { ApiKeyVO } from '@/types/api'

const rows = ref<ApiKeyVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref<ApiKeyVO | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const createdDialog = ref(false)
const createdKey = ref<ApiKeyVO | null>(null)

const form = reactive({
  appName: '',
  ipWhitelist: '',
  expireTime: '',
  status: 1,
})

const rules: FormRules = {
  appName: [{ required: true, message: '请输入应用名称', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const res = await listApiKeys(page.value, size.value, keyword.value || undefined)
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
  Object.assign(form, { appName: '', ipWhitelist: '', expireTime: '', status: 1 })
  dialogVisible.value = true
}

function openEdit(row: ApiKeyVO) {
  editing.value = row
  Object.assign(form, {
    appName: row.appName,
    ipWhitelist: row.ipWhitelist || '',
    expireTime: row.expireTime || '',
    status: row.status,
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editing.value) {
      await updateApiKey(editing.value.id, {
        appName: form.appName,
        ipWhitelist: form.ipWhitelist || undefined,
        status: form.status,
        expireTime: form.expireTime || undefined,
      })
      ElMessage.success('密钥已更新')
    } else {
      createdKey.value = await createApiKey({
        appName: form.appName,
        ipWhitelist: form.ipWhitelist || undefined,
        expireTime: form.expireTime || undefined,
      })
      createdDialog.value = true
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: ApiKeyVO, val: number) {
  try {
    await updateApiKey(row.id, { status: val })
    row.status = val
    ElMessage.success(val === 1 ? '已启用' : '已禁用')
  } catch {
    load()
  }
}

async function remove(row: ApiKeyVO) {
  await ElMessageBox.confirm(
    `确认删除密钥「${row.appName}」？删除后外部系统将无法调用开放接口。`,
    '提示',
    { type: 'warning' },
  )
  await deleteApiKey(row.id)
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
