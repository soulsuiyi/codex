<template>
  <el-card>
    <div class="toolbar">
      <el-button type="success" @click="openApply">申请借阅</el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="caseNo" label="案号" min-width="140" />
      <el-table-column prop="reason" label="借阅事由" min-width="180" show-overflow-tooltip />
      <el-table-column label="下载" width="80">
        <template #default="{ row }">{{ row.needDownload ? '允许' : '仅预览' }}</template>
      </el-table-column>
      <el-table-column prop="expireTime" label="到期时间" width="170" />
      <el-table-column label="状态" width="140">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="申请时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'PENDING_SECRETARY' || row.status === 'PENDING_ADMIN'"
            link
            type="primary"
            @click="openApprove(row)"
          >
            审批
          </el-button>
          <el-button v-if="row.status === 'ACTIVE'" link type="primary" @click="getToken(row)">
            Token
          </el-button>
          <el-button v-if="row.status === 'ACTIVE'" link type="warning" @click="doReturn(row)">
            归还
          </el-button>
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

    <el-dialog v-model="applyDialog" title="申请借阅" width="560px">
      <el-form ref="applyFormRef" :model="applyForm" :rules="applyRules" label-width="100px">
        <el-form-item label="案件案号" prop="caseNo">
          <el-input v-model="applyForm.caseNo" placeholder="请输入案号" style="width: 260px" />
          <el-button style="margin-left: 8px" @click="loadApplyFiles">加载文件</el-button>
        </el-form-item>
        <el-form-item label="借阅文件" prop="fileIds">
          <el-select
            v-model="applyForm.fileIds"
            multiple
            filterable
            placeholder="请选择借阅文件"
            style="width: 100%"
          >
            <el-option
              v-for="file in applyFiles"
              :key="file.id"
              :label="`${file.fileName}（${file.stage}）`"
              :value="file.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="借阅事由" prop="reason">
          <el-input v-model="applyForm.reason" type="textarea" :rows="3" placeholder="请说明借阅事由" />
        </el-form-item>
        <el-form-item label="允许下载">
          <el-switch v-model="applyForm.needDownload" />
        </el-form-item>
        <el-form-item label="到期时间">
          <el-date-picker
            v-model="applyForm.expireTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="默认由系统策略决定"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialog = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="approveDialog" title="借阅审批" width="440px">
      <el-form label-width="90px">
        <el-form-item label="审批结果">
          <el-radio-group v-model="approveForm.result">
            <el-radio value="APPROVED">通过</el-radio>
            <el-radio value="REJECTED">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input v-model="approveForm.comment" type="textarea" :rows="3" placeholder="审批意见（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialog = false">取消</el-button>
        <el-button type="primary" :loading="approving" @click="submitApprove">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tokenDialog" title="借阅授权 Token" width="560px">
      <el-descriptions v-if="tokenInfo" :column="1" border>
        <el-descriptions-item label="Token">
          <el-text type="primary" copyable>{{ tokenInfo.tokenValue }}</el-text>
        </el-descriptions-item>
        <el-descriptions-item label="到期时间">{{ tokenInfo.expireTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="允许下载">{{ tokenInfo.allowDownload ? '是' : '否' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listCaseFiles } from '@/api/file'
import { applyBorrow, approveBorrow, borrowToken, myBorrows, returnBorrow } from '@/api/borrow'
import type {
  ApprovalResult,
  BorrowApplyVO,
  BorrowApplyRequest,
  BorrowStatus,
  BorrowTokenVO,
  FileVO,
} from '@/types/api'

const rows = ref<BorrowApplyVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const applyDialog = ref(false)
const applying = ref(false)
const applyFormRef = ref<FormInstance>()
const applyFiles = ref<FileVO[]>([])
const applyForm = reactive<BorrowApplyRequest>({
  caseNo: '',
  fileIds: [],
  reason: '',
  needDownload: false,
  expireTime: '',
})

const applyRules: FormRules = {
  caseNo: [{ required: true, message: '请输入案件案号', trigger: 'blur' }],
  fileIds: [{ required: true, type: 'array', min: 1, message: '请至少选择一个文件', trigger: 'change' }],
  reason: [{ required: true, message: '请说明借阅事由', trigger: 'blur' }],
}

const approveDialog = ref(false)
const approving = ref(false)
const approveTarget = ref<BorrowApplyVO | null>(null)
const approveForm = reactive<{ result: ApprovalResult; comment: string }>({
  result: 'APPROVED',
  comment: '',
})

const tokenDialog = ref(false)
const tokenInfo = ref<BorrowTokenVO | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await myBorrows(page.value, size.value)
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  load()
}

function statusLabel(status: BorrowStatus) {
  return {
    PENDING_SECRETARY: '待秘书审批',
    PENDING_ADMIN: '待管理员审批',
    ACTIVE: '借阅中',
    REJECTED: '已驳回',
    EXPIRED: '已到期',
    RETURNED: '已归还',
  }[status]
}

function statusTag(status: BorrowStatus) {
  return {
    PENDING_SECRETARY: 'warning',
    PENDING_ADMIN: 'warning',
    ACTIVE: 'success',
    REJECTED: 'danger',
    EXPIRED: 'info',
    RETURNED: 'info',
  }[status] as 'warning' | 'success' | 'danger' | 'info'
}

function openApply() {
  Object.assign(applyForm, { caseNo: '', fileIds: [], reason: '', needDownload: false, expireTime: '' })
  applyFiles.value = []
  applyDialog.value = true
}

async function loadApplyFiles() {
  if (!applyForm.caseNo) {
    ElMessage.warning('请先输入案件案号')
    return
  }
  applyFiles.value = await listCaseFiles(applyForm.caseNo)
  if (!applyFiles.value.length) {
    ElMessage.info('该案件暂无文件')
  }
}

async function submitApply() {
  await applyFormRef.value?.validate()
  applying.value = true
  try {
    await applyBorrow({
      ...applyForm,
      expireTime: applyForm.expireTime || undefined,
      reason: applyForm.reason || undefined,
    })
    ElMessage.success('借阅申请已提交')
    applyDialog.value = false
    load()
  } finally {
    applying.value = false
  }
}

function openApprove(row: BorrowApplyVO) {
  approveTarget.value = row
  approveForm.result = 'APPROVED'
  approveForm.comment = ''
  approveDialog.value = true
}

async function submitApprove() {
  if (!approveTarget.value) return
  approving.value = true
  try {
    await approveBorrow(approveTarget.value.id, {
      result: approveForm.result,
      comment: approveForm.comment || undefined,
    })
    ElMessage.success('审批完成')
    approveDialog.value = false
    load()
  } finally {
    approving.value = false
  }
}

async function getToken(row: BorrowApplyVO) {
  tokenInfo.value = await borrowToken(row.id)
  tokenDialog.value = true
}

async function doReturn(row: BorrowApplyVO) {
  await ElMessageBox.confirm('确认归还该借阅申请并撤销授权？', '提示', { type: 'warning' })
  await returnBorrow(row.id)
  ElMessage.success('已归还')
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
