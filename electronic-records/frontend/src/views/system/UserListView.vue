<template>
  <el-card>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按用户名/姓名/手机号搜索"
        clearable
        style="width: 260px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button type="success" @click="openCreate">新建用户</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="realName" label="姓名" min-width="100" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
      <el-table-column label="角色" min-width="180">
        <template #default="{ row }">
          <el-tag v-for="code in row.roleCodes || []" :key="code" size="small" style="margin-right: 4px">
            {{ code }}
          </el-tag>
        </template>
      </el-table-column>
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
      <el-table-column prop="lastLoginTime" label="最后登录" width="170" />
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="openResetPassword(row)">重置密码</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑用户' : '新建用户'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!editing" placeholder="登录账号" />
        </el-form-item>
        <el-form-item v-if="!editing" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="初始密码" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="邮箱" />
        </el-form-item>
        <el-form-item label="部门ID">
          <el-input-number v-model="form.deptId" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple placeholder="选择角色" style="width: 100%">
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="`${role.roleName}（${role.roleCode}）`"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialog" title="重置密码" width="420px">
      <el-form label-width="90px">
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password placeholder="输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialog = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="submitPassword">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createUser, listRoles, listUsers, resetUserPassword, updateUser, updateUserStatus } from '@/api/system'
import type { RoleVO, UserVO } from '@/types/api'

const rows = ref<UserVO[]>([])
const roles = ref<RoleVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref<UserVO | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const passwordDialog = ref(false)
const resetting = ref(false)
const passwordTarget = ref<UserVO | null>(null)
const newPassword = ref('')

const form = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
  deptId: undefined as number | undefined,
  status: 1,
  roleIds: [] as number[],
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const res = await listUsers(page.value, size.value, keyword.value || undefined)
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  roles.value = await listRoles()
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

function openCreate() {
  editing.value = null
  Object.assign(form, {
    username: '',
    password: '',
    realName: '',
    phone: '',
    email: '',
    deptId: undefined,
    status: 1,
    roleIds: [],
  })
  dialogVisible.value = true
}

function openEdit(row: UserVO) {
  editing.value = row
  Object.assign(form, {
    username: row.username,
    password: '',
    realName: row.realName || '',
    phone: row.phone || '',
    email: row.email || '',
    deptId: row.deptId,
    status: row.status,
    roleIds: [...(row.roleIds || [])],
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editing.value) {
      await updateUser(editing.value.id, {
        realName: form.realName,
        phone: form.phone || undefined,
        email: form.email || undefined,
        deptId: form.deptId,
        status: form.status,
        roleIds: form.roleIds,
      })
      ElMessage.success('用户已更新')
    } else {
      await createUser({
        username: form.username,
        password: form.password,
        realName: form.realName,
        phone: form.phone || undefined,
        email: form.email || undefined,
        deptId: form.deptId,
        status: form.status,
        roleIds: form.roleIds,
      })
      ElMessage.success('用户已创建')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: UserVO, val: number) {
  try {
    await updateUserStatus(row.id, val)
    row.status = val
    ElMessage.success(val === 1 ? '已启用' : '已禁用')
  } catch {
    load()
  }
}

function openResetPassword(row: UserVO) {
  passwordTarget.value = row
  newPassword.value = ''
  passwordDialog.value = true
}

async function submitPassword() {
  if (!passwordTarget.value || !newPassword.value) {
    ElMessage.warning('请输入新密码')
    return
  }
  resetting.value = true
  try {
    await resetUserPassword(passwordTarget.value.id, newPassword.value)
    ElMessage.success('密码已重置')
    passwordDialog.value = false
  } finally {
    resetting.value = false
  }
}

onMounted(() => {
  load()
  loadRoles()
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
