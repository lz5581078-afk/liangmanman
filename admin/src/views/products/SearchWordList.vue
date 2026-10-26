<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { listSearchWords, createSearchWord, updateSearchWord, deleteSearchWord } from '@/api'
import { time } from '@/utils/format'
import { confirmAction } from '@/utils/prompt'

/** 搜索同义词：一行是一组意思相同的词，农户搜其中任何一个，都会带上整组去找 */
const keyword = ref('')
const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = (await listSearchWords(keyword.value.trim() ? { keyword: keyword.value.trim() } : {})) || []
  } finally {
    loading.value = false
  }
}

const EMPTY = { word: '', synonyms: '' }
const dialog = reactive({ visible: false, id: null, saving: false })
const form = reactive({ ...EMPTY })
const formRef = ref()
const rules = {
  word: [{ required: true, message: '请输入主词', trigger: 'blur' }],
  synonyms: [{ required: true, message: '请输入同义词', trigger: 'blur' }]
}

function openEdit(row) {
  dialog.id = row?.id || null
  Object.assign(form, EMPTY, row ? { word: row.word, synonyms: row.synonyms } : {})
  dialog.visible = true
  formRef.value?.clearValidate()
}

async function save() {
  await formRef.value.validate()
  dialog.saving = true
  try {
    const body = { word: form.word.trim(), synonyms: form.synonyms.trim() }
    if (dialog.id) await updateSearchWord(dialog.id, body)
    else await createSearchWord(body)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally {
    dialog.saving = false
  }
}

async function remove(row) {
  try {
    await confirmAction(`删除「${row.word}」这组同义词？`, '删除')
    await deleteSearchWord(row.id)
  } catch {
    return
  }
  ElMessage.success('已删除')
  load()
}

const words = (s) => (s || '').split(/\s+/).filter(Boolean)

onMounted(load)
</script>

<template>
  <div class="page-card">
    <el-alert type="info" :closable="false" class="tip">
      农户的叫法和包装上的写法常常不一样（苞米 / 玉米、打虫药 / 吡虫啉）。一行是一组意思相同的词，搜其中任何一个都会带上整组去找。
      看板里「搜不到的词」可以加到这里。拼音缩写（如 fhf）也可以放进同义词。
    </el-alert>
    <div class="bar">
      <el-input v-model="keyword" placeholder="查找词" clearable style="width: 240px" @keyup.enter="load" @clear="load" />
      <el-button @click="load">查询</el-button>
      <span class="grow" />
      <el-button type="success" :icon="Plus" @click="openEdit(null)">新增一组</el-button>
    </div>
    <el-table v-loading="loading" :data="list" row-key="id">
      <el-table-column prop="word" label="主词" width="160" />
      <el-table-column label="同义词" min-width="320">
        <template #default="{ row }">
          <el-tag v-for="w in words(row.synonyms)" :key="w" class="word" type="info" effect="plain">{{ w }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="添加时间" width="150">
        <template #default="{ row }">{{ time(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑同义词' : '新增同义词'" width="520px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="主词" prop="word">
          <el-input v-model="form.word" placeholder="如：除草剂" maxlength="32" />
        </el-form-item>
        <el-form-item label="同义词" prop="synonyms">
          <el-input v-model="form.synonyms" type="textarea" :rows="3" placeholder="空格或逗号分开，如：草甘膦 打草药 锄草剂" maxlength="300" />
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
.tip {
  margin-bottom: 14px;
}
.bar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.grow {
  flex: 1;
}
.word {
  margin: 2px 6px 2px 0;
}
</style>
