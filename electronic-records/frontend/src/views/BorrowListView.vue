<template>
  <el-card>
    <el-tabs v-model="activeTab" @tab-change="load">
      <el-tab-pane label="我的申请" name="mine">
        <div class="toolbar">
          <el-button type="success" @click="openApply">申请借阅</el-button>
          <el-button @click="load">刷新</el-button>
        </div>

        <el-table v-loading="loadingMine" :data="mineRows" border stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="caseNo" label="案号" min-width="140" />
          <el-table-column prop="reason" label="借阅事由" min-width="180" show-overflow-tooltip />
          <el-table-column label="文件数" width="80">
            <template #default="{ row }">{{ (row.fileIds || []).length }}</template>
          </el-table-column>
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
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-if="isPending(row)" link type="primary" @click="openApprove(row)">审批</el-button>
              <el-button v-if="row.status === 'ACTIVE'" link type="primary" @click="getToken(row)">Token</el-button>
              <el-button v-if="row.status === 'ACTIVE'" link type="success" @click="openBorrowFiles(row)">
                借阅文件
              </el-button>
              <el-button v-if="row.status === 'ACTIVE'" link type="warning" @click="doReturn(row)">归还</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination">
          <el-pagination
            :current-page="minePage"
            :page-size="mineSize"
            :total="mineTotal"
            layout="total, prev, pager, next, jumper"
            @current-change="onMinePageChange"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="待我审批" name="pending">
        <div class="toolbar">
          <el-button @click="load">刷新</el-button>
        </div>

        <el-table v-loading="loadingPending" :data="pendingRows" border stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="caseNo" label="案号" min-width="140" />
          <el-table-column prop="applicantName" label="申请人" width="110" />
          <el-table-column prop="reason" label="借阅事由" min-width="180" show-overflow-tooltip />
          <el-table-column label="文件数" width="80">
            <template #default="{ row }">{{ (row.fileIds || []).length }}</template>
          </el-table-column>
          <el-table-column prop="expireTime" label="期望归还" width="170" />
          <el-table-column label="状态" width="140">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="申请时间" width="170" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button link type="primary" @click="openApprove(row)">审批</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination">
          <el-pagination
            :current-page="pendingPage"
            :page-size="pendingSize"
            :total="pendingTotal"
            layout="total, prev, pager, next, jumper"
            @current-change="onPendingPageChange"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

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
      <template v-if="approveTarget">
        <el-descriptions :column="2" border style="margin-bottom: 12px">
          <el-descriptions-item label="案号">{{ approveTarget.caseNo }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ approveTarget.applicantName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="借阅事由" :span="2">{{ approveTarget.reason }}</el-descriptions-item>
        </el-descriptions>
      </template>
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

    <el-dialog v-model="detailDialog" title="借阅申请详情" width="720px">
      <template v-if="detailInfo">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="案号">{{ detailInfo.apply.caseNo }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detailInfo.apply.applicantName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="借阅事由" :span="2">{{ detailInfo.apply.reason }}</el-descriptions-item>
          <el-descriptions-item label="到期时间">{{ detailInfo.apply.expireTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="允许下载">
            {{ detailInfo.apply.needDownload ? '是' : '否' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">借阅文件</div>
        <el-table :data="detailInfo.files" border size="small">
          <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
          <el-table-column prop="mimeType" label="类型" width="150" />
          <el-table-column label="阶段" width="90">
            <template #default="{ row }">
              <el-tag size="small" type="success">{{ row.stage }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="detail-section">审批记录</div>
        <el-table v-if="detailInfo.approvals.length" :data="detailInfo.approvals" border size="small">
          <el-table-column label="环节" width="150">
            <template #default="{ row }">{{ approvalStepLabel(row.approvalStep) }}</template>
          </el-table-column>
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.result === 'APPROVED' ? 'success' : 'danger'">
                {{ row.result === 'APPROVED' ? '通过' : '驳回' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="approverName" label="审批人" width="110" />
          <el-table-column prop="comment" label="意见" min-width="160" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="时间" width="170" />
        </el-table>
        <el-empty v-else description="暂无审批记录" :image-size="60" />
      </template>
    </el-dialog>

    <el-dialog v-model="borrowFilesDialog" title="借阅文件" width="640px">
      <el-alert
        v-if="borrowTokenValue"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      >
        借阅授权已生效，请在到期时间前完成查看/下载。
      </el-alert>
      <el-table :data="borrowFiles" border size="small">
        <el-table-column prop="fileName" label="文件名" min-width="240" show-overflow-tooltip />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="previewBorrowFile(row)">预览</el-button>
            <el-button link type="primary" @click="downloadBorrowFile(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listCaseFiles } from '@/api/file'
import {
  applyBorrow,
  approveBorrow,
  borrowDetail,
  borrowDownload,
  borrowToken,
  myBorrows,
  pendingBorrows,
  returnBorrow,
} from '@/api/borrow'
import type {
  ApprovalResult,
  BorrowApplyVO,
  BorrowApplyRequest,
  BorrowDetailVO,
  BorrowStatus,
  BorrowTokenVO,
  FileVO,
} from '@/types/api'

const activeTab = ref('mine')

const mineRows = ref<BorrowApplyVO[]>([])
const mineTotal = ref(0)
const minePage = ref(1)
const mineSize = ref(10)
const loadingMine = ref(false)

const pendingRows = ref<BorrowApplyVO[]>([])
const pendingTotal = ref(0)
const pendingPage = ref(1)
const pendingSize = ref(10)
const loadingPending = ref(false)

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

const detailDialog = ref(false)
const detailInfo = ref<BorrowDetailVO | null>(null)

const borrowFilesDialog = ref(false)
const borrowFilesRow = ref<BorrowApplyVO | null>(null)
const borrowTokenValue = ref('')
const borrowFiles = ref<FileVO[]>([])

async function load() {
  if (activeTab.value === 'pending') {
    await loadPending()
  } else {
    await loadMine()
  }
}

async function loadMine() {
  loadingMine.value = true
  try {
    const res = await myBorrows(minePage.value, mineSize.value)
    mineRows.value = res.records
    mineTotal.value = res.total
  } finally {
    loadingMine.value = false
  }
}

async function loadPending() {
  loadingPending.value = true
  try {
    const res = await pendingBorrows(pendingPage.value, pendingSize.value)
    pendingRows.value = res.records
    pendingTotal.value = res.total
  } finally {
    loadingPending.value = false
  }
}

function onMinePageChange(p: number) {
  minePage.value = p
  loadMine()
}

function onPendingPageChange(p: number) {
  pendingPage.value = p
  loadPending()
}

function isPending(row: BorrowApplyVO) {
  return row.status === 'PENDING_SECRETARY' || row.status === 'PENDING_ADMIN'
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
    loadMine()
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

async function openDetail(row: BorrowApplyVO) {
  detailInfo.value = await borrowDetail(row.id)
  detailDialog.value = true
}

function approvalStepLabel(step: string) {
  return step === 'SECRETARY' ? '仲裁秘书初审' : '档案管理员终审'
}

async function openBorrowFiles(row: BorrowApplyVO) {
  const token = await borrowToken(row.id)
  const detail = await borrowDetail(row.id)
  borrowFilesRow.value = row
  borrowTokenValue.value = token.tokenValue
  borrowFiles.value = detail.files
  borrowFilesDialog.value = true
}

async function previewBorrowFile(file: FileVO) {
  if (!borrowFilesRow.value) return
  const res = await borrowDownload(borrowFilesRow.value.id, {
    tokenValue: borrowTokenValue.value,
    fileId: file.id,
  })
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
  window.open(url, '_blank')
}

async function downloadBorrowFile(file: FileVO) {
  if (!borrowFilesRow.value) return
  const res = await borrowDownload(borrowFilesRow.value.id, {
    tokenValue: borrowTokenValue.value,
    fileId: file.id,
  })
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
  a.download = file.fileName
  a.click()
  URL.revokeObjectURL(url)
}

async function doReturn(row: BorrowApplyVO) {
  await ElMessageBox.confirm('确认归还该借阅申请并撤销授权？', '提示', { type: 'warning' })
  await returnBorrow(row.id)
  ElMessage.success('已归还')
  loadMine()
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

.detail-section {
  margin: 14px 0 8px;
  font-weight: 600;
}
</style>
