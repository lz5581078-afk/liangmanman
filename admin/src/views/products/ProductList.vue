<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listProducts, offShelfProduct, linkProduct, unlinkProduct } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { useCategories } from '@/composables/useCategories'
import { NORMALIZE_STATUS } from '@/utils/dict'
import { yuan, text } from '@/utils/format'
import { askReason, confirmAction } from '@/utils/prompt'
import FilterBar from '@/components/FilterBar.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import Thumb from '@/components/Thumb.vue'
import StandardPicker from './StandardPicker.vue'

const route = useRoute()
const { nameOf } = useCategories()
const { query, records, total, loading, page, pageSize, load, search, reset } = usePagedList(listProducts, {
  keyword: '',
  storeId: '',
  normalize: '',
  onShelf: ''
})

const pickerVisible = ref(false)
const linking = ref(false)
const current = ref(null)

function openLink(row) {
  current.value = row
  pickerVisible.value = true
}

async function onPick(std) {
  if (!std || !current.value) return
  linking.value = true
  try {
    await linkProduct(current.value.id, std.id)
    ElMessage.success('已归一')
    pickerVisible.value = false
    load()
  } catch {
    /* 已提示 */
  } finally {
    linking.value = false
  }
}

async function unlink(row) {
  try {
    await confirmAction(`取消「${row.name}」与「${row.standardName || row.standardProductId}」的归一？取消后它不再出现在这一款的同款比价里。`)
    await unlinkProduct(row.id)
  } catch {
    return
  }
  ElMessage.success('已取消归一')
  load()
}

async function offShelf(row) {
  try {
    const reason = await askReason(`下架「${row.name}」`, { placeholder: '下架原因，如：违禁农药、虚假宣传、价格异常' })
    await offShelfProduct(row.id, reason)
  } catch {
    return
  }
  ElMessage.success('已下架')
  load()
}

function quickPending() {
  query.normalize = query.normalize === 'PENDING' ? '' : 'PENDING'
  search()
}

onMounted(() => {
  if (typeof route.query.normalize === 'string') query.normalize = route.query.normalize
  if (typeof route.query.storeId === 'string') query.storeId = route.query.storeId
  load()
})
</script>

<template>
  <div class="page-card">
    <FilterBar @search="search" @reset="reset">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="商品名称" clearable />
      </el-form-item>
      <el-form-item label="店铺 ID">
        <el-input v-model="query.storeId" placeholder="店铺 ID" clearable style="width: 110px" />
      </el-form-item>
      <el-form-item label="归一">
        <el-select v-model="query.normalize" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="o in NORMALIZE_STATUS.options" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="上架">
        <el-select v-model="query.onShelf" placeholder="全部" clearable style="width: 110px">
          <el-option label="在售" :value="true" />
          <el-option label="已下架" :value="false" />
        </el-select>
      </el-form-item>
      <template #extra>
        <el-button :type="query.normalize === 'PENDING' ? 'warning' : 'default'" plain @click="quickPending">
          {{ query.normalize === 'PENDING' ? '查看全部' : '只看待归一' }}
        </el-button>
      </template>
    </FilterBar>

    <el-table v-loading="loading" :data="records" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="商品" min-width="240">
        <template #default="{ row }">
          <div class="cell-main">
            <Thumb :src="row.images" :size="48" />
            <div class="cell-text">
              <div>{{ row.name }}</div>
              <div class="muted">{{ nameOf[row.categoryId] || '未分类' }}<template v-if="row.stock != null"> · 库存 {{ row.stock }}</template> · 销量 {{ row.sales ?? 0 }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="店铺" min-width="140">
        <template #default="{ row }">
          <div>{{ text(row.storeName) }}</div>
          <div class="muted">ID {{ row.storeId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="价格" width="110" align="right">
        <template #default="{ row }">
          <span class="money">{{ yuan(row.price) }}</span><span class="muted">/{{ row.unit || '件' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="配送" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.localDelivery" size="small" type="success" disable-transitions>本地</el-tag>
          <el-tag v-if="row.expressDelivery" size="small" type="primary" disable-transitions class="ml4">快递</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="归一" min-width="190">
        <template #default="{ row }">
          <template v-if="row.normalizeStatus === 'LINKED' || row.standardProductId">
            <StatusTag :dict="NORMALIZE_STATUS" value="LINKED" />
            <div class="muted ellipsis">{{ row.standardName || `款 #${row.standardProductId}` }}</div>
          </template>
          <StatusTag v-else :dict="NORMALIZE_STATUS" :value="row.normalizeStatus || 'PENDING'" />
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.onShelf ? 'success' : 'info'" size="small" disable-transitions>{{ row.onShelf ? '在售' : '已下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openLink(row)">{{ row.standardProductId ? '改归一' : '归一' }}</el-button>
          <el-button v-if="row.standardProductId" link type="warning" @click="unlink(row)">取消归一</el-button>
          <el-button v-if="row.onShelf" link type="danger" @click="offShelf(row)">下架</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pager v-model:page="page" v-model:size="pageSize" :total="total" @change="load" />

    <StandardPicker v-model="pickerVisible" :product="current" :submitting="linking" @select="onPick" />
  </div>
</template>

<style scoped>
.ml4 {
  margin-left: 4px;
}
</style>
