<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { createCategory, updateCategory } from '@/api'
import { useCategories } from '@/composables/useCategories'
import Thumb from '@/components/Thumb.vue'
import ImageUpload from '@/components/ImageUpload.vue'

const { categories, reload } = useCategories()
const loading = ref(false)
const dialog = reactive({ visible: false, id: null, saving: false })
const form = reactive({ name: '', sort: 0, icon: '' })
const formRef = ref()
const rules = { name: [{ required: true, message: '请输入名称', trigger: 'blur' }] }

async function refresh() {
  loading.value = true
  await reload()
  loading.value = false
}
refresh()

function openEdit(row) {
  dialog.id = row?.id || null
  const maxSort = Math.max(0, ...categories.value.map((c) => c.sort || 0))
  Object.assign(form, row ? { name: row.name || '', sort: row.sort ?? 0, icon: row.icon || '' } : { name: '', sort: maxSort + 1, icon: '' })
  dialog.visible = true
  formRef.value?.clearValidate()
}

async function save() {
  await formRef.value.validate()
  dialog.saving = true
  const body = { name: form.name.trim(), sort: form.sort ?? 0, icon: form.icon || null }
  try {
    if (dialog.id) await updateCategory(dialog.id, body)
    else await createCategory(body)
    ElMessage.success('已保存')
    dialog.visible = false
    refresh()
  } finally {
    dialog.saving = false
  }
}

const isUrl = (s) => /^(https?:)?\/\//.test(s || '') || (s || '').startsWith('/')
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <span class="muted">排序数字越小越靠前；小程序首页和商家上架时使用这些分类。</span>
      <div>
        <el-button :icon="Refresh" @click="refresh">刷新</el-button>
        <el-button type="success" :icon="Plus" @click="openEdit(null)">新增分类</el-button>
      </div>
    </div>
    <el-table v-loading="loading" :data="categories" row-key="id" style="max-width: 760px">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="图标" width="90">
        <template #default="{ row }">
          <Thumb v-if="isUrl(row.icon)" :src="row.icon" :size="36" />
          <span v-else>{{ row.icon || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column label="排序" width="100">
        <template #default="{ row }">{{ row.sort ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑分类' : '新增分类'" width="440px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="70px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="20" placeholder="如：化肥" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="图标">
          <ImageUpload v-model="form.icon" :width="80" :height="80" />
          <div class="muted" style="width: 100%">建议正方形 PNG，可不传</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
