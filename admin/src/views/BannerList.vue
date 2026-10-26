<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { listBanners, createBanner, updateBanner, deleteBanner } from '@/api'
import { useRegions } from '@/composables/useRegions'
import { toList, text } from '@/utils/format'
import { confirmAction } from '@/utils/prompt'
import Thumb from '@/components/Thumb.vue'
import ImageUpload from '@/components/ImageUpload.vue'

const { regions, load: loadRegions } = useRegions()
const regionName = computed(() => Object.fromEntries(regions.value.map((r) => [r.id, r.name])))

const loading = ref(false)
const banners = ref([])
async function load() {
  loading.value = true
  try {
    banners.value = toList(await listBanners())
      .slice()
      .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.id - b.id)
  } catch {
    banners.value = []
  } finally {
    loading.value = false
  }
}

const EMPTY = { image: '', title: '', link: '', sort: 0, enabled: true, regionId: null }
const dialog = reactive({ visible: false, id: null, saving: false })
const form = reactive({ ...EMPTY })
const formRef = ref()
const rules = {
  image: [{ required: true, message: '请上传图片', trigger: 'change' }],
  link: [{ validator: (_, v, cb) => (!v || v.startsWith('/') ? cb() : cb(new Error('以 / 开头，如 /pages/store/index?id=1'))), trigger: 'blur' }]
}

const pick = (row) => Object.fromEntries(Object.keys(EMPTY).map((k) => [k, row[k] ?? EMPTY[k]]))

function openEdit(row) {
  dialog.id = row?.id || null
  const maxSort = Math.max(0, ...banners.value.map((b) => b.sort || 0))
  Object.assign(form, row ? pick(row) : { ...EMPTY, sort: maxSort + 1 })
  dialog.visible = true
  formRef.value?.clearValidate()
}

async function save() {
  await formRef.value.validate()
  dialog.saving = true
  const body = { ...form, title: form.title.trim(), link: form.link.trim() || null, regionId: form.regionId || null }
  try {
    if (dialog.id) await updateBanner(dialog.id, body)
    else await createBanner(body)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally {
    dialog.saving = false
  }
}

async function toggle(row, val) {
  try {
    await updateBanner(row.id, { ...pick(row), enabled: val })
    ElMessage.success(val ? '已启用' : '已停用')
  } catch {
    /* 已提示 */
  }
  load()
}

async function remove(row) {
  try {
    await confirmAction(`确定删除轮播图「${row.title || row.id}」？`, '删除轮播图')
    await deleteBanner(row.id)
  } catch {
    return
  }
  ElMessage.success('已删除')
  load()
}

onMounted(() => {
  load()
  loadRegions()
})
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <span class="muted">小程序首页轮播；排序数字越小越靠前。不选区域即全部区域都显示。</span>
      <div>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="success" :icon="Plus" @click="openEdit(null)">新增轮播图</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="banners" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="图片" width="200">
        <template #default="{ row }">
          <Thumb :src="row.image" :size="160" :height="64" />
        </template>
      </el-table-column>
      <el-table-column label="标题" min-width="140">
        <template #default="{ row }">{{ text(row.title) }}</template>
      </el-table-column>
      <el-table-column label="跳转页面" min-width="200" show-overflow-tooltip>
        <template #default="{ row }"><code v-if="row.link">{{ row.link }}</code><span v-else class="muted">不跳转</span></template>
      </el-table-column>
      <el-table-column label="区域" width="120">
        <template #default="{ row }">{{ row.regionId ? regionName[row.regionId] || `#${row.regionId}` : '全部' }}</template>
      </el-table-column>
      <el-table-column label="排序" width="70">
        <template #default="{ row }">{{ row.sort ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }"><el-switch :model-value="!!row.enabled" @change="(v) => toggle(row, v)" /></template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑轮播图' : '新增轮播图'" width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="图片" prop="image">
          <ImageUpload v-model="form.image" :width="320" :height="128" />
          <div class="muted" style="width: 100%">建议 750×300，jpg / png / webp</div>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="40" placeholder="仅后台识别用，可空" />
        </el-form-item>
        <el-form-item label="跳转页面" prop="link">
          <el-input v-model="form.link" placeholder="小程序页面路径，如 /pages/store/index?id=1，可空" />
        </el-form-item>
        <el-form-item label="所属区域">
          <el-select v-model="form.regionId" placeholder="全部区域" clearable style="width: 220px">
            <el-option v-for="r in regions" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
code {
  font-size: 12px;
  background: var(--el-fill-color-light);
  padding: 2px 6px;
  border-radius: 4px;
}
</style>
