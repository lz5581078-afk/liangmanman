<script setup>
import { onMounted } from 'vue'
import { listGroupBuys } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { GROUP_STATUS } from '@/utils/dict'
import { time, yuan, text } from '@/utils/format'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import Thumb from '@/components/Thumb.vue'

const { query, records, total, loading, page, pageSize, load, search } = usePagedList(listGroupBuys, { status: '' })

const percent = (r) => {
  const t = Number(r.targetQty) || 0
  if (!t) return 0
  return Math.min(100, Math.round(((Number(r.currentQty) || 0) / t) * 100))
}
const barStatus = (r) => (r.status === 'SUCCESS' || percent(r) >= 100 ? 'success' : r.status === 'FAILED' ? 'exception' : r.status === 'WAIT_DECISION' ? 'warning' : '')

onMounted(load)
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="query.status" @change="search">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button v-for="o in GROUP_STATUS.options" :key="o.value" :value="o.value">{{ o.label }}</el-radio-button>
      </el-radio-group>
    </div>

    <el-table v-loading="loading" :data="records" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="商品" min-width="220">
        <template #default="{ row }">
          <div class="cell-main">
            <Thumb :src="row.productImage" :size="44" />
            <div class="cell-text">
              <div>{{ text(row.productName) }}</div>
              <div class="muted">商品 ID {{ text(row.productId) }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="店铺" min-width="140">
        <template #default="{ row }">
          <div>{{ text(row.storeName) }}</div>
          <div class="muted">{{ row.storePhone || '' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="进度" min-width="200">
        <template #default="{ row }">
          <el-progress :percentage="percent(row)" :status="barStatus(row)" :stroke-width="10" />
          <div class="muted">{{ row.currentQty ?? 0 }} / {{ row.targetQty ?? 0 }} {{ row.unit || '' }}<template v-if="row.limitPerUser"> · 每人限 {{ row.limitPerUser }}</template></div>
        </template>
      </el-table-column>
      <el-table-column label="凑单价" width="120" align="right">
        <template #default="{ row }">
          <div class="money price">{{ yuan(row.groupPrice) }}</div>
          <div v-if="row.originalPrice != null" class="muted"><s>{{ yuan(row.originalPrice) }}</s></div>
        </template>
      </el-table-column>
      <el-table-column label="截止时间" width="170">
        <template #default="{ row }">
          <div>{{ time(row.endAt) }}</div>
          <div v-if="row.endWhenReached" class="muted">凑够即结束</div>
          <div v-if="row.status === 'WAIT_DECISION' && row.decisionDeadline" class="muted">决定截止 {{ time(row.decisionDeadline) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="150">
        <template #default="{ row }"><StatusTag :dict="GROUP_STATUS" :value="row.status" :label="row.statusLabel" /></template>
      </el-table-column>
    </el-table>
    <Pager v-model:page="page" v-model:size="pageSize" :total="total" @change="load" />
  </div>
</template>

<style scoped>
.price {
  color: #d9480f;
  font-weight: 600;
}
</style>
