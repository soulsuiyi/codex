<template>
  <el-card>
    <div class="toolbar">
      <el-button type="success" @click="openCreate(null)">新建菜单</el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="tree"
      row-key="id"
      :tree-props="{ children: 'children' }"
      border
      default-expand-all
    >
      <el-table-column prop="menuName" label="菜单名称" min-width="220" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.menuType)">{{ typeLabel(row.menuType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="perms" label="权限标识" min-width="160" show-overflow-tooltip />
      <el-table-column prop="path" label="路由路径" min-width="140" />
      <el-table-column prop="component" label="组件" min-width="170" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" width="70" />
      <el-table-column label="可见" width="80">
        <template #default="{ row }">{{ row.visible === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openCreate(row)">新增子菜单</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑菜单' : '新建菜单'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="父级菜单">
          <el-select v-model="form.parentId" clearable placeholder="0 表示根节点" style="width: 100%">
            <el-option v-for="item in parentOptions" :key="item.id" :label="item.label" :value="item.id" />
            <el-option label="根节点" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="form.menuName" placeholder="如 系统管理" />
        </el-form-item>
        <el-form-item label="菜单类型" prop="menuType">
          <el-radio-group v-model="form.menuType">
            <el-radio value="M">目录</el-radio>
            <el-radio value="C">菜单</el-radio>
            <el-radio value="B">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="form.perms" placeholder="如 system:user:list" />
        </el-form-item>
        <el-form-item label="路由路径">
          <el-input v-model="form.path" placeholder="如 /system/users" />
        </el-form-item>
        <el-form-item label="组件路径">
          <el-input v-model="form.component" placeholder="如 system/UserListView" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="可见">
          <el-switch v-model="form.visible" :active-value="1" :inactive-value="0" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createMenu, deleteMenu, menuTree, updateMenu } from '@/api/system'
import type { MenuType, MenuVO } from '@/types/api'

const tree = ref<MenuVO[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref<MenuVO | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  parentId: 0,
  menuName: '',
  menuType: 'C' as MenuType,
  perms: '',
  path: '',
  component: '',
  sortOrder: 0,
  visible: 1,
})

const rules: FormRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
}

function flattenMenus(nodes: MenuVO[], depth = 0): { id: number; label: string }[] {
  const result: { id: number; label: string }[] = []
  for (const node of nodes) {
    result.push({ id: node.id, label: `${'　'.repeat(depth)}${node.menuName}` })
    if (node.children?.length) {
      result.push(...flattenMenus(node.children, depth + 1))
    }
  }
  return result
}

const parentOptions = computed(() =>
  flattenMenus(tree.value).filter((item) => item.id !== editing.value?.id),
)

async function load() {
  loading.value = true
  try {
    tree.value = await menuTree()
  } finally {
    loading.value = false
  }
}

function openCreate(parent: MenuVO | null) {
  editing.value = null
  Object.assign(form, {
    parentId: parent?.id ?? 0,
    menuName: '',
    menuType: 'C',
    perms: '',
    path: '',
    component: '',
    sortOrder: 0,
    visible: 1,
  })
  dialogVisible.value = true
}

function openEdit(row: MenuVO) {
  editing.value = row
  Object.assign(form, {
    parentId: row.parentId,
    menuName: row.menuName,
    menuType: row.menuType,
    perms: row.perms || '',
    path: row.path || '',
    component: row.component || '',
    sortOrder: row.sortOrder ?? 0,
    visible: row.visible ?? 1,
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = {
      parentId: form.parentId,
      menuName: form.menuName,
      menuType: form.menuType,
      perms: form.perms || undefined,
      path: form.path || undefined,
      component: form.component || undefined,
      sortOrder: form.sortOrder,
      visible: form.visible,
    }
    if (editing.value) {
      await updateMenu(editing.value.id, payload)
      ElMessage.success('菜单已更新')
    } else {
      await createMenu(payload)
      ElMessage.success('菜单已创建')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row: MenuVO) {
  await ElMessageBox.confirm(`确认删除菜单「${row.menuName}」？`, '提示', { type: 'warning' })
  await deleteMenu(row.id)
  ElMessage.success('已删除')
  load()
}

function typeLabel(type: MenuType) {
  return { M: '目录', C: '菜单', B: '按钮' }[type]
}

function typeTag(type: MenuType) {
  return { M: 'warning', C: 'success', B: 'info' }[type] as 'warning' | 'success' | 'info'
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
