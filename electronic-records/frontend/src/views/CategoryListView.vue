<template>
  <el-card>
    <div class="toolbar">
      <el-button type="success" @click="openCreate(null)">新建分类</el-button>
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
      <el-table-column prop="name" label="分类名称" min-width="260" />
      <el-table-column label="层级" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.level === 1 ? 'primary' : 'info'">L{{ row.level }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openCreate(row)">新增子分类</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑分类' : '新建分类'" width="440px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="父级分类">
          <el-select v-model="form.parentId" clearable placeholder="根分类" style="width: 100%">
            <el-option
              v-for="item in parentOptions"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
            <el-option label="根分类" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" placeholder="如 仲裁案件" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
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
import { categoryTree, createCategory, deleteCategory, updateCategory } from '@/api/case'
import type { CategoryVO } from '@/types/api'

const tree = ref<CategoryVO[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref<CategoryVO | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  parentId: 0,
  name: '',
  sortOrder: 0,
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
}

function flattenCategories(nodes: CategoryVO[], depth = 0): { id: number; label: string }[] {
  const result: { id: number; label: string }[] = []
  for (const node of nodes) {
    result.push({ id: node.id, label: `${'　'.repeat(depth)}${node.name}` })
    if (node.children?.length) {
      result.push(...flattenCategories(node.children, depth + 1))
    }
  }
  return result
}

const parentOptions = computed(() =>
  flattenCategories(tree.value).filter((item) => item.id !== editing.value?.id),
)

async function load() {
  loading.value = true
  try {
    tree.value = await categoryTree()
  } finally {
    loading.value = false
  }
}

function openCreate(parent: CategoryVO | null) {
  editing.value = null
  Object.assign(form, { parentId: parent?.id ?? 0, name: '', sortOrder: 0 })
  dialogVisible.value = true
}

function openEdit(row: CategoryVO) {
  editing.value = row
  Object.assign(form, { parentId: row.parentId, name: row.name, sortOrder: row.sortOrder ?? 0 })
  dialogVisible.value = true
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editing.value) {
      await updateCategory(editing.value.id, {
        name: form.name,
        sortOrder: form.sortOrder,
      })
      ElMessage.success('分类已更新')
    } else {
      await createCategory({
        parentId: form.parentId,
        name: form.name,
        sortOrder: form.sortOrder,
      })
      ElMessage.success('分类已创建')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row: CategoryVO) {
  await ElMessageBox.confirm(
    `确认删除分类「${row.name}」？存在子分类或被案件引用时将无法删除。`,
    '提示',
    { type: 'warning' },
  )
  await deleteCategory(row.id)
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
</style>
