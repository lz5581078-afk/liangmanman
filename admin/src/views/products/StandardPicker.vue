<script setup>
import { ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { listStandardProducts } from '@/api'
import { toPage } from '@/utils/format'
import { useCategories } from '@/composables/useCategories'
import Thumb from '@/components/Thumb.vue'

/** 选商品库里的一款：归一、合并都用它，选中后 emit('select', sku) */
const visible = defineModel({ type: Boolean, default: false })
const props = defineProps({
  product: { type: Object, default: null },
  submitting: { type: Boolean, default: false },
  title: { type: String, default: '归到商品库的一款' },
  confirmText: { type: String, default: '确定归一' },
  /** 列表里排除的款（合并时排除自己） */
  excludeId: { type: [Number, String], default: null }
})
const emit = defineEmits(['select'])

const { nameOf } = useCategories()
const keyword = ref('')
const list = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const selected = ref(null)

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, size: 10 }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const p = toPage(await listStandardProducts(params))
    list.value = props.excludeId ? p.records.filter((r) => r.id !== props.excludeId) : p.records
    total.value = p.total
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

watch(visible, (v) => {
  if (!v) return
  keyword.value = props.product?.name || ''
  selected.value = null
  search()
})

const display = (s) => [s.brand, s.name, s.spec].filter(Boolean).join(' ')
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="760px" top="6vh" destroy-on-close>
    <div v-if="product" class="current">
      <Thumb :src="product.images" :size="44" />
      <div>
        <div><b>{{ product.name }}</b></div>
        <div class="muted">{{ product.storeName || `店铺 ${product.storeId}` }}</div>
      </div>
    </div>
    <el-input v-model="keyword" placeholder="搜索商品库：名称、品牌、登记证号、条码" clearable @keyup.enter="search" @clear="search">
      <template #append><el-button :icon="Search" @click="search" /></template>
    </el-input>

    <el-table
      v-loading="loading"
      :data="list"
      highlight-current-row
      height="380"
      class="table"
      empty-text="没有找到，可先到「商品库」新增"
      @current-change="(r) => (selected = r)"
      @row-dblclick="(r) => emit('select', r)"
    >
      <el-table-column width="40">
        <template #default="{ row }">
          <el-radio :model-value="selected?.id" :value="row.id" class="pick-radio" @change="selected = row" />
        </template>
      </el-table-column>
      <el-table-column label="图" width="64">
        <template #default="{ row }"><Thumb :src="row.image" :size="40" /></template>
      </el-table-column>
      <el-table-column label="款" min-width="220">
        <template #default="{ row }">
          <div>{{ display(row) }} <el-tag v-if="row.status === 'PENDING'" size="small" type="warning">待核实</el-tag></div>
          <div v-if="row.regNo" class="muted">登记证号 {{ row.regNo }}</div>
          <div v-if="row.barcode" class="muted">条码 {{ row.barcode }}</div>
        </template>
      </el-table-column>
      <el-table-column label="分类" width="100">
        <template #default="{ row }">{{ nameOf[row.categoryId] || '-' }}</template>
      </el-table-column>
      <el-table-column label="关键词" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.keywords || '-' }}</template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="total > 10"
      v-model:current-page="page"
      :total="total"
      :page-size="10"
      layout="prev, pager, next"
      small
      class="pg"
      @current-change="load"
    />

    <template #footer>
      <span class="muted footer-tip">{{ selected ? `已选：${display(selected)}` : '请选择一款（双击可直接确认）' }}</span>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!selected" :loading="submitting" @click="emit('select', selected)">{{ confirmText }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.current {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
}
.table {
  margin-top: 12px;
}
.pick-radio :deep(.el-radio__label) {
  display: none;
}
.pg {
  justify-content: flex-end;
  margin-top: 8px;
}
.footer-tip {
  float: left;
  line-height: 32px;
}
</style>
