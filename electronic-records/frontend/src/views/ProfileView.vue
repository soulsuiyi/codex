<template>
  <el-card style="max-width: 640px">
    <template #header>个人资料</template>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="用户名">
        <el-input :model-value="profile?.username" disabled />
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
      <el-form-item label="最近登录">
        <el-input :model-value="profile?.lastLoginTime || '-'" disabled />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存资料</el-button>
        <el-button type="warning" @click="openPasswordDialog">修改密码</el-button>
      </el-form-item>
    </el-form>

    <el-dialog v-model="passwordDialog" title="修改密码" width="440px">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="输入原密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="输入新密码" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirm">
          <el-input v-model="pwdForm.confirm" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialog = false">取消</el-button>
        <el-button type="primary" :loading="changing" @click="changePwd">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { changePassword, getProfile, updateProfile } from '@/api/auth'
import type { ProfileVO } from '@/types/api'

const profile = ref<ProfileVO | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  realName: '',
  phone: '',
  email: '',
  deptId: undefined as number | undefined,
})

const rules: FormRules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
}

const passwordDialog = ref(false)
const changing = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirm: '',
})

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码至少 6 位', trigger: 'blur' },
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function load() {
  profile.value = await getProfile()
  Object.assign(form, {
    realName: profile.value.realName || '',
    phone: profile.value.phone || '',
    email: profile.value.email || '',
    deptId: profile.value.deptId,
  })
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    await updateProfile({
      realName: form.realName,
      phone: form.phone || undefined,
      email: form.email || undefined,
      deptId: form.deptId,
    })
    ElMessage.success('资料已更新')
    load()
  } finally {
    saving.value = false
  }
}

function openPasswordDialog() {
  Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirm: '' })
  passwordDialog.value = true
}

async function changePwd() {
  await pwdFormRef.value?.validate()
  changing.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    ElMessage.success('密码已修改，下次登录生效')
    passwordDialog.value = false
  } finally {
    changing.value = false
  }
}

onMounted(load)
</script>
