<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { createRegion, updateRegion, deleteRegion } from '@/api'
import { useRegions } from '@/composables/useRegions'
import { confirmAction } from '@/utils/prompt'

const { regions, loading, load } = useRegions()
const enabledCount = computed(() => regions.value.filter((r) => r.enabled).length)

const EMPTY = { name: '', lat: null, lng: null, radiusKm: 30, enabled: true }
const dialog = reactive({ visible: false, id: null, saving: false })
const form = reactive({ ...EMPTY })
const formRef = ref()
const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  lat: [{ required: true, message: '请输入纬度', trigger: 'blur' }],
  lng: [{ required: true, message: '请输入经度', trigger: 'blur' }],
  radiusKm: [{ required: true, message: '请输入半径', trigger: 'blur' }]
}

function openEdit(row) {
  dialog.id = row?.id || null
  Object.assign(form, EMPTY, row ? { name: row.name, lat: row.lat, lng: row.lng, radiusKm: row.radiusKm, enabled: !!row.enabled } : {})
  dialog.visible = true
  formRef.value?.clearValidate()
}

async function save() {
  await formRef.value.validate()
  dialog.saving = true
  const body = { ...form, name: form.name.trim() }
  try {
    if (dialog.id) await updateRegion(dialog.id, body)
    else await createRegion(body)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally {
    dialog.saving = false
  }
}

async function toggle(row, val) {
  try {
    await updateRegion(row.id, { name: row.name, lat: row.lat, lng: row.lng, radiusKm: row.radiusKm, enabled: val })
    ElMessage.success(val ? '已启用' : '已停用')
  } catch {
    /* 已提示 */
  }
  load()
}

async function remove(row) {
  try {
    await confirmAction(`确定删除区域「${row.name}」？`, '删除区域')
    await deleteRegion(row.id)
  } catch {
    return
  }
  ElMessage.success('已删除')
  load()
}

const mapUrl = (r) => `https://apis.map.qq.com/uri/v1/marker?marker=coord:${r.lat},${r.lng};title:${encodeURIComponent(r.name || '')}&referer=liangmanman`

onMounted(load)
</script>

<template>
  <div class="page-card">
    <el-alert type="warning" :closable="false" show-icon class="mb">
      <template #title>设置了启用的区域后，只有区域内的位置可以开店。</template>
      <template #default>
        当前启用 <b>{{ enabledCount }}</b> 个区域{{ enabledCount ? '：开店位置须落在任一启用区域的半径内。' : '：没有启用的区域，任何位置都可以开店。' }}
      </template>
    </el-alert>
    <div class="toolbar">
      <span class="muted">经纬度可在腾讯地图坐标拾取器（lbs.qq.com/getPoint）获取。</span>
      <el-button type="success" :icon="Plus" @click="openEdit(null)">新增区域</el-button>
    </div>

    <el-table v-loading="loading" :data="regions" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column label="中心点" min-width="200">
        <template #default="{ row }">
          <el-link v-if="row.lat != null && row.lng != null" :href="mapUrl(row)" target="_blank" type="primary">{{ row.lat }}, {{ row.lng }}</el-link>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="半径" width="110">
        <template #default="{ row }">{{ row.radiusKm ?? '-' }} 公里</template>
      </el-table-column>
      <el-table-column label="启用" width="100">
        <template #default="{ row }">
          <el-switch :model-value="!!row.enabled" @change="(v) => toggle(row, v)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑区域' : '新增区域'" width="460px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="30" placeholder="如：某某县城区" />
        </el-form-item>
        <el-form-item label="中心纬度" prop="lat">
          <el-input-number v-model="form.lat" :min="-90" :max="90" :precision="6" :step="0.01" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="中心经度" prop="lng">
          <el-input-number v-model="form.lng" :min="-180" :max="180" :precision="6" :step="0.01" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="半径" prop="radiusKm">
          <el-input-number v-model="form.radiusKm" :min="0.5" :max="500" :step="5" :precision="1" controls-position="right" style="width: 200px" />
          <span class="unit">公里</span>
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
.mb {
  margin-bottom: 16px;
}
.unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}
</style>
